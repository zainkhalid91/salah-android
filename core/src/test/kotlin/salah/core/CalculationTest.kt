package salah.core

import com.batoulapps.adhan.CalculationMethod as RefMethod
import com.batoulapps.adhan.Coordinates as RefCoordinates
import com.batoulapps.adhan.HighLatitudeRule as RefHighLatitudeRule
import com.batoulapps.adhan.PrayerTimes as RefPrayerTimes
import com.batoulapps.adhan.data.DateComponents as RefDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculationTest {
    private fun clock(s: DaySchedule, p: Prayer) = TimeFormatting.clock(s.time(p)!!, s.zone, use24Hour = true)

    /** Reference output of adhan-swift 1.4.0, MUIS, 28 Sep 2026 (from the macOS test suite). */
    @Test
    fun singaporeKnownValues() {
        val s = PrayerSchedule.forDate(LocalDate.of(2026, 9, 28), Fixtures.singapore, CalculationSettings(method = MethodID.SINGAPORE))
        val expected = mapOf(
            Prayer.FAJR to "05:36", Prayer.SUNRISE to "06:53", Prayer.DHUHR to "12:57",
            Prayer.ASR to "16:02", Prayer.MAGHRIB to "18:59", Prayer.ISHA to "20:08",
        )
        for ((p, t) in expected) assertEquals(t, clock(s, p), p.displayName)
    }

    /**
     * Cross-check against Batoul Apps' Java library across cities, seasons and every method both
     * libraries share. Allows ±1 minute, as the macOS suite does against adhan-swift.
     */
    @Test
    fun matchesReferenceLibraryAcrossCitiesMethodsAndDates() {
        val methods = listOf(
            MethodID.MUSLIM_WORLD_LEAGUE to RefMethod.MUSLIM_WORLD_LEAGUE,
            MethodID.EGYPTIAN to RefMethod.EGYPTIAN,
            MethodID.KARACHI to RefMethod.KARACHI,
            MethodID.UMM_AL_QURA to RefMethod.UMM_AL_QURA,
            MethodID.DUBAI to RefMethod.DUBAI,
            MethodID.MOONSIGHTING_COMMITTEE to RefMethod.MOON_SIGHTING_COMMITTEE,
            MethodID.NORTH_AMERICA to RefMethod.NORTH_AMERICA,
            MethodID.KUWAIT to RefMethod.KUWAIT,
            MethodID.QATAR to RefMethod.QATAR,
            MethodID.SINGAPORE to RefMethod.SINGAPORE,
        )
        val cities = listOf(Fixtures.singapore, Fixtures.mecca, Fixtures.newYork, Fixtures.london, Fixtures.jakarta, Fixtures.karachi, Fixtures.cairo)
        val dates = listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 21), LocalDate.of(2026, 9, 27), LocalDate.of(2027, 12, 21))
        var compared = 0
        for ((ours, theirs) in methods) for (loc in cities) for (d in dates) {
            val s = PrayerSchedule.forDate(d, loc, CalculationSettings(method = ours))
            // adhan-java always defaults to middle-of-the-night; adhan-swift 1.4 (the macOS app) uses
            // the latitude recommendation, so pin the reference to the same rule.
            val params = theirs.parameters.apply {
                highLatitudeRule = if (loc.latitude > 48) RefHighLatitudeRule.SEVENTH_OF_THE_NIGHT else RefHighLatitudeRule.MIDDLE_OF_THE_NIGHT
            }
            val ref = RefPrayerTimes(RefCoordinates(loc.latitude, loc.longitude), RefDate(d.year, d.monthValue, d.dayOfMonth), params)
            val pairs = listOf(
                Prayer.FAJR to ref.fajr, Prayer.SUNRISE to ref.sunrise, Prayer.DHUHR to ref.dhuhr,
                Prayer.ASR to ref.asr, Prayer.MAGHRIB to ref.maghrib, Prayer.ISHA to ref.isha,
            )
            for ((p, expected) in pairs) {
                val got = s.time(p) ?: error("${loc.name} $d $ours ${p.displayName} missing")
                val diff = abs(got.toEpochMilli() - expected.time) / 1000
                assertTrue(diff <= 60, "${loc.name} $d $ours ${p.displayName}: off by ${diff}s")
                compared++
            }
        }
        assertEquals(methods.size * cities.size * dates.size * 6, compared)
    }

    @Test
    fun tehranUsesMaghribAngle() {
        val d = LocalDate.of(2026, 9, 27)
        val tehran = SavedLocation("Tehran", 35.6892, 51.3890, "Asia/Tehran", "IR")
        val s = PrayerSchedule.forDate(d, tehran, CalculationSettings(method = MethodID.TEHRAN))
        val mwl = PrayerSchedule.forDate(d, tehran, CalculationSettings(method = MethodID.MUSLIM_WORLD_LEAGUE))
        // Sunset is the same instant; Tehran's Maghrib waits for the sun to reach 4.5° below the horizon.
        val delay = (s.time(Prayer.MAGHRIB)!!.epochSecond - mwl.time(Prayer.MAGHRIB)!!.epochSecond) / 60
        assertTrue(delay in 14..22, "Tehran Maghrib delay $delay min")
    }

    @Test
    fun jakartaCustomAngles() {
        val settings = CalculationSettings(method = MethodID.CUSTOM, customFajrAngle = 20.0, customIshaAngle = 18.0)
        val ours = PrayerSchedule.forDate(LocalDate.of(2026, 9, 27), Fixtures.jakarta, settings)
        val singapore = PrayerSchedule.forDate(LocalDate.of(2026, 9, 27), Fixtures.jakarta, CalculationSettings(method = MethodID.SINGAPORE))
        // Same angles as MUIS; MUIS only adds a minute to Dhuhr and rounds up.
        assertTrue(abs(ours.time(Prayer.FAJR)!!.epochSecond - singapore.time(Prayer.FAJR)!!.epochSecond) <= 60)
        assertTrue(abs(ours.time(Prayer.ISHA)!!.epochSecond - singapore.time(Prayer.ISHA)!!.epochSecond) <= 60)
        assertEquals("Custom (20° / 18°)", ours.methodName)
    }

    @Test
    fun offsetsApplyPerPrayer() {
        val d = LocalDate.of(2026, 9, 27)
        val base = PrayerSchedule.forDate(d, Fixtures.singapore, CalculationSettings(method = MethodID.SINGAPORE))
        val s = CalculationSettings(method = MethodID.SINGAPORE).withOffset(3, Prayer.ISHA).withOffset(-2, Prayer.FAJR)
        val adjusted = PrayerSchedule.forDate(d, Fixtures.singapore, s)
        assertEquals(180, adjusted.time(Prayer.ISHA)!!.epochSecond - base.time(Prayer.ISHA)!!.epochSecond)
        assertEquals(-120, adjusted.time(Prayer.FAJR)!!.epochSecond - base.time(Prayer.FAJR)!!.epochSecond)
        assertEquals(base.time(Prayer.ASR), adjusted.time(Prayer.ASR))
    }

    @Test
    fun madhabMovesAsrLater() {
        val d = LocalDate.of(2026, 9, 27)
        val shafi = PrayerSchedule.forDate(d, Fixtures.london, CalculationSettings(madhab = MadhabSetting.SHAFI))
        val hanafi = PrayerSchedule.forDate(d, Fixtures.london, CalculationSettings(madhab = MadhabSetting.HANAFI))
        assertTrue(hanafi.time(Prayer.ASR)!! > shafi.time(Prayer.ASR)!!)
    }

    @Test
    fun polarDayIsUndefinedNotACrash() {
        val s = PrayerSchedule.forDate(LocalDate.of(2026, 6, 21), Fixtures.tromso, CalculationSettings())
        assertTrue(s.times.isEmpty())
        assertEquals(Prayer.entries.toList(), s.undefined)
        assertNotNull(s.undefinedExplanation())
        assertNull(NextPrayerResolver.resolve(Fixtures.date("2026-06-21T12:00:00+02:00"), Fixtures.tromso, CalculationSettings()))
    }

    @Test
    fun highLatitudeRuleChangesOsloSummerIsha() {
        val d = LocalDate.of(2026, 6, 21)
        val a = PrayerSchedule.forDate(d, Fixtures.oslo, CalculationSettings(highLatitudeRule = HighLatitudeSetting.MIDDLE_OF_THE_NIGHT))
        val b = PrayerSchedule.forDate(d, Fixtures.oslo, CalculationSettings(highLatitudeRule = HighLatitudeSetting.SEVENTH_OF_THE_NIGHT))
        assertNotEquals(a.time(Prayer.ISHA), b.time(Prayer.ISHA))
    }

    @Test
    fun automaticMethodPicksMuisInSingapore() {
        assertEquals(MethodID.SINGAPORE, MethodID.automatic(Fixtures.singapore))
        assertEquals(MethodID.MUSLIM_WORLD_LEAGUE, MethodID.automatic(Fixtures.london))
        assertEquals(MethodID.MUSLIM_WORLD_LEAGUE, MethodID.automatic(null))
    }
}
