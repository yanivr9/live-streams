package com.streamdesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import java.util.TimeZone

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
        val israel = listOf(ch(11), ch(12))
        assertEquals(sport to 1, Resume.queue(ch(2), listOf(sport, israel)))
        assertEquals(israel to 1, Resume.queue(ch(12), listOf(sport, israel)))
        assertEquals(listOf(ch(9)) to 0, Resume.queue(ch(9), listOf(sport, israel)))
    }

    @Test
    fun splitsTheGuideIntoDays() {
        val tz = TimeZone.getTimeZone("Asia/Jerusalem")   // UTC+3 in this date range
        val now = 1_761_400_800L                         // 2025-10-25 17:00 local
        val list = listOf(
            Program("Earlier", now - 7200, now - 3600),    // over: left out
            Program("On now", now - 600, now + 600),
            Program("Late", now + 6 * 3600, now + 7 * 3600),     // 23:00 today
            Program("Night", now + 8 * 3600, now + 9 * 3600),    // 01:00 tomorrow
            Program("Later", now + 50 * 3600, now + 51 * 3600),
        )
        val days = Epg.days(list, now, tz)
        assertEquals(listOf("Today", "Tomorrow"), days.take(2).map { it.label })
        assertEquals(listOf("On now", "Late"), days[0].programs.map { it.title })
        assertEquals(listOf("Night"), days[1].programs.map { it.title })
        assertEquals(3, days.size)
        assertEquals(true, list[1].isOn(now))
        assertEquals(false, list[2].isOn(now))
    }

    @Test
    fun israeliTabPicksTheBestQualityOfChannels11To15() {
        fun ch(name: String) = Channel("http://h/live/u/p/${name.hashCode()}.ts", name, "Hebrew")
        val provider = listOf(
            ch("IL: Kan 11 4K"), ch("IL: Kan11 FHD"), ch("IL: Kan11 HD"), ch("IL: Makan FHD"), ch("IL: Kan33"),
            ch("IL: Channel 12 4K"), ch("IL: Channel 12 HD"), ch("IL: Channel 13 FHD"), ch("IL: Channel 13 SD"),
            ch("IL: Channel 14 HD"), ch("IL: Channel 14 HD"), ch("IL: Channel 120"), ch("IL: HOT Cinema"),
        )
        val picked = IsraelList.pick(provider)
        assertEquals(listOf("Channel 11 · Kan", "Channel 12 · Keshet", "Channel 13 · Reshet", "Channel 14"), picked.map { it.name })   // no 15 at this provider
        assertEquals(listOf("4K", "4K", "Full HD", "HD"), picked.map { it.group })
        assertEquals(true, picked.all { it.logo.startsWith("file:///android_asset/logos/") })
    }
}
