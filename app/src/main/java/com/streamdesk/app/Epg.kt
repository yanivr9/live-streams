package com.streamdesk.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

/** One entry of a channel's program guide. Times are epoch seconds. */
data class Program(val title: String, val start: Long, val stop: Long)

/** "What's on now" for IPTV channels, from the provider's guide (Xtream `get_short_epg`). */
object Epg {
    /** The stream number at the end of a live link, e.g. http://host/live/user/pass/123.ts → 123. */
    fun streamId(url: String): String? =
        Regex("""/live/[^/]+/[^/]+/(\d+)(?:\.\w+)?(?:[?#].*)?$""").find(url)?.groupValues?.get(1)

    fun parse(json: String): List<Program> {
        val items = runCatching { JSONObject(json).optJSONArray("epg_listings") }.getOrNull() ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optString("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val stop = o.optString("stop_timestamp").toLongOrNull() ?: return@mapNotNull null
            val title = decode(o.optString("title")).trim()
            if (title.isEmpty()) null else Program(title, start, stop)
        }.sortedBy { it.start }
    }

    /** Titles come base64-encoded; some servers send plain text. */
    private fun decode(s: String): String =
        runCatching { String(Base64.getMimeDecoder().decode(s), Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it.none { c -> c == '�' } }
            ?: s

    /** "Maccabi - Hapoel · until 21:00", or null when the guide has nothing on right now. */
    fun nowLabel(programs: List<Program>, nowSeconds: Long, zoneTime: (Long) -> String = ::clock): String? {
        val now = programs.firstOrNull { nowSeconds in it.start until it.stop } ?: return null
        return "${now.title} · until ${zoneTime(now.stop)}"
    }

    /** "21:00 News", the program after the one on now. */
    fun nextLabel(programs: List<Program>, nowSeconds: Long, zoneTime: (Long) -> String = ::clock): String? {
        val next = programs.firstOrNull { it.start > nowSeconds } ?: return null
        return "${zoneTime(next.start)} ${next.title}"
    }

    private fun clock(seconds: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(seconds * 1000))

    suspend fun load(login: XtreamLogin, channelUrl: String): List<Program> {
        val id = streamId(channelUrl) ?: return emptyList()
        return parse(fetchText(login.api("get_short_epg", "&stream_id=$id&limit=3"), login.userAgent.ifBlank { null }).text)
    }
}
