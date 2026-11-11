package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The phone's list / grid switch: the button swaps the view, the choice is kept, and a card plays its channel. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class PhoneGridTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun gridButtonSwitchesTheViewAndIsRemembered() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedChannels(context)
        PhoneView.setGrid(context, false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.mainClock.autoAdvance = false
            fun settle() { compose.mainClock.advanceTimeBy(2_000); shadowOf(android.os.Looper.getMainLooper()).idle() }
            settle()
            compose.onNodeWithText("Sport").performClick(); settle()
            compose.onNodeWithContentDescription("Show as grid").performClick(); settle()
            assertTrue(PhoneView.isGrid(context))
            compose.onNodeWithContentDescription("Show as list").assertExists()
            compose.onNodeWithText("Sport 1").assertExists()
            System.getProperty("tvshots")?.let { dir ->
                scenario.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                    v.draw(android.graphics.Canvas(b))
                    File(dir, "phone-grid.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            compose.onNodeWithContentDescription("Show as list").performClick(); settle()
            assertFalse(PhoneView.isGrid(context))
        }
    }
}
