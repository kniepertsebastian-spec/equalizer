package com.auracurve.app.ui.components.cyber

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.auracurve.app.ui.theme.CyberColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val PARTICLES = 40
private const val PAUSED_ALPHA = 0.35f
private const val LOADING_ALPHA = 0.25f

/**
 * The crown of light rays around the cover on Now Playing, orchid to cyan around the circle.
 *
 * It does not listen to the audio (no microphone or visualizer permission; decision E2/E3): the
 * rays move to a seeded pattern while [playing], with a soft beat, and stand still at a third of
 * their light when paused. [seed] comes from the track, so each track has its own crown. Purely
 * decorative, so TalkBack skips it. The cover goes on top, inside [innerRadiusFraction].
 */
@Composable
fun RadialVisualizer(
    playing: Boolean,
    seed: Long,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    innerRadiusFraction: Float = 0.62f,
) {
    val rays = remember(seed) { SyntheticRays(seed = seed) }
    val particles = remember(seed) { Particles(seed) }
    val clock = rememberMotionClock(running = playing && !loading)
    Spacer(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .graphicsLayer()
                .drawWithCache {
                    val outer = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val inner = outer * innerRadiusFraction
                    val start = inner + 4.dp.toPx()
                    val reach = outer - start
                    val count = rays.count
                    val rayWidth = (2f * PI.toFloat() * start / count * 0.45f).coerceAtLeast(1.dp.toPx())
                    val sweep =
                        Brush.sweepGradient(
                            listOf(
                                CyberColors.orchid,
                                CyberColors.violet,
                                CyberColors.cyan,
                                CyberColors.violet,
                                CyberColors.orchid,
                            ),
                            center = center,
                        )
                    val ring = Stroke(width = 2.dp.toPx())
                    val halo = Stroke(width = 8.dp.toPx())
                    val dot = 1.5.dp.toPx()
                    val lengths = FloatArray(count)
                    // Each ray's direction, worked out once; drawing only scales them.
                    val cosines = FloatArray(count) { cos(it * 2f * PI.toFloat() / count - PI.toFloat() / 2f) }
                    val sines = FloatArray(count) { sin(it * 2f * PI.toFloat() / count - PI.toFloat() / 2f) }
                    onDrawBehind {
                        val seconds = clock.floatValue
                        val alpha =
                            when {
                                loading -> LOADING_ALPHA
                                playing -> 1f
                                else -> PAUSED_ALPHA
                            }
                        rays.lengths(seconds, lengths)
                        val scale = if (loading) 0.3f else 1f
                        for (i in 0 until count) {
                            val length = reach * lengths[i] * scale
                            if (length < 1f) continue
                            val from = Offset(center.x + cosines[i] * start, center.y + sines[i] * start)
                            val to = Offset(from.x + cosines[i] * length, from.y + sines[i] * length)
                            drawLine(sweep, from, to, rayWidth, StrokeCap.Round, alpha = alpha, blendMode = BlendMode.Plus)
                        }
                        // The neon ring the cover sits in.
                        drawCircle(sweep, inner + halo.width / 2f, center, alpha = 0.18f * alpha, style = halo)
                        drawCircle(sweep, inner, center, alpha = 0.9f * alpha, style = ring)
                        if (playing && !loading) particles.draw(this, seconds, center, start, reach, dot, sweep)
                    }
                },
    )
}

/**
 * Small sparks that drift outward from the ring and fade, in seeded directions. Positions are a
 * function of time, so drawing a frame allocates nothing.
 */
private class Particles(
    seed: Long,
) {
    private val cosines: FloatArray
    private val sines: FloatArray
    private val phases: FloatArray
    private val speeds: FloatArray

    init {
        val random = SeededRandom(seed xor 0x7A51C1E5L)
        val angles = FloatArray(PARTICLES) { random.nextFloat() * 2f * PI.toFloat() }
        cosines = FloatArray(PARTICLES) { cos(angles[it]) }
        sines = FloatArray(PARTICLES) { sin(angles[it]) }
        phases = FloatArray(PARTICLES) { random.nextFloat() }
        speeds = FloatArray(PARTICLES) { 0.12f + random.nextFloat() * 0.25f }
    }

    fun draw(
        scope: androidx.compose.ui.graphics.drawscope.DrawScope,
        seconds: Float,
        center: Offset,
        start: Float,
        reach: Float,
        radius: Float,
        brush: Brush,
    ) = with(scope) {
        for (i in 0 until PARTICLES) {
            val progress = (phases[i] + seconds * speeds[i]) % 1f
            val distance = start + reach * (0.2f + progress * 1.1f)
            val position = Offset(center.x + cosines[i] * distance, center.y + sines[i] * distance)
            drawCircle(brush, radius, position, alpha = (1f - progress) * 0.8f, blendMode = BlendMode.Plus)
        }
    }
}
