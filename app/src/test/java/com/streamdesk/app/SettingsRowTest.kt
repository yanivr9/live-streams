package com.streamdesk.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A line in the side menu or Settings: a tap anywhere on it does its job, once per tap. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tappingTheLineFlipsTheSetting() {
        var flips = 0
        compose.setContent {
            StreamDeskTheme {
                var on by remember { mutableStateOf(true) }
                DrawerRow(Icons.Default.Info, "CH buttons", { on = !on; flips++ }, selected = true) { DrawerSwitch(on) { on = it; flips++ } }
            }
        }
        compose.onNodeWithText("CH buttons").performClick(); compose.waitForIdle()
        assertEquals(1, flips)
        compose.onNodeWithText("CH buttons").performClick(); compose.waitForIdle()
        assertEquals(2, flips)
    }
}
