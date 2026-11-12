package com.streamdesk.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Switch
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.DialogProperties
import android.text.format.DateUtils
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var invite by mutableStateOf<LinkInvite?>(null)   // set when the phone opens a TV's QR code link
    private var sharedLogin by mutableStateOf<Pair<XtreamLogin, String?>?>(null)   // an account from another phone's QR code

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // the system's logo screen, until the first frame below takes over
        super.onCreate(savedInstanceState)
        // On a phone the channels screen stays upright; the player still turns for full-screen video
        if (!isTelevision() && resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        val firstStart = savedInstanceState == null
        setContent {
            StreamDeskTheme(isTv = isTelevision()) {
                // The loading screen is the first thing drawn; the heavy part (reading the saved channels, building the
                // home screen) starts after that frame, so the screen is never just black while the app starts
                var home by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                var ready by remember { mutableStateOf(false) }
                val shownAt = remember { SystemClock.uptimeMillis() }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    val store = ChannelStore.get(this@MainActivity)
                    if (firstStart) importFromIntent(intent)
                    val crash = App.takeCrashReport(this@MainActivity)
                    if (firstStart && intent.action == Intent.ACTION_MAIN && Resume.enabled(this@MainActivity)) resumeLast(store)
                    home = { HomeScreen(store, ::openPlayer, crash, invite, { invite = null }, sharedLogin) { sharedLogin = null } }
                    withFrameNanos { }
                    // Long enough to be seen on a real start; at once when the screen is only rebuilt (turning the device)
                    if (firstStart) delay((LAUNCH_MIN_MS - (SystemClock.uptimeMillis() - shownAt)).coerceAtLeast(0))
                    ready = true
                }
                Box(Modifier.fillMaxSize()) {
                    home?.invoke()
                    AnimatedVisibility(visible = !ready, exit = fadeOut(tween(350))) { LaunchScreen() }
                }
            }
        }
    }

    // Same lint exception as the player: this is an app's own activity, not a library
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {   // Esc on a keyboard goes back, like the Back key
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) onBackPressedDispatcher.onBackPressed()
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isTelevision()) {
            AppLog.i("keys", "Remote key: ${android.view.KeyEvent.keyCodeToString(event.keyCode)}")
        }
        if (isTelevision() && HomeKeys.handler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFromIntent(intent)
    }

    /** Handles a .m3u file opened from a file manager or browser download. */
    private fun importFromIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "streamdesk" && uri.host == "login") {   // an account shared from another phone as a QR code
            val parsed = LoginQr.parse(uri.toString())
            if (parsed != null) sharedLogin = parsed
            else Toast.makeText(this, "That QR code doesn't hold a Stream Desk account.", Toast.LENGTH_LONG).show()
            return
        }
        if (uri.scheme == "streamdesk") {   // the link behind a TV's QR code
            val parsed = LinkInvite.parse(uri.toString())
            if (parsed != null) invite = parsed
            else Toast.makeText(this, "That isn't a valid link to a TV on your home network.", Toast.LENGTH_LONG).show()
            return
        }
        if (intent.action != Intent.ACTION_VIEW) return
        val text = runCatching { readText(this, uri) }.getOrNull()
        val msg = if (text == null) "Couldn't read that playlist file." else importText(ChannelStore.get(this), text)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    /** On a TV: straight to the channel watched last, with the Sport list as the channels to switch between. */
    private fun resumeLast(store: ChannelStore) {
        val last = store.recent.value.firstOrNull { it.kind == Kind.LIVE } ?: return
        val limit = DeviceQuality.cap(this)
        // A 4K channel watched before the TV's limit was set would open on a black screen again: stay on the channels instead
        if (SportList.qualityScore(last) > limit.maxScore) return
        val all = store.channels.value
        val sport = SportList.pick(all, SportSettingsStore.get(this).settings.value.limitedTo(limit))
        val (queue, index) = Resume.queue(last, listOf(sport, IsraelList.pick(all, limit)))
        openPlayer(queue, index, StreamFormat.Auto)
    }

    private fun openPlayer(queue: List<Channel>, index: Int, format: StreamFormat) {
        // Pass a window of the list so channel up/down works without hitting the Binder size limit
        val from = (index - 100).coerceAtLeast(0)
        val to = (index + 100).coerceAtMost(queue.size)
        AppLog.i("app", "Opening the player: ${queue.getOrNull(index)?.displayName() ?: "?"} (${queue.size} channels)")
        startActivity(PlayerActivity.intent(this, queue.subList(from, to), index - from, format))
    }
}

/** The shortest time the loading screen stays on screen when the app opens. */
private const val LAUNCH_MIN_MS = 1_400L

/** How long work the first screen doesn't need (guide, list refresh, update check) waits after the app opens. */
private const val STARTUP_QUIET_MS = 1_500L

private enum class Section(val label: String) {
    All("All"), Sport("Sport"), Israel("Israeli TV"), Favorites("Favorites"), Playlists("Playlists")
}

private sealed interface HomeDialog {
    data class Rename(val channel: Channel) : HomeDialog
    data class RenamePlaylist(val source: PlaylistSource) : HomeDialog
    data class RemovePlaylist(val source: PlaylistSource) : HomeDialog
    /** Adding channels; [linkFirst] opens on "Playlist link" instead of "Username & password". */
    data class AddPlaylist(val linkFirst: Boolean = false) : HomeDialog
    data object PasteText : HomeDialog
    data class LinkError(val link: String, val message: String) : HomeDialog
    /** Full-screen sign-in editor: [source] when editing a saved login, [prefill] for a new one. */
    data class EditLogin(val source: PlaylistSource?, val prefill: XtreamLogin?) : HomeDialog
    data class Series(val series: Channel) : HomeDialog
    data class Crash(val report: String) : HomeDialog
    data object Logs : HomeDialog
    data object About : HomeDialog
    data object Settings : HomeDialog
    data object EditSport : HomeDialog
    data object LinkPhone : HomeDialog
    data object Search : HomeDialog
    data class Invite(val invite: LinkInvite) : HomeDialog
    /** Showing an account as a QR code for another phone. */
    data class ShareQr(val source: PlaylistSource) : HomeDialog
    /** An account read from another phone's QR code, waiting for "Add account". */
    data class SharedLogin(val login: XtreamLogin, val name: String?) : HomeDialog
    data class PickTv(val job: TvJob) : HomeDialog
    data class Update(val info: UpdateInfo) : HomeDialog
    data class Guide(val channel: Channel, val login: XtreamLogin) : HomeDialog
    data class TvLogs(val tvName: String, val text: String) : HomeDialog
    data class Report(val text: String) : HomeDialog
}

/** Live TV / Movies / Series filter; Episode items only appear in Recent. */
private enum class KindFilter(val label: String, val kinds: Set<String>) {
    Live("Live TV", setOf(Kind.LIVE)),
    Movies("Movies", setOf(Kind.MOVIE)),
    Series("Series", setOf(Kind.SERIES, Kind.EPISODE)),
}

// Links that are clearly media files go straight to the player; anything else may be a channel list
private val MEDIA_FILE = Regex("""\.(mp4|m4v|mkv|webm|mov|avi|ts|mpd|mp3|aac|m4a|flv)$""", RegexOption.IGNORE_CASE)

