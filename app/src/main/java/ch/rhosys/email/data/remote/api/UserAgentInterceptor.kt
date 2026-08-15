package ch.rhosys.email.data.remote.api

import ch.rhosys.email.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps a real User-Agent on every request. Installed on both OkHttpClients
 * built in [ch.rhosys.email.di.AppContainer] — the Email API client and the
 * Authress-only client — instead of leaving OkHttp's default `okhttp/<version>`.
 */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "Numaeel-Android/${BuildConfig.VERSION_NAME}")
            .build()
        return chain.proceed(request)
    }
}
