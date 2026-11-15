package com.streamdesk.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Watches what the user asked for and writes a problem down when nothing happened: a channel that never started
 * after a tap or CH +/−, a TV guide that never came, a playlist stuck loading. Each problem keeps the log of the
 * moments before it, so "I pressed and nothing happened" can be looked into later.
 */
object Watch {
    private val main = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, Runnable>()

    /** Starts watching [what] (in the user's words, e.g. "Switch to Sport 2"); a problem is written if [done] doesn't come in time. */
    fun start(context: Context, key: String, what: String, timeoutMs: Long, kind: String = "no response") {
        val app = context.applicationContext
        AppLog.i("tap", what)
        val check = Runnable {
            synchronized(pending) { pending.remove(key) }
            Problems.record(app, kind, "$what: nothing after ${timeoutMs / 1000} s")
        }
        synchronized(pending) {
            pending.remove(key)?.let(main::removeCallbacks)
            pending[key] = check
        }
        main.postDelayed(check, timeoutMs)
    }

    /** It happened (or it failed and that was written down already, or the user moved on). */
    fun done(key: String) {
        synchronized(pending) { pending.remove(key) }?.let(main::removeCallbacks)
    }

    fun isWatching(key: String): Boolean = synchronized(pending) { key in pending }
}

/**
 * Notices when the app stops answering taps: the main thread is checked every second, and a freeze of 5 seconds
 * or more is written down with what the app was busy doing.
 */
object FreezeWatch {
    private const val FREEZE_MS = 5_000L
    @Volatile private var started = false

    fun start(context: Context) {
        if (started || android.os.Build.FINGERPRINT == "robolectric") return
        started = true
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            val lastBeat = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
            var reported = false
            while (true) {
                main.post { lastBeat.set(System.currentTimeMillis()) }
                try { Thread.sleep(1_000) } catch (e: InterruptedException) { return@Thread }
                val stuck = System.currentTimeMillis() - lastBeat.get()
                if (stuck >= FREEZE_MS && !reported) {
                    reported = true
                    val where = Looper.getMainLooper().thread.stackTrace.take(12).joinToString("\n") { "  at $it" }
                    AppLog.e("app", "The app stopped answering for ${stuck / 1000} s")
                    Problems.record(app, "freeze", "The app stopped answering taps for ${stuck / 1000} s\n$where")
                } else if (stuck < FREEZE_MS) {
                    reported = false
                }
            }
        }, "freeze-watch").apply { isDaemon = true }.start()
    }
}
