package com.hardbasseq.eq.ui.eq

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.ProcessingSettings

// The plain-language state of the EQ, shown in the player and the equalizer. ACTIVE is
// only ever the case when the engine confirmed that the effect was applied
// (AudioEngineState.Active) and the processing is not switched off or bypassed - the UI must
// never claim more than the audio path confirmed.
enum class EqStatusKind(
    @param:StringRes val labelRes: Int,
    @param:StringRes val hintRes: Int?,
) {
    ACTIVE(R.string.eq_status_active, null),
    WAITING(R.string.eq_status_waiting, R.string.eq_hint_waiting),
    CONNECTING(R.string.eq_status_connecting, R.string.eq_hint_connecting),
    LOST(R.string.eq_status_lost, R.string.eq_hint_lost),
    UNSUPPORTED(R.string.eq_status_unsupported, R.string.eq_hint_unsupported),
    ERROR(R.string.eq_status_error, R.string.eq_hint_error),
    OFF(R.string.eq_status_off, R.string.eq_hint_off),
}

object EqStatus {
    fun kind(
        state: AudioEngineState,
        settings: ProcessingSettings,
    ): EqStatusKind =
        when {
            !settings.masterEnabled || settings.bypass -> EqStatusKind.OFF
            else ->
                when (state) {
                    is AudioEngineState.Active -> EqStatusKind.ACTIVE
                    is AudioEngineState.Detached, is AudioEngineState.Listening -> EqStatusKind.WAITING
                    is AudioEngineState.Attaching -> EqStatusKind.CONNECTING
                    is AudioEngineState.LostControl, is AudioEngineState.Retrying -> EqStatusKind.LOST
                    is AudioEngineState.Unsupported -> EqStatusKind.UNSUPPORTED
                    is AudioEngineState.Error -> EqStatusKind.ERROR
                }
        }
}

// One tappable line: the status (with the audio path when known) and, if not active, what
// is missing and what to do next.
@Composable
fun EqStatusLine(
    kind: EqStatusKind,
    pathLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background =
        when (kind) {
            EqStatusKind.ACTIVE -> colors.primaryContainer
            EqStatusKind.ERROR -> colors.errorContainer
            EqStatusKind.UNSUPPORTED -> colors.tertiaryContainer
            else -> colors.surfaceVariant
        }
    val label =
        if (kind == EqStatusKind.ACTIVE && pathLabel != null) {
            stringResource(R.string.eq_status_active_for, pathLabel)
        } else {
            stringResource(kind.labelRes)
        }
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        kind.hintRes?.let { hint ->
            Text(text = stringResource(hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}
