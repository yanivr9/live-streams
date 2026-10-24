package com.streamdesk.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** "A new version is available": downloads it and opens Android's installer. */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var percent by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Version ${info.name} is available") },
        text = {
            Column {
                Text(info.notes.ifBlank { "A newer version of Stream Desk is ready." })
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.padding(end = 8.dp))
                    Text("Downloading… $percent%", style = MaterialTheme.typography.bodySmall)
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            AppLog.i("update", "Downloading ${info.name}")
                            val apk = Updates.download(context, info) { percent = it }
                            if (Updates.install(context, apk)) onDismiss()
                            else message = "Allow Stream Desk to install apps in the settings that just opened, then tap Install again."
                        } catch (e: Exception) {
                            AppLog.e("update", "Update failed: ${e.message}")
                            message = "The update failed: ${e.message ?: "unknown error"}"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Install") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Later") } },
    )
}