private const val DAY_MS = 24 * 60 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    store: ChannelStore,
    onPlay: (List<Channel>, Int, StreamFormat) -> Unit,
    crashReport: String? = null,
    invite: LinkInvite? = null,
    onInviteDone: () -> Unit = {},
    sharedLogin: Pair<XtreamLogin, String?>? = null,
    onSharedLoginDone: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val channels by store.channels.collectAsState()
    val recent by store.recent.collectAsState()
    val sources by store.sources.collectAsState()
    val sportStore = remember { SportSettingsStore.get(context) }
    val sportSettings by sportStore.settings.collectAsState()

    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(StreamFormat.Auto) }
    // Opens on the sport channels when there are any, otherwise on the full list
    var section by rememberSaveable { mutableStateOf(if (channels.any { it.isSport() }) Section.Sport else Section.All) }
    var query by rememberSaveable { mutableStateOf("") }
    // Phone: search is a glass button in the top bar that opens into a glass search bar
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    BackHandler(enabled = searchOpen) { query = ""; searchOpen = false }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var sourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var kindFilter by rememberSaveable { mutableStateOf<KindFilter?>(null) }
    val progress by store.progress.collectAsState()   // playlists loading right now → status
    val loading = progress.keys
    var checkingLink by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    // The "enter a link" bar is only in the way once there's a playlist; ⋮ → Open a link brings it back
    var linkBarOpen by rememberSaveable { mutableStateOf(false) }
    val showLinkBar = sources.isEmpty() || linkBarOpen
    var dialog by remember { mutableStateOf<HomeDialog?>(crashReport?.let { HomeDialog.Crash(it) }) }

    fun notify(msg: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(msg)
    }

    fun loadPlaylist(link: String, name: String? = null, prefetched: Fetched? = null, quiet: Boolean = false) {
        if (!quiet && store.sources.value.none { it.url == link }) section = Section.Playlists
        store.startPlaylist(link, name, prefetched, quiet)
    }

    fun addLogin(login: XtreamLogin, name: String?, enabled: Boolean = true, updateHours: Int = 24, replacing: String? = null) {
        section = Section.Playlists
        store.startXtream(login, name, enabled, updateHours, replacing)
    }

    // Results of downloads and sign-ins, which run in the background
    LaunchedEffect(Unit) {
        store.events.collect { e ->
            if (e.showSource != null) {
                section = Section.All
                sourceFilter = e.showSource
                kindFilter = null
                group = null
                query = ""
            }
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(e.message, withDismissAction = true, duration = SnackbarDuration.Long)
            }
        }
    }

    // Keep playlists current, each on its own schedule
    LaunchedEffect(Unit) {
        delay(STARTUP_QUIET_MS)   // let the first screen draw before refreshing lists (slow TV boxes)
        val now = System.currentTimeMillis()
        store.sources.value
            .filter { it.enabled && it.updateHours > 0 && it.updatedAt < now - it.updateHours * DAY_MS / 24 }
            .forEach { loadPlaylist(it.url, quiet = true) }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val msg = runCatching { withContext(Dispatchers.IO) { readText(context, uri) } }
                .map { importText(store, it) }
                .getOrElse { "Couldn't read that file." }
            notify(msg)
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = M3u.export(channels)
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(text) }
                }
            }.isSuccess
            notify(if (ok) "Saved ${channels.size} channels." else "Couldn't save the file.")
        }
    }

    fun runTvJob(job: TvJob, device: TvDevice, code: String) {
        if (job is TvJob.Logs) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { LinkClient.logs(device, code) } }
                    .onSuccess { LinkPrefs.save(context, device.host, code); dialog = HomeDialog.TvLogs(device.name, it) }
                    .onFailure { notify(it.message ?: "Couldn't reach the TV.") }
            }
            return
        }
        if (job is TvJob.TakeOver) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { LinkClient.takeOver(device, code) } }
                    .onSuccess { (queue, index) -> LinkPrefs.save(context, device.host, code); onPlay(queue, index, StreamFormat.Auto) }
                    .onFailure { notify(it.message ?: "Couldn't reach the TV.") }
            }
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (job) {
                        TvJob.TakeOver, TvJob.Logs -> Unit
                        is TvJob.Play -> LinkClient.play(device, code, job.queue, job.index)
                        is TvJob.Login -> LinkClient.sendLogin(
                            device, code,
                            LoginRequest(job.source.xtream!!, job.source.name, job.source.enabled, job.source.updateHours, sportSettings),
                        )
                    }
                }
            }
            result.onSuccess {
                LinkPrefs.save(context, device.host, code)
                notify(if (job is TvJob.Login) "Login sent to ${device.name}." else "Playing on ${device.name}.")
            }.onFailure { notify(it.message ?: "Couldn't reach the TV.") }
        }
    }

    fun connectInvite(invite: LinkInvite, source: PlaylistSource?) {
        val device = TvDevice(invite.name, invite.host, invite.port)
        scope.launch {
            // "hello" checks the code without doing anything on the TV
            val check = withContext(Dispatchers.IO) { runCatching { LinkClient.command(device, invite.code, "hello") } }
            check.onSuccess {
                LinkPrefs.save(context, device.host, invite.code)
                if (source != null) runTvJob(TvJob.Login(source), device, invite.code)
                else notify("Connected to ${device.name}. Tap ⋮ on a channel, then Play on TV.")
            }.onFailure { notify(it.message ?: "Couldn't reach the TV.") }
        }
    }

    LaunchedEffect(invite) { invite?.let { dialog = HomeDialog.Invite(it) } }
    LaunchedEffect(sharedLogin) { sharedLogin?.let { (login, name) -> dialog = HomeDialog.SharedLogin(login, name); onSharedLoginDone() } }
    // + Add playlist → QR code: a picture of the QR code from the gallery
    val pickQr = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { LoginQr.readPicture(context, uri)?.let(LoginQr::decode) }.getOrNull()
            }
            val parsed = LoginQr.parse(found)
            if (parsed != null) dialog = HomeDialog.SharedLogin(parsed.first, parsed.second)
            else notify(if (found == null) "No QR code found in that picture." else "That QR code isn't a Stream Desk account.")
        }
    }
    // A new version, if there is one: ask once when the app opens
    LaunchedEffect(Unit) {
        delay(STARTUP_QUIET_MS * 2)
        val found = runCatching { Updates.check(context) }.getOrNull()
        if (found != null && dialog == null) dialog = HomeDialog.Update(found)
    }
    // Something went wrong since the last look: say so, with the way to send it
    LaunchedEffect(Unit) {
        val n = Problems.unseen(context)
        if (n == 0 || crashReport != null) return@LaunchedEffect
        delay(STARTUP_QUIET_MS)
        val what = if (n == 1) "a problem" else "$n problems"
        if (context.isTelevision()) {
            notify("Stream Desk had $what. To send the details, on your phone: ⋮ → Problems from TV.")
        } else {
            val r = snackbar.showSnackbar("Stream Desk had $what.", actionLabel = "Send report", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) dialog = HomeDialog.Report(Problems.report(context))
            else Problems.markSeen(context)
        }
    }
    var openLast by remember { mutableStateOf(Resume.enabled(context)) }
    var tileCount by remember { mutableStateOf(TileCount.load(context)) }
    var phoneGrid by remember { mutableStateOf(PhoneView.isGrid(context)) }
    var backgroundSound by remember { mutableStateOf(BackgroundAudio.enabled(context)) }

    fun playLink(link: String) = onPlay(listOf(store.find(link) ?: Channel(link, nameFromUrl(link))), 0, format)

    fun submitUrl() {
        val link = url.trim()
        if (!Regex("^(https?|rtsp|rtmp)://", RegexOption.IGNORE_CASE).containsMatchIn(link)) {
            notify("Enter a link starting with http://, https://, rtsp:// or rtmp://")
            return
        }
        if (XtreamLogin.isLoginLink(link)) {
            dialog = HomeDialog.EditLogin(null, XtreamLogin.fromLink(link))
            return
        }
        if (!link.startsWith("http", ignoreCase = true) || MEDIA_FILE.containsMatchIn(Uri.parse(link).path.orEmpty())) {
            playLink(link)
            return
        }
        // Look inside first: the link may be a channel list (whatever its name) or a single stream
        checkingLink = true
        scope.launch {
            val result = runCatching { sniffChannelList(link) }
            checkingLink = false
            result.fold(
                onSuccess = { fetched ->
                    if (fetched != null) {
                        url = ""
                        loadPlaylist(link, prefetched = fetched)
                    } else {
                        playLink(link)
                    }
                },
                onFailure = { e ->
                    if (e is HttpStatusException || mightBePlaylistLink(link)) {
                        dialog = HomeDialog.LinkError(link, e.message ?: "network error")
                    } else {
                        playLink(link)   // the player shows its own error
                    }
                },
            )
        }
    }

    val numbers = remember(channels) { channels.withIndex().associate { it.value.url to it.index + 1 } }
    val kindsPresent = remember(channels, sources) {
        val off = sources.filter { !it.enabled }.mapTo(HashSet()) { it.url }
        KindFilter.entries.filter { k -> channels.any { it.kind in k.kinds && it.source !in off } }
    }
    val turnedOff = remember(sources) { sources.filter { !it.enabled }.mapTo(HashSet()) { it.url } }
    val scoped = remember(channels, section, sourceFilter, kindFilter, turnedOff) {
        channels.filter {
            it.source !in turnedOff &&
            (section != Section.All || sourceFilter == null || it.source == sourceFilter) &&
                (kindFilter == null || it.kind in kindFilter!!.kinds)
        }
    }
    // The Sport tab: the channels chosen in the Sport list, each in its best quality
    var deviceQuality by remember { mutableStateOf(DeviceQuality.cap(context)) }
    var saveMobileData by remember { mutableStateOf(DeviceQuality.saveData(context)) }
    val onMobileData by rememberOnMobileData()
    // What the lists may pick right now: the chosen quality, lowered on mobile data when ☰ → Lower quality on mobile data is on
    val qualityNow = remember(deviceQuality, saveMobileData, onMobileData) { DeviceQuality.effective(context, deviceQuality, onMobileData) }
    var pipOnLeave by remember { mutableStateOf(PlayerPrefs.pipOnLeave(context)) }
    var channelButtons by remember { mutableStateOf(PlayerPrefs.channelButtons(context)) }
    val sportPicked = remember(scoped, sportSettings, qualityNow) { SportList.pick(scoped, sportSettings.limitedTo(qualityNow)) }
    fun loginFor(ch: Channel): XtreamLogin? = if (ch.kind == Kind.LIVE) sources.firstOrNull { it.url == ch.source }?.xtream else null
    fun playSport(ch: Channel) {
        val i = sportPicked.indexOfFirst { it.url == ch.url }
        if (i >= 0) onPlay(sportPicked, i, StreamFormat.Auto) else onPlay(listOf(ch), 0, StreamFormat.Auto)
    }
    val israelPicked = remember(scoped, qualityNow) { IsraelList.pick(scoped, qualityNow) }
    // The guide on every channel: each row or tile asks for its own now/next as it shows (see NowOn)
    val guides by NowOn.guides.collectAsState()
    val nowSeconds = rememberNowSeconds()
    val groups = remember(scoped, section) {
        if (section == Section.Sport || section == Section.Israel) emptyList() else scoped.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = remember(scoped, sportPicked, israelPicked, channels, recent, section, group, query, kindFilter) {
        val base = when (section) {
            Section.All -> scoped
            Section.Sport -> sportPicked
            Section.Israel -> israelPicked
            Section.Favorites -> scoped.filter { it.favorite }
            Section.Playlists -> emptyList()
        }
        base.filter {
            (group == null || it.group == group) &&
                (query.isBlank() || it.name.contains(query, true) || it.url.contains(query, true) || it.group.contains(query, true))
        }
    }
    val filterName = sources.firstOrNull { it.url == sourceFilter }?.name

    // On a TV the remote needs something selected from the start: the first channel
    val isTv = LocalIsTv.current
    val firstRowFocus = remember { FocusRequester() }
    // TV remote: the channel grid has its own selection (see HomeKeys); the tabs and buttons use Android's focus
    var tvInGrid by remember { mutableStateOf(true) }
    var tvSelected by remember { mutableIntStateOf(0) }
    var tvMenuSignal by remember { mutableIntStateOf(0) }
    var tvOkDown by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val tvTabs = remember { listOf(Section.Sport, Section.Israel, Section.Favorites, Section.All) }
    val tabRequesters = remember { List(tvTabs.size) { FocusRequester() } }
    LaunchedEffect(section, group, sourceFilter, query) { tvSelected = 0; tvInGrid = true }

    val isTvMenu = LocalIsTv.current
    // The ⋮ entries, shown as a menu on a TV and as a panel from the bottom on a phone
    val moreEntries: @Composable ColumnScope.() -> Unit = {
                            if (sources.isNotEmpty()) {
                                AppMenuItem(text = { Text("Update all playlists") }, onClick = {
                                    moreMenu = false; sources.forEach { loadPlaylist(it.url) }
                                })
                            }
                            AppMenuItem(text = { Text("Export as .m3u file") }, onClick = {
                                moreMenu = false; saveFile.launch("stream-desk.m3u")
                            })
                            AppMenuItem(text = { Text("Share channel list") }, onClick = {
                                moreMenu = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, M3u.export(channels))
                                context.startActivity(Intent.createChooser(send, "Share channel list"))
                            })
                            if (sources.isNotEmpty()) {
                                AppMenuItem(
                                    text = { Text(if (linkBarOpen) "Hide the link bar" else "Open a link") },
                                    onClick = { moreMenu = false; linkBarOpen = !linkBarOpen },
                                )
                            }
                            if (LocalIsTv.current) {
                                AppMenuItem(text = { Text("Link a phone") }, onClick = {
                                    moreMenu = false; dialog = HomeDialog.LinkPhone
                                })
                            }
                            if (!LocalIsTv.current) {
                                AppMenuItem(text = { Text("Continue from TV") }, onClick = {
                                    moreMenu = false; dialog = HomeDialog.PickTv(TvJob.TakeOver)
                                })
                                AppMenuItem(text = { Text("Send problem report") }, onClick = {
                                    moreMenu = false; dialog = HomeDialog.Report(Problems.report(context))
                                })
                                AppMenuItem(text = { Text("Problems from TV") }, onClick = {
                                    moreMenu = false; dialog = HomeDialog.PickTv(TvJob.Logs)
                                })
                            }
                            if (LocalIsTv.current) {
                                AppMenuItem(text = { Text("Open on last channel: " + if (openLast) "On" else "Off") }, onClick = {
                                    moreMenu = false; openLast = !openLast; Resume.setEnabled(context, openLast)
                                })
                            }
                            if (LocalIsTv.current) {
                                AppMenuItem(text = { Text("Channels on screen: ${tileCount.label}") }, onClick = {
                                    moreMenu = false; tileCount = tileCount.next(); TileCount.save(context, tileCount)
                                })
                                AppMenuItem(text = { Text("Quality on this TV: " + if (deviceQuality == QualityCap.Best) "Best (4K)" else "Up to Full HD") }, onClick = {
                                    moreMenu = false
                                    deviceQuality = if (deviceQuality == QualityCap.Best) QualityCap.FullHd else QualityCap.Best
                                    DeviceQuality.set(context, deviceQuality)
                                    notify(if (deviceQuality == QualityCap.Best) "Tiles now pick 4K when there is one." else "Tiles now pick Full HD at most.")
                                })
                            }
                            if (!LocalIsTv.current) {
                                AppMenuItem(text = { Text("Sound with the screen off: " + if (backgroundSound) "On" else "Off") }, onClick = {
                                    moreMenu = false; backgroundSound = !backgroundSound; BackgroundAudio.setEnabled(context, backgroundSound)
                                })
                            }
                            AppMenuItem(text = { Text("Check for updates") }, onClick = {
                                moreMenu = false
                                scope.launch {
                                    try {
                                        val found = Updates.check(context, force = true)
                                        if (found != null) dialog = HomeDialog.Update(found)
                                        else notify("You have the latest version.")
                                    } catch (e: Exception) {
                                        AppLog.e("update", "Check failed: ${e.message}")
                                        notify("Couldn't check for updates.")
                                    }
                                }
                            })
    }

    val topActions: @Composable RowScope.() -> Unit = {
                    if (LocalIsTv.current) {
                        IconButton(onClick = { dialog = HomeDialog.LinkPhone }, modifier = Modifier.tvFocusRing()) {
                            Icon(Icons.Default.Cast, contentDescription = "Link a phone")
                        }
                    }
                    if (!LocalIsTv.current) {
                        GlassButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search channels", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (!LocalIsTv.current) {
                        // List or grid: the icon shows the view a tap switches to
                        IconButton(onClick = { phoneGrid = !phoneGrid; PhoneView.setGrid(context, phoneGrid) }) {
                            if (phoneGrid) Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Show as list", tint = Amber)
                            else Icon(Icons.Default.GridView, contentDescription = "Show as grid", tint = Amber)
                        }
                    }
                    // On a phone, adding channels is the floating "Add playlist" button at the bottom
                    if (LocalIsTv.current) {
                        Box {
                            IconButton(onClick = { dialog = HomeDialog.AddPlaylist() }, modifier = Modifier.tvFocusRing()) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add channels")
                            }
                        }
                    }
                    if (LocalIsTv.current) Box {
                        IconButton(onClick = { moreMenu = true }, modifier = Modifier.tvFocusRing()) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        // On a TV the remote moves through a menu next to the button; on a phone the long list slides up
                        // from the bottom instead, so it never covers the top of the app
                        if (isTvMenu) {
                            DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) { moreEntries() }
                        } else if (moreMenu) {
                            // Opens all the way, so every entry is in view without dragging the panel up first
                            ModalBottomSheet(onDismissRequest = { moreMenu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                                Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) { moreEntries() }
                            }
                        }
                    }
    }

    // The remote's keys while the channel grid is the active part of the screen (see HomeKeys)
    val tvVisible by rememberUpdatedState(visible)
    val tvColumns by rememberUpdatedState(tileCount.columns)
    val tvBlocked by rememberUpdatedState(dialog != null || moreMenu || section == Section.Playlists)
    val tvSection by rememberUpdatedState(section)
    val tvOpen by rememberUpdatedState<(Int) -> Unit> { i ->
        val list = tvVisible
        val ch = list.getOrNull(i)
        if (ch != null) {
            if (ch.kind == Kind.SERIES) dialog = HomeDialog.Series(ch)
            else onPlay(list.filter { it.kind != Kind.SERIES }, list.subList(0, i).count { it.kind != Kind.SERIES }, format)
        }
    }
    DisposableEffect(isTv) {
        val handler: (android.view.KeyEvent) -> Boolean = handler@{ e ->
            val code = e.keyCode
            val ok = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
            val arrow = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            val list = tvVisible
            if (tvBlocked || list.isEmpty() || !(ok || arrow)) return@handler false
            val down = e.action == android.view.KeyEvent.ACTION_DOWN
            if (!tvInGrid) {
                // On the tabs and buttons Android's focus does the work, except ▼, which goes down into the channels
                if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (down) { tvInGrid = true; focusManager.clearFocus() }
                    return@handler true
                }
                return@handler false
            }
            val cols = tvColumns
            val cur = tvSelected.coerceIn(0, list.size - 1)
            if (down) {
                when (code) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> tvSelected = GridMoves.down(cur, cols, list.size)
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        val up = GridMoves.up(cur, cols)
                        if (up != null) tvSelected = up
                        else {
                            tvInGrid = false
                            val tab = tvTabs.indexOf(tvSection).coerceAtLeast(0)
                            runCatching { tabRequesters[tab].requestFocus() }
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> tvSelected = GridMoves.left(cur, cols)
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> tvSelected = GridMoves.right(cur, cols, list.size)
                    else -> when (e.repeatCount) {
                        0 -> tvOkDown = true
                        1 -> if (tvOkDown) { tvOkDown = false; tvMenuSignal++ }   // holding OK: the channel's options
                    }
                }
            } else if (ok && tvOkDown) {
                tvOkDown = false
                AppLog.i("keys", "  OK on ${list[cur].displayName()}")
                tvOpen(cur)
            }
            true
        }
        if (isTv) HomeKeys.handler = handler
        onDispose { if (HomeKeys.handler === handler) HomeKeys.handler = null }
    }

    // The Add playlist button shrinks to just + while the channels scroll down, and opens out again when they scroll up
    var fabExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(section) { fabExpanded = true }   // a new tab starts at its top, with the full button
    val shrinkFabOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) fabExpanded = false
                else if (available.y > 1f) fabExpanded = true
                return Offset.Zero
            }
        }
    }

    // Phone: ☰ opens a side menu with the channels, sharing, settings and help (the TV keeps its ⋮ menu)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    fun closeDrawer() { scope.launch { drawerState.close() } }
    val xtreamSources = sources.filter { it.xtream != null }
    val mainAccount = sources.firstOrNull { it.enabled } ?: sources.firstOrNull()
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val homeScaffold: @Composable () -> Unit = {
        Scaffold(
            topBar = {
                if (!isTv && searchOpen) TopAppBar(
                    title = {
                        GlassSearchField(
                            value = query,
                            onValueChange = { query = it },
                            onClose = { query = ""; searchOpen = false },
                            focusRequester = searchFocus,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    },
                ) else if (!isTv) TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The app's logo (the same as on the loading screen), on a small rounded tile
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(R.drawable.splash_icon), contentDescription = "Stream Desk logo", modifier = Modifier.requiredSize(58.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("Stream Desk", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    actions = topActions,
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (!isTv) {
                    ExtendedFloatingActionButton(
                        expanded = fabExpanded,
                        onClick = { dialog = HomeDialog.AddPlaylist() },
                        icon = { Icon(Icons.Default.Add, contentDescription = if (fabExpanded) null else "Add playlist") },
                        text = { Text("Add playlist", fontWeight = FontWeight.SemiBold) },
                        containerColor = Amber,
                        contentColor = FocusInk,
                    )
                }
            },
        ) { padding ->
            // Scrolling the channels with a finger closes the keyboard (for example after typing in the search box)
            val keyboard = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val hideKeyboardOnScroll = remember(keyboard, focusManager) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput && available.y != 0f) {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }
                        return Offset.Zero
                    }
                }
            }
            // On a phone, the channels follow your finger as you drag left or right; when you let go, the next or previous tab opens
            val dragX = remember { Animatable(0f) }
            var paneWidth by remember { mutableFloatStateOf(1f) }
            val swipeToTab = if (isTv) Modifier else Modifier
                .onSizeChanged { paneWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { dragX.stop() } },
                        onDragEnd = {
                            scope.launch {
                                val last = Section.entries.lastIndex
                                val dir = when {
                                    dragX.value <= -paneWidth * 0.2f && section.ordinal < last -> 1    // finger moved left: the next tab
                                    dragX.value >= paneWidth * 0.2f && section.ordinal > 0 -> -1
                                    else -> 0
                                }
                                if (dir == 0) {
                                    dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                } else {
                                    dragX.animateTo(-dir * paneWidth, tween(140))        // the old tab slides out…
                                    section = Section.entries[section.ordinal + dir]
                                    dragX.snapTo(dir * paneWidth)
                                    dragX.animateTo(0f, tween(200))                      // …and the new one slides in
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                    ) { change, dragAmount ->
                        change.consume()
                        // A little resistance past the first or last tab
                        val atEdge = (dragAmount > 0 && section.ordinal == 0) || (dragAmount < 0 && section.ordinal == Section.entries.lastIndex)
                        scope.launch { dragX.snapTo(dragX.value + if (atEdge) dragAmount * 0.3f else dragAmount) }
                    }
                }
                .graphicsLayer { translationX = dragX.value }
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (isTv) {
                    // Just the red dot, not the name: the TV's width is needed for all the tabs
                    // The TV's tabs: Sport first, as it is what the TV is mostly used for
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(OnAir, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        TvTabBar(
                            labels = tvTabs.map { it.label },
                            selected = tvTabs.indexOf(section),
                            // OK on a tab opens it and goes down into its channels
                            onSelect = { section = tvTabs[it]; tvInGrid = true; focusManager.clearFocus() },
                            modifier = Modifier.weight(1f),
                            requesters = tabRequesters,
                        )
                        IconButton(onClick = { dialog = HomeDialog.Search }, modifier = Modifier.tvFocusRing()) { Icon(Icons.Default.Search, contentDescription = "Search channels") }
                        topActions()
                    }
                }
                if (isTv && sources.isEmpty()) {
                    LinkPhoneCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                if (showLinkBar) {
                    // Open network stream (or a playlist link)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Stream or playlist link") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { submitUrl() }),
                            trailingIcon = if (url.isNotEmpty()) {
                                { IconButton(onClick = { url = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear") } }
                            } else null,
                        )
                        FilledIconButton(onClick = ::submitUrl, enabled = !checkingLink, modifier = Modifier.size(52.dp)) {
                            if (checkingLink) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.PlayArrow, contentDescription = "Open")
                        }
                    }
                    FormatPicker(format, onPick = { format = it }, modifier = Modifier.padding(horizontal = 8.dp))
                }

                if (!isTv) {
        // Scrolls when the labels don't fit, e.g. with large text sizes
                    ScrollableTabRow(
                        selectedTabIndex = section.ordinal,
                        containerColor = MaterialTheme.colorScheme.background,
                        edgePadding = 8.dp,
                    ) {
                        Section.entries.forEach { s ->
                            Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label, maxLines = 1, softWrap = false) })
                        }
                    }
                }

                Column(Modifier.weight(1f).fillMaxWidth().nestedScroll(hideKeyboardOnScroll).nestedScroll(shrinkFabOnScroll).then(swipeToTab)) {
                if (section == Section.Playlists) {
                    PlaylistsPane(
                        sources = sources,
                        progress = progress,
                        onAdd = { dialog = HomeDialog.AddPlaylist() },
                        onRefresh = { loadPlaylist(it.url) },
                        onShow = {
                            section = Section.All
                            sourceFilter = it.url
                            group = null
                        },
                        onRename = {
                            dialog = if (it.xtream != null) HomeDialog.EditLogin(it, null) else HomeDialog.RenamePlaylist(it)
                        },
                        onToggle = { store.setEnabled(it.url, !it.enabled) },
                        onLogs = { dialog = HomeDialog.Logs },
                        onSendLogin = if (isTv) null else { source -> dialog = HomeDialog.PickTv(TvJob.Login(source)) },
                        onShareQr = if (isTv) null else { source -> dialog = HomeDialog.ShareQr(source) },
                        onCopy = { clipboard.setText(AnnotatedString(it.url)); notify("Link copied.") },
                        onRemove = { dialog = HomeDialog.RemovePlaylist(it) },
                        modifier = Modifier.weight(1f),
                    )
                    return@Column
                }

                if (!isTv && query.isNotBlank()) {
                    Text(
                        if (visible.size == 1) "1 channel" else "${visible.size} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                if (!isTv && kindsPresent.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(kindsPresent) { k ->
                            FilterChip(
                                selected = kindFilter == k,
                                onClick = { kindFilter = if (kindFilter == k) null else k; group = null },
                                label = { Text(k.label) },
                            )
                        }
                    }
                }
                val showSourceChip = section == Section.All && filterName != null
                if (groups.isNotEmpty() || showSourceChip || (isTv && query.isNotBlank())) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isTv && query.isNotBlank()) item {
                            FilterChip(
                                selected = true,
                                onClick = { query = "" },
                                label = { Text("Search: $query", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp)) },
                            )
                        }
                        if (showSourceChip) item {
                            FilterChip(
                                selected = true,
                                onClick = { sourceFilter = null; group = null },
                                label = { Text(filterName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Show all channels", modifier = Modifier.size(16.dp)) },
                            )
                        }
                        if (groups.isNotEmpty()) {
                            item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All groups") }) }
                            items(groups) { g ->
                                FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) })
                            }
                        }
                    }
                }

                if (visible.isEmpty()) {
                    EmptyState(section, filtered = query.isNotBlank() || group != null)
                } else if (isTv) {
                    // The tiles are sized so exactly [tileCount] fit on the screen: the rows share the height
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val gap = 12.dp
                        // The rows share the height exactly, leaving 8 dp at the bottom (less than the gap), so the next row never peeks in
                        val rowHeight = ((maxHeight - 8.dp - 8.dp - gap * (tileCount.rows - 1)) / tileCount.rows).coerceAtLeast(72.dp)
                    // Each tab (and folder) opens at its own top, not where the previous tab was scrolled to
                    key(section, group, sourceFilter) {
                    val gridState = rememberLazyGridState()
                    // The grid moves a whole row at a time, keeping the selected row in the middle: the tiles never end up
                    // half off the screen, and the picture doesn't jump by uneven amounts
                    LaunchedEffect(tvSelected, tvInGrid, tileCount) {
                        if (tvInGrid && visible.isNotEmpty()) {
                            val columns = tileCount.columns
                            val row = tvSelected.coerceIn(0, visible.size - 1) / columns
                            val firstRow = (row - (tileCount.rows - 1) / 2).coerceAtLeast(0)
                            if (gridState.firstVisibleItemIndex / columns != firstRow || gridState.firstVisibleItemScrollOffset != 0) {
                                gridState.animateScrollToItem(firstRow * columns)
                            }
                        }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(tileCount.columns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        itemsIndexed(visible, key = { _, ch -> ch.url }) { i, ch ->
                            // Asks for this channel's guide while it's on screen (again each minute; a fresh one isn't fetched again)
                            LaunchedEffect(ch.url, nowSeconds / 60) { loginFor(ch)?.let { NowOn.want(ch.url, it) } }
                            ChannelTile(
                                modifier = Modifier.height(rowHeight),
                                selected = tvInGrid && tvSelected.coerceIn(0, visible.size - 1) == i,
                                menuSignal = tvMenuSignal,
                                channel = ch,
                                number = if (section == Section.Sport || section == Section.Israel) i + 1 else numbers[ch.url],
                                saved = numbers[ch.url] != null,
                                onClick = {
                                    if (ch.kind == Kind.SERIES) dialog = HomeDialog.Series(ch)
                                    else onPlay(visible.filter { it.kind != Kind.SERIES }, visible.subList(0, i).count { it.kind != Kind.SERIES }, format)
                                },
                                onToggleFavorite = { store.toggleFavorite(ch) },
                                onRename = { dialog = HomeDialog.Rename(ch) },
                                onSave = { store.addAll(listOf(ch)); notify("Saved “${ch.name}”.") },
                                onCopy = { clipboard.setText(AnnotatedString(ch.url)); notify("Link copied.") },
                                onRemove = { store.remove(ch.url) },
                                removeLabel = "Delete channel",
                                onAir = NowNext.of(guides[ch.url], nowSeconds),
                                onGuide = loginFor(ch)?.let { login -> { dialog = HomeDialog.Guide(ch, login) } },
                                fillHeight = true,
                            )
                        }
                    }
                    }
                    }
                } else if (phoneGrid) {
                    // The phone's grid: 3 cards across when held upright, more on a tablet or sideways
                    key(section, group, sourceFilter) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(visible, key = { _, ch -> ch.url }) { i, ch ->
                            LaunchedEffect(ch.url, nowSeconds / 60) { loginFor(ch)?.let { NowOn.want(ch.url, it) } }
                            val saved = numbers[ch.url] != null
                            val guideLogin = loginFor(ch)
                            PhoneTile(
                                channel = ch,
                                tag = if (section == Section.Sport || section == Section.Israel) ch.group else null,
                                onAir = NowNext.of(guides[ch.url], nowSeconds),
                                onClick = {
                                    if (ch.kind == Kind.SERIES) dialog = HomeDialog.Series(ch)
                                    else onPlay(visible.filter { it.kind != Kind.SERIES }, visible.subList(0, i).count { it.kind != Kind.SERIES }, format)
                                },
                                onToggleFavorite = { store.toggleFavorite(ch) },
                                menuItems = { close ->
                                    if (saved) AppMenuItem(text = { Text("Rename") }, onClick = { close(); dialog = HomeDialog.Rename(ch) })
                                    else AppMenuItem(text = { Text("Save to channels") }, onClick = { close(); store.addAll(listOf(ch)); notify("Saved “${ch.name}”.") })
                                    if (guideLogin != null) AppMenuItem(text = { Text("TV guide") }, onClick = { close(); dialog = HomeDialog.Guide(ch, guideLogin) })
                                    AppMenuItem(text = { Text("Play on TV") }, onClick = {
                                        close()
                                        val from = (i - 100).coerceAtLeast(0)
                                        val to = (i + 100).coerceAtMost(visible.size)
                                        dialog = HomeDialog.PickTv(TvJob.Play(visible.subList(from, to), i - from))
                                    })
                                    AppMenuItem(text = { Text("Copy link") }, onClick = { close(); clipboard.setText(AnnotatedString(ch.url)); notify("Link copied.") })
                                    AppMenuItem(text = { Text("Delete channel") }, onClick = {
                                        close()
                                        store.remove(ch.url)
                                    })
                                },
                            )
                        }
                    }
                    }
                } else {
                    key(section, group, sourceFilter) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                        itemsIndexed(visible, key = { _, ch -> ch.url }) { i, ch ->
                            // Asks for this channel's guide while it's on screen (again each minute; a fresh one isn't fetched again)
                            LaunchedEffect(ch.url, nowSeconds / 60) { loginFor(ch)?.let { NowOn.want(ch.url, it) } }
                            ChannelRow(
                                modifier = if (i == 0) Modifier.focusRequester(firstRowFocus) else Modifier,
                                channel = ch,
                                number = if (section == Section.Sport || section == Section.Israel) i + 1 else numbers[ch.url],
                                saved = numbers[ch.url] != null,
                                onClick = {
                                    if (ch.kind == Kind.SERIES) dialog = HomeDialog.Series(ch)
                                    else onPlay(visible.filter { it.kind != Kind.SERIES }, visible.subList(0, i).count { it.kind != Kind.SERIES }, format)
                                },
                                onToggleFavorite = { store.toggleFavorite(ch) },
                                onRename = { dialog = HomeDialog.Rename(ch) },
                                onSave = { store.addAll(listOf(ch)); notify("Saved “${ch.name}”.") },
                                onCopy = { clipboard.setText(AnnotatedString(ch.url)); notify("Link copied.") },
                                onPlayOnTv = if (isTv) null else {
                                    {
                                        val from = (i - 100).coerceAtLeast(0)
                                        val to = (i + 100).coerceAtMost(visible.size)
                                        dialog = HomeDialog.PickTv(TvJob.Play(visible.subList(from, to), i - from))
                                    }
                                },
                                onRemove = {
                                    store.remove(ch.url)
                                },
                                removeLabel = "Delete channel",
                                onAir = NowNext.of(guides[ch.url], nowSeconds),
                                onGuide = loginFor(ch)?.let { login -> { dialog = HomeDialog.Guide(ch, login) } },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    }
                }
                }
            }
        }
    }
    if (isTv) homeScaffold() else ModalNavigationDrawer(
        drawerState = drawerState,
        // Only a tap on ☰ opens it: swiping sideways changes tabs
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp), modifier = Modifier.width(318.dp)) {
                // Built only while it is open (or opening/closing): nothing to keep up to date while it's hidden
                if (drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open) DrawerColumn(
                    version = appVersion,
                    account = mainAccount?.name,
                    accountDetail = mainAccount?.let { "%,d channels".format(channels.count { c -> c.source == it.url }) + if (it.updatedAt > 0) " · updated " + DateUtils.getRelativeTimeSpanString(it.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else "" },
                    onAccount = { closeDrawer(); section = Section.Playlists },
                ) {
                    fun go(s: Section) { closeDrawer(); section = s; sourceFilter = null; group = null }
                    DrawerSection("Channels", divider = false)
                    DrawerRow(Icons.Default.LiveTv, "All channels", { go(Section.All) }, section == Section.All) { DrawerCount(channels.size) }
                    DrawerRow(Icons.Default.SportsSoccer, "Sport", { go(Section.Sport) }, section == Section.Sport) { DrawerCount(sportPicked.size) }
                    DrawerRow(Icons.Default.Tv, "Israeli TV", { go(Section.Israel) }, section == Section.Israel) { DrawerCount(israelPicked.size) }
                    DrawerRow(Icons.Default.Star, "Favorites", { go(Section.Favorites) }, section == Section.Favorites) { DrawerCount(channels.count { it.favorite }) }
                    DrawerRow(Icons.AutoMirrored.Filled.PlaylistPlay, "My playlists", { go(Section.Playlists) }, section == Section.Playlists)

                    DrawerSection("Account & sharing")
                    DrawerRow(Icons.Default.Add, "Add playlist", { closeDrawer(); dialog = HomeDialog.AddPlaylist() })
                    if (sources.isNotEmpty()) DrawerRow(Icons.Default.Refresh, "Update all playlists", { closeDrawer(); sources.forEach { loadPlaylist(it.url) } })
                    DrawerRow(Icons.Default.QrCode2, "Share account with QR code", {
                        closeDrawer()
                        when (xtreamSources.size) {
                            0 -> notify("Sign in to an account first (+ Add playlist → Login).")
                            1 -> dialog = HomeDialog.ShareQr(xtreamSources[0])
                            else -> { section = Section.Playlists; notify("Tap ⋮ next to the account → Share with QR code.") }
                        }
                    })
                    DrawerRow(Icons.Default.Cast, "Send login to TV", {
                        closeDrawer()
                        when (xtreamSources.size) {
                            0 -> notify("Sign in to an account first (+ Add playlist → Login).")
                            1 -> dialog = HomeDialog.PickTv(TvJob.Login(xtreamSources[0]))
                            else -> { section = Section.Playlists; notify("Tap ⋮ next to the account → Send login to TV.") }
                        }
                    })
                    DrawerRow(Icons.Default.PhonelinkRing, "Continue from TV", { closeDrawer(); dialog = HomeDialog.PickTv(TvJob.TakeOver) })

                    DrawerSection("Settings")
                    DrawerRow(Icons.Default.Settings, "Settings", { closeDrawer(); dialog = HomeDialog.Settings })

                    DrawerSection("Help")
                    DrawerRow(Icons.Default.SystemUpdate, "Check for updates", {
                        closeDrawer()
                        scope.launch {
                            try {
                                val found = Updates.check(context, force = true)
                                if (found != null) dialog = HomeDialog.Update(found)
                                else notify("You have the latest version.")
                            } catch (e: Exception) {
                                AppLog.e("update", "Check failed: ${e.message}")
                                notify("Couldn't check for updates.")
                            }
                        }
                    })
                    DrawerRow(Icons.Default.BugReport, "Send problem report", { closeDrawer(); dialog = HomeDialog.Report(Problems.report(context)) })
                    DrawerRow(Icons.Default.Tv, "Problems from TV", { closeDrawer(); dialog = HomeDialog.PickTv(TvJob.Logs) })
                    DrawerRow(Icons.Default.Info, "About", { closeDrawer(); dialog = HomeDialog.About })
                }
            }
        },
    ) { homeScaffold() }
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }


    when (val d = dialog) {
        is HomeDialog.Rename -> TextDialog(
            title = "Rename channel",
            initial = d.channel.name,
            confirm = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { store.rename(d.channel.url, it.trim()); dialog = null },
        )
        is HomeDialog.RenamePlaylist -> TextDialog(
            title = "Rename playlist",
            initial = d.source.name,
            confirm = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { store.renamePlaylist(d.source.url, it.trim()); dialog = null },
        )
        is HomeDialog.RemovePlaylist -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Remove “${d.source.name}”?") },
            text = { Text("Its ${d.source.channelCount} channels are removed from your list. Channels you starred stay in your favorites.") },
            confirmButton = {
                TextButton(onClick = {
                    store.removePlaylist(d.source.url)
                    if (sourceFilter == d.source.url) sourceFilter = null
                    dialog = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        HomeDialog.Settings -> SettingsScreen(version = appVersion, onDismiss = { dialog = null }) {
                      DrawerSection("Video", divider = false)
                      DrawerRow(Icons.Default.HighQuality, "Video quality", {
                          deviceQuality = if (deviceQuality == QualityCap.Best) QualityCap.FullHd else QualityCap.Best; DeviceQuality.set(context, deviceQuality)
                      }) {
                          DrawerSegments(listOf("Full HD", "4K"), if (deviceQuality == QualityCap.Best) 1 else 0) {
                              deviceQuality = if (it == 1) QualityCap.Best else QualityCap.FullHd; DeviceQuality.set(context, deviceQuality)
                          }
                      }
                      DrawerRow(Icons.Default.SignalCellularAlt, "Lower quality on mobile data", {
                          saveMobileData = !saveMobileData; DeviceQuality.setSaveData(context, saveMobileData)
                      }) { DrawerSwitch(saveMobileData) { saveMobileData = it; DeviceQuality.setSaveData(context, it) } }
                      DrawerSection("Player")
                      DrawerRow(Icons.Default.PictureInPictureAlt, "Small window when leaving", { pipOnLeave = !pipOnLeave; PlayerPrefs.setPipOnLeave(context, pipOnLeave) }) {
                          DrawerSwitch(pipOnLeave) { pipOnLeave = it; PlayerPrefs.setPipOnLeave(context, it) }
                      }
                      DrawerRow(Icons.Default.UnfoldMore, "CH + / − buttons on the picture", { channelButtons = !channelButtons; PlayerPrefs.setChannelButtons(context, channelButtons) }) {
                          DrawerSwitch(channelButtons) { channelButtons = it; PlayerPrefs.setChannelButtons(context, it) }
                      }
                      DrawerRow(Icons.AutoMirrored.Filled.VolumeUp, "Sound with screen off", { backgroundSound = !backgroundSound; BackgroundAudio.setEnabled(context, backgroundSound) }) {
                          DrawerSwitch(backgroundSound) { backgroundSound = it; BackgroundAudio.setEnabled(context, it) }
                      }
                      DrawerSection("Home screen")
                      DrawerRow(Icons.Default.GridView, "View", { phoneGrid = !phoneGrid; PhoneView.setGrid(context, phoneGrid) }) {
                          DrawerSegments(listOf("List", "Grid"), if (phoneGrid) 1 else 0) { phoneGrid = it == 1; PhoneView.setGrid(context, phoneGrid) }
                      }
                      DrawerRow(Icons.Default.History, "Open on last channel", { openLast = !openLast; Resume.setEnabled(context, openLast) }) {
                          DrawerSwitch(openLast) { openLast = it; Resume.setEnabled(context, it) }
                      }
                      if (sources.isNotEmpty()) DrawerSection("Links")
                      if (sources.isNotEmpty()) DrawerRow(Icons.Default.Link, if (linkBarOpen) "Hide the link bar" else "Open a stream link", { dialog = null; linkBarOpen = !linkBarOpen })

        }
        HomeDialog.About -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Image(painterResource(R.drawable.splash_icon), contentDescription = null, modifier = Modifier.size(72.dp)) },
            title = { Text("Stream Desk") },
            text = {
                Text(
                    "Version ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()}\n\n" +
                        "Live TV from your IPTV account on the phone, the TV and the PC: Sport and Israeli TV lists, the TV guide, " +
                        "favorites, and sharing an account with a QR code.",
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
        is HomeDialog.ShareQr -> ShareLoginDialog(d.source, onDismiss = { dialog = null }, onDone = { notify(it) })
        is HomeDialog.SharedLogin -> AddSharedLoginDialog(
            login = d.login,
            name = d.name,
            onAdd = { dialog = null; addLogin(d.login, d.name); notify("Account added. Loading the channels…") },
            onDismiss = { dialog = null },
        )
        is HomeDialog.AddPlaylist -> AddPlaylistDialog(
            linkFirst = d.linkFirst,
            onPickQr = { dialog = null; pickQr.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onImportFile = { dialog = null; openFile.launch(arrayOf("*/*")) },
            onPasteText = { dialog = HomeDialog.PasteText },
            initialLink = clipboard.getText()?.text?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) && ' ' !in it }.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { link, name -> dialog = null; loadPlaylist(link, name.ifBlank { null }) },
            onSignIn = { prefill -> dialog = HomeDialog.EditLogin(null, prefill) },
        )
        HomeDialog.PasteText -> TextDialog(
            title = "Paste playlist",
            initial = clipboard.getText()?.text.orEmpty(),
            hint = "#EXTM3U\n#EXTINF:-1,My channel\nhttps://…",
            confirm = "Add channels",
            multiline = true,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; notify(importText(store, it)) },
        )
        is HomeDialog.EditLogin -> LoginEditor(
            source = d.source,
            prefill = d.prefill,
            onDismiss = { dialog = null },
            onSave = { login, name, enabled, hours ->
                dialog = null
                addLogin(login, name.ifBlank { null }, enabled, hours, replacing = d.source?.url)
            },
        )
        is HomeDialog.Crash -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Stream Desk stopped unexpectedly") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sorry about that. Copy these details and send them to whoever builds the app so it can be fixed. Passwords are removed.")
                    Text(
                        d.report,
                        modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(d.report)); dialog = null; notify("Details copied.") }) { Text("Copy details") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )
        HomeDialog.LinkPhone -> LinkPhoneDialog(onDismiss = { dialog = null })
        is HomeDialog.Invite -> {
            val source = sources.firstOrNull { it.xtream != null && it.enabled }
            InviteDialog(
                invite = d.invite,
                canSendLogin = source != null,
                onSendLogin = { dialog = null; onInviteDone(); connectInvite(d.invite, source) },
                onConnectOnly = { dialog = null; onInviteDone(); connectInvite(d.invite, null) },
                onDismiss = { dialog = null; onInviteDone() },
            )
        }
        HomeDialog.Search -> TextDialog(
            title = "Search channels",
            initial = query,
            confirm = "Search",
            onDismiss = { dialog = null },
            onConfirm = { query = it.trim(); dialog = null },
        )
        is HomeDialog.PickTv -> TvPickerDialog(
            title = when (d.job) { is TvJob.Login -> "Send login to TV"; TvJob.TakeOver -> "Continue from TV"; TvJob.Logs -> "Problems from TV"; else -> "Play on TV" },
            onDismiss = { dialog = null },
            onChosen = { device, code -> dialog = null; runTvJob(d.job, device, code) },
        )
        HomeDialog.EditSport -> SportListEditor(
            initial = sportSettings,
            onDismiss = { dialog = null },
            onSave = { sportStore.save(it); dialog = null; section = Section.Sport },
        )
        is HomeDialog.Update -> UpdateDialog(d.info, onDismiss = { dialog = null })
        is HomeDialog.Report -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Problem report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Send it to yourself (WhatsApp, email…) and paste it to whoever fixes the app. Passwords are hidden.")
                    Text(
                        d.text,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { Problems.markSeen(context); dialog = null; Problems.share(context, d.text) }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(d.text)); Problems.markSeen(context); dialog = null; notify("Report copied.") }) { Text("Copy") }
            },
        )
        is HomeDialog.TvLogs -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Problems from ${d.tvName}") },
            text = {
                Text(
                    d.text.ifBlank { "The TV hasn't logged anything yet." },
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                TextButton(onClick = { dialog = null; Problems.share(context, d.text) }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(d.text)); dialog = null; notify("TV report copied. Passwords are hidden.") }) { Text("Copy") }
            },
        )
        is HomeDialog.Guide -> ChannelGuideDialog(d.channel, d.login, onPlay = { dialog = null; playSport(d.channel) }, onDismiss = { dialog = null })
        HomeDialog.Logs -> LogsScreen(
            onDismiss = { dialog = null },
            onCopy = { clipboard.setText(AnnotatedString(it)); notify("Logs copied. Passwords are hidden.") },
        )
        is HomeDialog.Series -> SeriesDialog(
            series = d.series,
            onDismiss = { dialog = null },
            onPlay = { episodes, i -> dialog = null; onPlay(episodes, i, StreamFormat.Auto) },
        )
        is HomeDialog.LinkError -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Couldn't open this link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.message.replaceFirstChar { it.uppercase() } + ".")
                    MonoLine(d.link)
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = { dialog = null; playLink(d.link) }) { Text("Try as a stream") }
            },
        )
        null -> Unit
    }
}

