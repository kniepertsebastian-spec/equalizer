package com.hardbasseq.eq.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.theme.SonicNumerals

// Building blocks shared by the equalizer and the player. They separate content with
// hairlines and tonal steps rather than stacked cards, and carry state in text and shape as
// well as color: a selected control is filled and outlined, never only tinted.

/** A titled block of the screen: a hairline above, generous space, no box around it. */
@Composable
internal fun Section(
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = style.border)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** A raised, tonal area for something that needs an answer, e.g. a question or a hint. */
@Composable
internal fun Notice(
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(style.surface)
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Small secondary text above or beside a control. Sentence case, never shouted. */
@Composable
internal fun Caption(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Selectable choice: outlined when off; tinted, outlined in amber and bold when on. */
@Composable
internal fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) style.accent.copy(alpha = 0.14f) else style.surfaceVariant,
        contentColor = if (selected) style.text else style.mutedText,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) style.accent else style.border),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** The master switch, built like a hardware key: lit amber when on, dark when off. */
@Composable
internal fun PowerKey(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val fill by animateColorAsState(if (checked) style.accent else style.surfaceVariant, tween(150), label = "powerFill")
    val tint by animateColorAsState(if (checked) style.background else style.mutedText, tween(150), label = "powerTint")
    Box(
        modifier =
            modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(fill)
                .border(BorderStroke(1.dp, if (checked) style.accent else style.border), MaterialTheme.shapes.medium)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, tint = tint)
    }
}

/** Small curve without axes, for genre choices and the player's sound chip. */
@Composable
internal fun MiniCurve(
    points: List<TargetPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val rangeDb = CurveGeometry.rangeDb(points)
        val middle = size.height / 2f
        drawLine(color.copy(alpha = 0.25f), Offset(0f, middle), Offset(size.width, middle), 1.dp.toPx())
        val samples = 40
        val path = Path()
        for (i in 0..samples) {
            val fraction = i / samples.toFloat()
            val gain = CurveGeometry.gainAt(points, CurveGeometry.frequencyAt(fraction))
            val y = middle - (gain / rangeDb) * middle * 0.9f
            if (i == 0) path.moveTo(0f, y) else path.lineTo(fraction * size.width, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** How a [LedMeter] colors its lit segments. */
internal enum class LedScale {
    /** Neutral, then yellow, then red at the top - for levels where "more" means "closer to the limit". */
    LEVEL,

    /** All neutral - for a reduction, which is neither good nor bad by itself. */
    REDUCTION,

    /** All red - for limiting. */
    HOT,
}

/**
 * Vertical segment meter, as on a rack unit. Shows a value that is estimated from the
 * settings; the caller says so next to it.
 */
@Composable
internal fun LedMeter(
    label: String,
    value: String,
    fraction: Float,
    scale: LedScale,
    modifier: Modifier = Modifier,
    segments: Int = 14,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val description = "$label $value"
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            modifier =
                Modifier
                    .width(18.dp)
                    .height(104.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(style.background),
        ) {
            val gap = 2.dp.toPx()
            val pad = 2.dp.toPx()
            val segmentHeight = (size.height - 2 * pad - gap * (segments - 1)) / segments
            val lit = if (fraction <= 0f) 0 else (fraction.coerceIn(0f, 1f) * segments).toInt().coerceAtLeast(1)
            for (i in 0 until segments) {
                val position = i / (segments - 1).toFloat()
                val litColor =
                    when (scale) {
                        LedScale.LEVEL ->
                            when {
                                position >= 0.86f -> style.hot
                                position >= 0.6f -> style.caution
                                else -> style.text
                            }
                        LedScale.REDUCTION -> style.text
                        LedScale.HOT -> style.hot
                    }
                val top = size.height - pad - (i + 1) * segmentHeight - i * gap
                drawRect(
                    color = if (i < lit) litColor else style.surfaceVariant,
                    topLeft = Offset(pad, top),
                    size = Size(size.width - 2 * pad, segmentHeight),
                )
            }
        }
        Caption(text = label, color = style.mutedText)
        Text(text = value, color = style.text, style = SonicNumerals.value, maxLines = 1)
    }
}

/**
 * Stepped slider for the intensity levels: an amber track up to the chosen step. Each step
 * is its own selectable target, so TalkBack reads them as radio buttons.
 */
@Composable
internal fun IntensityStepper(
    labels: List<String>,
    selectedIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val count = labels.size.coerceAtLeast(1)
    Box(modifier = modifier.fillMaxWidth().height(48.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val centerY = size.height / 2f

            fun xFor(index: Int): Float = (index + 0.5f) / count * size.width
            drawLine(
                color = style.surfaceVariant,
                start = Offset(xFor(0), centerY),
                end = Offset(xFor(count - 1), centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            val selected = selectedIndex?.takeIf { enabled }
            if (selected != null && selected > 0) {
                drawLine(
                    color = style.accent,
                    start = Offset(xFor(0), centerY),
                    end = Offset(xFor(selected), centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
            }
            for (i in 0 until count) {
                val reached = selected != null && i <= selected
                drawCircle(
                    color = if (reached) style.accent else style.mutedText.copy(alpha = if (enabled) 0.7f else 0.35f),
                    radius = 3.dp.toPx(),
                    center = Offset(xFor(i), centerY),
                )
            }
            if (selected != null) {
                val center = Offset(xFor(selected), centerY)
                drawCircle(color = style.background, radius = 12.dp.toPx(), center = center)
                drawCircle(color = style.accent, radius = 10.dp.toPx(), center = center)
                drawCircle(color = style.background, radius = 3.dp.toPx(), center = center)
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

/** One stage of the signal chain: a switch tile with a status lamp and its value. */
@Composable
internal fun SignalModuleTile(
    title: String,
    detail: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier =
            modifier
                .heightIn(min = 84.dp)
                .clip(shape)
                .background(if (checked) style.accent.copy(alpha = 0.10f) else style.surfaceVariant)
                .border(BorderStroke(if (checked) 1.5.dp else 1.dp, if (checked) style.accent else style.border), shape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The lamp: a filled square when on, an empty one when off - readable without color.
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .then(
                        if (checked) {
                            Modifier.background(style.accent)
                        } else {
                            Modifier.border(1.dp, style.mutedText)
                        },
                    ),
        )
        Text(
            text = title,
            color = style.text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
        )
        if (detail != null) {
            Text(
                text = detail,
                color = if (checked) style.text else style.mutedText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Short status tag, e.g. "Limiting often": outlined in its color, text in sentence case. */
@Composable
internal fun StatusTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .border(BorderStroke(1.dp, color), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * Section heading with an optional (i) button: the explanation stays one tap away instead of
 * filling the screen with text.
 */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    style: EqualizerDesignStyle = SonicStyle,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = style.text,
                style = MaterialTheme.typography.titleMedium,
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
