package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.theme.SonicNumerals
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** One adjustable EQ band as the graph shows it: what the device reported, plus its gain. */
internal data class GraphBand(
    val index: Int,
    val frequencyHz: Float,
    val gainDb: Float,
    val minGainDb: Float,
    val maxGainDb: Float,
)

/** Step of the − and + buttons and of TalkBack's adjust gesture. */
internal const val GAIN_STEP_DB = 0.5f

private val GraphHeight = 248.dp
private val GutterStart = 30.dp
private val GutterBottom = 22.dp
private val PlotTop = 18.dp
private val PlotEnd = 12.dp
private val NodeTarget = 48.dp

// Where the plot sits inside the graph; shared by drawing, touch and the TalkBack targets so
// all three agree on where a band is.
private class PlotFrame(
    size: Size,
    density: Density,
    private val scaleDb: Float,
) {
    val left = with(density) { GutterStart.toPx() }
    val top = with(density) { PlotTop.toPx() }
    val right = size.width - with(density) { PlotEnd.toPx() }
    val bottom = size.height - with(density) { GutterBottom.toPx() }
    val width = right - left
    val height = bottom - top

    fun x(frequencyHz: Float): Float = left + CurveGeometry.xFraction(frequencyHz) * width

    fun y(gainDb: Float): Float = top + (1f - (gainDb.coerceIn(-scaleDb, scaleDb) + scaleDb) / (2f * scaleDb)) * height

    fun fractionAt(x: Float): Float = ((x - left) / width).coerceIn(0f, 1f)

    fun dbPerPx(): Float = 2f * scaleDb / height
}

/**
 * The equalizer's frequency response graph and its main control.
 *
 * Frequency runs logarithmically from 20 Hz to 20 kHz, gain on a fixed scale from the
 * device's band range. The points are the gains the bands are really set to; the line
 * between them is a smooth interpolation for reading the shape, not a measured or
 * computed filter response - Android's system equalizer does not report its filter
 * shapes. Dragging anywhere picks the nearest band and moves it up or down.
 */
@Composable
internal fun ResponseGraph(
    curve: List<TargetPoint>,
    bands: List<GraphBand>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onGainChange: (bandIndex: Int, gainDb: Float) -> Unit,
    processing: Boolean,
    stateLabel: String?,
    hotFrequencyHz: Float?,
    description: String,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val sorted = bands.sortedBy { it.frequencyHz }
    val scaleDb = CurveGeometry.scaleDb(sorted.minOfOrNull { it.minGainDb }, sorted.maxOfOrNull { it.maxGainDb })
    var dragging by remember { mutableStateOf(false) }
    // A preset change glides; a drag follows the finger without delay.
    val shownGains =
        sorted.map { band ->
            key(band.index) {
                animateFloatAsState(
                    targetValue = band.gainDb,
                    animationSpec = if (dragging) snap() else tween(durationMillis = 180),
                    label = "bandGain",
                ).value
            }
        }
    val curveColor by animateColorAsState(
        targetValue = if (processing) style.accent else style.mutedText,
        animationSpec = tween(durationMillis = 150),
        label = "curveColor",
    )
    val currentBands by rememberUpdatedState(sorted)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val axisStyle = SonicNumerals.axis.copy(color = style.mutedText)
    val stateStyle = MaterialTheme.typography.labelMedium.copy(color = style.mutedText)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(GraphHeight)) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(GraphHeight)
                    .semantics { contentDescription = description }
                    .pointerInput(scaleDb) {
                        detectTapGestures { offset ->
                            val frame = PlotFrame(Size(size.width.toFloat(), size.height.toFloat()), this, scaleDb)
                            nearestBand(currentBands, frame.fractionAt(offset.x))?.let { currentOnSelect(it.index) }
                        }
                    }.pointerInput(scaleDb) {
                        var active: GraphBand? = null
                        var gain = 0f
                        var sent = 0f
                        var dbPerPx = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                val frame = PlotFrame(Size(size.width.toFloat(), size.height.toFloat()), this, scaleDb)
                                dbPerPx = frame.dbPerPx()
                                active = nearestBand(currentBands, frame.fractionAt(offset.x))
                                active?.let { band ->
                                    gain = band.gainDb
                                    sent = band.gainDb
                                    dragging = true
                                    currentOnSelect(band.index)
                                }
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            val band = active ?: return@detectDragGestures
                            change.consume()
                            val next = (gain - drag.y * dbPerPx).coerceIn(band.minGainDb, band.maxGainDb)
                            // A tick when the band crosses 0 dB, so flat can be found without looking.
                            if (sign(next) != sign(gain) && next != 0f) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            gain = next
                            val rounded = (next * 10f).roundToInt() / 10f
                            if (rounded != sent) {
                                sent = rounded
                                currentOnGainChange(band.index, rounded)
                            }
                        }
                    },
        ) {
            val frame = PlotFrame(size, this, scaleDb)
            drawGrid(frame, scaleDb, style, measurer, axisStyle)
            val selected = sorted.indexOfFirst { it.index == selectedIndex }
            if (selected >= 0) drawSelection(frame, sorted, selected, style)

            val points =
                if (sorted.isEmpty()) curve else sorted.mapIndexed { i, band -> TargetPoint(band.frequencyHz, shownGains[i]) }
            if (points.isNotEmpty()) {
                val line = curvePath(frame, points)
                val zeroY = frame.y(0f)
                val fill =
                    Path().apply {
                        addPath(line)
                        lineTo(frame.right, zeroY)
                        lineTo(frame.left, zeroY)
                        close()
                    }
                drawPath(fill, color = curveColor.copy(alpha = if (processing) 0.10f else 0.05f))
                drawPath(
                    path = line,
                    color = curveColor,
                    style =
                        Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (processing) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                        ),
                )
            }

            hotFrequencyHz?.let { drawHotMarker(frame, it, style) }

            sorted.forEachIndexed { i, band ->
                drawNode(
                    center = Offset(frame.x(band.frequencyHz), frame.y(shownGains[i])),
                    selected = i == selected,
                    processing = processing,
                    hot = hotFrequencyHz != null && band.frequencyHz == hotFrequencyHz,
                    style = style,
                )
            }

            stateLabel?.let { label ->
                val layout = measurer.measure(AnnotatedString(label), style = stateStyle)
                drawText(layout, topLeft = Offset(frame.right - layout.size.width - 6.dp.toPx(), frame.top + 6.dp.toPx()))
            }
        }

        // Invisible TalkBack targets: each band becomes an adjustable value at its point.
        val plotWidth = maxWidth - GutterStart - PlotEnd
        val plotHeight = GraphHeight - PlotTop - GutterBottom
        sorted.forEachIndexed { i, band ->
            val label = CurveGeometry.formatFrequency(band.frequencyHz)
            val value = CurveGeometry.formatGain(band.gainDb)
            val x = GutterStart + plotWidth * CurveGeometry.xFraction(band.frequencyHz)
            val y = PlotTop + plotHeight * (1f - (shownGains[i].coerceIn(-scaleDb, scaleDb) + scaleDb) / (2f * scaleDb))
            Box(
                modifier =
                    Modifier
                        .offset(x = x - NodeTarget / 2, y = y - NodeTarget / 2)
                        .size(NodeTarget)
                        .semantics {
                            contentDescription = label
                            stateDescription = value
                            progressBarRangeInfo = ProgressBarRangeInfo(band.gainDb, band.minGainDb..band.maxGainDb)
                            setProgress { target ->
                                val stepped = (target / GAIN_STEP_DB).roundToInt() * GAIN_STEP_DB
                                onGainChange(band.index, stepped.coerceIn(band.minGainDb, band.maxGainDb))
                                true
                            }
                            onClick {
                                onSelect(band.index)
                                true
                            }
                        },
            )
        }
    }
}

