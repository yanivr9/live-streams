package com.streamdesk.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

/** A large account, as some IPTV providers have: checks sign-in finishes within a phone-sized memory limit. */
class BigAccountTest {
    private fun items(n: Int, idKey: String, extra: String) = buildString {
        append('[')
        for (i in 0 until n) {
            if (i > 0) append(',')
            append("""{"num":$i,"name":"Item number $i with a fairly long descriptive title","$idKey":$i,"category_id":"${i % 200}",""")
            append(""""stream_icon":"http://images.example-provider.com/posters/$i.jpg","added":"1700000000","rating":"7.5",$extra}""")
        }
        append(']')
    }

    @Test
    fun largeAccountLoads() = runBlocking {
        val live = items(15_000, "stream_id", """"epg_channel_id":"ch$15.example","tv_archive":0""")
        val vod = items(80_000, "stream_id", """"container_extension":"mkv","plot":"A long plot description that many providers include in the list response for each movie entry."""")
        val series = items(10_000, "series_id", """"cover":"http://img.example/c.jpg","plot":"Series plot text."""")
        val cats = (0 until 200).joinToString(",", "[", "]") { """{"category_id":"$it","category_name":"Category $it"}""" }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(
                when (request.requestUrl!!.queryParameter("action")) {
                    null -> """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","max_connections":"1"}}"""
                    "get_live_streams" -> live
                    "get_vod_streams" -> vod
                    "get_series" -> series
                    else -> cats
                }
            )
        }
        server.start()
        val rt = Runtime.getRuntime()
        println("BIGACCOUNT maxHeapMB=${rt.maxMemory() / 1048576} jsonMB=${(live.length + vod.length + series.length) / 1048576}")
        val t0 = System.currentTimeMillis()
        try {
            val r = Xtream.load(XtreamLogin(server.url("/").toString(), "u", "p"))
            println("BIGACCOUNT ok items=${r.channels.size} ms=${System.currentTimeMillis() - t0} account=${r.account}")
        } catch (e: Throwable) {
            println("BIGACCOUNT FAILED ${e.javaClass.name}: ${e.message} after ms=${System.currentTimeMillis() - t0}")
            throw e
        } finally {
            server.shutdown()
        }
    }
}
