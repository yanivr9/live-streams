package com.streamdesk.app

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/** Records a crash so the next launch can show what happened (the app has no other way to report it). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(filesDir)
        if (isTelevision()) LinkReceiver.start(this)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        AppLog.i("app", "Started Stream Desk $version on Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            AppLog.e("app", "Crashed", e)
            runCatching {
                val report = "Stream Desk $version · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}\n\n" +
                    redact(e.stackTraceToString()).take(8_000)
                File(filesDir, CRASH_FILE).writeText(report)
            }
            runCatching { Problems.record(this, "crash", e.stackTraceToString().lines().take(12).joinToString("\n")) }
            previous?.uncaughtException(thread, e)
        }
    }

    companion object {
        private const val CRASH_FILE = "last-crash.txt"

        /** The report from the last crash, once; null if the app didn't crash. */
        fun takeCrashReport(context: Context): String? {
            val file = File(context.filesDir, CRASH_FILE)
            if (!file.exists()) return null
            return runCatching { file.readText() }.getOrNull().also { file.delete() }
        }

        private fun redact(text: String) = AppLog.redact(text)
    }
}
