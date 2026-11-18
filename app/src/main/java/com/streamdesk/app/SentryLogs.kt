package com.streamdesk.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.logger.SentryLogParameters

/**
 * Sends the activity log to Sentry (project "livio"), so what happened on a phone or a TV box can be read from
 * anywhere: sentry.io → Explore → Logs. Every [AppLog] line goes there as it is written, with its tag ("net",
 * "player", …) and already without account usernames or passwords. Crashes and freezes show up under Issues.
 * Off in the screen tests, which run on a simulated Android.
 */
object SentryLogs {
    /** The project's client key: it can only send reports, not read them, so it can sit in the app. */
    private const val DSN = "https://81c2a27eef30b937287c20b3fc907202@o4512435201048576.ingest.de.sentry.io/4512453565218896"

    @Volatile
    private var started = false

    /** [evenInTests]: only for SentryCheckTest, which sends one line on request to see that reports arrive. */
    fun start(app: Application, evenInTests: Boolean = false) {
        if (Build.FINGERPRINT == "robolectric" && !evenInTests) return
        runCatching {
            val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            SentryAndroid.init(app) { options ->
                options.dsn = DSN
                options.environment = if (debuggable) "debug" else "production"
                options.logs.isEnabled = true
                // Stream and API addresses carry the account's username and password: hidden in crash reports too
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> redact(event) }
                options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { crumb, _ -> redact(crumb) }
            }
            // Which kind of device a log line or crash came from
            val kind = if (app.isTelevision()) "tv" else "phone"
            Sentry.setTag("device.kind", kind)
            Sentry.setAttribute("device.kind", kind)
            started = true
        }.onFailure { android.util.Log.w("Livio", "Sentry not started", it) }
    }

    /** One activity-log line (already redacted), sent as a Sentry log. */
    fun send(tag: String, message: String, isError: Boolean) {
        if (!started) return
        runCatching {
            Sentry.logger().log(
                if (isError) SentryLogLevel.ERROR else SentryLogLevel.INFO,
                SentryLogParameters.create(SentryAttributes.of(SentryAttribute.stringAttribute("log.tag", tag))),
                "[$tag] $message",
            )
        }
    }

    internal fun redact(event: SentryEvent): SentryEvent {
        event.exceptions?.forEach { it.value = it.value?.let(AppLog::redact) }
        event.message?.let { message ->
            message.formatted = message.formatted?.let(AppLog::redact)
            message.message = message.message?.let(AppLog::redact)
        }
        event.breadcrumbs?.forEach { redact(it) }
        return event
    }

    internal fun redact(crumb: Breadcrumb): Breadcrumb {
        crumb.message = crumb.message?.let(AppLog::redact)
        for ((key, value) in crumb.data.entries.toList()) {
            if (value is String) crumb.setData(key, AppLog.redact(value))
        }
        return crumb
    }
}
