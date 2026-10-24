package com.streamdesk.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer version of the app: what the update file says. */
data class UpdateInfo(val code: Int, val name: String, val apkUrl: String, val notes: String)

/**
 * Updates from inside the app. The app reads a small file (`update.json`) and, when it names a newer version,
 * downloads the APK and hands it to Android's installer. Android asks for one confirmation before installing,
 * and only accepts an APK signed with the same key as the installed app.
 */
object Updates {
    const val UPDATE_URL = "https://raw.githubusercontent.com/yanivr9/live-streams/main/dist/update.json"

    fun parse(text: String, baseUrl: String = UPDATE_URL): UpdateInfo? {
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val code = o.optInt("versionCode", 0)
        val apk = o.optString("apk")
        if (code <= 0 || apk.isBlank()) return null
        val absolute = runCatching { URL(URL(baseUrl), apk).toString() }.getOrNull() ?: return null
        if (!absolute.startsWith("https://")) return null
        return UpdateInfo(code, o.optString("versionName", code.toString()), absolute, o.optString("notes"))
    }

    fun isNewer(info: UpdateInfo, installedCode: Int) = info.code > installedCode

    @Suppress("DEPRECATION")
    fun installedCode(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    /** The newer version if there is one, null if you are up to date. Throws when the server can't be reached. */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val text = fetchText(UPDATE_URL).text
        val info = parse(text) ?: throw IllegalStateException("The update file isn't valid.")
        info.takeIf { isNewer(it, installedCode(context)) }
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "StreamDesk-${info.name}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("The server answered ${conn.responseCode}.")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            if (total > 0 && done != total) throw IllegalStateException("The download was cut off.")
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        file
    }

    /** Opens Android's installer. Returns false when the app first needs permission to install apps. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
