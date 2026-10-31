package com.streamdesk.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/** A newer version of the app: what the update file says. */
data class UpdateInfo(val code: Int, val name: String, val apkUrl: String, val notes: String)

/**
 * "A new version is available". The app reads a small file (`update.json`) and, when it names a newer version, says so
 * and offers the download link; installing stays with the browser and Android's installer, as for the first install.
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

    /**
     * Opens the new version's download link in the browser (phones). The app doesn't install anything itself: an app that
     * can install other apps is what Play Protect blocks on boxes like the Mi Box. Returns false when there's no browser (TVs).
     */
    fun openDownload(context: Context, info: UpdateInfo): Boolean =
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
}
