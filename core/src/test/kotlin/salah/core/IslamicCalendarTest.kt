package salah.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IslamicCalendarTest {
    @Test
    fun roundTripsBothWays() {
        var d = LocalDate.of(2024, 1, 1)
        while (d.year < 2028) {
            for (adj in -2..2) {
                val h = IslamicCalendar.toHijri(d, adj)!!
                assertEquals(d, IslamicCalendar.toGregorian(h.year, h.month, h.day, adj), "$d adj $adj")
            }
            d = d.plusDays(3)
        }
    }

    @Test
    fun knownDates() {
        // Umm al-Qura: 1 Ramadan 1445 was 11 March 2024, Eid al-Adha 1445 was 16 June 2024.
        assertEquals(LocalDate.of(2024, 3, 11), IslamicCalendar.toGregorian(1445, 9, 1))
        assertEquals(LocalDate.of(2024, 6, 16), IslamicCalendar.toGregorian(1445, 12, 10))
        assertEquals(HijriDate(1, 1, 1446), IslamicCalendar.toHijri(LocalDate.of(2024, 7, 7)))
    }

    @Test
    fun outOfRangeIsNull() {
        assertNull(IslamicCalendar.toGregorian(1200, 1, 1))
        assertNull(IslamicCalendar.toGregorian(1446, 13, 1))
        assertNull(IslamicCalendar.toGregorian(1446, 1, 31))
    }

    @Test
    fun hijriGridCoversTheWholeMonthInWeeks() {
        val g = IslamicCalendar.hijriMonth(1445, 9, 0, DayOfWeek.SATURDAY)!!
        assertEquals(0, g.days.size % 7)
        assertEquals(DayOfWeek.SATURDAY, g.days.first().date.dayOfWeek)
        assertEquals(IslamicCalendar.monthLength(1445, 9), g.inMonth.size)
        assertTrue(g.inMonth.all { it.hijri.month == 9 })
        assertTrue(IslamicEvent.RAMADAN_START in g.inMonth.first().events)
    }

    @Test
    fun gregorianGridMarksEvents() {
        val g = IslamicCalendar.gregorianMonth(YearMonth.of(2024, 6), 0, DayOfWeek.MONDAY)!!
        val eid = g.inMonth.first { IslamicEvent.EID_AL_ADHA in it.events }
        assertEquals(LocalDate.of(2024, 6, 16), eid.date)
        assertTrue(g.inMonth.single { it.date == LocalDate.of(2024, 6, 18) }.events.contains(IslamicEvent.TASHREEQ))
    }

    @Test
    fun monthShiftWraps() {
        assertEquals(1447 to 1, IslamicCalendar.shiftHijriMonth(1446, 12, 1))
        assertEquals(1445 to 12, IslamicCalendar.shiftHijriMonth(1446, 1, -1))
    }

    @Test
    fun yearEventsAreOrdered() {
        val ev = IslamicCalendar.yearEvents(1446, 0)
        assertEquals(IslamicEvent.entries.size, ev.size)
        assertEquals(ev.map { it.second }.sorted(), ev.map { it.second })
    }

    @Test
    fun localisedText() {
        assertEquals("رمضان", CalendarText.hijriMonth(9, CalLang.AR))
        assertEquals("١٤٤٦", CalendarText.number(1446, CalLang.AR))
        assertEquals("۱۴۴۶", CalendarText.number(1446, CalLang.UR))
        assertEquals(CalLang.UR, CalendarText.lang(Locale("ur", "PK")))
        assertEquals(CalLang.EN, CalendarText.lang(Locale.GERMAN))
        assertEquals("In 3 days", CalendarText.relative(3, CalLang.EN))
        assertTrue(CalendarText.gregorian(LocalDate.of(2026, 10, 2), Locale("ar")).contains("٢٠٢٦"))
    }

    @Test
    fun alertsFireAtMaghribTheEveningBefore() {
        val loc = SavedLocation("Singapore", 1.3521, 103.8198, "Asia/Singapore")
        val config = SalahConfig(location = loc)
        // A week before 1 Ramadan 1445 (11 March 2024).
        val now = Instant.parse("2024-03-04T00:00:00Z")
        val alerts = IslamicAlertPlanner.plan(now, config, Locale.ENGLISH)
        val ramadan = alerts.first { it.date == LocalDate.of(2024, 3, 11) }
        assertEquals("First day of Ramadan", ramadan.title)
        val maghrib = PrayerSchedule.forDate(LocalDate.of(2024, 3, 10), loc, config.calculation).time(Prayer.MAGHRIB)
        assertEquals(maghrib, ramadan.fireDate)
        assertNotNull(alerts.firstOrNull { it.title.startsWith("Shawwal") || it.title == "Eid al-Fitr" })
    }

    @Test
    fun alertsCanBeTurnedOff() {
        val config = SalahConfig(calendar = CalendarSettings(notifyNewMonth = false, notifySpecialDays = false))
        assertTrue(IslamicAlertPlanner.plan(Instant.parse("2024-03-04T00:00:00Z"), config).isEmpty())
    }
}
