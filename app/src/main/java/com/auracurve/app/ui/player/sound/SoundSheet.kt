package com.auracurve.app.ui.player.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auracurve.app.R
import com.auracurve.app.context.SoundContext
import com.auracurve.app.eq.ActivePlaybackSource
import com.auracurve.app.eq.EqController
import com.auracurve.app.preset.GenrePresetGroups
import com.auracurve.app.preset.Preset
import com.auracurve.app.preset.TargetPoint
import com.auracurve.app.ui.components.cyber.CyberCard
import com.auracurve.app.ui.eq.EqStatusKind
import com.auracurve.app.ui.eq.EqStatusLine
import com.auracurve.app.ui.equalizer.ContextModeChips
import com.auracurve.app.ui.equalizer.GenrePresetList
import com.auracurve.app.ui.equalizer.MiniCurve
import com.auracurve.app.ui.equalizer.PresetSourceTabs
import com.auracurve.app.ui.equalizer.SonicStyle
import com.auracurve.app.ui.equalizer.initialPresetSourceTab
import com.auracurve.app.ui.player.PlaybackTimersCard
import com.auracurve.app.ui.player.PlaybackTimersViewModel
import com.auracurve.app.ui.player.PlayerDspCard
import com.auracurve.app.ui.player.PlayerDspViewModel
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Roadmap 16: the preset choice in the sound sheet, straight on the EQ controller - the same
 * active preset and detected source the equalizer screen shows.
 */
@HiltViewModel
class SoundPresetViewModel
    @Inject
    constructor(
        private val controller: EqController,
    ) : ViewModel() {
        val activePreset: StateFlow<Preset> = controller.activePreset
        val playbackSource: StateFlow<ActivePlaybackSource> = controller.activePlaybackSource

        fun selectPreset(preset: Preset) = controller.selectPreset(preset)
    }

// The sound behind the EQ chip: what used to sit at the bottom of the player page (listening
// context, mono bass, limiter), plus the way to the full equalizer. Opens from the chip in
// "Now playing" and from the lamp in the mini player.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSheet(
    soundName: String,
    soundCurve: List<TargetPoint>,
    eqStatus: EqStatusKind,
    pathLabel: String?,
    activeContext: SoundContext?,
    onContextModeChanged: (SoundContext?) -> Unit,
    onOpenEqualizer: () -> Unit,
    onDismiss: () -> Unit,
    dspViewModel: PlayerDspViewModel = hiltViewModel(),
    timersViewModel: PlaybackTimersViewModel = hiltViewModel(),
    presetViewModel: SoundPresetViewModel = hiltViewModel(),
) {
    val spacing = MaterialTheme.spacing
    val activePreset by presetViewModel.activePreset.collectAsStateWithLifecycle()
    val playbackSource by presetViewModel.playbackSource.collectAsStateWithLifecycle()
    val dsp by dspViewModel.settings.collectAsStateWithLifecycle()
    val sleepTimer by timersViewModel.sleepTimer.collectAsStateWithLifecycle()
    val trackFadeMs by timersViewModel.trackFadeMs.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = spacing.medium)
                    .padding(bottom = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sound_sheet_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = SonicSteel,
                    )
                    Text(
                        text = soundName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                MiniCurve(points = soundCurve, color = SonicStyle.accent, modifier = Modifier.width(96.dp).height(36.dp))
            }
            EqStatusLine(kind = eqStatus, pathLabel = pathLabel, onClick = onOpenEqualizer, modifier = Modifier.fillMaxWidth())
            SoundPresetCard(
                activePreset = activePreset,
                playbackSource = playbackSource,
                onPresetSelected = presetViewModel::selectPreset,
            )
            // The listening-context mode is an overlay on the EQ (subsonic, loudness, virtual
            // bass, limiter), so it applies to everything playing here too.
            CyberCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Text(
                        stringResource(R.string.context_mode_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.context_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = SonicSteel,
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    ContextModeChips(activeContext = activeContext, onContextModeChanged = onContextModeChanged)
                }
            }
            PlayerDspCard(
                settings = dsp,
                onMonoBass = dspViewModel::setMonoBass,
                onCutoff = dspViewModel::setMonoBassCutoff,
                onLimiter = dspViewModel::setLimiter,
                onVirtualBassCharacter = dspViewModel::setVirtualBassCharacter,
            )
            PlaybackTimersCard(
                sleepTimer = sleepTimer,
                trackFadeMs = trackFadeMs,
                onStartSleepTimer = timersViewModel::startSleepTimer,
                onCancelSleepTimer = timersViewModel::cancelSleepTimer,
                onTrackFadeSeconds = timersViewModel::setTrackFadeSeconds,
            )
            Button(onClick = onOpenEqualizer, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.GraphicEq, contentDescription = null)
                Spacer(modifier = Modifier.width(spacing.small))
                Text(stringResource(R.string.sound_open_equalizer))
            }
        }
    }
}

// Roadmap 16, P16-3: the presets split into own player and Spotify & other apps, the ones for the
// other source greyed out - the same list as in the equalizer, without its edit actions.
@Composable
private fun SoundPresetCard(
    activePreset: Preset,
    playbackSource: ActivePlaybackSource,
    onPresetSelected: (Preset) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    var sourceTab by rememberSaveable(playbackSource) { mutableStateOf(initialPresetSourceTab(playbackSource, activePreset)) }
    CyberCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                stringResource(R.string.sound_sheet_presets_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            PresetSourceTabs(
                selected = sourceTab,
                detected = playbackSource,
                externalAppLabel = null,
                onSelect = { sourceTab = it },
                style = SonicStyle,
            )
            key(sourceTab) {
                GenrePresetList(
                    groups = remember(sourceTab) { GenrePresetGroups.forSource(sourceTab) },
                    activePresetId = activePreset.id,
                    onPresetSelected = onPresetSelected,
                    onDuplicatePreset = null,
                    onSharePreset = null,
                    style = SonicStyle,
                    detectedSource = playbackSource,
                )
            }
        }
    }
}
