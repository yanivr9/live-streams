package com.streamdesk.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTest {
    @Test
    fun hidesAccountDetails() {
        assertEquals(
            "GET http://h:8080/player_api.php?username=***&password=***&action=get_series",
            AppLog.redact("GET http://h:8080/player_api.php?username=amy&password=s3cr%40t&action=get_series"),
        )
        assertEquals("http://h:8080/live/***/***/101.ts", AppLog.redact("http://h:8080/live/amy/s3cret/101.ts"))
        assertEquals("http://h/movie/***/***/7.mkv", AppLog.redact("http://h/movie/amy/s3cret/7.mkv"))
    }

    @Test
    fun signInIsLoggedWithoutPassword() = runBlocking {
        AppLog.clear()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active","max_connections":"1"}}"""))
        repeat(2) { server.enqueue(MockResponse().setBody("[]")) }
        server.start()
        runCatching { Xtream.load(XtreamLogin(server.url("/").toString(), "amy", "TopSecret99", movies = false, series = false)) }
        server.shutdown()
        val log = AppLog.text()
        assertTrue(log, log.contains("[net] GET") && log.contains("→ HTTP 200"))
        assertTrue(log, log.contains("Account OK: status Active"))
        assertFalse(log, log.contains("TopSecret99"))
        assertFalse(log, log.contains("amy"))
    }
}
