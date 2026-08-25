package ch.rhosys.email.data.remote.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Authress session token to Email API calls — the HTTP call
 * wrapper that grabs the token right before the request that needs it, via
 * [ch.rhosys.email.data.auth.AuthressLoginClient.waitForToken]. (The Email
 * API's WebSocket, [ch.rhosys.email.data.realtime.RealtimeClient], is the
 * other legitimate caller, attaching a token to the connection handshake the
 * same way.) It returns immediately when a token is already cached, and
 * otherwise actively revalidates the session — e.g. a token that expired
 * between route changes — rather than firing a request that's certain to be
 * rejected.
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
