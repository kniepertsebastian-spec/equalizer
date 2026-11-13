package com.hardbasseq.eq.ui.player.sound

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.context.SoundContext
import com.hardbasseq.eq.preset.TargetPoint
import com.hardbasseq.eq.ui.eq.EqStatusKind
import com.hardbasseq.eq.ui.eq.EqStatusLine
import com.hardbasseq.eq.ui.equalizer.ContextModeChips
import com.hardbasseq.eq.ui.equalizer.MiniCurve
import com.hardbasseq.eq.ui.equalizer.SonicStyle
import com.hardbasseq.eq.ui.player.PlayerDspCard
import com.hardbasseq.eq.ui.player.PlayerDspViewModel
import com.hardbasseq.eq.ui.theme.SonicRule
import com.hardbasseq.eq.ui.theme.SonicSteel
import com.hardbasseq.eq.ui.theme.spacing

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
) {
    val spacing = MaterialTheme.spacing
    val dsp by dspViewModel.settings.collectAsStateWithLifecycle()
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
            // The listening-context mode is an overlay on the EQ (subsonic, loudness, virtual
            // bass, limiter), so it applies to everything playing here too.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, SonicRule),
            ) {
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
            )
            Button(onClick = onOpenEqualizer, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.GraphicEq, contentDescription = null)
                Spacer(modifier = Modifier.width(spacing.small))
                Text(stringResource(R.string.sound_open_equalizer))
            }
        }
    }
}
