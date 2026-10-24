package com.streamdesk.app

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/** Remote-control commands that reached the TV ("next", "prev", "stop"), for the open player to act on. */
object LinkBus {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val commands: SharedFlow<String> = _commands
    fun send(action: String) { _commands.tryEmit(action) }
}

/** The TV side: listens for the phone, and announces itself on the Wi-Fi so the phone can find it. */
object LinkReceiver {
    private var server: LinkServer? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    /** The port the TV is really listening on (the usual one, unless it was taken). */
    @Volatile var port: Int = LINK_PORT

    /** The pairing code shown on the TV. Created once; "New code" replaces it. */
    fun code(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("link", Context.MODE_PRIVATE)
        return prefs.getString("code", null) ?: newCode(context)
    }

    fun newCode(context: Context): String {
        val code = "%06d".format(SecureRandom().nextInt(1_000_000))
        context.applicationContext.getSharedPreferences("link", Context.MODE_PRIVATE).edit().putString("code", code).apply()
        return code
    }

    /** This TV's address on the home network, for phones that can't find it automatically. */
    fun address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull().orEmpty()
        val s = LinkServer("Stream Desk (${Build.MODEL})", version, { code(app) }, AndroidHandler(app))
        val port = runCatching { s.start() }.getOrElse {
            AppLog.e("link", "Couldn't start the phone link", it)
            return
        }
        server = s
        this.port = port
        AppLog.i("link", "Listening for a phone on ${address() ?: "?"}:$port")
        runCatching {
            val manager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) { AppLog.i("link", "Announced on the network as \"${info.serviceName}\"") }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { AppLog.e("link", "Announcing failed (code $errorCode); phones can still use the address") }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            val info = NsdServiceInfo().apply {
                serviceName = "Stream Desk (${Build.MODEL})"
                serviceType = LINK_SERVICE_TYPE
                setPort(port)
            }
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsd = manager
            registration = listener
        }.onFailure { AppLog.e("link", "Couldn't announce on the network", it) }
    }

    private class AndroidHandler(private val app: Context) : LinkHandler {
        private val main = Handler(Looper.getMainLooper())

        override fun play(queue: List<Channel>, index: Int) {
            AppLog.i("link", "A phone sent ${queue.size} channels to play (starting at ${index + 1})")
            main.post {
                runCatching {
                    app.startActivity(PlayerActivity.intent(app, queue, index, StreamFormat.Auto).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { AppLog.e("link", "Couldn't open the player (keep Stream Desk open on the TV)", it) }
            }
        }

        override fun command(action: String) {
            AppLog.i("link", "A phone sent: $action")
            LinkBus.send(action)
        }

        override fun login(request: LoginRequest) {
            AppLog.i("link", "A phone sent a login for ${request.login.base}")
            main.post {
                ChannelStore.get(app).startXtream(request.login, request.name, request.enabled, request.updateHours, null)
                request.sport?.let { SportSettingsStore.get(app).save(it) }
            }
        }
    }
}

/** The phone side: finds TVs that announce themselves, and remembers the code for each. */
class TvFinder(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices
    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized
    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { AppLog.e("link", "Looking for TVs failed (code $code)") }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.startsWith("_streamdesk")) return
                synchronized(this@TvFinder) { queue.addLast(info) }
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _devices.value = _devices.value.filterNot { it.name == info.serviceName }
            }
        }
        listener = l
        runCatching { manager.discoverServices(LINK_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { AppLog.e("link", "Couldn't look for TVs", it); listener = null }
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
        queue.clear()
    }

    // Android resolves one service at a time
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(this) {
            if (resolving) return
            queue.removeFirstOrNull()?.also { resolving = true }
        } ?: return
        manager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { done() }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null && ':' !in host) {   // IPv4 only
                    val device = TvDevice(info.serviceName.replace("\\032", " "), host, info.port)
                    _devices.value = _devices.value.filterNot { it.host == host } + device
                    AppLog.i("link", "Found TV \"${device.name}\" at $host:${device.port}")
                }
                done()
            }

            private fun done() {
                synchronized(this@TvFinder) { resolving = false }
                resolveNext()
            }
        })
    }
}

/** Pairing codes entered on the phone, remembered per TV. */
object LinkPrefs {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tvlink", Context.MODE_PRIVATE)
    fun code(context: Context, host: String): String = prefs(context).getString(host, "").orEmpty()
    fun save(context: Context, host: String, code: String) = prefs(context).edit().putString(host, code).apply()
}
