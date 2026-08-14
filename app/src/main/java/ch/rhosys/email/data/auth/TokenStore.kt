package ch.rhosys.email.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local state that outlives a session, in EncryptedSharedPreferences backed by
 * the Android Keystore.
 *
 * Deliberately no access or refresh token: the Authress session is held in
 * cookies, managed by AuthressCookieJar, exactly as the login SDK does it. The
 * only thing kept here is which account the user last had selected.
 */
class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "numaeel_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var activeAccountId: String?
        get() = prefs.getString(KEY_ACTIVE_ACCOUNT, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_ACCOUNT, value).apply()


    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACTIVE_ACCOUNT = "active_account_id"
    }
}
