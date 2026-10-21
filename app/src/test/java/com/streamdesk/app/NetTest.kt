package com.streamdesk.app

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Runs the download code against a local server that behaves like picky IPTV servers. */
class NetTest {
    private val server = MockWebServer()
    private lateinit var base: String

    private val playlist = "#EXTM3U\n#EXTINF:-1 group-title=\"News\",News One\nhttp://cdn.example/live/1.ts\n"

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ua = request.getHeader("User-Agent").orEmpty()
                return when (request.path) {
                    // Refuses VLC with 451, serves everyone else; the link has no playlist-looking name
                    "/pack" -> if (ua.startsWith("VLC")) MockResponse().setResponseCode(451) else MockResponse().setBody(playlist)
                    "/blocked" -> MockResponse().setResponseCode(451)
                    "/missing" -> MockResponse().setResponseCode(404)
                    "/stream" -> MockResponse().setBody(Buffer().write(ByteArray(20_000) { 0x47 }))   // MPEG-TS sync bytes
                    "/hls" -> MockResponse().setBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8\n")
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun findsPlaylistBehindAnyLinkAndWorksAroundRefusal() = runBlocking {
        val got = sniffChannelList("$base/pack")!!
        assertTrue(got.text.contains("News One"))
        assertEquals(CLIENT_USER_AGENTS[1], got.userAgent)
    }

    @Test
    fun streamsAreNotMistakenForPlaylists() = runBlocking {
        assertNull(sniffChannelList("$base/stream"))
        assertNull(sniffChannelList("$base/hls"))
    }

    @Test
    fun reportsRefusalAfterTryingEveryClient() = runBlocking {
        try {
            fetchText("$base/blocked")
            fail("expected an error")
        } catch (e: HttpStatusException) {
            assertEquals(451, e.code)
            assertTrue(e.message!!.contains("legal reasons"))
        }
    }

    @Test
    fun doesNotRetryMissingLinks() = runBlocking {
        try {
            fetchText("$base/missing")
            fail("expected an error")
        } catch (e: HttpStatusException) {
            assertEquals(404, e.code)
        }
    }
}
