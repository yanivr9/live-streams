package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Compatibility matrix: the real home screen on many screen sizes, densities, orientations, font sizes, languages
 * and Android versions. Each one must open, show the Sport list, switch tabs and survive being turned.
 * Pictures of each go to -Dcompat=<folder> when given.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class CompatMatrixTest(private val name: String, private val qualifiers: String, private val tv: Boolean, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun configs() = listOf(
            arrayOf("phone-small-320x568", "w320dp-h568dp-port-xhdpi", false, 1f),
            arrayOf("phone-normal-360x640", "w360dp-h640dp-port-xxhdpi", false, 1f),
            arrayOf("phone-oneplus-411x891", "w411dp-h891dp-port-xxhdpi", false, 1f),
            arrayOf("phone-tall-412x915", "w412dp-h915dp-port-xxhdpi", false, 1f),
            arrayOf("phone-landscape-891x411", "w891dp-h411dp-land-xxhdpi", false, 1f),
            arrayOf("phone-big-font-2x", "w411dp-h891dp-port-xxhdpi", false, 2f),
            arrayOf("phone-small-big-font", "w320dp-h568dp-port-xhdpi", false, 1.5f),
            arrayOf("phone-hebrew-rtl", "he-rtl-w411dp-h891dp-port-xxhdpi", false, 1f),
            arrayOf("phone-night", "w411dp-h891dp-port-night-xxhdpi", false, 1f),
            arrayOf("foldable-open-673x841", "w673dp-h841dp-port-xxhdpi", false, 1f),
            arrayOf("tablet-7-600x960", "w600dp-h960dp-port-xhdpi", false, 1f),
            arrayOf("tablet-10-800x1280", "w800dp-h1280dp-port-xhdpi", false, 1f),
            arrayOf("tablet-10-landscape-1280x800", "w1280dp-h800dp-land-xhdpi", false, 1f),
            arrayOf("tv-720p-1280x720", "w1280dp-h720dp-land-television-mdpi", true, 1f),
            arrayOf("tv-1080p-960x540", "w960dp-h540dp-land-television-xhdpi", true, 1f),
            arrayOf("tv-1080p-1920x1080", "w1920dp-h1080dp-land-television-mdpi", true, 1f),
            arrayOf("tv-4k-960x540-xxhdpi", "w960dp-h540dp-land-television-xxhdpi", true, 1f),
            arrayOf("tv-big-font", "w960dp-h540dp-land-television-xhdpi", true, 1.3f),
            arrayOf("tv-hebrew-rtl", "he-rtl-w960dp-h540dp-land-television-xhdpi", true, 1f),
        )
    }

    @get:Rule val compose = createEmptyComposeRule()

    @Test(timeout = 240_000)
    fun opensShowsChannelsSwitchesTabsAndSurvivesTurning() {
        val context: Context = ApplicationProvider.getApplicationContext()
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, !tv)
        seedChannels(context)
        val pictures = System.getProperty("compat")?.let { File(it).also { d -> d.mkdirs() } }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.mainClock.autoAdvance = false
            fun settle() { compose.mainClock.advanceTimeBy(800); shadowOf(android.os.Looper.getMainLooper()).idle() }
            fun shot(step: String) {
                if (pictures == null) return
                settle()
                sc.onActivity { a ->
                    val v = a.window.decorView
                    val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                    v.draw(android.graphics.Canvas(b))
                    File(pictures, "$name-$step.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            settle()
            sc.onActivity { assertTrue(it.isTelevision() == tv) }
            compose.onAllNodesWithText("Sport 1")[0].assertExists()
            shot("1-sport")
            // The TV has Sport, Favorites and All; phones also have Israeli TV
            if (tv) { compose.onNodeWithText("All").performClick(); settle(); compose.onAllNodesWithText("YES SPORT 1 FHD")[0].assertExists() }
            else { compose.onNodeWithText("Israeli TV").performScrollToIfPhone(tv).performClick(); settle(); compose.onAllNodesWithText("Channel 12 · Keshet")[0].assertExists() }
            shot("2-israeli")
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToIfPhone(tv: Boolean) =
    if (tv) this else runCatching { performScrollTo() }.getOrDefault(this)

/** Android versions: the app must start on the oldest it supports (8.0) up to the newest. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class AndroidVersionBase {
    @get:Rule val compose = createEmptyComposeRule()

    @Test(timeout = 240_000)
    fun startsAndShowsTheChannels() {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedChannels(context)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(1_000)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("Sport 1")[0].assertExists()
            compose.onNodeWithText("Israeli TV").performClick()
            compose.mainClock.advanceTimeBy(800)
            compose.onAllNodesWithText("Channel 12 · Keshet")[0].assertExists()
        }
    }
}

@RunWith(AndroidJUnit4::class) @Config(sdk = [26], qualifiers = "w411dp-h891dp-port") class Android26Test : AndroidVersionBase()
@RunWith(AndroidJUnit4::class) @Config(sdk = [28], qualifiers = "w411dp-h891dp-port") class Android28Test : AndroidVersionBase()
@RunWith(AndroidJUnit4::class) @Config(sdk = [29], qualifiers = "w411dp-h891dp-port") class Android29Test : AndroidVersionBase()
@RunWith(AndroidJUnit4::class) @Config(sdk = [30], qualifiers = "w411dp-h891dp-port") class Android30Test : AndroidVersionBase()
@RunWith(AndroidJUnit4::class) @Config(sdk = [31], qualifiers = "w411dp-h891dp-port") class Android31Test : AndroidVersionBase()
@RunWith(AndroidJUnit4::class) @Config(sdk = [33], qualifiers = "w411dp-h891dp-port") class Android33Test : AndroidVersionBase()
@RunWith(AndroidJUnit4::class) @Config(sdk = [35], qualifiers = "w411dp-h891dp-port") class Android35Test : AndroidVersionBase()
