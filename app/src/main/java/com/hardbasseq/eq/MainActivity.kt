package com.hardbasseq.eq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hardbasseq.eq.audio.AndroidAudioEffectRepository
import com.hardbasseq.eq.audio.spike.SessionAttachSpikeController
import com.hardbasseq.eq.ui.main.MainViewModel
import com.hardbasseq.eq.ui.main.MainViewModelFactory
import com.hardbasseq.eq.ui.navigation.AppNavHost
import com.hardbasseq.eq.ui.theme.HardBassEqTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(AndroidAudioEffectRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HardBassEqTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val spikeController = remember { SessionAttachSpikeController(applicationContext) }
                    AppNavHost(viewModel = viewModel, spikeController = spikeController)
                }
            }
        }
    }
}
