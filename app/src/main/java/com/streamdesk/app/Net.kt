package com.streamdesk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// IPTV servers often accept only players they know, so the app identifies as VLC by default.
const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

/** Client names tried in order when a server refuses the previous one. */
val CLIENT_USER_AGENTS = listOf(
    VLC_USER_AGENT,
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
    "Lavf/60.16.100",
    "okhttp/4.12.0",
)

/** HTTP answers that can mean "I don't serve this client", worth retrying under another name. */
val REFUSED_CODES = setOf(400, 403, 406, 451)

private const val MAX_PLAYLIST_BYTES = 64L * 1024 * 1024
private const val SNIFF_BYTES = 4096

class HttpStatusException(val code: Int) : IOException(httpProblem(code))

/** The secure (https) connection failed, e.g. because the server only speaks plain http on that port. */
class TlsException(message: String) : IOException(message)

/** A plain-language reason for an HTTP error, written to follow "Couldn't …: ". */
fun httpProblem(code: Int): String = when (code) {
    401 -> "the server wants a username and password (HTTP 401)"
    403 -> "the server refused access (HTTP 403). The link may have expired, or be locked to another app or device"
    404, 410 -> "nothing was found at that link (HTTP $code). It may be mistyped or expired"
    451 -> "the server refuses it for legal reasons (HTTP 451). It isn't available in your country or on your network, " +
        "or the provider blocks this app"
    in 500..599 -> "the server has a problem (HTTP $code). Try again later"
    else -> "the server answered HTTP $code"
}

data class Fetched(val text: String, val userAgent: String)

/** Downloads a playlist. If the server refuses, tries again as other common players. */
suspend fun fetchText(link: String, preferredUserAgent: String? = null): Fetched =
    tryClients(link, preferredUserAgent) { ua -> download(link, ua, sniff = false)!! }

/**
 * For links typed in the main box: returns the text if the link is an M3U channel list,
 * or null if it's something else, such as a stream. Throws if the server can't be reached or refuses.
 */
suspend fun sniffChannelList(link: String): Fetched? =
    try {
        tryClients(link, null) { ua -> download(link, ua, sniff = true) ?: throw NotAPlaylist() }
    } catch (e: NotAPlaylist) {
        null
    }

private class NotAPlaylist : Exception()

private suspend fun tryClients(link: String, preferred: String?, get: (String) -> String): Fetched = withContext(Dispatchers.IO) {
    val agents = listOfNotNull(preferred) + CLIENT_USER_AGENTS.filter { it != preferred }
    var refused: HttpStatusException? = null
    for (ua in agents) {
        try {
            return@withContext Fetched(get(ua), ua)
        } catch (e: HttpStatusException) {
            if (e.code !in REFUSED_CODES) throw e
            AppLog.i("net", "  server refused ${agentName(ua)} (HTTP ${e.code}); trying another client name")
            refused = refused ?: e
        }
    }
    throw refused!!
}

private fun download(link: String, userAgent: String, sniff: Boolean): String? {
    val conn = try {
        URL(link).openConnection() as HttpURLConnection
    } catch (e: Exception) {
        throw IOException("that isn't a valid web link")
    }
    try {
        conn.connectTimeout = 15_000
        conn.readTimeout = if (sniff) 15_000 else 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", userAgent)
        val started = System.currentTimeMillis()
        val code = try {
            conn.responseCode
        } catch (e: IOException) {
            AppLog.e("net", "GET $link as ${agentName(userAgent)} failed after ${System.currentTimeMillis() - started} ms", e)
            if (e is javax.net.ssl.SSLException) {
                throw TlsException("the secure connection (https) failed. If the address uses a port like 8080, try http:// instead of https://")
            }
            throw IOException(
                when (e) {
                    is java.net.UnknownHostException -> "can't find the server. Check the address, or your internet connection"
                    is java.net.SocketTimeoutException -> "the server didn't answer in time. It may be down, or blocked on your network"
                    is java.net.ConnectException -> "the server refused the connection. It may be down, or blocked on your network"
                    else -> "can't reach the server. Check the link and your internet connection"
                }
            )
        }
        val moved = if (conn.url.toString() != link) " (via ${conn.url.host})" else ""
        AppLog.i("net", "GET $link as ${agentName(userAgent)} → HTTP $code$moved in ${System.currentTimeMillis() - started} ms")
        if (code !in 200..299) throw HttpStatusException(code)
        if (sniff && conn.contentType.orEmpty().lowercase().startsWith("video/")) return null

        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var checkedStart = false
        conn.inputStream.use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                // A stream starts with binary video data; a playlist starts with "#EXTM3U", "#EXTINF" or a link
                if (sniff && !checkedStart && out.size() >= SNIFF_BYTES) {
                    checkedStart = true
                    if (!looksLikeText(out.toByteArray())) return null
                }
                if (out.size() > MAX_PLAYLIST_BYTES) throw IOException("the file is larger than ${MAX_PLAYLIST_BYTES / 1024 / 1024} MB")
            }
        }
        AppLog.i("net", "  received ${out.size() / 1024} KB in ${System.currentTimeMillis() - started} ms")
        val text = out.toString(Charsets.UTF_8.name())
        return if (sniff && !isChannelList(text)) null else text
    } finally {
        conn.disconnect()
    }
}

private fun looksLikeText(bytes: ByteArray): Boolean {
    val start = String(bytes, 0, minOf(bytes.size, 64), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
    return start.startsWith("#") || start.startsWith("http", ignoreCase = true)
}
