package ch.rhosys.email.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Authress session lives in cookies, not in a stored access/refresh token
 * pair — `authorization` carries the bearer token and `user` carries the identity
 * token. The React Native SDK keeps them in the native cookie jar and mirrors
 * them into encrypted storage so a session survives a process restart
 * (authStorageManager.backupCookies / restoreCookies).
 *
 * This is the OkHttp equivalent: an in-memory jar backed by
 * EncryptedSharedPreferences. When several calls set the same cookie name on
 * different paths, the last value written wins — the SDK's `lastValue`.
 */
class AuthressCookieJar(context: Context, private val authressHost: String) : CookieJar {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "authress_cookies",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** name -> value, last write wins. */
    private val cookies = linkedMapOf<String, String>()

    init {
        restore()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.equals(authressHost, ignoreCase = true)) return
        var changed = false
        cookies.forEach { cookie ->
            // An expiry in the past is a deletion.
            if (cookie.expiresAt < System.currentTimeMillis()) {
                changed = this.cookies.remove(cookie.name) != null || changed
            } else if (this.cookies[cookie.name] != cookie.value) {
                this.cookies[cookie.name] = cookie.value
                changed = true
            }
        }
        if (changed) persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.equals(authressHost, ignoreCase = true)) return emptyList()
        return cookies.map { (name, value) ->
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(authressHost)
                .path("/")
                .secure()
                .httpOnly()
                .build()
        }
    }

    /** The bearer token used for API calls. */
    @Synchronized
    fun authorizationCookie(): String? = cookies[COOKIE_AUTHORIZATION]

    /** The identity token, carrying the user's profile claims. */
    @Synchronized
    fun userCookie(): String? = cookies[COOKIE_USER]

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private fun persist() {
        val array = JSONArray()
        cookies.forEach { (name, value) ->
            array.put(JSONObject().put("name", name).put("value", value))
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    private fun restore() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                cookies[entry.getString("name")] = entry.getString("value")
            }
        }
    }

    private companion object {
        const val KEY_COOKIES = "authress-cookies"
        const val COOKIE_AUTHORIZATION = "authorization"
        const val COOKIE_USER = "user"
    }
}
