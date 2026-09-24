package com.hardbasseq.eq.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "HardBass EQ") {
            App()
        }
    }
