package com.hardbasseq.eq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.navigation.AppNavHost
import com.hardbasseq.eq.ui.player.PlayerViewModel
import com.hardbasseq.eq.ui.theme.HardBassEqTheme
import com.hardbasseq.eq.ui.theme.hardBassIndustrialBackground
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var spikeController: SessionAttachSpikeController

    // Activity-scoped, handed to AppNavHost explicitly: a link shared into the app
    // (onNewIntent below) has to reach the same instance the player screen shows.
    private val playerViewModel: PlayerViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        // Only on a fresh start: after a rotation the same intent is delivered again
        // and must not import the shared link a second time.
        if (savedInstanceState == null) handleShareIntent(intent)
        setContent {
            HardBassEqTheme {
                Surface(modifier = Modifier.hardBassIndustrialBackground(), color = Color.Transparent) {
                    AppNavHost(spikeController = spikeController, playerViewModel = playerViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    // "Share" from the SoundCloud app (or any app) with this one as the target: the
    // shared text carries the link, which the player screen imports.
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        playerViewModel.importFromText(text, fromShare = true)
    }

    // Without this, the AudioSessionForegroundService's notification (explaining why
    // HardBass EQ keeps running) stays invisible on API 33+ - the service itself still
    // works, but the user has no way to know it's there.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
