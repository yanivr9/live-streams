package com.streamdesk.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What to send once a TV has been chosen. */
sealed interface TvJob {
    data class Play(val queue: List<Channel>, val index: Int) : TvJob
    data class Login(val source: PlaylistSource) : TvJob
}

/** Phone: choose a TV on the Wi-Fi (or type its address), then enter the code shown on it. */
@Composable
fun TvPickerDialog(title: String, onDismiss: () -> Unit, onChosen: (TvDevice, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finder = remember { TvFinder(context) }
    DisposableEffect(Unit) {
        finder.start()
        onDispose { finder.stop() }
    }
    val found by finder.devices.collectAsState()
    var picked by remember { mutableStateOf<TvDevice?>(null) }
    var typeAddress by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun choose(device: TvDevice) {
        picked = device
        code = LinkPrefs.code(context, device.host)
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (picked == null) title else "Code from the TV") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val device = picked
                if (device == null) {
                    if (found.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Looking for your TV…")
                        }
                    }
                    found.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choose(d) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null)
                            Column {
                                Text(d.name, fontWeight = FontWeight.SemiBold)
                                Text(d.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        "Open Stream Desk on the TV first. The phone and the TV must use the same Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!typeAddress) {
                        TextButton(onClick = { typeAddress = true }) { Text("My TV isn't listed") }
                    } else {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it.trim() },
                            label = { Text("TV address") },
                            placeholder = { Text("192.168.1.50") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("The TV shows its address under ⋮ → Link a phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            enabled = host.isNotEmpty() && !checking,
                            onClick = {
                                checking = true
                                error = null
                                scope.launch {
                                    val h = host.substringBefore(':')
                                    val p = host.substringAfter(':', "").toIntOrNull() ?: LINK_PORT
                                    runCatching { withContext(Dispatchers.IO) { LinkClient.ping(h, p) } }
                                        .onSuccess { choose(it) }
                                        .onFailure { error = it.message }
                                    checking = false
                                }
                            },
                        ) { Text(if (checking) "Checking…" else "Find it") }
                    }
                } else {
                    Text("${device.name}\nOn the TV open ⋮ → Link a phone, and type the 6-digit code here.")
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (picked != null) TextButton(onClick = { onChosen(picked!!, code) }, enabled = code.length == 6) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = { if (picked != null) picked = null else onDismiss() }) { Text(if (picked != null) "Back" else "Cancel") }
        },
    )
}

/** TV: on the first screen, until a playlist is loaded: how to connect the phone, with the code in plain sight. */
@Composable
fun LinkPhoneCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val code = remember { LinkReceiver.code(context) }
    val address = remember { LinkReceiver.address() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(2.dp, Amber),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Connect your phone, so you don't type with the remote", fontWeight = FontWeight.Bold)
                Text(
                    "On your phone: Stream Desk → Playlists → ⋮ next to your account → Send login to TV. Choose this TV and type the code." +
                        (if (address != null) "  If it isn't listed, use the address $address." else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(code.chunked(3).joinToString(" "), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Amber)
            }
        }
    }
}

/** TV: shows the code and address a phone needs. */
@Composable
fun LinkPhoneDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf(LinkReceiver.code(context)) }
    val address = remember { LinkReceiver.address() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link a phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("On your phone: open Stream Desk → Playlists → ⋮ next to your account → Send login to TV (or ⋮ on a channel → Play on TV). Choose this TV and type this code.")
                Text(code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Amber)
                Text(
                    if (address != null) "If the phone can't find the TV, type this address: $address"
                    else "This TV isn't connected to a network.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Keep Stream Desk open on the TV while you send to it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = { code = LinkReceiver.newCode(context) }) { Text("New code") } },
    )
}
