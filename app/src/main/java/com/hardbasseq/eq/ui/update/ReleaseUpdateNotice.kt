package com.hardbasseq.eq.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Checks on launch and every six hours while the app is open. Installation always needs user confirmation. */
@Composable
fun ReleaseUpdateNotice() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val client = remember(context) { ReleaseUpdateClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    var dismissedTag by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client) {
        while (true) {
            runCatching { client.checkForUpdate() }.getOrNull()?.let { update = it }
            delay(6L * 60L * 60L * 1000L)
        }
    }

    val available = update ?: return
    if (available.tag == dismissedTag) return

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.update_available, available.versionName), style = MaterialTheme.typography.titleMedium)
            if (downloading) Text(progress.ifEmpty { stringResource(R.string.update_downloading) })
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row {
                Button(
                    enabled = !downloading,
                    onClick = {
                        errorMessage = null
                        if (!context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                            errorMessage = resources.getString(R.string.update_allow_install)
                        } else {
                            downloading = true
                            scope.launch {
                                runCatching {
                                    val file =
                                        client.download(available) { downloaded, total ->
                                            val currentMb = downloaded / 1_000_000
                                            progress =
                                                if (total >
                                                    0L
                                                ) {
                                                    resources.getString(R.string.update_progress_total, currentMb, total / 1_000_000)
                                                } else {
                                                    resources.getString(R.string.update_progress, currentMb)
                                                }
                                        }
                                    context.startActivity(client.installIntent(file))
                                }.onFailure { errorMessage = it.message ?: resources.getString(R.string.update_failed) }
                                downloading = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.update_action)) }
                TextButton(
                    onClick = { dismissedTag = available.tag },
                    enabled = !downloading,
                ) { Text(stringResource(R.string.action_later)) }
            }
        }
    }
}
