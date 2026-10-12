package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.TargetPoint

// Building blocks of the neon look: glowing curve, LED meters, intensity stepper,
// signal-chain tiles. Every color comes from the active EqualizerDesignStyle, so the
// same components carry each genre's own accent.

/** Corner radius shared by all neon panels, independent of the genre style. */
internal val NeonPanelCorner: Dp = 20.dp

/** Red used for clipping and heavy limiting, the same in every genre. */
internal val NeonHotColor = Color(0xFFFF4A4A)

private val CurveLabelFrequencies = listOf(30f, 100f, 300f, 1000f, 3000f, 10_000f)

/** Dark backdrop with a soft accent glow at the top, replacing the busy background photo. */
internal fun Modifier.neonBackdrop(style: EqualizerDesignStyle): Modifier =
    this
        .background(Brush.verticalGradient(listOf(style.background, style.backgroundEnd, style.background)))
        .drawBehind {
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(style.accent.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.1f,
                    ),
            )
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(style.secondaryAccent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.75f),
                        radius = size.width,
                    ),
            )
        }

/** Rounded panel with a dark gradient fill; [glow] adds an accent halo for the hero. */
@Composable
internal fun NeonPanel(
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(NeonPanelCorner)
    val glowModifier =
        if (glow) {
            Modifier.shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = style.accent,
                spotColor = style.accent,
            )
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(glowModifier)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(style.surface, style.background.copy(alpha = 0.96f))))
                .border(
                    BorderStroke(1.dp, if (glow) style.accent.copy(alpha = 0.45f) else style.border.copy(alpha = 0.55f)),
                    shape,
                ).padding(contentPadding),
        content = content,
    )
}

/** Small uppercase monospace label used for section headings inside the panels. */
@Composable
internal fun NeonLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pill-shaped selectable chip: filled when selected, outlined otherwise. */
@Composable
internal fun NeonChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        color = if (selected) style.text else Color.Transparent,
        contentColor = if (selected) style.background else style.mutedText,
        border = BorderStroke(1.dp, if (selected) style.text else style.border),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Round, glowing on/off button for the whole EQ. */
@Composable
internal fun NeonPowerButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    val tint = if (checked) style.accent else style.mutedText
    Box(
        modifier =
            modifier
                .size(56.dp)
                .drawBehind {
                    if (checked) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(style.accent.copy(alpha = 0.45f), Color.Transparent)),
                            radius = size.minDimension * 0.75f,
                        )
                    }
                }.clip(CircleShape)
                .background(if (checked) style.accent.copy(alpha = 0.16f) else style.surfaceVariant)
                .border(BorderStroke(1.5.dp, tint), CircleShape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, tint = tint)
    }
}

/**
 * The hero: the EQ curve on a logarithmic frequency axis with a glowing stroke, a fill
 * fading into the panel, dots on the real band points and, when [hot], a red glow
 * around the strongest boost - where the limiter is expected to work.
 */
