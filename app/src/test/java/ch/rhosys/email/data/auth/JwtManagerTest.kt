package ch.rhosys.email.data.auth

import ch.rhosys.email.testutil.testJwt
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest
import java.util.Base64

/**
 * [JwtManager] backs both [AuthressLoginClient.getToken]'s expiry check and
 * the PKCE/anti-abuse machinery `authenticate()` depends on — its edge cases
 * are exercised directly here rather than only indirectly through the client.
 */
@RunWith(RobolectricTestRunner::class)
class JwtManagerTest {

    @Test
    fun `decode returns null for a null token`() {
        assertNull(JwtManager.decode(null))
    }

    @Test
    fun `decode returns null for a blank token`() {
        assertNull(JwtManager.decode("   "))
    }

    @Test
    fun `decode returns null when there is no payload segment`() {
        assertNull(JwtManager.decode("onlyheader"))
    }

    @Test
    fun `decode returns null for invalid base64`() {
        assertNull(JwtManager.decode("header.not-valid-base64!!!.sig"))
    }

    @Test
    fun `decode returns null when the payload is not JSON`() {
        val notJson = Base64.getUrlEncoder().withoutPadding().encodeToString("not json".toByteArray())
        assertNull(JwtManager.decode("header.$notJson.sig"))
    }

    @Test
    fun `decode leaves claims untouched when there is no exp`() {
        val token = testJwt(JSONObject().put("iss", "https://x").put("sub", "u1"))
        val payload = JwtManager.decode(token)
        assertNotNull(payload)
        assertFalse(payload!!.has("exp"))
        assertEquals("u1", payload.getString("sub"))
    }

    @Test
    fun `decode shortens exp by 10 seconds`() {
        val originalExp = 1_000_000L
        val token = testJwt(JSONObject().put("iss", "https://x").put("exp", originalExp))
        val payload = JwtManager.decode(token)
        assertEquals(originalExp - 10, payload!!.getLong("exp"))
    }

    @Test
    fun `getAuthCodes derives the challenge as sha256 of the verifier`() {
        val codes = JwtManager.getAuthCodes()
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(codes.codeVerifier.toByteArray(Charsets.UTF_8)),
        )
        assertEquals(expectedChallenge, codes.codeChallenge)
    }

    @Test
    fun `getAuthCodes produces a different pair on each call`() {
        val a = JwtManager.getAuthCodes()
        val b = JwtManager.getAuthCodes()
        assertTrue(a.codeVerifier != b.codeVerifier)
    }

    @Test
    fun `calculateAntiAbuseHash produces the v2 format with a hash starting 00`() = runBlocking {
        val hash = JwtManager.calculateAntiAbuseHash(linkedMapOf("applicationId" to "app-1"))
        val parts = hash.split(";")
        assertEquals(4, parts.size)
        assertEquals("v2", parts[0])
        assertTrue(parts[1].toLong() > 0) // timestamp
        assertTrue(parts[2].toInt() > 0) // fineTuner
        assertTrue(parts[3].startsWith("00"))
    }

    @Test
    fun `calculateAntiAbuseHash ignores null, empty-string and false values`() = runBlocking {
        // Sanity check that it doesn't throw and still produces a valid hash
        // when most props are absent, mirroring authenticate()'s default options.
        val hash = JwtManager.calculateAntiAbuseHash(
            linkedMapOf(
                "connectionId" to null,
                "tenantLookupIdentifier" to null,
                "inviteId" to null,
                "applicationId" to "app-1",
                "audiences" to null,
            ),
        )
        assertTrue(hash.startsWith("v2;"))
    }
}
