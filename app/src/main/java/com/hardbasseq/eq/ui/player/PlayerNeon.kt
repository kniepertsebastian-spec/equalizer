package com.hardbasseq.eq.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.equalizer.EqualizerDesignStyle
import com.hardbasseq.eq.ui.equalizer.MiniCurve

// Player pieces of the neon look. The ring around the cover turns while a track plays;
// it shows "playing", not the audio itself - the app has no level or spectrum data for
// what is playing.

/** Cover frame: a soft accent glow and a ring that slowly turns while [isPlaying]. */
@Composable
internal fun NeonArtworkFrame(
    isPlaying: Boolean,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Only a playing track runs the animation; a paused player draws nothing per frame.
    val rotation = if (isPlaying) rememberRingRotation() else 0f
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .drawBehind {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(style.accent.copy(alpha = 0.38f), Color.Transparent),
                                center = center,
                                radius = radius * 1.2f,
                            ),
                        radius = radius * 1.2f,
                    )
                    rotate(rotation) {
                        drawCircle(
                            brush = Brush.sweepGradient(listOf(style.accent, style.secondaryAccent, Color.Transparent, style.accent)),
                            radius = radius - 2.dp.toPx(),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }.padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun rememberRingRotation(): Float {
    val transition = rememberInfiniteTransition(label = "coverRing")
    val angle =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 14_000, easing = LinearEasing)),
            label = "coverRingRotation",
        )
    return angle.value
}

/** Big round play/pause button with an accent glow. */
@Composable
internal fun NeonPlayButton(
    onClick: () -> Unit,
    enabled: Boolean,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .size(size)
                .shadow(
                    elevation = if (enabled) 18.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = style.accent,
                    spotColor = style.accent,
                ),
        shape = CircleShape,
        color = if (enabled) style.accent else style.surfaceVariant,
        contentColor = style.background,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** The active sound as a small chip with its curve; tapping it opens the equalizer. */
@Composable
internal fun PlayerSoundChip(
    soundName: String,
    curve: List<TargetPoint>,
    contextName: String?,
    onClick: () -> Unit,
    style: EqualizerDesignStyle,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.player_open_equalizer, soundName)
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = style.accent.copy(alpha = 0.08f),
        contentColor = style.text,
        border = BorderStroke(1.dp, style.accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniCurve(points = curve, color = style.accent, modifier = Modifier.width(64.dp).height(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = soundName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
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
