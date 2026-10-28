package com.streamdesk.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** "Sound with the screen off": the phone keeps playing the channel's sound after the screen turns off or the app is left. */
object BackgroundAudio {
    private fun prefs(context: Context) = context.getSharedPreferences("launch", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("background_audio", true)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("background_audio", on).apply()

    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The notification's Stop button was pressed. */
    val stopRequests: SharedFlow<Unit> = _stopRequests

    internal fun requestStop() { _stopRequests.tryEmit(Unit) }
}

/**
 * Keeps the app running while a channel plays with the screen off. Android would otherwise stop the app's network and sound
 * soon after the screen turns off. Shows a notification with the channel name and a Stop button.
 */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BackgroundAudio.requestStop()
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Playing in the background", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Stream Desk" })
            .setContentText("Playing · tap to open")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.streamdesk.app.STOP_PLAYBACK"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, title: String) =
            Intent(context, PlaybackService::class.java).putExtra(EXTRA_TITLE, title)
    }
}
