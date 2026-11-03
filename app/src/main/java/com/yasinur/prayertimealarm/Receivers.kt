package com.yasinur.prayertimealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at a prayer time: plays the azan and schedules the same prayer for the next day. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = Prayer.fromName(intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER)) ?: return
        AzanService.start(context, prayer)
        AlarmScheduler.schedule(context, prayer)
    }
}

/** Alarms are cleared on reboot / time change, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
    }
}
