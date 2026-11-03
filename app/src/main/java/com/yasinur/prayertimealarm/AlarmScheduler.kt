package com.yasinur.prayertimealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    const val EXTRA_PRAYER = "extra_prayer"

    fun scheduleAll(c: Context) {
        Prayer.entries.forEach { schedule(c, it) }
    }

    /** Cancels any pending alarm for [p] and, if enabled, sets the next one (today or tomorrow). */
    fun schedule(c: Context, p: Prayer) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(c, p)
        am.cancel(pi)
        if (!PrayerPrefs.isEnabled(c, p)) return

        val triggerAt = nextTrigger(PrayerPrefs.hour(c, p), PrayerPrefs.minute(c, p))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            val showIntent = PendingIntent.getActivity(
                c, 100 + p.id, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
        }
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!t.after(now)) t.add(Calendar.DAY_OF_YEAR, 1)
        return t.timeInMillis
    }

    private fun alarmIntent(c: Context, p: Prayer): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_PRAYER, p.name)
        return PendingIntent.getBroadcast(
            c, p.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
