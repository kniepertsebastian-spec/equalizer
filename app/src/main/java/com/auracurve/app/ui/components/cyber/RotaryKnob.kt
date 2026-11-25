package com.auracurve.app.ui.components.cyber

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.SonicNumerals
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private const val LED_SEGMENTS = 24
private const val LED_GAP_DEGREES = 2.6f
private const val TICKS = 40

/** Finger travel for the whole range when dragging up or down (Design/DESIGN_SPEC.md §4). */
private val KnobTravel = 220.dp

/**
 * A rotary knob for a gain macro: a ring of 24 LED segments lit from 0 dB toward the value, a
 * brushed metal cap with a glowing marker, and below it the value in a pill and a [description].
 *
 * Drag up or down to turn it (the whole range over 220 dp), or turn it with the finger when it
 * lands on the outer ring. Double tap resets to 0 dB. Values snap to [step]; each step ticks and
 * crossing 0 dB clicks. TalkBack reads it like the slider it replaces: [label], [valueText], and
 * an adjustable value in steps of [step].
 */
@Composable
fun RotaryKnob(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    ringColors: List<Color>,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    knobSize: Dp = 112.dp,
    step: Float = 0.5f,
    enabled: Boolean = true,
) {
    val colors = ringColors.ifEmpty { listOf(CyberColors.cyan) }
    val pillBorder = remember(colors) { if (colors.size > 1) Brush.horizontalGradient(colors) else SolidColor(colors.first()) }
    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CyberColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        KnobDial(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = colors,
            label = label,
            valueText = valueText,
            step = step,
            enabled = enabled,
            modifier = Modifier.widthIn(max = knobSize).fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = valueText,
            style = SonicNumerals.value,
            color = CyberColors.text,
            maxLines = 1,
            modifier =
                Modifier
                    .background(CyberColors.surfaceHigh, PillShape)
                    .border(CyberDims.border, pillBorder, PillShape)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = CyberColors.mutedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun KnobDial(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    colors: List<Color>,
    label: String,
    valueText: String,
    step: Float,
    enabled: Boolean,
    modifier: Modifier,
) {
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // A preset change turns the knob briefly instead of jumping; read only while drawing.
    val shown = animateFloatAsState(targetValue = value, animationSpec = tween(140), label = "knob")
    val haptics = LocalHapticFeedback.current
    val span = valueRange.endInclusive - valueRange.start
    val steps = if (step > 0f && span > 0f) ((span / step).roundToInt() - 1).coerceAtLeast(0) else 0

    Box(
        modifier =
            modifier
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(value.coerceIn(valueRange.start, valueRange.endInclusive), valueRange, steps)
                    if (enabled) {
                        setProgress { target ->
                            currentOnValueChange(KnobMath.snap(target, step, valueRange))
                            true
                        }
                    } else {
                        disabled()
                    }
                }.pointerInput(enabled, valueRange, step) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            val reset = 0f.coerceIn(valueRange.start, valueRange.endInclusive)
                            if (reset != currentValue.value) {
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                currentOnValueChange(reset)
                            }
                        },
                    )
                }.pointerInput(enabled, valueRange, step) {
                    if (!enabled) return@pointerInput
                    val travel = KnobTravel.toPx()
                    var raw = 0f
                    var sent = 0f
                    var radial = false
                    var lastAngle = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            raw = currentValue.value
                            sent = raw
                            val dx = offset.x - size.width / 2f
                            val dy = offset.y - size.height / 2f
                            // On the outer ring the knob follows the finger round; inside, up and down.
                            radial = hypot(dx, dy) > 0.6f * size.width / 2f
                            lastAngle = KnobMath.pointerAngle(dx, dy)
                        },
                    ) { change, drag ->
                        change.consume()
                        raw +=
                            if (radial) {
                                val angle =
                                    KnobMath.pointerAngle(change.position.x - size.width / 2f, change.position.y - size.height / 2f)
                                val turn = KnobMath.angleDelta(lastAngle, angle)
                                lastAngle = angle
                                KnobMath.turnDelta(turn, valueRange)
                            } else {
                                KnobMath.dragDelta(drag.y, travel, valueRange)
                            }
                        raw = raw.coerceIn(valueRange.start, valueRange.endInclusive)
                        val snapped = KnobMath.snap(raw, step, valueRange)
                        if (snapped != sent) {
                            // A click on landing on 0 dB or jumping across it; a tick for every other step.
                            val crossed = snapped == 0f || sign(snapped) == -sign(sent)
                            haptics.performHapticFeedback(
                                if (crossed) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick,
                            )
                            sent = snapped
                            currentOnValueChange(snapped)
                        }
                    }
                }.drawWithCache {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val ledWidth = radius * 0.13f
                    val ledRadius = radius - ledWidth * 0.9f
                    val ledTopLeft = Offset(center.x - ledRadius, center.y - ledRadius)
                    val ledSize = Size(ledRadius * 2f, ledRadius * 2f)
                    val ledStroke = Stroke(width = ledWidth, cap = StrokeCap.Butt)
                    val ledHalo = Stroke(width = ledWidth * 1.9f, cap = StrokeCap.Butt)
                    val segmentSweep = KnobMath.SWEEP_DEGREES / LED_SEGMENTS
                    val segmentColors = Array(LED_SEGMENTS) { colorAt(colors, (it + 0.5f) / LED_SEGMENTS) }
                    val unlit = CyberColors.outline.copy(alpha = 0.45f)

                    val tickOuter = ledRadius - ledWidth * 0.9f
                    val tickInner = tickOuter - radius * 0.06f
                    val ticks =
                        Path().apply {
                            for (i in 0..TICKS) {
                                val angle = Math.toRadians((KnobMath.START_DEGREES + i * KnobMath.SWEEP_DEGREES / TICKS).toDouble())
                                val s = sin(angle).toFloat()
                                val c = cos(angle).toFloat()
                                moveTo(center.x + s * tickInner, center.y - c * tickInner)
                                lineTo(center.x + s * tickOuter, center.y - c * tickOuter)
                            }
                        }
                    val tickStroke = Stroke(width = 1.dp.toPx())

                    val capRadius = tickInner - radius * 0.06f
                    val bevel = Stroke(width = 2.dp.toPx())
                    val shine =
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                            center = Offset(center.x - capRadius * 0.35f, center.y - capRadius * 0.4f),
                            radius = capRadius * 0.9f,
                        )
                    val markerInner = capRadius * 0.35f
                    val markerOuter = capRadius * 0.86f
                    val markerWidth = 3.dp.toPx()
                    val glowPaint =
                        Paint().apply {
                            style = PaintingStyle.Stroke
                            strokeWidth = markerWidth * 2f
                            strokeCap = StrokeCap.Round
                            nativePaint.maskFilter = BlurMaskFilter(CyberGlow.thumb.radius.toPx() / 2f, BlurMaskFilter.Blur.NORMAL)
                        }

                    onDrawBehind {
                        val v = shown.value
                        val lit = KnobMath.litSegments(v, valueRange, LED_SEGMENTS)
                        for (i in 0 until LED_SEGMENTS) {
                            // drawArc counts from 3 o'clock, the dial from 12 o'clock.
                            val start = KnobMath.START_DEGREES - 90f + i * segmentSweep + LED_GAP_DEGREES / 2f
                            val sweep = segmentSweep - LED_GAP_DEGREES
                            if (i in lit) {
                                drawArc(segmentColors[i].copy(alpha = 0.22f), start, sweep, false, ledTopLeft, ledSize, style = ledHalo)
                                drawArc(segmentColors[i], start, sweep, false, ledTopLeft, ledSize, style = ledStroke)
                            } else {
                                drawArc(unlit, start, sweep, false, ledTopLeft, ledSize, style = ledStroke)
                            }
                        }
                        drawPath(ticks, CyberColors.mutedText.copy(alpha = 0.35f), style = tickStroke)

                        drawCircle(CyberColors.void, capRadius + bevel.width, center)
                        drawCircle(CyberBrushes.metal, capRadius, center)
                        drawCircle(shine, capRadius, center)
                        drawCircle(CyberColors.void.copy(alpha = 0.7f), capRadius, center, style = bevel)

                        val angle = Math.toRadians(KnobMath.angleFor(v, valueRange).toDouble())
                        val s = sin(angle).toFloat()
                        val c = cos(angle).toFloat()
                        val from = Offset(center.x + s * markerInner, center.y - c * markerInner)
                        val to = Offset(center.x + s * markerOuter, center.y - c * markerOuter)
                        val t = (KnobMath.angleFor(v, valueRange) - KnobMath.START_DEGREES) / KnobMath.SWEEP_DEGREES
                        val markerColor = segmentColors[(t * LED_SEGMENTS).toInt().coerceIn(0, LED_SEGMENTS - 1)]
                        if (enabled) {
                            glowPaint.color = markerColor
                            glowPaint.alpha = CyberGlow.thumb.alpha
                            drawIntoCanvas { it.drawLine(from, to, glowPaint) }
                        }
                        drawLine(markerColor, from, to, markerWidth, StrokeCap.Round)
                    }
                },
    )
}

/** The ring color at [t] (0 = lowest value, 1 = highest) along [colors]. */
private fun colorAt(
    colors: List<Color>,
    t: Float,
): Color {
    if (colors.size == 1) return colors.first()
    val position = t.coerceIn(0f, 1f) * (colors.size - 1)
    val index = position.toInt().coerceAtMost(colors.size - 2)
    return lerp(colors[index], colors[index + 1], position - index)
}
