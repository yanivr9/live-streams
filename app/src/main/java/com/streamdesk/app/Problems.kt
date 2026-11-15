package com.streamdesk.app

import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Something that went wrong: a crash, a channel that wouldn't play, a failed sign-in. */
data class Problem(val time: Long, val kind: String, val message: String, val log: String = "", val network: String = "") {
    fun toJson(): JSONObject = JSONObject().put("time", time).put("kind", kind).put("message", message).put("log", log).put("network", network)

    companion object {
        fun fromJson(o: JSONObject) = Problem(o.optLong("time"), o.optString("kind"), o.optString("message"), o.optString("log"), o.optString("network"))
    }
}

/**
 * Problems are written down as they happen, on the phone and on the TV, so they can be sent as one report:
 * from the phone with Android's share menu, and from the TV through the phone (⋮ → Problems from TV).
 * Passwords never get in: messages go through the same redaction as the activity log.
 */
object Problems {
    private const val FILE = "problems.json"
    private const val MAX = 30
    private const val LOG_LINES = 120
    private const val DETAILED = 5

    /** How much of the log is kept with each problem: what happened in the last 2 minutes before it. */
    private const val SNAPSHOT_MS = 2 * 60_000L
    private const val SNAPSHOT_LINES = 80
    /** The same problem again within this time is only noted in the log (a channel failing on every retry isn't 10 reports). */
    private const val REPEAT_MS = 2 * 60_000L

    @Synchronized
    fun record(context: Context, kind: String, message: String) {
        val now = System.currentTimeMillis()
        val text = AppLog.redact(message).take(2_000)
        val earlier = read(context)
        if (earlier.any { it.kind == kind && it.message == text && now - it.time < REPEAT_MS }) {
            AppLog.i("problem", "again: $kind: ${text.lineSequence().firstOrNull().orEmpty()}")
            return
        }
        val problem = Problem(now, kind, text, AppLog.recent(SNAPSHOT_MS, SNAPSHOT_LINES), networkName(context))
        val list = (earlier + problem).takeLast(MAX)
        write(context, list)
        prefs(context).edit().putInt("unseen", prefs(context).getInt("unseen", 0) + 1).apply()
        AppLog.i("problem", "$kind: ${AppLog.redact(message).lineSequence().firstOrNull().orEmpty()}")
    }

    /** Problems since the user last looked (or sent a report). */
    fun unseen(context: Context): Int = prefs(context).getInt("unseen", 0)

    fun markSeen(context: Context) = prefs(context).edit().putInt("unseen", 0).apply()

    fun all(context: Context): List<Problem> = read(context)

    @Synchronized
    fun clear(context: Context) {
        write(context, emptyList())
        markSeen(context)
    }

    /** One text with the device, the problems and the end of the activity log: what to paste to whoever fixes the app. */
    fun report(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val device = if (context.isTelevision()) "TV box" else "phone"
        val clock = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)
        val problems = read(context)
        return buildString {
            appendLine("Livio $version problem report · $device · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            appendLine()
            if (problems.isEmpty()) appendLine("No problems recorded.")
            else problems.forEach { appendLine("${clock.format(Date(it.time))} [${it.kind}] ${it.message}") }
            // What led up to the latest problems, as it was at that moment (the log below may have moved on since)
            problems.filter { it.log.isNotBlank() }.takeLast(DETAILED).forEach {
                appendLine()
                appendLine("── ${clock.format(Date(it.time))} [${it.kind}] ${it.message.lineSequence().firstOrNull().orEmpty()}" + (if (it.network.isNotBlank()) " · ${it.network}" else ""))
                appendLine(it.log)
            }
            appendLine()
            appendLine("Last activity:")
            append(AppLog.text().lines().takeLast(LOG_LINES).joinToString("\n"))
        }
    }

    /** Opens Android's share menu (WhatsApp, email, …) with the report. */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Livio problem report")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Send problem report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun prefs(context: Context) = context.getSharedPreferences("problems", Context.MODE_PRIVATE)

    private fun read(context: Context): List<Problem> = runCatching {
        val arr = JSONArray(File(context.filesDir, FILE).readText())
        List(arr.length()) { Problem.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun write(context: Context, list: List<Problem>) {
        runCatching { File(context.filesDir, FILE).writeText(JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString()) }
    }
}

/** "Wi-Fi", "mobile data", "Ethernet" or "no internet", for the report. */
fun networkName(context: Context): String = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "no internet"
    when {
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
        else -> "other network"
    }
}.getOrDefault("")
