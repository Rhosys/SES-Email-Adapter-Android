package ch.rhosys.email.data.remote.api

import ch.rhosys.email.data.auth.AuthressLoginClient
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Authress session token to API calls. The token comes from the
 * `authorization` cookie rather than a stored access token, which is where the
 * login SDK keeps it.
 *
 * [tokenProvider] is a lambda because the login client is constructed after the
 * OkHttp client it shares.
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
