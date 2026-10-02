package com.zainkhalid.salah.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zainkhalid.salah.ui.MainActivity
import salah.core.CalKey
import salah.core.CalendarText
import salah.core.IslamicCalendar
import salah.core.MonthGrid
import salah.core.PrimaryCalendar
import salah.core.localDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** This month as a grid, in the user's main calendar and language. */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val data = WidgetData.load(context, appWidgetId)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        provideContent { CalendarContent(data, locale) }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetStyles.delete(context, appWidgetIds)
    }
}

@Composable
private fun CalendarContent(data: WidgetData, locale: Locale) {
    val size = LocalSize.current
    val c = data.colors
    val lang = CalendarText.lang(locale)
    val adj = data.config.display.hijriAdjustment
    val zone = data.config.location?.zone ?: ZoneId.systemDefault()
    val today = localDate(Instant.now(), zone)
    val primary = data.config.calendar.primary
    val firstDay = CalendarText.firstDayOfWeek(locale)
    val grid: MonthGrid? = when (primary) {
        PrimaryCalendar.HIJRI -> IslamicCalendar.toHijri(today, adj)?.let { IslamicCalendar.hijriMonth(it.year, it.month, adj, firstDay) }
        PrimaryCalendar.GREGORIAN -> IslamicCalendar.gregorianMonth(YearMonth.from(today), adj, firstDay)
    }
    val showSecond = size.height.value >= 300f
    val showNext = size.height.value >= 220f

    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(c.background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        if (grid == null) {
            Text(CalendarText.text(CalKey.OUT_OF_RANGE, lang), style = TextStyle(color = ColorProvider(c.text), fontSize = 13.sp))
            return@Column
        }
        val days = grid.inMonth
        val title: String
        val subtitle: String
        if (primary == PrimaryCalendar.HIJRI) {
            title = CalendarText.hijriMonthYear(grid.year, grid.month, lang)
            val a = days.first().date
            val b = days.last().date
            subtitle = "${CalendarText.gregorianMonth(a.monthValue, locale)} – ${CalendarText.gregorianMonth(b.monthValue, locale)} ${CalendarText.number(b.year, lang)}"
        } else {
            title = "${CalendarText.gregorianMonth(grid.month, locale)} ${CalendarText.number(grid.year, lang)}"
            val a = days.first().hijri
            val b = days.last().hijri
            subtitle = "${CalendarText.hijriMonth(a.month, lang)} – ${CalendarText.hijriMonthYear(b.year, b.month, lang)}"
        }
        Text(title, maxLines = 1, style = TextStyle(color = ColorProvider(c.text), fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Text(subtitle, maxLines = 1, style = TextStyle(color = ColorProvider(c.secondary), fontSize = 11.sp))
        Spacer(GlanceModifier.height(6.dp))

        Row(GlanceModifier.fillMaxWidth()) {
            for (cell in grid.weeks.first()) {
                Text(
                    CalendarText.weekdayShort(cell.date.dayOfWeek, locale),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(if (cell.date.dayOfWeek == DayOfWeek.FRIDAY) c.highlight else c.secondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        for (week in grid.weeks) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (cell in week) {
                    val isToday = cell.date == today
                    val marked = cell.events.isNotEmpty()
                    val main = if (primary == PrimaryCalendar.HIJRI) cell.hijri.day else cell.date.dayOfMonth
                    val second = if (primary == PrimaryCalendar.HIJRI) cell.date.dayOfMonth else cell.hijri.day
                    val fg = when {
                        isToday -> c.onHighlight
                        !cell.inMonth -> c.secondary.copy(alpha = 0.45f)
                        marked -> c.highlight
                        else -> c.text
                    }
                    Box(
                        GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .padding(1.dp)
                            .cornerRadius(9.dp)
                            .background(if (isToday) c.highlight else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                CalendarText.number(main, lang),
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(fg),
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday || marked) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            if (showSecond) {
                                Text(
                                    CalendarText.number(second, lang),
                                    maxLines = 1,
                                    style = TextStyle(color = ColorProvider(if (isToday) c.onHighlight else c.secondary), fontSize = 8.sp, textAlign = TextAlign.Center),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showNext) {
            val next = nextEvent(today, adj, lang)
            if (next != null) {
                val (name, date) = next
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    "${CalendarText.text(CalKey.NEXT_EVENT, lang)} · $name · ${CalendarText.relative(ChronoUnit.DAYS.between(today, date), lang)}",
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(c.secondary), fontSize = 11.sp),
                )
            }
        }
    }
}

/** First Islamic day on or after today, looking into next year if needed. */
private fun nextEvent(today: LocalDate, adj: Int, lang: salah.core.CalLang): Pair<String, LocalDate>? {
    val year = IslamicCalendar.hijriYear(today, adj) ?: return null
    return (IslamicCalendar.yearEvents(year, adj) + IslamicCalendar.yearEvents(year + 1, adj))
        .firstOrNull { !it.second.isBefore(today) }
        ?.let { CalendarText.event(it.first, lang) to it.second }
}
