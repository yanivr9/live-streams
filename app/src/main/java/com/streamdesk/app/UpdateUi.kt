package com.streamdesk.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A full-screen "new version" page: what's new, then Install. While installing it shows
 * "Updating Stream Desk… 45%" with a large progress bar, then hands the file to Android's installer.
 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val installFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { installFocus.requestFocus() }   // so OK on the TV remote installs straight away
    }

    fun install() {
        busy = true
        message = null
        percent = 0
        scope.launch {
            try {
                AppLog.i("update", "Downloading ${info.name}")
                val apk = Updates.download(context, info) { percent = it }
                if (Updates.install(context, apk)) onDismiss()
                else message = "Allow Stream Desk to install apps in the settings that just opened, then press Install again."
            } catch (e: Exception) {
                AppLog.e("update", "Update failed: ${e.message}")
                message = "The update didn't download: ${e.message ?: "unknown error"}. Check the internet connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Amber, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    if (busy) {
                        Text("Updating Stream Desk…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(20.dp))
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            color = Amber,
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Downloading version ${info.name} · $percent%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "The installer opens when the download finishes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        Text("A new version is ready", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Stream Desk ${info.name}",
                            color = Amber,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (info.notes.isNotBlank()) {
                            Text(
                                info.notes,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                        message?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                        }
                        Spacer(Modifier.height(28.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(onClick = onDismiss) { Text("Later") }
                            Button(onClick = ::install, modifier = Modifier.focusRequester(installFocus)) { Text("Install now") }
                        }
                    }
                }
            }
        }
    }
}