private fun nearestBand(
    bands: List<GraphBand>,
    fraction: Float,
): GraphBand? = CurveGeometry.nearestIndex(bands.map { it.frequencyHz }, fraction)?.let { bands[it] }

private fun DrawScope.drawGrid(
    frame: PlotFrame,
    scaleDb: Float,
    style: EqualizerDesignStyle,
    measurer: TextMeasurer,
    axisStyle: TextStyle,
) {
    val hairline = 1.dp.toPx()
    drawRect(color = style.surface, topLeft = Offset(frame.left, frame.top), size = Size(frame.width, frame.height))

    CurveGeometry.minorFrequencies.forEach { hz ->
        val x = frame.x(hz)
        drawLine(style.border.copy(alpha = 0.35f), Offset(x, frame.top), Offset(x, frame.bottom), hairline)
    }
    CurveGeometry.labelledFrequencies.forEach { hz ->
        val x = frame.x(hz)
        drawLine(style.border, Offset(x, frame.top), Offset(x, frame.bottom), hairline)
        val layout = measurer.measure(AnnotatedString(CurveGeometry.axisLabel(hz)), style = axisStyle)
        val labelX = (x - layout.size.width / 2f).coerceIn(frame.left, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(labelX, frame.bottom + 4.dp.toPx()))
    }

    CurveGeometry.gainTicks(scaleDb).forEach { gain ->
        val y = frame.y(gain)
        if (gain != 0f) drawLine(style.border, Offset(frame.left, y), Offset(frame.right, y), hairline)
        val layout = measurer.measure(AnnotatedString(CurveGeometry.gainTickLabel(gain)), style = axisStyle)
        val labelY = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
        drawText(layout, topLeft = Offset(frame.left - 6.dp.toPx() - layout.size.width, labelY))
    }
    // The 0 dB reference: brighter than the grid, thinner than the curve.
    drawLine(style.mutedText, Offset(frame.left, frame.y(0f)), Offset(frame.right, frame.y(0f)), hairline)

    // Units, printed once each: dB above the gain labels, Hz below them.
    val db = measurer.measure(AnnotatedString("dB"), style = axisStyle)
    drawText(db, topLeft = Offset(frame.left - 6.dp.toPx() - db.size.width, 0f))
    val hz = measurer.measure(AnnotatedString("Hz"), style = axisStyle)
    drawText(hz, topLeft = Offset(frame.left - 6.dp.toPx() - hz.size.width, frame.bottom + 4.dp.toPx()))
}

