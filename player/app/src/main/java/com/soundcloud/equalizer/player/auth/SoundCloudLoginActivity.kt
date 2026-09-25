package com.soundcloud.equalizer.player.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SoundCloudLoginActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "sc_auth_prefs"
        const val KEY_OAUTH_TOKEN = "oauth_token"

        // Android WebView's default UA includes a "wv" token (and an old
        // "Version/4.0" segment) that Google's login explicitly detects and
        // blocks ("This browser or app may not be secure"), since SoundCloud's
        // sign-in is Facebook/Google/Apple-only now (no email/password form).
        // A UA matching a real Chrome build sidesteps that block.
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        fun getSavedToken(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_OAUTH_TOKEN, null)
        }

        fun saveToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_OAUTH_TOKEN, token).apply()
        }

        fun clearToken(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_OAUTH_TOKEN).apply()
        }
    }

    private lateinit var webView: WebView
    private val cookiePoller = Handler(Looper.getMainLooper())
    private var polling = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = CHROME_USER_AGENT
        // Facebook/Google/Apple's SoundCloud sign-in buttons open their OAuth
        // flow via window.open() - without multi-window support the WebView
        // silently swallows that call and nothing happens on click.
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkCookies()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                checkCookies()
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                // The OAuth provider popup doesn't need its own visible window -
                // create an off-screen WebView, let it run the OAuth redirect
                // chain (setting cookies on the real soundcloud.com domain as it
                // goes via shared CookieManager/third-party-cookie state), and
                // fold it back into the main WebView once it lands somewhere.
                val popup = WebView(this@SoundCloudLoginActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.userAgentString = CHROME_USER_AGENT
                cookieManager.setAcceptThirdPartyCookies(popup, true)
                popup.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(popupView: WebView?, url: String?) {
                            super.onPageFinished(popupView, url)
                            checkCookies()
                            // Once the popup has navigated back to a soundcloud.com
                            // page (OAuth flow completed/redirected), surface that
                            // in the main WebView so the user sees the result and
                            // any further first-party redirects keep working.
                            if (url != null && url.contains("soundcloud.com") && !url.contains("accounts.google") &&
                                !url.contains("facebook.com") && !url.contains("appleid.apple.com")
                            ) {
                                webView.loadUrl(url)
                                (view?.parent as? ViewGroup)?.removeView(popup)
                            }
                        }
                    }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popup
                resultMsg?.sendToTarget()
                return true
            }
        }

        webView.loadUrl("https://soundcloud.com/signin")
        startCookiePolling()
    }

    // SoundCloud's sign-in is a client-side SPA: once logged in it updates its
    // own state without a full page navigation, so WebViewClient's
    // onPageFinished/shouldOverrideUrlLoading callbacks alone can miss it. Poll
    // the cookie jar directly while this screen is open as a fallback.
    private fun startCookiePolling() {
        if (polling) return
        polling = true
        val tick =
            object : Runnable {
                override fun run() {
                    if (!polling) return
                    checkCookies()
                    cookiePoller.postDelayed(this, 1000)
                }
            }
        cookiePoller.postDelayed(tick, 1000)
    }

    private fun checkCookies() {
        CookieManager.getInstance().flush()
        val cookies = CookieManager.getInstance().getCookie("https://soundcloud.com") ?: return

        for (cookie in cookies.split(";")) {
            val pair = cookie.trim().split("=")
            if (pair.size >= 2) {
                val name = pair[0].trim()
                val value = pair.subList(1, pair.size).joinToString("=").trim()
                if (name == "oauth_token" || name == "oauth_token_v2") {
                    polling = false
                    saveToken(this, value)
                    setResult(RESULT_OK)
                    finish()
                    return
                }
            }
        }
    }

    override fun onDestroy() {
        polling = false
        cookiePoller.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
