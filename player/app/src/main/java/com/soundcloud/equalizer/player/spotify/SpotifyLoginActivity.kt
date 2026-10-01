package com.soundcloud.equalizer.player.spotify

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Where the browser lands after the Spotify sign-in (redirect hardbasseq://spotify-callback):
// swaps the code for tokens, says how it went and brings the app back. No screen of its own.
class SpotifyLoginActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirect = intent?.data?.toString()
        if (redirect == null) {
            finish()
            return
        }
        scope.launch {
            val error = SpotifyApiClient.get(applicationContext).completeLogin(redirect)
            Toast.makeText(applicationContext, error ?: "Mit Spotify angemeldet", Toast.LENGTH_LONG).show()
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