// The selected band: a faint column over the frequencies it covers (halfway to its
// neighbours on the log axis) and a hairline at its center.
private fun DrawScope.drawSelection(
    frame: PlotFrame,
    bands: List<GraphBand>,
    selected: Int,
    style: EqualizerDesignStyle,
) {
    val band = bands[selected]
    val lowEdge = bands.getOrNull(selected - 1)?.let { sqrt(it.frequencyHz * band.frequencyHz) } ?: CurveGeometry.MIN_HZ
    val highEdge = bands.getOrNull(selected + 1)?.let { sqrt(it.frequencyHz * band.frequencyHz) } ?: CurveGeometry.MAX_HZ
    val left = frame.x(lowEdge)
    drawRect(
        color = style.text.copy(alpha = 0.05f),
        topLeft = Offset(left, frame.top),
        size = Size(frame.x(highEdge) - left, frame.height),
    )
    val x = frame.x(band.frequencyHz)
    drawLine(style.accent.copy(alpha = 0.45f), Offset(x, frame.top), Offset(x, frame.bottom), 1.dp.toPx())
}

private fun curvePath(
    frame: PlotFrame,
    points: List<TargetPoint>,
): Path {
    val samples = 160
    return Path().apply {
        for (i in 0..samples) {
            val fraction = i / samples.toFloat()
            val x = frame.left + fraction * frame.width
            val y = frame.y(CurveGeometry.gainAt(points, CurveGeometry.frequencyAt(fraction)))
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
}

// Where heavy limiting is expected: a small red wedge on the top edge, no glow.
private fun DrawScope.drawHotMarker(
    frame: PlotFrame,
    frequencyHz: Float,
    style: EqualizerDesignStyle,
) {
    val x = frame.x(frequencyHz)
    val half = 5.dp.toPx()
    val wedge =
        Path().apply {
            moveTo(x - half, frame.top)
            lineTo(x + half, frame.top)
            lineTo(x, frame.top + half * 1.4f)
            close()
        }
    drawPath(wedge, color = style.hot)
}

private fun DrawScope.drawNode(
    center: Offset,
    selected: Boolean,
    processing: Boolean,
    hot: Boolean,
    style: EqualizerDesignStyle,
) {
    val ring =
        if (hot) {
            style.hot
        } else if (processing) {
            style.text
        } else {
            style.mutedText
        }
    if (selected) {
        drawCircle(color = style.background, radius = 9.dp.toPx(), center = center)
        drawCircle(color = if (processing) style.accent else style.mutedText, radius = 7.dp.toPx(), center = center)
        drawCircle(color = style.background, radius = 2.5.dp.toPx(), center = center)
        drawCircle(color = ring.copy(alpha = 0.6f), radius = 13.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    } else {
        drawCircle(color = style.background, radius = 5.5.dp.toPx(), center = center)
        drawCircle(color = ring, radius = 5.5.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
    }
}

/**
 * The selected band's frequency and gain in large figures, with − and + for steps of
 * [GAIN_STEP_DB] - the precise way to set a value that a finger on the graph only roughs in.
 */
@Composable
internal fun BandReadout(
    band: GraphBand?,
    onGainChange: (bandIndex: Int, gainDb: Float) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (band == null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = style.mutedText,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }
        val frequency = CurveGeometry.formatFrequency(band.frequencyHz)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = frequency, style = SonicNumerals.value, color = style.mutedText)
            Text(text = CurveGeometry.formatGain(band.gainDb), style = SonicNumerals.readout, color = style.text)
        }
        StepButton(
            icon = { Icon(Icons.Default.Remove, contentDescription = null) },
            description = stringResource(R.string.graph_band_lower, frequency),
            enabled = band.gainDb > band.minGainDb,
            onClick = { onGainChange(band.index, (band.gainDb - GAIN_STEP_DB).coerceAtLeast(band.minGainDb)) },
            style = style,
        )
        StepButton(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            description = stringResource(R.string.graph_band_raise, frequency),
            enabled = band.gainDb < band.maxGainDb,
            onClick = { onGainChange(band.index, (band.gainDb + GAIN_STEP_DB).coerceAtMost(band.maxGainDb)) },
            style = style,
        )
    }
}

@Composable
private fun StepButton(
    icon: @Composable () -> Unit,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    style: EqualizerDesignStyle,
    size: Dp = 48.dp,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (enabled) style.border else style.border.copy(alpha = 0.4f)),
        modifier = Modifier.size(size).semantics { contentDescription = description },
    ) {
        icon()
    }
}
