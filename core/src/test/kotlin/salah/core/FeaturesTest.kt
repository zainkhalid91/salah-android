package salah.core

import salah.core.focusmode.BusyReason
import salah.core.focusmode.BusyState
import salah.core.focusmode.CallBusyMode
import salah.core.focusmode.FocusModeBusySettings
import salah.core.focusmode.FocusModeContext
import salah.core.focusmode.FocusModePlanner
import salah.core.focusmode.FocusModeSettings
import salah.core.focusmode.PlannedAction
import salah.core.home.HomeSettings
import salah.core.home.HomeWidget
import salah.core.kindwords.KindWordCategory
import salah.core.kindwords.KindWordsCadence
import salah.core.kindwords.KindWordsLibrary
import salah.core.kindwords.KindWordsPlanner
import salah.core.kindwords.KindWordsSettings
import salah.core.quran.QuranCatalog
import salah.core.quran.QuranPlaylist
import salah.core.quran.QuranQueue
import salah.core.quran.QuranRepeatMode
import salah.core.quran.QuranSettings
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuranTest {
    @Test
    fun catalogIsComplete() {
        assertEquals(114, QuranCatalog.surahs.size)
        assertEquals((1..114).toList(), QuranCatalog.surahs.map { it.number })
        assertEquals(6236, QuranCatalog.surahs.sumOf { it.ayahCount })
        assertEquals("Al-Kahf", QuranCatalog.surah(18)?.name)
        assertEquals(QuranCatalog.reciters[0], QuranCatalog.reciter("nobody"))
    }

    @Test
    fun audioUrlUsesAsciiDigitsInAnyLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val r = QuranCatalog.reciters[0]
            val url = QuranCatalog.audioUrl(r, 18)
            assertNotNull(url)
            assertTrue(url.endsWith("/018.mp3"), url)
            assertTrue(url.startsWith("https://"), url)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun nextRespectsRepeatModes() {
        val q = QuranQueue(listOf(1, 2, 3), startAt = 2)
        assertNull(q.copy().next(QuranRepeatMode.OFF, userInitiated = false))
        assertEquals(1, q.copy().next(QuranRepeatMode.ALL, userInitiated = false))
        assertEquals(3, q.copy().next(QuranRepeatMode.ONE, userInitiated = false))
        assertEquals(1, q.copy().next(QuranRepeatMode.ONE, userInitiated = true))
    }

    @Test
    fun nextSkipsUnavailable() {
        val q = QuranQueue(listOf(1, 2, 3))
        assertEquals(3, q.next(QuranRepeatMode.OFF, userInitiated = true, unavailable = setOf(2)))
        assertEquals(2, q.index)
    }

    @Test
    fun previousRestartsPastThreshold() {
        val q = QuranQueue(listOf(1, 2, 3), startAt = 1)
        assertEquals(2, q.previous(elapsedSeconds = 10.0))
        assertEquals(1, q.previous(elapsedSeconds = 1.0))
        assertEquals(1, q.previous(elapsedSeconds = 1.0), "nothing before the first surah restarts it")
    }

    @Test
    fun shuffleKeepsCurrentAndRestores() {
        val items = (1..20).toList()
        val q = QuranQueue(items, startAt = 4)
        q.setShuffle(true, seed = 42)
        assertEquals(5, q.current)
        assertEquals(items.take(5), q.items.take(5))
        assertEquals(items.toSet(), q.items.toSet())
        val again = QuranQueue(items, startAt = 4).also { it.setShuffle(true, seed = 42) }
        assertEquals(q.items, again.items, "same seed, same order")
        q.setShuffle(false, seed = 0)
        assertEquals(items, q.items)
        assertEquals(5, q.current)
    }

    @Test
    fun editingUpcoming() {
        val q = QuranQueue(listOf(1, 2, 3, 4), startAt = 1)
        q.playNext(9)
        assertEquals(listOf(9, 3, 4), q.upcoming)
        q.remove(1)
        assertEquals(listOf(1, 2, 9, 3, 4), q.items, "the current surah can't be removed")
        q.remove(0)
        assertEquals(0, q.index)
        assertEquals(2, q.current)
        q.moveUpcoming(3, 1)
        assertEquals(listOf(2, 4, 9, 3), q.items)
        q.clearUpcoming()
        assertEquals(listOf(2), q.items)
    }

    @Test
    fun settingsNormalize() {
        val s = QuranSettings(reciterId = "gone", playlists = listOf(QuranPlaylist(name = "x", surahs = listOf(0, 18, 200))))
        val n = ConfigStore.decode(ConfigStore.encode(SalahConfig(quran = s))).quran
        assertEquals(QuranCatalog.reciters[0].id, n.reciterId)
        assertEquals(listOf(18), n.playlists[0].surahs)
    }
}

class KindWordsTest {
    private val sunday = Fixtures.date("2026-09-27T00:00:30+08:00")
    private val config = Fixtures.config(Fixtures.singapore).copy(kindWords = KindWordsSettings(enabled = true))

