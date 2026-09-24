package com.hardbasseq.eq.ui.diagnostics

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.hardbasseq.eq.audio.AudioCapabilities
import com.hardbasseq.eq.audio.AudioEngineState
import com.hardbasseq.eq.audio.AudioRoute
import com.hardbasseq.eq.audio.ProcessingSettings
import com.hardbasseq.eq.diagnostics.DiagnosticsEvent
import com.hardbasseq.eq.diagnostics.DiagnosticsReportFormatter
import com.hardbasseq.eq.ui.theme.spacing

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
        )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Systemdiagnose",
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
                        Text("Kopieren")
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
                Text("Zurück zum Equalizer")
            }
        }
    }
}
