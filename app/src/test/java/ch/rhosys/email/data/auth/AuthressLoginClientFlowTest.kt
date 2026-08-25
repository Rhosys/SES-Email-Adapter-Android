package ch.rhosys.email.data.auth

import android.net.Uri
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

/**
 * Covers the full sign-in lifecycle: [AuthressLoginClient.authenticate],
 * [AuthressLoginClient.completeAuthenticationRequest] and
 * [AuthressLoginClient.isRedirect] — every branch of the PKCE + deep-link
 * dance, including the abandoned-request and duplicate-redirect edge cases
 * the code comments call out as "seen in production".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthressLoginClientFlowTest {

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

    private fun enqueueAuthenticationResponse(authenticationRequestId: String, authenticationUrl: String = "https://login.rhosys.cloud/continue") {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject()
                    .put("authenticationUrl", authenticationUrl)
                    .put("authenticationRequestId", authenticationRequestId)
                    .toString(),
            ),
        )
    }

    private fun enqueueTokenExchangeResponse(freshToken: String, statusCode: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(statusCode)
                .apply { if (statusCode in 200..299) addHeader("Set-Cookie", "authorization=$freshToken; Path=/; HttpOnly; Secure") }
                .setBody("{}"),
        )
    }

    // ── authenticate() ───────────────────────────────────────────────────

    @Test
    fun `authenticate posts PKCE and anti-abuse fields, and ends at AwaitingRedirect`() = runBlocking {
        enqueueAuthenticationResponse("req-1")

        val result = client.authenticate()

        assertTrue(result.isSuccess)
        assertEquals(AuthressLoginClient.AuthStatus.AwaitingRedirect, client.authStatus.value)
        assertNull(client.authError.value)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/authentication", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI, body.getString("redirectUrl"))
        assertEquals(ch.rhosys.email.BuildConfig.AUTHRESS_APPLICATION_ID, body.getString("applicationId"))
        assertEquals("S256", body.getString("codeChallengeMethod"))
        assertTrue(body.getString("codeChallenge").isNotBlank())
        assertTrue(body.getString("antiAbuseHash").startsWith("v2;"))
        // Optional fields all omitted by default.
        assertFalse(body.has("connectionId"))

        assertEquals("req-1", storageBacking.pending?.authenticationRequestId)
    }

    @Test
    fun `authenticate includes every optional field when provided`() = runBlocking {
        enqueueAuthenticationResponse("req-1")

        client.authenticate(
            AuthressLoginClient.AuthenticationOptions(
                connectionId = "conn-1",
                tenantLookupIdentifier = "tenant-1",
                inviteId = "invite-1",
                responseLocation = "somewhere",
                flowType = "flow-x",
                scopes = listOf("scope-a", "scope-b"),
                audiences = listOf("aud-1"),
                connectionProperties = mapOf("k" to "v"),
                multiAccount = true,
            ),
        )

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("conn-1", body.getString("connectionId"))
        assertEquals("tenant-1", body.getString("tenantLookupIdentifier"))
        assertEquals("invite-1", body.getString("inviteId"))
        assertEquals("somewhere", body.getString("responseLocation"))
        assertEquals("flow-x", body.getString("flowType"))
        assertEquals(2, body.getJSONArray("scopes").length())
        assertEquals(1, body.getJSONArray("audiences").length())
        assertEquals("v", body.getJSONObject("connectionProperties").getString("k"))
        assertTrue(body.getBoolean("multiAccount"))
    }

    @Test
    fun `authenticate surfaces a server failure from POST slash authentication`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        val result = client.authenticate()

        assertTrue(result.isFailure)
        assertEquals(AuthressLoginClient.AuthStatus.Idle, client.authStatus.value)
        assertNotNull(client.authError.value)
    }

    @Test
    fun `authenticate re-entering while a previous attempt is in flight abandons it`() = runBlocking {
        enqueueAuthenticationResponse("req-1")
        assertTrue(client.authenticate().isSuccess)
        assertEquals(AuthressLoginClient.AuthStatus.AwaitingRedirect, client.authStatus.value)

        enqueueAuthenticationResponse("req-2")
        assertTrue(client.authenticate().isSuccess)
        assertEquals("req-2", storageBacking.pending?.authenticationRequestId)

        // The stale first Custom Tab finally redirects back with req-1's id — it
        // should be silently dropped, not surfaced as a mismatch error.
        val staleRedirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc&nonce=req-1")
        val result = client.completeAuthenticationRequest(staleRedirect)

        assertTrue(result.isSuccess)
        assertNull(client.authError.value)
        // Untouched by the drop: still whatever the second authenticate() left it at.
        assertEquals(AuthressLoginClient.AuthStatus.AwaitingRedirect, client.authStatus.value)
    }

    // ── completeAuthenticationRequest() ─────────────────────────────────

    @Test
    fun `completeAuthenticationRequest exchanges the code and establishes a session`() = runBlocking {
        enqueueAuthenticationResponse("req-1")
        client.authenticate()
        val freshToken = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        enqueueTokenExchangeResponse(freshToken)

        val redirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc123&nonce=req-1")
        val result = client.completeAuthenticationRequest(redirect)

        assertTrue(result.isSuccess)
        assertEquals(freshToken, client.getToken())
        assertTrue(client.sessionEstablished.value)
        assertEquals(AuthressLoginClient.AuthStatus.Idle, client.authStatus.value)
        assertEquals(1, cookieBacking.backupCalls)
        assertNull(storageBacking.pending)

        server.takeRequest() // the /authentication call
        val tokenRequest = server.takeRequest()
        assertEquals("POST", tokenRequest.method)
        assertEquals("/api/authentication/req-1/tokens", tokenRequest.path)
        val body = JSONObject(tokenRequest.body.readUtf8())
        assertEquals("authorization_code", body.getString("grant_type"))
        assertEquals("abc123", body.getString("code"))
        assertEquals(ch.rhosys.email.BuildConfig.AUTHRESS_APPLICATION_ID, body.getString("client_id"))
    }

    @Test
    fun `completeAuthenticationRequest fails when there is no pending request`() = runBlocking {
        val redirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc&nonce=req-1")

        val result = client.completeAuthenticationRequest(redirect)

        assertTrue(result.isFailure)
        assertEquals("No authentication request in progress (redirect carried authenticationRequestId=req-1)", client.authError.value)
    }

    @Test
    fun `completeAuthenticationRequest fails on a genuine id mismatch`() = runBlocking {
        storageBacking.pending = AuthStorageManager.PendingAuthentication(
            codeVerifier = "verifier",
            authenticationRequestId = "req-A",
            redirectUrl = ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI,
        )

        val redirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc&nonce=req-B")
        val result = client.completeAuthenticationRequest(redirect)

        assertTrue(result.isFailure)
        assertEquals("Authentication request mismatch", client.authError.value)
    }

    @Test
    fun `completeAuthenticationRequest assumes the sole pending request when the redirect carries no id`() = runBlocking {
        storageBacking.pending = AuthStorageManager.PendingAuthentication(
            codeVerifier = "verifier",
            authenticationRequestId = "req-C",
            redirectUrl = ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI,
        )
        val freshToken = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        enqueueTokenExchangeResponse(freshToken)

        val redirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc")
        val result = client.completeAuthenticationRequest(redirect)

        assertTrue(result.isSuccess)
        val tokenRequest = server.takeRequest()
        assertEquals("/api/authentication/req-C/tokens", tokenRequest.path)
    }

    @Test
    fun `completeAuthenticationRequest treats a failed exchange as a harmless duplicate when already signed in`() = runBlocking {
        cookieBacking.cookies["authorization"] = testJwt(TEST_ORIGIN, secondsFromNow = 3600)
        storageBacking.pending = AuthStorageManager.PendingAuthentication(
            codeVerifier = "verifier",
            authenticationRequestId = "req-1",
            redirectUrl = ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI,
        )
        server.enqueue(MockResponse().setResponseCode(409).setBody("duplicate"))

        val redirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc&nonce=req-1")
        val result = client.completeAuthenticationRequest(redirect)

        assertTrue(result.isSuccess)
        assertTrue(client.sessionEstablished.value)
        assertEquals(AuthressLoginClient.AuthStatus.Idle, client.authStatus.value)
        assertNull(storageBacking.pending)
    }

    @Test
    fun `completeAuthenticationRequest surfaces a failed exchange when not already signed in`() = runBlocking {
        storageBacking.pending = AuthStorageManager.PendingAuthentication(
            codeVerifier = "verifier",
            authenticationRequestId = "req-1",
            redirectUrl = ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI,
        )
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad code"))

        val redirect = Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc&nonce=req-1")
        val result = client.completeAuthenticationRequest(redirect)

        assertTrue(result.isFailure)
        assertNotNull(client.authError.value)
        // Not cleared on a real failure — nothing in the failure path clears it.
        assertNotNull(storageBacking.pending)
    }

    // ── isRedirect() ─────────────────────────────────────────────────────

    @Test
    fun `isRedirect is false for null`() {
        assertFalse(client.isRedirect(null))
    }

    @Test
    fun `isRedirect is false for an unrelated uri`() {
        assertFalse(client.isRedirect(Uri.parse("https://example.com/callback")))
    }

    @Test
    fun `isRedirect is true for the exact redirect uri`() {
        assertTrue(client.isRedirect(Uri.parse(ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI)))
    }

    @Test
    fun `isRedirect is true when the redirect uri carries query params`() {
        assertTrue(client.isRedirect(Uri.parse("${ch.rhosys.email.BuildConfig.OAUTH_REDIRECT_URI}?code=abc&nonce=req-1")))
    }
}
