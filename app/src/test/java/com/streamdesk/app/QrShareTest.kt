package com.streamdesk.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Share with QR code: "Send picture" opens the share menu with the picture, "Save picture" saves it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QrShareTest {
    @Test
    fun sendAndSaveTheQrPicture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val text = LoginQr.encode(XtreamLogin("http://x.example:8080", "u", "p"), "Account")
                LoginQr.send(a, text)   // threw "Immutable bitmap passed to Canvas" until 3.85
                val chooser = shadowOf(a).nextStartedActivity
                assertEquals(Intent.ACTION_CHOOSER, chooser.action)
                val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
                assertEquals("image/png", send.type)
                assertTrue(LoginQr.save(a, text, "Account"))
            }
        }
    }
}
