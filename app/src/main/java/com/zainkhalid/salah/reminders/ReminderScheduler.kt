package com.zainkhalid.salah.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import com.zainkhalid.salah.repo
import salah.core.IslamicAlertPlanner
import salah.core.NotificationPlanner
import salah.core.Prayer
import salah.core.PrayerSchedule
import salah.core.localDate
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
    const val EXTRA_AZAN = "azan"
    const val EXTRA_SILENT = "silent"
    /** A prayer begins: pause the Quran player and run Focus Mode. */
    const val ACTION_PRAYER_TIME = "com.zainkhalid.salah.PRAYER_TIME"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_PRAYER_TIME = "prayerTime"

    private const val PREFS = "salah_alarms"
    private const val KEY_IDS = "ids"
    private const val KEY_MOMENTS = "moments"

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
                .putExtra(EXTRA_AZAN, NotificationPlanner.playsAzan(n, config.reminders))
                .putExtra(EXTRA_SILENT, n.silent)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setAlarm(context, n.fireDate.toEpochMilli(), pi)
        }
        // Islamic dates, in the app's language.
        val phone = context.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
        val alerts = IslamicAlertPlanner.plan(now, config, config.display.lang.locale(phone))
        for (a in alerts) {
            val intent = remindIntent(context, a.id)
                .putExtra(EXTRA_TITLE, a.title)
                .putExtra(EXTRA_BODY, a.body)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setAlarm(context, a.fireDate.toEpochMilli(), pi)
        }
        prefs.edit { putStringSet(KEY_IDS, (plan.map { it.id } + alerts.map { it.id }).toSet()) }

        schedulePrayerMoments(context, now)

        // Replan a minute after the next local midnight.
        val zone = config.location?.zone ?: java.time.ZoneId.systemDefault()
        val midnight = now.atZone(zone).toLocalDate().plusDays(1).atTime(LocalTime.of(0, 1)).atZone(zone).toInstant()
        setAlarm(context, midnight.toEpochMilli(), broadcast(context, ACTION_REPLAN))
    }

    /**
     * One alarm at each prayer time today and tomorrow, independent of reminders, for
     * "pause at prayer time" and Focus Mode.
     */
    private fun schedulePrayerMoments(context: Context, now: Instant) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (key in prefs.getStringSet(KEY_MOMENTS, emptySet()).orEmpty()) {
            PendingIntent.getBroadcast(context, 0, momentIntent(context, key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
                ?.let { am.cancel(it); it.cancel() }
        }
        val config = context.repo.config.value
        val location = config.location?.takeIf { it.isValid }
        val wanted = config.quran.pauseAtPrayerTime || config.focusMode.enabled
        val keys = mutableSetOf<String>()
        if (location != null && wanted) {
            val today = localDate(now, location.zone)
            for (day in PrayerSchedule.range(today, 2, location, config.calculation)) {
                for (p in Prayer.prayers) {
                    val t = day.time(p) ?: continue
                    if (t <= now) continue
                    val key = "${day.date}.${p.raw}"
                    val intent = momentIntent(context, key)
                        .putExtra(EXTRA_PRAYER, p.raw)
                        .putExtra(EXTRA_PRAYER_TIME, t.toEpochMilli())
                    val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    setAlarm(context, t.toEpochMilli(), pi)
                    keys += key
                }
            }
        }
        prefs.edit { putStringSet(KEY_MOMENTS, keys) }
    }

    private fun momentIntent(context: Context, key: String) =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_PRAYER_TIME)
            .setData(Uri.parse("salah://prayer/$key"))

    /** Sets an exact alarm (or a one-minute window without the permission) for [intent]. */
    fun setAt(context: Context, at: Instant, intent: Intent, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarm(context, at.toEpochMilli(), pi)
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
