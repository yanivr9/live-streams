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
data class SportSettings(val text: String, val cap: QualityCap) {
    /** The same list, never above [limit] (the lower of the two limits wins). */
    fun limitedTo(limit: QualityCap) = if (limit.maxScore < cap.maxScore) copy(cap = limit) else this
}

/**
 * The highest quality the tiles may pick on this TV. A Mi Box takes minutes to show a picture on these 4K streams,
 * so a TV starts at Full HD; ⋮ → Quality on this TV switches 4K back on. Phones always use the Sport list's own limit.
 */
object DeviceQuality {
    private fun prefs(context: Context) = context.getSharedPreferences("launch", Context.MODE_PRIVATE)

    fun cap(context: Context): QualityCap =
        if (!context.isTelevision()) QualityCap.Best
        else runCatching { QualityCap.valueOf(prefs(context).getString("tv_quality", null).orEmpty()) }.getOrDefault(QualityCap.FullHd)

    fun set(context: Context, cap: QualityCap) = prefs(context).edit().putString("tv_quality", cap.name).apply()
}

/**
 * The Sport tab: a short list of chosen channels. Providers offer each channel in several qualities
 * ("ONE 1 4K", "ONE 1 FHD", "ONE 1 HD"); the list shows each channel once, in the best quality allowed.
 */
object SportList {
    // "Label = name at the provider" (several names may be separated by "|"). Sport 1–4 and 6 are called "YES SPORT" by this provider.
    val DEFAULT_TEXT = """
        Sport 1 = YES SPORT 1
        Sport 2 = YES SPORT 2
        Sport 3 = YES SPORT 3
        Sport 4 = YES SPORT 4
        Sport 5 = Sport 5
        Sport 5+ = Sport 5+ | Sport 5 Plus
        Sport 5 Live = Sport 5+ Live
        Sport 5 Stars = Sport 5+ Stars
        Sport 5 Gold = Sport 5 Gold | Sport 5+ Gold
        Sport 5+ Max = Sport 5+ Max
        Sport 6 = YES SPORT 6
        ONE 1
        ONE 2
    """.trimIndent()

    /** Earlier defaults: a phone that still has one of these (never edited) gets the new default. */
    val OLD_DEFAULT_TEXTS = listOf(
        """
        Sport 1 = YES SPORT 1
        Sport 2 = YES SPORT 2
        Sport 3 = YES SPORT 3
        Sport 4 = YES SPORT 4
        Sport 5 = Sport 5
        Sport 5+ = Sport 5+ | Sport 5 Plus
        Sport 5 Live = Sport 5+ Live
        Sport 5 Stars = Sport 5+ Stars
        Sport 6 = YES SPORT 6
        ONE 1
        ONE 2
        """.trimIndent(),
        """
        Sport 1 = YES SPORT 1
        Sport 2 = YES SPORT 2
        Sport 3 = YES SPORT 3
        Sport 4 = YES SPORT 4
        Sport 5 = Sport 5
        Sport 6 = YES SPORT 6
        ONE 1
        ONE 2
        """.trimIndent(),
    )

    private const val LOGO_DIR = "file:///android_asset/logos/"

    // Channel logos shipped with the app (the provider's own logo field is empty or broken for these channels)
    private val LOGOS = mapOf(
        "SPORT 1" to "sport1", "SPORT 2" to "sport2", "SPORT 3" to "sport3", "SPORT 4" to "sport4",
        "SPORT 5" to "sport5", "SPORT 6" to "sport6",
        "SPORT 5+" to "sport5plus", "SPORT 5 PLUS" to "sport5plus",
        "SPORT 5 LIVE" to "sport5live", "SPORT 5+ LIVE" to "sport5live",
        "SPORT 5 STARS" to "sport5stars", "SPORT 5+ STARS" to "sport5stars",
        "ONE 1" to "one1", "ONE 2" to "one2",
        "KAN 11" to "kan11", "KAN11" to "kan11", "CHANNEL 12" to "channel12", "CHANNEL 13" to "channel13", "CHANNEL 14" to "channel14",
    )

    private fun logoFor(entry: Entry): String? =
        (listOf(entry.label) + entry.patterns).firstNotNullOfOrNull { key ->
            LOGOS[key.trim().uppercase().removePrefix("YES ").trim()]
        }?.let { "$LOGO_DIR$it.png" }

    val DEFAULT = SportSettings(DEFAULT_TEXT, QualityCap.Best)

    // Words that only describe picture quality; anything else after the name means a different channel
    private val QUALITY_WORDS = setOf("4K", "UHD", "HDR", "FHD", "HD", "SD", "P50", "P60", "HEVC", "H265", "[MULTI-SUB]")

