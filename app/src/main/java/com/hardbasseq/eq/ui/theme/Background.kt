package com.hardbasseq.eq.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

// Dark brushed-metal backdrop with sparse orange "circuit" accent lines; cards stay
// opaque on top, so this only shows through the gaps between them.
fun Modifier.hardBassIndustrialBackground(): Modifier =
    this
        .fillMaxSize()
        .background(
            Brush.linearGradient(
                colors = listOf(HardBassDarkBackground, Color(0xFF1A1418), HardBassDarkBackground),
            ),
        ).drawBehind {
            val strokeWidth = 1.dp.toPx()
            rotate(degrees = -18f) {
                var x = -size.height
                val spacing = 26.dp.toPx()
                while (x < size.width + size.height) {
                    drawLine(
                        color = HardBassBackgroundStreak,
                        start = Offset(x, -size.height),
                        end = Offset(x, size.height * 2f),
                        strokeWidth = strokeWidth,
                    )
                    x += spacing
                }
            }

            val accentLines =
                listOf(
                    Offset(size.width * 0.08f, size.height * 0.04f) to Offset(size.width * 0.34f, size.height * 0.015f),
                    Offset(size.width * 0.74f, size.height * 0.10f) to Offset(size.width * 0.99f, size.height * 0.06f),
                    Offset(size.width * 0.04f, size.height * 0.92f) to Offset(size.width * 0.38f, size.height * 0.97f),
                    Offset(size.width * 0.58f, size.height * 0.99f) to Offset(size.width * 0.94f, size.height * 0.88f),
                )
            accentLines.forEach { (start, end) ->
                drawLine(color = HardBassBackgroundAccent, start = start, end = end, strokeWidth = 1.5.dp.toPx())
            }
        }
