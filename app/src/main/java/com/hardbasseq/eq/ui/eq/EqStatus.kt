package com.hardbasseq.eq.ui.eq

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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

// One tappable line: a lamp, the status (with the audio path when known) and, if not
// active, what is missing and what to do next. The lamp's shape carries the state as well
// as its color: filled when the effect runs, a ring when it is off, a square for problems.
@Composable
fun EqStatusLine(
    kind: EqStatusKind,
    pathLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val lampColor =
        when (kind) {
            EqStatusKind.ACTIVE -> colors.secondary
            EqStatusKind.OFF -> colors.onSurfaceVariant
            EqStatusKind.ERROR, EqStatusKind.UNSUPPORTED, EqStatusKind.LOST -> colors.error
            EqStatusKind.WAITING, EqStatusKind.CONNECTING -> colors.tertiary
        }
    val label =
        if (kind == EqStatusKind.ACTIVE && pathLabel != null) {
            stringResource(R.string.eq_status_active_for, pathLabel)
        } else {
            stringResource(kind.labelRes)
        }
    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(if (kind == EqStatusKind.ERROR) colors.errorContainer else colors.surface)
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .then(
                        when (kind) {
                            EqStatusKind.ACTIVE -> Modifier.clip(CircleShape).background(lampColor)
                            EqStatusKind.OFF -> Modifier.border(1.5.dp, lampColor, CircleShape)
                            else -> Modifier.background(lampColor)
                        },
                    ),
        )
        Column {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            kind.hintRes?.let { hint ->
                Text(text = stringResource(hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
