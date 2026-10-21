package com.streamdesk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

/** Kinds of items in the channel list. Live channels use "" so lists saved by older versions stay valid. */
object Kind {
    const val LIVE = ""
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val EPISODE = "episode"
}

/** An IPTV account on an Xtream Codes compatible server: the "server, username, password" login. */
data class XtreamLogin(
    val server: String,
    val username: String,
    val password: String,
    val live: Boolean = true,
    val movies: Boolean = true,
    val series: Boolean = true,
    // Empty means the app's default (it identifies as VLC and tries other common players if refused)
    val userAgent: String = "",
    // Only load folders whose name contains one of these words (comma separated); empty means all folders
    val categories: String = "",
) {
    /** The words from [categories], lower case. */
    val categoryTerms: List<String> get() = categories.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** The server address with a trailing slash, e.g. http://host:8080/ */
    val base: String get() = server.trim().trimEnd('/') + "/"

    fun api(action: String? = null, extra: String = ""): String =
        base + "player_api.php?username=${enc(username)}&password=${enc(password)}" +
            (if (action != null) "&action=$action" else "") + extra

    /** Identifies this login in the playlist list. */
    val sourceUrl: String get() = api()

    fun toJson(): JSONObject = JSONObject()
        .put("server", server)
        .put("username", username)
        .put("password", password)
        .put("live", live)
        .put("movies", movies)
        .put("series", series)
        .put("userAgent", userAgent)
        .put("categories", categories)

    companion object {
        fun fromJson(o: JSONObject) = XtreamLogin(
            server = o.getString("server"),
            username = o.getString("username"),
            password = o.getString("password"),
            live = o.optBoolean("live", true),
            movies = o.optBoolean("movies", true),
            series = o.optBoolean("series", true),
            userAgent = o.optString("userAgent"),
            categories = o.optString("categories"),
        )

        /** Reads the server, username and password out of a get.php or player_api.php link. */
        fun fromLink(link: String): XtreamLogin? {
            val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host == null) return null
            val query = uri.rawQuery?.split('&')?.associate {
                it.substringBefore('=') to runCatching { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
            } ?: return null
            val user = query["username"]?.takeIf { it.isNotBlank() } ?: return null
            val pass = query["password"]?.takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return XtreamLogin("${uri.scheme}://${uri.host}$port/", user, pass)
        }

        /** True for links like http://host:8080/get.php?username=…&password=… */
        fun isLoginLink(link: String): Boolean {
            val path = runCatching { URI(link.trim()).path.orEmpty().lowercase() }.getOrDefault("")
            return (path.endsWith("/get.php") || path.endsWith("/player_api.php")) && fromLink(link) != null
        }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/**
 * What a sign-in loaded. [failedKinds] are the parts (live, movies, series) that couldn't be downloaded,
 * described in [problems]; the rest loaded fine.
 */
data class XtreamResult(
    val channels: List<Channel>,
    val userAgent: String,
    val account: String,
    val failedKinds: Set<String> = emptySet(),
    val problems: String = "",
)

object Xtream {
    /** Signs in and downloads the account's live channels, movies and series. Throws IOException with a readable message. */
    suspend fun load(
        requested: XtreamLogin,
        preferredUserAgent: String? = null,
        onProgress: (String) -> Unit = {},
        onPart: (kind: String, items: List<Channel>) -> Unit = { _, _ -> },
    ): XtreamResult = withContext(Dispatchers.IO) {
        onProgress("Signing in…")
        var effective = requested
        val auth = try {
            fetchText(effective.api(), preferredUserAgent)
        } catch (e: TlsException) {
            // Many IPTV panels serve plain http on ports like 8080; a typed https:// address can't work there
            if (!effective.base.startsWith("https://", ignoreCase = true)) throw e
            val plain = effective.copy(server = "http://" + effective.base.substring("https://".length))
            AppLog.i("signin", "https:// failed; trying ${plain.base} (plain http)")
            fetchText(plain.api(), preferredUserAgent).also { effective = plain }
        }
        val login = effective
        val ua = auth.userAgent
        val root = parseObject(auth.text)
            ?: throw IOException("this server doesn't answer like an IPTV login server. Check the server address")
        val user = root.optJSONObject("user_info")
            ?: throw IOException("this server doesn't answer like an IPTV login server. Check the server address")
        if (user.optString("auth") != "1") {
            AppLog.e("signin", "Server says the username or password is wrong")
            throw IOException("the username or password is wrong")
        }
        val expires = user.optString("exp_date").toLongOrNull()?.takeIf { it > 0 }?.let {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000))
        }
        val status = user.optString("status")
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
            throw IOException("the account is ${status.lowercase()}" + (expires?.let { " (it ran until $it)" } ?: ""))
        }
        val formats = user.optJSONArray("allowed_output_formats")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
        val liveExt = if (formats.isEmpty() || "ts" in formats) "ts" else "m3u8"
        AppLog.i(
            "signin",
            "Account OK: status ${status.ifBlank { "?" }}, expires ${expires ?: "never"}, " +
                "screens ${user.optString("max_connections").ifBlank { "?" }} (in use ${user.optString("active_cons").ifBlank { "?" }}), " +
                "formats $formats → live as .$liveExt",
        )

        suspend fun categories(action: String): Map<String, String> =
            parseArray(fetchText(login.api(action), ua).text).associate { it.optString("category_id") to it.optString("category_name") }

        suspend fun items(action: String): List<JSONObject> = parseArray(fetchText(login.api(action), ua).text)

        val path = "${enc(login.username)}/${enc(login.password)}"
        val out = mutableListOf<Channel>()
        val counts = linkedMapOf<String, Int>()
        val failed = linkedMapOf<String, String>()   // kind → reason

        // One part of the account: its categories plus its items, turned into list entries
        suspend fun part(kind: String, label: String, catAction: String, itemAction: String, make: (JSONObject, Map<String, String>) -> Channel?) {
            onProgress(if (out.isEmpty()) "Loading $label…" else "Loaded ${out.size} items. Loading $label…")
            try {
                val cats = categories(catAction)
                val terms = login.categoryTerms
                val allowed: Set<String>? = if (terms.isEmpty()) null else cats.filterValues { n -> terms.any { n.contains(it, ignoreCase = true) } }.keys
                if (allowed != null && allowed.isEmpty()) {
                    throw IOException("no $label folders match \"${login.categories.trim()}\". Folders on this server: ${cats.values.take(12).joinToString(", ")}")
                }
                val made = items(itemAction)
                    .filter { allowed == null || it.optString("category_id") in allowed }
                    .mapNotNull { make(it, cats) }
                    .map { if (ua == VLC_USER_AGENT) it else it.copy(userAgent = ua) }
                out += made
                counts[label] = made.size
                AppLog.i("signin", "Loaded ${made.size} $label in ${allowed?.size ?: cats.size} of ${cats.size} folders" + if (allowed != null) " (only: ${login.categories.trim()})" else "")
                onPart(kind, made)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("signin", "$label didn't load", e)
                failed[kind] = "$label didn't load: ${e.message ?: "network error"}"
            } catch (e: OutOfMemoryError) {
                failed[kind] = "$label didn't load: the list is too big for this phone's memory"
            }
        }

        if (login.live) part(Kind.LIVE, "channels", "get_live_categories", "get_live_streams") { o, cats ->
            val id = o.optString("stream_id").ifBlank { return@part null }
            Channel(
                url = "${login.base}live/$path/$id.$liveExt",
                name = o.optString("name").ifBlank { "Channel $id" },
                group = cats[o.optString("category_id")].orEmpty(),
                logo = o.optString("stream_icon"),
                kind = Kind.LIVE,
            )
        }
        if (login.movies) part(Kind.MOVIE, "movies", "get_vod_categories", "get_vod_streams") { o, cats ->
            val id = o.optString("stream_id").ifBlank { return@part null }
            Channel(
                url = "${login.base}movie/$path/$id.${o.optString("container_extension").ifBlank { "mp4" }}",
                name = o.optString("name").ifBlank { "Movie $id" },
                group = cats[o.optString("category_id")].orEmpty(),
                logo = o.optString("stream_icon"),
                kind = Kind.MOVIE,
            )
        }
        if (login.series) part(Kind.SERIES, "series", "get_series_categories", "get_series") { o, cats ->
            val id = o.optString("series_id").ifBlank { return@part null }
            Channel(
                // Episodes are listed when the series is opened, from this address
                url = login.api("get_series_info", "&series_id=$id"),
                name = o.optString("name").ifBlank { "Series $id" },
                group = cats[o.optString("category_id")].orEmpty(),
                logo = o.optString("cover"),
                kind = Kind.SERIES,
            )
        }

        if (counts.isEmpty() && failed.isNotEmpty()) throw IOException(failed.values.first().substringAfter(": "))
        if (out.isEmpty()) throw IOException("the account has no channels, movies or series")

        val account = buildList {
            counts.forEach { (label, n) -> add("$n $label") }
            if (expires != null) add("expires $expires")
            user.optString("max_connections").toIntOrNull()?.let { add(if (it == 1) "1 screen" else "$it screens") }
        }.joinToString(" · ")

        XtreamResult(out, ua, account, failed.keys, failed.values.joinToString("; "))
    }

    /** Lists a series' episodes as playable items, in season and episode order. */
    suspend fun episodes(series: Channel): List<Channel> = withContext(Dispatchers.IO) {
        val login = XtreamLogin.fromLink(series.url) ?: throw IOException("this series has no server address")
        val info = parseObject(fetchText(series.url, series.userAgent.ifBlank { null }).text)
            ?: throw IOException("the server didn't send the episode list")
        val path = "${enc(login.username)}/${enc(login.password)}"

        val found = mutableListOf<Triple<Int, Int, JSONObject>>()
        fun add(seasonKey: String?, o: JSONObject) {
            val season = o.optString("season").toIntOrNull() ?: seasonKey?.toIntOrNull() ?: 0
            found += Triple(season, o.optString("episode_num").toIntOrNull() ?: 0, o)
        }
        // Servers send episodes either as {"1": [...], "2": [...]} or as [[...], [...]]
        when (val eps = info.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key ->
                val arr = eps.optJSONArray(key) ?: return@forEach
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(key, it) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val season = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until season.length()) season.optJSONObject(j)?.let { add(null, it) }
                    is JSONObject -> add(null, season)
                }
            }
        }
        found.sortedWith(compareBy({ it.first }, { it.second })).mapNotNull { (season, number, o) ->
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            val title = o.optString("title").ifBlank { "Episode $number" }
            Channel(
                url = "${login.base}series/$path/$id.${o.optString("container_extension").ifBlank { "mp4" }}",
                name = "S$season E$number · $title",
                group = series.name,
                logo = series.logo,
                userAgent = series.userAgent,
                kind = Kind.EPISODE,
            )
        }
    }

    private fun parseObject(text: String): JSONObject? =
        text.trim().takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() }

    // Empty lists sometimes come back as {} instead of []
    private fun parseArray(text: String): List<JSONObject> {
        val t = text.trim()
        if (!t.startsWith("[")) return emptyList()
        val arr = runCatching { JSONArray(t) }.getOrNull() ?: return emptyList()
        return List(arr.length()) { arr.optJSONObject(it) }.filterNotNull()
    }
}
