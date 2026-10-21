package com.streamdesk.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Signs in to a simulated Xtream Codes server. */
class XtreamTest {
    private val server = MockWebServer()
    private lateinit var base: String
    private var status = "Active"
    private var moviesBroken = false
    private var dropFirstConnection = false   // makes the first connection fail like a TLS handshake with a plain-http server

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun peek(): MockResponse =
                if (dropFirstConnection) {
                    dropFirstConnection = false
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                } else {
                    super.peek()
                }

            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)   // bookkeeping request for a dropped connection
                if (url.encodedPath != "/player_api.php") return MockResponse().setResponseCode(404)
                if (url.queryParameter("username") != "amy" || url.queryParameter("password") != "s3cret") {
                    return json("""{"user_info":{"auth":0}}""")
                }
                return when (url.queryParameter("action")) {
                    null -> json(
                        """{"user_info":{"auth":1,"status":"$status","exp_date":"1893456000","max_connections":"2",
                           "allowed_output_formats":["m3u8","ts"]},"server_info":{"url":"x"}}"""
                    )
                    "get_live_categories" -> json("""[{"category_id":"1","category_name":"News"}]""")
                    "get_live_streams" -> json("""[{"stream_id":101,"name":"News 24","category_id":"1","stream_icon":"http://img/n.png"},
                                                  {"stream_id":102,"name":"Sport 1","category_id":"9"}]""")
                    "get_vod_categories" -> json("""[{"category_id":"5","category_name":"Action"}]""")
                    "get_vod_streams" -> if (moviesBroken) MockResponse().setResponseCode(503) else json("""[{"stream_id":"201","name":"Big Movie","category_id":"5","container_extension":"mkv"}]""")
                    "get_series_categories" -> json("""{}""")   // some servers send {} for an empty list
                    "get_series" -> json("""[{"series_id":301,"name":"Show","cover":"http://img/s.png","category_id":"2"}]""")
                    "get_series_info" -> when (url.queryParameter("series_id")) {
                        "301" -> json(
                            """{"info":{},"episodes":{"2":[{"id":"9002","episode_num":1,"title":"Back","container_extension":"mp4","season":2}],
                                                     "1":[{"id":"9001","episode_num":2,"title":"Second","container_extension":"mkv"},
                                                          {"id":"9000","episode_num":1,"title":"Pilot","container_extension":"mp4"}]}}"""
                        )
                        else -> json("""{"episodes":[[{"id":"7","episode_num":1,"title":"Only","season":1}]]}""")
                    }
                    else -> MockResponse().setResponseCode(400)
                }
            }
        }
        server.start()
        base = server.url("/").toString()
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun signsInAndLoadsEverything() = runBlocking {
        val result = Xtream.load(XtreamLogin(base, "amy", "s3cret"))
        val byName = result.channels.associateBy { it.name }

        val news = byName.getValue("News 24")
        assertEquals("${base}live/amy/s3cret/101.ts", news.url)
        assertEquals("News", news.group)
        assertEquals("http://img/n.png", news.logo)
        assertEquals(Kind.LIVE, news.kind)
        assertEquals("", byName.getValue("Sport 1").group)   // unknown category

        val movie = byName.getValue("Big Movie")
        assertEquals("${base}movie/amy/s3cret/201.mkv", movie.url)
        assertEquals("Action", movie.group)
        assertEquals(Kind.MOVIE, movie.kind)

        val show = byName.getValue("Show")
        assertEquals(Kind.SERIES, show.kind)
        assertTrue(show.url.contains("action=get_series_info&series_id=301"))

        assertTrue(result.account, result.account.startsWith("2 channels · 1 movies · 1 series · expires "))
        assertTrue(result.account, result.account.endsWith(" · 2 screens"))
    }

    @Test
    fun listsEpisodesInOrder() = runBlocking {
        val show = Xtream.load(XtreamLogin(base, "amy", "s3cret")).channels.first { it.kind == Kind.SERIES }
        val eps = Xtream.episodes(show)
        assertEquals(listOf("S1 E1 · Pilot", "S1 E2 · Second", "S2 E1 · Back"), eps.map { it.name })
        assertEquals("${base}series/amy/s3cret/9001.mkv", eps[1].url)
        assertEquals(Kind.EPISODE, eps[0].kind)

        // The other episode-list format: an array of seasons
        val other = show.copy(url = show.url.replace("series_id=301", "series_id=5"))
        assertEquals(listOf("S1 E1 · Only"), Xtream.episodes(other).map { it.name })
    }

    @Test
    fun onlyLoadsWhatWasChosen() = runBlocking {
        val result = Xtream.load(XtreamLogin(base, "amy", "s3cret", live = true, movies = false, series = false))
        assertEquals(setOf(Kind.LIVE), result.channels.map { it.kind }.toSet())
        assertFalse(result.account.contains("movies"))
    }

    @Test
    fun wrongPassword() = runBlocking {
        try {
            Xtream.load(XtreamLogin(base, "amy", "nope"))
            fail("expected an error")
        } catch (e: Exception) {
            assertEquals("the username or password is wrong", e.message)
        }
    }

    @Test
    fun expiredAccount() = runBlocking {
        status = "Expired"
        try {
            Xtream.load(XtreamLogin(base, "amy", "s3cret"))
            fail("expected an error")
        } catch (e: Exception) {
            assertTrue(e.message!!, e.message!!.startsWith("the account is expired (it ran until "))
        }
    }

    @Test
    fun notAnIptvServer() = runBlocking {
        try {
            Xtream.load(XtreamLogin(base + "nothing/", "amy", "s3cret"))
            fail("expected an error")
        } catch (e: Exception) {
            assertTrue(e.message!!, e.message!!.contains("HTTP 404") || e.message!!.contains("doesn't answer like an IPTV"))
        }
    }

    @Test
    fun readsLoginFromLinks() {
        val login = XtreamLogin.fromLink("http://cdn.example.club:8080/get.php?username=u1&password=p%402&type=m3u&output=ts")!!
        assertEquals("http://cdn.example.club:8080/", login.base)
        assertEquals("u1", login.username)
        assertEquals("p@2", login.password)
        assertTrue(XtreamLogin.isLoginLink("http://h:8080/get.php?username=a&password=b"))
        assertFalse(XtreamLogin.isLoginLink("http://h:8080/live/index.m3u8"))
        assertNull(XtreamLogin.fromLink("http://h/get.php?username=a"))
    }

    @Test
    fun loginSurvivesSaving() {
        val src = PlaylistSource("k", "Mine", xtream = XtreamLogin("http://h:1/", "a", "b", movies = false, categories = "Hebrew, Sport"), account = "3 channels")
        assertEquals(src, PlaylistSource.fromJson(JSONObject(src.toJson().toString())))
    }

    @Test
    fun reportsProgressAndDeliversEachPart() = runBlocking {
        val steps = mutableListOf<String>()
        val parts = mutableListOf<Pair<String, Int>>()
        Xtream.load(XtreamLogin(base, "amy", "s3cret"), onProgress = { steps += it }, onPart = { kind, items -> parts += kind to items.size })
        assertEquals(listOf("Signing in…", "Loading channels…", "Loaded 2 items. Loading movies…", "Loaded 3 items. Loading series…"), steps)
        assertEquals(listOf(Kind.LIVE to 2, Kind.MOVIE to 1, Kind.SERIES to 1), parts)
    }

    @Test
    fun brokenMoviesKeepTheRest() = runBlocking {
        moviesBroken = true
        val result = Xtream.load(XtreamLogin(base, "amy", "s3cret"))
        assertEquals(setOf(Kind.LIVE, Kind.SERIES), result.channels.map { it.kind }.toSet())
        assertEquals(setOf(Kind.MOVIE), result.failedKinds)
        assertTrue(result.problems, result.problems.startsWith("movies didn't load: the server has a problem (HTTP 503)"))
        assertFalse(result.account.contains("movies"))
    }

    @Test
    fun everythingBrokenIsAnError() = runBlocking {
        moviesBroken = true
        try {
            Xtream.load(XtreamLogin(base, "amy", "s3cret", live = false, series = false))
            fail("expected an error")
        } catch (e: Exception) {
            assertTrue(e.message!!, e.message!!.startsWith("the server has a problem (HTTP 503)"))
        }
    }

    @Test
    fun httpsTypedForPlainHttpServerFallsBackToHttp() = runBlocking {
        // The mock server speaks plain http; an https:// address to it fails the TLS handshake
        dropFirstConnection = true
        val typed = base.replaceFirst("http://", "https://")
        val result = Xtream.load(XtreamLogin(typed, "amy", "s3cret"))
        assertTrue(result.channels.isNotEmpty())
        assertTrue(result.channels.first().url, result.channels.first().url.startsWith("http://"))
    }

    @Test
    fun onlyLoadsFoldersMatchingTheFilter() = runBlocking {
        // Folder "News" holds News 24; Sport 1 sits in a folder the server doesn't list
        val result = Xtream.load(XtreamLogin(base, "amy", "s3cret", movies = false, series = false, categories = "nEw"))
        assertEquals(listOf("News 24"), result.channels.map { it.name })
        val either = Xtream.load(XtreamLogin(base, "amy", "s3cret", movies = false, series = false, categories = "xyz, news"))
        assertEquals(listOf("News 24"), either.channels.map { it.name })
    }

    @Test
    fun filterThatMatchesNothingExplainsWhy() = runBlocking {
        try {
            Xtream.load(XtreamLogin(base, "amy", "s3cret", movies = false, series = false, categories = "Hebrew"))
            fail("expected an error")
        } catch (e: Exception) {
            assertTrue(e.message!!, e.message!!.contains("no channels folders match \"Hebrew\"") && e.message!!.contains("News"))
        }
    }
}
