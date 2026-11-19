package com.auracurve.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.SonicNumerals

// Building blocks shared by the equalizer and the player. Content sits on tonal cards with
// round corners; selected controls are pills filled with the violet-to-blue gradient. State
// is carried in fill, weight and position as well as color, never only in a tint.

/** A titled block of the screen: one tonal card, generous padding, no outline. */
@Composable
internal fun Section(
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(style.surface)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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

/**
 * Selectable choice as a pill: a flat control-tone pill when off; filled with the accent
 * gradient, white and bold when on - the fill and the weight carry the state, not the hue.
 */
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
        shape = PillShape,
        color = style.surfaceVariant,
        contentColor = if (selected) Color.White else style.mutedText,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .then(if (selected) Modifier.background(style.accentFill) else Modifier)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * The master switch: a round key, filled with the accent gradient and a soft glow when on,
 * a dark outlined disc when off.
 */
@Composable
internal fun PowerKey(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val tint by animateColorAsState(if (checked) Color.White else style.mutedText, tween(150), label = "powerTint")
    val glow by animateDpAsState(if (checked) 14.dp else 0.dp, tween(200), label = "powerGlow")
    Box(
        modifier =
            modifier
                .size(52.dp)
                .shadow(glow, CircleShape, ambientColor = style.accentFillStart, spotColor = style.accentFillStart)
                .clip(CircleShape)
                .then(
                    if (checked) {
                        Modifier.background(style.accentFill)
                    } else {
                        Modifier.background(style.surfaceVariant).border(1.dp, style.border, CircleShape)
                    },
                ).toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
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
    brush: Brush? = null,
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
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (brush != null) drawPath(path, brush, style = stroke) else drawPath(path, color, style = stroke)
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
 * One stage of the signal chain as a compact tile: icon, name and state in words. Filled
 * with the accent gradient when on, the control tone when off; the whole tile is the switch.
 */
@Composable
internal fun SignalModuleTile(
    title: String,
    detail: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val content = if (checked) Color.White else style.text
    Column(
        modifier =
            modifier
                .heightIn(min = 76.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(style.surfaceVariant)
                .then(if (checked) Modifier.background(style.accentFill) else Modifier)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) Color.White else style.accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            Text(
                text = detail,
                color = if (checked) Color.White.copy(alpha = 0.85f) else style.mutedText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Display-only switch track; the row around it handles input and semantics. */
@Composable
internal fun GradientSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, tween(150), label = "switchThumb")
    Box(
        modifier =
            modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(PillShape)
                .then(
                    if (checked) {
                        Modifier.background(style.accentFill)
                    } else {
                        Modifier.background(style.surfaceVariant).border(1.dp, style.border, PillShape)
                    },
                ).padding(3.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = offset)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else style.mutedText),
        )
    }
}

/** Short status tag, e.g. "Limiting often": a pill outlined in its color, text in sentence case. */
@Composable
internal fun StatusTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(PillShape)
                .background(color.copy(alpha = 0.10f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), PillShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
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
