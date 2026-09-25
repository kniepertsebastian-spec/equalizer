package com.soundcloud.equalizer.player.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SoundCloudLoginActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "sc_auth_prefs"
        const val KEY_OAUTH_TOKEN = "oauth_token"

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                checkCookies(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                checkCookies(url)
                return false
            }
        }

        webView.loadUrl("https://soundcloud.com/signin")
    }

    private fun checkCookies(url: String?) {
        if (url == null) return
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://soundcloud.com") ?: return

        // SoundCloud sets oauth_token or oauth_token cookie upon login
        for (cookie in cookies.split(";")) {
            val pair = cookie.trim().split("=")
            if (pair.size >= 2) {
                val name = pair[0].trim()
                val value = pair.subList(1, pair.size).joinToString("=").trim()
                if (name == "oauth_token" || name == "oauth_token_v2") {
                    saveToken(this, value)
                    setResult(RESULT_OK)
                    finish()
                    return
                }
            }
        }
    }
}
