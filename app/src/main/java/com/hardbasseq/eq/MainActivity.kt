package com.hardbasseq.eq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.navigation.AppNavHost
import com.hardbasseq.eq.ui.theme.HardBassEqTheme
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(spikeController = spikeController)
                }
            }
        }
    }
}
