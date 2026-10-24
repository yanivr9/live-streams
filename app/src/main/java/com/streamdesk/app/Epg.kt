package com.streamdesk.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One entry of a channel's program guide. Times are epoch seconds. */
data class Program(val title: String, val start: Long, val stop: Long, val description: String = "") {
    fun isOn(nowSeconds: Long) = nowSeconds in start until stop
}

/** A day in the guide: "Today", "Tomorrow" or "Sun 26/10", and its programs. */
data class GuideDay(val label: String, val programs: List<Program>)

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
            if (title.isEmpty() || stop <= start) null else Program(title, start, stop, decode(o.optString("description")).trim())
        }.distinctBy { it.start to it.title }.sortedBy { it.start }
    }

    /** Titles come base64-encoded; some servers send plain text. */
    private fun decode(s: String): String =
        runCatching { String(Base64.getMimeDecoder().decode(s), Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it.none { c -> c == '\uFFFD' } }
            ?: s

    /** "Maccabi - Hapoel · until 21:00", or null when the guide has nothing on right now. */
    fun nowLabel(programs: List<Program>, nowSeconds: Long, zoneTime: (Long) -> String = Epg::clock): String? {
        val now = programs.firstOrNull { nowSeconds in it.start until it.stop } ?: return null
        return "${now.title} · until ${zoneTime(now.stop)}"
    }

    /** "21:00 News", the program after the one on now. */
    fun nextLabel(programs: List<Program>, nowSeconds: Long, zoneTime: (Long) -> String = Epg::clock): String? {
        val next = programs.firstOrNull { it.start > nowSeconds } ?: return null
        return "${zoneTime(next.start)} ${next.title}"
    }

    /** "Today", "Tomorrow" or "Sun 26/10" for a program starting at [start]; anything already on counts as today. */
    fun dayLabel(start: Long, nowSeconds: Long, zone: TimeZone = TimeZone.getDefault()): String {
        fun dayNumber(seconds: Long) = Math.floorDiv(seconds * 1000 + zone.getOffset(seconds * 1000), 86_400_000L)
        return when (dayNumber(start).coerceAtLeast(dayNumber(nowSeconds)) - dayNumber(nowSeconds)) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> SimpleDateFormat("EEE d/M", Locale.getDefault()).apply { timeZone = zone }.format(Date(start * 1000))
        }
    }

    /** What's still to come (and what's on now), split into days in the phone's time zone. */
    fun days(programs: List<Program>, nowSeconds: Long, zone: TimeZone = TimeZone.getDefault()): List<GuideDay> =
        programs.filter { it.stop > nowSeconds }
            .sortedBy { it.start }
            .groupBy { dayLabel(it.start, nowSeconds, zone) }
            .map { (label, list) -> GuideDay(label, list) }

    fun clock(seconds: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(seconds * 1000))

    /** The channel's whole guide (usually several days). Falls back to the short guide on servers without the full one. */
    suspend fun loadFull(login: XtreamLogin, channelUrl: String): List<Program> {
        val id = streamId(channelUrl) ?: return emptyList()
        val ua = login.userAgent.ifBlank { null }
        val full = runCatching { parse(fetchText(login.api("get_simple_data_table", "&stream_id=$id"), ua).text) }.getOrDefault(emptyList())
        return full.ifEmpty { parse(fetchText(login.api("get_short_epg", "&stream_id=$id&limit=40"), ua).text) }
    }

    suspend fun load(login: XtreamLogin, channelUrl: String): List<Program> {
        val id = streamId(channelUrl) ?: return emptyList()
        return parse(fetchText(login.api("get_short_epg", "&stream_id=$id&limit=3"), login.userAgent.ifBlank { null }).text)
    }
}
