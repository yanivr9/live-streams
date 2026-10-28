package com.streamdesk.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SportListTest {
    // The kinds of names a provider uses: one channel in several qualities, plus look-alike channels
    private val names = listOf(
        "IL: YES SPORT 1", "IL: YES SPORT 1 FHD", "IL: YES SPORT 1 HD", "IL: YES SPORT 10 HD",
        "IL: YES SPORT 2 FHD", "IL: YES SPORT 2 HD", "IL: YES SPORT 3 FHD", "IL: YES SPORT 4 HD", "IL: YES SPORT 6 FHD",
        "IL: Sport 5 4K HDR", "IL: Sport 5 4K", "IL: Sport 5 FHD P50", "IL: Sport 5 FHD", "IL: Sport 5 HD", "IL: Sport 5 SD",
        "IL: Sport 5+ Live 4K", "IL: Sport 5+ Live FHD", "IL: Sport 5+ Live HD", "IL: Sport 5+ Stars FHD P50", "IL: Sport 5+ Stars HD",
        "IL: Sport 5 Plus FHD", "IL: Sport 5 Plus", "IL: Sport 5 Gold", "IL: Sport 5+ UHD 4K", "IL: Sport 5+ Max FHD", "IL: Sport 5+ Plus 4K",
        "IL: ONE 1 4K", "IL: ONE 1 FHD P50", "IL: ONE 1 FHD", "IL: ONE 1 HD", "IL: ONE 2 HD", "IL: ONE 2 FHD",
        "IL: ONE DOCO FHD", "IL: HOT Cinema",
    )
    private val channels = names.mapIndexed { i, n -> Channel("http://x/$i", n, "Hebrew - Sport") }
    private fun urlOf(name: String) = channels.first { it.name == name }.url

    private fun pick(cap: QualityCap) = SportList.pick(channels, SportSettings(SportList.DEFAULT_TEXT, cap))

    @Test
    fun picksEightChannelsInOrderWithLabels() {
        assertEquals(
            listOf("Sport 1", "Sport 2", "Sport 3", "Sport 4", "Sport 5", "Sport 5+", "Sport 5 Live", "Sport 5 Stars", "Sport 5 Gold", "Sport 5+ Max", "Sport 6", "ONE 1", "ONE 2"),
            pick(QualityCap.Best).map { it.name },
        )
    }

    @Test
    fun bestQualityIsPickedAndLookAlikesAreLeftOut() {
        val picked = pick(QualityCap.Best).associateBy { it.name }
        assertEquals(urlOf("IL: YES SPORT 1 FHD"), picked.getValue("Sport 1").url)
        assertEquals(urlOf("IL: YES SPORT 2 FHD"), picked.getValue("Sport 2").url)
        assertEquals(urlOf("IL: Sport 5 4K HDR"), picked.getValue("Sport 5").url)   // not "Sport 5+", Gold, Plus…
        assertEquals("4K HDR", picked.getValue("Sport 5").group)
        assertEquals(urlOf("IL: ONE 1 4K"), picked.getValue("ONE 1").url)
        assertEquals(urlOf("IL: ONE 2 FHD"), picked.getValue("ONE 2").url)
    }

    @Test
    fun sportFivePlusLiveAndStars() {
        val picked = pick(QualityCap.Best).associateBy { it.name }
        assertEquals(urlOf("IL: Sport 5+ UHD 4K"), picked.getValue("Sport 5+").url)            // not "Sport 5+ Max", "Plus 4K" or "Gold"
        assertEquals(urlOf("IL: Sport 5+ Live 4K"), picked.getValue("Sport 5 Live").url)
        assertEquals(urlOf("IL: Sport 5+ Stars FHD P50"), picked.getValue("Sport 5 Stars").url)
        // "Sport 5 Plus" is the same channel under another name
        val named = SportList.pick(channels.filter { it.name.contains("5 Plus") }, SportSettings("Sport 5+ = Sport 5+ | Sport 5 Plus", QualityCap.Best))
        assertEquals(listOf(urlOf("IL: Sport 5 Plus FHD")), named.map { it.url })
    }

    @Test
    fun everyListedChannelHasItsLogo() {
        val picked = pick(QualityCap.Best)
        val expected = listOf("sport1", "sport2", "sport3", "sport4", "sport5", "sport5plus", "sport5live", "sport5stars", "sport6", "one1", "one2")
        // Sport 5 Gold and 5+ Max have no logo of their own in the app: they keep the provider's
        assertEquals(expected.map { "file:///android_asset/logos/$it.png" }, picked.filter { it.name !in setOf("Sport 5 Gold", "Sport 5+ Max") }.map { it.logo })
        // ...and the files are really in the app
        for (name in expected) assertEquals(true, java.io.File("src/main/assets/logos/$name.png").exists())
    }

    @Test
    fun qualityLimitIsRespected() {
        val fullHd = pick(QualityCap.FullHd).associateBy { it.name }
        assertEquals(urlOf("IL: Sport 5 FHD P50"), fullHd.getValue("Sport 5").url)
        assertEquals(urlOf("IL: ONE 1 FHD P50"), fullHd.getValue("ONE 1").url)
        val hd = pick(QualityCap.Hd).associateBy { it.name }
        assertEquals(urlOf("IL: YES SPORT 1 HD"), hd.getValue("Sport 1").url)
        assertEquals(urlOf("IL: ONE 1 HD"), hd.getValue("ONE 1").url)
        assertEquals(urlOf("IL: Sport 5 HD"), hd.getValue("Sport 5").url)
        assertEquals(urlOf("IL: YES SPORT 6 FHD"), hd.getValue("Sport 6").url)      // only FHD exists: lowest available
    }

    @Test
    fun skipsMissingChannelsAndEmptyListFallsBackToAllSport() {
        val s = SportSettings("Sport 1 = YES SPORT 1\nNo Such Channel\n# comment\n\nONE 2", QualityCap.Best)
        assertEquals(listOf("Sport 1", "ONE 2"), SportList.pick(channels, s).map { it.name })
        assertEquals(channels.size, SportList.pick(channels, SportSettings("  \n", QualityCap.Best)).size)   // all have "Sport" in the folder
    }

    @Test
    fun sport5TabShowsEveryVariantGroupedBestFirst() {
        fun ch(name: String) = Channel("http://h/live/u/p/${name.hashCode()}.ts", name, "Hebrew - Sport")
        val names = listOf(
            "IL: Sport 5 HD", "IL: Sport 5 4K HDR", "IL: Sport 5+ Live FHD", "IL: Sport 5 Gold", "IL: Sport 5+ Gold 4K",
            "IL: Sport 5+ Max FHD", "IL: Sport 5+ Plus 4K", "IL: Sport 5 Plus", "IL: Sport 5+ Stars HD", "IL: Sport 50", "IL: Sport 5 Extra",
        )
        val all = SportList.sport5All(names.map(::ch))
        assertEquals(
            listOf(
                "Sport 5 4K HDR", "Sport 5 HD", "Sport 5+ Plus 4K", "Sport 5 Plus", "Sport 5+ Live FHD", "Sport 5+ Stars HD",
                "Sport 5+ Gold 4K", "Sport 5 Gold", "Sport 5+ Max FHD",
            ),
            all.map { it.name },
        )
    }

    @Test
    fun sportTabNowHasGoldAndMax() {
        val list = SportList.pick(
            listOf(Channel("http://h/1", "IL: Sport 5+ Gold 4K"), Channel("http://h/2", "IL: Sport 5 Gold"), Channel("http://h/3", "IL: Sport 5+ Max FHD")),
            SportList.DEFAULT,
        )
        assertEquals(listOf("Sport 5 Gold", "Sport 5+ Max"), list.map { it.name })
        assertEquals("4K", list[0].group)
    }
}
