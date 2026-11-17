package com.auracurve.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.equalizer.EqualizerDesignStyle
import com.auracurve.app.ui.equalizer.MiniCurve
import com.auracurve.app.ui.equalizer.SonicStyle
import com.auracurve.app.ui.equalizer.accentFill
import com.auracurve.app.ui.equalizer.lineBrush

// Player transport pieces. Nothing here moves on its own: the app has no level or spectrum
// data for what is playing, so the player does not pretend to visualize it.

/**
 * Round cover with the playback position as a ring around it. The ring shows how far the track
 * has played (display only; the seek bar below is the control).
 */
@Composable
internal fun ProgressRingCover(
    progress: Float,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .drawBehind {
                    val stroke = 4.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = style.surfaceVariant,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    val sweep = 360f * progress.coerceIn(0f, 1f)
                    if (sweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(style.lineStart, style.lineEnd, style.lineStart)),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }.padding(12.dp)
                .clip(CircleShape)
                .background(style.surface),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Main play/pause key: a round key in the accent gradient with a soft glow when it can act, a
 * flat control surface when it cannot.
 */
@Composable
internal fun PlayKey(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    size: Dp = 72.dp,
    // The soft glow; off for small keys inside list rows, whose clip would cut it.
    glow: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .shadow(
                    elevation = if (enabled && glow) 16.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = style.accentFillStart,
                    spotColor = style.accentFillStart,
                ).clip(CircleShape)
                .then(if (enabled) Modifier.background(style.accentFill) else Modifier.background(style.surfaceVariant))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) Color.White else style.mutedText) { content() }
    }
}

/** Secondary transport key (previous, next, shuffle, repeat): a round control surface. */
@Composable
internal fun RoundControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(style.surfaceVariant)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) style.text else style.mutedText.copy(alpha = 0.5f)) {
            content()
        }
    }
}

/** The active sound as a small chip with its curve; tapping it opens the equalizer. */
@Composable
internal fun PlayerSoundChip(
    soundName: String,
    curve: List<TargetPoint>,
    contextName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val description = stringResource(R.string.player_open_equalizer, soundName)
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.large,
        color = style.surface,
        contentColor = style.text,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniCurve(
                points = curve,
                color = style.accent,
                brush = style.lineBrush(),
                modifier = Modifier.width(64.dp).height(26.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = soundName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contextName != null) {
                    Text(text = contextName, style = MaterialTheme.typography.bodySmall, color = style.mutedText, maxLines = 1)
                }
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = style.mutedText)
        }
    }
}
