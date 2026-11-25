package com.auracurve.app.ui.components.cyber

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.equalizer.CurveGeometry
import com.auracurve.app.ui.equalizer.GAIN_STEP_DB
import com.auracurve.app.ui.equalizer.GraphBand
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberCurveBass
import com.auracurve.app.ui.theme.CyberCurveMid
import com.auracurve.app.ui.theme.CyberCurveTreble
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.SonicNumerals
import com.auracurve.app.ui.theme.SonicRule
import com.auracurve.app.ui.theme.drawInsetShade
import com.auracurve.app.ui.theme.drawStripes
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** How the spectrum bars behind the curve behave (decision E2). They never claim to measure. */
enum class SpectrumMode {
    /** No bars: the Device tab, and whenever the EQ is off or bypassed. */
    OFF,

    /** Dim, still bars shaped by the curve: the EQ runs but nothing is known to play through it. */
    STATIC,

    /** Bars moving around the curve's shape: only while the EQ is active and a track plays. */
    LIVE,
}

private val SpectrumGraphHeight = 248.dp
private val GutterStart = 30.dp
private val GutterBottom = 22.dp
private val PlotTop = 18.dp
private val PlotEnd = 12.dp
private val NodeTarget = 48.dp
private val HcGutterStart = 40.dp
private val HcGutterBottom = 26.dp
private val HcPlotTop = 16.dp
private val HcPlotEnd = 16.dp
private const val CURVE_SAMPLES = 160

// Where the plot sits inside the graph; shared by the drawing layers, touch and the TalkBack
// targets so all of them agree on where a band is. The same frame as ResponseGraph's.
private class GraphFrame(
    totalWidth: Float,
    totalHeight: Float,
    density: Density,
    private val scaleDb: Float,
    // The hardcore look frames the plot in a screwed-on panel: wider gutters on every side.
    hardcore: Boolean = false,
) {
    val left = with(density) { (if (hardcore) HcGutterStart else GutterStart).toPx() }
    val top = with(density) { (if (hardcore) HcPlotTop else PlotTop).toPx() }
    val right = totalWidth - with(density) { (if (hardcore) HcPlotEnd else PlotEnd).toPx() }
    val bottom = totalHeight - with(density) { (if (hardcore) HcGutterBottom else GutterBottom).toPx() }
    val width = right - left
    val height = bottom - top

    fun xAt(fraction: Float): Float = left + fraction * width

    fun x(frequencyHz: Float): Float = xAt(CurveGeometry.xFraction(frequencyHz))

    fun y(gainDb: Float): Float = top + (1f - (gainDb.coerceIn(-scaleDb, scaleDb) + scaleDb) / (2f * scaleDb)) * height

    fun fractionAt(x: Float): Float = ((x - left) / width).coerceIn(0f, 1f)

    fun dbPerPx(): Float = 2f * scaleDb / height
}

// What the curve layer draws, handed over in one State so the layer's cache survives a drag:
// a new value only redraws.
private class GraphInputs(
    val bands: List<GraphBand>,
    val gains: List<State<Float>>,
    val curve: List<TargetPoint>,
    val selected: Int,
    val processing: Boolean,
    val hotFrequencyHz: Float?,
    val collapsed: Boolean,
)

/**
 * The Cyber equalizer graph: the response curve in the bass-to-treble gradient with a soft glow,
 * over spectrum bars and a fine grid. It takes the parameters of ResponseGraph and behaves the
 * same: tap selects the nearest band, a drag moves it, every band is an adjustable value for
 * TalkBack. The points are the gains the bands are set to; the line between them is a smooth
 * interpolation for reading the shape, not a measured filter response.
 *
 * The bars are synthetic (decision E2): shaped by the curve, moving only in [SpectrumMode.LIVE].
 * Grid, bars and curve are separate layers, so moving bars never redraw the curve or the labels.
 */
