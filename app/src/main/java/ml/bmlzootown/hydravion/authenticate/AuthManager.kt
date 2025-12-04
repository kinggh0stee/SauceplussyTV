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

 // Centralized helper for managing OAuth tokens and automatic access-token refresh.
class AuthManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val queue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val TAG = "AuthManager"

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
    fun withValidAccessToken(
        onToken: (String) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        if (isAccessTokenValid()) {
            Log.d(TAG, "Access token is valid, using existing token")
            onToken(getAccessToken())
            return
        }

        Log.d(TAG, "Access token expired or invalid, attempting refresh")
        val refreshToken = getRefreshToken()
        if (refreshToken.isEmpty()) {
            Log.w(TAG, "Refresh token is empty, cannot refresh. Clearing tokens.")
            clearTokens()
            onFailure?.invoke()
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

                        onToken(newAccessToken)
                    } else {
                        Log.e(TAG, "Token refresh response missing access_token")
                        clearTokens()
                        onFailure?.invoke()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception parsing token refresh response", e)
                    clearTokens()
                    onFailure?.invoke()
                }
            },
            { error ->
                Log.e(TAG, "Token refresh request failed: ${error.message}")
                clearTokens()
                onFailure?.invoke()
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


