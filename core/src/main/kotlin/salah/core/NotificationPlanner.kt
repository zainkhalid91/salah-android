package salah.core

import salah.core.kindwords.KindWordsPlanner
import java.time.Instant
import java.time.LocalDate

/** A notification the app should deliver. Pure data; the app owns the timer that shows it. */
data class PlannedNotification(
    /** Deterministic: `salah.<yyyy-MM-dd>.<prayer>.<leadMinutes>` or `salah.<yyyy-MM-dd>.kind.<slot>`. */
    val id: String,
    val fireDate: Instant,
    /** Null for Kind Words notifications. */
    val prayer: Prayer?,
    val prayerTime: Instant?,
    val leadMinutes: Int,
    val date: LocalDate,
    val title: String,
    val body: String,
    /** Delivered without sound (Kind Words with "silent" on). */
    val silent: Boolean = false,
)

object NotificationPlanner {
    const val ID_PREFIX = "salah."
    const val DEFAULT_DAYS = 3
    /** Same cap as the macOS app (which must stay under the OS's 64 pending requests). */
    const val MAX_PENDING = 48

    fun identifier(date: LocalDate, prayer: Prayer, leadMinutes: Int): String = "$ID_PREFIX$date.${prayer.raw}.$leadMinutes"

    /**
     * Plans prayer reminders and Kind Words for [days] local days starting today. Entries in the
     * past, inside quiet hours, or before the pause ends are left out entirely.
     */
    fun plan(now: Instant, config: SalahConfig, days: Int = DEFAULT_DAYS): List<PlannedNotification> {
        val location = config.location ?: return emptyList()
        if (!location.isValid) return emptyList()
        val r = config.reminders
        val zone = location.zone
        val all = prayerReminders(now, config, days) + KindWordsPlanner.plan(now, days, config)
        return all.filter { item ->
            if (item.fireDate <= now) return@filter false
            val until = r.pausedUntil
            if (until != null && item.fireDate < until) return@filter false
            val local = item.fireDate.atZone(zone)
            !r.quietHours.contains(local.hour * 60 + local.minute)
        }
            .sortedWith(compareBy<PlannedNotification> { it.fireDate }.thenBy { it.id })
            .take(MAX_PENDING)
    }

    private fun prayerReminders(now: Instant, config: SalahConfig, days: Int): List<PlannedNotification> {
        val location = config.location ?: return emptyList()
        val r = config.reminders
        if (!r.enabled) return emptyList()
        val start = localDate(now, location.zone)
        val lang = config.display.lang
        val out = mutableListOf<PlannedNotification>()
        for (schedule in PrayerSchedule.range(start, days, location, config.calculation)) {
            for (prayer in Prayer.prayers) {
                val reminder = r.reminder(prayer)
                val time = schedule.time(prayer)
                if (!reminder.enabled || time == null) continue
                val label = schedule.label(prayer, config.display.jumuahRelabel, lang)
                for (lead in reminder.leads) {
                    val fire = time.minusSeconds(lead * 60L)
                    if (fire <= now) continue
                    var body = body(time, location, config.display.use24HourClock, lang)
                    if (lead > 0) {
                        KindWordsPlanner.beforePrayerLine(prayer, schedule, config)?.let { body += "\n$it" }
                    }
                    out += PlannedNotification(
                        id = identifier(schedule.date, prayer, lead),
                        fireDate = fire, prayer = prayer, prayerTime = time, leadMinutes = lead, date = schedule.date,
                        title = title(label, lead, lang),
                        body = body,
                    )
                }
            }
        }
        return out
    }

    /** The at-time reminder of a prayer plays the azan, unless reminders are silent. */
    fun playsAzan(n: PlannedNotification, reminders: ReminderSettings): Boolean =
        n.leadMinutes == 0 && n.prayer?.isPrayer == true && !n.silent && reminders.azan && reminders.sound != ReminderSound.SILENT

    /** "Asr in 10 minutes" or "Time for Asr". */
    fun title(label: String, leadMinutes: Int, lang: AppLanguage = AppLanguage.EN): String =
        if (leadMinutes > 0) AppText.t(lang, "{0} in {1} minutes", label, leadMinutes) else AppText.t(lang, "Time for {0}", label)

    /** "4:05 PM · Singapore" */
    fun body(time: Instant, location: SavedLocation, use24Hour: Boolean, lang: AppLanguage = AppLanguage.EN): String =
        "${TimeFormatting.clock(time, location.zone, use24Hour, lang = lang)} · ${location.name}"
}
