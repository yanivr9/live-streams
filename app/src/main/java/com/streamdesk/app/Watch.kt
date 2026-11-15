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
    /** How many of the app's screens are on top right now: a freeze only counts while someone can tap. */
    private val resumed = java.util.concurrent.atomic.AtomicInteger(0)

    /** Whether the main thread is just waiting for work (then nothing is stuck: the phone was asleep or the app paused). */
    internal fun isIdle(stack: List<StackTraceElement>): Boolean =
        stack.firstOrNull()?.let { it.className == "android.os.MessageQueue" && it.methodName.startsWith("nativePollOnce") } == true

    fun start(context: Context) {
        if (started || android.os.Build.FINGERPRINT == "robolectric") return
        started = true
        val app = context.applicationContext
        (app as? android.app.Application)?.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: android.app.Activity) { resumed.incrementAndGet() }
            override fun onActivityPaused(a: android.app.Activity) { resumed.updateAndGet { (it - 1).coerceAtLeast(0) } }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        val main = Handler(Looper.getMainLooper())
        Thread({
            // uptimeMillis stops while the phone sleeps, so a screen that was off isn't taken for a freeze
            val lastBeat = java.util.concurrent.atomic.AtomicLong(android.os.SystemClock.uptimeMillis())
            var reported = false
            while (true) {
                main.post { lastBeat.set(android.os.SystemClock.uptimeMillis()) }
                try { Thread.sleep(1_000) } catch (e: InterruptedException) { return@Thread }
                val stuck = android.os.SystemClock.uptimeMillis() - lastBeat.get()
                if (resumed.get() == 0) { lastBeat.set(android.os.SystemClock.uptimeMillis()); reported = false; continue }
                if (stuck >= FREEZE_MS && !reported) {
                    reported = true
                    val stack = Looper.getMainLooper().thread.stackTrace.toList()
                    if (isIdle(stack)) {
                        AppLog.i("app", "Main thread late by ${stuck / 1000} s but idle: not a freeze")
                        continue
                    }
                    val where = stack.take(12).joinToString("\n") { "  at $it" }
                    AppLog.e("app", "The app stopped answering for ${stuck / 1000} s")
                    Problems.record(app, "freeze", "The app stopped answering taps for ${stuck / 1000} s\n$where")
                } else if (stuck < FREEZE_MS) {
                    reported = false
                }
            }
        }, "freeze-watch").apply { isDaemon = true }.start()
    }
}
