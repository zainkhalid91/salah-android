package com.zainkhalid.salah.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.zainkhalid.salah.repo
import salah.core.NotificationPlanner
import java.time.Instant
import java.time.LocalTime

/**
 * Turns the shared [NotificationPlanner] plan into exact alarms. The plan covers
 * three days, so a replan alarm just after local midnight keeps it rolling.
 */
object ReminderScheduler {
    const val ACTION_REMIND = "com.zainkhalid.salah.REMIND"
    const val ACTION_REPLAN = "com.zainkhalid.salah.REPLAN"
    const val ACTION_WIDGET_TICK = "com.zainkhalid.salah.WIDGET_TICK"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"

    private const val PREFS = "salah_alarms"
    private const val KEY_IDS = "ids"

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Cancel whatever the last plan set.
        for (id in prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()) {
            val pi = PendingIntent.getBroadcast(
                context, 0, remindIntent(context, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }

        val config = context.repo.config.value
        val now = Instant.now()
        val plan = NotificationPlanner.plan(now, config)
        for (n in plan) {
            val intent = remindIntent(context, n.id)
                .putExtra(EXTRA_TITLE, n.title)
                .putExtra(EXTRA_BODY, n.body)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setAlarm(context, n.fireDate.toEpochMilli(), pi)
        }
        prefs.edit { putStringSet(KEY_IDS, plan.map { it.id }.toSet()) }

        // Replan a minute after the next local midnight.
        val zone = config.location?.zone ?: java.time.ZoneId.systemDefault()
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atTime(LocalTime.of(0, 1)).atZone(zone).toInstant()
        setAlarm(context, midnight.toEpochMilli(), broadcast(context, ACTION_REPLAN))
    }

    /** Wakes up at [at] to redraw widgets (a prayer starts, NOW ends, a new day). */
    fun scheduleWidgetTick(context: Context, at: Instant) {
        setAlarm(context, at.toEpochMilli(), broadcast(context, ACTION_WIDGET_TICK))
    }

    private fun remindIntent(context: Context, id: String) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            // The data URI keeps each reminder's PendingIntent distinct.
            .setData(Uri.parse("salah://reminder/$id"))
            .putExtra(EXTRA_ID, id)

    private fun broadcast(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setAlarm(context: Context, atMillis: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 60_000L, pi)
            }
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 60_000L, pi)
        }
    }
}
