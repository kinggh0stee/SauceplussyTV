package com.saucedplussytv.androidtv.authenticate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import com.saucedplussytv.androidtv.R

/**
 * Cookie-session login for SaucedplussyTV (Sauce+ / white-label Floatplane).
 *
 * Loads the real Sauce+ login page in a WebView so the user solves the Cloudflare
 * Turnstile challenge naturally. On success, harvests `sails.sid` + `cf_clearance`
 * from CookieManager and the WebView's User-Agent for all subsequent API calls.
 *
 * Login completion is detected two ways (whichever fires first):
 *  1. URL-based: navigated away from /login to any non-CF-interstitial route + sails.sid present.
 *  2. SID-change: sails.sid value changed from the baseline captured on initial load
 *     (handles React SPAs that submit via XHR and never navigate away from /login).
 *
 * Returns RESULT_OK with extras [EXTRA_SESSION_COOKIE] and [EXTRA_USER_AGENT] on success.
 */
class WebLoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    @Volatile private var completed = false
    private val pollHandler = Handler(Looper.getMainLooper())

    /** sails.sid value captured after the login page first loads; changes on successful auth. */
    private var initialSidValue: String? = null

    /** Last URL seen by doUpdateVisitedHistory; more reliable than webView.url after pushState. */
    private var lastNavigatedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_login)
        webView = findViewById(R.id.web_view)
        progress = findViewById(R.id.progress)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE

                // Scale down for TV: site targets ~1280px desktop; a 1920px WebView upscales it
                // ~1.5x making everything too large. CSS zoom scales the entire render tree.
                view?.evaluateJavascript("""
                    (function() {
                        if (document.getElementById('tv-compact-style')) return;
                        var s = document.createElement('style');
                        s.id = 'tv-compact-style';
                        s.textContent = 'html { zoom: 0.55 !important; }';
                        document.head.appendChild(s);
                    })()
                """.trimIndent(), null)

                // Auto-focus the first visible text input so TV D-pad can navigate to it.
                // Without a focused element, D-pad arrows are consumed as page scroll events
                // and the user can never reach the username/password fields.
                if (url?.contains("/login") == true) {
                    view?.evaluateJavascript("""
                        (function() {
                            var sel = 'input[type=text],input[type=email],input[type=tel],input:not([type])';
                            var inputs = document.querySelectorAll(sel);
                            for (var i = 0; i < inputs.length; i++) {
                                var r = inputs[i].getBoundingClientRect();
                                if (r.width > 0 && r.height > 0) { inputs[i].focus(); return; }
                            }
                        })()
                    """.trimIndent(), null)

                    // Capture baseline sails.sid so we can detect when it changes (= login done)
                    if (initialSidValue == null) {
                        initialSidValue = extractSidValue(CookieManager.getInstance().getCookie(SITE))
                    }
                }

                view?.requestFocus()
                maybeFinish(url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (url != null) lastNavigatedUrl = url
                maybeFinish(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (completed || request?.isForMainFrame != true) return
                Log.w(TAG, "Login page failed to load: ${error?.description}")
                progress.visibility = View.GONE
                Toast.makeText(
                    this@WebLoginActivity,
                    "Couldn't reach the SaucedplussyTV login page. Check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        webView.loadUrl(LOGIN_URL)

        // Polling fallback — three purposes:
        // 1. Catch React replaceState navigations that doUpdateVisitedHistory misses.
        // 2. Retry the URL check after the cookie race window (webView.url may be stale
        //    after pushState; lastNavigatedUrl is used instead).
        // 3. Detect sails.sid value change for SPAs that never navigate away from /login.
        pollHandler.postDelayed(object : Runnable {
            override fun run() {
                if (completed || isFinishing || isDestroyed) return

                // Use lastNavigatedUrl — webView.url can be stale after React Router pushState.
                val pollUrl = lastNavigatedUrl ?: webView.url
                pollUrl?.let { maybeFinish(it) }

                if (!completed) {
                    CookieManager.getInstance().flush()
                    val cookieHeader = CookieManager.getInstance().getCookie(SITE)
                    val currentSid = extractSidValue(cookieHeader)
                    val baseline = initialSidValue

                    when {
                        baseline == null && currentSid != null -> {
                            // First sails.sid seen — store as baseline AND retry URL detection.
                            // The SPA may have already navigated to the home page before this poll
                            // fires; now that the cookie is available maybeFinish will succeed.
                            initialSidValue = currentSid
                            pollUrl?.let { maybeFinish(it) }
                        }
                        baseline != null && currentSid != null && currentSid != baseline -> {
                            Log.d(TAG, "Login detected via sails.sid change")
                            acceptLogin(cookieHeader!!)
                        }
                    }
                }

                if (!completed) pollHandler.postDelayed(this, 1000)
            }
        }, 2000)
    }

    /**
     * URL-based login detection: once we navigate away from the login page and have sails.sid,
     * the user has authenticated. Invalid cookies are caught downstream by getSubs().
     *
     * Retries up to 5 times at 300 ms intervals to handle the race where the React SPA
     * fires a pushState navigation before the WebKit cookie jar has flushed the sails.sid
     * from the XHR Set-Cookie response.
     */
    private fun maybeFinish(url: String?, retryCount: Int = 0) {
        if (completed || url == null || isFinishing || isDestroyed) return
        val uri = Uri.parse(url)
        val host = uri.host ?: return
        if (host != HOST && host != "www.$HOST") return
        if (!isAppRoute(uri.path)) return

        CookieManager.getInstance().flush()
        val cookieHeader = CookieManager.getInstance().getCookie(SITE)
        if (!cookieHeader.isNullOrEmpty() && cookieHeader.contains(SESSION_COOKIE_NAME)) {
            Log.d(TAG, "Login detected via URL navigation")
            acceptLogin(cookieHeader)
            return
        }

        // On a valid non-login route but cookie not yet available — schedule retries.
        if (retryCount < 5) {
            pollHandler.postDelayed({ maybeFinish(url, retryCount + 1) }, 300)
        }
    }

    private fun acceptLogin(cookieHeader: String) {
        if (completed) return
        completed = true
        CookieManager.getInstance().flush()
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(EXTRA_SESSION_COOKIE, cookieHeader)
                putExtra(EXTRA_USER_AGENT, webView.settings.userAgentString)
            }
        )
        finish()
    }

    /** True when we've left the login page; sails.sid presence is the real auth signal. */
    private fun isAppRoute(path: String?): Boolean {
        val p = (path ?: "/").lowercase()
        return !p.startsWith("/login") && !p.startsWith("/cdn-cgi")
    }

    /** Extracts the value portion of `sails.sid=<value>` from a cookie header string. */
    private fun extractSidValue(cookieHeader: String?): String? {
        cookieHeader ?: return null
        return cookieHeader.split(";")
            .firstOrNull { it.trim().startsWith("$SESSION_COOKIE_NAME=") }
            ?.substringAfter("$SESSION_COOKIE_NAME=")
            ?.trim()
    }

    @Deprecated("Deprecated in Android API")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        pollHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WebLoginActivity"
        private const val HOST = "sauceplus.com"
        private const val SITE = "https://www.sauceplus.com"
        private const val LOGIN_URL = "$SITE/login"
        private const val SESSION_COOKIE_NAME = "sails.sid"

        const val EXTRA_SESSION_COOKIE = "session_cookie"
        const val EXTRA_USER_AGENT = "user_agent"
    }
}
