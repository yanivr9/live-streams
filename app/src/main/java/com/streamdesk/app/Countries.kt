package com.streamdesk.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** A live folder on the provider that isn't in the list (only its name is known until it's loaded). */
data class RemoteCategory(val sourceUrl: String, val id: String, val name: String)

/** Which country a provider folder is for, from how its name starts ("UK | Sport", "US: News", "Hebrew - Kids"). */
object Countries {
    /** Country codes → name and flag. */
    private val NAMES = linkedMapOf(
        "IL" to ("Israel" to "🇮🇱"), "UK" to ("United Kingdom" to "🇬🇧"), "US" to ("USA" to "🇺🇸"), "CA" to ("Canada" to "🇨🇦"),
        "FR" to ("France" to "🇫🇷"), "DE" to ("Germany" to "🇩🇪"), "IT" to ("Italy" to "🇮🇹"), "ES" to ("Spain" to "🇪🇸"),
        "PT" to ("Portugal" to "🇵🇹"), "NL" to ("Netherlands" to "🇳🇱"), "BE" to ("Belgium" to "🇧🇪"), "CH" to ("Switzerland" to "🇨🇭"),
        "AT" to ("Austria" to "🇦🇹"), "IE" to ("Ireland" to "🇮🇪"), "TR" to ("Turkey" to "🇹🇷"), "AR" to ("Arabic" to "🌙"),
        "RU" to ("Russia" to "🇷🇺"), "UA" to ("Ukraine" to "🇺🇦"), "PL" to ("Poland" to "🇵🇱"), "GR" to ("Greece" to "🇬🇷"),
        "RO" to ("Romania" to "🇷🇴"), "AL" to ("Albania" to "🇦🇱"), "EXYU" to ("Ex-Yugoslavia" to "🇷🇸"), "SE" to ("Sweden" to "🇸🇪"),
        "NO" to ("Norway" to "🇳🇴"), "DK" to ("Denmark" to "🇩🇰"), "FI" to ("Finland" to "🇫🇮"), "IN" to ("India" to "🇮🇳"),
        "PK" to ("Pakistan" to "🇵🇰"), "AU" to ("Australia" to "🇦🇺"), "NZ" to ("New Zealand" to "🇳🇿"), "BR" to ("Brazil" to "🇧🇷"),
        "MX" to ("Mexico" to "🇲🇽"), "LATAM" to ("Latin America" to "🌎"), "AF" to ("Africa" to "🌍"), "CN" to ("China" to "🇨🇳"),
        "IR" to ("Iran" to "🇮🇷"), "HU" to ("Hungary" to "🇭🇺"), "CZ" to ("Czechia" to "🇨🇿"), "BG" to ("Bulgaria" to "🇧🇬"),
    )

    /** Other spellings of the codes, and the words a folder name may start with instead of a code. */
    private val ALIASES = mapOf(
        "GB" to "UK", "ENG" to "UK", "USA" to "US", "GER" to "DE", "SP" to "ES", "ESP" to "ES", "ARB" to "AR", "ARA" to "AR",
        "HE" to "IL", "HEB" to "IL", "ISR" to "IL", "TUR" to "TR", "FRA" to "FR", "ITA" to "IT", "POR" to "PT", "POL" to "PL",
        "YU" to "EXYU", "EX-YU" to "EXYU", "LAT" to "LATAM",
        "israel" to "IL", "hebrew" to "IL", "uk" to "UK", "british" to "UK", "england" to "UK", "usa" to "US", "america" to "US",
        "american" to "US", "canada" to "CA", "france" to "FR", "french" to "FR", "germany" to "DE", "german" to "DE",
        "italy" to "IT", "italian" to "IT", "spain" to "ES", "spanish" to "ES", "portugal" to "PT", "netherlands" to "NL",
        "dutch" to "NL", "belgium" to "BE", "turkey" to "TR", "turkish" to "TR", "arabic" to "AR", "arab" to "AR",
        "russia" to "RU", "russian" to "RU", "poland" to "PL", "polish" to "PL", "greece" to "GR", "greek" to "GR",
        "romania" to "RO", "albania" to "AL", "india" to "IN", "indian" to "IN", "pakistan" to "PK", "brazil" to "BR",
        "mexico" to "MX", "latino" to "LATAM", "africa" to "AF", "african" to "AF", "ireland" to "IE", "sweden" to "SE",
        "norway" to "NO", "denmark" to "DK", "finland" to "FI", "australia" to "AU", "iran" to "IR", "persian" to "IR",
        "ukraine" to "UA", "hungary" to "HU", "bulgaria" to "BG", "china" to "CN", "chinese" to "CN",
    )

