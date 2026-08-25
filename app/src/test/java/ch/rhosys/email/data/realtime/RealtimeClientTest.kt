package ch.rhosys.email.data.realtime

import ch.rhosys.email.data.auth.AuthressLoginClient
import ch.rhosys.email.data.log.AppLogger
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [RealtimeClient] is the WebSocket half of the same choke point
 * ([AuthressLoginClient.waitForToken]) whose token-expiry bug produced the
 * 403 storm this branch fixes — these tests pin down that it actually
 * attaches the token/account to the handshake, and that it recovers from a
 * rejected handshake (the exact "Expected HTTP 101 ... 403 Forbidden"
 * symptom from production) instead of getting stuck.
 *
 * Real time is used throughout rather than a virtual-time test dispatcher:
 * [RealtimeClient] owns its own `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
 * rather than accepting an injected one, so its `delay()` calls (ping
 * interval, reconnect backoff) are not reachable from a test's virtual clock.
 */
class RealtimeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        httpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.SECONDS).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun wsBaseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun mockAuthManager(token: String?): AuthressLoginClient {
        val authManager = mockk<AuthressLoginClient>()
        coEvery { authManager.waitForToken() } returns token
        return authManager
    }

    /** Records the server-side handshake and lets the test push messages down after it opens. */
    private class RecordingServerListener : WebSocketListener() {
        val openLatch = CountDownLatch(1)
        val socket = AtomicReference<WebSocket>()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket.set(webSocket)
            openLatch.countDown()
        }
    }

    private fun buildClient(
        authManager: AuthressLoginClient,
        onThreadUpdated: suspend (accountId: String, threadId: String) -> Unit = { _, _ -> },
    ) = RealtimeClient(
        wsBaseUrl = wsBaseUrl(),
        httpClient = httpClient,
        authManager = authManager,
        logger = mockk<AppLogger>(relaxed = true),
        onThreadUpdated = onThreadUpdated,
    )

    @Test
    fun `connect attaches the token and accountId as handshake query params`() {
        val serverListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val client = buildClient(mockAuthManager("tok-1"))

        client.start("acc-1")
        assertTrue(serverListener.openLatch.await(5, TimeUnit.SECONDS))

        val request = server.takeRequest()
        assertEquals("token=tok-1&accountId=acc-1", request.requestUrl!!.query)

        client.stop()
    }

    @Test
    fun `an empty token from a failed waitForToken still connects rather than never trying`() {
        val serverListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val client = buildClient(mockAuthManager(null))

        client.start("acc-1")
        assertTrue(serverListener.openLatch.await(5, TimeUnit.SECONDS))

        val request = server.takeRequest()
        assertEquals("token=&accountId=acc-1", request.requestUrl!!.query)

        client.stop()
    }

    @Test
    fun `a rejected handshake (403) is retried and eventually succeeds`() {
        // First attempt: the server rejects the WebSocket upgrade outright —
        // OkHttp surfaces this as onFailure with "Expected HTTP 101 ... 403",
        // the exact symptom from production. Second attempt succeeds.
        server.enqueue(MockResponse().setResponseCode(403))
        val serverListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val client = buildClient(mockAuthManager("tok-1"))

        client.start("acc-1")
        // INITIAL_RECONNECT_DELAY_MS is a hardcoded 1s, not injectable — allow
        // enough real time for the backoff to fire once.
        assertTrue(serverListener.openLatch.await(5, TimeUnit.SECONDS))

        assertEquals(2, server.requestCount)
        client.stop()
    }

    @Test
    fun `switching accounts closes the old socket and reconnects under the new one`() {
        val firstServerListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(firstServerListener))
        val secondServerListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(secondServerListener))
        val client = buildClient(mockAuthManager("tok-1"))

        client.start("acc-1")
        assertTrue(firstServerListener.openLatch.await(5, TimeUnit.SECONDS))
        server.takeRequest() // acc-1's handshake

        client.start("acc-2")
        assertTrue(secondServerListener.openLatch.await(5, TimeUnit.SECONDS))

        val secondRequest = server.takeRequest()
        assertEquals("token=tok-1&accountId=acc-2", secondRequest.requestUrl!!.query)

        client.stop()
    }

    @Test
    fun `starting again for the same already-connected account is a no-op`() {
        val serverListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val client = buildClient(mockAuthManager("tok-1"))

        client.start("acc-1")
        assertTrue(serverListener.openLatch.await(5, TimeUnit.SECONDS))

        client.start("acc-1")

        // No second handshake was ever enqueued/consumed; a second connect
        // attempt here would deadlock waiting on a response that isn't there,
        // so reaching this line at all proves the no-op held.
        assertEquals(1, server.requestCount)
        client.stop()
    }

    @Test
    fun `a thread updated message invokes the callback with the current account and thread id`() {
        val serverListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        val received = CountDownLatch(1)
        val seenAccountId = AtomicReference<String>()
        val seenThreadId = AtomicReference<String>()
        val client = buildClient(mockAuthManager("tok-1")) { accountId, threadId ->
            seenAccountId.set(accountId)
            seenThreadId.set(threadId)
            received.countDown()
        }

        client.start("acc-1")
        assertTrue(serverListener.openLatch.await(5, TimeUnit.SECONDS))
        serverListener.socket.get().send("""{"type":"thread:updated","threadId":"thr-1"}""")

        assertTrue(received.await(5, TimeUnit.SECONDS))
        assertEquals("acc-1", seenAccountId.get())
        assertEquals("thr-1", seenThreadId.get())

        client.stop()
    }

    @Test
    fun `stop closes the socket and start after stop reconnects`() {
        val firstServerListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(firstServerListener))
        val client = buildClient(mockAuthManager("tok-1"))

        client.start("acc-1")
        assertTrue(firstServerListener.openLatch.await(5, TimeUnit.SECONDS))
        client.stop()

        val secondServerListener = RecordingServerListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(secondServerListener))
        client.start("acc-1")

        assertTrue(secondServerListener.openLatch.await(5, TimeUnit.SECONDS))
        assertEquals(2, server.requestCount)
        client.stop()
    }
}
