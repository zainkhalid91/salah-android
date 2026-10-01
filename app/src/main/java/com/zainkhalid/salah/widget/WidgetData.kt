package com.zainkhalid.salah.widget

import android.content.Context
import com.zainkhalid.salah.data.AccentColor
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.ui.isDark
import salah.core.HijriDate
import salah.core.PrayerClock
import salah.core.PrayerClockState
import salah.core.SalahConfig
import java.time.Instant

/** Everything a widget needs for one redraw. */
class WidgetData(
    val config: SalahConfig,
    val state: PrayerClockState?,
    val now: Instant,
    val colors: WidgetColors,
    val style: WidgetStyle,
) {
    val hijri: String?
        get() = state?.let { HijriDate.of(it.today.date, config.display.hijriAdjustment).formatted }

    companion object {
        fun load(context: Context, appWidgetId: Int): WidgetData {
            val repo = context.repo
            val config = repo.config.value
            val accent: AccentColor = repo.accent.value
            val now = Instant.now()
            val state = config.location?.takeIf { it.isValid }?.let {
                PrayerClock.state(now, it, config.calculation, config.display.nowWindowMinutes)
            }
            val style = WidgetStyles.load(context, appWidgetId)
            val colors = WidgetStyles.colors(context, style, accent, context.isDark(config.display.theme))
            return WidgetData(config, state, now, colors, style)
        }
    }
}
