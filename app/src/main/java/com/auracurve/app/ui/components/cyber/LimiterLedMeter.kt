package com.auracurve.app.ui.components.cyber

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.ui.theme.CyberColors

private const val SEGMENTS = 6
private const val WARNING_LOW = 0.35f
private const val WARNING_PULSE_MS = 1200

/**
 * How hard the limiter is expected to work, as a staircase of six LEDs: two green for enough
 * headroom, four yellow to orange when it limits now and then, all six up to red for heavy
 * limiting, none while the EQ is off ([level] null).
 *
 * The level is an estimate from the curve's boosts, not a reading of the limiter (Android has no
 * API for that): the LEDs show the warning level, never a moving level. Only the top LED of heavy
 * limiting breathes slowly while [processing], as a warning light. They are decorative; the text
 * beside them carries the state for TalkBack and without color.
 */
@Composable
fun LimiterLedMeter(
    level: HeadroomWarningLevel?,
    modifier: Modifier = Modifier,
    processing: Boolean = false,
) {
    val lit =
        when (level) {
            null -> 0
            HeadroomWarningLevel.SUFFICIENT -> 2
            HeadroomWarningLevel.OCCASIONAL_LIMITING -> 4
            HeadroomWarningLevel.HEAVY_LIMITING -> SEGMENTS
        }
    val warning = rememberPulse(lit == SEGMENTS && processing, WARNING_LOW, 1f, WARNING_PULSE_MS)
    Spacer(
        modifier =
            modifier
                .size(width = 34.dp, height = 20.dp)
                .clearAndSetSemantics {}
                .drawWithCache {
                    val gap = 2.dp.toPx()
                    val width = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
                    val corner = CornerRadius(1.dp.toPx())
                    val colors = Array(SEGMENTS) { ledColor(it, lit) }
                    val unlit = CyberColors.outline.copy(alpha = 0.6f)
                    onDrawBehind {
                        for (i in 0 until SEGMENTS) {
                            // Rising from a third of the height to the full height, like a level.
                            val height = size.height * (1f + 2f * i / (SEGMENTS - 1)) / 3f
                            val topLeft = Offset(i * (width + gap), size.height - height)
                            val alpha = if (i == SEGMENTS - 1 && warning != null) warning.value else 1f
                            drawRoundRect(if (i < lit) colors[i] else unlit, topLeft, Size(width, height), corner, alpha = alpha)
                        }
                    }
                },
    )
}

// The color of LED [index] when [lit] are on: one color family per level, warming up the stair.
private fun ledColor(
    index: Int,
    lit: Int,
): Color =
    when (lit) {
        2 -> CyberColors.signal
        4 -> lerp(CyberColors.yellow, CyberColors.orange, index / 3f)
        SEGMENTS -> lerp(CyberColors.orange, CyberColors.red, index / (SEGMENTS - 1f))
        else -> CyberColors.outline
    }
