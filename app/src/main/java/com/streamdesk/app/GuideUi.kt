package com.streamdesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/** Guides already downloaded, so moving between screens doesn't fetch them again. Kept for 15 minutes. */
object GuideCache {
    private const val KEEP_MS = 15 * 60_000L
    private val entries = ConcurrentHashMap<String, Pair<Long, List<Program>>>()

    suspend fun get(login: XtreamLogin, channel: Channel): List<Program> {
        val now = System.currentTimeMillis()
        entries[channel.url]?.let { (at, list) -> if (now - at < KEEP_MS) return list }
        val list = Epg.loadFull(login, channel.url)
        entries[channel.url] = now to list
        return list
    }
}

private fun nowSeconds() = System.currentTimeMillis() / 1000

/** The current time in seconds, ticking every 30 s so "now" moves along while the screen is open. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(nowSeconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = nowSeconds()
        }
    }
    return now
}

/** One channel's schedule for the coming days. OK on the program that's on now plays the channel. */
@Composable
fun ChannelGuideDialog(channel: Channel, login: XtreamLogin, onPlay: () -> Unit, onDismiss: () -> Unit) {
    var programs by remember { mutableStateOf<List<Program>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(channel.url) {
        runCatching { GuideCache.get(login, channel) }
            .onSuccess { programs = it }
            .onFailure { error = "Couldn't load the guide: ${it.message ?: "no answer"}"; AppLog.e("guide", "Guide for ${channel.name} failed", it) }
    }
    val now = rememberNow()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(channel, Modifier.size(width = 56.dp, height = 38.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("${channel.displayName()} · Guide", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                val list = programs
                when {
                    error != null -> Message(error!!)
                    list == null -> Loading()
                    else -> {
                        val days = Epg.days(list, now)
                        if (days.isEmpty()) Message("This channel has no guide.")
                        else GuideList(days.map { d -> d.label to d.programs.map { null to it } }, now, onPick = { _, p -> if (p.isOn(now)) onPlay() })
                    }
                }
            }
        }
    }
}

/** The Guide tab: today's and tomorrow's programs on all the Sport channels, in time order. */
@Composable
fun SportGuidePane(channels: List<Channel>, loginFor: (Channel) -> XtreamLogin?, onPlay: (Channel) -> Unit, onNotYet: (Channel, Program) -> Unit, modifier: Modifier = Modifier) {
    var entries by remember { mutableStateOf<List<Pair<Channel, Program>>?>(null) }
    LaunchedEffect(channels) {
        entries = coroutineScope {
            channels.map { ch ->
                async {
                    val login = loginFor(ch) ?: return@async emptyList()
                    runCatching { GuideCache.get(login, ch) }.getOrDefault(emptyList()).map { ch to it }
                }
            }.awaitAll().flatten()
        }
    }
    val now = rememberNow()
    Box(modifier) {
        val all = entries
        when {
            channels.isEmpty() -> Message("No sport channels yet. Sign in to your IPTV account, or tap ⋮ → Edit Sport list.")
            all == null -> Loading()
            else -> {
                val soon = all.filter { (_, p) -> p.stop > now && p.start < now + 2 * 86_400 }.sortedWith(compareBy({ it.second.start }, { channels.indexOf(it.first) }))
                if (soon.isEmpty()) Message("Your provider has no guide for these channels right now.")
                else GuideList(
                    soon.groupBy { Epg.dayLabel(it.second.start, now) }.map { (label, list) -> label to list.map { (ch, p) -> ch to p } },
                    now,
                    onPick = { ch, p -> if (ch != null) { if (p.isOn(now)) onPlay(ch) else onNotYet(ch, p) } },
                )
            }
        }
    }
}

@Composable
private fun GuideList(days: List<Pair<String, List<Pair<Channel?, Program>>>>, now: Long, onPick: (Channel?, Program) -> Unit) {
    val first = remember { FocusRequester() }
    val isTv = LocalIsTv.current
    LaunchedEffect(Unit) {
        if (isTv) {
            delay(80)
            runCatching { first.requestFocus() }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        days.forEachIndexed { d, (label, rows) ->
            item(key = "day-$label") {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Amber,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                )
            }
            items(rows, key = { (ch, p) -> "${ch?.url}|${p.start}|${p.title}" }) { (ch, p) ->
                val isFirst = d == 0 && rows.firstOrNull()?.second == p && rows.first().first == ch
                GuideRow(ch, p, now, if (isFirst) Modifier.focusRequester(first) else Modifier) { onPick(ch, p) }
            }
        }
    }
}

@Composable
private fun GuideRow(channel: Channel?, program: Program, now: Long, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val lit = LocalIsTv.current && focused
    val on = program.isOn(now)
    val ink = if (lit) FocusInk else MaterialTheme.colorScheme.onSurface
    val soft = if (lit) FocusInkSoft else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (lit) Color.White else if (on) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (!on && program.description.isNotEmpty()) open = !open; onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(64.dp)) {
            Text(Epg.clock(program.start), color = ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(Epg.clock(program.stop), color = soft, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        if (channel != null) {
            ChannelLogo(channel, Modifier.padding(end = 10.dp).size(width = 52.dp, height = 36.dp))
        }
        Column(Modifier.weight(1f)) {
            if (channel != null) {
                Text(channel.displayName(), color = soft, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (on) {
                    Text(
                        "NOW",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp).background(OnAir, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(program.title, color = ink, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (program.description.isNotEmpty()) {
                Text(
                    program.description,
                    color = soft,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (open || (lit && !on)) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (on) {
                LinearProgressIndicator(
                    progress = { ((now - program.start).toFloat() / (program.stop - program.start)).coerceIn(0f, 1f) },
                    color = if (lit) FocusInk else OnAir,
                    trackColor = if (lit) Color(0xFFD5DADD) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                )
                Text(if (LocalIsTv.current) "OK to watch" else "Tap to watch", color = soft, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun ChannelLogo(channel: Channel, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (channel.logo.startsWith("http") || channel.logo.startsWith("file:///android_asset/")) {
            SubcomposeAsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
        }
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun Message(text: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
}
