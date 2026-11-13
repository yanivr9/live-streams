package com.streamdesk.app

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A picture of the phone player with its guide bar (only with -Dqashots=<folder>). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class PlayerShotTest {
    @Test
    fun phonePlayer() {
        val dir = System.getProperty("qashots")?.let(::File)
        assumeTrue(dir != null)
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        seedChannels(context)
        val queue = listOf(
            Channel("http://127.0.0.1:9/live/u/p/1.ts", "IL: Sport 5 FHD", "Hebrew", logo = "file:///android_asset/logos/sport5.png"),
            Channel("http://127.0.0.1:9/live/u/p/2.ts", "IL: Sport 2 FHD", "Hebrew"),
        )
        ActivityScenario.launch<PlayerActivity>(PlayerActivity.intent(context, queue, 0, StreamFormat.Auto)).use { scenario ->
            repeat(20) { shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)) }
            scenario.onActivity { a ->
                val v = a.window.decorView
                val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(b))
                File(dir!!, "player.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
