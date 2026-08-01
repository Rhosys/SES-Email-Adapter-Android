package ch.rhosys.email.data.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import ch.rhosys.email.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
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
 * OIDC login against Authress (decision #6): social logins, passkeys, or
 * email/password are all handled by Authress's hosted login page — the app
 * only speaks standard OAuth2/OIDC via AppAuth, so no credential UI lives here.
 */
class AuthressAuthManager(private val context: Context, private val tokenStore: TokenStore) {

    private val service = AuthorizationService(context)

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}/authorize"),
        Uri.parse("https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}/oauth/token"),
    )

    private val redirectUri = Uri.parse("${BuildConfig.OAUTH_REDIRECT_SCHEME}:/oauth2redirect")

    fun buildAuthRequestIntent() = service.getAuthorizationRequestIntent(
        AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.AUTHRESS_APPLICATION_ID,
            ResponseTypeValues.CODE,
            redirectUri,
        ).setScope("openid profile email offline_access").build(),
    )

    fun launchSignIn(launcher: ActivityResultLauncher<android.content.Intent>) {
        launcher.launch(buildAuthRequestIntent())
    }

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
        val authState = AuthState(serviceConfig)
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(
                net.openid.appauth.TokenRequest.Builder(serviceConfig, BuildConfig.AUTHRESS_APPLICATION_ID)
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
