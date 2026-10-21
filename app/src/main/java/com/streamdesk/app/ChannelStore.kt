package com.streamdesk.app

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/** Saved channels, playlist links and play history, kept as JSON files in app storage. */
class ChannelStore private constructor(context: Context) {
    private val dir = context.filesDir
    // Lists can hold tens of thousands of channels, so files are written off the main thread, in order.
    private val writer = Executors.newSingleThreadExecutor()

    private val _channels = MutableStateFlow(readList(FILE_CHANNELS, Channel::fromJson) ?: legacyChannels(context) ?: SAMPLES)
    val channels: StateFlow<List<Channel>> = _channels

    private val _recent = MutableStateFlow(readList(FILE_RECENT, Channel::fromJson).orEmpty())
    val recent: StateFlow<List<Channel>> = _recent

    private val _sources = MutableStateFlow(readList(FILE_SOURCES, PlaylistSource::fromJson).orEmpty())
    val sources: StateFlow<List<PlaylistSource>> = _sources

    fun find(url: String): Channel? = _channels.value.firstOrNull { it.url == url }

    /** Adds channels whose links aren't saved yet. Returns how many were added. */
    fun addAll(list: List<Channel>): Int {
        val known = _channels.value.mapTo(HashSet()) { it.url }
        val fresh = list.filter { known.add(it.url) }
        if (fresh.isNotEmpty()) update { it + fresh }
        return fresh.size
    }

    fun toggleFavorite(channel: Channel) {
        if (find(channel.url) == null) addAll(listOf(channel.copy(favorite = true)))
        else update { list -> list.map { if (it.url == channel.url) it.copy(favorite = !it.favorite) else it } }
    }

    fun rename(url: String, name: String) =
        update { list -> list.map { if (it.url == url) it.copy(name = name) else it } }

    fun remove(url: String) = update { list -> list.filterNot { it.url == url } }

    fun markPlayed(channel: Channel) {
        _recent.value = (listOf(channel.copy(favorite = false)) + _recent.value.filterNot { it.url == channel.url }).take(MAX_RECENT)
        save(FILE_RECENT, _recent.value, Channel::toJson)
    }

    fun removeRecent(url: String) {
        _recent.value = _recent.value.filterNot { it.url == url }
        save(FILE_RECENT, _recent.value, Channel::toJson)
    }

    fun clearRecent() {
        _recent.value = emptyList()
        save(FILE_RECENT, emptyList(), Channel::toJson)
    }

    // ---------- playlist links and logins ----------

    // Downloads run here, not in a screen's scope, so leaving or rebuilding a screen never cancels them
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _progress = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Playlists loading right now, with what is happening (playlist url → status text). */
    val progress: StateFlow<Map<String, String>> = _progress

    private val _events = MutableSharedFlow<StoreEvent>(extraBufferCapacity = 16)
    /** Results to tell the user about. */
    val events: SharedFlow<StoreEvent> = _events

    /** The built-in demo channels are no use once the user has loaded a real list. */
    private fun removeSamples() = update { list -> list.filterNot { c -> c.source.isEmpty() && !c.favorite && SAMPLES.any { it.url == c.url } } }

    private fun setProgress(url: String, text: String?) =
        _progress.update { if (text == null) it - url else it + (url to text) }

