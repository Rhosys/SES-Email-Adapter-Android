package ch.rhosys.email.testutil

import org.json.JSONObject
import java.util.Base64

/**
 * Builds an unsigned JWT string (header.payload.signature) good enough for
 * [ch.rhosys.email.data.auth.JwtManager.decode] to parse — it never verifies
 * the signature, only base64url-decodes the payload segment, matching the SDK
 * it ports. Encoding here uses [java.util.Base64]'s URL-safe, no-padding
 * encoder, which produces the same output `android.util.Base64` does under
 * Robolectric's shadow with `URL_SAFE or NO_PADDING or NO_WRAP`.
 */
fun testJwt(claims: JSONObject): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray(Charsets.UTF_8))
    val payload = encoder.encodeToString(claims.toString().toByteArray(Charsets.UTF_8))
    return "$header.$payload.signature"
}

/**
 * A token whose `exp` is [secondsFromNow] in the future (or past, if negative),
 * issued by [issuer], for the given [subject]. `iss` and `exp` are the only
 * claims [ch.rhosys.email.data.auth.AuthressLoginClient.getToken] inspects.
 */
fun testJwt(issuer: String, secondsFromNow: Long, subject: String = "user-1"): String =
    testJwt(
        JSONObject()
            .put("iss", issuer)
            .put("sub", subject)
            .put("exp", (System.currentTimeMillis() / 1000) + secondsFromNow),
    )
