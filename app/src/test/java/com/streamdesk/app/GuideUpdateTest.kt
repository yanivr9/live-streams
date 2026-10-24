package com.streamdesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class GuideUpdateTest {
    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private val guide = """{"epg_listings":[
        {"title":"${b64("Maccabi - Hapoel")}","start_timestamp":"1000","stop_timestamp":"2000"},
        {"title":"${b64("Studio")}","start_timestamp":"2000","stop_timestamp":"3000"},
        {"title":"Plain title","start_timestamp":"3000","stop_timestamp":"4000"}]}"""

    @Test
    fun readsStreamNumbers() {
        assertEquals("123", Epg.streamId("http://h:8080/live/u/p/123.ts"))
        assertEquals("45", Epg.streamId("http://h/live/u/p/45.m3u8?x=1"))
        assertNull(Epg.streamId("http://h/movie/u/p/9.mkv"))
    }

    @Test
    fun readsTitlesAndFindsNowAndNext() {
        val list = Epg.parse(guide)
        assertEquals(listOf("Maccabi - Hapoel", "Studio", "Plain title"), list.map { it.title })
        assertEquals("Maccabi - Hapoel · until t2000", Epg.nowLabel(list, 1500) { "t$it" })
        assertEquals("t2000 Studio", Epg.nextLabel(list, 1500) { "t$it" })
        assertNull(Epg.nowLabel(list, 9000) { "t$it" })      // nothing on right now
        assertNull(Epg.nowLabel(Epg.parse("not json"), 1500))
    }

    @Test
    fun readsTheUpdateFile() {
        val info = Updates.parse("""{"versionCode":28,"versionName":"3.7","apk":"StreamDesk.apk","notes":"Faster"}""")!!
        assertEquals(28, info.code)
        assertEquals("https://raw.githubusercontent.com/yanivr9/live-streams/main/dist/StreamDesk.apk", info.apkUrl)
        assertEquals(true, Updates.isNewer(info, 27))
        assertEquals(false, Updates.isNewer(info, 28))
        assertNull(Updates.parse("""{"versionCode":28,"apk":"http://insecure/x.apk"}"""))   // only https
        assertNull(Updates.parse("""{"versionCode":0,"apk":"x.apk"}"""))
        assertNull(Updates.parse("<html>404</html>"))
    }

    @Test
    fun resumeStaysInsideTheSportList() {
        fun ch(n: Int) = Channel("http://x/$n", "c$n", "")
        val sport = listOf(ch(1), ch(2), ch(3))
        assertEquals(sport to 1, Resume.queue(ch(2), sport))
        assertEquals(listOf(ch(9)) to 0, Resume.queue(ch(9), sport))
    }
}
