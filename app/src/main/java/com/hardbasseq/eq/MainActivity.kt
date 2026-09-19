package com.hardbasseq.eq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HardBassEqTheme {
                Surface(modifier = Modifier.hardBassIndustrialBackground(), color = Color.Transparent) {
                    AppNavHost(spikeController = spikeController)
                }
            }
        }
    }
}
