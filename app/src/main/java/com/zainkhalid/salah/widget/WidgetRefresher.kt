package com.zainkhalid.salah.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.zainkhalid.salah.reminders.ReminderScheduler
import com.zainkhalid.salah.repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import salah.core.PrayerClock
import java.time.Instant
import java.time.LocalTime

object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshNow(app) }
    }

    suspend fun refreshNow(context: Context) {
        runCatching {
            NextPrayerWidget().updateAll(context)
            TodayWidget().updateAll(context)
            CalendarWidget().updateAll(context)
        }
        scheduleNextTick(context)
    }

    /** Next moment a widget's content changes: a prayer starts, NOW ends, or a new day. */
    private suspend fun scheduleNextTick(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val any = manager.getGlanceIds(NextPrayerWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(TodayWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(CalendarWidget::class.java).isNotEmpty()
        if (!any) return
        val config = context.repo.config.value
        val location = config.location?.takeIf { it.isValid } ?: return
        val now = Instant.now()
        val state = PrayerClock.state(now, location, config.calculation, config.display.nowWindowMinutes)
        val zone = location.zone
        val candidates = buildList {
            state.next?.time?.let { add(it) }
            state.nowPrayer?.let { p -> state.today.time(p)?.plusSeconds(config.display.nowWindowMinutes * 60L)?.let { add(it) } }
            add(now.atZone(zone).toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(zone).toInstant())
        }
        val at = candidates.filter { it > now }.minOrNull() ?: return
        ReminderScheduler.scheduleWidgetTick(context, at.plusSeconds(1))
    }
}
