package com.zainkhalid.salah.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zainkhalid.salah.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms don't survive a reboot or a clock change, so plan them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.reschedule(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetRefresher.refreshNow(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
