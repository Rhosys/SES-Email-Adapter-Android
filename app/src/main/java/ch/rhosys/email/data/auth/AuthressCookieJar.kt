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
 * The Authress session lives in cookies rather than an access/refresh pair:
 * `authorization` carries the bearer token and `user` carries the identity token.
 *
 * Structured to match authStorageManager.ts. The SDK keeps two things — the
 * platform cookie jar that its HTTP calls read and write, and a mirror of it in
 * encrypted storage — and moves between them with explicit backupCookies and
 * restoreCookies at defined points. That split is reproduced here rather than
 * collapsed into a single always-persisted store, so the call sites line up with
 * the SDK's one for one.
 *
 * `lastValue` behaviour is preserved: when several calls set the same cookie name
 * on different paths, the last value written wins.
 */
class AuthressCookieJar(context: Context, private val authressHost: String) : CookieJar {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "authress_cookies",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** The live jar, equivalent to the SDK's native cookie store. */
    private val cookies = linkedMapOf<String, String>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!url.host.equals(authressHost, ignoreCase = true)) return
        cookies.forEach { cookie ->
            // An expiry in the past is a deletion.
            if (cookie.expiresAt < System.currentTimeMillis()) {
                this.cookies.remove(cookie.name)
            } else {
                this.cookies[cookie.name] = cookie.value
            }
        }
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

    /**
     * Mirrors the live jar into encrypted storage. The SDK calls this after a
     * successful token exchange and after a successful session check.
     */
    @Synchronized
    fun backupCookies() {
        if (cookies.isEmpty()) return
        val array = JSONArray()
        cookies.forEach { (name, value) ->
            array.put(JSONObject().put("name", name).put("value", value))
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    /**
     * Repopulates the live jar from the backup, and only when the jar is empty —
     * the SDK returns early if the platform store already holds cookies, so a
     * live session is never overwritten by a stale mirror.
     */
    @Synchronized
    fun restoreCookies() {
        if (cookies.isNotEmpty()) return
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                cookies[entry.getString("name")] = entry.getString("value")
            }
        }
    }

    /** Clears the live jar and the backup together. */
    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private companion object {
        const val KEY_COOKIES = "authress-cookies"
        const val COOKIE_AUTHORIZATION = "authorization"
        const val COOKIE_USER = "user"
    }
}