    /** Downloads a playlist link, or refreshes a saved playlist or login, in the background. */
    fun startPlaylist(url: String, name: String? = null, prefetched: Fetched? = null, quiet: Boolean = false) {
        if (url in _progress.value) return
        val existing = _sources.value.firstOrNull { it.url == url }
        if (existing?.xtream != null) {
            runXtream(existing.copy(name = name ?: existing.name), quiet, isNew = false)
            return
        }
        setProgress(url, "Downloading…")
        AppLog.i("playlist", "Start: $url")
        jobs.launch {
            try {
                val count = loadPlaylist(url, name, prefetched)
                AppLog.i("playlist", "Done: $count channels")
                removeSamples()
                if (!quiet) {
                    val msg = if (existing == null) "Playlist added: $count channels." else "Playlist updated: $count channels."
                    _events.emit(StoreEvent(msg, showSource = url.takeIf { existing == null }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.e("playlist", "Failed: ${describe(e)}", e)
                if (!quiet) _events.emit(StoreEvent("Couldn't load the playlist: ${describe(e)}."))
            } finally {
                setProgress(url, null)
            }
        }
    }

    /** Saves an IPTV login and signs in to it in the background. */
    fun startXtream(login: XtreamLogin, name: String?, enabled: Boolean, updateHours: Int, replacing: String?) {
        val existing = _sources.value.firstOrNull { it.url == login.sourceUrl }
            ?: _sources.value.firstOrNull { it.url == replacing }?.copy(url = login.sourceUrl)
        val host = runCatching { java.net.URI(login.base).host }.getOrNull() ?: login.server
        val source = (existing ?: PlaylistSource(login.sourceUrl, "", xtream = login)).copy(
            xtream = login,
            name = name?.takeIf { it.isNotBlank() } ?: existing?.name?.takeIf { it.isNotBlank() } ?: "${login.username} · $host",
            enabled = enabled,
            updateHours = updateHours,
            error = "",
        )
        // Server, username or password changed: the old entry and its channels make way for the new one
        if (replacing != null && replacing != source.url) removePlaylist(replacing)
        // Listed straight away, so the account shows up while it loads and stays (with the reason) if signing in fails
        putSource(source)
        if (!enabled) {
            _events.tryEmit(StoreEvent("Saved. The playlist is turned off."))
            return
        }
        runXtream(source, quiet = false, isNew = existing == null || replacing != null)
    }

    private fun runXtream(source: PlaylistSource, quiet: Boolean, isNew: Boolean) {
        val url = source.url
        if (url in _progress.value) return
        setProgress(url, "Signing in…")
        AppLog.i("signin", "Start: \"${source.name}\" at ${source.xtream?.base} (channels ${source.xtream?.live}, movies ${source.xtream?.movies}, series ${source.xtream?.series})")
        jobs.launch {
            try {
                val count = loadXtream(source) { setProgress(url, it) }
                AppLog.i("signin", "Done: \"${source.name}\" has $count items")
                removeSamples()
                if (!quiet) _events.emit(StoreEvent("Signed in: $count items.", showSource = url.takeIf { isNew }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val msg = describe(e)
                AppLog.e("signin", "Failed: \"${source.name}\": $msg", e)
                putSource((_sources.value.firstOrNull { it.url == url } ?: source).copy(error = msg))
                if (!quiet) _events.emit(StoreEvent("Couldn't sign in: $msg."))
            } finally {
                setProgress(url, null)
            }
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is PlaylistException, is IOException -> e.message ?: "network error"
        is OutOfMemoryError -> "the list is too big for this phone's memory"
        else -> "unexpected error (${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""})"
    }

    /** Downloads a playlist link and adds or replaces its channels. Returns the channel count. */
    private suspend fun loadPlaylist(url: String, name: String?, prefetched: Fetched?): Int {
        val existing = _sources.value.firstOrNull { it.url == url }
        val source = existing?.copy(name = name ?: existing.name)
            ?: PlaylistSource(url, name?.takeIf { it.isNotBlank() } ?: playlistName(url))
        var fetched: Fetched? = prefetched
        val parsed = try {
            val got = prefetched ?: fetchText(url, existing?.userAgent?.ifBlank { null })
            fetched = got
            withContext(Dispatchers.Default) {
                // If the server only accepted another client name, its channels likely need it too
                M3u.parse(got.text).map { if (it.userAgent.isBlank() && got.userAgent != VLC_USER_AGENT) it.copy(userAgent = got.userAgent) else it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "network error"
            if (existing != null) putSource(existing.copy(error = msg))
            throw PlaylistException(msg)
        }
        if (parsed.isEmpty()) {
            val msg = "no stream links found in it"
            if (existing != null) putSource(existing.copy(error = msg))
            throw PlaylistException(msg)
        }
        val count = withContext(Dispatchers.Default) { replaceSourceChannels(url, parsed) }
        putSource(source.copy(channelCount = count, updatedAt = System.currentTimeMillis(), error = "", userAgent = fetched?.userAgent.orEmpty()))
        return count
    }

    /** Hides or shows a playlist's channels without deleting them. */
    fun setEnabled(url: String, enabled: Boolean) {
        _sources.value = _sources.value.map { if (it.url == url) it.copy(enabled = enabled) else it }
        save(FILE_SOURCES, _sources.value, PlaylistSource::toJson)
    }

    /**
     * Signs in and loads live channels, movies and series one after another. Each part appears in the list as soon
     * as it arrives; a part that fails keeps what it had before. Returns the item count.
     */
    private suspend fun loadXtream(source: PlaylistSource, onProgress: (String) -> Unit): Int {
        val url = source.url
        val login = source.xtream!!
        val old = _channels.value.filter { it.source == url }
        val pending = buildSet {
            if (login.live) add(Kind.LIVE)
            if (login.movies) add(Kind.MOVIE)
            if (login.series) add(Kind.SERIES)
        }.toMutableSet()
        val loaded = mutableListOf<Channel>()
        val result = Xtream.load(login, login.userAgent.ifBlank { source.userAgent.ifBlank { null } }, onProgress) { kind, items ->
            loaded += items
            pending -= kind
            replaceSourceChannels(url, loaded + old.filter { it.kind in pending })
        }
        val count = withContext(Dispatchers.Default) {
            replaceSourceChannels(url, result.channels + old.filter { it.kind in result.failedKinds })
        }
        val latest = _sources.value.firstOrNull { it.url == url } ?: source
        putSource(
            latest.copy(
                channelCount = count,
                updatedAt = System.currentTimeMillis(),
                error = result.problems,
                userAgent = result.userAgent,
                account = result.account,
            )
        )
        return count
    }

    /** Removes a playlist link and its channels. Favorites are kept as hand-added channels. */
    fun removePlaylist(url: String) {
        update { list -> list.mapNotNull { if (it.source != url) it else if (it.favorite) it.copy(source = "") else null } }
        _sources.value = _sources.value.filterNot { it.url == url }
        save(FILE_SOURCES, _sources.value, PlaylistSource::toJson)
    }

    fun renamePlaylist(url: String, name: String) {
        _sources.value = _sources.value.map { if (it.url == url) it.copy(name = name) else it }
        save(FILE_SOURCES, _sources.value, PlaylistSource::toJson)
    }

    /** Swaps a playlist's channels for a fresh copy in the same place in the list, keeping favorites. */
    private fun replaceSourceChannels(url: String, parsed: List<Channel>): Int {
        var count = 0
        update { current ->
            val favorites = current.filter { it.source == url && it.favorite }.mapTo(HashSet()) { it.url }
            val others = current.filterNot { it.source == url }
            val taken = others.mapTo(HashSet()) { it.url }
            val fresh = parsed.filter { taken.add(it.url) }.map { it.copy(source = url, favorite = it.url in favorites) }
            count = fresh.size
            val firstOld = current.indexOfFirst { it.source == url }
            val at = if (firstOld < 0) others.size else current.subList(0, firstOld).count { it.source != url }
            others.subList(0, at) + fresh + others.subList(at, others.size)
        }
        return count
    }

    private fun putSource(source: PlaylistSource) {
        val list = _sources.value
        _sources.value = if (list.any { it.url == source.url }) list.map { if (it.url == source.url) source else it } else list + source
        save(FILE_SOURCES, _sources.value, PlaylistSource::toJson)
    }

    // ---------- storage ----------

    @Synchronized
    private fun update(change: (List<Channel>) -> List<Channel>) {
        _channels.value = change(_channels.value)
        save(FILE_CHANNELS, _channels.value, Channel::toJson)
    }

    private fun <T> save(name: String, list: List<T>, toJson: (T) -> JSONObject) {
        writer.execute {
            val arr = JSONArray()
            list.forEach { arr.put(toJson(it)) }
            val file = AtomicFile(File(dir, name))
            val out = file.startWrite()
            try {
                out.write(arr.toString().toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    private fun <T> readList(name: String, fromJson: (JSONObject) -> T): List<T>? {
        val file = AtomicFile(File(dir, name))
        if (!file.baseFile.exists()) return null
        return runCatching {
            val arr = JSONArray(String(file.readFully()))
            List(arr.length()) { fromJson(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    /** Channels saved by version 1.0, which kept them in SharedPreferences. */
    private fun legacyChannels(context: Context): List<Channel>? {
        val json = context.getSharedPreferences("channels", Context.MODE_PRIVATE).getString("channels", null) ?: return null
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { Channel.fromJson(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    companion object {
        private const val FILE_CHANNELS = "channels.json"
        private const val FILE_RECENT = "recent.json"
        private const val FILE_SOURCES = "playlists.json"
        private const val MAX_RECENT = 30

        // Public test streams shown on first launch; the user can remove them.
        private val SAMPLES = listOf(
            Channel("https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8", "Akamai live test", "Samples"),
            Channel("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "Big Buck Bunny (HLS)", "Samples"),
            Channel("https://dash.akamaized.net/envivio/EnvivioDash3/manifest.mpd", "Envivio (DASH)", "Samples"),
            Channel("https://media.w3.org/2010/05/sintel/trailer.mp4", "Sintel trailer (MP4)", "Samples"),
        )

        @Volatile private var instance: ChannelStore? = null

        fun get(context: Context): ChannelStore =
            instance ?: synchronized(this) { instance ?: ChannelStore(context.applicationContext).also { instance = it } }
    }
}

class PlaylistException(message: String) : Exception(message)

/** Something to tell the user; [showSource] asks the list to open that playlist's channels. */
data class StoreEvent(val message: String, val showSource: String? = null)
