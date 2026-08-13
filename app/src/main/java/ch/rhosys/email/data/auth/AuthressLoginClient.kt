package ch.rhosys.email.data.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import ch.rhosys.email.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Port of loginClient.ts from @authress/login-react-native.
 *
 * Authress is not a plain OAuth provider and this is deliberately not an
 * authorize/token exchange. The flow the SDK implements is:
 *
 *  1. POST /api/authentication with the applicationId, the redirect URL and a
 *     PKCE S256 challenge. Authress answers with an `authenticationUrl` to open
 *     and an `authenticationRequestId` to correlate the response.
 *  2. Open that URL in a Custom Tab. The user picks a provider, passkey or
 *     password on the Authress-hosted page.
 *  3. Authress redirects back to the app's deep link with `code` and
 *     `authenticationRequestId` query parameters.
 *  4. POST /api/authentication/{authenticationRequestId}/tokens with the code,
 *     the stored code verifier and the redirect URI.
 *
 * The session is then held in cookies rather than in a token pair —
 * see [AuthressCookieJar].
 */
class AuthressLoginClient(
    private val context: Context,
    private val cookieJar: AuthressCookieJar,
    httpClient: OkHttpClient,
) {
    /** The SDK's HttpClient appends /api to the origin; every path below is relative to it. */
    private val loginUrl = "https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}/api"

    private val origin = "https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}"

    private val redirectUri = BuildConfig.OAUTH_REDIRECT_URI

    private val storage = AuthStorageManager(context)

    /** The Authress calls carry the session cookie and must not carry our API bearer. */
    private val http = httpClient.newBuilder().cookieJar(cookieJar).build()

    class AuthressException(message: String, val status: Int? = null) : Exception(message)

    private val _sessionEstablished = MutableStateFlow(false)

    /**
     * Emits when a session exists. The SDK resolves an internal promise at the
     * same points; a flow is the idiomatic equivalent for Compose to collect.
     */
    val sessionEstablished: StateFlow<Boolean> = _sessionEstablished.asStateFlow()

    init {
        _sessionEstablished.value = getToken() != null
    }

    data class AuthenticationResponse(
        val authenticationUrl: String,
        val authenticationRequestId: String,
    )

    // ── authenticate ────────────────────────────────────────────────────────

    /**
     * Begins the login flow and opens the Authress-hosted login page. Returns
     * once the browser has been launched; completion arrives via the deep link.
     */
    suspend fun authenticate(connectionId: String? = null): Result<Unit> = runCatching {
        storage.setAuthenticationRequest(null)

        val codes = JwtManager.getAuthCodes()
        val body = JSONObject()
            .put("redirectUrl", redirectUri)
            .put("applicationId", BuildConfig.AUTHRESS_APPLICATION_ID)
            .put("codeChallenge", codes.codeChallenge)
            .put("codeChallengeMethod", "S256")
            .apply { connectionId?.let { put("connectionId", it) } }

        val response = post("/authentication", body)
        val authenticationUrl = response.getString("authenticationUrl")
        val authenticationRequestId = response.getString("authenticationRequestId")

        storage.setAuthenticationRequest(
            AuthStorageManager.PendingAuthentication(
                codeVerifier = codes.codeVerifier,
                authenticationRequestId = authenticationRequestId,
                redirectUrl = redirectUri,
            ),
        )

        withContext(Dispatchers.Main) {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authenticationUrl))
        }
    }

    // ── completeAuthenticationRequest ───────────────────────────────────────

    /**
     * Completes the flow from the deep link. Mirrors the SDK: a mismatched or
     * missing pending request is an error, but a 4xx from the token exchange is
     * treated as success-and-clean-up, because it most often means the code was
     * already redeemed.
     */
    suspend fun completeAuthenticationRequest(uri: Uri): Result<Unit> = runCatching {
        val code = uri.getQueryParameter("code").orEmpty()
        val authenticationRequestId = uri.getQueryParameter("authenticationRequestId").orEmpty()

        val pending = storage.getAuthenticationRequest()
            ?: throw AuthressException("No authentication request in progress")
        if (pending.authenticationRequestId != authenticationRequestId) {
            throw AuthressException("Authentication request mismatch")
        }

        val body = JSONObject()
            .put("code", code)
            .put("codeVerifier", pending.codeVerifier)
            .put("redirectUri", pending.redirectUrl)

        try {
            post("/authentication/$authenticationRequestId/tokens", body)
        } catch (e: AuthressException) {
            val status = e.status
            if (status != null && status < 500) {
                // Code already used — the session is established, nothing to do.
                storage.setAuthenticationRequest(null)
                _sessionEstablished.value = getToken() != null
                return@runCatching
            }
            throw e
        }

        storage.setAuthenticationRequest(null)
        _sessionEstablished.value = getToken() != null
    }

    /** True when the redirect belongs to this client. */
    fun isRedirect(uri: Uri?): Boolean =
        uri != null && uri.toString().startsWith(redirectUri)

    // ── session ─────────────────────────────────────────────────────────────

    /**
     * The bearer token for API calls, read from the `authorization` cookie and
     * checked against the issuer, as the SDK's getToken does.
     */
    fun getToken(): String? {
        val token = cookieJar.authorizationCookie() ?: return null
        val payload = JwtManager.decode(token) ?: return null
        if (payload.optString("iss") != origin) return null
        return token
    }

    val isSignedIn: Boolean get() = getToken() != null

    /** The identity token's claims, for showing who is signed in. */
    fun getUserIdentity(): JSONObject? {
        val payload = JwtManager.decode(cookieJar.userCookie()) ?: return null
        if (payload.optString("iss") != origin) return null
        return payload
    }

    /**
     * Validates the session server-side and refreshes the cookie when the current
     * token has expired. The SDK calls PATCH /session for this.
     */
    suspend fun userIsLoggedIn(): Boolean {
        if (getToken() != null) return true
        return runCatching {
            patch("/session", JSONObject())
            (getToken() != null).also { _sessionEstablished.value = it }
        }.getOrDefault(false)
    }

    /**
     * Waits until a bearer token is available, then returns it. Blocks until
     * [authenticate] plus [completeAuthenticationRequest], or [userIsLoggedIn],
     * establishes a session. This is the SDK's documented way to obtain the value
     * for an Authorization header, and is what [ch.rhosys.email.data.remote.api.AuthInterceptor]
     * uses — reading the cookie directly would race a session that is mid-refresh.
     *
     * Returns null if no token arrives within [timeoutInMillis]; 0 means do not
     * wait at all, matching the SDK.
     */
    suspend fun waitForToken(timeoutInMillis: Long = 5000): String? {
        getToken()?.let { return it }
        if (timeoutInMillis == 0L) return null

        return withTimeoutOrNull(timeoutInMillis) {
            // Resolved by completeAuthenticationRequest or a successful session check.
            _sessionEstablished.first { it }
            getToken()
        }
    }

    /** Ends the server session first, while the cookie can still identify it. */
    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { delete("/session") }
        cookieJar.clear()
        storage.clear()
        _sessionEstablished.value = false
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private suspend fun post(path: String, body: JSONObject): JSONObject =
        execute(Request.Builder().url(loginUrl + path).post(body.toBody()))

    private suspend fun patch(path: String, body: JSONObject): JSONObject =
        execute(Request.Builder().url(loginUrl + path).patch(body.toBody()))

    private suspend fun delete(path: String): JSONObject =
        execute(Request.Builder().url(loginUrl + path).delete())

    private fun JSONObject.toBody() = toString().toRequestBody(JSON)

    private suspend fun execute(builder: Request.Builder): JSONObject = withContext(Dispatchers.IO) {
        val request = builder
            .header("Content-Type", "application/json")
            .header("X-Powered-By", "Authress Login SDK; Android; ${BuildConfig.VERSION_NAME}")
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthressException(
                    "Authress ${request.method} ${request.url.encodedPath} failed: ${response.code} $text",
                    status = response.code,
                )
            }
            runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
