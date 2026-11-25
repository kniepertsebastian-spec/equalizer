package com.auracurve.app.ui.equalizer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.dsp.HeadroomWarningLevel
import com.auracurve.app.ui.components.cyber.CyberSegmented
import com.auracurve.app.ui.components.cyber.LimiterLedMeter
import com.auracurve.app.ui.theme.CyberColors
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

// The Device tab's curve is the target the device is tuned to: a little taller (not when compact).
internal val DeviceGraphExtra = 16.dp

// The folded curve while the options are scrolled: still readable as a shape, a third of the full one.
internal val CollapsedGraphHeight = 96.dp
internal val COMPACT_HEIGHT = 640.dp
internal val WIDE_LAYOUT_MIN_WIDTH = 600.dp

// Three separate pills: the chosen tab filled with the accent gradient, a light edge and a soft
// glow, set in bold white; the others outlined in steel. Each pill is a Role.Tab for TalkBack.
@Composable
internal fun EqualizerTabs(
    selected: EqualizerTab,
    onSelect: (EqualizerTab) -> Unit,
    @Suppress("UNUSED_PARAMETER") style: EqualizerDesignStyle = SonicStyle,
) {
    val tabs = EqualizerTab.entries
    CyberSegmented(
        options = tabs.map { stringResource(it.labelRes) },
        selectedIndex = tabs.indexOf(selected),
        onSelect = { onSelect(tabs[it]) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        role = Role.Tab,
    )
}

/**
 * The headroom in short, next to the status: text plus a small staircase of LEDs, so the state
 * does not rest on the color alone. [level] is null while the EQ is off. Both are an estimate
 * from the settings, not a reading of the signal. Tapping opens the signal chain.
 */
@Composable
internal fun HeadroomBadge(
    level: HeadroomWarningLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EqualizerDesignStyle = SonicStyle,
    processing: Boolean = level != null,
) {
    val (label, color) =
        when (level) {
            null -> stringResource(R.string.signal_chain_short) to style.mutedText
            HeadroomWarningLevel.SUFFICIENT -> stringResource(R.string.headroom_badge_ok) to style.mutedText
            HeadroomWarningLevel.OCCASIONAL_LIMITING -> stringResource(R.string.tag_limiting_sometimes) to style.caution
            HeadroomWarningLevel.HEAVY_LIMITING -> stringResource(R.string.tag_limiting_heavy) to style.hot
        }
    val description = stringResource(R.string.signal_chain_open, label)
    Row(
        modifier =
            modifier
                .clip(PillShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .heightIn(min = 48.dp)
                .padding(start = 6.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (level == HeadroomWarningLevel.HEAVY_LIMITING) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 2,
        )
        LimiterLedMeter(level = level, processing = processing)
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
        scrimColor = CyberColors.void.copy(alpha = 0.72f),
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
