package salah.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.chrono.HijrahDate

/**
 * Islamic days of the year, by Hijri month and day.
 * [days] > 1 marks a run of days, e.g. the three days of Tashreeq.
 * [major] events get their own alert; seasons (last ten nights, first ten days) are softer.
 */
enum class IslamicEvent(val month: Int, val day: Int, val major: Boolean, val days: Int = 1) {
    NEW_YEAR(1, 1, true),
    ASHURA(1, 10, true),
    MAWLID(3, 12, true),
    ISRA_MIRAJ(7, 27, true),
    MID_SHABAN(8, 15, true),
    RAMADAN_START(9, 1, true),
    LAST_TEN_NIGHTS(9, 21, false),
    LAYLAT_AL_QADR(9, 27, true),
    EID_AL_FITR(10, 1, true),
    DHUL_HIJJAH_TEN(12, 1, false),
    ARAFAH(12, 9, true),
    EID_AL_ADHA(12, 10, true),
    TASHREEQ(12, 11, false, days = 3);

    /** True on every day the event covers. */
    fun covers(h: HijriDate): Boolean = h.month == month && h.day >= day && h.day < day + days

    /** True only on the first day. */
    fun startsOn(h: HijriDate): Boolean = h.month == month && h.day == day

    companion object {
        fun on(h: HijriDate): List<IslamicEvent> = entries.filter { it.covers(h) }
    }
}

/** One cell of a month grid. */
data class CalendarDay(
    val date: LocalDate,
    val hijri: HijriDate,
    /** False for the leading and trailing days that belong to the neighbouring months. */
    val inMonth: Boolean,
    val events: List<IslamicEvent>,
    /** 13th to 15th, not counting the days of Tashreeq. */
    val whiteDay: Boolean,
)

/** A month laid out in whole weeks, starting on [firstDayOfWeek]. */
data class MonthGrid(
    val primary: PrimaryCalendar,
    /** For a Hijri grid, the Hijri year and month; for a Gregorian grid, the Gregorian ones. */
    val year: Int,
    val month: Int,
    val firstDayOfWeek: DayOfWeek,
    val days: List<CalendarDay>,
) {
    val weeks: List<List<CalendarDay>> get() = days.chunked(7)
    val inMonth: List<CalendarDay> get() = days.filter { it.inMonth }
}

/**
 * Hijri and Gregorian conversion and month grids, using the Umm al-Qura calendar
 * with the user's ±2 day adjustment for local moon sighting.
 *
 * Java's Umm al-Qura data covers 1300 to 1600 AH (about 1882 to 2174), so
 * anything outside that returns null instead of a wrong date.
 */
object IslamicCalendar {
    const val MIN_HIJRI_YEAR = 1300
    const val MAX_HIJRI_YEAR = 1600

    fun toHijri(date: LocalDate, adjustment: Int = 0): HijriDate? = runCatching { HijriDate.of(date, adjustment) }.getOrNull()

    fun toGregorian(year: Int, month: Int, day: Int, adjustment: Int = 0): LocalDate? = runCatching {
        LocalDate.from(HijrahDate.of(year, month, day)).minusDays(adjustment.coerceIn(-2, 2).toLong())
    }.getOrNull()

    /** 29 or 30, or null outside the supported range. */
    fun monthLength(year: Int, month: Int): Int? = runCatching { HijrahDate.of(year, month, 1).lengthOfMonth() }.getOrNull()

    fun isWhiteDay(h: HijriDate): Boolean = h.day in 13..15 && !(h.month == 12 && h.day == 13)

    fun day(date: LocalDate, adjustment: Int, inMonth: Boolean = true): CalendarDay? {
        val h = toHijri(date, adjustment) ?: return null
        return CalendarDay(date, h, inMonth, IslamicEvent.on(h), isWhiteDay(h))
    }

    /** Hijri month [month] of [year], padded to whole weeks. */
    fun hijriMonth(year: Int, month: Int, adjustment: Int, firstDayOfWeek: DayOfWeek): MonthGrid? {
        val length = monthLength(year, month) ?: return null
        val first = toGregorian(year, month, 1, adjustment) ?: return null
        val last = first.plusDays(length - 1L)
        return grid(PrimaryCalendar.HIJRI, year, month, first, last, adjustment, firstDayOfWeek)
    }

    fun gregorianMonth(month: YearMonth, adjustment: Int, firstDayOfWeek: DayOfWeek): MonthGrid? =
        grid(PrimaryCalendar.GREGORIAN, month.year, month.monthValue, month.atDay(1), month.atEndOfMonth(), adjustment, firstDayOfWeek)

    private fun grid(
        primary: PrimaryCalendar, year: Int, month: Int, first: LocalDate, last: LocalDate,
        adjustment: Int, firstDayOfWeek: DayOfWeek,
    ): MonthGrid? {
        val lead = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
        val start = first.minusDays(lead.toLong())
        val total = ((lead + first.until(last).days + 1 + 6) / 7) * 7
        val days = (0 until total).map { i ->
            val d = start.plusDays(i.toLong())
            day(d, adjustment, inMonth = !d.isBefore(first) && !d.isAfter(last)) ?: return null
        }
        return MonthGrid(primary, year, month, firstDayOfWeek, days)
    }

    /** Hijri year and month after adding [delta] months. */
    fun shiftHijriMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val index = year * 12 + (month - 1) + delta
        return Math.floorDiv(index, 12) to Math.floorMod(index, 12) + 1
    }

    /** Every event in a Hijri year with its Gregorian start date, in order. */
    fun yearEvents(hijriYear: Int, adjustment: Int): List<Pair<IslamicEvent, LocalDate>> =
        IslamicEvent.entries.mapNotNull { e -> toGregorian(hijriYear, e.month, e.day, adjustment)?.let { e to it } }

    /** Hijri year of today's date, for "this year" lists. */
    fun hijriYear(date: LocalDate, adjustment: Int): Int? = toHijri(date, adjustment)?.year

}
