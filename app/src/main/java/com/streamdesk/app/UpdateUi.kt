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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * A full-screen "new version" page: what's new, and where to get it. The phone opens the download in the browser;
 * a TV box has no browser, so it says to install the new version the same way as the first one.
 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    var message by remember { mutableStateOf<String?>(null) }
    val mainFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { mainFocus.requestFocus() }   // so OK on the TV remote works straight away
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Amber, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("A new version is ready", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Stream Desk ${info.name}", color = Amber, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    if (info.notes.isNotBlank()) {
                        Text(info.notes, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                    }
                    val how = message ?: if (isTv) "Install it on this TV box the same way as the first time." else null
                    how?.let { Text(it, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
                    Spacer(Modifier.height(28.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (isTv) {
                            Button(onClick = onDismiss, modifier = Modifier.focusRequester(mainFocus)) { Text("OK") }
                        } else {
                            OutlinedButton(onClick = onDismiss) { Text("Later") }
                            Button(
                                onClick = {
                                    if (Updates.openDownload(context, info)) onDismiss()
                                    else message = "No browser to download it with. The new version is at:\n${info.apkUrl}"
                                },
                                modifier = Modifier.focusRequester(mainFocus),
                            ) { Text("Download") }
                        }
                    }
                }
            }
        }
    }
}
