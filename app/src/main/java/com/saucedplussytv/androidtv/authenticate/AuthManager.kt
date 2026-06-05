package com.saucedplussytv.androidtv.authenticate

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import com.saucedplussytv.androidtv.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Manages the SaucedplussyTV session credential.
 *
 * SaucedplussyTV uses the Sauce+ backend (white-label Floatplane) with cookie-session
 * auth (not OAuth/OIDC tokens). The credential is the `Cookie` header value harvested
 * from the WebView login ([WebLoginActivity]) — typically `sails.sid=...; cf_clearance=...`.
 * It is paired with the WebView's User-Agent, which must be reused on every request
 * so Cloudflare's `cf_clearance` cookie remains valid.
 *
 * Unlike OAuth there is no silent refresh: when the server session expires the API
 * returns 401/403 and the user must log in again. The legacy method names
 * ([withValidAccessToken], [getAccessToken], [clearTokens]) are retained so existing
 * call sites are unchanged, but the value they carry is now a Cookie header, not a
 * Bearer token.
 *
 * Credentials are stored in AndroidX Preferences DataStore. On first run, they are
 * migrated from the legacy SharedPreferences file written by MainActivity.
 */
class AuthManager private constructor(private val context: Context) {

    private val TAG = "AuthManager"

    private val dataStore: DataStore<Preferences> = context.applicationContext.sessionDataStore

    private val cookieKey = stringPreferencesKey(Constants.PREF_SESSION_COOKIE)
    private val uaKey = stringPreferencesKey(Constants.PREF_USER_AGENT)

    /** In-memory cache populated once at construction via a blocking read. */
    @Volatile
    private var cachedCookie: String

    @Volatile
    private var cachedUserAgent: String

    init {
        val snapshot = runBlocking { dataStore.data.first() }
        cachedCookie = snapshot[cookieKey] ?: ""
        cachedUserAgent = snapshot[uaKey]?.takeIf { it.isNotEmpty() } ?: DEFAULT_USER_AGENT
    }

    /** The full Cookie header value for the Sauce+ API, or "" if not logged in. */
    fun getAccessToken(): String = cachedCookie

    /** Alias for [getAccessToken] with the accurate name; both return the session cookie. */
    fun getSessionCookie(): String = getAccessToken()

    /** The User-Agent captured at login, or a sensible browser default for fresh installs. */
    fun getUserAgent(): String =
        cachedUserAgent.takeIf { it.isNotEmpty() } ?: DEFAULT_USER_AGENT

    fun isLoggedIn(): Boolean = cachedCookie.isNotEmpty()

    /** Persist the session harvested from the WebView login. */
    fun saveSession(cookieHeader: String, userAgent: String) {
        val ua = userAgent.takeIf { it.isNotEmpty() } ?: DEFAULT_USER_AGENT
        cachedCookie = cookieHeader
        cachedUserAgent = ua
        runBlocking {
            dataStore.edit { prefs ->
                prefs[cookieKey] = cookieHeader
                prefs[uaKey] = ua
            }
        }
    }

    fun clearTokens() {
        cachedCookie = ""
        cachedUserAgent = DEFAULT_USER_AGENT
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(cookieKey)
                prefs.remove(uaKey)
            }
        }
    }

    /**
     * Runs [onToken] with the stored session cookie when logged in, otherwise [onFailure].
     * Synchronous (no network) — cookie sessions cannot be silently refreshed here.
     */
    fun withValidAccessToken(
        onToken: (String) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        val cookie = getAccessToken()
        if (cookie.isNotEmpty()) {
            onToken(cookie)
        } else {
            Log.d(TAG, "No session cookie stored; login required")
            onFailure?.invoke()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        // Desktop-Chrome UA used as a fallback before a login captures the real WebView UA.
        // Cloudflare binds cf_clearance to the UA, so the stored value should win at runtime.
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        /** Reads the current User-Agent without blocking; safe before login (returns default). */
        fun peekUserAgent(): String = INSTANCE?.getUserAgent() ?: DEFAULT_USER_AGENT
    }
}

/**
 * App-scoped DataStore property delegate. Created once with applicationContext to avoid leaks.
 * A [SharedPreferencesMigration] moves the two legacy keys from the old MainActivity prefs file
 * on first access so no stored credentials are lost on upgrade.
 */
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = Constants.SESSION_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context,
                // Activity.getPreferences(MODE_PRIVATE) names the file after the fully-qualified
                // Activity class name — this is the file MainActivity historically wrote to.
                "com.saucedplussytv.androidtv.browse.MainActivity",
                keysToMigrate = setOf(Constants.PREF_SESSION_COOKIE, Constants.PREF_USER_AGENT)
            )
        )
    }
)
