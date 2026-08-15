package ch.rhosys.email.data.remote.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Authress session token to Email API calls — the one place that
 * should ever call [ch.rhosys.email.data.auth.AuthressLoginClient.waitForToken]:
 * this is the HTTP call wrapper, grabbing the token right before the request
 * that needs it. It returns immediately when a token is already cached, and
 * otherwise waits briefly for one being established rather than firing a
 * request that's certain to be rejected — e.g. a token that expired between
 * route changes, while [AppNavHost][ch.rhosys.email.presentation.navigation.AppNavHost]'s
 * `userIsLoggedIn()` refresh is still in flight.
 *
 * `runBlocking` is safe here — OkHttp interceptors run on OkHttp's own
 * dispatcher, never the main thread. It only stays safe because this
 * interceptor is never installed on Authress's own client: Authress's calls
 * (see `AppContainer.authHttpClient`) don't carry it, since a call like
 * `POST /authentication` is what establishes the session in the first place —
 * waiting on its own result here would deadlock until the timeout, every time.
 */
class AuthInterceptor(private val tokenProvider: suspend () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider() }
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}