    // "[UK] News", "(US) Sport", or "UK | Sport", "US: News", "FR- TF1"
    private val CODE = Regex("""^\s*(?:[\[(]([A-Za-z]{2,5}(?:-[A-Za-z]{2})?)[\])]|([A-Za-z]{2,5}(?:-[A-Za-z]{2})?)\s*[|:\-–•/])""")

    /** The country code of a folder name, or "" when it doesn't say (it goes under Other). */
    fun of(name: String): String {
        val n = name.trim()
        CODE.find(n)?.groupValues?.let { g -> g[1].ifEmpty { g[2] } }?.uppercase()?.let { c -> (ALIASES[c] ?: c).takeIf { it in NAMES }?.let { return it } }
        val first = n.lowercase().split(Regex("""[^\p{L}]+""")).firstOrNull { it.isNotEmpty() }.orEmpty()
        return ALIASES[first] ?: ""
    }

    /** "🇬🇧 United Kingdom"-style parts for a code from [of]. */
    fun name(code: String): String = NAMES[code]?.first?.let { t(it) } ?: t("Other countries")
    fun flag(code: String): String = NAMES[code]?.second ?: "🌐"

    /** Order: the countries by name, Other last. */
    fun sort(codes: Collection<String>): List<String> = codes.sortedWith(compareBy({ it.isEmpty() }, { name(it).lowercase() }))
}

/** The provider's folders that aren't loaded, fetched (names only) the first time the All tab is opened. */
object RemoteCategories {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _all = MutableStateFlow<Map<String, List<RemoteCategory>>>(emptyMap())
    val all: StateFlow<Map<String, List<RemoteCategory>>> = _all
    private val asked = ConcurrentHashMap.newKeySet<String>()

    /** Accounts worth asking: a login that loads only some live folders (one that loads them all has nothing more). */
    fun eligible(source: PlaylistSource): Boolean =
        source.enabled && source.xtream?.let { it.live && it.categoryTerms.isNotEmpty() } == true

    private fun agent(source: PlaylistSource): String? = source.xtream?.userAgent?.ifBlank { null } ?: source.userAgent.ifBlank { null }

    fun fetch(source: PlaylistSource) {
        val login = source.xtream ?: return
        if (!eligible(source) || !asked.add(source.url)) return
        scope.launch {
            try {
                val list = Xtream.liveCategories(login, agent(source)).map { (id, name) -> RemoteCategory(source.url, id, name) }
                _all.update { it + (source.url to list) }
                AppLog.i("categories", "Provider has ${list.size} live folders")
            } catch (e: Exception) {
                asked.remove(source.url)   // ask again next time
                AppLog.e("categories", "Couldn't get the provider's folders", e)
            }
        }
    }

    /** Downloads one folder's channels. [liveExt] is the account's live format (ts or m3u8), as its other channels use. */
    suspend fun load(source: PlaylistSource, category: RemoteCategory, liveExt: String): List<Channel> {
        val login = source.xtream ?: throw java.io.IOException("this playlist has no login")
        val list = Xtream.categoryChannels(login, agent(source), category.id, category.name, liveExt).map { it.copy(source = source.url) }
        AppLog.i("categories", "Loaded ${list.size} channels of ${category.name}")
        return list
    }

    /** The folders not in the list yet: [loadedGroups] are the groups the list already has. */
    fun notLoaded(all: Map<String, List<RemoteCategory>>, loadedGroups: Set<String>): List<RemoteCategory> =
        all.values.flatten().filter { it.name.trim() !in loadedGroups }.distinctBy { it.name.trim().lowercase() }

    internal fun resetForTests() { asked.clear(); _all.value = emptyMap() }
    internal fun setForTests(url: String, list: List<RemoteCategory>) { _all.value = _all.value + (url to list) }
}

/** A provider folder opened from More countries: its channels once they've downloaded. */
data class PeekState(val category: RemoteCategory, val channels: List<Channel> = emptyList(), val loading: Boolean = true, val error: String? = null)
