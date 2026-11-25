package com.auracurve.app.ui.components.cyber

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.playlist.PlayerFormat
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.SonicNumerals
import com.auracurve.app.ui.theme.cyberGlow

private val WaveHeight = 36.dp
private val BarPitch = 3.dp

/**
 * The seek bar of Now Playing as a waveform: the played part in the curve's gradient, the rest
 * dimmed, a glowing orb at the position. It behaves like the SeekBar it replaces: dragging only
 * moves the orb and the time, the seek happens once when the finger lifts.
 *
 * The waveform is a stylized shape seeded from the track (decision E3), not decoded audio: the
 * app reads no samples for it. [seed] comes from the track's id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformSeekbar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    seed: Long,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: PlayerFormat.progress(positionMs, durationMs)
    val active = enabled && durationMs > 0
    // Read only while drawing, so playback moving on redraws the track without rebuilding it.
    val shown = rememberUpdatedState(fraction)
    val interaction = remember { MutableInteractionSource() }
    val pulse = rememberPulse(playing && active, CyberGlow.thumb.alpha * 0.5f, CyberGlow.thumb.alpha, THUMB_PULSE_MS)
    val seekDescription = stringResource(R.string.seek_description)
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                dragFraction = null
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekDescription },
            enabled = active,
            valueRange = 0f..1f,
            interactionSource = interaction,
            colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.Transparent),
            thumb = {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .then(
                                if (active) {
                                    Modifier.cyberGlow(CyberColors.cyan, CyberGlow.thumb.radius, CircleShape) {
                                        pulse?.value ?: CyberGlow.thumb.alpha
                                    }
                                } else {
                                    Modifier
                                },
                            ).drawWithCache {
                                val radius = size.minDimension / 2f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val orb =
                                    Brush.radialGradient(
                                        listOf(Color.White, CyberColors.cyan, CyberColors.violet),
                                        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                                        radius = radius * 1.4f,
                                    )
                                onDrawBehind {
                                    if (active) {
                                        drawCircle(orb, radius, center)
                                    } else {
                                        drawCircle(CyberColors.mutedText, radius * 0.7f, center)
                                    }
                                }
                            },
                )
            },
            track = {
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(WaveHeight)
                            .drawWithCache {
                                val pitch = BarPitch.toPx()
                                val count = (size.width / pitch).toInt().coerceAtLeast(1)
                                val amplitudes = WaveformShape.amplitudes(seed, count)
                                val barWidth = pitch * 0.6f
                                val corner = CornerRadius(barWidth / 2f)
                                val middle = size.height / 2f
                                val played = CyberBrushes.curve(0f, size.width)
                                val rest = CyberColors.outline
                                onDrawBehind {
                                    val split = shown.value.coerceIn(0f, 1f) * size.width
                                    for (pass in 0..1) {
                                        // Two passes, each clipped to its side of the position, so a bar
                                        // under the orb is split exactly where the orb is.
                                        val left = if (pass == 0) 0f else split
                                        val right = if (pass == 0) split else size.width
                                        if (right <= left) continue
                                        clipRect(left = left, right = right) {
                                            for (i in 0 until count) {
                                                val half = middle * amplitudes[i]
                                                val topLeft = Offset(i * pitch + (pitch - barWidth) / 2f, middle - half)
                                                val barSize = Size(barWidth, half * 2f)
                                                if (pass == 0 && active) {
                                                    drawRoundRect(played, topLeft, barSize, corner)
                                                } else {
                                                    drawRoundRect(rest, topLeft, barSize, corner, alpha = if (active) 0.9f else 0.5f)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                )
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val timeStyle = SonicNumerals.axis.copy(color = if (active) CyberColors.orchid else CyberColors.mutedText)
            Text(text = PlayerFormat.duration(dragFraction?.let { (it * durationMs).toLong() } ?: positionMs), style = timeStyle)
            Text(text = PlayerFormat.duration(durationMs), style = timeStyle)
        }
    }
}

private const val THUMB_PULSE_MS = 2000
