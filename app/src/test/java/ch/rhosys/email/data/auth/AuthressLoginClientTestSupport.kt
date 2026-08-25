package ch.rhosys.email.data.auth

import android.content.Context
import ch.rhosys.email.BuildConfig
import ch.rhosys.email.data.log.AppLogger
import ch.rhosys.email.testutil.RedirectToMockServerInterceptor
import io.mockk.Runs
import io.mockk.captureNullable
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.secondArg
import io.mockk.slot
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

/** Origin every test JWT's `iss` claim must match — mirrors `AuthressLoginClient.origin`. */
const val TEST_ORIGIN = "https://${BuildConfig.AUTHRESS_CUSTOM_DOMAIN}"

/**
 * In-memory stand-in for the two cookies [AuthressCookieJar] persists
 * (encrypted, on real devices). Mirrors its actual save/read/clear semantics —
 * including "an expiry in the past is a deletion" from `saveFromResponse` —
 * closely enough that a PATCH /session response with a `Set-Cookie:
 * authorization=...` header really does change what [AuthressLoginClient.getToken]
 * sees next, the same way it would in production.
 */
class FakeCookieJarBacking {
    val cookies = mutableMapOf<String, String>()
    var backupCalls = 0
        private set
    var clearCalls = 0
        private set

    fun recordBackup() {
        backupCalls++
    }

    fun recordClear() {
        clearCalls++
        cookies.clear()
    }
}

fun mockCookieJar(backing: FakeCookieJarBacking): AuthressCookieJar {
    val jar = mockk<AuthressCookieJar>()
    every { jar.authorizationCookie() } answers { backing.cookies["authorization"] }
    every { jar.userCookie() } answers { backing.cookies["user"] }
    every { jar.saveFromResponse(any(), any()) } answers {
        val cookieList = secondArg<List<Cookie>>()
        cookieList.forEach { cookie ->
            if (cookie.expiresAt < System.currentTimeMillis()) {
                backing.cookies.remove(cookie.name)
            } else {
                backing.cookies[cookie.name] = cookie.value
            }
        }
    }
    every { jar.loadForRequest(any()) } returns emptyList()
    every { jar.backupCookies() } answers { backing.recordBackup() }
    every { jar.restoreCookies() } just Runs
    every { jar.clear() } answers { backing.recordClear() }
    return jar
}

/** In-memory stand-in for [AuthStorageManager]'s encrypted pending-auth-request slot. */
class FakeStorageBacking {
    var pending: AuthStorageManager.PendingAuthentication? = null
    var clearCalls = 0
        private set
}

fun mockStorage(backing: FakeStorageBacking): AuthStorageManager {
    val storage = mockk<AuthStorageManager>()
    val stateSlot = slot<AuthStorageManager.PendingAuthentication?>()
    every { storage.setAuthenticationRequest(captureNullable(stateSlot)) } answers { backing.pending = stateSlot.captured }
    every { storage.getAuthenticationRequest() } answers { backing.pending }
    every { storage.clear() } answers { backing.clearCalls++; backing.pending = null }
    return storage
}

fun mockLogger(): AppLogger = mockk(relaxed = true)

/**
 * A client wired to [server] via [RedirectToMockServerInterceptor], so requests
 * still address the real `AUTHRESS_CUSTOM_DOMAIN` host (keeping `iss` checks
 * consistent with production) while physically landing on the local mock
 * server. [readTimeoutMillis] lets a couple of tests use a short OkHttp
 * read timeout to deterministically simulate a hung/slow server, since a
 * synchronous `Call.execute()` inside `withContext(Dispatchers.IO)` is not
 * itself interruptible by coroutine cancellation.
 */
fun testAuthressLoginClient(
    context: Context,
    server: MockWebServer,
    cookieJar: AuthressCookieJar,
    storage: AuthStorageManager,
    logger: AppLogger = mockLogger(),
    readTimeoutMillis: Long = 5_000,
): AuthressLoginClient {
    val httpClient = OkHttpClient.Builder()
        .addInterceptor(RedirectToMockServerInterceptor(server.url("/")))
        .connectTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()
    return AuthressLoginClient(context, cookieJar, httpClient, logger, storage)
}

fun MockWebServer.baseUrl(): HttpUrl = url("/")
