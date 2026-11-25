package com.auracurve.app.ui.eq

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.ui.components.hardcore.HcLed
import com.auracurve.app.ui.components.hardcore.HcStatusCard
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.glassSurface

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

// One tappable line on a glass card: a lamp, the status (with the audio path when known) and, if
// not active, what is missing and what to do next. The lamp's shape carries the state as well
// as its color: filled when the effect runs, a ring when it is off, a square for problems; while
// waiting for or losing the audio path a warning sign takes its place. The card's edge repeats
// the state: amber while waiting, coral on an error, a thin signal green when active.
@Composable
fun EqStatusLine(
    kind: EqStatusKind,
    pathLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Optional end slot, e.g. a "more" chevron when tapping the line opens an explanation.
    trailing: (@Composable () -> Unit)? = null,
) {
    val label =
        if (kind == EqStatusKind.ACTIVE && pathLabel != null) {
            stringResource(R.string.eq_status_active_for, pathLabel)
        } else {
            stringResource(kind.labelRes)
        }
    val warning = kind == EqStatusKind.WAITING || kind == EqStatusKind.CONNECTING || kind == EqStatusKind.LOST
    if (LocalHardcoreLook.current) {
        HcStatusLine(kind, label, warning, onClick, modifier, trailing)
        return
    }
    val shape = MaterialTheme.shapes.large
    Row(
        modifier =
            modifier
                .glassSurface(
                    shape = shape,
                    color = if (kind == EqStatusKind.ERROR) ErrorGlass else CyberColors.surface,
                    border = statusEdge(kind),
                    borderWidth = if (kind == EqStatusKind.ACTIVE) CyberDims.border else CyberDims.activeBorder,
                ).clip(shape)
                .clickable(onClick = onClick)
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (warning) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = CyberColors.amber,
                modifier = Modifier.padding(top = 1.dp).size(20.dp),
            )
        } else {
            EqStatusLamp(kind = kind, modifier = Modifier.padding(top = 6.dp, start = 5.dp, end = 5.dp))
        }
        Column(modifier = if (trailing != null) Modifier.weight(1f) else Modifier) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = CyberColors.text)
            kind.hintRes?.let { hint ->
                Text(text = stringResource(hint), style = MaterialTheme.typography.bodySmall, color = CyberColors.mutedText)
            }
        }
        trailing?.invoke()
    }
}

// The hardcore status card: a hazard tape in the state's color (hazard while waiting, blood on a
// problem, toxic green when the effect runs, gray when off), the warning sign or a lamp, the words.
@Composable
private fun HcStatusLine(
    kind: EqStatusKind,
    label: String,
    warning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
) {
    val tape =
        when (kind) {
            EqStatusKind.WAITING, EqStatusKind.CONNECTING, EqStatusKind.LOST -> HardcoreColors.hazard
            EqStatusKind.ERROR, EqStatusKind.UNSUPPORTED -> HardcoreColors.bloodHi
            EqStatusKind.ACTIVE -> HardcoreColors.toxic
            EqStatusKind.OFF -> HardcoreColors.ash2
        }
    HcStatusCard(
        label = label,
        hint = kind.hintRes?.let { stringResource(it) },
        tapeColor = tape,
        lamp = {
            if (warning) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = HardcoreColors.hazard,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                HcLed(
                    on = kind != EqStatusKind.OFF,
                    color = if (kind == EqStatusKind.ACTIVE) HardcoreColors.toxic else HardcoreColors.bloodHi,
                    gray = kind == EqStatusKind.OFF,
                    size = 10.dp,
                    modifier = Modifier.padding(horizontal = 7.dp),
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
        trailing = trailing,
    )
}

private val ErrorGlass = Color(0x2EFF6B62)
private val WarningEdge = Brush.horizontalGradient(listOf(CyberColors.amber, CyberColors.violet.copy(alpha = 0.8f)))
private val ErrorEdge = SolidColor(CyberColors.error)
private val ActiveEdge = SolidColor(CyberColors.signal.copy(alpha = 0.7f))
private val QuietEdge = SolidColor(CyberColors.outline)

private fun statusEdge(kind: EqStatusKind): Brush =
    when (kind) {
        EqStatusKind.ACTIVE -> ActiveEdge
        EqStatusKind.WAITING, EqStatusKind.CONNECTING, EqStatusKind.LOST, EqStatusKind.UNSUPPORTED -> WarningEdge
        EqStatusKind.ERROR -> ErrorEdge
        EqStatusKind.OFF -> QuietEdge
    }

// The status lamp on its own (mini player, now playing). Shape carries the state as well as
// the color: filled when the effect runs, a ring when it is off, a square for problems.
@Composable
fun EqStatusLamp(
    kind: EqStatusKind,
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
    Box(
        modifier =
            modifier
                .size(10.dp)
                .then(
                    when (kind) {
                        EqStatusKind.ACTIVE -> Modifier.clip(CircleShape).background(lampColor)
                        EqStatusKind.OFF -> Modifier.border(1.5.dp, lampColor, CircleShape)
                        else -> Modifier.background(lampColor)
                    },
                ),
    )
}
