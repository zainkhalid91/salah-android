package salah.core

import java.time.Instant
import java.time.LocalDate

/** A reminder the app should deliver. Pure data; the app owns the timer that shows it. */
data class PlannedNotification(
    /** Deterministic: `salah.<yyyy-MM-dd>.<prayer>.<leadMinutes>`. */
    val id: String,
    val fireDate: Instant,
    val prayer: Prayer,
    val prayerTime: Instant,
    val leadMinutes: Int,
    val date: LocalDate,
    val title: String,
    val body: String,
)

object NotificationPlanner {
    const val ID_PREFIX = "salah."
    const val DEFAULT_DAYS = 3
    /** Same cap as the macOS app (which must stay under the OS's 64 pending requests). */
    const val MAX_PENDING = 48

    fun identifier(date: LocalDate, prayer: Prayer, leadMinutes: Int): String = "$ID_PREFIX$date.${prayer.raw}.$leadMinutes"

    /**
     * Plans reminders for [days] local days starting today. Entries in the past, inside quiet hours,
     * or before the pause ends are left out entirely (never scheduled then suppressed).
     */
    fun plan(now: Instant, config: SalahConfig, days: Int = DEFAULT_DAYS): List<PlannedNotification> {
        val location = config.location ?: return emptyList()
        if (!config.reminders.enabled || !location.isValid) return emptyList()
        val r = config.reminders
        val zone = location.zone
        val start = localDate(now, zone)
        val out = mutableListOf<PlannedNotification>()

        for (schedule in PrayerSchedule.range(start, days, location, config.calculation)) {
            for (prayer in Prayer.prayers) {
                val reminder = r.reminder(prayer)
                val time = schedule.time(prayer)
                if (!reminder.enabled || time == null) continue
                val label = schedule.label(prayer, config.display.jumuahRelabel)
                for (lead in reminder.leads) {
                    val fire = time.minusSeconds(lead * 60L)
                    if (fire <= now) continue
                    val until = r.pausedUntil
                    if (until != null && fire < until) continue
                    val local = fire.atZone(zone)
                    if (r.quietHours.contains(local.hour * 60 + local.minute)) continue
                    out += PlannedNotification(
                        id = identifier(schedule.date, prayer, lead),
                        fireDate = fire, prayer = prayer, prayerTime = time, leadMinutes = lead, date = schedule.date,
                        title = title(label, lead),
                        body = body(time, location, config.display.use24HourClock),
                    )
                }
            }
        }
        return out.sortedWith(compareBy<PlannedNotification> { it.fireDate }.thenBy { it.id }).take(MAX_PENDING)
    }

    /** "Asr in 10 minutes" or "Time for Asr". */
    fun title(label: String, leadMinutes: Int): String = if (leadMinutes > 0) "$label in $leadMinutes minutes" else "Time for $label"

    /** "4:05 PM · Singapore" */
    fun body(time: Instant, location: SavedLocation, use24Hour: Boolean): String =
        "${TimeFormatting.clock(time, location.zone, use24Hour)} · ${location.name}"
}
