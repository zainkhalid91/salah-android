package salah.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NotificationPlannerTest {
    /** Just after local midnight, so all three days are fully in the future. */
    private val startOfDay = Fixtures.date("2026-09-27T00:00:30+08:00")

    private fun SalahConfig.reminders(body: (ReminderSettings) -> ReminderSettings) = copy(reminders = body(reminders))

    @Test
    fun threeDayWindowCountAndCap() {
        val plan = NotificationPlanner.plan(startOfDay, Fixtures.config(Fixtures.singapore))
        assertEquals(30, plan.size)
        assertEquals(3, plan.map { it.date }.toSet().size)
        assertEquals(plan.map { it.fireDate }.sorted(), plan.map { it.fireDate })
    }

    @Test
    fun pastEntriesAreSkipped() {
        val evening = Fixtures.date("2026-09-27T19:30:00+08:00")
        val plan = NotificationPlanner.plan(evening, Fixtures.config(Fixtures.singapore))
        assertTrue(plan.all { it.fireDate > evening })
        assertEquals(2, plan.count { it.date == LocalDate.of(2026, 9, 27) })
    }

    @Test
    fun deterministicIdentifiersAndIdempotentReplan() {
        val config = Fixtures.config(Fixtures.singapore)
        val a = NotificationPlanner.plan(startOfDay, config)
        assertEquals(a, NotificationPlanner.plan(startOfDay, config))
        assertEquals(a.size, a.map { it.id }.toSet().size)
        assertTrue(a.any { it.id == "salah.2026-09-27.asr.10" })
        assertTrue(a.any { it.id == "salah.2026-09-27.asr.0" })
    }

    @Test
    fun quietHoursExcludeEntries() {
        val config = Fixtures.config(Fixtures.singapore).reminders { it.copy(quietHours = QuietHours(true, "04:00", "06:00")) }
        val plan = NotificationPlanner.plan(startOfDay, config)
        assertFalse(plan.any { it.prayer == Prayer.FAJR })
        assertEquals(24, plan.size)
    }

    @Test
    fun quietHoursWrapPastMidnight() {
        val q = QuietHours(true, "23:00", "04:30")
        assertTrue(q.contains(23 * 60 + 30))
        assertTrue(q.contains(60))
        assertFalse(q.contains(4 * 60 + 30))
        assertFalse(QuietHours(false, "23:00", "04:30").contains(60))
    }

    @Test
    fun pauseExcludesUntilItEnds() {
        val until = Fixtures.date("2026-09-28T00:00:00+08:00")
        val plan = NotificationPlanner.plan(startOfDay, Fixtures.config(Fixtures.singapore).reminders { it.withPausedUntil(until) })
        assertTrue(plan.all { it.fireDate >= until })
        assertEquals(20, plan.size)
    }

    @Test
    fun disabledPrayerAndGlobalSwitch() {
        var config = Fixtures.config(Fixtures.singapore).reminders { r -> r.update(Prayer.ASR) { it.copy(enabled = false) } }
        assertFalse(NotificationPlanner.plan(startOfDay, config).any { it.prayer == Prayer.ASR })
        config = config.reminders { it.copy(enabled = false) }
        assertTrue(NotificationPlanner.plan(startOfDay, config).isEmpty())
        assertTrue(NotificationPlanner.plan(startOfDay, Fixtures.config(null)).isEmpty())
    }

    @Test
    fun leadOnlyAndAtTimeOnly() {
        var config = Fixtures.config(Fixtures.singapore)
        for (p in Prayer.prayers) config = config.reminders { r -> r.update(p) { it.copy(atTime = false) } }
        assertTrue(NotificationPlanner.plan(startOfDay, config).all { it.leadMinutes > 0 })
        for (p in Prayer.prayers) config = config.reminders { r -> r.update(p) { it.copy(atTime = true, leadMinutes = 0) } }
        val plan = NotificationPlanner.plan(startOfDay, config)
        assertEquals(15, plan.size)
        assertTrue(plan.all { it.fireDate == it.prayerTime })
    }

    @Test
    fun changingInputsChangesFireDates() {
        val base = NotificationPlanner.plan(startOfDay, Fixtures.config(Fixtures.singapore, MethodID.SINGAPORE))
        val offset = Fixtures.config(Fixtures.singapore, MethodID.SINGAPORE).let { it.copy(calculation = it.calculation.withOffset(2, Prayer.ISHA)) }
        val method = Fixtures.config(Fixtures.singapore, MethodID.MUSLIM_WORLD_LEAGUE).let {
            it.copy(calculation = it.calculation.copy(madhab = MadhabSetting.HANAFI))
        }
        val moved = Fixtures.config(Fixtures.jakarta, MethodID.SINGAPORE)
        fun fire(plan: List<PlannedNotification>, id: String) = plan.firstOrNull { it.id == id }?.fireDate
        val isha = "salah.2026-09-28.isha.0"
        val fajr = "salah.2026-09-28.fajr.0"
        assertNotEquals(fire(base, isha), fire(NotificationPlanner.plan(startOfDay, offset), isha))
        assertEquals(fire(base, fajr), fire(NotificationPlanner.plan(startOfDay, offset), fajr))
        assertNotEquals(fire(base, fajr), fire(NotificationPlanner.plan(startOfDay, method), fajr))
        assertNotEquals(fire(base, fajr), fire(NotificationPlanner.plan(startOfDay, moved), fajr))
    }

    @Test
    fun copyAndJumuah() {
        val friday = Fixtures.date("2026-09-25T00:00:30+08:00")
        val plan = NotificationPlanner.plan(friday, Fixtures.config(Fixtures.singapore))
        val lead = plan.first { it.id == "salah.2026-09-25.dhuhr.10" }
        assertEquals("Jumu'ah in 10 minutes", lead.title)
        assertEquals("12:58 · Singapore", lead.body)
        assertEquals("Time for Asr", plan.first { it.id == "salah.2026-09-25.asr.0" }.title)
        val twelve = Fixtures.config(Fixtures.singapore).let { it.copy(display = it.display.copy(use24HourClock = false)) }
        val asrLead = NotificationPlanner.plan(friday, twelve).first { it.id == "salah.2026-09-25.asr.10" }
        assertEquals("Asr in 10 minutes", asrLead.title)
        assertEquals("4:01 PM · Singapore", asrLead.body)
    }
}
