package com.yasinur.prayertimealarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Foreground service that plays the azan audio once and shows a notification with a stop button. */
class AzanService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prayer = Prayer.fromName(intent?.getStringExtra(AlarmScheduler.EXTRA_PRAYER))
        val title = prayer?.let { "${it.banglaName} নামাজের সময় হয়েছে" } ?: "টেস্ট আজান"
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        play()
        return START_NOT_STICKY
    }

    private fun play() {
        player?.release()
        player = null

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val mp = MediaPlayer()

        // Try each source until one loads (e.g. the chosen file may have been deleted).
        val loaded = candidateUris().any { uri ->
            try {
                mp.reset()
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                true
            } catch (e: Exception) {
                false
            }
        }
        if (!loaded) {
            mp.release()
            stopSelf()
            return
        }

        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { stopSelf() }
        mp.setOnErrorListener { _, _, _ -> stopSelf(); true }
        mp.prepareAsync()
        player = mp
    }

    /** User-selected file first, then a bundled res/raw/azan file if present, then the system alarm sound. */
    private fun candidateUris(): List<Uri> {
        val list = mutableListOf<Uri>()
        PrayerPrefs.azanUri(this)?.let { list.add(it) }
        val rawId = resources.getIdentifier("azan", "raw", packageName)
        if (rawId != 0) list.add(Uri.parse("android.resource://$packageName/$rawId"))
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { list.add(it) }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { list.add(it) }
        return list
    }

    private fun buildNotification(title: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)

        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, AzanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("আজান হচ্ছে…")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setDeleteIntent(stopIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "azan_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.yasinur.prayertimealarm.STOP"

        fun start(c: Context, prayer: Prayer?) {
            val intent = Intent(c, AzanService::class.java)
            prayer?.let { intent.putExtra(AlarmScheduler.EXTRA_PRAYER, it.name) }
            ContextCompat.startForegroundService(c, intent)
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, AzanService::class.java))
        }
    }
}
