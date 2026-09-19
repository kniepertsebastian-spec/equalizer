package com.hardbasseq.eq

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.navigation.AppNavHost
import com.hardbasseq.eq.ui.theme.HardBassEqTheme
import com.hardbasseq.eq.ui.theme.hardBassIndustrialBackground
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var spikeController: SessionAttachSpikeController

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            HardBassEqTheme {
                Surface(modifier = Modifier.hardBassIndustrialBackground(), color = Color.Transparent) {
                    AppNavHost(spikeController = spikeController)
                }
            }
        }
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
