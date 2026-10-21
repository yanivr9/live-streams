package com.streamdesk.app

import java.net.URI
import org.json.JSONObject

data class Channel(
    val url: String,
    val name: String,
    val group: String = "",
    val logo: String = "",
    val favorite: Boolean = false,
    // Optional per-channel HTTP headers, as set by #EXTVLCOPT lines in IPTV playlists
    val userAgent: String = "",
    val referrer: String = "",
    // Link of the playlist this channel came from; empty for channels added by hand
    val source: String = "",
    val kind: String = Kind.LIVE,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("name", name)
        .put("group", group)
        .put("logo", logo)
        .put("favorite", favorite)
        .put("userAgent", userAgent)
        .put("referrer", referrer)
        .put("source", source)
        .put("kind", kind)

    companion object {
        fun fromJson(o: JSONObject) = Channel(
            url = o.getString("url"),
            name = o.optString("name").ifBlank { nameFromUrl(o.getString("url")) },
            group = o.optString("group"),
            logo = o.optString("logo"),
            favorite = o.optBoolean("favorite"),
            userAgent = o.optString("userAgent"),
            referrer = o.optString("referrer"),
            source = o.optString("source"),
            kind = o.optString("kind"),
        )
    }
}

/** A channel list loaded from a link, which can be refreshed later. */
data class PlaylistSource(
    val url: String,
    val name: String,
    val channelCount: Int = 0,
    val updatedAt: Long = 0,
    val error: String = "",
    val userAgent: String = "",
    val xtream: XtreamLogin? = null,
    val account: String = "",
    val enabled: Boolean = true,
    val updateHours: Int = 24,   // 0 = only when asked
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("name", name)
        .put("channelCount", channelCount)
        .put("updatedAt", updatedAt)
        .put("error", error)
        .put("userAgent", userAgent)
        .put("xtream", xtream?.toJson())
        .put("account", account)
        .put("enabled", enabled)
        .put("updateHours", updateHours)

    companion object {
        fun fromJson(o: JSONObject) = PlaylistSource(
            url = o.getString("url"),
            name = o.optString("name").ifBlank { playlistName(o.getString("url")) },
            channelCount = o.optInt("channelCount"),
            updatedAt = o.optLong("updatedAt"),
            error = o.optString("error"),
            userAgent = o.optString("userAgent"),
            xtream = o.optJSONObject("xtream")?.let(XtreamLogin::fromJson),
            account = o.optString("account"),
            enabled = o.optBoolean("enabled", true),
            updateHours = o.optInt("updateHours", 24),
        )
    }
}

/** A default name for a playlist link: the file name, or just the host for links like get.php?... */
fun playlistName(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host ?: return url
    val last = uri.path.orEmpty().split('/').lastOrNull { it.isNotBlank() }
    return if (last == null || last.endsWith(".php", ignoreCase = true)) host else "$host · $last"
}

/** Links that are probably channel lists rather than streams (checked by downloading them). */
fun mightBePlaylistLink(url: String): Boolean {
    val u = url.lowercase()
    val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault(u)
    return path.endsWith(".m3u") || path.endsWith(".m3u8") || path.endsWith(".php") || "type=m3u" in u
}

/** True for an IPTV channel list; false for an HLS stream playlist, which also starts with #EXTM3U. */
fun isChannelList(text: String): Boolean =
    text.contains("#EXTINF", ignoreCase = true) && !text.contains("#EXT-X-", ignoreCase = true) && text.contains("://")

enum class StreamFormat(val label: String) {
    Auto("Auto"),
    Hls("HLS"),
    Dash("DASH"),
    Direct("Direct file"),
}

fun nameFromUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host ?: return url
    val last = uri.path.orEmpty().split('/').lastOrNull { it.isNotBlank() }
    return if (last == null) host else "$host · $last"
}

/** Guesses the format from the link; Auto lets ExoPlayer decide from the file extension. */
fun detectFormat(url: String): StreamFormat {
    val u = url.lowercase()
    return when {
        ".m3u8" in u || "format=m3u8" in u || "/hls/" in u -> StreamFormat.Hls
        ".mpd" in u || "format=mpd" in u -> StreamFormat.Dash
        else -> StreamFormat.Auto
    }
}

/** Reads and writes M3U / M3U8 channel lists (the IPTV playlist format VLC uses). */
object M3u {
    private val attribute = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var name: String? = null
        var group = ""
        var logo = ""
        var userAgent = ""
        var referrer = ""

        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF')
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    group = attrs["group-title"].orEmpty()
                    logo = attrs["tvg-logo"].orEmpty()
                    userAgent = attrs["http-user-agent"] ?: userAgent
                    referrer = attrs["http-referrer"] ?: attrs["http-referer"] ?: referrer
                    // The title follows the first comma once the quoted attributes are removed
                    name = line.replace(attribute, "").substringAfter(',', "").trim().ifEmpty { null }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    when (opt.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> userAgent = opt.substringAfter('=').trim()
                        "http-referrer", "http-referer" -> referrer = opt.substringAfter('=').trim()
                    }
                }
                line.startsWith("#") -> Unit
                line.contains("://") -> {
                    out += Channel(line, name ?: nameFromUrl(line), group, logo, false, userAgent, referrer)
                    name = null; group = ""; logo = ""; userAgent = ""; referrer = ""
                }
            }
        }
        return out
    }

    fun export(channels: List<Channel>): String = buildString {
        appendLine("#EXTM3U")
        for (c in channels.filter { it.kind != Kind.SERIES }) {
            append("#EXTINF:-1")
            if (c.logo.isNotBlank()) append(" tvg-logo=\"${c.logo}\"")
            if (c.group.isNotBlank()) append(" group-title=\"${c.group}\"")
            appendLine(",${c.name}")
            if (c.userAgent.isNotBlank()) appendLine("#EXTVLCOPT:http-user-agent=${c.userAgent}")
            if (c.referrer.isNotBlank()) appendLine("#EXTVLCOPT:http-referrer=${c.referrer}")
            appendLine(c.url)
        }
    }
}

// Providers prefix every channel with its country ("IL: Sport 5"). It repeats on every row and eats the space for the real name.
private val COUNTRY_PREFIX = Regex("""^[A-Z]{2}\s*[:|]\s+""")

/** The name to show in lists: without a leading country code such as "IL: ". */
fun Channel.displayName(): String = name.replace(COUNTRY_PREFIX, "").ifBlank { name }

/** Sport channels: the folder or the name mentions sport (in English or Hebrew). */
fun Channel.isSport(): Boolean =
    group.contains("sport", ignoreCase = true) || name.contains("sport", ignoreCase = true) ||
        group.contains("ספורט") || name.contains("ספורט")
