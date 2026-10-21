package com.streamdesk.app

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uTest {
    private val playlist = """
        #EXTM3U x-tvg-url="https://example.com/epg.xml"
        #EXTINF:-1 tvg-id="news.1" tvg-logo="https://example.com/news.png" group-title="News, World",Channel One, HD
        #EXTVLCOPT:http-user-agent=Mozilla/5.0
        #EXTVLCOPT:http-referrer=https://example.com/
        https://example.com/live/one.m3u8
        #EXTINF:-1 http-user-agent="TV/2.0" http-referrer="https://ref.example/",Sports
        #EXTGRP:Sports
        http://example.com:8080/sports/index.mpd

        rtsp://192.168.1.20:554/stream1
    """.trimIndent()

    @Test
    fun parsesAttributesTitlesAndOptions() {
        val list = M3u.parse(playlist)
        assertEquals(3, list.size)

        val one = list[0]
        assertEquals("Channel One, HD", one.name)
        assertEquals("News, World", one.group)
        assertEquals("https://example.com/news.png", one.logo)
        assertEquals("Mozilla/5.0", one.userAgent)
        assertEquals("https://example.com/", one.referrer)
        assertEquals("https://example.com/live/one.m3u8", one.url)

        assertEquals("Sports", list[1].name)
        assertEquals("Sports", list[1].group)
        assertEquals("TV/2.0", list[1].userAgent)
        assertEquals("https://ref.example/", list[1].referrer)
        assertEquals("", list[2].userAgent)

        // No #EXTINF: the name comes from the link
        assertEquals("192.168.1.20 · stream1", list[2].name)
        assertEquals("", list[2].group)
    }

    @Test
    fun plainLinkListWorks() {
        val list = M3u.parse("https://a.com/x.m3u8\r\n﻿https://b.com/\nnot a link\n")
        assertEquals(listOf("https://a.com/x.m3u8", "https://b.com/"), list.map { it.url })
        assertEquals("b.com", list[1].name)
    }

    @Test
    fun exportRoundTrips() {
        val list = M3u.parse(playlist)
        assertEquals(list, M3u.parse(M3u.export(list)))
    }

    @Test
    fun detectsFormatFromLink() {
        assertEquals(StreamFormat.Hls, detectFormat("https://x.com/live/index.M3U8?token=1"))
        assertEquals(StreamFormat.Hls, detectFormat("https://x.com/play?format=m3u8"))
        assertEquals(StreamFormat.Dash, detectFormat("https://x.com/manifest.mpd"))
        assertEquals(StreamFormat.Auto, detectFormat("https://x.com/video.mp4"))
        assertEquals(StreamFormat.Auto, detectFormat("https://x.com/live/12345"))
    }

    @Test
    fun jsonRoundTrips() {
        val ch = M3u.parse(playlist)[0].copy(favorite = true)
        assertEquals(ch, Channel.fromJson(ch.toJson()))
    }

    @Test
    fun tellsChannelListsFromHlsStreams() {
        assertEquals(true, isChannelList(playlist))
        val hlsMaster = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n"
        val hlsMedia = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg1.ts\n"
        assertEquals(false, isChannelList(hlsMaster))
        assertEquals(false, isChannelList(hlsMedia))
    }

    @Test
    fun spotsPossiblePlaylistLinks() {
        assertEquals(true, mightBePlaylistLink("https://x.com/lists/tv.m3u"))
        assertEquals(true, mightBePlaylistLink("https://x.com/tv.m3u8?token=a"))
        assertEquals(true, mightBePlaylistLink("http://panel.tv:8080/get.php?username=a&password=b&type=m3u_plus"))
        assertEquals(false, mightBePlaylistLink("https://x.com/video.mp4"))
        assertEquals(false, mightBePlaylistLink("https://x.com/manifest.mpd"))
    }

    @Test
    fun namesPlaylistsFromLinks() {
        assertEquals("x.com · tv.m3u", playlistName("https://x.com/lists/tv.m3u"))
        assertEquals("panel.tv", playlistName("http://panel.tv:8080/get.php?username=a"))
    }

    @Test
    fun showsNamesWithoutCountryPrefixAndFindsSport() {
        fun ch(name: String, group: String = "") = Channel("http://x/1", name, group)
        assertEquals("YES SPORT 1 FHD", ch("IL: YES SPORT 1 FHD").displayName())
        assertEquals("ONE 1 4K", ch("IL: ONE 1 4K").displayName())
        assertEquals("Sport 5 [Multi-Sub]", ch("IL|  Sport 5 [Multi-Sub]").displayName())
        assertEquals("CNN: Live", ch("CNN: Live").displayName())     // not a 2-letter country code
        assertEquals("ESPN", ch("US | ESPN").displayName())
        assertEquals("IL:", ch("IL:").displayName())                // never empty
        assertEquals("Mixed Case", ch("Mixed Case").displayName())
        assertEquals(true, ch("IL: ONE 1 4K", "Hebrew - Sport").isSport())
        assertEquals(true, ch("IL: YES SPORT 1").isSport())
        assertEquals(true, ch("ערוץ ספורט 5").isSport())
        assertEquals(false, ch("IL: HOT Cinema", "Hebrew - HOT").isSport())
    }
}
