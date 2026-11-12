package com.streamdesk.app

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** A server saved as https:// that only speaks plain http on its port (like cdn-…:8080): requests still get through. */
class PlainHttpTest {
    // A tiny plain-http server: answers every request with the same JSON (a TLS hello just gets a broken reply)
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val worker = thread(isDaemon = true) {
        while (!socket.isClosed) {
            val c = runCatching { socket.accept() }.getOrNull() ?: break
            runCatching {
                c.use {
                    val reader = it.getInputStream().bufferedReader()
                    val first = reader.readLine().orEmpty()
                    if (first.startsWith("GET")) {
                        while (reader.readLine().orEmpty().isNotEmpty()) { /* headers */ }
                        val body = """{"epg_listings":[]}"""
                        it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                    } else {
                        it.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                }
            }
        }
    }

    @After fun stop() = socket.close()

    @Test
    fun httpsOnAPlainHttpPortFallsBackAndIsRemembered() = runBlocking {
        val port = socket.localPort
        val https = "https://127.0.0.1:$port/player_api.php?username=u&password=p&action=get_short_epg&stream_id=1"
        assertEquals("""{"epg_listings":[]}""", fetchText(https).text)
        // Remembered: the next request to that server goes to http straight away
        assertEquals("http://127.0.0.1:$port/player_api.php?x=1", PlainHttp.rewrite("https://127.0.0.1:$port/player_api.php?x=1"))
        assertEquals("https://example.com/a", PlainHttp.rewrite("https://example.com/a"))
    }
}
