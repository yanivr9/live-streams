package com.streamdesk.app

import android.annotation.SuppressLint
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.Manifest
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.material.icons.filled.Cast
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class Status(val text: String, val isError: Boolean = false)

private data class Stats(
    val format: String = "–",
    val resolution: String = "–",
    val codec: String = "–",
    val bitrate: String = "–",
    val buffer: String = "–",
    val dropped: String = "–",
    val latency: String = "–",
)

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private lateinit var store: ChannelStore
    private lateinit var player: ExoPlayer
    private lateinit var http: DefaultHttpDataSource.Factory
    private lateinit var mediaSources: DefaultMediaSourceFactory
    private val handler = Handler(Looper.getMainLooper())

    private var queue: List<Channel> = emptyList()
    private var index = 0
    private var requestedFormat = StreamFormat.Auto
    private var detectedFormat = StreamFormat.Auto
    private var tryingFormat = StreamFormat.Auto
    private var retries = 0
    private var busyRetries = 0
    private var streamOpen = false   // a stream is open (or opening) with the provider
    private var clientAttempt = 0   // index into CLIENT_USER_AGENTS for channels without their own
    private var reachedReady = false
    private var droppedFrames = 0
    private var pausedByStop = false
    private var videoOffInBackground = false   // the sound plays on in the background, so the picture was switched off
    private var closedInBackground = false   // a live stream was closed (not just paused) when the app left the screen

    // Compose state
    private var current by mutableStateOf<Channel?>(null)
    private var status by mutableStateOf<Status?>(null)
    private var stats by mutableStateOf(Stats())
    private var isLive by mutableStateOf(false)
    private var inPip by mutableStateOf(false)
    private var controlsVisible by mutableStateOf(true)
    private var showStats by mutableStateOf(false)
    private val isTv by lazy { isTelevision() }
    private var listOpen by mutableStateOf(false)   // TV: the channel list opened with OK

    // Phone: the TV this phone is currently controlling ("Play on TV"), and the code it was paired with
    private var castTarget by mutableStateOf<TvDevice?>(null)
    private var castCode = ""
    private var showTvPicker by mutableStateOf(false)
    private var banner by mutableStateOf<String?>(null)   // "12 · channel name", shown briefly after switching channel
    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner = null }
    private val typedNumber = NumberEntry()
    private val goToTypedNumber = Runnable { commitTypedNumber() }
    private var hasQualities by mutableStateOf(false)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen stays on for as long as the player is open, whatever the phone's screen timeout is
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = ChannelStore.get(this)

        http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(VLC_USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        mediaSources = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSources)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)   // keeps the CPU and Wi-Fi awake while a stream plays with the screen off
            .build()
        // Always start on the best picture the stream and this phone's decoder offer, not whatever the connection speed suggests
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(true)
            .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, false)
            .build()
        player.addListener(playerListener)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                AppLog.i("player", "  video decoder: $decoderName")
            }

            override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
                AppLog.e("player", "  video decoder problem", videoCodecError)
            }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                this@PlayerActivity.droppedFrames += droppedFrames
            }
        })

        if (!isTv) {
            // The Stop button on the "playing in the background" notification
            lifecycleScope.launch { BackgroundAudio.stopRequests.collect { finish() } }
            if (Build.VERSION.SDK_INT >= 33 && BackgroundAudio.enabled(this) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (isTv) {   // commands from a phone paired with this TV
            lifecycleScope.launch {
                LinkBus.commands.collect { action ->
                    when (action) {
                        "next" -> zap(1)
                        "prev" -> zap(-1)
                        "stop" -> finish()
                    }
                }
            }
        }

        // Record when the phone turns its screen off, so a black screen can be told apart from a stopped video
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        goFullscreen()
        addOnPictureInPictureModeChangedListener { inPip = it.isInPictureInPictureMode }
        setContent { StreamDeskTheme(isTv = isTv) { PlayerScreen() } }

        if (!loadFromIntent(intent)) {
            finish()
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var seconds = 0
                var bufferingFor = 0
                while (true) {
                    updateStats()
                    bufferingFor = if (player.playbackState == Player.STATE_BUFFERING && castTarget == null) bufferingFor + 1 else 0
                    if (bufferingFor >= STALL_SECONDS && reachedReady && liveLike()) {
                        bufferingFor = 0
                        reconnectLive("frozen for $STALL_SECONDS s")
                    }
                    // A line every 30 s: if the picture goes black, the log shows whether playback was still running
                    if (++seconds % 30 == 0) {
                        AppLog.i("player", "  alive ${seconds}s: ${stateName(player.playbackState)}, playing=${player.isPlaying}, buffer ${stats.buffer}, dropped ${stats.dropped}")
                    }
                    delay(1000)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadFromIntent(intent)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLog.i("player", "  phone screen turned OFF (screen timeout, power button or lock)")
        }
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "state $state"
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppLog.i("player", "  screen-on flag set: ${window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0}")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) AppLog.i("player", "  window lost focus (a popup, notification shade or another app is on top)")
    }

    override fun onStart() {
        super.onStart()
        if (videoOffInBackground) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
            videoOffInBackground = false
        }
        if (pausedByStop) AppLog.i("player", "  app is back on screen; resuming")
        if (pausedByStop && closedInBackground) load() else if (pausedByStop) player.play()
        closedInBackground = false
        pausedByStop = false
    }

    override fun onStop() {
        super.onStop()
        AppLog.i("player", "  app left the screen (playing=${player.isPlaying}, picture-in-picture=$isInPictureInPictureMode)")
        // Keep playing in picture-in-picture; pause when the app really goes to the background
        if (!isInPictureInPictureMode && player.isPlaying) {
            if (!isTv && castTarget == null && BackgroundAudio.enabled(this)) {
                // The sound goes on; the picture isn't needed while nothing is looking at it
                AppLog.i("player", "  the sound keeps playing in the background")
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
                videoOffInBackground = true
                return
            }
            if (liveLike() && castTarget == null) {
                // A paused live stream still holds its connection, and so uses up one of the account's screens
                AppLog.i("player", "  closing the live stream because the app is in the background")
                handler.removeCallbacksAndMessages(null)
                player.stop()
                closedInBackground = true
            } else {
                AppLog.i("player", "  pausing because the app is in the background")
                player.pause()
            }
            pausedByStop = true
        }
    }

    override fun onDestroy() {
        if (isTv) NowPlaying.clear()
        handler.removeCallbacksAndMessages(null)
        bannerHandler.removeCallbacksAndMessages(null)
        typedNumber.clear()
        runCatching { unregisterReceiver(screenReceiver) }
        stopService(Intent(this, PlaybackService::class.java))
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP on its own via setAutoEnterEnabled
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player.isPlaying) enterPip()
    }

    // ---------- loading ----------

    private fun loadFromIntent(intent: Intent): Boolean {
        val passed = intent.getStringArrayListExtra(EXTRA_QUEUE)?.map { Channel.fromJson(JSONObject(it)) }
        val start: Int
        queue = when {
            !passed.isNullOrEmpty() -> {
                start = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(passed.indices)
                passed
            }
            intent.action == Intent.ACTION_VIEW && intent.data != null -> {
                start = 0
                listOf(channelFor(intent.data.toString()))
            }
            intent.action == Intent.ACTION_SEND -> {
                val link = intent.getStringExtra(Intent.EXTRA_TEXT)?.let { LINK.find(it)?.value } ?: return false
                start = 0
                listOf(channelFor(link))
            }
            else -> return false
        }
        requestedFormat = StreamFormat.entries.getOrElse(intent.getIntExtra(EXTRA_FORMAT, 0)) { StreamFormat.Auto }
        play(start)
        if (isTv) showBanner()
        return true
    }

    private fun channelFor(url: String) = store.find(url) ?: Channel(url, nameFromUrl(url))

    private fun play(i: Int) {
        index = i
        val ch = queue[i]
        if (isTv) NowPlaying.set(queue, i)
        startBackgroundService(ch.displayName())
        current = ch
        retries = 0
        busyRetries = 0
        clientAttempt = 0
        reachedReady = false
        droppedFrames = 0
        isLive = false
        // A quality chosen by hand on the previous channel doesn't carry over: every channel opens on its best
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        detectedFormat = detectFormat(ch.url)
        tryingFormat = if (requestedFormat != StreamFormat.Auto) requestedFormat else detectedFormat
        store.markPlayed(ch)
        AppLog.i("player", "Open \"${ch.name}\": ${ch.url}")
        // Switching channel: close the old stream first, and give the provider a moment to count it as closed.
        // A one-screen account otherwise sees two streams at once and refuses the new one (HTTP 458).
        // Pressing CH+ several times quickly only opens the channel you stop on.
        handler.removeCallbacksAndMessages(null)
        if (streamOpen) {
            player.stop()
            streamOpen = false
            status = Status("Switching…")
            handler.postDelayed({ load() }, SWITCH_GAP_MS)
        } else {
            load()
        }
    }

    /** Phone: lets the sound go on with the screen off. Started while the app is on screen, which Android requires. */
    private fun startBackgroundService(title: String) {
        if (isTv || castTarget != null || !BackgroundAudio.enabled(this)) return
        runCatching { ContextCompat.startForegroundService(this, PlaybackService.intent(this, title)) }
            .onFailure { AppLog.e("player", "Couldn't start the service that keeps the sound on", it) }
    }

    private fun load() {
        val ch = queue[index]
        handler.removeCallbacksAndMessages(null)
        val scheme = Uri.parse(ch.url).scheme?.lowercase().orEmpty()
        if (scheme in UNSUPPORTED_SCHEMES) {
            player.stop()
            status = Status("${scheme.uppercase()} links aren't supported yet. Re-stream it as HLS or RTSP, or open it in VLC.", isError = true)
            return
        }
        http.setUserAgent(ch.userAgent.ifBlank { CLIENT_USER_AGENTS[clientAttempt] })
        http.setDefaultRequestProperties(if (ch.referrer.isBlank()) emptyMap() else mapOf("Referer" to ch.referrer))

        status = Status("Connecting…")
        streamOpen = true
        AppLog.i("player", "  loading as ${tryingFormat.label} with ${agentName(ch.userAgent.ifBlank { CLIENT_USER_AGENTS[clientAttempt] })}")
        player.setMediaSource(buildSource(ch, tryingFormat))
        player.prepare()
        player.play()
    }

    private fun buildSource(ch: Channel, format: StreamFormat): MediaSource {
        val uri = Uri.parse(ch.url)
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.name).build())
            .setMimeType(
                when (format) {
                    StreamFormat.Hls -> MimeTypes.APPLICATION_M3U8
                    StreamFormat.Dash -> MimeTypes.APPLICATION_MPD
                    else -> null
                }
            )
            .build()
        return when (uri.scheme?.lowercase()) {
            "rtmp" -> ProgressiveMediaSource.Factory(RtmpDataSource.Factory()).createMediaSource(item)
            "rtsp" -> RtspMediaSource.Factory().createMediaSource(item)
            else ->
                if (format == StreamFormat.Direct) ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this, http)).createMediaSource(item)
                else mediaSources.createMediaSource(item)
        }
    }

    private fun zap(delta: Int) {
        if (queue.size < 2) return
        val tv = castTarget
        if (tv != null) {
            // The TV changes channel; this phone only follows along so "Play here" starts on the same one
            index = (index + delta + queue.size) % queue.size
            current = queue[index]
            sendToTv(tv, if (delta > 0) "next" else "prev")
            return
        }
        play((index + delta + queue.size) % queue.size)
        showBanner()
    }

    private fun sendToTv(tv: TvDevice, action: String) {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { LinkClient.command(tv, castCode, action) } }
                .onFailure { Toast.makeText(this@PlayerActivity, it.message ?: "Couldn't reach the TV.", Toast.LENGTH_LONG).show() }
        }
    }

    /** "Play here": the TV stops and says which channel it was on (it may have changed with its own remote). */
    private fun takeBack(tv: TvDevice) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { LinkClient.takeOver(tv, castCode) } }
            castTarget = null
            result.onSuccess { (q, i) ->
                queue = q
                play(i)
            }.onFailure {
                sendToTv(tv, "stop")
                play(index)
            }
        }
    }

    private fun playOnTv(tv: TvDevice, code: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { LinkClient.play(tv, code, queue, index) } }
            result.onSuccess {
                LinkPrefs.save(this@PlayerActivity, tv.host, code)
                castTarget = tv
                castCode = code
                stopService(Intent(this@PlayerActivity, PlaybackService::class.java))
                // Stop, don't pause: a paused stream keeps its connection to the provider, which counts as a second screen
                handler.removeCallbacksAndMessages(null)
                player.stop()
            }.onFailure { Toast.makeText(this@PlayerActivity, it.message ?: "Couldn't reach the TV.", Toast.LENGTH_LONG).show() }
        }
    }

    /** A number key on the remote: wait a moment for more digits (or OK), then go to that channel. */
    private fun typeNumber(digit: Int) {
        typedNumber.press(digit)
        banner = "Channel ${typedNumber.text}_"
        bannerHandler.removeCallbacks(hideBanner)
        bannerHandler.removeCallbacks(goToTypedNumber)
        bannerHandler.postDelayed(goToTypedNumber, 1500)
    }

    private fun commitTypedNumber() {
        bannerHandler.removeCallbacks(goToTypedNumber)
        val n = typedNumber.take() ?: return
        if (n in 1..queue.size) {
            if (n - 1 != index) play(n - 1)
            showBanner()
        } else {
            banner = "No channel $n (1–${queue.size})"
            bannerHandler.removeCallbacks(hideBanner)
            bannerHandler.postDelayed(hideBanner, 2500)
        }
    }

    private fun showBanner() {
        banner = "${index + 1} / ${queue.size}  ·  ${queue[index].displayName()}"
        bannerHandler.removeCallbacks(hideBanner)
        bannerHandler.postDelayed(hideBanner, if (isTv) 4000 else 2500)
    }

    // TV remote: up/down change channel, OK opens the channel list (or retries after an error), Menu opens quality or stream info.
    // Overriding here (not onKeyDown) catches the keys before any view does; lint's restriction is meant for library authors.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isTv && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            AppLog.i("keys", "Remote key: ${KeyEvent.keyCodeToString(event.keyCode)}")   // to see which keys a TV's remote passes on
        }
        if (isTv && !listOpen && event.action == KeyEvent.ACTION_DOWN) {
            val digit = NumberKeys.digit(event.keyCode)
            if (digit != null && queue.size > 1 && castTarget == null) {
                if (event.repeatCount == 0) typeNumber(digit)
                return true
            }
            val action = TvKeys.map(event.keyCode)
            if (action == TvAction.Select && typedNumber.text.isNotEmpty()) {
                if (event.repeatCount == 0) commitTypedNumber()
                return true
            }
            if (action != null) {
                if (event.repeatCount == 0) when (action) {
                    TvAction.ChannelUp -> zap(1)
                    TvAction.ChannelDown -> zap(-1)
                    TvAction.Select -> if (status?.isError == true) play(index) else if (queue.size > 1) listOpen = true else showBanner()
                    TvAction.Menu -> if (hasQualities) showQualityPicker() else showStats = !showStats
                    TvAction.ShowInfo -> showBanner()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // Channel up/down keys, as on a Bluetooth remote or keyboard
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { zap(1); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { zap(-1); true }
        else -> super.onKeyDown(keyCode, event)
    }

    // ---------- errors & reconnect ----------

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val why = when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "you"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "another app took the audio"
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "headphones or Bluetooth disconnected"
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "a remote control"
                else -> "reason $reason"
            }
            AppLog.i("player", "  ${if (playWhenReady) "play" else "pause"} requested by $why")
        }

        override fun onPlaybackStateChanged(state: Int) {
            AppLog.i("player", "  ${stateName(state)} at ${player.currentPosition / 1000}s")
            when (state) {
                Player.STATE_READY -> {
                    if (!reachedReady) {
                        val v = player.videoFormat
                        AppLog.i("player", "  playing: ${v?.width ?: 0}×${v?.height ?: 0} ${v?.sampleMimeType ?: "no video"}, live=${player.isCurrentMediaItemLive}")
                    }
                    status = null
                    retries = 0
                    reachedReady = true
                }
                Player.STATE_ENDED -> if (liveLike()) reconnectLive("the stream ended") else status = Status("Finished playing.")
                else -> Unit
            }
            isLive = liveLike()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            runCatching { setPictureInPictureParams(pipParams()) }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            hasQualities = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.length > 1 }
            val sizes = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it) } }
                .filter { it.height > 0 }.map { "${it.width}×${it.height}" }.distinct()
            if (sizes.isNotEmpty()) AppLog.i("player", "  video quality available: ${sizes.joinToString(", ")}")
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private fun handleError(e: PlaybackException) {
        val code = e.errorCode
        AppLog.e("player", "  ${e.errorCodeName}" + (httpErrorCode(e)?.let { " (HTTP $it)" } ?: ""), e.cause ?: e)

        // Live stream moved past our position: jump back to the live edge
        if (code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }

        // The link has no telling extension: try it as HLS, then DASH
        if (requestedFormat == StreamFormat.Auto && detectedFormat == StreamFormat.Auto && code in PARSING_ERRORS && tryingFormat != StreamFormat.Dash) {
            tryingFormat = if (tryingFormat == StreamFormat.Hls) StreamFormat.Dash else StreamFormat.Hls
            AppLog.i("player", "  not a direct file; trying as ${tryingFormat.label}")
            load()
            return
        }

        // The server refused this client: try again under another common player's name
        val httpCode = httpErrorCode(e)
        if (httpCode in REFUSED_CODES && queue[index].userAgent.isBlank() && clientAttempt < CLIENT_USER_AGENTS.lastIndex) {
            clientAttempt++
            AppLog.i("player", "  server refused the client; retrying as ${agentName(CLIENT_USER_AGENTS[clientAttempt])}")
            load()
            return
        }

        // The account's screen limit: another screen may still be closing its stream, which takes a few seconds
        if (httpCode in ACCOUNT_BUSY_CODES && busyRetries < BUSY_MAX_RETRIES) {
            busyRetries++
            AppLog.i("player", "  account busy (HTTP $httpCode); trying again ($busyRetries of $BUSY_MAX_RETRIES)")
            status = Status("Your account is in use on another screen. Trying again ($busyRetries of $BUSY_MAX_RETRIES)…")
            player.stop()
            handler.postDelayed({ load() }, if (busyRetries == 1) SWITCH_GAP_MS * 2 else BUSY_RETRY_MS)
            return
        }

        // A stream that was playing dropped: reconnect with backoff. Live TV gets many more tries than a movie.
        val live = liveLike()
        if (reachedReady && live && (code in NETWORK_ERRORS || code in PARSING_ERRORS)) {
            reconnectLive(e.errorCodeName)
            return
        }
        if (reachedReady && code in NETWORK_ERRORS && retries < MAX_RETRIES) {
            retries++
            AppLog.i("player", "  connection lost; reconnecting ($retries of $MAX_RETRIES)")
            status = Status("Connection lost. Reconnecting ($retries of $MAX_RETRIES)…")
            handler.postDelayed({ player.prepare() }, Recovery.delayMs(retries))
            return
        }

        status = Status(explain(e), isError = true)
    }

    /** Is this a live stream (as opposed to a movie)? Providers' .ts streams don't announce that themselves. */
    private fun liveLike(): Boolean =
        player.isCurrentMediaItemLive || queue.getOrNull(index)?.let { looksLikeLiveStream(it.url, it.kind) } == true

    /** A live stream stopped (the provider closed it, the network blinked, or it froze): open it again, with growing pauses. */
    private fun reconnectLive(reason: String) {
        if (retries >= LIVE_MAX_RETRIES) {
            AppLog.e("player", "  gave up after $LIVE_MAX_RETRIES reconnects ($reason)")
            status = Status("The stream keeps stopping. Press OK or tap Try again, or check the internet connection.", isError = true)
            return
        }
        retries++
        AppLog.i("player", "  $reason; reconnecting ($retries of $LIVE_MAX_RETRIES)")
        status = Status("Connection lost. Reconnecting…")
        handler.postDelayed({ load() }, Recovery.delayMs(retries))
    }

    private fun explain(e: PlaybackException): String {
        val httpCode = httpErrorCode(e)
        val code = e.errorCode
        return when {
            httpCode != null -> httpProblem(httpCode).replaceFirstChar { it.uppercase() } + "."
            code in NETWORK_ERRORS -> "Can't reach the server. Check the link and your internet connection."
            code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "That file doesn't exist."
            code in PARSING_ERRORS ->
                "This link doesn't look like a playable stream. If you know its format, pick HLS or DASH on the main screen and try again."
            code in DECODER_ERRORS -> "This device can't decode the stream's video format."
            code in 6000..6999 -> "This stream is DRM-protected and can't be played here."
            else -> "Playback failed (${e.errorCodeName})."
        }
    }

    private fun httpErrorCode(e: PlaybackException): Int? =
        generateSequence(e.cause) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

    // ---------- stats ----------

    private fun updateStats() {
        if (current == null) return
        val f = player.videoFormat
        val size = player.videoSize
        val scheme = Uri.parse(current!!.url).scheme?.lowercase()
        val fmt = when {
            scheme == "rtsp" -> "RTSP"
            scheme == "rtmp" -> "RTMP"
            player.currentManifest is HlsManifest -> "HLS"
            player.currentManifest is DashManifest -> "DASH"
            player.playbackState == Player.STATE_IDLE -> "–"
            else -> "Direct"
        }
        val liveOffset = player.currentLiveOffset
        stats = Stats(
            format = fmt,
            resolution = if (size.width > 0) "${size.width}×${size.height}" else "–",
            codec = f?.codecs ?: f?.sampleMimeType?.substringAfter('/') ?: "–",
            bitrate = f?.bitrate?.takeIf { it > 0 }?.let(::formatBitrate) ?: "–",
            buffer = "%.1f s".format(player.totalBufferedDuration / 1000.0),
            dropped = droppedFrames.toString(),
            latency = if (player.isCurrentMediaItemLive && liveOffset != C.TIME_UNSET) "%.1f s".format(liveOffset / 1000.0) else "–",
        )
        isLive = liveLike()
    }

    // ---------- window ----------

    private fun goFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun pipParams(): PictureInPictureParams {
        val s = player.videoSize
        val ratio = if (s.width > 0 && s.height > 0) s.width.toFloat() / s.height else 16f / 9
        // Android rejects aspect ratios outside 1:2.39 – 2.39:1
        val clamped = ratio.coerceIn(1 / 2.39f, 2.39f)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((clamped * 1000).toInt(), 1000))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(true) }
            .build()
    }

    private fun enterPip() {
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    private fun showQualityPicker() {
        TrackSelectionDialogBuilder(this, "Video quality", player, C.TRACK_TYPE_VIDEO)
            .setShowDisableOption(false)
            .setAllowAdaptiveSelections(true)
            .build()
            .show()
    }

    // ---------- UI ----------

    @Composable
    private fun PlayerScreen() {
        val channels by store.channels.collectAsState()
        val favorite = channels.any { it.url == current?.url && it.favorite }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = this@PlayerActivity.player
                        if (isTv) {   // the remote is handled by the activity, not by the view
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setShowSubtitleButton(true)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                            controlsVisible = v == View.VISIBLE
                        })
                    }
                },
                update = { it.useController = !inPip && !isTv },
                modifier = Modifier.fillMaxSize(),
            )

            if (inPip) return@Box

            BackHandler(enabled = listOpen) { listOpen = false }

            if (!isTv) AnimatedVisibility(
                visible = controlsVisible || status?.isError == true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopBar(favorite)
            }

            if (!isTv && queue.size > 1) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                        .padding(end = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ChannelButton("+", "Next channel") { zap(1) }
                        ChannelButton("−", "Previous channel") { zap(-1) }
                    }
                }
            }

            banner?.let { text ->
                Text(
                    text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        .padding(top = 96.dp, start = 16.dp, end = 16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            status?.let { s ->
                StatusCard(s, Modifier.align(Alignment.Center))
            }

            if (isTv && listOpen) {
                ChannelPicker(Modifier.align(Alignment.CenterStart))
            }

            castTarget?.let { tv ->
                CastPanel(tv, Modifier.fillMaxSize())
            }

            if (showTvPicker) {
                TvPickerDialog(
                    title = "Play on TV",
                    onDismiss = { showTvPicker = false },
                    onChosen = { tv, code -> showTvPicker = false; playOnTv(tv, code) },
                )
            }

            if (showStats) {
                StatsPanel(
                    Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Top))
                        .padding(top = 72.dp, end = 12.dp)
                )
            }
        }
    }

    /** Phone: while a TV plays, this screen is its remote control. */
    @Composable
    private fun CastPanel(tv: TvDevice, modifier: Modifier) {
        Column(
            modifier.background(Color.Black.copy(alpha = 0.92f)).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Cast, contentDescription = null, tint = Amber, modifier = Modifier.size(48.dp))
            Text("Playing on ${tv.name}", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                current?.displayName().orEmpty(),
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            if (queue.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ChannelButton("−", "Previous channel on the TV") { zap(-1) }
                    ChannelButton("+", "Next channel on the TV") { zap(1) }
                }
            }
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    sendToTv(tv, "stop")
                    castTarget = null
                }) { Text("Stop the TV") }
                Button(onClick = { takeBack(tv) }) { Text("Play here") }
            }
            Text(
                "Back leaves the TV playing.",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    /** TV: the channel list, opened with OK. Up/down to choose, OK to switch, Back to close. */
    @Composable
    private fun ChannelPicker(modifier: Modifier) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 3).coerceAtLeast(0))
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { focus.requestFocus() }
        }
        Column(modifier.width(460.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.94f)).padding(vertical = 20.dp)) {
            Text(
                "Channels",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(queue) { i, ch ->
                    var focused by remember { mutableStateOf(false) }
                    val now = i == index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .then(if (now) Modifier.focusRequester(focus) else Modifier)
                            .onFocusChanged { focused = it.isFocused }
                            .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable {
                                listOpen = false
                                if (i != index) play(i)
                                showBanner()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            if (i == index) "▶" else "%02d".format(i + 1),
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                focused -> if (now) FocusAmberDark else FocusInkSoft
                                now -> Amber
                                else -> Color.White.copy(alpha = 0.75f)
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.displayName(),
                                color = when {
                                    focused -> if (now) FocusAmberDark else FocusInk
                                    now -> Amber
                                    else -> Color.White
                                },
                                fontWeight = if (now || focused) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (ch.group.isNotBlank()) Text(ch.group, color = if (focused) FocusInkSoft else Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /** A big round channel up/down button, easy to hit while watching. */
    @Composable
    private fun ChannelButton(sign: String, description: String, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            modifier = Modifier.size(68.dp).semantics { contentDescription = description },
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CH", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Text(sign, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
            }
        }
    }

    @Composable
    private fun TopBar(favorite: Boolean) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = ::finish) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    current?.displayName().orEmpty(),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Text(
                            "LIVE",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(OnAir, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    if (queue.size > 1) {
                        Text(
                            "${index + 1} / ${queue.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            if (queue.size > 1) {
                IconButton(onClick = { zap(-1) }) { Icon(Icons.Default.SkipPrevious, "Previous channel", tint = Color.White) }
                IconButton(onClick = { zap(1) }) { Icon(Icons.Default.SkipNext, "Next channel", tint = Color.White) }
            }
            IconButton(onClick = { current?.let(store::toggleFavorite) }) {
                if (favorite) Icon(Icons.Default.Star, "Remove from favorites", tint = Amber)
                else Icon(Icons.Outlined.StarBorder, "Add to favorites", tint = Color.White)
            }
            if (hasQualities) {
                IconButton(onClick = ::showQualityPicker) { Icon(Icons.Default.HighQuality, "Video quality", tint = Color.White) }
            }
            IconButton(onClick = { showStats = !showStats }) {
                Icon(Icons.Outlined.QueryStats, "Stream info", tint = if (showStats) Amber else Color.White)
            }
            IconButton(onClick = { showTvPicker = true }) { Icon(Icons.Default.Cast, "Play on TV", tint = Color.White) }
            IconButton(onClick = ::enterPip) { Icon(Icons.Default.PictureInPictureAlt, "Picture-in-picture", tint = Color.White) }
        }
    }

    @Composable
    private fun StatusCard(s: Status, modifier: Modifier) {
        if (!s.isError) {
            Text(
                s.text,
                color = Color.White,
                modifier = modifier.padding(top = 96.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            return
        }
        Surface(
            modifier = modifier.padding(24.dp).widthIn(max = 420.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Can't play this stream", style = MaterialTheme.typography.titleMedium, color = OnAir)
                Text(s.text, style = MaterialTheme.typography.bodyMedium)
                if (isTv) Text("Press OK to try again, or ▲ ▼ to change channel.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { play(index) }) { Text("Try again") }
                    if (queue.size > 1) OutlinedButton(onClick = { zap(1) }) { Text("Next channel") }
                }
            }
        }
    }

    @Composable
    private fun StatsPanel(modifier: Modifier) {
        val rows = listOf(
            "Format" to stats.format,
            "Resolution" to stats.resolution,
            "Codec" to stats.codec,
            "Bitrate" to stats.bitrate,
            "Buffer" to stats.buffer,
            "Dropped frames" to stats.dropped,
            "Behind live" to stats.latency,
        )
        Column(
            modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            rows.forEach { (label, value) ->
                Row {
                    Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(min = 104.dp))
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(value, color = Color.White, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_QUEUE = "queue"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_FORMAT = "format"
        private const val MAX_RETRIES = 5
        private const val LIVE_MAX_RETRIES = 12
        private val ACCOUNT_BUSY_CODES = setOf(458, 429)   // providers answer this when the account's screen limit is reached
        private const val BUSY_MAX_RETRIES = 4
        private const val BUSY_RETRY_MS = 4_000L
        private const val SWITCH_GAP_MS = 1_200L
        private const val STALL_SECONDS = 25

        private val LINK = Regex("""(https?|rtsp|rtmp)://\S+""", RegexOption.IGNORE_CASE)
        private val UNSUPPORTED_SCHEMES = setOf("udp", "rtp", "srt", "mms", "rtmps")

        private val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )
        private val PARSING_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        )
        private val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )

        fun formatBitrate(bps: Int): String =
            if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1e6) else "${bps / 1000} kbps"

        fun intent(context: Context, queue: List<Channel>, index: Int, format: StreamFormat): Intent =
            Intent(context, PlayerActivity::class.java)
                .putStringArrayListExtra(EXTRA_QUEUE, ArrayList(queue.map { it.toJson().toString() }))
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_FORMAT, format.ordinal)
    }
}
