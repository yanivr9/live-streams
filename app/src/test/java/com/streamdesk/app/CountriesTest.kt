package com.streamdesk.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A provider with Hebrew folders (loaded) and UK / US folders (not loaded). */
private fun provider(ignoreCategory: Boolean = false): MockWebServer = MockWebServer().apply {
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            fun json(s: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(s)
            return when (url.queryParameter("action")) {
                null -> json("""{"user_info":{"auth":1,"status":"Active"}}""")
                "get_live_categories" -> json(
                    """[{"category_id":"1","category_name":"Hebrew - Sport"},{"category_id":"2","category_name":"Hebrew - News"},
                       {"category_id":"10","category_name":"UK | Sport"},{"category_id":"11","category_name":"UK | News"},
                       {"category_id":"20","category_name":"US: News"},{"category_id":"30","category_name":"VIP Events"}]"""
                )
                "get_live_streams" -> {
                    val all = listOf("10" to "Sky Sports Main Event", "10" to "TNT Sports 1", "11" to "BBC News", "20" to "CNN")
                    val cat = url.queryParameter("category_id")
                    val list = all.filter { ignoreCategory || cat == null || it.first == cat }
                    json(JSONArray().also { a -> list.forEachIndexed { i, (c, n) -> a.put(org.json.JSONObject().put("stream_id", 500 + i).put("name", n).put("category_id", c)) } }.toString())
                }
                else -> MockResponse().setResponseCode(400)
            }
        }
    }
    start()
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CountriesTest {
    @Test
    fun countryFromTheFolderName() {
        assertEquals("UK", Countries.of("UK | Sport"))
        assertEquals("UK", Countries.of("[GB] News"))
        assertEquals("US", Countries.of("USA: Movies"))
        assertEquals("US", Countries.of("US - NBA"))
        assertEquals("IL", Countries.of("Hebrew - Sport"))
        assertEquals("FR", Countries.of("FR| TF1"))
        assertEquals("AR", Countries.of("Arabic Kids"))
        assertEquals("", Countries.of("VIP Events"))
        assertEquals("", Countries.of("4K | Movies"))
        assertEquals(listOf("FR", "UK", ""), Countries.sort(listOf("", "UK", "FR")))
    }

    @Test
    fun onlyFoldersNotInTheListAreOffered() {
        val all = mapOf("a" to listOf(RemoteCategory("a", "1", "Hebrew - Sport"), RemoteCategory("a", "10", "UK | Sport")))
        assertEquals(listOf("UK | Sport"), RemoteCategories.notLoaded(all, setOf("Hebrew - Sport")).map { it.name })
    }

    @Test
    fun loadsOneFolderEvenWhenTheServerSendsEverything() = runBlocking {
        val server = provider(ignoreCategory = true)
        try {
            val login = XtreamLogin(server.url("/").toString(), "u", "p")
            val list = Xtream.categoryChannels(login, null, "10", "UK | Sport", "ts")
            assertEquals(listOf("Sky Sports Main Event", "TNT Sports 1"), list.map { it.name })
            assertTrue(list.all { it.group == "UK | Sport" && it.url.endsWith(".ts") && "/live/u/p/" in it.url })
        } finally {
            server.shutdown()
        }
    }
}

/** Phone: All → More countries → United Kingdom → UK | Sport loads on its own → Keep in my list. */
@RunWith(AndroidJUnit4::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class CountriesPhoneTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before fun up() { server = provider(); RemoteCategories.resetForTests() }
    @After fun down() = server.shutdown()

    @Test
    fun browseLoadAndKeep() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        ChannelStore.resetForTests()
        PhoneView.setGrid(context, false)
        val login = XtreamLogin(server.url("/").toString(), "u", "p", movies = false, series = false, categories = "Hebrew")
        val src = login.sourceUrl
        val channels = JSONArray()
        listOf("Hebrew - Sport" to "Sport 1", "Hebrew - Sport" to "Sport 2", "Hebrew - News" to "Channel 12").forEachIndexed { i, (g, n) ->
            channels.put(Channel("${login.base}live/u/p/${100 + i}.ts", n, g, source = src).toJson())
        }
        File(context.filesDir, "channels.json").writeText(channels.toString())
        val source = PlaylistSource(src, "Test account", channelCount = 3, updatedAt = System.currentTimeMillis(), updateHours = 0, xtream = login)
        File(context.filesDir, "playlists.json").writeText(JSONArray().put(source.toJson()).toString())
        File(context.filesDir, "recent.json").delete()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.mainClock.autoAdvance = false
            fun idle() {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                compose.mainClock.advanceTimeBy(500)
                shadowOf(android.os.Looper.getMainLooper()).idle()
            }
            fun waitFor(text: String) {
                repeat(100) {
                    idle()
                    if (compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
                    Thread.sleep(100)
                }
                throw AssertionError("never showed: $text")
            }
            fun shot(name: String) {
                idle()
                System.getProperty("qashots")?.let(::File)?.let { dir ->
                    scenario.onActivity { a ->
                        val v = a.window.decorView
                        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                        v.draw(android.graphics.Canvas(b))
                        File(dir, "countries-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
            idle()
            compose.tab("All").performClick()
            waitFor("United Kingdom")
            compose.onNodeWithText("Hebrew - Sport").assertExists()   // the loaded folders stay as they are
            assertEquals(0, compose.onAllNodesWithText("Sky Sports", substring = true).fetchSemanticsNodes().size)
            shot("1-all")

            compose.onNodeWithText("United Kingdom").performClick(); idle()
            compose.onNodeWithText("UK | News").assertExists()
            assertEquals(0, compose.onAllNodesWithText("US: News").fetchSemanticsNodes().size)
            shot("2-country")

            compose.onNodeWithText("UK | Sport").performClick()
            waitFor("TNT Sports 1")
            assertEquals(0, compose.onAllNodesWithText("BBC News").fetchSemanticsNodes().size)
            // Just looking: nothing joined the list yet
            assertEquals(3, ChannelStore.get(context).channels.value.size)
            shot("3-peek")

            compose.onNodeWithText("+ Keep in my list").performClick(); idle()
            val store = ChannelStore.get(context)
            assertEquals(5, store.channels.value.size)
            assertEquals("Hebrew, UK | Sport", store.sources.value.first().xtream!!.categories)
            assertTrue(store.channels.value.filter { it.group == "UK | Sport" }.all { it.source == src })
            compose.onAllNodesWithText("TNT Sports 1")[0].assertExists()   // now inside the kept category
            shot("4-kept")

            // Back to the categories: UK | Sport is one of mine now, and not offered under the UK any more
            compose.onAllNodesWithContentDescriptionCompat("Back to the categories"); idle()
            compose.onNodeWithText("UK | Sport").assertExists()
            compose.onNodeWithText("United Kingdom").performClick(); idle()
            assertEquals(0, compose.onAllNodesWithText("UK | Sport").fetchSemanticsNodes().size)
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onAllNodesWithContentDescriptionCompat(d: String) =
    onAllNodes(androidx.compose.ui.test.hasContentDescription(d))[0].performClick()
