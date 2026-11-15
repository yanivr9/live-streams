package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val SRC = "http://test.invalid/player_api.php?username=u&password=p"

private fun seedGroups(context: Context) {
    ChannelStore.resetForTests()
    FavoriteCategories.reset()
    context.getSharedPreferences("categories", Context.MODE_PRIVATE).edit().clear().commit()
    val list = listOf(
        "Hebrew Sport" to listOf("Sport 1 HD", "Sport 2 HD", "Sport 5 HD"),
        "Hebrew Movies" to listOf("Movies 1", "Movies 2"),
        "News" to listOf("Channel 12", "Channel 13", "CNN"),
        "Kids" to listOf("Junior", "Disney"),
    )
    var id = 100
    val channels = JSONArray()
    list.forEach { (g, names) -> names.forEach { n -> channels.put(Channel("http://test.invalid/live/u/p/${id++}.ts", n, g, source = SRC).toJson()) } }
    File(context.filesDir, "channels.json").writeText(channels.toString())
    val source = PlaylistSource(SRC, "Test account", channelCount = channels.length(), updatedAt = System.currentTimeMillis(), updateHours = 0)
    File(context.filesDir, "playlists.json").writeText(JSONArray().put(source.toJson()).toString())
    File(context.filesDir, "recent.json").delete()
}

/** The All tab's categories: what's in the list, in which order, and the star. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CategoriesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun ch(name: String, group: String) = Channel("http://h/$name", name, group)

    @Before
    fun clean() {
        FavoriteCategories.reset()
        context.getSharedPreferences("categories", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun allChannelsFirstThenFavoritesThenTheRestByName() {
        val channels = listOf(ch("a", "News"), ch("b", "Sport"), ch("c", "sport "), ch("d", ""), ch("e", "Kids"), ch("f", "Movies"))
        val list = Categories.build(channels, setOf("Movies"))
        assertEquals(listOf(Category.ALL, "Movies", "Kids", "News", "Sport", "sport", ""), list.map { it.key })
        assertEquals(6, list[0].count)
        assertTrue(list[1].favorite)
        assertEquals("Other", list.last().name)   // channels without a group go last
        assertTrue(Categories.matches(ch("x", "News "), "News"))
        assertTrue(Categories.matches(ch("x", "News"), Category.ALL))
        assertFalse(Categories.matches(ch("x", "News"), "Sport"))
    }

    @Test
    fun theStarIsSavedOnTheDevice() {
        assertTrue(FavoriteCategories.toggle(context, "Hebrew Sport"))
        FavoriteCategories.reset()
        assertEquals(setOf("Hebrew Sport"), FavoriteCategories.load(context).value)
        assertFalse(FavoriteCategories.toggle(context, "Hebrew Sport"))
        FavoriteCategories.reset()
        assertEquals(emptySet<String>(), FavoriteCategories.load(context).value)
    }

    @Test
    fun iconsFromTheName() {
        assertEquals("⚽", Categories.icon("IL: Hebrew Sport"))
        assertEquals("🎬", Categories.icon("Hebrew Movies"))
        assertEquals("🧸", Categories.icon("Kids"))
        assertEquals("🇮🇱", Categories.icon("IL | Channels"))
        assertEquals("📺", Categories.icon("Brazil TV"))
    }
}

private fun shooter(scenario: ActivityScenario<MainActivity>, prefix: String): (String) -> Unit = { name ->
    System.getProperty("qashots")?.let(::File)?.let { dir ->
        scenario.onActivity { a ->
            val v = a.window.decorView
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            File(dir, "$prefix-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

/** Phone: All → the categories, a category's channels, its star, Back, and Favorites showing the starred category. */
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class CategoriesPhoneTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun browseAndStarACategory() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        seedGroups(context)
        PhoneView.setGrid(context, false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val shot0 = shooter(scenario, "cat-phone")
            compose.mainClock.autoAdvance = false
            fun idle() { compose.mainClock.advanceTimeBy(2_000); shadowOf(android.os.Looper.getMainLooper()).idle() }
            val shot: (String) -> Unit = { idle(); shot0(it) }
            idle()
            compose.tab("All").performClick(); idle()
            compose.onNodeWithText("Hebrew Movies").assertExists()
            compose.onNodeWithText("All channels").assertExists()
            assertEquals(0, compose.onAllNodesWithText("CNN").fetchSemanticsNodes().size)   // categories, not channels
            compose.onAllNodesWithContentDescription("Add category to favorites")[1].performClick()   // Hebrew Sport (by name: Kids, Hebrew Movies, Hebrew Sport…)
            idle()
            shot("1-categories")
            assertEquals(1, FavoriteCategories.names.value.size)
            val starred = FavoriteCategories.names.value.first()
            compose.onNodeWithText("★ Favorite categories".uppercase()).assertExists()

            compose.onNodeWithText("News").performClick(); idle()
            compose.onNodeWithText("CNN").assertExists()
            assertEquals(0, compose.onAllNodesWithText("Junior").fetchSemanticsNodes().size)
            shot("2-inside")
            compose.onNodeWithContentDescription("Back to the categories").performClick(); idle()
            compose.onNodeWithText("Kids").assertExists()

            compose.tab("Favorites").performClick(); idle()
            compose.onAllNodesWithText("$starred · 3", substring = true)[0].assertExists()
            shot("3-favorites")
            compose.onAllNodesWithText("$starred · 3", substring = true)[0].performClick(); idle()
            compose.onNodeWithContentDescription("Back to the categories").assertExists()
        }
    }
}

/** TV: the categories are tiles; OK opens one, holding OK stars it, Back returns to the same tile. */
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class CategoriesTvTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun remoteOpensAndStarsCategories() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
        seedGroups(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val shot = shooter(scenario, "cat-tv")
            compose.mainClock.autoAdvance = false
            fun idle() {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                compose.mainClock.advanceTimeBy(1_000)
                shadowOf(android.os.Looper.getMainLooper()).idle()
            }
            fun key(code: Int, repeat: Int = 0, up: Boolean = false) = scenario.onActivity {
                it.dispatchKeyEvent(KeyEvent(0, 0, if (up) KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN, code, repeat))
            }
            idle()
            compose.onNodeWithText("All").performClick(); idle()
            compose.onNodeWithText("Hebrew Movies").assertExists()
            // ▶ to the second tile (Hebrew Movies), hold OK: starred
            key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_RIGHT, up = true); idle()
            key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1); key(KeyEvent.KEYCODE_DPAD_CENTER, up = true); idle()
            assertEquals(setOf("Hebrew Movies"), FavoriteCategories.names.value)
            shot("1-categories")
            // Now Hebrew Movies is the second tile still (favorites first); OK opens it
            key(KeyEvent.KEYCODE_DPAD_CENTER); key(KeyEvent.KEYCODE_DPAD_CENTER, up = true); idle()
            shot("2-inside")
            compose.onNodeWithText("Movies 2", substring = true).assertExists()
            assertEquals(0, compose.onAllNodesWithText("CNN").fetchSemanticsNodes().size)
            shot("2-inside")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; idle()
            compose.onNodeWithText("Kids").assertExists()
            shot("3-back")
        }
    }
}
