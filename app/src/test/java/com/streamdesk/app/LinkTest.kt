package com.streamdesk.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** A TV (server) and a phone (client) talking to each other over the local machine. */
class LinkTest {
    private class Recorder : LinkHandler {
        var played: Pair<List<Channel>, Int>? = null
        val commands = mutableListOf<String>()
        var login: LoginRequest? = null
        override fun play(queue: List<Channel>, index: Int) { played = queue to index }
        override fun command(action: String) { commands += action }
        override fun login(request: LoginRequest) { login = request }
        var nowPlaying: Pair<List<Channel>, Int>? = null
        override fun takeOver() = nowPlaying?.also { commands += "stop" }
    }

    private val recorder = Recorder()
    private var clock = 1_000_000L
    private lateinit var server: LinkServer
    private lateinit var tv: TvDevice

    @Before
    fun start() {
        server = LinkServer("Mi Box", "9.9", { "123456" }, recorder, now = { clock })
        tv = TvDevice("Mi Box", "127.0.0.1", server.start(0))
    }

    @After
    fun stop() = server.stop()

    private fun failsWith(kind: LinkException.Kind, block: () -> Unit) {
        try { block(); fail("expected a LinkException") } catch (e: LinkException) { assertEquals(kind, e.kind) }
    }

    @Test
    fun phoneTakesOverWhatTheTvPlays() {
        failsWith(LinkException.Kind.Other) { LinkClient.takeOver(tv, "123456") }   // nothing playing
        val list = listOf(Channel("http://x/1", "One"), Channel("http://x/2", "Two"))
        recorder.nowPlaying = list to 1
        assertEquals(list to 1, LinkClient.takeOver(tv, "123456"))
        assertEquals(listOf("stop"), recorder.commands)
        failsWith(LinkException.Kind.WrongCode) { LinkClient.takeOver(tv, "000000") }
    }

    @Test
    fun findsTheTvByAddress() {
        assertEquals("Mi Box", LinkClient.ping("127.0.0.1", tv.port).name)
    }

    @Test
    fun sendsAChannelListWithTheRightOneSelected() {
        val queue = listOf(
            Channel("http://h/live/u/p/1.ts", "IL: Sport 1", "Full HD", logo = "file:///android_asset/logos/sport1.png", userAgent = "VLC/3"),
            Channel("http://h/live/u/p/2.ts", "Sport 2", "HD"),
            Channel("http://h/live/u/p/3.ts", "ONE 1", "4K"),
        )
        LinkClient.play(tv, "123456", queue, 2)
        assertEquals(queue, recorder.played!!.first)     // every field survives, including the stream's client name
        assertEquals(2, recorder.played!!.second)
    }

    @Test
    fun remoteCommandsArrive() {
        LinkClient.command(tv, "123456", "next")
        LinkClient.command(tv, "123456", "stop")
        assertEquals(listOf("next", "stop"), recorder.commands)
    }

    @Test
    fun loginAndSportListArrive() {
        val sent = LoginRequest(
            XtreamLogin("http://h:8080/", "u", "p", live = true, movies = false, series = false, categories = "Hebrew"),
            "Yaniv", true, 24, SportSettings("Sport 1 = YES SPORT 1\nONE 1", QualityCap.FullHd),
        )
        LinkClient.sendLogin(tv, "123456", sent)
        assertEquals(sent, recorder.login)
    }

    @Test
    fun wrongCodeIsRefusedAndNothingHappens() {
        failsWith(LinkException.Kind.WrongCode) { LinkClient.command(tv, "000000", "stop") }
        failsWith(LinkException.Kind.WrongCode) { LinkClient.play(tv, "", listOf(Channel("http://x/1", "A")), 0) }
        assertTrue(recorder.commands.isEmpty() && recorder.played == null)
    }

    @Test
    fun guessingTheCodeLocksOutForAMinute() {
        repeat(5) { failsWith(LinkException.Kind.WrongCode) { LinkClient.command(tv, "00000$it", "stop") } }
        failsWith(LinkException.Kind.TooManyTries) { LinkClient.command(tv, "123456", "stop") }   // even the right code is refused now
        clock += 61_000
        LinkClient.command(tv, "123456", "stop")
        assertEquals(listOf("stop"), recorder.commands)
    }

    @Test
    fun anUnreachableTvIsReportedClearly() {
        server.stop()
        failsWith(LinkException.Kind.Unreachable) { LinkClient.ping("127.0.0.1", tv.port) }
    }
}
