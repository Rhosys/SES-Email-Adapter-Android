package ch.rhosys.email.data.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import ch.rhosys.email.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OIDC login against Authress. Social logins, passkeys and email/password are all
 * handled by Authress's hosted login page, so no credential UI lives here.
 *
 * Endpoints are discovered from the issuer's /.well-known/openid-configuration
 * rather than hardcoded. The previous hardcoded pair was wrong on both counts —
 * Authress's authorization endpoint is the issuer root, not /authorize, and its
 * token endpoint is /api/authentication/oauth/tokens, not /oauth/token — and
 * discovery means a future change on Authress's side does not silently break
 * sign-in again.
 */
class AuthressAuthManager(private val context: Context, private val tokenStore: TokenStore) {

    private val service = AuthorizationService(context)

    private val issuer = Uri.parse("https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}")

    private val redirectUri = Uri.parse("${BuildConfig.OAUTH_REDIRECT_SCHEME}:/oauth2redirect")

    /** Discovered once per process, then reused. */
    @Volatile
    private var cachedConfig: AuthorizationServiceConfiguration? = null

    private val configMutex = Mutex()

    private suspend fun serviceConfig(): AuthorizationServiceConfiguration {
        cachedConfig?.let { return it }
        return configMutex.withLock {
            cachedConfig ?: fetchConfig().also { cachedConfig = it }
        }
    }

    private suspend fun fetchConfig(): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(issuer) { config, ex ->
                when {
                    config != null -> cont.resume(config)
                    else -> cont.resumeWithException(
                        ex ?: IllegalStateException("Could not discover OIDC configuration at $issuer"),
                    )
                }
            }
        }

    suspend fun buildAuthRequestIntent(): android.content.Intent =
        service.getAuthorizationRequestIntent(
            AuthorizationRequest.Builder(
                serviceConfig(),
                BuildConfig.AUTHRESS_APPLICATION_ID,
                ResponseTypeValues.CODE,
                redirectUri,
            )
                // Only openid and profile are advertised in scopes_supported.
                // Refresh tokens come from the refresh_token grant, which is
                // advertised, rather than from an offline_access scope that is not.
                .setScope("openid profile")
                .build(),
        )

    /** Discovery is a network call, so signing in has to suspend. */
    suspend fun launchSignIn(launcher: ActivityResultLauncher<android.content.Intent>): Result<Unit> =
        runCatching { launcher.launch(buildAuthRequestIntent()) }

    suspend fun handleAuthResponse(data: android.content.Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        if (response == null) return Result.failure(exception ?: IllegalStateException("Sign-in cancelled"))

        return runCatching {
            val tokenResponse = exchangeToken(response)
            tokenStore.accessToken = tokenResponse.accessToken
            tokenStore.refreshToken = tokenResponse.refreshToken
            tokenStore.accessTokenExpiresAt = tokenResponse.accessTokenExpirationTime ?: 0L
        }
    }

    private suspend fun exchangeToken(response: AuthorizationResponse): TokenResponse =
        suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
                when {
                    tokenResponse != null -> cont.resume(tokenResponse)
                    ex != null -> cont.resumeWithException(ex)
                    else -> cont.resumeWithException(IllegalStateException("Token exchange failed"))
                }
            }
        }

    suspend fun refreshAccessToken(): Boolean {
        val refreshToken = tokenStore.refreshToken ?: return false
        val config = runCatching { serviceConfig() }.getOrNull() ?: return false
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(
                net.openid.appauth.TokenRequest.Builder(config, BuildConfig.AUTHRESS_APPLICATION_ID)
                    .setGrantType(net.openid.appauth.GrantTypeValues.REFRESH_TOKEN)
                    .setRefreshToken(refreshToken)
                    .build(),
            ) { tokenResponse, ex ->
                if (tokenResponse != null) {
                    tokenStore.accessToken = tokenResponse.accessToken
                    tokenResponse.refreshToken?.let { tokenStore.refreshToken = it }
                    tokenStore.accessTokenExpiresAt = tokenResponse.accessTokenExpirationTime ?: 0L
                    cont.resume(true)
                } else {
                    cont.resume(false)
                }
            }
        }
    }

    fun signOut() {
        tokenStore.clear()
    }

    fun dispose() {
        service.dispose()
    }
}
