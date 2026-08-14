package ch.rhosys.email.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * PKCE state between starting a login and returning from the browser, ported
 * from authStorageManager.ts. The code verifier must survive the app being
 * killed while the Custom Tab is in front, so it goes to encrypted storage
 * rather than memory.
 */
class AuthStorageManager(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "authress_pending_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    data class PendingAuthentication(
        val codeVerifier: String,
        val authenticationRequestId: String,
        val redirectUrl: String,
    )

    fun setAuthenticationRequest(state: PendingAuthentication?) {
        if (state == null) {
            prefs.edit().remove(KEY_PENDING).apply()
            return
        }
        val json = JSONObject()
            .put("codeVerifier", state.codeVerifier)
            .put("authenticationRequestId", state.authenticationRequestId)
            .put("redirectUrl", state.redirectUrl)
        prefs.edit().putString(KEY_PENDING, json.toString()).apply()
    }

    fun getAuthenticationRequest(): PendingAuthentication? {
        val raw = prefs.getString(KEY_PENDING, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            PendingAuthentication(
                codeVerifier = json.getString("codeVerifier"),
                authenticationRequestId = json.getString("authenticationRequestId"),
                redirectUrl = json.getString("redirectUrl"),
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PENDING = "authress-pending-auth"
    }
}