@Composable
internal fun NeonCurve(
    points: List<TargetPoint>,
    nodes: List<TargetPoint>,
    style: EqualizerDesignStyle,
    hot: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = style.mutedText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    val rangeDb = CurveGeometry.rangeDb(points + nodes)
    val peak = CurveGeometry.strongestChange(nodes.ifEmpty { points })
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp)
                .semantics { contentDescription = description },
    ) {
        val labelHeight = 18.dp.toPx()
        val inset = 14.dp.toPx()
        val plotTop = inset
        val plotBottom = size.height - labelHeight - inset
        val plotHeight = plotBottom - plotTop
        val width = size.width

        fun yFor(gainDb: Float): Float = plotTop + (1f - (gainDb.coerceIn(-rangeDb, rangeDb) + rangeDb) / (2f * rangeDb)) * plotHeight

        val zeroY = yFor(0f)
        val gridColor = style.text.copy(alpha = 0.07f)
        listOf(rangeDb / 2f, -rangeDb / 2f).forEach { gain ->
            drawLine(gridColor, Offset(0f, yFor(gain)), Offset(width, yFor(gain)), strokeWidth = 1.dp.toPx())
        }
        listOf(100f, 1000f, 10_000f).forEach { hz ->
            val x = CurveGeometry.xFraction(hz) * width
            drawLine(gridColor, Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
        }
        drawLine(
            color = style.text.copy(alpha = 0.25f),
            start = Offset(0f, zeroY),
            end = Offset(width, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()), 0f),
        )

        CurveLabelFrequencies.forEach { hz ->
            val label = if (hz >= 1000f) "${(hz / 1000f).toInt()}k" else hz.toInt().toString()
            val layout = measurer.measure(AnnotatedString(label), style = labelStyle)
            val x = (CurveGeometry.xFraction(hz) * width - layout.size.width / 2f).coerceIn(0f, width - layout.size.width)
            drawText(textLayoutResult = layout, topLeft = Offset(x, size.height - layout.size.height))
        }

        if (points.isEmpty() && nodes.isEmpty()) return@Canvas
        val source = points.ifEmpty { nodes }

        if (hot && peak != null && peak.gainDb > 0f) {
            val center = Offset(CurveGeometry.xFraction(peak.frequencyHz) * width, yFor(peak.gainDb))
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(NeonHotColor.copy(alpha = 0.55f), Color.Transparent),
                        center = center,
                        radius = 56.dp.toPx(),
                    ),
                radius = 56.dp.toPx(),
                center = center,
            )
        }

        val samples = 120
        val line = Path()
        for (i in 0..samples) {
            val fraction = i / samples.toFloat()
            val y = yFor(CurveGeometry.gainAt(source, CurveGeometry.frequencyAt(fraction)))
            val x = fraction * width
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill =
            Path().apply {
                addPath(line)
                lineTo(width, plotBottom)
                lineTo(0f, plotBottom)
                close()
            }
        drawPath(
            path = fill,
            brush =
                Brush.verticalGradient(
                    colors = listOf(style.accent.copy(alpha = 0.38f), Color.Transparent),
                    startY = plotTop,
                    endY = plotBottom,
                ),
        )
        drawGlowStroke(line, style.accent)

        nodes.forEach { node ->
            val center = Offset(CurveGeometry.xFraction(node.frequencyHz) * width, yFor(node.gainDb))
            val isPeak = node == peak
            drawCircle(color = style.background, radius = if (isPeak) 7.dp.toPx() else 5.5.dp.toPx(), center = center)
            drawCircle(
                color = style.text,
                radius = if (isPeak) 7.dp.toPx() else 5.5.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            if (isPeak) {
                drawCircle(
                    color = style.accent.copy(alpha = 0.6f),
                    radius = 13.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

// Neon glow without blur effects (those need API 31+): the same stroke drawn wide and
// faint, then narrower, then the sharp line on top.
private fun DrawScope.drawGlowStroke(
    path: Path,
    color: Color,
    width: Dp = 3.dp,
) {
    listOf(16.dp to 0.08f, 9.dp to 0.16f, 5.dp to 0.30f).forEach { (glowWidth, alpha) ->
        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(width = glowWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    drawPath(path = path, color = color, style = Stroke(width = width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Small curve without axes, for genre cards and the player's sound chip. */
@Composable
internal fun MiniCurve(
    points: List<TargetPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val rangeDb = CurveGeometry.rangeDb(points)
        val samples = 40
        val path = Path()
        for (i in 0..samples) {
            val fraction = i / samples.toFloat()
            val gain = CurveGeometry.gainAt(points, CurveGeometry.frequencyAt(fraction))
            val y = size.height / 2f - (gain / rangeDb) * (size.height / 2f) * 0.9f
            if (i == 0) path.moveTo(0f, y) else path.lineTo(fraction * size.width, y)
        }
        drawGlowStroke(path, color, width = 2.dp)
    }
}

/** How a [LedMeter] colors its lit segments. */
internal enum class LedScale {
    /** Green, then amber, then red at the top - for levels where "more" means "closer to the limit". */
    LEVEL,

    /** All amber - for a reduction, which is neither good nor bad by itself. */
    REDUCTION,

    /** All red - for limiting. */
    HOT,
}

/**
 * Vertical LED chain from the hardware-rack design. Shows a value that is estimated
 * from the settings; the caller says so next to it.
 */
@Composable
internal fun LedMeter(
    label: String,
    value: String,
    fraction: Float,
    scale: LedScale,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    segments: Int = 14,
) {
    val description = "$label $value"
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            modifier =
                Modifier
                    .width(22.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0A0A0C)),
        ) {
            val gap = 2.dp.toPx()
            val pad = 3.dp.toPx()
            val segmentHeight = (size.height - 2 * pad - gap * (segments - 1)) / segments
            val lit = if (fraction <= 0f) 0 else (fraction.coerceIn(0f, 1f) * segments).toInt().coerceAtLeast(1)
            for (i in 0 until segments) {
                val on = i < lit
                val position = i / (segments - 1).toFloat()
                val litColor =
                    when (scale) {
                        LedScale.LEVEL ->
                            when {
                                position >= 0.86f -> NeonHotColor
                                position >= 0.6f -> Color(0xFFFFB020)
                                else -> Color(0xFF8FD14F)
                            }
                        LedScale.REDUCTION -> Color(0xFFFFB020)
                        LedScale.HOT -> NeonHotColor
                    }
                val top = size.height - pad - (i + 1) * segmentHeight - i * gap
                if (on) {
                    drawRect(
                        color = litColor.copy(alpha = 0.35f),
                        topLeft = Offset(0f, top - gap / 2f),
                        size = Size(size.width, segmentHeight + gap),
                    )
                }
                drawRect(
                    color = if (on) litColor else Color(0xFF1E1F23),
                    topLeft = Offset(pad, top),
                    size = Size(size.width - 2 * pad, segmentHeight),
                )
            }
        }
        NeonLabel(text = label, color = style.mutedText)
        Text(text = value, color = style.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * Stepped slider for the five intensity levels: a glowing track up to the chosen step.
 * Each step is its own selectable target, so TalkBack reads them as radio buttons.
 */
@Composable
internal fun IntensityStepper(
    labels: List<String>,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    val count = labels.size.coerceAtLeast(1)
    Box(modifier = modifier.fillMaxWidth().height(48.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 6.dp.toPx()
            val centerY = size.height / 2f

            fun xFor(index: Int): Float = (index + 0.5f) / count * size.width
            drawLine(
                color = style.text.copy(alpha = 0.10f),
                start = Offset(xFor(0), centerY),
                end = Offset(xFor(count - 1), centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            val selected = selectedIndex
            if (selected != null && enabled) {
                val endX = xFor(selected)
                if (selected > 0) {
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(style.secondaryAccent, style.accent), startX = xFor(0), endX = endX),
                        start = Offset(xFor(0), centerY),
                        end = Offset(endX, centerY),
                        strokeWidth = trackHeight * 2.4f,
                        cap = StrokeCap.Round,
                        alpha = 0.25f,
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(style.secondaryAccent, style.accent), startX = xFor(0), endX = endX),
                        start = Offset(xFor(0), centerY),
                        end = Offset(endX, centerY),
                        strokeWidth = trackHeight,
                        cap = StrokeCap.Round,
                    )
                }
            }
            for (i in 0 until count) {
                val reached = enabled && selected != null && i <= selected
                drawCircle(
                    color = if (reached) style.text else style.text.copy(alpha = 0.3f),
                    radius = 3.dp.toPx(),
                    center = Offset(xFor(i), centerY),
                )
            }
            if (selected != null && enabled) {
                val center = Offset(xFor(selected), centerY)
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(style.accent.copy(alpha = 0.6f), Color.Transparent),
                            center = center,
                            radius = 26.dp.toPx(),
                        ),
                    radius = 26.dp.toPx(),
                    center = center,
                )
                drawCircle(color = Color.White, radius = 13.dp.toPx(), center = center)
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = index == selectedIndex,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            ).semantics { contentDescription = label },
                )
            }
        }
    }
}

/** One stage of the signal chain: a tappable tile with a status LED. */
@Composable
internal fun SignalModuleTile(
    title: String,
    detail: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val ledColor = if (checked) style.secondaryAccent else style.text.copy(alpha = 0.18f)
    Column(
        modifier =
            modifier
                .heightIn(min = 84.dp)
                .clip(shape)
                .background(if (checked) style.accent.copy(alpha = 0.10f) else style.text.copy(alpha = 0.04f))
                .border(BorderStroke(1.dp, if (checked) style.accent.copy(alpha = 0.5f) else style.border.copy(alpha = 0.5f)), shape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            if (checked) {
                drawCircle(brush = Brush.radialGradient(listOf(ledColor.copy(alpha = 0.7f), Color.Transparent)), radius = size.minDimension)
            }
            drawCircle(color = ledColor, radius = size.minDimension / 3f)
        }
        Text(
            text = title,
            color = style.text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        if (detail != null) {
            Text(
                text = detail,
                color = style.mutedText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Short status tag in the hero, e.g. "HEAVY LIMITING". */
@Composable
internal fun NeonTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.16f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text.uppercase(), color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * Panel heading with an optional (i) button: the explanation stays one tap away instead of
 * filling the screen with text.
 */
@Composable
internal fun NeonSectionHeader(
    title: String,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    info: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = style.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            trailing()
            if (info != null) {
                IconButton(onClick = { infoOpen = !infoOpen }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(if (infoOpen) R.string.info_less else R.string.info_more),
                        tint = if (infoOpen) style.accent else style.mutedText,
                    )
                }
            }
        }
        AnimatedVisibility(visible = infoOpen && info != null) {
            Text(
                text = info.orEmpty(),
                color = style.mutedText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
