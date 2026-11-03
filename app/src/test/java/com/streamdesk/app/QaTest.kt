package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** QA walk-through with the real channel list, opt-in: -Dqa=<folder holding hebrew.json>; pictures go to <folder>/out. */
private fun seedReal(context: Context, dir: File) {
    ChannelStore.resetForTests()
    val src = "http://test.invalid/player_api.php?username=u&password=p"
    val given = JSONArray(File(dir, "hebrew.json").readText())
    val channels = JSONArray()
    for (i in 0 until given.length()) {
        val o = given.getJSONObject(i)
        channels.put(Channel("http://test.invalid/live/u/p/${o.getInt("id")}.ts", o.getString("name"), o.getString("group"), source = src).toJson())
    }
    File(context.filesDir, "channels.json").writeText(channels.toString())
    File(context.filesDir, "playlists.json").writeText(JSONArray().put(PlaylistSource(src, "Account", channelCount = given.length(), updatedAt = System.currentTimeMillis(), updateHours = 0).toJson()).toString())
    File(context.filesDir, "recent.json").delete()
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class QaPhoneTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun fingerWalk() {
        val dir = System.getProperty("qa")?.let(::File)
        assumeTrue(dir != null && File(dir, "hebrew.json").exists())
        val out = File(dir, "out").also { it.mkdirs() }
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedReal(context, dir!!)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.mainClock.autoAdvance = false
            fun shot(name: String) {
                compose.mainClock.advanceTimeBy(1_000)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                sc.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                    v.draw(android.graphics.Canvas(b))
                    File(out, "ph-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            fun swipeUp() { compose.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.25f, durationMillis = 300) }; compose.mainClock.advanceTimeBy(1_000) }
            fun swipeLeft() { compose.onRoot().performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f, durationMillis = 300) }; compose.mainClock.advanceTimeBy(1_000) }
            fun swipeRight() { compose.onRoot().performTouchInput { swipeRight(startX = width * 0.1f, endX = width * 0.9f, durationMillis = 300) }; compose.mainClock.advanceTimeBy(1_000) }
            shot("00-start")
            compose.onNodeWithText("Israeli TV").performClick(); compose.mainClock.advanceTimeBy(500); shot("01-israeli")
            repeat(3) { swipeUp() }; shot("02-israeli-scrolled3")
            repeat(10) { swipeUp() }; shot("03-israeli-scrolled13")
            repeat(60) { swipeUp() }; shot("04-israeli-bottom")
            repeat(80) { compose.onRoot().performTouchInput { swipeDown(startY = height * 0.2f, endY = height * 0.85f, durationMillis = 200) }; compose.mainClock.advanceTimeBy(500) }; shot("05-israeli-top")
            swipeLeft(); shot("06-swipe-left")
            swipeRight(); swipeRight(); shot("07-swipe-right-twice")
            compose.onNodeWithContentDescription("More options").performClick(); shot("08-menu")
        }
    }
}
