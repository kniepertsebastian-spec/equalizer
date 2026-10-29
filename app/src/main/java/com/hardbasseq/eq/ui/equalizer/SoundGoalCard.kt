package com.hardbasseq.eq.ui.equalizer

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.SoundGoal
import com.hardbasseq.eq.ui.theme.spacing

@StringRes
internal fun SoundGoal.nameRes(): Int =
    when (this) {
        SoundGoal.BALANCED -> R.string.goal_balanced_name
        SoundGoal.VOCALS_FORWARD -> R.string.goal_vocals_forward_name
        SoundGoal.MORE_POWER -> R.string.goal_more_power_name
        SoundGoal.MORE_PUNCH -> R.string.goal_more_punch_name
        SoundGoal.LESS_HARSH -> R.string.goal_less_harsh_name
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
        SoundGoal.LESS_BASS -> R.string.goal_less_bass_desc
    }

// Beginners start here: what they want to hear, in plain words, with what it changes.
@Composable
internal fun SoundGoalCard(
    active: SoundGoal,
    onSelect: (SoundGoal) -> Unit,
) {
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
