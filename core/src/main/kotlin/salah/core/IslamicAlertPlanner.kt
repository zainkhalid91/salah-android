package salah.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** An Islamic date alert. One per day at most; several things on one day share it. */
data class PlannedAlert(
    /** `salah.cal.<yyyy-MM-dd>` */
    val id: String,
    val fireDate: Instant,
    val date: LocalDate,
    val title: String,
    val body: String,
)

/**
 * Plans alerts for the start of each Islamic month and for special days, in
 * the user's language. By default they fire at Maghrib the evening before,
 * because that is when the Islamic day begins.
 */
object IslamicAlertPlanner {
    const val ID_PREFIX = "salah.cal."
    const val DEFAULT_DAYS = 60
    const val MAX_PENDING = 16
    private val MORNING = LocalTime.of(8, 0)
    private val EVENING_FALLBACK = LocalTime.of(18, 0)

    fun plan(now: Instant, config: SalahConfig, locale: Locale = Locale.getDefault(), days: Int = DEFAULT_DAYS): List<PlannedAlert> {
        val cal = config.calendar
        if (!cal.notifyNewMonth && !cal.notifySpecialDays && !cal.notifyWhiteDays) return emptyList()
        val location = config.location?.takeIf { it.isValid }
        val zone = location?.zone ?: java.time.ZoneId.systemDefault()
        val adjustment = config.display.hijriAdjustment
        val lang = CalendarText.lang(locale)
        val today = localDate(now, zone)
        val out = mutableListOf<PlannedAlert>()

        for (i in 0..days) {
            val date = today.plusDays(i.toLong())
            val h = IslamicCalendar.toHijri(date, adjustment) ?: continue
            val titles = mutableListOf<String>()
            val events = IslamicEvent.entries.filter { it.startsOn(h) }
            if (cal.notifySpecialDays) events.forEach { titles += CalendarText.event(it, lang) }
            if (cal.notifyNewMonth && h.day == 1 && (!cal.notifySpecialDays || events.none { it.major })) {
                titles += CalendarText.text(CalKey.NEW_MONTH_BEGINS, lang)
                    .replace("%s", CalendarText.hijriMonthYear(h.year, h.month, lang))
            }
            if (cal.notifyWhiteDays && h.day == 13 && h.month != 12) titles += CalendarText.text(CalKey.WHITE_DAYS, lang)
            if (titles.isEmpty()) continue

            val fire = when (cal.alertTime) {
                IslamicAlertTime.MORNING -> date.atTime(MORNING).atZone(zone).toInstant()
                IslamicAlertTime.MAGHRIB_BEFORE -> {
                    val eve = date.minusDays(1)
                    location?.let { PrayerSchedule.forDate(eve, it, config.calculation).time(Prayer.MAGHRIB) }
                        ?: eve.atTime(EVENING_FALLBACK).atZone(zone).toInstant()
                }
            }
            if (fire <= now) continue
            if (config.reminders.isPaused(fire)) continue

            val hijriLine = CalendarText.hijri(h, lang)
            val body = if (cal.alertTime == IslamicAlertTime.MAGHRIB_BEFORE) {
                "${CalendarText.text(CalKey.BEGINS_TONIGHT, lang)} · $hijriLine"
            } else {
                "$hijriLine · ${CalendarText.gregorian(date, locale)}"
            }
            out += PlannedAlert("$ID_PREFIX$date", fire, date, titles.joinToString(" · "), body)
        }
        return out.sortedBy { it.fireDate }.take(MAX_PENDING)
    }
}
