package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Pictures of the phone screens for a look-over (only with -Dqashots=<folder>). */
abstract class QaShotsBase {
    @get:Rule val compose = createEmptyComposeRule()
    abstract val tag: String
    open val theme: ThemeMode = ThemeMode.Dark

    @Test
    fun screens() {
        val dir = System.getProperty("qashots")?.let(::File)
        assumeTrue(dir != null)
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedChannels(context)
        ThemePrefs.set(context, theme)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.mainClock.autoAdvance = false
            fun shot(name: String) {
                compose.mainClock.advanceTimeBy(2_000)
                shadowOf(android.os.Looper.getMainLooper()).idle()
                scenario.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                    v.draw(android.graphics.Canvas(b))
                    File(dir, "$tag-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            PhoneView.setGrid(context, false)
            shot("1-list")
            compose.onNodeWithContentDescription("Show as grid").performClick(); shot("2-grid")
            compose.tab("Israeli TV").performClick(); shot("3-israeli-grid")
            compose.onNodeWithContentDescription("Search channels").performClick(); shot("4-search-open")
            compose.onNode(hasSetTextAction()).performTextInput("Channel 1"); shot("5-search-typed")
            compose.onNodeWithContentDescription("Close search").performClick(); shot("6-search-closed")
            compose.onNodeWithContentDescription("Menu").performClick(); shot("7-menu")
            PhoneView.setGrid(context, false)
        }
    }
}

@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-port-xhdpi")
class QaShotsSmallPhone : QaShotsBase() { override val tag = "small" }

@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class QaShotsPhone : QaShotsBase() { override val tag = "phone" }

@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi")
class QaShotsPhoneSideways : QaShotsBase() { override val tag = "sideways" }

@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-port-xhdpi")
class QaShotsTablet : QaShotsBase() { override val tag = "tablet" }

@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class QaShotsPhoneLight : QaShotsBase() { override val tag = "light"; override val theme = ThemeMode.Light }
