package com.streamdesk.app

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class QrLinkTest {
    /** Draws the QR the way a screen would (with its quiet zone), then reads it back like a phone camera. */
    private fun scan(text: String): String {
        val m = Qr.matrix(text)
        val scale = 8
        val quiet = 4
        val size = (m.width + 2 * quiet) * scale
        val pixels = IntArray(size * size) { 0xFFFFFFFF.toInt() }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale) pixels[((y + quiet) * scale + dy) * size + (x + quiet) * scale + dx] = 0xFF000000.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun theTvsQrCodeReadsBackExactly() {
        val url = "http://192.168.1.50:8765/connect?c=482913"
        assertEquals(url, scan(url))
    }

    @Test
    fun validInvitationIsReadFromTheLink() {
        val invite = LinkInvite.parse("streamdesk://connect?host=192.168.1.50&port=8765&code=482913&name=Stream+Desk+%28Mi+Box%29")!!
        assertEquals(LinkInvite("192.168.1.50", 8765, "482913", "Stream Desk (Mi Box)"), invite)
    }

    @Test
    fun linksPointingAnywhereButTheHomeNetworkAreRefused() {
        fun link(host: String, code: String = "482913", port: String = "8765") = "streamdesk://connect?host=$host&port=$port&code=$code&name=TV"
        assertNull(LinkInvite.parse(link("8.8.8.8")))                 // a server on the internet
        assertNull(LinkInvite.parse(link("evil.example.com")))        // a name, not a home address
        assertNull(LinkInvite.parse(link("192.168.1.999")))
        assertNull(LinkInvite.parse(link("172.32.0.1")))              // just outside the private range
        assertNull(LinkInvite.parse(link("192.168.1.50", code = "12345")))
        assertNull(LinkInvite.parse(link("192.168.1.50", code = "12345a")))
        assertNull(LinkInvite.parse(link("192.168.1.50", port = "70000")))
        assertNull(LinkInvite.parse("https://connect?host=192.168.1.50&code=482913"))
        assertNotNull(LinkInvite.parse(link("10.0.0.7")))
        assertNotNull(LinkInvite.parse(link("172.16.5.5")))
    }

    // ---- the page the QR code opens on the phone
    private val server = LinkServer("Stream Desk (Mi Box)", "9", { "482913" }, object : LinkHandler {
        override fun play(queue: List<Channel>, index: Int) {}
        override fun command(action: String) {}
        override fun login(request: LoginRequest) {}
    })

    @After
    fun stop() = server.stop()

    private fun get(port: Int, query: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port/connect$query").openConnection() as HttpURLConnection
        return try {
            val code = c.responseCode
            code to (if (code == 200) c.inputStream.bufferedReader().readText() else "")
        } finally { c.disconnect() }
    }

    @Test
    fun theConnectPageOpensStreamDeskWithTheTvsDetails() {
        val port = server.start(0)
        val (status, html) = get(port, "?c=482913")
        assertEquals(200, status)
        assertTrue(html, html.contains("intent://connect?host=127.0.0.1&port=$port&code=482913&name=Stream+Desk+%28Mi+Box%29#Intent;scheme=streamdesk;package=com.streamdesk.app;end"))
        assertTrue(html.contains("482 913"))
        // the link on that page is a valid invitation for the phone's app
        val invite = LinkInvite.parse("streamdesk://connect?host=127.0.0.1&port=$port&code=482913&name=Stream+Desk+%28Mi+Box%29")
        assertEquals("Stream Desk (Mi Box)", invite!!.name)
    }

    @Test
    fun theConnectPageNeedsTheRightCode() {
        val port = server.start(0)
        assertEquals(403, get(port, "?c=000000").first)
        assertEquals(403, get(port, "").first)
    }
}
