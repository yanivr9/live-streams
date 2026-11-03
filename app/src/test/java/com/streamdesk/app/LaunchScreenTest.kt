package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The app starts with the logo and a loading bar, not a black screen, and hands over to the channels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class LaunchScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun loadingScreenFirstThenTheChannels() {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
        seedChannels(context)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(50)
            assertEquals(1, compose.onAllNodesWithText("Loading your channels…").fetchSemanticsNodes().size)
            System.getProperty("qa")?.let { dir ->
                sc.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                    v.draw(android.graphics.Canvas(b))
                    File(dir, "out").also { it.mkdirs() }.let { File(it, "launch-screen.png").outputStream().use { o -> b.compress(Bitmap.CompressFormat.PNG, 100, o) } }
                }
            }
            compose.mainClock.advanceTimeBy(4_000)
            assertEquals(0, compose.onAllNodesWithText("Loading your channels…").fetchSemanticsNodes().size)
            assertEquals(true, compose.onAllNodesWithText("Sport 1").fetchSemanticsNodes().isNotEmpty())
        }
    }
}
