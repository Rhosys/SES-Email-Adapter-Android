package ch.rhosys.email.data.auth

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Port of jwtManager.ts from @authress/login-react-native.
 *
 * Base64url without padding throughout, matching the SDK's `b64urlEncode`.
 */
object JwtManager {

    private const val B64_URL = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    data class AuthCodes(val codeVerifier: String, val codeChallenge: String)

    /**
     * PKCE pair. The SDK derives the verifier from 16 random 32-bit values rendered
     * as a comma-joined decimal string, then base64url-encodes that; the challenge
     * is base64url(SHA-256(verifier)).
     */
    fun getAuthCodes(): AuthCodes {
        val random = SecureRandom()
        val words = IntArray(16) { random.nextInt() }
        val joined = words.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }
        val codeVerifier = Base64.encodeToString(joined.toByteArray(Charsets.UTF_8), B64_URL)
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return AuthCodes(codeVerifier, Base64.encodeToString(digest, B64_URL))
    }

    /**
     * Decodes a JWT payload without verifying the signature — the SDK does the same,
     * because the token arrives over TLS from the issuer it is then checked against.
     * `exp` is shortened by 10 seconds as a clock-skew buffer, matching the SDK.
     */
    fun decode(token: String?): JSONObject? {
        if (token.isNullOrBlank()) return null
        return runCatching {
            val payloadSegment = token.split(".").getOrNull(1) ?: return null
            val json = JSONObject(String(Base64.decode(payloadSegment, B64_URL), Charsets.UTF_8))
            if (json.has("exp")) json.put("exp", json.getLong("exp") - 10)
            json
        }.getOrNull()
    }
}
