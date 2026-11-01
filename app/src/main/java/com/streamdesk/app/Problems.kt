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
data class Problem(val time: Long, val kind: String, val message: String) {
    fun toJson(): JSONObject = JSONObject().put("time", time).put("kind", kind).put("message", message)

    companion object {
        fun fromJson(o: JSONObject) = Problem(o.optLong("time"), o.optString("kind"), o.optString("message"))
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

    @Synchronized
    fun record(context: Context, kind: String, message: String) {
        val list = (read(context) + Problem(System.currentTimeMillis(), kind, AppLog.redact(message).take(2_000))).takeLast(MAX)
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
            appendLine("Stream Desk $version problem report · $device · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            appendLine()
            if (problems.isEmpty()) appendLine("No problems recorded.")
            else problems.forEach { appendLine("${clock.format(Date(it.time))} [${it.kind}] ${it.message}") }
            appendLine()
            appendLine("Last activity:")
            append(AppLog.text().lines().takeLast(LOG_LINES).joinToString("\n"))
        }
    }

    /** Opens Android's share menu (WhatsApp, email, …) with the report. */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Stream Desk problem report")
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
