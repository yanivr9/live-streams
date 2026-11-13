package com.streamdesk.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A picture of the longest settings lines at phone width (only with -Dqashots=<folder>). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class SettingsLookTest {
    // Opens nothing unless pictures are asked for (a test screen left open would hold up the next tests)
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun longNamesWrap() {
        val dir = System.getProperty("qashots")?.let(::File)
        assumeTrue(dir != null)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.setContent {
                StreamDeskTheme {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        DrawerSection("Look", divider = false)
                        DrawerRow(Icons.Default.DarkMode, "Theme", {}) { DrawerSegments(listOf("Phone", "Dark", "Light"), 0) {} }
                        DrawerRow(Icons.Default.Language, "Language", {}) { DrawerSegments(listOf("Phone", "עברית", "English"), 2) {} }
                        DrawerRow(Icons.Default.SignalCellularAlt, "Lower quality on mobile data", {}) { DrawerSwitch(true) {} }
                        DrawerRow(Icons.Default.PictureInPictureAlt, "Small window when leaving", {}) { DrawerSwitch(true) {} }
                        DrawerRow(Icons.Default.UnfoldMore, "CH + / − buttons on the picture", {}) { DrawerSwitch(true) {} }
                    }
                }
            }
            }
            compose.waitForIdle()
            scenario.onActivity { a ->
                val v = a.window.decorView
                val img = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(img))
                File(dir!!, "settings-lines.png").outputStream().use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