@Composable
internal fun SpectrumEqGraph(
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
    height: Dp = SpectrumGraphHeight,
    // A short help line drawn into the plot's top left, so it needs no row of its own.
    hint: String? = null,
    // Folded while the options below are scrolled: a flat overview without points or gestures.
    collapsed: Boolean = false,
    spectrum: SpectrumMode = SpectrumMode.STATIC,
    // The Device tab: a thicker curve with a stronger glow, as the one thing on the plot.
    emphasis: Boolean = false,
) {
    val sorted = remember(bands) { bands.sortedBy { it.frequencyHz } }
    val sortedCurve = remember(curve) { curve.sortedBy { it.frequencyHz } }
    val scaleDb = CurveGeometry.scaleDb(sorted.minOfOrNull { it.minGainDb }, sorted.maxOfOrNull { it.maxGainDb })
    var dragging by remember { mutableStateOf(false) }
    // A preset change glides; a drag follows the finger without delay. Read only while drawing.
    val gains =
        sorted.map { band ->
            key(band.index) {
                animateFloatAsState(
                    targetValue = band.gainDb,
                    animationSpec = if (dragging) snap() else tween(durationMillis = 180),
                    label = "bandGain",
                )
            }
        }
    val selected = if (collapsed) -1 else sorted.indexOfFirst { it.index == selectedIndex }
    val inputs = rememberUpdatedState(GraphInputs(sorted, gains, sortedCurve, selected, processing, hotFrequencyHz, collapsed))
    val currentBands by rememberUpdatedState(sorted)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnGainChange by rememberUpdatedState(onGainChange)
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val hardcore = LocalHardcoreLook.current
    val axisStyle =
        if (hardcore) HardcoreType.axis.copy(color = HcAxisText) else SonicNumerals.axis.copy(color = CyberColors.mutedText)
    val stateStyle =
        if (hardcore) {
            HardcoreType.label.copy(color = HardcoreColors.ash)
        } else {
            MaterialTheme.typography.labelMedium.copy(color = CyberColors.mutedText)
        }
    val hintStyle =
        if (hardcore) {
            HardcoreType.small.copy(
                color = HardcoreColors.ash,
            )
        } else {
            MaterialTheme.typography.labelSmall.copy(color = CyberColors.mutedText)
        }

    val synthetic = remember { SyntheticSpectrum() }
    val rest =
        remember(sorted, sortedCurve, scaleDb) {
            val points = if (sorted.isEmpty()) sortedCurve else sorted.map { TargetPoint(it.frequencyHz, it.gainDb) }
            synthetic.rest(points, scaleDb)
        }
    val restState = rememberUpdatedState(rest)
    val showBars = spectrum != SpectrumMode.OFF && processing && !collapsed
    val live = showBars && spectrum == SpectrumMode.LIVE
    val clock = rememberMotionClock(running = live)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
        // Layer 1: grid, axes and units; also the touch surface and the graph's description.
        Spacer(
            modifier =
                Modifier
                    .matchParentSize()
                    .semantics { contentDescription = description }
                    .pointerInput(scaleDb, collapsed, hardcore) {
                        if (collapsed) return@pointerInput
                        detectTapGestures { offset ->
                            val frame = GraphFrame(size.width.toFloat(), size.height.toFloat(), this, scaleDb, hardcore)
                            nearestBand(currentBands, frame.fractionAt(offset.x))?.let { currentOnSelect(it.index) }
                        }
                    }.pointerInput(scaleDb, collapsed, hardcore) {
                        if (collapsed) return@pointerInput
                        var active: GraphBand? = null
                        var gain = 0f
                        var sent = 0f
                        var dbPerPx = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                val frame = GraphFrame(size.width.toFloat(), size.height.toFloat(), this, scaleDb, hardcore)
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
                    }.drawWithCache {
                        val frame = GraphFrame(size.width, size.height, this, scaleDb, hardcore)
                        if (hardcore) {
                            val grid = HcGridLayer(frame, scaleDb, collapsed, measurer, axisStyle, this, size)
                            onDrawBehind { grid.draw(this) }
                        } else {
                            val grid = GridLayer(frame, scaleDb, collapsed, measurer, axisStyle, this, size)
                            onDrawBehind { grid.draw(this) }
                        }
                    },
        )

        // Layer 2: the bars, in their own layer so a moving frame redraws nothing else.
        if (showBars) {
            Spacer(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer()
                        .drawWithCache {
                            val frame = GraphFrame(size.width, size.height, this, scaleDb, hardcore)
                            val n = synthetic.barCount
                            val slot = frame.width / n
                            val barWidth = slot * 0.62f
                            val hues = Array(n) { spectrumHue(synthetic.fractions[it]) }
                            val bodies =
                                Array(n) {
                                    Brush.verticalGradient(
                                        listOf(hues[it].copy(alpha = 0.8f), hues[it].copy(alpha = 0.1f)),
                                        startY = frame.top,
                                        endY = frame.bottom,
                                    )
                                }
                            val levels = FloatArray(n)
                            val cap = 2.dp.toPx()
                            val alpha = if (live) 0.85f else 0.35f
                            val block = 3.2.dp.toPx()
                            val blockGap = 1.6.dp.toPx()
                            val blockInset = 1.dp.toPx()
                            val blockWidth = slot - 2.2.dp.toPx()
                            val zeroY = frame.y(0f)
                            onDrawBehind {
                                if (live) {
                                    synthetic.animate(restState.value, clock.floatValue, levels)
                                } else {
                                    restState.value.copyInto(levels)
                                }
                                if (hardcore) {
                                    // LED blocks: dark segments, the top one lit dull red above 0 dB.
                                    for (i in 0 until n) {
                                        val h = levels[i] * frame.height
                                        val count = (h / (block + blockGap)).toInt()
                                        val x = frame.left + i * slot + blockInset
                                        for (k in 0 until count) {
                                            val y = frame.bottom - (k + 1) * (block + blockGap) + blockGap
                                            val color =
                                                when {
                                                    k == count - 1 -> if (y < zeroY) HcBlockHot else HcBlockTop
                                                    k < count * 0.45f -> HcBlockLow
                                                    else -> HcBlockMid
                                                }
                                            drawRect(color, Offset(x, y), Size(blockWidth, block), alpha = if (live) 1f else 0.85f)
                                        }
                                    }
                                    return@onDrawBehind
                                }
                                for (i in 0 until n) {
                                    val h = levels[i] * frame.height
                                    if (h < 0.5f) continue
                                    val topLeft = Offset(frame.left + i * slot + (slot - barWidth) / 2f, frame.bottom - h)
                                    drawRect(bodies[i], topLeft, Size(barWidth, h), alpha = alpha)
                                    // A light cap on each bar, added onto what is below like light.
                                    drawRect(hues[i], topLeft, Size(barWidth, cap), alpha = alpha, blendMode = BlendMode.Plus)
                                }
                            }
                        },
            )
        }

        // Layer 3: selection, curve, nodes and labels.
        Spacer(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val frame = GraphFrame(size.width, size.height, this, scaleDb, hardcore)
                        val sampler = CurveSampler(capacity = 16)
                        val line = Path()
                        val fill = Path()
                        val wedge = Path()
                        val lineBrush = CyberBrushes.curve(frame.left, frame.right)
                        val fillBrush = CyberBrushes.curveFill(frame.top, frame.bottom)
                        val lineWidth = (if (emphasis) 3.dp else 2.5.dp).toPx()
                        val lineStroke = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val dashStroke =
                            Stroke(
                                width = lineWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                            )
                        val curveGlow =
                            Paint().apply {
                                style = PaintingStyle.Stroke
                                strokeWidth = lineWidth * 2f
                                strokeCap = StrokeCap.Round
                                strokeJoin = StrokeJoin.Round
                                lineBrush.applyTo(size, this, if (emphasis) 0.7f else 0.5f)
                                nativePaint.maskFilter =
                                    BlurMaskFilter((if (emphasis) 8.dp else 6.dp).toPx(), BlurMaskFilter.Blur.NORMAL)
                            }
                        val nodeGlow =
                            Paint().apply {
                                color = CyberColors.cyan
                                alpha = CyberGlow.thumb.alpha
                                nativePaint.maskFilter = BlurMaskFilter(CyberGlow.thumb.radius.toPx(), BlurMaskFilter.Blur.NORMAL)
                            }
                        val ringStroke = Stroke(width = 1.5.dp.toPx())
                        val haloStroke = Stroke(width = 1.dp.toPx())
                        val small = 5.5.dp.toPx()
                        val large = 9.dp.toPx()
                        val core = 7.dp.toPx()
                        val dot = 2.5.dp.toPx()
                        val halo = 13.dp.toPx()
                        val wedgeHalf = 5.dp.toPx()
                        val selectionFill = if (hardcore) HardcoreColors.bone.copy(alpha = 0.04f) else CyberColors.text.copy(alpha = 0.05f)
                        val selectionLine =
                            if (hardcore) {
                                HardcoreColors.bloodHi.copy(
                                    alpha = 0.5f,
                                )
                            } else {
                                CyberColors.cyan.copy(alpha = 0.45f)
                            }
                        // Hardcore: a bone line over a black under-stroke, hatched red above 0 dB and gray below.
                        val hcLineWidth = (if (emphasis) 2.6.dp else 2.1.dp).toPx()
                        val hcLine = Stroke(width = hcLineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val hcUnder = Stroke(width = (if (emphasis) 6.dp else 5.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val hcDash =
                            Stroke(
                                width = hcLineWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                            )
                        val hatchPitch = 5.dp.toPx()
                        val hatchRed = 1.4.dp.toPx()
                        val hatchGray = 1.2.dp.toPx()
                        val hcNodeGlow =
                            Paint().apply {
                                color = HardcoreColors.bloodHi
                                alpha = 0.55f
                                nativePaint.maskFilter = BlurMaskFilter(8.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                            }

                        val state: TextLayoutResult? = stateLabel?.let { measurer.measure(AnnotatedString(it), style = stateStyle) }
                        val stateTopLeft =
                            state?.let { Offset(frame.right - it.size.width - 6.dp.toPx(), frame.top + 6.dp.toPx()) } ?: Offset.Zero
                        val hintLayout: TextLayoutResult? =
                            if (collapsed || hint == null) {
                                null
                            } else {
                                val reserved = if (stateLabel != null) 72.dp.toPx() else 0f
                                measurer.measure(
                                    AnnotatedString(hint),
                                    style = hintStyle,
                                    constraints = Constraints(maxWidth = (frame.width - 12.dp.toPx() - reserved).toInt().coerceAtLeast(1)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        val hintTopLeft = Offset(frame.left + 6.dp.toPx(), frame.top + 4.dp.toPx())

                        onDrawBehind {
                            val g = inputs.value
                            val count = g.bands.size
                            if (count > 0) {
                                sampler.resize(count)
                                for (i in 0 until count) sampler.put(i, g.bands[i].frequencyHz, g.gains[i].value)
                            } else {
                                sampler.resize(g.curve.size)
                                for (i in g.curve.indices) sampler.put(i, g.curve[i].frequencyHz, g.curve[i].gainDb)
                            }

                            // The selected band: a faint column over the frequencies it covers
                            // (halfway to its neighbours on the log axis) and a line at its center.
                            if (g.selected in 0 until count) {
                                val band = g.bands[g.selected]
                                val low =
                                    if (g.selected >
                                        0
                                    ) {
                                        sqrt(g.bands[g.selected - 1].frequencyHz * band.frequencyHz)
                                    } else {
                                        CurveGeometry.MIN_HZ
                                    }
                                val high =
                                    if (g.selected < count - 1) {
                                        sqrt(g.bands[g.selected + 1].frequencyHz * band.frequencyHz)
                                    } else {
                                        CurveGeometry.MAX_HZ
                                    }
                                val left = frame.x(low)
                                drawRect(selectionFill, Offset(left, frame.top), Size(frame.x(high) - left, frame.height))
                                val x = frame.x(band.frequencyHz)
                                drawLine(selectionLine, Offset(x, frame.top), Offset(x, frame.bottom), 1.dp.toPx())
                            }

                            if (sampler.size > 0) {
                                val zeroY = frame.y(0f)
                                line.rewind()
                                fill.rewind()
                                fill.moveTo(frame.left, zeroY)
                                for (i in 0..CURVE_SAMPLES) {
                                    val fraction = i / CURVE_SAMPLES.toFloat()
                                    val x = frame.xAt(fraction)
                                    val y = frame.y(sampler.gainAt(fraction))
                                    if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
                                    fill.lineTo(x, y)
                                }
                                fill.lineTo(frame.right, zeroY)
                                fill.close()
                                // Running: the gradient line with its glow over a fading fill. Off or
                                // bypassed: a dashed line at half strength, so the state also reads
                                // without color.
                                if (hardcore) {
                                    clipPath(fill) {
                                        if (g.processing) {
                                            clipRect(frame.left, frame.top, frame.right, zeroY) {
                                                drawStripes(HardcoreColors.blood, hatchPitch, hatchRed, rising = true, alpha = 0.5f)
                                            }
                                        }
                                        clipRect(frame.left, zeroY, frame.right, frame.bottom) {
                                            drawStripes(HardcoreColors.ash, hatchPitch, hatchGray, rising = false, alpha = 0.28f)
                                        }
                                    }
                                    if (g.processing) {
                                        drawPath(line, Color.Black, alpha = 0.8f, style = hcUnder)
                                        drawPath(line, HardcoreColors.bone, style = hcLine)
                                    } else {
                                        drawPath(line, HardcoreColors.ash, alpha = 0.7f, style = hcDash)
                                    }
                                } else if (g.processing) {
                                    drawPath(fill, fillBrush)
                                    drawIntoCanvas { it.drawPath(line, curveGlow) }
                                    drawPath(line, lineBrush, style = lineStroke)
                                } else {
                                    drawPath(fill, CyberColors.mutedText, alpha = 0.05f)
                                    drawPath(line, lineBrush, alpha = 0.5f, style = dashStroke)
                                }
                            }

                            // Where heavy limiting is expected: a small red wedge on the top edge.
                            val hot = g.hotFrequencyHz
                            if (hot != null) {
                                val x = frame.x(hot)
                                wedge.rewind()
                                wedge.moveTo(x - wedgeHalf, frame.top)
                                wedge.lineTo(x + wedgeHalf, frame.top)
                                wedge.lineTo(x, frame.top + wedgeHalf * 1.4f)
                                wedge.close()
                                drawPath(wedge, if (hardcore) HardcoreColors.bloodHi else CyberColors.error)
                            }

                            if (!g.collapsed) {
                                for (i in 0 until count) {
                                    val band = g.bands[i]
                                    val center = Offset(frame.x(band.frequencyHz), frame.y(g.gains[i].value))
                                    if (hardcore) {
                                        val ring =
                                            when {
                                                hot != null && band.frequencyHz == hot -> HardcoreColors.bloodHi
                                                g.processing -> HardcoreColors.bone
                                                else -> HardcoreColors.ash
                                            }
                                        if (i == g.selected) {
                                            if (g.processing) drawIntoCanvas { it.drawCircle(center, large, hcNodeGlow) }
                                            drawCircle(HardcoreColors.void, large, center)
                                            drawCircle(if (g.processing) HardcoreColors.bone else HardcoreColors.ash, core, center)
                                            drawCircle(HardcoreColors.void, dot, center)
                                            drawCircle(
                                                color = if (g.processing) HardcoreColors.bloodHi else ring,
                                                radius = halo,
                                                center = center,
                                                alpha = 0.8f,
                                                style = haloStroke,
                                            )
                                        } else {
                                            drawCircle(HardcoreColors.void, small, center)
                                            drawCircle(ring, small, center, style = ringStroke)
                                        }
                                        continue
                                    }
                                    val ring =
                                        when {
                                            hot != null && band.frequencyHz == hot -> CyberColors.error
                                            g.processing -> CyberColors.text
                                            else -> CyberColors.mutedText
                                        }
                                    if (i == g.selected) {
                                        if (g.processing) drawIntoCanvas { it.drawCircle(center, large, nodeGlow) }
                                        drawCircle(CyberColors.void, large, center)
                                        drawCircle(if (g.processing) Color.White else CyberColors.mutedText, core, center)
                                        drawCircle(CyberColors.void, dot, center)
                                        drawCircle(
                                            color = if (g.processing) CyberColors.cyan else ring,
                                            radius = halo,
                                            center = center,
                                            alpha = 0.6f,
                                            style = haloStroke,
                                        )
                                    } else {
                                        drawCircle(CyberColors.void, small, center)
                                        drawCircle(ring, small, center, style = ringStroke)
                                    }
                                }
                            }

                            if (state != null) drawText(state, topLeft = stateTopLeft)
                            if (hintLayout != null) drawText(hintLayout, topLeft = hintTopLeft)
                        }
                    },
        )

        // Invisible TalkBack targets: each band becomes an adjustable value at its point.
        if (!collapsed) {
            val widthPx = constraints.maxWidth.toFloat()
            sorted.forEachIndexed { i, band ->
                val shown = gains[i]
                val label = CurveGeometry.formatFrequency(band.frequencyHz)
                val value = CurveGeometry.formatGain(band.gainDb)
                Box(
                    modifier =
                        Modifier
                            .offset {
                                val frame = GraphFrame(widthPx, height.toPx(), this, scaleDb, hardcore)
                                val half = NodeTarget.toPx() / 2f
                                IntOffset((frame.x(band.frequencyHz) - half).roundToInt(), (frame.y(shown.value) - half).roundToInt())
                            }.size(NodeTarget)
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
}

private fun nearestBand(
    bands: List<GraphBand>,
    fraction: Float,
): GraphBand? = CurveGeometry.nearestIndex(bands.map { it.frequencyHz }, fraction)?.let { bands[it] }

/** A bar's color by its place on the axis: the curve's gradient, orchid in the bass to cyan on top. */
private fun spectrumHue(fraction: Float): Color =
    if (fraction < 0.5f) {
        lerp(CyberCurveBass, CyberCurveMid, fraction * 2f)
    } else {
        lerp(CyberCurveMid, CyberCurveTreble, (fraction - 0.5f) * 2f)
    }

/**
 * The still part of the graph, built once per size and scale: plot ground, grid lines, axis
 * labels and units. Drawing it only replays paths and measured text.
 */
private class GridLayer(
    private val frame: GraphFrame,
    scaleDb: Float,
    sparse: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    axisStyle: androidx.compose.ui.text.TextStyle,
    density: Density,
    canvasSize: Size,
) {
    private val minor = Path()
    private val major = Path()
    private val labels = ArrayList<TextLayoutResult>()
    private val labelPositions = ArrayList<Offset>()
    private val hairline = Stroke(width = 1f)
    private val zeroY = frame.y(0f)

    init {
        val gap = with(density) { 6.dp.toPx() }
        val below = frame.bottom + with(density) { 4.dp.toPx() }
        CurveGeometry.minorFrequencies.forEach { hz ->
            val x = frame.x(hz)
            minor.moveTo(x, frame.top)
            minor.lineTo(x, frame.bottom)
        }
        CurveGeometry.labelledFrequencies.forEach { hz ->
            val x = frame.x(hz)
            major.moveTo(x, frame.top)
            major.lineTo(x, frame.bottom)
            val layout = measurer.measure(AnnotatedString(CurveGeometry.axisLabel(hz)), style = axisStyle)
            labels += layout
            labelPositions += Offset((x - layout.size.width / 2f).coerceIn(frame.left, canvasSize.width - layout.size.width), below)
        }
        CurveGeometry.gainTicks(scaleDb).forEach { gain ->
            val y = frame.y(gain)
            if (gain != 0f) {
                major.moveTo(frame.left, y)
                major.lineTo(frame.right, y)
            }
            // Folded graph: only 0 dB and the outer ticks are labelled, the rest would overlap.
            if (sparse && gain != 0f && abs(gain) != scaleDb) return@forEach
            val layout = measurer.measure(AnnotatedString(CurveGeometry.gainTickLabel(gain)), style = axisStyle)
            labels += layout
            labelPositions +=
                Offset(
                    frame.left - gap - layout.size.width,
                    (y - layout.size.height / 2f).coerceIn(0f, canvasSize.height - layout.size.height),
                )
        }
        // Units, printed once each: dB above the gain labels, Hz below them.
        val db = measurer.measure(AnnotatedString("dB"), style = axisStyle)
        labels += db
        labelPositions += Offset(frame.left - gap - db.size.width, 0f)
        val hz = measurer.measure(AnnotatedString("Hz"), style = axisStyle)
        labels += hz
        labelPositions += Offset(frame.left - gap - hz.size.width, below)
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) =
        with(scope) {
            drawRect(PlotGround, Offset(frame.left, frame.top), Size(frame.width, frame.height))
            drawPath(minor, SonicRule, alpha = 0.3f, style = hairline)
            drawPath(major, SonicRule, alpha = 0.6f, style = hairline)
            // The 0 dB reference: brighter than the grid, thinner than the curve.
            drawLine(CyberColors.mutedText.copy(alpha = 0.9f), Offset(frame.left, zeroY), Offset(frame.right, zeroY), 1f)
            for (i in labels.indices) drawText(labels[i], topLeft = labelPositions[i])
        }

    private companion object {
        val PlotGround = Color(0x59070510)
    }
}

private val HcAxisText = Color(0xFF77726A)
private val HcBlockLow = Color(0xFF181716)
private val HcBlockMid = Color(0xFF201F1D)
private val HcBlockTop = Color(0xFF3A2925)
private val HcBlockHot = Color(0xFF62201A)

/**
 * The hardcore graph's still part: a black panel with a graphite edge, a shade along the top and
 * four screws, the plot's grid in dark graphite (dashed for the gain ticks), the 0 dB line a step
 * brighter, and stamped axis labels.
 */
private class HcGridLayer(
    private val frame: GraphFrame,
    scaleDb: Float,
    sparse: Boolean,
    measurer: androidx.compose.ui.text.TextMeasurer,
    axisStyle: androidx.compose.ui.text.TextStyle,
    density: Density,
    private val canvasSize: Size,
) {
    private val verticals = Path()
    private val ticks = Path()
    private val labels = ArrayList<TextLayoutResult>()
    private val labelPositions = ArrayList<Offset>()
    private val hairline = Stroke(width = with(density) { 1.dp.toPx() })
    private val dashed =
        Stroke(
            width =
                with(density) {
                    1.dp.toPx()
                },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(with(density) { 2.dp.toPx() }, with(density) { 3.dp.toPx() })),
        )
    private val zeroY = frame.y(0f)
    private val edge = with(density) { 1.dp.toPx() }
    private val shade = with(density) { 10.dp.toPx() }
    private val screwRadius = with(density) { 4.5.dp.toPx() }
    private val slot = with(density) { 1.2.dp.toPx() }
    private val screws: List<Pair<Offset, Float>>

    init {
        val gap = with(density) { 6.dp.toPx() }
        val below = frame.bottom + with(density) { 6.dp.toPx() }
        CurveGeometry.labelledFrequencies.forEach { hz ->
            val x = frame.x(hz)
            if (hz > CurveGeometry.MIN_HZ && hz < CurveGeometry.MAX_HZ) {
                verticals.moveTo(x, frame.top)
                verticals.lineTo(x, frame.bottom)
            }
            val layout = measurer.measure(AnnotatedString(CurveGeometry.axisLabel(hz)), style = axisStyle)
            labels += layout
            labelPositions += Offset((x - layout.size.width / 2f).coerceIn(frame.left, frame.right - layout.size.width), below)
        }
        CurveGeometry.gainTicks(scaleDb).forEach { gain ->
            val y = frame.y(gain)
            if (gain != 0f) {
                ticks.moveTo(frame.left, y)
                ticks.lineTo(frame.right, y)
            }
            if (sparse && gain != 0f && abs(gain) != scaleDb) return@forEach
            val layout = measurer.measure(AnnotatedString(CurveGeometry.gainTickLabel(gain)), style = axisStyle)
            labels += layout
            labelPositions +=
                Offset(
                    frame.left - gap - layout.size.width,
                    (y - layout.size.height / 2f).coerceIn(0f, canvasSize.height - layout.size.height),
                )
        }
        val db = measurer.measure(AnnotatedString("dB"), style = axisStyle)
        labels += db
        labelPositions += Offset(frame.left - gap - db.size.width, 0f)
        val hz = measurer.measure(AnnotatedString("Hz"), style = axisStyle)
        labels += hz
        labelPositions += Offset(frame.left - gap - hz.size.width, below)
        val near = with(density) { 9.5.dp.toPx() }
        val far = with(density) { 8.5.dp.toPx() }
        screws =
            listOf(
                Offset(near, near) to 20f,
                Offset(canvasSize.width - near, near) to -40f,
                Offset(far, canvasSize.height - far) to 75f,
                Offset(canvasSize.width - far, canvasSize.height - far) to 5f,
            )
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) =
        with(scope) {
            drawRect(HcPanel)
            drawInsetShade(shade, 0.6f)
            drawRect(
                HardcoreColors.edge,
                topLeft = Offset(edge / 2f, edge / 2f),
                size = Size(size.width - edge, size.height - edge),
                style = hairline,
            )
            drawPath(verticals, HcGrid, style = hairline)
            drawPath(ticks, HcGrid, style = dashed)
            drawLine(HcZero, Offset(frame.left, zeroY), Offset(frame.right, zeroY), edge)
            for (i in labels.indices) drawText(labels[i], topLeft = labelPositions[i])
            screws.forEach { (center, angle) ->
                drawCircle(Color.Black, screwRadius, center + Offset(0f, edge))
                drawCircle(
                    Brush.radialGradient(
                        0f to HcScrewLight,
                        0.75f to HcScrewDark,
                        center = center + Offset(-screwRadius * 0.3f, -screwRadius * 0.4f),
                        radius = screwRadius * 1.4f,
                    ),
                    screwRadius,
                    center,
                )
                rotate(angle, pivot = center) {
                    drawLine(HcScrewSlot, center - Offset(screwRadius - edge, 0f), center + Offset(screwRadius - edge, 0f), slot)
                }
            }
        }

    private companion object {
        val HcPanel = Color(0xFF0D0D0C)
        val HcGrid = Color(0xFF1D1C1A)
        val HcZero = Color(0xFF4A4540)
        val HcScrewLight = Color(0xFF6A645C)
        val HcScrewDark = Color(0xFF1D1B19)
        val HcScrewSlot = Color(0xFF0B0A09)
    }
}
