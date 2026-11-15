package com.streamdesk.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** "I pressed and nothing happened" is written down with the log of those moments; things that worked aren't. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WatchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() = Problems.clear(context)

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun aChannelSwitchThatNeverStartsIsReportedWithItsLog() {
        AppLog.i("player", "Open \"Sport 2\": http://h/live/user/pass/2.ts")
        Watch.start(context, "player", "Switch from \"Sport 1\" to \"Sport 2\"", 20_000)
        pass(19_000)
        assertTrue(Problems.all(context).isEmpty())
        pass(2_000)
        val p = Problems.all(context).single()
        assertEquals("no response", p.kind)
        assertEquals("Switch from \"Sport 1\" to \"Sport 2\": nothing after 20 s", p.message)
        assertTrue(p.log, p.log.contains("[tap] Switch from \"Sport 1\" to \"Sport 2\""))
        assertTrue(p.log, p.log.contains("/live/***/***/") && !p.log.contains("pass"))   // the password stays out
        val report = Problems.report(context)
        assertTrue(report, report.contains("── ") && report.contains("nothing after 20 s"))
    }

    @Test
    fun whatStartedInTimeIsNotReported() {
        Watch.start(context, "player", "Open \"Sport 1\"", 20_000)
        pass(3_000)
        Watch.done("player")
        pass(30_000)
        assertTrue(Problems.all(context).isEmpty())
    }

    @Test
    fun aNewTapReplacesTheOldWait() {
        Watch.start(context, "player", "Switch to \"A\"", 20_000)
        pass(10_000)
        Watch.start(context, "player", "Switch to \"B\"", 20_000)   // pressed CH+ again
        pass(15_000)
        assertTrue(Problems.all(context).isEmpty())
        pass(6_000)
        assertEquals("Switch to \"B\": nothing after 20 s", Problems.all(context).single().message)
    }

    @Test
    fun theSameProblemTwiceInARowIsOneReport() {
        Problems.record(context, "playback", "Sport 1: The server didn't answer.")
        Problems.record(context, "playback", "Sport 1: The server didn't answer.")
        Problems.record(context, "playback", "Sport 2: The server didn't answer.")
        assertEquals(2, Problems.all(context).size)
    }
}
