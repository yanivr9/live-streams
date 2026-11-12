package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onRoot
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
            compose.onAllNodesWithText("Recent").assertCountEquals(0)   // the phone has no Recent tab
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

            // Scrolling the channels down shrinks Add playlist to just +, scrolling back up opens it out again
            compose.onNodeWithText("All").performClick(); settle()
            compose.onNodeWithText("Add playlist", useUnmergedTree = true).assertExists()
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.4f) }; settle()
            compose.onNodeWithText("Add playlist", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("Add playlist").assertExists()
            compose.onRoot().performTouchInput { swipeDown(startY = bottom * 0.4f, endY = bottom * 0.8f) }; settle()
            compose.onNodeWithText("Add playlist", useUnmergedTree = true).assertExists()

            // Search: the glass button opens the glass bar, typing filters, ✕ closes it and shows everything again
            compose.onNodeWithContentDescription("Search channels").performClick(); settle()
            compose.onNode(hasSetTextAction()).performTextInput("Sport 5"); settle()
            compose.onNodeWithText("2 channels").assertExists()   // Sport 5 in Full HD and 4K
            compose.onNodeWithText("Channel 13").assertDoesNotExist()
            compose.onNodeWithContentDescription("Close search").performClick(); settle()
            compose.onNodeWithContentDescription("Search channels").assertExists()
            compose.onNodeWithText("2 channels").assertDoesNotExist()   // the filter is cleared

            // The side menu: ☰ opens it, a channel list in it opens that tab, and the ⋮ menu is gone from the phone
            compose.onNodeWithContentDescription("More options").assertDoesNotExist()
            compose.onNodeWithContentDescription("Menu").performClick(); settle()
            compose.onNodeWithText("Share account with QR code").assertExists()
            compose.onNodeWithText("Settings").assertExists()   // one entry: the settings are on their own screen
            compose.onNodeWithText("Video quality").assertDoesNotExist()
            System.getProperty("tvshots")?.let { dir ->
                scenario.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                    v.draw(android.graphics.Canvas(b))
                    File(dir, "phone-drawer.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            compose.onNodeWithText("Israeli TV", useUnmergedTree = true).let { compose.onAllNodesWithText("Israeli TV")[1].performClick() }; settle()
            compose.onNodeWithText("Share account with QR code").assertDoesNotExist()   // closed again
        }
    }
}
