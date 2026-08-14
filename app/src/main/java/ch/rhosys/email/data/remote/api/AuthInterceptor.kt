package ch.rhosys.email.data.remote.api

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Authress session token to API calls.
 *
 * The token comes from the login client's waitForToken, which is what the SDK
 * documents for an Authorization header: it returns immediately when a valid
 * session exists, and otherwise waits briefly for one being established rather
 * than firing a request that is certain to be rejected.
 *
 * runBlocking is safe here — OkHttp interceptors already run on a background
 * dispatcher, never the main thread.
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