@Composable
private fun PlaylistsPane(
    sources: List<PlaylistSource>,
    progress: Map<String, String>,
    onAdd: () -> Unit,
    onRefresh: (PlaylistSource) -> Unit,
    onShow: (PlaylistSource) -> Unit,
    onRename: (PlaylistSource) -> Unit,
    onToggle: (PlaylistSource) -> Unit,
    onLogs: () -> Unit,
    onSendLogin: ((PlaylistSource) -> Unit)?,
    onShareQr: ((PlaylistSource) -> Unit)?,
    onCopy: (PlaylistSource) -> Unit,
    onRemove: (PlaylistSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loading = progress.keys
    val pending = loading.filter { link -> sources.none { it.url == link } }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add channels")
            }
        }
        if (sources.isEmpty() && pending.isEmpty()) item {
            Text(
                "Add the link to an M3U channel list, or sign in with the server, username and password from your IPTV provider (+ button above). Stream Desk adds the channels to your list and updates them once a day.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(pending, key = { "loading:$it" }) { link ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                headlineContent = { Text(progress[link] ?: "Loading…") },
                supportingContent = { MonoLine(link) },
                trailingContent = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        items(sources, key = { it.url }) { s ->
            var menu by remember { mutableStateOf(false) }
            ListItem(
                modifier = Modifier.clickable { onShow(s) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                headlineContent = {
                    Text(
                        if (s.enabled) s.name else "${s.name} (off)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (s.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                supportingContent = {
                    Column {
                        if (s.account.isNotBlank() && s.url !in loading) {
                            Text(s.account, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (s.url in loading) progress[s.url].orEmpty()
                            else if (s.updatedAt == 0L) (if (s.xtream != null) "${s.xtream.username} · not loaded yet" else "Not loaded yet")
                            else if (s.xtream != null) "Signed in as ${s.xtream.username} · updated ${DateUtils.getRelativeTimeSpanString(s.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
                            else "${s.channelCount} channels · updated ${DateUtils.getRelativeTimeSpanString(s.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        MonoLine(s.xtream?.base ?: s.url)
                        if (s.error.isNotBlank() && s.url !in loading) {
                            Text(if (s.updatedAt == 0L) "Couldn't sign in: ${s.error}" else "Problem at last update: ${s.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.url in loading) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { onRefresh(s) }) { Icon(Icons.Default.Refresh, contentDescription = "Update playlist") }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Playlist options") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                AppMenuItem(text = { Text("Show channels") }, onClick = { menu = false; onShow(s) })
                                AppMenuItem(text = { Text(if (s.xtream != null) "Edit login" else "Rename") }, onClick = { menu = false; onRename(s) })
                                AppMenuItem(text = { Text(if (s.enabled) "Turn off" else "Turn on") }, onClick = { menu = false; onToggle(s) })
                                if (onSendLogin != null && s.xtream != null) {
                                    AppMenuItem(text = { Text("Send login to TV") }, onClick = { menu = false; onSendLogin(s) })
                                }
                                if (onShareQr != null && s.xtream != null) {
                                    AppMenuItem(text = { Text("Share with QR code") }, onClick = { menu = false; onShareQr(s) })
                                }
                                AppMenuItem(text = { Text("Show logs") }, onClick = { menu = false; onLogs() })
                                AppMenuItem(text = { Text("Copy link") }, onClick = { menu = false; onCopy(s) })
                                AppMenuItem(text = { Text("Remove playlist") }, onClick = { menu = false; onRemove(s) })
                            }
                        }
                    }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MonoLine(text: String) = Text(
    text,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** A link as typed: "server.com:8080/list.m3u" works too (http:// is added); null if it isn't a link. */
internal fun normalizeLink(text: String): String? {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val withScheme = if (Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(t)) t else "http://$t"
    val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    val host = uri.host ?: return null
    return withScheme.takeIf { '.' in host || host == "localhost" }
}

/**
 * Adding channels: the two ways side by side. "Username & password" (what IPTV providers give, and what the app
 * handles best) goes on to the sign-in screen; "Playlist link" takes an M3U link. A get.php link with a username
 * and password in it is sent to the sign-in screen, filled in.
 */
@Composable
private fun AddPlaylistDialog(linkFirst: Boolean, onPickQr: () -> Unit, onImportFile: () -> Unit, onPasteText: () -> Unit, initialLink: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit, onSignIn: (XtreamLogin?) -> Unit) {
    // 0 = username & password, 1 = playlist link, 2 = QR code from another phone
    var mode by rememberSaveable { mutableStateOf(if (linkFirst || (initialLink.isNotEmpty() && !XtreamLogin.isLoginLink(initialLink))) 1 else 0) }
    val useLink = mode == 1
    var link by rememberSaveable { mutableStateOf(initialLink) }
    var name by rememberSaveable { mutableStateOf("") }
    val normalized = normalizeLink(link)
    val loginInLink = normalized?.let { if (XtreamLogin.isLoginLink(it)) XtreamLogin.fromLink(it) else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add channels") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Login", "Link", "QR code").forEachIndexed { i, label ->
                        SegmentedButton(selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, 3), icon = {}) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
                if (mode == 2) {
                    Text(
                        "Add an account shared from another phone (Playlists → ⋮ → Share with QR code). " +
                            "Pick the QR code picture from the gallery, or point this phone's camera at the QR code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!useLink) {
                    Text(
                        "Sign in with the server address, username and password your IPTV provider gave you. " +
                            "Best for providers: channels with logos, folders and the TV guide.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Paste the link to your M3U channel list. It's updated automatically once a day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Playlist link") },
                        placeholder = { Text("server.com:8080/playlist.m3u") },
                        singleLine = true,
                        isError = link.isNotBlank() && normalized == null,
                        supportingText = when {
                            link.isNotBlank() && normalized == null -> { { Text("That isn't a link. It looks like server.com/list.m3u, with no spaces.") } }
                            loginInLink != null -> { { Text("This link has a username and password in it: it will sign you in.") } }
                            else -> null
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (loginInLink == null) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // The rarer ways, for a list saved as a file or copied as text
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Or:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onImportFile) { Text("Open a .m3u file") }
                        TextButton(onClick = onPasteText) { Text("Paste text") }
                    }
                }
            }
        },
        confirmButton = {
            when {
                mode == 2 -> TextButton(onClick = onPickQr) { Text("Pick the picture") }
                !useLink -> TextButton(onClick = { onSignIn(null) }) { Text("Continue") }
                loginInLink != null -> TextButton(onClick = { onSignIn(loginInLink) }) { Text("Sign in") }
                else -> TextButton(onClick = { onConfirm(normalized!!, name.trim()) }, enabled = normalized != null) { Text("Add") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FormatPicker(format: StreamFormat, onPick: (StreamFormat) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text("Format: ${format.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            StreamFormat.entries.forEach { f ->
                AppMenuItem(text = { Text(f.label) }, onClick = { onPick(f); open = false })
            }
        }
    }
}

@Composable
private fun ChannelRow(
    modifier: Modifier = Modifier,
    channel: Channel,
    number: Int?,
    saved: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onPlayOnTv: (() -> Unit)?,
    onRemove: () -> Unit,
    removeLabel: String,
    onAir: NowNext? = null,
    onGuide: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val lit = LocalIsTv.current && focused   // on a TV, the row the remote is on turns solid white
    ListItem(
        modifier = modifier.onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (lit) Color.White else MaterialTheme.colorScheme.background,
            headlineColor = if (lit) FocusInk else MaterialTheme.colorScheme.onSurface,
            supportingColor = if (lit) FocusInkSoft else MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = if (lit) FocusInkSoft else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = { ChannelBadge(channel, number) },
        headlineContent = { Text(channel.displayName(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val soft = if (lit) FocusInkSoft else MaterialTheme.colorScheme.onSurfaceVariant
            if (onAir != null) {
                // The guide: what's on now (and how far it has got), and what's next
                Column {
                    onAir.now?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    onAir.progress?.let {
                        LinearProgressIndicator(
                            progress = { it },
                            color = if (lit) FocusInk else Amber,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 3.dp).fillMaxWidth(0.6f).height(2.dp),
                        )
                    }
                    onAir.next?.let { Text("Next: $it", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = soft) }
                }
            } else {
                // Channels from a playlist or login have long stream addresses with account details: show only the folder
                val detail = if (channel.source.isNotEmpty()) channel.group else listOf(channel.group, channel.url).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (channel.source.isNotEmpty()) FontFamily.Default else FontFamily.Monospace,
                    )
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onToggleFavorite) {
                    if (channel.favorite) Icon(Icons.Default.Star, contentDescription = "Remove from favorites", tint = if (lit) FocusAmberDark else Amber)
                    else Icon(Icons.Outlined.StarBorder, contentDescription = "Add to favorites")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Channel options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        if (saved) AppMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        else AppMenuItem(text = { Text("Save to channels") }, onClick = { menu = false; onSave() })
                        if (onGuide != null) AppMenuItem(text = { Text("TV guide") }, onClick = { menu = false; onGuide() })
                        if (onPlayOnTv != null) AppMenuItem(text = { Text("Play on TV") }, onClick = { menu = false; onPlayOnTv() })
                        AppMenuItem(text = { Text("Copy link") }, onClick = { menu = false; onCopy() })
                        AppMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onRemove() })
                    }
                }
            }
        },
    )
}

@Composable
private fun ChannelBadge(channel: Channel, number: Int?) {
    val hasLogo = channel.logo.startsWith("http") || channel.logo.startsWith("file:///android_asset/")
    Box(
        Modifier.size(width = 64.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val numberText: @Composable () -> Unit = {
            Text(
                number?.let { "%02d".format(it) } ?: "–",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The number shows until the logo arrives (or for good if there is none); a plain AsyncImage is cheaper than
        // SubcomposeAsyncImage, which matters when a screenful of rows is drawn at once
        var logoShown by remember(channel.logo) { mutableStateOf(false) }
        if (!logoShown) numberText()
        if (hasLogo) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                onSuccess = { logoShown = true },
                onError = { logoShown = false },
            )
        }
    }
}

@Composable
private fun EmptyState(section: Section, filtered: Boolean) {
    val text = when {
        filtered -> "No channels match your search."
        section == Section.Favorites -> "No favorites yet. Tap the star next to a channel to pin it here."
        section == Section.Israel -> "No Israeli channels found. They show up here once your account has Channel 11, 12, 13, 14 or 15."
        section == Section.Sport -> "No sport channels found. They show up here once your account has Sport 1–6, Sport 5 or ONE."
        section == Section.Playlists -> ""
        else -> "No channels yet. Paste a stream link above, or import an M3U playlist with the + button."
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    hint: String = "",
    multiline: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = !multiline,
                placeholder = { Text(hint, fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (multiline) FontFamily.Monospace else FontFamily.Default),
                modifier = Modifier.fillMaxWidth().then(if (multiline) Modifier.heightIn(min = 160.dp, max = 320.dp) else Modifier),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun importText(store: ChannelStore, text: String): String {
    val found = M3u.parse(text)
    if (found.isEmpty()) return "No stream links found in that playlist."
    val added = store.addAll(found)
    val skipped = found.size - added
    return buildString {
        append(if (added == 1) "Added 1 channel" else "Added $added channels")
        if (skipped > 0) append(" ($skipped already in your list)")
        append('.')
    }
}

internal fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }

private val UPDATE_CHOICES = listOf(12 to "Every 12 hours", 24 to "Everyday", 168 to "Every week", 0 to "Only when I ask")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginEditor(
    source: PlaylistSource?,
    prefill: XtreamLogin?,
    onDismiss: () -> Unit,
    onSave: (XtreamLogin, String, Boolean, Int) -> Unit,
) {
    val start = source?.xtream ?: prefill
    var name by rememberSaveable { mutableStateOf(source?.name.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(start?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(start?.password.orEmpty()) }
    var server by rememberSaveable { mutableStateOf(start?.base.orEmpty()) }
    var defaultAgent by rememberSaveable { mutableStateOf(start?.userAgent.isNullOrBlank()) }
    var userAgent by rememberSaveable { mutableStateOf(start?.userAgent.orEmpty()) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var enabled by rememberSaveable { mutableStateOf(source?.enabled ?: true) }
    var updateHours by rememberSaveable { mutableStateOf(source?.updateHours ?: 24) }
    var live by rememberSaveable { mutableStateOf(start?.live ?: true) }
    var movies by rememberSaveable { mutableStateOf(start?.movies ?: false) }
    var series by rememberSaveable { mutableStateOf(start?.series ?: false) }
    // A new login starts on the Hebrew folders, as this app is used; editing keeps what the login had
    var categories by rememberSaveable { mutableStateOf(if (source == null && start?.categories.isNullOrBlank()) "Hebrew" else start?.categories.orEmpty()) }
    var updateMenu by remember { mutableStateOf(false) }

    fun onServerChange(text: String) {
        // A full get.php link pasted into the server box fills in username and password too
        val fromLink = XtreamLogin.fromLink(text)
        if (fromLink != null) {
            server = fromLink.base
            username = fromLink.username
            password = fromLink.password
        } else {
            server = text.trim()
        }
    }

    val serverOk = Regex("^https?://[^\\s/]+", RegexOption.IGNORE_CASE).containsMatchIn(server)
    val valid = serverOk && username.isNotBlank() && password.isNotBlank() && (live || movies || series) &&
        (defaultAgent || userAgent.isNotBlank())

    fun save() = onSave(
        XtreamLogin(server, username, password, live, movies, series, if (defaultAgent) "" else userAgent.trim(), categories.trim()),
        name.trim(),
        enabled,
        updateHours,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    title = { Text(if (source == null) "New login" else "Editing") },
                    actions = {
                        IconButton(onClick = ::save, enabled = valid) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = if (valid) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            },
            // Always in full view: above the gesture bar, and above the keyboard while typing
            bottomBar = {
                Button(
                    onClick = ::save,
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                ) { Text(if (source == null) "Sign in" else "Save") }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineField(name, { name = it }, "Name")
                    LineField(username, { username = it.trim() }, "Username")
                    LineField(
                        password, { password = it.trim() }, "Password",
                        keyboardType = KeyboardType.Password,
                        hidden = !showPassword,
                        trailing = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                )
                            }
                        },
                    )
                    LineField(server, ::onServerChange, "Server", keyboardType = KeyboardType.Uri, isError = server.isNotBlank() && !serverOk)
                    Text(
                        "Provide a server address (should start with \"http://\" or \"https://\"). " +
                            "You can also paste your whole get.php link here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (server.isNotBlank() && !serverOk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { defaultAgent = !defaultAgent },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = defaultAgent, onCheckedChange = { defaultAgent = it })
                        Text("Use default User-Agent", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (!defaultAgent) {
                        LineField(userAgent, { userAgent = it }, "User-Agent")
                    }
                }

                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    SwitchRow("Turn on playlist", enabled) { enabled = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clickable { updateMenu = true }.padding(horizontal = 16.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("How often to update?", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(
                                UPDATE_CHOICES.firstOrNull { it.first == updateHours }?.second ?: "Everyday",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(updateMenu, onDismissRequest = { updateMenu = false }) {
                            UPDATE_CHOICES.forEach { (hours, label) ->
                                AppMenuItem(text = { Text(label) }, onClick = { updateHours = hours; updateMenu = false })
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow("Enable channels", live) { live = it }
                    if (live || movies || series) {
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            LineField(categories, { categories = it }, "Only these folders (optional)")
                            Text(
                                "Type part of a folder name, for example Hebrew. Separate several with commas. Leave empty to load every folder.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow("Enable movies", movies) { movies = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow("Enable series", series) { series = it }
                }
                if (!(live || movies || series)) {
                    Text(
                        "Turn on at least one of channels, movies or series.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** An underlined text field, like the sign-in screens of other IPTV players. */
@Composable
private fun LineField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    hidden: Boolean = false,
    isError: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.titleMedium,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false),
        trailingIcon = trailing,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedTextColor = Amber,
            unfocusedTextColor = Amber,
            focusedIndicatorColor = Amber,
            unfocusedIndicatorColor = Amber.copy(alpha = 0.7f),
        ),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SeriesDialog(series: Channel, onDismiss: () -> Unit, onPlay: (List<Channel>, Int) -> Unit) {
    var episodes by remember { mutableStateOf<List<Channel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(series.url) {
        try {
            episodes = Xtream.episodes(series)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "network error"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(series.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val eps = episodes
            when {
                error != null -> Text("Couldn't load the episodes: $error.")
                eps == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Loading episodes…")
                }
                eps.isEmpty() -> Text("This series has no episodes yet.")
                else -> LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    itemsIndexed(eps) { i, ep ->
                        Text(
                            ep.name,
                            modifier = Modifier.fillMaxWidth().clickable { onPlay(eps, i) }.padding(vertical = 12.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogsScreen(onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val lines by AppLog.lines.collectAsState()
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    // Newest at the bottom: follow new lines as they arrive
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    title = { Text("Logs") },
                    actions = {
                        TextButton(onClick = { onCopy(AppLog.text()) }, enabled = lines.isNotEmpty()) { Text("Copy") }
                        TextButton(onClick = { confirmClear = true }, enabled = lines.isNotEmpty()) { Text("Clear") }
                    },
                )
            },
        ) { padding ->
            if (lines.isEmpty()) {
                Text(
                    "Nothing logged yet. Sign-ins, downloads and playback show up here as they happen.",
                    modifier = Modifier.padding(padding).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(lines.size) { i ->
                            val line = lines[i]
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (" ERROR " in line) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the logs?") },
            confirmButton = { TextButton(onClick = { AppLog.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SportListEditor(initial: SportSettings, onDismiss: () -> Unit, onSave: (SportSettings) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var cap by rememberSaveable { mutableStateOf(initial.cap) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sport list") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "One channel per line, in the order you want. To show a different name, write “Name = channel name at your provider”. " +
                        "Each channel is shown once, in the best quality allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                Text("Quality", style = MaterialTheme.typography.labelLarge)
                QualityCap.entries.forEach { option ->
                    FilterChip(selected = cap == option, onClick = { cap = option }, label = { Text(option.label) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(SportSettings(text, cap)) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { text = SportList.DEFAULT_TEXT; cap = QualityCap.Best }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
