package com.streamdesk.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** What a channel is showing: the program on now (with how far it has got) and the one after it. */
data class NowNext(val now: String?, val progress: Float?, val next: String?) {
    companion object {
        /** From a channel's short guide at [nowSeconds]; null when the guide has nothing to say. */
        fun of(programs: List<Program>?, nowSeconds: Long, clock: (Long) -> String = Epg::clock): NowNext? {
            if (programs.isNullOrEmpty()) return null
            val on = programs.firstOrNull { it.isOn(nowSeconds) }
            val next = programs.firstOrNull { it.start >= (on?.stop ?: nowSeconds) && it !== on }
            if (on == null && next == null) return null
            return NowNext(
                now = on?.let { "${it.title} · until ${clock(it.stop)}" },
                progress = on?.let { ((nowSeconds - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) },
                next = next?.let { "${clock(it.start)} ${it.title}" },
            )
        }
    }
}

/**
 * Every channel's now/next guide for the app session, shared by all screens. Only channels that are on screen are
 * asked for (each row or tile asks when it shows), a few at a time, and kept for 3 minutes, so scrolling through
 * hundreds of channels doesn't send hundreds of requests at once.
 */
object NowOn {
    private const val FRESH_MS = 3 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parallel = Semaphore(6)
    private val fetchedAt = ConcurrentHashMap<String, Long>()
    private var failures = 0   // in a row: when the network is down, the guide waits longer between tries instead of asking every 3 minutes
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val _guides = MutableStateFlow<Map<String, List<Program>>>(emptyMap())

    /** Channel link → its short guide. */
    val guides: StateFlow<Map<String, List<Program>>> = _guides

    /** Fetches the channel's guide unless it's fresh or already on its way. Cheap to call often. */
    fun want(url: String, login: XtreamLogin) {
        if (Epg.streamId(url) == null) return
        val at = fetchedAt[url]
        if (at != null && System.currentTimeMillis() - at < FRESH_MS) return
        if (!inFlight.add(url)) return
        scope.launch {
            try {
                parallel.withPermit {
                    val result = runCatching { Epg.load(login, url) }
                    if (result.isSuccess) {
                        failures = 0
                        fetchedAt[url] = System.currentTimeMillis()
                        _guides.update { it + (url to result.getOrThrow()) }
                    } else {
                        // Keep the guide we had; try again after 3, 6, 12 … up to 30 minutes
                        failures = (failures + 1).coerceAtMost(4)
                        fetchedAt[url] = System.currentTimeMillis() + (FRESH_MS * (1 shl failures) - FRESH_MS).coerceAtMost(27 * 60_000L)
                    }
                }
            } finally {
                inFlight.remove(url)
            }
        }
    }
}

/** The current time in seconds, moving on every 30 s so "until 21:00" and the progress bars stay right. */
@Composable
fun rememberNowSeconds(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    return now
}
