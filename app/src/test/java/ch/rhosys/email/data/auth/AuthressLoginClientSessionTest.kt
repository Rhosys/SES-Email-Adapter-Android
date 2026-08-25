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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers everything past initial sign-in: [AuthressLoginClient.logout],
 * [AuthressLoginClient.linkIdentity], [AuthressLoginClient.getUserProfile],
 * [AuthressLoginClient.getDevices] and [AuthressLoginClient.deleteDevice].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthressLoginClientSessionTest {

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

    private fun signIn() {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
    }

    // ── logout() ─────────────────────────────────────────────────────────

    @Test
    fun `logout deletes the server session and clears local state`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = client.logout()

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/session", request.path)
        assertEquals(1, cookieBacking.clearCalls)
        assertEquals(1, storageBacking.clearCalls)
        assertFalse(client.sessionEstablished.value)
        assertNull(client.getToken())
    }

    @Test
    fun `logout still clears local state when the server delete call fails`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = client.logout()

        assertTrue(result.isSuccess)
        assertEquals(1, cookieBacking.clearCalls)
        assertEquals(1, storageBacking.clearCalls)
    }

    @Test
    fun `logout still clears local state when the server is unreachable`() = runBlocking {
        signIn()
        server.shutdown()

        val result = client.logout()

        assertTrue(result.isSuccess)
        assertEquals(1, cookieBacking.clearCalls)
        assertEquals(1, storageBacking.clearCalls)
    }

    // ── linkIdentity() ───────────────────────────────────────────────────

    @Test
    fun `linkIdentity fails when neither connectionId nor tenantLookupIdentifier is given`() = runBlocking {
        signIn()

        val result = client.linkIdentity()

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `linkIdentity fails when not signed in`() = runBlocking {
        val result = client.linkIdentity(connectionId = "conn-1")

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `linkIdentity posts linkIdentity true and stores the new pending request`() = runBlocking {
        signIn()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject()
                    .put("authenticationUrl", "https://login.rhosys.cloud/link")
                    .put("authenticationRequestId", "link-req-1")
                    .toString(),
            ),
        )

        val result = client.linkIdentity(connectionId = "conn-1")

        assertTrue(result.isSuccess)
        assertEquals("link-req-1", result.getOrNull()?.authenticationRequestId)
        assertEquals("link-req-1", storageBacking.pending?.authenticationRequestId)

        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.getBoolean("linkIdentity"))
        assertEquals("conn-1", body.getString("connectionId"))
    }

    // ── getUserProfile() ─────────────────────────────────────────────────

    @Test
    fun `getUserProfile fails when not signed in`() = runBlocking {
        val result = client.getUserProfile()

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `getUserProfile returns the profile payload when signed in`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Alex"}"""))

        val result = client.getUserProfile()

        assertTrue(result.isSuccess)
        assertEquals("Alex", result.getOrNull()?.getString("name"))
        assertEquals("/api/session/profile", server.takeRequest().path)
    }

    // ── getDevices() ─────────────────────────────────────────────────────

    @Test
    fun `getDevices returns empty without a network call when not signed in`() = runBlocking {
        val result = client.getDevices()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().isNullOrEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `getDevices returns empty on a 401`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(401))

        val result = client.getDevices()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().isNullOrEmpty())
    }

    @Test
    fun `getDevices returns empty on a 404`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.getDevices()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().isNullOrEmpty())
    }

    @Test
    fun `getDevices surfaces a genuine server error`() = runBlocking {
        signIn()
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = client.getDevices()

        assertTrue(result.isFailure)
    }

    @Test
    fun `getDevices parses the device list on success`() = runBlocking {
        signIn()
        val devices = JSONArray()
            .put(JSONObject().put("deviceId", "dev-1").put("name", "Pixel"))
            .put(JSONObject().put("deviceId", "dev-2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(JSONObject().put("devices", devices).toString()))

        val result = client.getDevices()

        assertTrue(result.isSuccess)
        val list = result.getOrNull()!!
        assertEquals(2, list.size)
        assertEquals("dev-1", list[0].deviceId)
        assertEquals("Pixel", list[0].name)
        assertEquals("dev-2", list[1].deviceId)
        assertEquals("", list[1].name)
    }

    // ── deleteDevice() ───────────────────────────────────────────────────

    @Test
    fun `deleteDevice calls the device-scoped delete endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = client.deleteDevice("dev-1")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/session/devices/dev-1", request.path)
    }

    @Test
    fun `deleteDevice surfaces a server failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = client.deleteDevice("dev-1")

        assertTrue(result.isFailure)
    }
}
