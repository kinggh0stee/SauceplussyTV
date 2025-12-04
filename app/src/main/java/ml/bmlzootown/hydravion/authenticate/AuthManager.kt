package ml.bmlzootown.hydravion.authenticate

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import ml.bmlzootown.hydravion.Constants
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

 // Centralized helper for managing OAuth tokens and automatic access-token refresh.
class AuthManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val queue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val TAG = "AuthManager"
    
    // Cache for validated token to avoid re-validation on every request
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var cacheValidUntil: Long = 0
    
    // Queue for concurrent token validation requests
    private val pendingCallbacks = ConcurrentLinkedQueue<Pair<(String) -> Unit, (() -> Unit)?>>()
    @Volatile
    private var isRefreshing = false

    private val tokenEndpoint =
        "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"
    private val clientId = "hydravion"

    fun getAccessToken(): String =
        prefs.getString(Constants.PREF_ACCESS_TOKEN, "") ?: ""

    fun getRefreshToken(): String =
        prefs.getString(Constants.PREF_REFRESH_TOKEN, "") ?: ""

    private fun getExpiresAt(): Long =
        prefs.getLong(Constants.PREF_TOKEN_EXPIRES_AT, 0L)

    fun clearTokens() {
        prefs.edit()
            .remove(Constants.PREF_ACCESS_TOKEN)
            .remove(Constants.PREF_REFRESH_TOKEN)
            .remove(Constants.PREF_TOKEN_EXPIRES_AT)
            .commit()
        // Clear cache when tokens are cleared
        cachedToken = null
        cacheValidUntil = 0
    }

    private fun isAccessTokenValid(): Boolean {
        val token = getAccessToken()
        if (token.isEmpty()) {
            Log.d(TAG, "Access token is empty")
            return false
        }

        // Add 60s of leeway to avoid race with server-side expiry.
        val now = System.currentTimeMillis()
        val expiresAt = getExpiresAt()
        val isValid = now + 60_000L < expiresAt
        if (!isValid) {
            val timeUntilExpiry = expiresAt - now
            Log.d(TAG, "Access token expired or expiring soon. Time until expiry: ${timeUntilExpiry / 1000}s")
        }
        return isValid
    }

     // Ensures a valid access token, refreshing with the refresh token when necessary.
     // - On success: invokes [onToken] with a non-empty access token.
     // - On failure: clears stored tokens and invokes [onFailure].
     // - Batches concurrent requests to avoid multiple simultaneous refresh attempts.
    fun withValidAccessToken(
        onToken: (String) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        // Check cache first (valid for 30 seconds to reduce validation overhead)
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < cacheValidUntil) {
            Log.d(TAG, "Using cached valid token")
            onToken(cachedToken!!)
            return
        }
        
        // Check if token is valid in storage
        if (isAccessTokenValid()) {
            val token = getAccessToken()
            // Cache the token for 30 seconds
            cachedToken = token
            cacheValidUntil = now + 30_000L
            Log.d(TAG, "Access token is valid, using existing token (cached for 30s)")
            onToken(token)
            return
        }

        // If already refreshing, queue this callback to be notified when refresh completes
        if (isRefreshing) {
            Log.d(TAG, "Token refresh in progress, queuing callback")
            pendingCallbacks.add(Pair(onToken, onFailure))
            return
        }

        // Start refresh process
        isRefreshing = true
        Log.d(TAG, "Access token expired or invalid, attempting refresh")
        val refreshToken = getRefreshToken()
        if (refreshToken.isEmpty()) {
            Log.w(TAG, "Refresh token is empty, cannot refresh. Clearing tokens.")
            clearTokens()
            
            // Notify all pending callbacks before returning
            val callbacks = mutableListOf<Pair<(String) -> Unit, (() -> Unit)?>>()
            while (pendingCallbacks.isNotEmpty()) {
                pendingCallbacks.poll()?.let { callbacks.add(it) }
            }
            isRefreshing = false
            
            // Notify current caller
            onFailure?.invoke()
            // Notify all queued callbacks
            callbacks.forEach { (_, failureCallback) ->
                failureCallback?.invoke()
            }
            return
        }

        Log.d(TAG, "Refreshing access token using refresh token")

        val request = object : StringRequest(
            Method.POST,
            tokenEndpoint,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("access_token")) {
                        val newAccessToken = json.getString("access_token")
                        val newRefreshToken = json.optString("refresh_token", refreshToken)
                        val expiresIn = json.optLong("expires_in", 1800L)
                        val expiresAt = System.currentTimeMillis() + expiresIn * 1000L

                        Log.d(TAG, "Token refresh successful. New token expires in ${expiresIn}s")
                        if (newRefreshToken != refreshToken) {
                            Log.d(TAG, "Received new refresh token")
                        }

                        prefs.edit()
                            .putString(Constants.PREF_ACCESS_TOKEN, newAccessToken)
                            .putString(Constants.PREF_REFRESH_TOKEN, newRefreshToken)
                            .putLong(Constants.PREF_TOKEN_EXPIRES_AT, expiresAt)
                            .commit()

                        // Cache the new token
                        cachedToken = newAccessToken
                        cacheValidUntil = System.currentTimeMillis() + 30_000L
                        
                        // Notify all pending callbacks
                        val callbacks = mutableListOf<Pair<(String) -> Unit, (() -> Unit)?>>()
                        while (pendingCallbacks.isNotEmpty()) {
                            pendingCallbacks.poll()?.let { callbacks.add(it) }
                        }
                        isRefreshing = false
                        
                        onToken(newAccessToken)
                        callbacks.forEach { (tokenCallback, _) ->
                            tokenCallback(newAccessToken)
                        }
                    } else {
                        Log.e(TAG, "Token refresh response missing access_token")
                        clearTokens()
                        val callbacks = mutableListOf<Pair<(String) -> Unit, (() -> Unit)?>>()
                        while (pendingCallbacks.isNotEmpty()) {
                            pendingCallbacks.poll()?.let { callbacks.add(it) }
                        }
                        isRefreshing = false
                        onFailure?.invoke()
                        callbacks.forEach { (_, failureCallback) ->
                            failureCallback?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception parsing token refresh response", e)
                    clearTokens()
                    val callbacks = mutableListOf<Pair<(String) -> Unit, (() -> Unit)?>>()
                    while (pendingCallbacks.isNotEmpty()) {
                        pendingCallbacks.poll()?.let { callbacks.add(it) }
                    }
                    isRefreshing = false
                    onFailure?.invoke()
                    callbacks.forEach { (_, failureCallback) ->
                        failureCallback?.invoke()
                    }
                }
            },
            { error ->
                Log.e(TAG, "Token refresh request failed: ${error.message}")
                clearTokens()
                val callbacks = mutableListOf<Pair<(String) -> Unit, (() -> Unit)?>>()
                while (pendingCallbacks.isNotEmpty()) {
                    pendingCallbacks.poll()?.let { callbacks.add(it) }
                }
                isRefreshing = false
                onFailure?.invoke()
                callbacks.forEach { (_, failureCallback) ->
                    failureCallback?.invoke()
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> =
                mutableMapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to clientId,
                    "refresh_token" to refreshToken
                )
        }

        queue.add(request)
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(context: Context, prefs: SharedPreferences): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext, prefs).also { INSTANCE = it }
            }
        }
    }
}


