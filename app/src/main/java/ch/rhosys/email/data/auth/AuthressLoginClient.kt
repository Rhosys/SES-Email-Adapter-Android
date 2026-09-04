package ch.rhosys.email.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import ch.rhosys.email.BuildConfig
import ch.rhosys.email.data.log.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Port of loginClient.ts from @authress/login-react-native, cross-checked
 * against the more complete @authress/login (web) SDK for antiAbuseHash
 * ordering and the session/device/profile endpoints the RN SDK also exposes.
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
 *
 * Every request carries an `antiAbuseHash` (a proof-of-work computed by
 * [JwtManager.calculateAntiAbuseHash]) — Authress rejects `/authentication`
 * and `/authentication/{id}/tokens` calls without one. The prop order passed
 * into that hash matters (it's part of what's hashed), so each call site
 * below mirrors the exact key order the web SDK uses for the equivalent call.
 */
class AuthressLoginClient(
    private val context: Context,
    private val cookieJar: AuthressCookieJar,
    httpClient: OkHttpClient,
    private val logger: AppLogger,
    /** Sent as the `Origin` header on every request to Authress; also the app's deep-link scheme, e.g. "ch.rhosys.email://auth". Required — Authress rejects requests that omit it. */
    private val requestOrigin: String,
    /** Injectable like [cookieJar], so tests can substitute a fake instead of touching EncryptedSharedPreferences. */
    private val storage: AuthStorageManager = AuthStorageManager(context),
) {
    /** The SDK's HttpClient appends /api to the origin; every path below is relative to it. */
    private val loginUrl = "https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}/api"

    private val origin = "https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}"

    private val redirectUri = BuildConfig.OAUTH_REDIRECT_URI

    /** The Authress calls carry the session cookie and must not carry our API bearer. */
    private val http = httpClient.newBuilder().cookieJar(cookieJar).build()

    class AuthressException(message: String, val status: Int? = null) : Exception(message)

    private val _sessionEstablished = MutableStateFlow(false)

    /**
     * Emits when a session exists. The SDK resolves an internal promise at the
     * same points; a flow is the idiomatic equivalent for Compose to collect.
     */
    val sessionEstablished: StateFlow<Boolean> = _sessionEstablished.asStateFlow()

    /**
     * Where the sign-in flow currently is, so the UI can show *which* step is
     * slow instead of a single spinner covering everything from "tapped
     * Continue" to "mailbox loaded". [AwaitingRedirect] can legitimately sit
     * for a while (the user is doing something in the browser); the others
     * are calls to Authress (or local validation) and are worth a "this is
     * taking a while" hint if they don't resolve quickly.
     *
     * Split finer than a single "signing in" spinner on purpose: each of these
     * is a distinct network call or check, and a user stuck on, say,
     * [ExchangingToken] for 30s is showing us something different than one
     * stuck on [RequestingAuthenticationUrl].
     */
    enum class AuthStatus {
        Idle,
        RequestingAuthenticationUrl,
        OpeningBrowser,
        AwaitingRedirect,
        VerifyingRedirect,
        ExchangingToken,
    }

    private val _authStatus = MutableStateFlow(AuthStatus.Idle)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    /** The reason the last [authenticate] or [completeAuthenticationRequest] failed, if any. */
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    /**
     * Set by [authenticate] when it starts a new attempt while a previous one was
     * still in flight (e.g. "Try again" tapped while the first Custom Tab is still
     * alive). If that first tab still redirects back afterwards, its
     * authenticationRequestId won't match the new pending one — expected, not a
     * bug — so [completeAuthenticationRequest] drops it quietly instead of
     * surfacing "Authentication request mismatch" to the user.
     */
    private var abandonedAuthenticationRequestId: String? = null

    init {
        // The SDK restores cookies from encrypted storage in its constructor,
        // before anything reads a token.
        cookieJar.restoreCookies()
        _sessionEstablished.value = getToken() != null
    }

    data class AuthenticationResponse(
        val authenticationUrl: String,
        val authenticationRequestId: String,
    )

    /** Mirrors the web SDK's AuthenticationParameters that make sense for a native app (redirectUrl is fixed to the app's own deep link). */
    data class AuthenticationOptions(
        val connectionId: String? = null,
        val tenantLookupIdentifier: String? = null,
        val inviteId: String? = null,
        val responseLocation: String? = null,
        val flowType: String? = null,
        val scopes: List<String>? = null,
        val audiences: List<String>? = null,
        val connectionProperties: Map<String, String>? = null,
        val multiAccount: Boolean? = null,
    )

    data class Device(val deviceId: String, val name: String)

    // ── authenticate ────────────────────────────────────────────────────────

    /**
     * Begins the login flow and opens the Authress-hosted login page. Returns
     * once the browser has been launched; completion arrives via the deep link.
     */
    suspend fun authenticate(options: AuthenticationOptions = AuthenticationOptions()): Result<Unit> = runCatching {
        val flowStartedAt = System.currentTimeMillis()

        val previousStatus = _authStatus.value
        val abandonedPending = storage.getAuthenticationRequest()
        if (previousStatus != AuthStatus.Idle) {
            // Most often: the user waited past the slow-hint and tapped "Try again"
            // while the first Custom Tab is still alive. That tab can still redirect
            // back with the OLD authenticationRequestId after we've moved on to a new
            // one; recording it here lets completeAuthenticationRequest() recognize
            // and quietly drop that stale redirect instead of surfacing it as an
            // "Authentication request mismatch" error.
            logger.warn(
                "Authress",
                "authenticate() re-entered while previous attempt was $previousStatus" +
                    (abandonedPending?.let { " — abandoning authenticationRequestId=${it.authenticationRequestId}" } ?: ""),
            )
            abandonedAuthenticationRequestId = abandonedPending?.authenticationRequestId
        }

        logger.info("Authress", "authenticate() started (connectionId=${options.connectionId})")
        _authError.value = null
        _authStatus.value = AuthStatus.RequestingAuthenticationUrl
        storage.setAuthenticationRequest(null)

        val codes = JwtManager.getAuthCodes()
        // Key order matches @authress/login's authenticate(): connectionId,
        // tenantLookupIdentifier, inviteId, applicationId, audiences.
        val hashStartedAt = System.currentTimeMillis()
        val antiAbuseHash = JwtManager.calculateAntiAbuseHash(
            linkedMapOf(
                "connectionId" to options.connectionId,
                "tenantLookupIdentifier" to options.tenantLookupIdentifier,
                "inviteId" to options.inviteId,
                "applicationId" to BuildConfig.AUTHRESS_APPLICATION_ID,
                "audiences" to options.audiences,
            ),
        )
        logAntiAbuseHash(antiAbuseHash, System.currentTimeMillis() - hashStartedAt)
        val body = JSONObject()
            .put("redirectUrl", redirectUri)
            .put("applicationId", BuildConfig.AUTHRESS_APPLICATION_ID)
            .put("codeChallenge", codes.codeChallenge)
            .put("codeChallengeMethod", "S256")
            .put("antiAbuseHash", antiAbuseHash)
            .apply {
                options.connectionId?.let { put("connectionId", it) }
                options.tenantLookupIdentifier?.let { put("tenantLookupIdentifier", it) }
                options.inviteId?.let { put("inviteId", it) }
                options.responseLocation?.let { put("responseLocation", it) }
                options.flowType?.let { put("flowType", it) }
                options.scopes?.let { put("requestedScopes", JSONArray(it)) }
                options.audiences?.let { put("audiences", JSONArray(it)) }
                options.connectionProperties?.let { put("connectionProperties", JSONObject(it)) }
                options.multiAccount?.let { put("multiAccount", it) }
            }

        val response = post("/authentication", body)
        val authenticationUrl = response.getString("authenticationUrl")
        val authenticationRequestId = response.getString("authenticationRequestId")
        logger.info("Authress", "authentication request created: authenticationRequestId=$authenticationRequestId")

        storage.setAuthenticationRequest(
            AuthStorageManager.PendingAuthentication(
                codeVerifier = codes.codeVerifier,
                authenticationRequestId = authenticationRequestId,
                redirectUrl = redirectUri,
            ),
        )

        _authStatus.value = AuthStatus.OpeningBrowser
        withContext(Dispatchers.Main) {
            launchAuthenticationUrl(authenticationUrl)
        }
        logger.info("Authress", "Custom Tab launched, ${System.currentTimeMillis() - flowStartedAt}ms since authenticate() started")
        _authStatus.value = AuthStatus.AwaitingRedirect
    }.onFailure {
        logger.error("Authress", "authenticate() failed", it)
        _authStatus.value = AuthStatus.Idle
        _authError.value = it.message
    }

    // ── completeAuthenticationRequest ───────────────────────────────────────

    /**
     * Completes the flow from the deep link. A mismatched or missing pending
     * request is an error. A failed token exchange is only ever treated as
     * harmless when a valid session cookie already exists (a verifiable fact,
     * not a guess) — otherwise it's a real failure and is surfaced as such.
     */
    suspend fun completeAuthenticationRequest(uri: Uri): Result<Unit> = runCatching {
        val flowStartedAt = System.currentTimeMillis()
        val code = uri.getQueryParameter("code").orEmpty()
        val authenticationRequestId = uri.getQueryParameter("nonce").orEmpty()
        logger.info(
            "Authress",
            "completeAuthenticationRequest() started (redirect received, authenticationRequestId=$authenticationRequestId, " +
                "code=${if (code.isEmpty()) "missing" else "present"})",
        )

        if (authenticationRequestId.isNotEmpty() && authenticationRequestId == abandonedAuthenticationRequestId) {
            // A stale Custom Tab from an attempt we already moved on from (see
            // authenticate()) finally redirected back. Expected, not an error — drop
            // it without touching _authStatus, which belongs to whatever attempt is
            // actually current.
            logger.info("Authress", "ignoring redirect for abandoned authenticationRequestId=$authenticationRequestId")
            return@runCatching
        }

        _authStatus.value = AuthStatus.VerifyingRedirect

        val pending = storage.getAuthenticationRequest()
            ?: throw AuthressException("No authentication request in progress (redirect carried authenticationRequestId=$authenticationRequestId)")

        // Seen in production: Authress's hosted redirect sometimes comes back with
        // `code` but no `authenticationRequestId` at all (not merely a different
        // one). isRedirect() already confirmed this deep link matches our exclusive
        // redirectUri, so as long as there's no other attempt we've abandoned and
        // could be confusing this with, a single pending request is unambiguous —
        // trust it rather than fail a login that otherwise has a valid code.
        val effectiveAuthenticationRequestId = if (authenticationRequestId.isEmpty() && abandonedAuthenticationRequestId == null) {
            logger.warn(
                "Authress",
                "redirect carried no authenticationRequestId; assuming it belongs to the sole pending request=${pending.authenticationRequestId}",
            )
            pending.authenticationRequestId
        } else {
            authenticationRequestId
        }

        if (pending.authenticationRequestId != effectiveAuthenticationRequestId) {
            // Not a recognized abandonment (checked above) and doesn't match the
            // current pending request either — a genuinely unexpected mismatch.
            logger.warn(
                "Authress",
                "authentication request mismatch: pending=${pending.authenticationRequestId}, redirect=$authenticationRequestId " +
                    "— not a known-abandoned request either; redirect may be stale from before the app was killed/reinstalled",
            )
            throw AuthressException("Authentication request mismatch")
        }

        // Key order matches @authress/login's token exchange: client_id
        // (applicationId), authenticationRequestId, code.
        _authStatus.value = AuthStatus.ExchangingToken
        val hashStartedAt = System.currentTimeMillis()
        val antiAbuseHash = JwtManager.calculateAntiAbuseHash(
            linkedMapOf(
                "applicationId" to BuildConfig.AUTHRESS_APPLICATION_ID,
                "authenticationRequestId" to effectiveAuthenticationRequestId,
                "code" to code,
            ),
        )
        logAntiAbuseHash(antiAbuseHash, System.currentTimeMillis() - hashStartedAt)
        // This endpoint is OAuth-shaped (unlike /authentication, which uses its own
        // camelCase body): grant_type, client_id, redirect_uri and code_verifier are
        // exactly what @authress/login's own token exchange sends, snake_case included.
        val body = JSONObject()
            .put("grant_type", "authorization_code")
            .put("client_id", BuildConfig.AUTHRESS_APPLICATION_ID)
            .put("code", code)
            .put("code_verifier", pending.codeVerifier)
            .put("redirect_uri", pending.redirectUrl)
            .put("antiAbuseHash", antiAbuseHash)

        try {
            post("/authentication/$effectiveAuthenticationRequestId/tokens", body)
        } catch (e: AuthressException) {
            // Always log what Authress actually said — status and body — rather than
            // guessing at the meaning of a status code. The one case genuinely safe to
            // continue past is verifiable, not assumed: a session cookie is already on
            // hand, meaning some earlier exchange (e.g. a duplicate redirect delivery)
            // already completed this login. Anything else is a real failure and must
            // surface to the user, not be silently treated as success.
            logger.error("Authress", "token exchange failed: ${e.message}", e)
            if (getToken() != null) {
                logger.info("Authress", "a valid session cookie is already present; treating this failure as a harmless duplicate")
                storage.setAuthenticationRequest(null)
                _sessionEstablished.value = true
                _authStatus.value = AuthStatus.Idle
                return@runCatching
            }
            throw e
        }

        cookieJar.backupCookies()
        storage.setAuthenticationRequest(null)
        _sessionEstablished.value = getToken() != null
        _authStatus.value = AuthStatus.Idle
        logger.info("Authress", "session established, ${System.currentTimeMillis() - flowStartedAt}ms since redirect received")
    }.onFailure {
        logger.error("Authress", "completeAuthenticationRequest() failed", it)
        _authStatus.value = AuthStatus.Idle
        _authError.value = it.message
    }

    /** True when the redirect belongs to this client. */
    fun isRedirect(uri: Uri?): Boolean =
        uri != null && uri.toString().startsWith(redirectUri)

    /**
     * [context] here is always the Application context (this client is built
     * once in [ch.rhosys.email.di.AppContainer], never per-Activity), so the
     * Custom Tab intent needs FLAG_ACTIVITY_NEW_TASK explicitly — launchUrl
     * doesn't add it, and starting an activity from a non-Activity context
     * without it throws.
     */
    private fun launchAuthenticationUrl(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    // ── session ─────────────────────────────────────────────────────────────

    /**
     * The bearer token for API calls, read from the `authorization` cookie and
     * checked against the issuer and expiry, as the SDK's getToken does.
     * [JwtManager.decode] already shortens `exp` by a 10s clock-skew buffer.
     */
    fun getToken(): String? {
        val token = cookieJar.authorizationCookie() ?: return null
        val payload = JwtManager.decode(token) ?: return null
        if (payload.optString("iss") != origin) return null
        if (payload.has("exp") && payload.getLong("exp") * 1000 <= System.currentTimeMillis()) return null
        return token
    }

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
        logger.info("Authress", "userIsLoggedIn() found no cached token, refreshing via PATCH /session")
        return runCatching {
            patch("/session", JSONObject())
            val loggedIn = getToken() != null
            if (loggedIn) cookieJar.backupCookies()
            loggedIn.also { _sessionEstablished.value = it }
        }.getOrDefault(false)
    }

    /**
     * Waits until a bearer token is available, then returns it. When the cached
     * token is missing or expired, this actively revalidates via
     * [userIsLoggedIn] (PATCH /session) rather than passively waiting for some
     * other caller to refresh it — every HTTP/WebSocket call goes through this
     * function, so this is the one choke point that makes an expired token get
     * refreshed instead of reused. [userIsLoggedIn] itself no-ops (no network
     * call) whenever a valid cached token already exists, so a burst of
     * concurrent callers only pays for a PATCH /session while none of them has
     * one yet.
     *
     * This is the SDK's documented way to obtain the value for an Authorization
     * header, and its legitimate callers are
     * [ch.rhosys.email.data.remote.api.AuthInterceptor] (grabbing a token right
     * before an Email API request goes out) and
     * [ch.rhosys.email.data.realtime.RealtimeClient] (attaching a token to the
     * WebSocket handshake). It must never be called from Authress's own client:
     * a call like `POST /authentication` is what establishes the session, so
     * waiting on its own result here would just deadlock until the timeout.
     *
     * Returns null if no token arrives within [timeoutInMillis]; 0 means do not
     * wait at all, matching the SDK.
     */
    suspend fun waitForToken(timeoutInMillis: Long = 5000): String? {
        getToken()?.let { return it }
        if (timeoutInMillis == 0L) return null

        return withTimeoutOrNull(timeoutInMillis) {
            if (userIsLoggedIn()) getToken() else null
        }
    }

    /** Ends the server session first, while the cookie can still identify it. */
    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { delete("/session") }
        cookieJar.clear()
        storage.clear()
        _sessionEstablished.value = false
    }

    // ── linkIdentity ────────────────────────────────────────────────────────

    /**
     * Links a new identity to the currently signed-in user, following the same
     * `/authentication` + deep-link flow as [authenticate] (the redirect lands
     * back in [completeAuthenticationRequest]), but with `linkIdentity: true`.
     * Requires an existing session. Mirrors the RN SDK's `linkIdentity`, plus
     * the antiAbuseHash the web SDK sends for the same call.
     */
    suspend fun linkIdentity(connectionId: String? = null, tenantLookupIdentifier: String? = null): Result<AuthenticationResponse> = runCatching {
        if (connectionId == null && tenantLookupIdentifier == null) {
            throw AuthressException("connectionId or tenantLookupIdentifier must be specified")
        }
        if (getToken() == null) throw AuthressException("Not logged in")

        storage.setAuthenticationRequest(null)
        val codes = JwtManager.getAuthCodes()
        // Key order matches @authress/login's linkIdentity(): connectionId,
        // tenantLookupIdentifier, applicationId.
        val antiAbuseHash = JwtManager.calculateAntiAbuseHash(
            linkedMapOf(
                "connectionId" to connectionId,
                "tenantLookupIdentifier" to tenantLookupIdentifier,
                "applicationId" to BuildConfig.AUTHRESS_APPLICATION_ID,
            ),
        )
        val body = JSONObject()
            .put("redirectUrl", redirectUri)
            .put("applicationId", BuildConfig.AUTHRESS_APPLICATION_ID)
            .put("codeChallenge", codes.codeChallenge)
            .put("codeChallengeMethod", "S256")
            .put("linkIdentity", true)
            .put("antiAbuseHash", antiAbuseHash)
            .apply {
                connectionId?.let { put("connectionId", it) }
                tenantLookupIdentifier?.let { put("tenantLookupIdentifier", it) }
            }

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
            launchAuthenticationUrl(authenticationUrl)
        }
        AuthenticationResponse(authenticationUrl, authenticationRequestId)
    }.onFailure { logger.error("Authress", "linkIdentity() failed", it) }

    // ── profile & devices ──────────────────────────────────────────────────

    /** The signed-in user's full profile, including linked identities. */
    suspend fun getUserProfile(): Result<JSONObject> = runCatching {
        if (getToken() == null) throw AuthressException("Not logged in")
        get("/session/profile")
    }.onFailure { logger.error("Authress", "getUserProfile() failed", it) }

    /** MFA devices registered to the current user; empty (not an error) if none exist or the user is signed out. */
    suspend fun getDevices(): Result<List<Device>> = runCatching {
        if (getToken() == null) return@runCatching emptyList()
        val response = try {
            get("/session/devices")
        } catch (e: AuthressException) {
            if (e.status == 401 || e.status == 404) return@runCatching emptyList()
            throw e
        }
        val devices = response.optJSONArray("devices") ?: JSONArray()
        (0 until devices.length()).map { index ->
            val device = devices.getJSONObject(index)
            Device(deviceId = device.getString("deviceId"), name = device.optString("name"))
        }
    }.onFailure { logger.error("Authress", "getDevices() failed", it) }

    /** Removes an MFA device from the current user's profile. */
    suspend fun deleteDevice(deviceId: String): Result<Unit> = runCatching {
        delete("/session/devices/$deviceId")
        Unit
    }.onFailure { logger.error("Authress", "deleteDevice() failed", it) }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private suspend fun get(path: String): JSONObject =
        execute(Request.Builder().url(loginUrl + path).get())

    private suspend fun post(path: String, body: JSONObject): JSONObject =
        execute(Request.Builder().url(loginUrl + path).post(body.toBody()))

    private suspend fun patch(path: String, body: JSONObject): JSONObject =
        execute(Request.Builder().url(loginUrl + path).patch(body.toBody()))

    private suspend fun delete(path: String): JSONObject =
        execute(Request.Builder().url(loginUrl + path).delete())

    private fun JSONObject.toBody() = toString().toRequestBody(JSON)

    /**
     * The hash is `v2;timestamp;fineTuner;hash` — fineTuner is the proof-of-work
     * iteration count, the concrete number that tells us whether a slow sign-in is
     * this device's CPU grinding through the search versus network/browser time.
     */
    private fun logAntiAbuseHash(antiAbuseHash: String, elapsedMs: Long) {
        val iterations = antiAbuseHash.split(";").getOrNull(2) ?: "?"
        logger.info("Authress", "anti-abuse hash computed in ${elapsedMs}ms ($iterations iterations)")
    }

    private suspend fun execute(builder: Request.Builder): JSONObject = withContext(Dispatchers.IO) {
        val request = builder
            .header("Content-Type", "application/json")
            .header("X-Powered-By", "Authress Login SDK; Android; ${BuildConfig.VERSION_NAME}")
            .header("Origin", requestOrigin)
            .build()

        val startedAt = System.currentTimeMillis()
        logger.info("Authress", "-> ${request.method} ${request.url.encodedPath}")

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            logger.warn("Authress", "${request.method} ${request.url.encodedPath} network failure after ${elapsedMs}ms", e)
            throw e
        }

        response.use {
            val elapsedMs = System.currentTimeMillis() - startedAt
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                // The URL and response body alone aren't enough to diagnose most
                // Authress failures (e.g. a rejected antiAbuseHash or a malformed
                // field) — the request headers and body sent are what's actually
                // under suspicion, so include them too. `request` here is the
                // pre-CookieJar Request this client built, so it never carries the
                // session Cookie header that OkHttp's cookie jar adds later.
                val requestHeaders = request.headers.joinToString("; ") { (name, value) -> "$name=$value" }
                val requestBody = request.bodyText()
                val detail = "request headers: $requestHeaders\nrequest body: $requestBody\nresponse body: $text"
                logger.warn("Authress", "<- ${request.method} ${request.url.encodedPath} failed: ${it.code} in ${elapsedMs}ms\n$detail")
                throw AuthressException(
                    "Authress ${request.method} ${request.url.encodedPath} failed: ${it.code}\n$detail",
                    status = it.code,
                )
            }
            logger.info("Authress", "<- ${request.method} ${request.url.encodedPath} ${it.code} in ${elapsedMs}ms")
            runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        }
    }

    /** Reads the request body without consuming it — RequestBody.writeTo() can be called repeatedly on OkHttp's buffer-backed bodies. */
    private fun Request.bodyText(): String {
        val requestBody = body ?: return ""
        return try {
            val buffer = okio.Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: IOException) {
            "<unavailable: ${e.message}>"
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
