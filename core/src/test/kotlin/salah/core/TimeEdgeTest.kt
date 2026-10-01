package salah.core

import java.time.LocalDate
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeEdgeTest {
    private fun assertOrdered(s: DaySchedule) {
        val times = Prayer.entries.mapNotNull { s.time(it) }
        assertEquals(6, times.size)
        assertEquals(times.sorted(), times, "times out of order on ${s.date}")
        for (t in times) assertEquals(s.date, localDate(t, s.zone), "time falls on another local day")
    }

    @Test
    fun dstTransitionDays() {
        val days = listOf(
            Fixtures.newYork to LocalDate.of(2026, 3, 8), Fixtures.newYork to LocalDate.of(2026, 11, 1),
            Fixtures.london to LocalDate.of(2026, 3, 29), Fixtures.london to LocalDate.of(2026, 10, 25),
        )
        for ((loc, d) in days) for (offset in -1L..1L) {
            assertOrdered(PrayerSchedule.forDate(d.plusDays(offset), loc, CalculationSettings(method = MethodID.NORTH_AMERICA)))
        }
    }

    @Test
    fun dstSpringForwardShiftsLocalClockByAnHour() {
        val s = CalculationSettings(method = MethodID.NORTH_AMERICA)
        val before = PrayerSchedule.forDate(LocalDate.of(2026, 3, 7), Fixtures.newYork, s)
        val after = PrayerSchedule.forDate(LocalDate.of(2026, 3, 8), Fixtures.newYork, s)
        assertTrue(TimeFormatting.clock(before.time(Prayer.DHUHR)!!, Fixtures.newYork.zone, true).startsWith("12:"))
        assertTrue(TimeFormatting.clock(after.time(Prayer.DHUHR)!!, Fixtures.newYork.zone, true).startsWith("13:"))
    }

    @Test
    fun manualLocationIgnoresMachineTimeZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val now = Fixtures.date("2026-09-28T01:00:00+08:00")
            val state = PrayerClock.state(now, Fixtures.singapore, CalculationSettings(), 15)
            assertEquals(LocalDate.of(2026, 9, 28), state.today.date)
            assertEquals(Prayer.FAJR, state.next?.prayer)
            assertEquals(false, state.next?.isTomorrow)
            assertEquals("05:36", TimeFormatting.clock(state.next!!.time, Fixtures.singapore.zone, true))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun afterIshaNextIsTomorrowsFajrAcrossMidnight() {
        val now = Fixtures.date("2026-09-27T22:10:00+08:00")
        val next = NextPrayerResolver.resolve(now, Fixtures.singapore, CalculationSettings())!!
        assertEquals(Prayer.FAJR, next.prayer)
        assertTrue(next.isTomorrow)
        assertEquals(LocalDate.of(2026, 9, 28), next.date)
        assertEquals("07:26:00", TimeFormatting.countdown(next.secondsRemaining(now)))
    }

    @Test
    fun midnightRolloverSwapsSchedule() {
        val before = PrayerClock.state(Fixtures.date("2026-09-27T23:59:59+08:00"), Fixtures.singapore, CalculationSettings(), 15)
        val after = PrayerClock.state(Fixtures.date("2026-09-28T00:00:01+08:00"), Fixtures.singapore, CalculationSettings(), 15)
        assertEquals(LocalDate.of(2026, 9, 27), before.today.date)
        assertEquals(LocalDate.of(2026, 9, 28), after.today.date)
        assertTrue(before.next!!.isTomorrow)
        assertFalse(after.next!!.isTomorrow)
        assertEquals(before.next!!.time, after.next!!.time)
    }

    @Test
    fun jumuahOnlyOnFridayAndWhenEnabled() {
        val f = PrayerSchedule.forDate(LocalDate.of(2026, 9, 25), Fixtures.singapore, CalculationSettings())
        val s = PrayerSchedule.forDate(LocalDate.of(2026, 9, 26), Fixtures.singapore, CalculationSettings())
        assertEquals("Jumu'ah", f.label(Prayer.DHUHR, true))
        assertEquals("Dhuhr", f.label(Prayer.DHUHR, false))
        assertEquals("Dhuhr", s.label(Prayer.DHUHR, true))
        assertEquals("Asr", f.label(Prayer.ASR, true))
        val next = NextPrayerResolver.resolve(Fixtures.date("2026-09-25T10:00:00+08:00"), Fixtures.singapore, CalculationSettings())!!
        assertEquals("Jumu'ah", next.label(true))
        assertEquals("Dhuhr", next.label(false))
    }

    @Test
    fun nowWindowStartsAndEnds() {
        val settings = CalculationSettings()
        val asr = PrayerSchedule.forDate(LocalDate.of(2026, 9, 27), Fixtures.singapore, settings).time(Prayer.ASR)!!
        fun state(offset: Long, window: Int = 15) = PrayerClock.state(asr.plusSeconds(offset), Fixtures.singapore, settings, window)
        assertNull(state(-1).nowPrayer)
        assertEquals(Prayer.ASR, state(0).nowPrayer)
        assertEquals(Prayer.ASR, state(14 * 60 + 59).nowPrayer)
        assertNull(state(15 * 60).nowPrayer)
        assertNull(state(60, window = 0).nowPrayer)
        assertEquals(Prayer.MAGHRIB, state(60).next?.prayer)
        assertEquals(Prayer.ASR, state(60).current)
    }

    @Test
    fun fajrPeriodEndsAtSunrise() {
        val s = PrayerSchedule.forDate(LocalDate.of(2026, 9, 27), Fixtures.singapore, CalculationSettings())
        val during = PrayerClock.state(s.time(Prayer.FAJR)!!.plusSeconds(600), Fixtures.singapore, CalculationSettings(), 0)
        val afterSunrise = PrayerClock.state(s.time(Prayer.SUNRISE)!!.plusSeconds(60), Fixtures.singapore, CalculationSettings(), 0)
        assertEquals(Prayer.FAJR, during.current)
        assertNull(afterSunrise.current)
    }

    @Test
    fun hijriDateAndAdjustment() {
        val d = LocalDate.of(2026, 9, 27)
        val h = HijriDate.of(d)
        assertEquals(1448, h.year)
        assertEquals(4, h.month)
        assertEquals("Rabi' al-Thani", h.monthName)
        assertEquals(HijriDate.of(d.plusDays(1)), HijriDate.of(d, 1))
        assertEquals(HijriDate.of(d.minusDays(2)), HijriDate.of(d, -2))
        assertEquals(HijriDate.of(d, 2), HijriDate.of(d, 5))
        // The design mock: Thursday 1 October 2026 is 20 Rabi' al-Thani 1448.
        assertEquals("20 Rabi' al-Thani 1448", HijriDate.of(LocalDate.of(2026, 10, 1)).formatted)
    }

    @Test
    fun localDateParsing() {
        assertEquals(LocalDate.of(2026, 9, 27), parseLocalDate("2026-09-27"))
        assertNull(parseLocalDate("2026-02-30"))
        assertNull(parseLocalDate("27/09/2026"))
    }

    @Test
    fun formatting() {
        assertEquals("01:25:43", TimeFormatting.countdown(5143.0))
        assertEquals("00:00:00", TimeFormatting.countdown(-4.0))
        assertEquals("42m", TimeFormatting.short(42 * 60 + 18.0))
        assertEquals("1h 25m", TimeFormatting.short(85 * 60.0))
        assertEquals("<1m", TimeFormatting.short(30.0))
        assertEquals("1 hour 25 minutes", TimeFormatting.spoken(85 * 60.0))
        val t = Fixtures.date("2026-09-27T20:10:00+08:00")
        val z = Fixtures.singapore.zone
        assertEquals("20:10", TimeFormatting.clock(t, z, true))
        assertEquals("8:10 PM", TimeFormatting.clock(t, z, false))
        assertEquals("08:10 PM", TimeFormatting.clock(t, z, false, padHour = true))
        assertEquals("2026-09-27T20:10:00+08:00", TimeFormatting.iso8601(t, z))
        assertEquals("Sunday, 27 September 2026", TimeFormatting.longDate(LocalDate.of(2026, 9, 27)))
        assertEquals("Fri, 25 Sep", TimeFormatting.shortDate(LocalDate.of(2026, 9, 25)))
    }
}
