package salah.core

import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SunnahTimesTest {
    private val date = LocalDate.of(2026, 10, 3)
    private val settings = CalculationSettings(method = MethodID.KARACHI)
    private val day = PrayerSchedule.forDate(date, Fixtures.karachi, settings)
    private val extras = SunnahTimes.forDate(date, Fixtures.karachi, settings).associateBy { it.time }

    @Test
    fun everyTimeIsWorkedOut() {
        assertEquals(ExtraTime.entries.toSet(), extras.keys)
    }

    @Test
    fun windowsSitBetweenThePrayers() {
        fun t(p: Prayer) = day.time(p)!!
        val tahajjud = extras.getValue(ExtraTime.TAHAJJUD)
        assertTrue(tahajjud.start < t(Prayer.FAJR))
        assertEquals(t(Prayer.FAJR), tahajjud.end)
        assertEquals(Duration.ofMinutes(20), Duration.between(t(Prayer.SUNRISE), extras.getValue(ExtraTime.ISHRAQ).start))
        val duha = extras.getValue(ExtraTime.DUHA)
        assertTrue(duha.start > extras.getValue(ExtraTime.ISHRAQ).start)
        assertTrue(duha.end < t(Prayer.DHUHR))
        val zawal = extras.getValue(ExtraTime.ZAWAL)
        assertTrue(zawal.start < t(Prayer.DHUHR))
        assertTrue(zawal.end <= t(Prayer.DHUHR))
        val awwabin = extras.getValue(ExtraTime.AWWABIN)
        assertTrue(awwabin.start > t(Prayer.MAGHRIB) && awwabin.end == t(Prayer.ISHA))
        assertTrue(extras.getValue(ExtraTime.MIDNIGHT).start > t(Prayer.ISHA))
    }

    @Test
    fun timelineKeepsPrayerOrder() {
        val items = TimelineItem.of(day, extras.values.toList())
        assertEquals(12, items.size)
        val starts = items.map { it.start!! }
        assertEquals(starts.sorted(), starts)
        assertEquals(6, TimelineItem.of(day, emptyList()).size)
    }

    @Test
    fun polarDaysHaveNoExtras() {
        val polar = PrayerSchedule.forDate(LocalDate.of(2026, 6, 21), Fixtures.tromso, CalculationSettings())
        val out = SunnahTimes.of(polar, null, null)
        assertTrue(out.none { it.time == ExtraTime.TAHAJJUD || it.time == ExtraTime.MIDNIGHT })
    }
}

class AppTextTest {
    @Test
    fun arabicNamesAndFallback() {
        assertEquals("الجمعة", Prayer.DHUHR.label(isFriday = true, jumuahRelabel = true, lang = AppLanguage.AR))
        assertEquals("Jumuah", Prayer.DHUHR.label(isFriday = true, jumuahRelabel = true))
        assertEquals("Not translated", AppText.t(AppLanguage.AR, "Not translated"))
        assertEquals("العصر بعد 10 دقيقة", NotificationPlanner.title("العصر", 10, AppLanguage.AR))
        assertEquals("Asr in 10 minutes", NotificationPlanner.title("Asr", 10))
    }

    @Test
    fun hijriWithMonthNumber() {
        val h = HijriDate(22, 4, 1448)
        assertEquals("22 Rabi' al-Thani (4) 1448 AH", CalendarText.hijri(h, CalLang.EN, monthNumber = true))
        assertEquals("22 Rabi' al-Thani 1448 AH", CalendarText.hijri(h, CalLang.EN))
        assertEquals("٢٢ ربيع الآخر (٤) ١٤٤٨ هـ", CalendarText.hijri(h, CalLang.AR, monthNumber = true))
    }

    @Test
    fun languageIsSavedAndOptional() {
        assertEquals(null, ConfigStore.decode("{}").display.language)
        val c = SalahConfig(display = DisplaySettings(language = AppLanguage.AR))
        assertEquals(AppLanguage.AR, ConfigStore.decode(ConfigStore.encode(c)).display.language)
        assertTrue(ConfigStore.encode(c).contains("\"language\": \"ar\""))
    }
}
