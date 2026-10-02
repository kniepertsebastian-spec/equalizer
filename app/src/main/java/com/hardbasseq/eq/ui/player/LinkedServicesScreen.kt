package com.hardbasseq.eq.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardbasseq.eq.R
import com.hardbasseq.eq.ui.theme.HardBassCardBorder
import com.hardbasseq.eq.ui.theme.PlayerTextColor
import com.hardbasseq.eq.ui.theme.PlayerTextMutedColor
import com.hardbasseq.eq.ui.theme.spacing

// "Verknüpfte Dienste": the music accounts and their connection state, in one place of their own
// (sound profiles are not accounts and live in the Equalizer).
@Composable
fun LinkedServicesScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenSoundProfiles: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val spotify by viewModel.spotifyAccount.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // Coming back from a sign-in screen: pick up the new state.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAccount() }

    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides PlayerTextColor) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = spacing.medium,
                        end = spacing.medium,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + spacing.medium,
                        bottom = spacing.medium,
                    ),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.services_back))
                }
                Text(
                    text = stringResource(R.string.services_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.services_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerTextMutedColor,
            )

            ServiceCard(
                name = stringResource(R.string.service_soundcloud_name),
                connected = signedIn,
                description =
                    stringResource(
                        if (signedIn) R.string.service_soundcloud_connected else R.string.service_soundcloud_not_connected,
                    ),
            ) {
                if (signedIn) {
                    OutlinedButton(onClick = viewModel::signIn) { Text(stringResource(R.string.service_action_reconnect)) }
                    TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.service_action_disconnect)) }
                } else {
                    Button(onClick = viewModel::signIn) { Text(stringResource(R.string.service_action_connect)) }
                }
            }

            var clientIdText by remember(spotify.clientId) { mutableStateOf(spotify.clientId) }
            ServiceCard(
                name = stringResource(R.string.service_spotify_name),
                connected = spotify.signedIn,
                description =
                    stringResource(
                        if (spotify.signedIn) R.string.service_spotify_connected else R.string.service_spotify_not_connected,
                    ),
                extra = {
                    OutlinedTextField(
                        value = clientIdText,
                        onValueChange = { clientIdText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.service_spotify_client_id)) },
                        singleLine = true,
                    )
                },
            ) {
                TextButton(
                    onClick = { viewModel.saveSpotifyClientId(clientIdText) },
                    enabled = clientIdText.trim() != spotify.clientId,
                ) { Text(stringResource(R.string.service_action_save)) }
                if (spotify.signedIn) {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveSpotifyClientId(clientIdText)
                            viewModel.signInSpotify()
                        },
                        enabled = clientIdText.isNotBlank(),
                    ) { Text(stringResource(R.string.service_action_reconnect)) }
                    TextButton(onClick = viewModel::signOutSpotify) { Text(stringResource(R.string.service_action_disconnect)) }
                } else {
                    Button(
                        onClick = {
                            viewModel.saveSpotifyClientId(clientIdText)
                            viewModel.signInSpotify()
                        },
                        enabled = clientIdText.isNotBlank(),
                    ) { Text(stringResource(R.string.service_action_connect)) }
                }
            }

            importState.message?.takeIf { importState.isError }?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = onOpenSoundProfiles) { Text(stringResource(R.string.services_open_profiles)) }
        }
    }
}

@Composable
private fun ServiceCard(
    name: String,
    connected: Boolean,
    description: String,
    extra: @Composable () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, HardBassCardBorder),
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (connected) R.string.service_status_connected else R.string.service_status_not_connected),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (connected) MaterialTheme.colorScheme.primary else PlayerTextMutedColor,
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = PlayerTextMutedColor)
            Spacer(modifier = Modifier.height(spacing.small))
            extra()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}
