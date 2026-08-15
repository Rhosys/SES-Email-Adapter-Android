package ch.rhosys.email.data.remote.api

import ch.rhosys.email.data.log.AppLogger
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Logs every request through the shared OkHttpClient — method, path, status,
 * duration, and response size — to [AppLogger]. `HttpLoggingInterceptor` only
 * runs in debug builds; this is the production-visible equivalent, so a slow
 * or failing Email API call (mailbox load, sync, etc.) shows up in the same
 * log a user can review in Settings > Logs or the onboarding/login overlay,
 * not just Authress calls.
 */
class ApiLoggingInterceptor(private val logger: AppLogger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.currentTimeMillis()

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            val elapsedMs = System.currentTimeMillis() - startedAt
            logger.warn("Api", "${request.method} ${request.url.encodedPath} network failure after ${elapsedMs}ms", e)
            throw e
        }

        val elapsedMs = System.currentTimeMillis() - startedAt
        val contentLength = response.body?.contentLength()?.takeIf { it >= 0 }
        val sizeSuffix = contentLength?.let { ", ${it}B" }.orEmpty()
        if (!response.isSuccessful) {
            logger.warn("Api", "${request.method} ${request.url.encodedPath} failed: ${response.code} in ${elapsedMs}ms$sizeSuffix")
        } else {
            logger.info("Api", "${request.method} ${request.url.encodedPath} -> ${response.code} in ${elapsedMs}ms$sizeSuffix")
        }
        return response
    }
}
