package com.auracurve.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.auracurve.app.BuildConfig
import com.auracurve.app.R
import com.auracurve.app.audio.AudioCapabilities
import com.auracurve.app.audio.AudioEngineState
import com.auracurve.app.audio.AudioRoute
import com.auracurve.app.audio.ProcessingSettings
import com.auracurve.app.diagnostics.DiagnosticsEvent
import com.auracurve.app.diagnostics.DiagnosticsReportFormatter
import com.auracurve.app.diagnostics.OutputAudioInfo
import com.auracurve.app.diagnostics.ProcessExitRecord
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.SonicViolet
import com.auracurve.app.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: AudioEngineState,
    capabilities: AudioCapabilities,
    route: AudioRoute,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    processingSettings: ProcessingSettings? = null,
    recentEvents: List<DiagnosticsEvent> = emptyList(),
    playerSource: String? = null,
    androidAutoConnected: Boolean? = null,
    processExits: List<ProcessExitRecord> = emptyList(),
    outputAudio: OutputAudioInfo? = null,
    extendedAppDetection: Boolean? = null,
) {
    val spacing = MaterialTheme.spacing
    val clipboardManager = LocalClipboardManager.current
    val reportText =
        DiagnosticsReportFormatter.generateReport(
            engineState = state,
            capabilities = capabilities,
            route = route,
            processingSettings = processingSettings,
            recentEvents = recentEvents,
            playerSource = playerSource,
            androidAutoConnected = androidAutoConnected,
            processExits = processExits,
            outputAudio = outputAudio,
            extendedAppDetection = extendedAppDetection,
        )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.diag_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(reportText))
                        },
                    ) {
                        Text(stringResource(R.string.action_copy))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            AboutCard()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                SelectionContainer {
                    Text(
                        text = reportText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(spacing.medium),
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Button(
                onClick = onBackClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.diag_back))
            }
        }
    }
}

// Name, slogan and version (AuraCurve roadmap 01, M.2): what the app is, above the technical report.
@Composable
private fun AboutCard() {
    val spacing = MaterialTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.about_slogan),
                style = MaterialTheme.typography.titleSmall,
                color = SonicViolet,
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = stringResource(R.string.about_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = SonicSteel,
            )
        }
    }
}
