package com.hardbasseq.eq.ui.player.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R

// "PREVIEW" next to a title that only plays SoundCloud's 30-second preview. An outlined text
// tag, so it reads without color.
@Composable
fun PreviewTag(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.preview_only)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.error,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Text(
            text = stringResource(R.string.preview_tag),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
