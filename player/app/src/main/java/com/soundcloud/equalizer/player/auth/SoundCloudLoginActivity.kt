package com.soundcloud.equalizer.player.auth

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
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
        //
        // Must be a DESKTOP UA: soundcloud.com 307-redirects any mobile-looking UA
        // (including a mobile Chrome one) to m.soundcloud.com/signin instead, an
        // unverified separate flow - stick to the desktop /signin page this was
        // actually tested against.
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

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
    private var popupDialog: Dialog? = null

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
                // The OAuth provider popup needs to actually be shown - the user has
                // to type credentials/approve the sign-in on it. A previous version
                // of this routed it to an off-screen, never-attached WebView, so
                // nothing visibly happened on tapping Google/Facebook/Apple and
                // those WebViews were never destroyed either (leaking on every
                // attempt). Show it full-screen in a Dialog instead.
                popupDialog?.dismiss()

                val popup = WebView(this@SoundCloudLoginActivity)
                popup.settings.javaScriptEnabled = true
                popup.settings.domStorageEnabled = true
                popup.settings.userAgentString = CHROME_USER_AGENT
                cookieManager.setAcceptThirdPartyCookies(popup, true)

                val dialog = Dialog(this@SoundCloudLoginActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.setContentView(popup)
                dialog.setOnDismissListener {
                    popup.destroy()
                    if (popupDialog === dialog) popupDialog = null
                }
                popupDialog = dialog

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
                                dialog.dismiss()
                            }
                        }
                    }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popup
                resultMsg?.sendToTarget()
                dialog.show()
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
        popupDialog?.dismiss()
        popupDialog = null
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }
}