    @Test
    fun libraryIsBundled() {
        val lib = KindWordsLibrary.BUNDLED
        assertEquals(lib.words.size, lib.words.map { it.id }.toSet().size)
        assertEquals(lib.duas.size, lib.duas.map { it.id }.toSet().size)
        for (id in listOf(KindWordsLibrary.JUMUAH_ID, KindWordsLibrary.KAHF_ID, KindWordsLibrary.LAST_HOUR_ID, KindWordsLibrary.SALAWAT_ID)) {
            assertNotNull(lib.word(id), id)
        }
        assertTrue(lib.words(KindWordCategory.KIND_WORD).isNotEmpty())
        assertNotNull(lib.duaOfTheDay(LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun offPlansNothing() {
        assertTrue(KindWordsPlanner.plan(sunday, 3, Fixtures.config(Fixtures.singapore)).isEmpty())
    }

    @Test
    fun planIsSilentStableAndKeepsGaps() {
        val plan = KindWordsPlanner.plan(sunday, 7, config)
        assertTrue(plan.isNotEmpty())
        assertEquals(plan, KindWordsPlanner.plan(sunday, 7, config))
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
        assertTrue(plan.all { it.silent && it.prayer == null && it.id.contains(".kind.") })
        val words = plan.filter { it.id.contains(".kind.word-") }
        for (w in words) {
            val schedule = PrayerSchedule.forDate(w.date, Fixtures.singapore, config.calculation)
            for (p in Prayer.prayers) {
                val t = schedule.time(p) ?: continue
                assertTrue(abs(t.epochSecond - w.fireDate.epochSecond) >= KindWordsPlanner.PRAYER_GAP_MINUTES * 60L, w.id)
            }
        }
    }

    @Test
    fun fridayEntries() {
        val plan = KindWordsPlanner.plan(sunday, 7, config)
        val friday = LocalDate.of(2026, 10, 2)
        assertTrue(plan.any { it.id == "salah.$friday.kind.friday-kahf" })
        assertTrue(plan.any { it.id == "salah.$friday.kind.friday-lasthour" })
        val firstWord = plan.filter { it.date == friday && it.id.contains("word-") }.minByOrNull { it.fireDate }
        assertNotNull(firstWord)
        assertEquals(KindWordsLibrary.BUNDLED.word(KindWordsLibrary.SALAWAT_ID)!!.notificationBody, firstWord.body)
    }

    @Test
    fun workDuasOnWeekdaysOnly() {
        val c = config.copy(kindWords = config.kindWords.copy(workDuas = true, cadence = KindWordsCadence.OFF))
        val plan = KindWordsPlanner.plan(sunday, 7, c)
        val days = plan.filter { it.id.contains("work-start") }.map { it.date.dayOfWeek.value }
        assertEquals(5, days.size)
        assertTrue(days.all { it in 1..5 })
    }

    @Test
    fun mergedIntoNotificationPlanWithBeforePrayerLine() {
        val plan = NotificationPlanner.plan(sunday, config, days = 1)
        assertTrue(plan.any { it.prayer == null })
        val early = plan.first { it.prayer == Prayer.ASR && it.leadMinutes > 0 }
        assertTrue(early.body.contains("\n"), "early reminder carries a verse or hadith")
        val atTime = plan.first { it.prayer == Prayer.ASR && it.leadMinutes == 0 }
        assertFalse(atTime.body.contains("\n"))
        assertTrue(plan.size <= NotificationPlanner.MAX_PENDING)
    }

    @Test
    fun pickRotatesDeterministically() {
        val pool = listOf("a", "b", "c")
        val d = LocalDate.of(2026, 9, 27)
        assertEquals(KindWordsPlanner.pick(pool, d, 0), KindWordsPlanner.pick(pool, d, 0))
        assertEquals(pool[((d.toEpochDay() * 3 + 1) % 3).toInt()], KindWordsPlanner.pick(pool, d, 1))
    }
}

class FocusModeTest {
    private val time = Fixtures.date("2026-09-27T12:55:00+08:00")
    private val on = FocusModeSettings(enabled = true)

    private fun ctx(
        settings: FocusModeSettings = on,
        prayer: Prayer = Prayer.DHUHR,
        lateMinutes: Long = 0,
        busy: BusyState = BusyState.FREE,
        handled: Boolean = false,
        owns: Boolean = false,
    ) = FocusModeContext(prayer, time, time.plusSeconds(lateMinutes * 60), settings, busy, false, handled, owns)

    @Test
    fun onTimeRunsEverythingEnabled() {
        assertEquals(listOf(PlannedAction.PauseMedia, PlannedAction.ShowCard(null)), FocusModePlanner.decide(ctx()))
        val withDnd = on.copy(focus = on.focus.copy(enabled = true))
        assertEquals(
            listOf(PlannedAction.PauseMedia, PlannedAction.FocusOn, PlannedAction.ShowCard(null)),
            FocusModePlanner.decide(ctx(withDnd)),
        )
    }

    @Test
    fun skipsWhenOffHandledOrNotSelected() {
        assertTrue(FocusModePlanner.decide(ctx(FocusModeSettings())).isEmpty())
        assertTrue(FocusModePlanner.decide(ctx(handled = true)).isEmpty())
        assertTrue(FocusModePlanner.decide(ctx(prayer = Prayer.FAJR)).isEmpty(), "Fajr is off by default")
    }

    @Test
    fun lateShowsOnlyTheCardInsideAutoClose() {
        assertEquals(listOf(PlannedAction.ShowCard(null)), FocusModePlanner.decide(ctx(lateMinutes = 12)))
        assertTrue(FocusModePlanner.decide(ctx(lateMinutes = 20)).isEmpty())
    }

    @Test
    fun busyNudges() {
        assertEquals(
            listOf(PlannedAction.ShowNudge(BusyReason.CALL, true), PlannedAction.DeferUntilFree),
            FocusModePlanner.decide(ctx(busy = BusyState.ON_CALL)),
        )
        val nudgeOnly = on.copy(whenBusy = FocusModeBusySettings(onCall = CallBusyMode.NUDGE_ONLY))
        assertEquals(listOf(PlannedAction.ShowNudge(BusyReason.FOCUS, false)), FocusModePlanner.decide(ctx(nudgeOnly, busy = BusyState.FOCUS_ON)))
        assertEquals(2, FocusModePlanner.decide(ctx(busy = BusyState.FOCUS_ON, owns = true)).size, "Salah's own DND isn't busy")
    }

    @Test
    fun settingsDropSunriseAndUnknownPrayers() {
        val json = """{"focusMode":{"enabled":true,"prayers":["isha","sunrise","fajr","nope"],"card":{"autoCloseMinutes":99}}}"""
        val f = ConfigStore.decode(json).focusMode
        assertEquals(setOf(Prayer.FAJR, Prayer.ISHA), f.prayers)
        assertEquals(listOf("fajr", "isha"), f.prayerNames)
        assertEquals(15, f.card.autoCloseMinutes)
    }
}

class SunnahAndHomeTest {
    private fun hijri(y: Int, m: Int, d: Int) = IslamicCalendar.toGregorian(y, m, d)!!

    @Test
    fun specialDays() {
        assertTrue(SunnahFast.ARAFAH in SunnahFast.reasons(hijri(1447, 12, 9)))
        assertTrue(SunnahFast.ASHURA in SunnahFast.reasons(hijri(1448, 1, 10)))
        assertTrue(SunnahFast.WHITE_DAYS in SunnahFast.reasons(hijri(1448, 3, 14)))
    }

    @Test
    fun forbiddenAndRamadanDays() {
        assertTrue(SunnahFast.reasons(hijri(1447, 9, 14)).isEmpty(), "Ramadan")
        assertTrue(SunnahFast.reasons(hijri(1447, 10, 1)).isEmpty(), "Eid al-Fitr")
        assertTrue(SunnahFast.reasons(hijri(1447, 12, 13)).isEmpty(), "Tashriq")
    }

    @Test
    fun mondaysAndTags() {
        // 2026-09-28 is a Monday, outside the White Days.
        val monday = LocalDate.of(2026, 9, 28)
        val r = SunnahFast.reasons(monday)
        assertTrue(SunnahFast.MONDAY_THURSDAY in r)
        assertEquals("Sunnah fast", SunnahFast.tag(listOf(SunnahFast.MONDAY_THURSDAY)))
        assertEquals("Sunnah fast · White day", SunnahFast.tag(listOf(SunnahFast.WHITE_DAYS, SunnahFast.MONDAY_THURSDAY)))
        assertNull(SunnahFast.tag(emptyList()))
    }

    @Test
    fun homeWidgets() {
        val h = ConfigStore.decode("""{"home":{"widgets":["quran","future","quran","nextPrayer"]}}""").home
        assertEquals(listOf(HomeWidget.QURAN, HomeWidget.NEXT_PRAYER), h.widgets)
        assertEquals(listOf(HomeWidget.NEXT_PRAYER, HomeWidget.QURAN), h.move(HomeWidget.QURAN, 1).widgets)
        assertEquals(h, h.move(HomeWidget.QURAN, -1))
        assertTrue(h.set(HomeWidget.KIND_WORD, true).isOn(HomeWidget.KIND_WORD))
        assertFalse(h.set(HomeWidget.QURAN, false).isOn(HomeWidget.QURAN))
        assertEquals(HomeSettings.DEFAULT_WIDGETS, SalahConfig().home.widgets)
    }

    @Test
    fun newSectionsRoundTrip() {
        val c = Fixtures.config(Fixtures.singapore).copy(
            kindWords = KindWordsSettings(enabled = true, cadence = KindWordsCadence.PEAK_MOMENTS, afterPrayerMinutes = 45),
            focusMode = FocusModeSettings(enabled = true).withPrayer(Prayer.FAJR, true),
            home = HomeSettings.of(listOf(HomeWidget.KIND_WORD)),
            quran = QuranSettings(repeatMode = QuranRepeatMode.ONE, shuffle = true),
        )
        val text = ConfigStore.encode(c)
        assertTrue(text.contains("\"repeat\": \"one\""), text)
        assertEquals(c, ConfigStore.decode(text))
    }
}
