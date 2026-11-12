package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Two accounts: the round selector in ☰ → My playlists picks the one whose channels show; the other is turned off. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port")
class ActiveAccountTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun choosingAnAccountTurnsTheOtherOff() {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedChannels(context)
        // A second account (a friend's), on as well, as after adding it
        val second = "http://test.invalid/player_api.php?username=friend&password=p"
        val sources = JSONArray(File(context.filesDir, "playlists.json").readText())
            .put(PlaylistSource(second, "Friend", channelCount = 11, updatedAt = System.currentTimeMillis(), updateHours = 0).toJson())
        File(context.filesDir, "playlists.json").writeText(sources.toString())
        ChannelStore.resetForTests()

        ActivityScenario.launch(MainActivity::class.java).use {
            fun settle() = compose.waitForIdle()
            settle()
            // No Playlists tab any more: the playlists open from ☰ → My playlists
            compose.onNodeWithText("Playlists").assertDoesNotExist()
            compose.onNodeWithContentDescription("Menu").performClick(); settle()
            compose.onNodeWithText("My playlists").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()   // (the drawer's rounded edge blocks simulated finger taps)
            compose.onNodeWithContentDescription("Back to the channels").assertExists()
            compose.onNodeWithContentDescription("Use Friend").performClick(); settle()
            val saved = ChannelStore.get(context).sources.value.associate { it.name to it.enabled }
            assertEquals(mapOf("Test account" to false, "Friend" to true), saved)
            compose.onNodeWithContentDescription("Use Test account").performClick(); settle()
            assertEquals(mapOf("Test account" to true, "Friend" to false), ChannelStore.get(context).sources.value.associate { it.name to it.enabled })
        }
    }
}
