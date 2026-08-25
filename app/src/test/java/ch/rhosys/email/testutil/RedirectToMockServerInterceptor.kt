package ch.rhosys.email.testutil

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * [AuthressLoginClient][ch.rhosys.email.data.auth.AuthressLoginClient] builds
 * its request URLs from a fixed `BuildConfig.AUTHRESS_CUSTOM_DOMAIN` host, not
 * an injectable base URL — matching production, where that host never
 * changes at runtime. Rather than adding test-only seams to production code,
 * this interceptor keeps the client pointed at the real host end to end (so
 * `iss` checks against `origin` still line up) and only swaps the physical
 * scheme/host/port onto a local [okhttp3.mockwebserver.MockWebServer] right
 * before the request leaves the process.
 */
class RedirectToMockServerInterceptor(private val target: HttpUrl) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val redirected = original.url.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()
        return chain.proceed(original.newBuilder().url(redirected).build())
    }
}
