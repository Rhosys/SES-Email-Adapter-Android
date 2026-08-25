package ch.rhosys.email.data.remote.api

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * [AuthInterceptor] is the one place (besides [ch.rhosys.email.data.realtime.RealtimeClient])
 * that attaches auth to an outgoing Email API call — every case here is
 * exercised through a real [OkHttpClient] call against a [MockWebServer], not
 * just by invoking `intercept()` directly, so it also proves the header
 * really reaches the wire.
 */
class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWith(tokenProvider: suspend () -> String?): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenProvider)).build()

    @Test
    fun `attaches a Bearer header when a token is available`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWith { "token-123" }

        client.newCall(Request.Builder().url(server.url("/threads")).build()).execute().close()

        val request = server.takeRequest()
        assertEquals("Bearer token-123", request.getHeader("Authorization"))
    }

    @Test
    fun `sends no Authorization header when the token provider returns null`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWith { null }

        client.newCall(Request.Builder().url(server.url("/threads")).build()).execute().close()

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `waits for a suspending token provider before sending the request`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = clientWith {
            delay(50)
            "delayed-token"
        }

        client.newCall(Request.Builder().url(server.url("/threads")).build()).execute().close()

        assertEquals("Bearer delayed-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `propagates a token provider failure as a failed call, without ever reaching the server`() {
        val client = clientWith { throw IllegalStateException("token refresh exploded") }

        assertThrows(IllegalStateException::class.java) {
            client.newCall(Request.Builder().url(server.url("/threads")).build()).execute()
        }
        assertEquals(0, server.requestCount)
    }
}
