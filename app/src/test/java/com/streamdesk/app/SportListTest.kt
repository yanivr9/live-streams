package com.streamdesk.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SportListTest {
    // The kinds of names a provider uses: one channel in several qualities, plus look-alike channels
    private val names = listOf(
        "IL: YES SPORT 1", "IL: YES SPORT 1 FHD", "IL: YES SPORT 1 HD", "IL: YES SPORT 10 HD",
        "IL: YES SPORT 2 FHD", "IL: YES SPORT 2 HD", "IL: YES SPORT 3 FHD", "IL: YES SPORT 4 HD", "IL: YES SPORT 6 FHD",
        "IL: Sport 5 4K HDR", "IL: Sport 5 4K", "IL: Sport 5 FHD P50", "IL: Sport 5 FHD", "IL: Sport 5 HD", "IL: Sport 5 SD",
        "IL: Sport 5+ Live 4K", "IL: Sport 5 Plus FHD", "IL: Sport 5 Gold", "IL: Sport 5+ UHD 4K",
        "IL: ONE 1 4K", "IL: ONE 1 FHD P50", "IL: ONE 1 FHD", "IL: ONE 1 HD", "IL: ONE 2 HD", "IL: ONE 2 FHD",
        "IL: ONE DOCO FHD", "IL: HOT Cinema",
    )
    private val channels = names.mapIndexed { i, n -> Channel("http://x/$i", n, "Hebrew - Sport") }
    private fun urlOf(name: String) = channels.first { it.name == name }.url

    private fun pick(cap: QualityCap) = SportList.pick(channels, SportSettings(SportList.DEFAULT_TEXT, cap))

    @Test
    fun picksEightChannelsInOrderWithLabels() {
        assertEquals(
            listOf("Sport 1", "Sport 2", "Sport 3", "Sport 4", "Sport 5", "Sport 6", "ONE 1", "ONE 2"),
            pick(QualityCap.Best).map { it.name },
        )
    }

    @Test
    fun bestQualityIsPickedAndLookAlikesAreLeftOut() {
        val picked = pick(QualityCap.Best).associateBy { it.name }
        assertEquals(urlOf("IL: YES SPORT 1 FHD"), picked.getValue("Sport 1").url)
        assertEquals(urlOf("IL: YES SPORT 2 FHD"), picked.getValue("Sport 2").url)
        assertEquals(urlOf("IL: Sport 5 4K HDR"), picked.getValue("Sport 5").url)   // not "Sport 5+ UHD 4K", Gold, Plus…
        assertEquals("4K HDR", picked.getValue("Sport 5").group)
        assertEquals(urlOf("IL: ONE 1 4K"), picked.getValue("ONE 1").url)
        assertEquals(urlOf("IL: ONE 2 FHD"), picked.getValue("ONE 2").url)
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
}
