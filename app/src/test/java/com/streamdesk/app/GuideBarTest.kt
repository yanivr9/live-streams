package com.streamdesk.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The TV guide bar: what it says for a channel with a guide, and for one without. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class GuideBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val ch = Channel("http://h/live/u/p/1.ts", "Sport 1", "Hebrew", logo = "file:///android_asset/logos/sport1.png")

    @Test
    fun showsNowNextAndProgress() {
        val air = NowNext.of(listOf(Program("ישיר! ה.י-ם - אריס סלוניקי", 0, 100), Program("סיכום המחזור בליגה האנגלית", 100, 200)), 62) { "t$it" }!!
        assertEquals("ישיר! ה.י-ם - אריס סלוניקי", air.title); assertEquals("t100", air.until); assertEquals("t100", air.nextTime)
        compose.setContent {
            StreamDeskTheme(isTv = true) {
                Box(Modifier.fillMaxSize().background(Color(0xFF3A4A66)), contentAlignment = Alignment.BottomCenter) { androidx.compose.foundation.layout.Box(Modifier.requiredWidth(960.dp)) { GuideBar(GuideBannerInfo(ch, 1, 13, air)) } }
            }
        }
        compose.onNodeWithText("Sport 1").assertExists()
        compose.onNodeWithText("until t100  ·  1/13").assertExists()
        compose.onNodeWithText("NEXT · t100").assertExists()
        System.getProperty("qa")?.let { dir ->
            compose.activity.let { a ->
                val v = a.window.decorView
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(b))
                File(dir, "out").also { it.mkdirs() }.let { File(it, "guide-bar.png").outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 100, o) } }
            }
        }
    }

    @Test
    fun saysSoWhenThereIsNoGuide() {
        compose.setContent { StreamDeskTheme(isTv = true) { GuideBar(GuideBannerInfo(ch, 3, 13, null)) } }
        compose.onNodeWithText("No guide for this channel").assertExists()
    }
}
