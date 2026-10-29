package com.hardbasseq.eq.ui.player

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hardbasseq.eq.ui.equalizer.SonicStyle

// Player transport pieces. Nothing here moves on its own: the app has no level or spectrum
// data for what is playing, so the player does not pretend to visualize it.

/** Square cover in a thin rule frame. */
@Composable
internal fun ArtworkFrame(
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        color = style.surface,
        border = BorderStroke(1.dp, style.border),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Main play/pause key: amber when it can act, a flat control surface when it cannot. */
@Composable
internal fun PlayKey(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    size: Dp = 72.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = if (enabled) style.accent else style.surfaceVariant,
        contentColor = if (enabled) style.background else style.mutedText,
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
        shape = MaterialTheme.shapes.small,
        color = style.surface,
        contentColor = style.text,
        border = BorderStroke(1.dp, style.border),
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
