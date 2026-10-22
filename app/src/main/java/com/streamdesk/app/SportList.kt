package com.streamdesk.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How high a picture the Sport list may pick. */
enum class QualityCap(val label: String, val maxScore: Double) {
    Best("Best available", Double.MAX_VALUE),
    FullHd("Up to Full HD", 4.9),
    Hd("Up to HD", 3.9),
}

/** The text the user edits (one channel per line) and the quality limit. */
data class SportSettings(val text: String, val cap: QualityCap)

/**
 * The Sport tab: a short list of chosen channels. Providers offer each channel in several qualities
 * ("ONE 1 4K", "ONE 1 FHD", "ONE 1 HD"); the list shows each channel once, in the best quality allowed.
 */
object SportList {
    // "Label = name at the provider". Sport 1–4 and 6 are called "YES SPORT" by this provider.
    val DEFAULT_TEXT = """
        Sport 1 = YES SPORT 1
        Sport 2 = YES SPORT 2
        Sport 3 = YES SPORT 3
        Sport 4 = YES SPORT 4
        Sport 5 = Sport 5
        Sport 6 = YES SPORT 6
        ONE 1
        ONE 2
    """.trimIndent()

    val DEFAULT = SportSettings(DEFAULT_TEXT, QualityCap.Best)

    // Words that only describe picture quality; anything else after the name means a different channel
    private val QUALITY_WORDS = setOf("4K", "UHD", "HDR", "FHD", "HD", "SD", "P50", "P60", "HEVC", "H265", "[MULTI-SUB]")

    private data class Entry(val label: String, val pattern: String)
    private data class Quality(val score: Double, val label: String)

    private fun parse(text: String): List<Entry> = text.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val label = line.substringBefore('=').trim()
            val pattern = if ('=' in line) line.substringAfter('=').trim() else label
            if (pattern.isEmpty()) null else Entry(label.ifEmpty { pattern }, pattern)
        }

    /** The quality of [channel] if it is the channel [pattern] (in any quality), otherwise null. */
    private fun qualityIfMatches(channel: Channel, pattern: String): Quality? {
        val name = channel.displayName().trim().uppercase()
        val want = pattern.trim().uppercase()
        if (!name.startsWith(want)) return null
        val rest = name.substring(want.length)
        if (rest.isNotEmpty() && !rest[0].isWhitespace()) return null   // "SPORT 1" must not match "SPORT 10"
        val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.any { it !in QUALITY_WORDS }) return null            // "SPORT 5 GOLD" is another channel
        val fourK = "4K" in words || "UHD" in words
        val score = when {
            fourK -> 5.0 + if ("HDR" in words) 0.5 else 0.0
            "FHD" in words -> 4.0 + if ("P50" in words || "P60" in words) 0.5 else 0.0
            "HD" in words -> 3.0
            "SD" in words -> 1.0
            else -> 2.0                                                 // no quality in the name
        }
        val label = when {
            fourK && "HDR" in words -> "4K HDR"
            fourK -> "4K"
            "FHD" in words && ("P50" in words || "P60" in words) -> "Full HD 50 fps"
            "FHD" in words -> "Full HD"
            "HD" in words -> "HD"
            "SD" in words -> "SD"
            else -> ""
        }
        return Quality(score, label)
    }

    /** One channel per line of the list, in that order, each in the best quality [SportSettings.cap] allows. */
    fun pick(channels: List<Channel>, settings: SportSettings): List<Channel> {
        val entries = parse(settings.text)
        if (entries.isEmpty()) return channels.filter { it.isSport() }
        return entries.mapNotNull { entry ->
            val options = channels.mapNotNull { c -> qualityIfMatches(c, entry.pattern)?.let { c to it } }
            if (options.isEmpty()) return@mapNotNull null
            val allowed = options.filter { it.second.score <= settings.cap.maxScore }
            // Nothing within the limit: take the lowest quality there is
            val (channel, quality) = if (allowed.isNotEmpty()) allowed.maxBy { it.second.score } else options.minBy { it.second.score }
            channel.copy(name = entry.label, group = quality.label)
        }
    }
}

/** The Sport list settings, kept on the phone. */
class SportSettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("sport", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        SportSettings(
            text = prefs.getString("text", null) ?: SportList.DEFAULT_TEXT,
            cap = runCatching { QualityCap.valueOf(prefs.getString("cap", null).orEmpty()) }.getOrDefault(QualityCap.Best),
        )
    )
    val settings: StateFlow<SportSettings> = _settings

    fun save(settings: SportSettings) {
        _settings.value = settings
        prefs.edit().putString("text", settings.text).putString("cap", settings.cap.name).apply()
    }

    companion object {
        @Volatile private var instance: SportSettingsStore? = null

        fun get(context: Context): SportSettingsStore =
            instance ?: synchronized(this) { instance ?: SportSettingsStore(context.applicationContext).also { instance = it } }
    }
}
