package ch.rhosys.email.data.auth

import androidx.test.core.app.ApplicationProvider
import ch.rhosys.email.testutil.testJwt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Covers [AuthressLoginClient.getToken], [AuthressLoginClient.getUserIdentity],
 * [AuthressLoginClient.userIsLoggedIn] and [AuthressLoginClient.waitForToken] —
 * the exact surface behind the 403 bug fixed alongside these tests (an expired
 * cached token being reused instead of triggering a refresh). Every success
 * and failure mode of the local expiry check and the PATCH /session refresh
 * path is exercised here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthressLoginClientTokenTest {

    private lateinit var server: MockWebServer
    private lateinit var cookieBacking: FakeCookieJarBacking
    private lateinit var storageBacking: FakeStorageBacking
    private lateinit var client: AuthressLoginClient

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { start() }
        cookieBacking = FakeCookieJarBacking()
        storageBacking = FakeStorageBacking()
        client = testAuthressLoginClient(
            context = ApplicationProvider.getApplicationContext(),
            server = server,
            cookieJar = mockCookieJar(cookieBacking),
            storage = mockStorage(storageBacking),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    // ── getToken() ────────────────────────────────────────────────────────

    @Test
    fun `getToken returns null when there is no cached cookie`() {
        assertNull(client.getToken())
    }

    @Test
    fun `getToken returns null for a malformed token`() {
        cookieBacking.cookies["authorization"] = "not-a-jwt"
        assertNull(client.getToken())
    }

    @Test
    fun `getToken returns null when the issuer does not match`() {
        cookieBacking.cookies["authorization"] = testJwt("https://someone-else.example", secondsFromNow = 3600)
        assertNull(client.getToken())
    }

    @Test
    fun `getToken returns null for an already-expired token`() {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -3600)
        assertNull(client.getToken())
    }

    @Test
    fun `getToken returns null for a token inside the 10s clock-skew buffer`() {
        // JwtManager.decode shortens exp by 10s; a token expiring 5s from now is
        // therefore already treated as expired.
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = 5)
        assertNull(client.getToken())
    }

    @Test
    fun `getToken returns the token when valid and not yet expired`() {
        val token = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        cookieBacking.cookies["authorization"] = token
        assertEquals(token, client.getToken())
    }

    @Test
    fun `getToken tolerates a token with no exp claim at all`() {
        val token = testJwt(JSONObject().put("iss", TEST_ORIGIN).put("sub", "user-1"))
        cookieBacking.cookies["authorization"] = token
        assertEquals(token, client.getToken())
    }

    // ── getUserIdentity() ────────────────────────────────────────────────

    @Test
    fun `getUserIdentity returns null when there is no user cookie`() {
        assertNull(client.getUserIdentity())
    }

    @Test
    fun `getUserIdentity returns null when the issuer does not match`() {
        cookieBacking.cookies["user"] = testJwt("https://someone-else.example", secondsFromNow = 3600)
        assertNull(client.getUserIdentity())
    }

    @Test
    fun `getUserIdentity returns claims for a valid identity token, even if expired`() {
        // Unlike getToken, getUserIdentity never checks exp — it's shown to the
        // user as "who is signed in", not used to authorize a request.
        val token = testJwt(
            JSONObject().put("iss", TEST_ORIGIN).put("sub", "user-1").put("exp", 1),
        )
        cookieBacking.cookies["user"] = token
        val identity = client.getUserIdentity()
        assertNotNull(identity)
        assertEquals("user-1", identity!!.getString("sub"))
    }

    // ── userIsLoggedIn() ─────────────────────────────────────────────────

    @Test
    fun `userIsLoggedIn returns true without a network call when a valid token is cached`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = 3600)

        assertTrue(client.userIsLoggedIn())

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `userIsLoggedIn refreshes via PATCH slash session when the cached token is expired`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        val freshToken = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "authorization=$freshToken; Path=/; HttpOnly; Secure")
                .setBody("{}"),
        )

        assertTrue(client.userIsLoggedIn())

        val request = server.takeRequest(1, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("PATCH", request!!.method)
        assertEquals("/api/session", request.path)
        assertEquals(freshToken, client.getToken())
        assertTrue(client.sessionEstablished.value)
    }

    @Test
    fun `userIsLoggedIn refreshes when there is no cookie at all yet`() = runBlocking {
        val freshToken = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "authorization=$freshToken; Path=/; HttpOnly; Secure")
                .setBody("{}"),
        )

        assertTrue(client.userIsLoggedIn())
        assertEquals(freshToken, client.getToken())
    }

    @Test
    fun `userIsLoggedIn returns false when the server rejects the refresh`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid session\"}"))

        assertFalse(client.userIsLoggedIn())
        assertNull(client.getToken())
        assertFalse(client.sessionEstablished.value)
    }

    @Test
    fun `userIsLoggedIn returns false on a 500 from session refresh`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertFalse(client.userIsLoggedIn())
    }

    @Test
    fun `userIsLoggedIn returns false on a network failure talking to the server`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        server.shutdown()

        assertFalse(client.userIsLoggedIn())
    }

    @Test
    fun `userIsLoggedIn returns false when the refresh succeeds but the new cookie is still expired`() = runBlocking {
        // Pathological but should not be reported as logged in: the server
        // handed back a cookie that is already expired by our clock.
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        val staleReplacement = testJwt(TEST_ORIGIN, secondsFromNow = -1)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "authorization=$staleReplacement; Path=/; HttpOnly; Secure")
                .setBody("{}"),
        )

        assertFalse(client.userIsLoggedIn())
    }

    // ── waitForToken() ───────────────────────────────────────────────────

    @Test
    fun `waitForToken returns the cached token immediately without a network call`() = runBlocking {
        val token = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        cookieBacking.cookies["authorization"] = token

        assertEquals(token, client.waitForToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `waitForToken with a zero timeout returns null immediately without refreshing`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)

        assertNull(client.waitForToken(timeoutInMillis = 0))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `waitForToken refreshes an expired token and returns the new one`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        val freshToken = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "authorization=$freshToken; Path=/; HttpOnly; Secure")
                .setBody("{}"),
        )

        assertEquals(freshToken, client.waitForToken())
    }

    @Test
    fun `waitForToken returns null promptly when the refresh is rejected, without waiting out the full timeout`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        server.enqueue(MockResponse().setResponseCode(401))

        val startedAt = System.currentTimeMillis()
        val result = client.waitForToken(timeoutInMillis = 5_000)
        val elapsedMs = System.currentTimeMillis() - startedAt

        assertNull(result)
        assertTrue("expected a fast failure, took ${elapsedMs}ms", elapsedMs < 2_000)
    }

    @Test
    fun `waitForToken on a hung server fails within OkHttp's own read timeout, bounding wall time`() = runBlocking {
        // withTimeoutOrNull cannot interrupt a synchronous OkHttp Call.execute()
        // mid-flight, so what actually bounds a hung PATCH /session here is the
        // client's own read timeout, not waitForToken's timeoutInMillis. This
        // pins down that real behavior rather than the parameter's name.
        val shortTimeoutClient = testAuthressLoginClient(
            context = ApplicationProvider.getApplicationContext(),
            server = server,
            cookieJar = mockCookieJar(cookieBacking),
            storage = mockStorage(storageBacking),
            readTimeoutMillis = 300,
        )
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = -60)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setHeadersDelay(5, TimeUnit.SECONDS),
        )

        val startedAt = System.currentTimeMillis()
        val result = shortTimeoutClient.waitForToken(timeoutInMillis = 10_000)
        val elapsedMs = System.currentTimeMillis() - startedAt

        assertNull(result)
        assertTrue("expected the 300ms read timeout to fire, took ${elapsedMs}ms", elapsedMs < 2_000)
    }
}
