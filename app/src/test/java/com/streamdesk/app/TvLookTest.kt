package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the TV screens with a real channel list (only when one is given: -Dtvshots=<folder>). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvLookTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun pictures() {
        val dir = System.getProperty("tvshots")?.let(::File)
        assumeTrue(dir != null && File(dir, "hebrew.json").exists())
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
        ChannelStore.resetForTests()
        val src = "http://test.invalid/player_api.php?username=u&password=p"
        val given = JSONArray(File(dir!!, "hebrew.json").readText())
        val channels = JSONArray()
        for (i in 0 until given.length()) {
            val o = given.getJSONObject(i)
            channels.put(Channel("http://test.invalid/live/u/p/${o.getInt("id")}.ts", o.getString("name"), o.getString("group"), source = src).toJson())
        }
        File(context.filesDir, "channels.json").writeText(channels.toString())
        File(context.filesDir, "playlists.json").writeText(JSONArray().put(PlaylistSource(src, "Account", channelCount = given.length(), updatedAt = System.currentTimeMillis(), updateHours = 0).toJson()).toString())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The app's clocks never stop (guide refresh, update check), so time is moved on by hand
            compose.mainClock.autoAdvance = false
            fun shot(name: String) {
                compose.mainClock.advanceTimeBy(2_000)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            shot("1-sport")
            compose.onNodeWithText("Favorites").performClick(); shot("2-favorites")
            compose.onNodeWithText("All").performClick(); shot("3-all")
        }
    }
}
