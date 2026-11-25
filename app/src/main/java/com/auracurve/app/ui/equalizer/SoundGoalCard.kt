package com.auracurve.app.ui.equalizer

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.preset.SoundGoal
import com.auracurve.app.ui.components.hardcore.HcIconKey
import com.auracurve.app.ui.components.hardcore.HcRotarySwitch
import com.auracurve.app.ui.components.hardcore.HcVfd
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.spacing

@StringRes
internal fun SoundGoal.nameRes(): Int =
    when (this) {
        SoundGoal.BALANCED -> R.string.goal_balanced_name
        SoundGoal.VOCALS_FORWARD -> R.string.goal_vocals_forward_name
        SoundGoal.MORE_POWER -> R.string.goal_more_power_name
        SoundGoal.MORE_PUNCH -> R.string.goal_more_punch_name
        SoundGoal.LESS_HARSH -> R.string.goal_less_harsh_name
        SoundGoal.CLEAR_BASS -> R.string.goal_clear_bass_name
        SoundGoal.LESS_BASS -> R.string.goal_less_bass_name
    }

@StringRes
internal fun SoundGoal.descriptionRes(): Int =
    when (this) {
        SoundGoal.BALANCED -> R.string.goal_balanced_desc
        SoundGoal.VOCALS_FORWARD -> R.string.goal_vocals_forward_desc
        SoundGoal.MORE_POWER -> R.string.goal_more_power_desc
        SoundGoal.MORE_PUNCH -> R.string.goal_more_punch_desc
        SoundGoal.LESS_HARSH -> R.string.goal_less_harsh_desc
        SoundGoal.CLEAR_BASS -> R.string.goal_clear_bass_desc
        SoundGoal.LESS_BASS -> R.string.goal_less_bass_desc
    }

// Beginners start here: what they want to hear, in plain words, with what it changes.
@Composable
internal fun SoundGoalCard(
    active: SoundGoal,
    onSelect: (SoundGoal) -> Unit,
) {
    if (LocalHardcoreLook.current) {
        HcSoundGoalCard(active, onSelect)
        return
    }
    val spacing = MaterialTheme.spacing
    Section {
        SectionHeader(
            title = stringResource(R.string.goal_title),
            info = stringResource(R.string.goal_subtitle),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            SoundGoal.entries.forEach { goal ->
                ChoiceChip(
                    text = stringResource(goal.nameRes()),
                    selected = goal == active,
                    onClick = { onSelect(goal) },
                )
            }
        }
        Text(
            text = stringResource(active.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = SonicStyle.mutedText,
        )
    }
}

// The hardcore sound goal: a rotary switch with one stop per goal, a display that reads the
// chosen goal out, and two keys to step through the goals one by one.
@Composable
private fun HcSoundGoalCard(
    active: SoundGoal,
    onSelect: (SoundGoal) -> Unit,
) {
    val goals = SoundGoal.entries
    val index = goals.indexOf(active)
    val name = stringResource(active.nameRes())
    Section {
        SectionHeader(
            title = stringResource(R.string.goal_title),
            info = stringResource(R.string.goal_subtitle),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HcRotarySwitch(
                index = index,
                count = goals.size,
                onSelect = { onSelect(goals[it]) },
                description = stringResource(R.string.goal_title),
                stateDescription = name,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HcVfd(
                    positionLabel = stringResource(R.string.goal_position_label),
                    name = name,
                    index = index,
                    count = goals.size,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HcIconKey(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.goal_step_previous),
                        onClick = { onSelect(goals[index - 1]) },
                        enabled = index > 0,
                        modifier = Modifier.weight(1f),
                    )
                    HcIconKey(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.goal_step_next),
                        onClick = { onSelect(goals[index + 1]) },
                        enabled = index < goals.lastIndex,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            text = stringResource(active.descriptionRes()),
            style = HardcoreType.note,
            color = HardcoreColors.ash,
        )
    }
}
