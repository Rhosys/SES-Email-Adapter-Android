package ch.rhosys.email.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Authress session token to API calls.
 *
 * Deliberately synchronous and non-blocking: [tokenProvider] reads whatever
 * token is cached right now (see [ch.rhosys.email.data.auth.AuthressLoginClient.getToken])
 * and nothing more. This interceptor runs on OkHttp's dispatcher for every
 * request — it is not the right place to await a session that only a
 * foreground, user-driven flow (the login screen, waiting on the browser) can
 * establish. An earlier version called the suspend `waitForToken()` here via
 * `runBlocking`, which meant an ordinary API call could sit blocked on a pool
 * thread for its full timeout waiting on unrelated browser-driven login —
 * exactly the kind of stall this class exists to attach a token quickly, not
 * cause. If no token is cached, the request goes out without one and the
 * caller sees the resulting 401 like any other API error; ensuring a session
 * is ready is [ch.rhosys.email.presentation.navigation.AppNavHost]'s job via
 * `userIsLoggedIn()`, not this interceptor's.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}
