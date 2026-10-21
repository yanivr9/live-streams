package com.streamdesk.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The in-app activity log (Settings → Logs): network requests, sign-ins and playback events, so problems
 * on a phone can be read and copied without a computer. Account usernames and passwords are always hidden.
 */
object AppLog {
    private const val MAX_LINES = 600
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    /** Loads the log kept from earlier runs. Call once at app start. */
    fun init(dir: File) {
        val f = File(dir, "activity-log.txt")
        file = f
        val earlier = runCatching { f.readLines() }.getOrDefault(emptyList()).takeLast(MAX_LINES)
        synchronized(this) { _lines.value = earlier + _lines.value }
    }

    fun i(tag: String, message: String) = add(tag, message)

    fun e(tag: String, message: String, error: Throwable? = null) =
        add(tag, "ERROR " + message + (error?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))

    fun clear() {
        synchronized(this) { _lines.value = emptyList() }
        writer.execute { runCatching { file?.writeText("") } }
    }

    fun text(): String = _lines.value.joinToString("\n")

    private fun add(tag: String, message: String) {
        val line = "${time.format(Date())} [$tag] ${redact(message)}"
        val all = synchronized(this) {
            (_lines.value + line).takeLast(MAX_LINES).also { _lines.value = it }
        }
        val f = file ?: return
        writer.execute { runCatching { f.writeText(all.joinToString("\n", postfix = "\n")) } }
    }

    // Stream and API addresses carry the account's username and password
    fun redact(text: String) = text
        .replace(Regex("""(password|username)=[^&\s"]+"""), "$1=***")
        .replace(Regex("""/(live|movie|series|timeshift)/[^/\s]+/[^/\s]+/"""), "/$1/***/***/")
}

/** A short name for a client identity in logs: "VLC", "Chrome", … */
fun agentName(ua: String): String = when {
    ua.startsWith("VLC") -> "VLC"
    ua.contains("Chrome") -> "Chrome"
    ua.startsWith("Lavf") -> "FFmpeg"
    ua.startsWith("okhttp") -> "OkHttp"
    else -> ua.take(24)
}
