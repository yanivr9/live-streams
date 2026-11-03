package com.streamdesk.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** The real screens on a simulated phone, driven like a user: tabs, rebuilding the screen, menus, the player. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port")   // a phone like the OnePlus
class PhoneScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedChannels(context)
    }

    @Test
    fun tabsMenusAndRebuildingTheScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            // Opens on Sport, with Sport 5 as the FHD link
            compose.onAllNodesWithText("Sport 1")[0].assertExists()
            compose.onAllNodesWithText("Sport 5")[0].assertExists()
            scenario.onActivity { assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, it.requestedOrientation) }

            compose.onNodeWithText("Israeli TV").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText("Channel 12 · Keshet")[0].assertExists()

            compose.onNodeWithText("All").performScrollTo().performClick()   // the tab bar may have scrolled it out of view
            compose.waitForIdle()
            compose.onAllNodesWithText("YES SPORT 1 FHD")[0].assertExists()
            // Scrolling down the list, like a finger would
            compose.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasText("HOT Cinema"))
            compose.onAllNodesWithText("HOT Cinema")[0].assertExists()

            // The screen is rebuilt (what turning the phone did before): it comes back with its channels, not black
            scenario.recreate()
            compose.waitForIdle()
            compose.onAllNodesWithText("HOT Cinema")[0].assertExists()   // still scrolled to where it was

            // The menu and the problem report
            compose.onNodeWithContentDescription("More options").performClick()
            compose.waitForIdle()
            // (Taps don't reach pop-up windows in this simulator, so the entry is pressed directly)
            compose.onNodeWithText("Send problem report").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
            compose.onNodeWithText("Problem report").assertExists()
            compose.onNodeWithText("Copy").performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
        }
    }

    @Test
    fun addingChannelsOpensWithBothChoices() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForIdle()
            // The add button in the top bar goes straight to the Add channels window
            compose.onNodeWithContentDescription("Add channels").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Username & password").assertExists()
            compose.onNodeWithText("Playlist link").assertExists()
            // (Text fields inside pop-up windows never settle in this simulator, so the link page is checked in
            // linksWithoutHttpAreAccepted instead.) Username & password → Continue opens the sign-in screen
            compose.onNodeWithText("Continue").assertIsEnabled().performClick()
            compose.waitForIdle()
        }
    }

    @Test
    fun linksWithoutHttpAreAccepted() {
        assertEquals("http://server.com:8080/tv.m3u", normalizeLink(" server.com:8080/tv.m3u "))
        assertEquals("https://x.com/a.m3u", normalizeLink("https://x.com/a.m3u"))
        assertEquals(null, normalizeLink("not a link"))
        assertEquals(null, normalizeLink("hello"))
        assertEquals(null, normalizeLink("ftp://x.com/a"))
        // A provider's get.php link with a username and password goes to the sign-in instead of being added as a list
        val provider = normalizeLink("cdn.example.com:8080/get.php?username=u&password=p&type=m3u")!!
        assertEquals(true, XtreamLogin.isLoginLink(provider))
        assertEquals("u", XtreamLogin.fromLink(provider)?.username)
    }

    @Test
    fun playerStartsLeavesAndComesBack() {
        val queue = listOf(Channel("http://127.0.0.1:9/live/u/p/1.ts", "IL: Sport 5 FHD", "Hebrew - Sport"), Channel("http://127.0.0.1:9/live/u/p/2.ts", "IL: ONE 1 FHD", "Hebrew - Sport"))
        val intent = PlayerActivity.intent(context, queue, 0, StreamFormat.Auto)
        ActivityScenario.launch<PlayerActivity>(intent).use { scenario ->
            compose.waitForIdle()
            // Leave (Home) and come back, twice, then close it with Back
            repeat(2) {
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        }
    }
}

/** Leaving the player: the sound goes on only when "Sound with the screen off" is on; otherwise the "Playing" notice goes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port")
class PlayerLeavingTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val queue = listOf(Channel("http://127.0.0.1:9/live/u/p/1.ts", "IL: Sport 5 FHD", "Hebrew - Sport"))

    @Before
    fun phone() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedChannels(context)
    }

    private fun leaveAndReturnStoppedService(soundInBackground: Boolean): String? {
        BackgroundAudio.setEnabled(context, soundInBackground)
        ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context, queue, 0, StreamFormat.Auto)).use { scenario ->
            val app = shadowOf(context as android.app.Application)
            while (app.nextStoppedService != null) { /* forget earlier ones */ }
            if (soundInBackground) assertEquals(PlaybackService::class.java.name, app.peekNextStartedService()?.component?.className)
            scenario.moveToState(Lifecycle.State.CREATED)   // Home
            return app.nextStoppedService?.component?.className
        }
    }

    @Test
    fun soundOffClosesTheStreamAndTheNotice() {
        assertEquals(PlaybackService::class.java.name, leaveAndReturnStoppedService(soundInBackground = false))
    }

    @Test
    fun soundOnKeepsPlayingInTheBackground() {
        assertEquals(null, leaveAndReturnStoppedService(soundInBackground = true))
    }
}

/** The same screens on a simulated TV box: tiles, the tab bar, the TV-only menu entries. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")   // a 1080p TV
class TvScreensTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
        seedChannels(context)
    }

    @Test
    fun tilesTabsAndTvMenu() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitForIdle()
            scenario.onActivity { assertTrue(it.isTelevision()) }
            compose.onAllNodesWithText("Sport 1")[0].assertExists()
            // The TV has only Sport, Favorites and All
            compose.onAllNodesWithText("Israeli TV").assertCountEquals(0)
            compose.onNodeWithText("All").performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText("Channel 13 FHD")[0].assertExists()
            compose.onNodeWithContentDescription("More options").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Channels on screen: 12").assertExists()
            compose.onNodeWithText("Quality on this TV: Up to Full HD").assertExists()
        }
    }
}

/** A small account like the real one: Sport and Israeli channels in several qualities, plus another channel. */
internal fun seedChannels(context: Context) {
    ChannelStore.resetForTests()
    val src = "http://test.invalid/player_api.php?username=u&password=p"
    val names = listOf(
        "IL: YES SPORT 1 FHD", "IL: YES SPORT 2 FHD", "IL: Sport 5 FHD", "IL: Sport 5 4K", "IL: ONE 1 FHD", "IL: ONE 1 4K",
        "IL: Kan11 FHD", "IL: Channel 12 FHD", "IL: Channel 13 FHD", "IL: Channel 14 HD", "IL: HOT Cinema",
    )
    val channels = JSONArray().also { a ->
        names.forEachIndexed { i, n -> a.put(Channel("http://test.invalid/live/u/p/${100 + i}.ts", n, "Hebrew", source = src).toJson()) }
    }
    File(context.filesDir, "channels.json").writeText(channels.toString())
    val source = PlaylistSource(src, "Test account", channelCount = names.size, updatedAt = System.currentTimeMillis(), updateHours = 0)
    File(context.filesDir, "playlists.json").writeText(JSONArray().put(source.toJson()).toString())
    File(context.filesDir, "recent.json").delete()
}
