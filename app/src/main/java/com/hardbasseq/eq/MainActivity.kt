package com.hardbasseq.eq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.hardbasseq.eq.audio.AndroidAudioEffectRepository
import com.hardbasseq.eq.ui.MainScreen
import com.hardbasseq.eq.ui.theme.HardBassEqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HardBassEqTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val repository = remember { AndroidAudioEffectRepository() }
                    MainScreen(repository = repository)
                }
            }
        }
    }
}
