package com.streamdesk.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An account shared as a QR code: what one phone draws, the other reads back exactly (also from a screenshot). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LoginQrTest {
    private val login = XtreamLogin("http://server.example:8080", "friend", "p@ss w/ord&=?", live = true, movies = false, series = false, categories = "Hebrew, Sport")

    @Test
    fun textRoundTrip() {
        val text = LoginQr.encode(login, "Yossi's account")
        assertEquals(login to "Yossi's account", LoginQr.parse(text))
        assertEquals(login to null, LoginQr.parse(LoginQr.encode(login, null)))
    }

    @Test
    fun otherTextIsNotAnAccount() {
        assertNull(LoginQr.parse("https://example.com"))
        assertNull(LoginQr.parse("streamdesk://connect?host=192.168.1.5&code=123456"))
        assertNull(LoginQr.parse("streamdesk://login?d=not-base64-json"))
        assertNull(LoginQr.parse(null))
    }

    @Test
    fun theQrPictureReadsBack() {
        val text = LoginQr.encode(login, "Yossi's account")
        val qr = LoginQr.bitmap(text)
        assertEquals(text, LoginQr.decode(qr))
        // Like a screenshot: the code smaller, on a dark screen with other things around it
        val shot = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        Canvas(shot).apply {
            drawColor(Color.rgb(14, 17, 19))
            drawBitmap(Bitmap.createScaledBitmap(qr, 600, 600, true), 240f, 800f, null)
        }
        assertEquals(login to "Yossi's account", LoginQr.parse(LoginQr.decode(shot)))
    }
}
