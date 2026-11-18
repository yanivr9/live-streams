package com.streamdesk.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.Sentry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Sends two real activity-log lines to Sentry (project livio) through the app's own code, to see that reports
 * arrive. Only on request:
 *   ./gradlew testDebugUnitTest --tests "com.streamdesk.app.SentryCheckTest" -Dsentrycheck=<some text>
 * then look for the text in sentry.io → Explore → Logs.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SentryCheckTest {
    @Test
    fun sendsTwoLogLines() {
        val text = System.getProperty("sentrycheck")
        assumeTrue("only on request (-Dsentrycheck=…)", !text.isNullOrBlank())
        SentryLogs.start(ApplicationProvider.getApplicationContext<Application>(), evenInTests = true)
        AppLog.i("check", "Sentry check: $text")
        AppLog.e("check", "Sentry check, error level: $text (http://h/live/amy/s3cret/1.ts)")
        Sentry.flush(15_000)
        Sentry.close()
    }
}
