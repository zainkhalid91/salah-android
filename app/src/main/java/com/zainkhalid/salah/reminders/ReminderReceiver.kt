package com.zainkhalid.salah.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> {
                val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
                val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY).orEmpty()
                Notifications.show(context, id, title, body, context.repo.config.value.reminders.sound)
            }
            ReminderScheduler.ACTION_REPLAN -> {
                ReminderScheduler.reschedule(context)
                refreshWidgets(context)
            }
            ReminderScheduler.ACTION_WIDGET_TICK -> refreshWidgets(context)
        }
    }

    private fun refreshWidgets(context: Context) {
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
