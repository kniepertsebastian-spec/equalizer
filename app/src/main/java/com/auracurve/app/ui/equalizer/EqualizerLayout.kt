package com.auracurve.app.ui.equalizer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.ui.theme.PillShape

// AuraCurve roadmap 04, part A (docs/EQUALIZER_REDESIGN.md): the building blocks of the equalizer's
// new layout. The sections themselves are unchanged and live in EqualizerScreen.

// Sorted by how often they are used: the sound is changed all the time, the device set up once.
internal enum class EqualizerTab(
    @param:StringRes val labelRes: Int,
) {
    SOUND(R.string.eq_tab_sound),
    FINE_TUNE(R.string.eq_tab_fine_tune),
    DEVICE(R.string.eq_tab_device),
}

// The full curve on a normal phone; a shorter one when the screen is short, so the head never
// takes more than about half of it.
internal val DefaultGraphHeight = 208.dp
internal val CompactGraphHeight = 132.dp

// The folded curve while the options are scrolled: still readable as a shape, a third of the full one.
internal val CollapsedGraphHeight = 96.dp
internal val COMPACT_HEIGHT = 640.dp
internal val WIDE_LAYOUT_MIN_WIDTH = 600.dp

// A pill-shaped segmented control: the chosen tab filled with the accent gradient and set in
// bold white, the others steel on the control tone. Each segment is a Role.Tab for TalkBack.
@Composable
internal fun EqualizerTabs(
    selected: EqualizerTab,
    onSelect: (EqualizerTab) -> Unit,
    style: EqualizerDesignStyle = SonicStyle,
) {
    Row(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(PillShape)
                .background(style.surface)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EqualizerTab.entries.forEach { tab ->
            val isSelected = selected == tab
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .then(if (isSelected) Modifier.background(style.accentFill) else Modifier)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color.White else style.mutedText,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The headroom in short, next to the status: text plus three segments, so the state does not
 * rest on the color alone. [level] is null while the EQ is off. Tapping opens the signal chain.
 */
@Composable
internal fun HeadroomBadge(
    level: HeadroomWarningLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
) {
    val (label, color, filled) =
        when (level) {
            null -> Triple(stringResource(R.string.signal_chain_short), style.mutedText, 0)
            HeadroomWarningLevel.SUFFICIENT -> Triple(stringResource(R.string.headroom_badge_ok), style.mutedText, 1)
            HeadroomWarningLevel.OCCASIONAL_LIMITING -> Triple(stringResource(R.string.tag_limiting_sometimes), style.caution, 2)
            HeadroomWarningLevel.HEAVY_LIMITING -> Triple(stringResource(R.string.tag_limiting_heavy), style.hot, 3)
        }
    val description = stringResource(R.string.signal_chain_open, label)
    Row(
        modifier =
            modifier
                .clip(PillShape)
                .background(style.surface)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            repeat(3) { segment ->
                Box(
                    modifier =
                        Modifier
                            .size(width = 5.dp, height = (8 + segment * 4).dp)
                            .background(if (segment < filled) color else style.border),
                )
            }
        }
    }
}

// The signal chain (levels, limiter, compressor, headroom) as a sheet: looked at, not set often.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignalChainSheet(
    onDismiss: () -> Unit,
    style: EqualizerDesignStyle = SonicStyle,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = style.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}
