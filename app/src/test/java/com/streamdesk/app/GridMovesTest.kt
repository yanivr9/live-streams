package com.streamdesk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The TV remote's cursor on a grid of 4 columns with 13 channels (the last row has one). */
class GridMovesTest {
    private val cols = 4
    private val n = 13

    @Test fun downGoesToTheRowBelowOrStaysOnTheLastRow() {
        assertEquals(4, GridMoves.down(0, cols, n))
        assertEquals(9, GridMoves.down(5, cols, n))
        assertEquals(12, GridMoves.down(9, cols, n))   // the last row is short: its only channel
        assertEquals(12, GridMoves.down(11, cols, n))
        assertEquals(12, GridMoves.down(12, cols, n))   // nothing below
        assertEquals(10, GridMoves.down(10, 4, 11).let { it })
    }

    @Test fun upLeavesForTheTabsFromTheTopRow() {
        assertNull(GridMoves.up(0, cols)); assertNull(GridMoves.up(3, cols))
        assertEquals(0, GridMoves.up(4, cols))
        assertEquals(8, GridMoves.up(12, cols))
    }

    @Test fun sidewaysStaysInTheRow() {
        assertEquals(0, GridMoves.left(0, cols)); assertEquals(4, GridMoves.left(4, cols)); assertEquals(1, GridMoves.left(2, cols))
        assertEquals(3, GridMoves.right(3, cols, n)); assertEquals(2, GridMoves.right(1, cols, n))
        assertEquals(12, GridMoves.right(12, cols, n))   // no channel to its right
    }
}

/** The whole chain on a simulated TV: arrow keys and OK reaching the home screen open the channel the cursor is on. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television")
class TvRemoteTest {
    @get:org.junit.Rule val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    private fun press(activity: MainActivity, code: Int, holdRepeats: Int = 0) {
        activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, code, 0))
        for (r in 1..holdRepeats) activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, code, r))
        activity.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_UP, code, 0))
    }

    @org.junit.Test
    fun arrowsMoveTheCursorAndOkPlaysThatChannel() {
        val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        org.robolectric.Shadows.shadowOf(context.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN, false)
        seedChannels(context)
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { sc ->
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(1_500)
            sc.onActivity { a ->
                // Sport list in the test account: Sport 1, Sport 2, Sport 5, ONE 1 (one row of four). OK on the first plays it
                press(a, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)       // to Sport 2
                press(a, android.view.KeyEvent.KEYCODE_DPAD_CENTER)      // OK
                val started = org.robolectric.Shadows.shadowOf(a).nextStartedActivity
                org.junit.Assert.assertEquals(PlayerActivity::class.java.name, started?.component?.className)
                val queue = started!!.getStringArrayListExtra("queue")!!
                val index = started.getIntExtra("index", -1)
                org.junit.Assert.assertEquals("Sport 2", org.json.JSONObject(queue[index]).getString("name"))
            }
        }
    }
}
