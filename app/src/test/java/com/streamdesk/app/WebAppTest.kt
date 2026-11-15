package com.streamdesk.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/** Livio in an iPad's browser, served by the TV box: its pages, the TV's account, and the provider's lists and streams relayed. */
class WebAppTest {
    private val login = XtreamLogin("http://provider.example:8080", "friend", "secret", categories = "Hebrew")
    private lateinit var server: LinkServer
    private var port = 0
    private lateinit var provider: ServerSocket

    @Before
    fun start() {
        val files = mapOf("index.html" to "<html>Livio</html>".toByteArray(), "js/app.js" to "var a=1".toByteArray())
        server = LinkServer("Livio (Mi Box)", "3.89", { "482913" }, object : LinkHandler {
            override fun play(queue: List<Channel>, index: Int) {}
            override fun command(action: String) {}
            override fun login(request: LoginRequest) {}
            override fun takeOver(): Pair<List<Channel>, Int>? = null
            override fun logs(): String = ""
            override fun account() = login
        }, files = { files[it] }, allowLocalTargets = true)
        port = server.start(0)
        // A fake provider: a playlist with relative parts, a redirect, and a stream
        provider = ServerSocket(0)
        thread(isDaemon = true) {
            while (!provider.isClosed) {
                val s = runCatching { provider.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    s.use {
                        val line = it.getInputStream().bufferedReader().readLine().orEmpty()
                        val path = line.split(' ').getOrElse(1) { "/" }
                        val out = it.getOutputStream()
                        when (path) {
                            "/live/friend/secret/1.m3u8" -> out.write("HTTP/1.1 302 Found\r\nLocation: /hls/1/index.m3u8\r\nContent-Length: 0\r\n\r\n".toByteArray())
                            "/hls/1/index.m3u8" -> {
                                val body = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:6,\nseg1.ts\n#EXTINF:6,\nhttp://cdn.example/seg2.ts\n"
                                out.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray())
                            }
                            else -> out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: 5\r\n\r\nVIDEO".toByteArray())
                        }
                        out.flush()
                    }
                }
            }
        }
    }

    @After
    fun stop() { server.stop(); provider.close() }

    private fun get(path: String): Triple<Int, String, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        return try {
            val code = c.responseCode
            Triple(code, c.contentType.orEmpty(), if (code < 400) c.inputStream.bufferedReader().readText() else "")
        } finally { c.disconnect() }
    }

    private fun relay(link: String, key: String = "482913") = "/p?k=$key&u=" + URLEncoder.encode(link, "UTF-8")

    @Test
    fun theAppsPagesAreServed() {
        assertEquals(Triple(200, "text/html; charset=utf-8", "<html>Livio</html>"), get("/app/"))
        assertEquals("var a=1", get("/app/js/app.js").third)
        assertEquals(302, get("/app?k=482913").first)
        assertEquals(404, get("/app/../secret").first)
        assertEquals(404, get("/app/missing.js").first)
    }

    @Test
    fun theTvsAccountNeedsTheCode() {
        assertEquals(403, get("/app/account?k=000000").first)
        val (status, _, body) = get("/app/account?k=482913")
        assertEquals(200, status)
        assertTrue(body, body.contains("\"user\":\"friend\"") && body.contains("\"folders\":\"Hebrew\""))
    }

    @Test
    fun streamsAndPlaylistsComeThroughTheTv() {
        val base = "http://127.0.0.1:${provider.localPort}"
        assertEquals("VIDEO", get(relay("$base/live/friend/secret/1.ts")).third)
        // the redirect is followed, and every address in the playlist points back through the TV
        val (status, type, playlist) = get(relay("$base/live/friend/secret/1.m3u8"))
        assertEquals(200, status)
        assertEquals("application/vnd.apple.mpegurl", type)
        val lines = playlist.lines()
        assertTrue(playlist, lines.contains(relay("$base/hls/1/seg1.ts")))
        assertTrue(playlist, lines.contains(relay("http://cdn.example/seg2.ts")))
        assertTrue(playlist, playlist.contains("URI=\"" + relay("$base/hls/1/key.bin") + "\""))
        assertEquals("VIDEO", get(lines.first { it.startsWith("/p?") }).third)
    }

    @Test
    fun theRelayNeedsTheCodeAndAWebAddress() {
        assertEquals(403, get(relay("http://provider.example/x.ts", key = "000000")).first)
        assertEquals(400, get(relay("file:///etc/passwd")).first)
    }

    @Test
    fun theHomeNetworkCantBeReachedThroughTheRelay() {
        val strict = LinkServer("TV", "1", { "482913" }, object : LinkHandler {
            override fun play(queue: List<Channel>, index: Int) {}
            override fun command(action: String) {}
            override fun login(request: LoginRequest) {}
            override fun takeOver(): Pair<List<Channel>, Int>? = null
            override fun logs(): String = ""
        })
        val p = strict.start(0)
        try {
            val c = URL("http://127.0.0.1:$p" + relay("http://192.168.1.1/admin")).openConnection() as HttpURLConnection
            assertEquals(403, c.responseCode)
        } finally { strict.stop() }
    }
}