    private data class Entry(val label: String, val patterns: List<String>)
    private data class Quality(val score: Double, val label: String)

    private fun parse(text: String): List<Entry> = text.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val label = line.substringBefore('=').trim()
            val patterns = (if ('=' in line) line.substringAfter('=') else line).split('|').map { it.trim() }.filter { it.isNotEmpty() }
            if (patterns.isEmpty()) null else Entry(label.ifEmpty { patterns.first() }, patterns)
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
        return qualityOf(words)
    }

    private fun qualityOf(words: List<String>): Quality {
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

    /** How good the picture is, judging by the channel's name: 5+ is 4K, 4 Full HD, 3 HD, 1 SD, 2 when the name doesn't say. */
    fun qualityScore(channel: Channel): Double =
        qualityOf(channel.displayName().trim().uppercase().split(Regex("\\s+")).filter { it in QUALITY_WORDS }).score

    private const val SPORT5_LOGO = "sport5"

    /**
     * The Sport 5 tab: every Sport 5 channel the provider has, in every quality, grouped by channel
     * (Sport 5, 5+, Live, Stars, Gold, Max) and best quality first.
     */
    fun sport5All(channels: List<Channel>): List<Channel> {
        val families = listOf("", "PLUS", "LIVE", "STARS", "GOLD", "MAX")
        val familyLogos = mapOf("" to SPORT5_LOGO, "PLUS" to "sport5plus", "LIVE" to "sport5live", "STARS" to "sport5stars")
        val name = Regex("""^SPORT 5(\+)?(?=\s|$)(.*)$""")
        return channels.mapNotNull { c ->
            val m = name.find(c.displayName().trim().uppercase()) ?: return@mapNotNull null
            val words = m.groupValues[2].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val named = words.filter { it !in QUALITY_WORDS && it != "PLUS" }   // "Sport 5 Plus" and "Sport 5+ Plus" are Sport 5+
            val family = when {
                named.isEmpty() -> if (m.groupValues[1].isNotEmpty() || "PLUS" in words) "PLUS" else ""
                named.size == 1 && named[0] in families -> named[0]
                else -> return@mapNotNull null
            }
            Triple(c, family, qualityOf(words.filter { it in QUALITY_WORDS }))
        }
            .sortedWith(compareBy({ (_, family, _) -> families.indexOf(family) }, { (_, _, q) -> -q.score }))
            .map { (c, family, q) ->
                // The provider's own name ("Sport 5+ UHD 4K"), so versions of the same quality can be told apart
                c.copy(
                    name = c.displayName(),
                    group = q.label.ifEmpty { "Sport 5" },
                    logo = familyLogos[family]?.let { "$LOGO_DIR$it.png" } ?: c.logo,
                )
            }
    }

    /** One channel per line of the list, in that order, each in the best quality [SportSettings.cap] allows. */
    fun pick(channels: List<Channel>, settings: SportSettings): List<Channel> {
        val entries = parse(settings.text)
        if (entries.isEmpty()) return channels.filter { it.isSport() }
        return entries.mapNotNull { entry ->
            val options = channels.mapNotNull { c ->
                entry.patterns.firstNotNullOfOrNull { qualityIfMatches(c, it) }?.let { c to it }
            }
            if (options.isEmpty()) return@mapNotNull null
            val allowed = options.filter { it.second.score <= settings.cap.maxScore }
            // Nothing within the limit: take the lowest quality there is
            val (channel, quality) = if (allowed.isNotEmpty()) allowed.maxBy { it.second.score } else options.minBy { it.second.score }
            channel.copy(name = entry.label, group = quality.label, logo = logoFor(entry) ?: channel.logo)
        }
    }
}

/** The Israeli TV tab: the main Israeli channels, each in the best quality, picked the same way as the Sport list. */
object IsraelList {
    private val SETTINGS = SportSettings(
        """
        Channel 11 · Kan = Kan 11 | Kan11
        Channel 12 · Keshet = Channel 12
        Channel 13 · Reshet = Channel 13
        Channel 14 = Channel 14
        Channel 15 = Channel 15
        """.trimIndent(),
        QualityCap.Best,
    )

    fun pick(channels: List<Channel>, limit: QualityCap = QualityCap.Best): List<Channel> = SportList.pick(channels, SETTINGS.limitedTo(limit))
}

/** The Sport list settings, kept on the phone. */
class SportSettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("sport", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        SportSettings(
            text = prefs.getString("text", null)?.takeIf { it !in SportList.OLD_DEFAULT_TEXTS } ?: SportList.DEFAULT_TEXT,
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
