package com.streamdesk.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The player's choices from ☰ → Settings on a phone. */
object PlayerPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    /** Leaving the app while a channel plays shrinks it into a small window (on by default). */
    fun pipOnLeave(context: Context): Boolean = prefs(context).getBoolean("pip_on_leave", true)
    fun setPipOnLeave(context: Context, on: Boolean) = prefs(context).edit().putBoolean("pip_on_leave", on).apply()

    /** The big CH + / CH − buttons over the picture (shown by default). */
    fun channelButtons(context: Context): Boolean = prefs(context).getBoolean("channel_buttons", true)
    fun setChannelButtons(context: Context, on: Boolean) = prefs(context).edit().putBoolean("channel_buttons", on).apply()
}

/** Whether the phone is on mobile data right now (not Wi-Fi or a cable), kept up to date while on screen. */
fun isOnMobileData(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

@Composable
fun rememberOnMobileData(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isOnMobileData(context)) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { state.value = isOnMobileData(context) }
            override fun onLost(network: Network) { state.value = isOnMobileData(context) }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return state
}
