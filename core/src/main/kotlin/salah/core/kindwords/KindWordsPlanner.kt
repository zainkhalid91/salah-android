package salah.core.kindwords

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import salah.core.AppLanguage
import salah.core.AppText
import salah.core.DaySchedule
import salah.core.NotificationPlanner
import salah.core.PlannedNotification
import salah.core.Prayer
import salah.core.PrayerSchedule
import salah.core.QuietHours
import salah.core.SalahConfig
import salah.core.isFriday
import salah.core.localDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Serializable
enum class KindWordsCadence(val displayName: String, val slots: List<Int>) {
    /** 09:00, 13:00, 17:00, 21:00. */
    @SerialName("everyFourHours") EVERY_FOUR_HOURS("Every 4 hours", listOf(9 * 60, 13 * 60, 17 * 60, 21 * 60)),
    /** 10:30, 15:30, 20:30. */
    @SerialName("peakMoments") PEAK_MOMENTS("Peak moments only", listOf(10 * 60 + 30, 15 * 60 + 30, 20 * 60 + 30)),
    /** No daytime kind words; prayer and Friday reminders still follow their own switches. */
    @SerialName("off") OFF("Off", emptyList()),
}

/** Kind Words settings (roadmap/kind-words.md). Off by default: it adds notifications, so the user turns it on. */
@Serializable
data class KindWordsSettings(
    val enabled: Boolean = false,
    val cadence: KindWordsCadence = KindWordsCadence.EVERY_FOUR_HOURS,
    /** Adds a verse or hadith to the early prayer reminder. */
    val beforePrayer: Boolean = true,
    /** "Have you prayed?" after each prayer. */
    val afterPrayer: Boolean = true,
    val afterPrayerMinutes: Int = 30,
    /** Al-Kahf in the morning and the last hour after Asr. */
    val friday: Boolean = true,
    /** A du'a at the start and end of the work day, Monday to Friday. */
    val workDuas: Boolean = false,
    val workStart: String = "09:00",
    val workEnd: String = "17:00",
    /** Kind words arrive without sound, whatever the prayer reminder sound is. */
    val silent: Boolean = true,
) {
    internal fun normalized() = copy(
        afterPrayerMinutes = if (afterPrayerMinutes in ALLOWED_AFTER_PRAYER_MINUTES) afterPrayerMinutes else 30,
        workStart = if (QuietHours.minutes(workStart) == null) "09:00" else workStart,
        workEnd = if (QuietHours.minutes(workEnd) == null) "17:00" else workEnd,
    )

    companion object {
        val ALLOWED_AFTER_PRAYER_MINUTES = listOf(20, 30, 45, 60)
    }
}

/**
 * Plans Kind Words notifications, ported from the macOS app's KindWordsPlanner. Pure, like
 * [NotificationPlanner], which merges this output with the prayer reminders and applies the
 * pause and quiet hours to both.
 */
object KindWordsPlanner {
    /** A daytime kind word is skipped when a prayer time is this close. */
    const val PRAYER_GAP_MINUTES = 45
    /** …or when another Kind Words notification is this close. */
    const val KIND_GAP_MINUTES = 30

    fun plan(now: Instant, days: Int, config: SalahConfig, library: KindWordsLibrary = KindWordsLibrary.BUNDLED): List<PlannedNotification> {
        val k = config.kindWords
        val location = config.location ?: return emptyList()
        if (!k.enabled || !location.isValid) return emptyList()
        val zone = location.zone
        val lang = config.display.lang
        val start = localDate(now, zone)
        val out = mutableListOf<PlannedNotification>()

        for (schedule in PrayerSchedule.range(start, days, location, config.calculation)) {
            val date = schedule.date
            val fixed = mutableListOf<PlannedNotification>()
            fun add(slot: String, fire: Instant, title: String, body: String) {
                fixed += PlannedNotification(
                    id = identifier(date, slot), fireDate = fire, prayer = null, prayerTime = null,
                    leadMinutes = 0, date = date, title = title, body = body, silent = k.silent,
                )
            }

            if (k.afterPrayer) {
                val pool = library.words(KindWordCategory.AFTER_PRAYER)
                for ((i, prayer) in Prayer.prayers.withIndex()) {
                    val time = schedule.time(prayer) ?: continue
                    if (pool.isEmpty()) continue
                    val fire = time.plusSeconds(k.afterPrayerMinutes * 60L)
                    // Never ask about a prayer once the next one has begun.
                    val next = nextTime(prayer, schedule)
                    if (next != null && fire >= next) continue
                    val label = schedule.label(prayer, config.display.jumuahRelabel, lang)
                    add("after-${prayer.raw}", fire, AppText.t(lang, "Have you prayed {0}?", label), pick(pool, date, i).notificationBody)
                }
            }

            if (k.friday && date.isFriday) {
                library.word(KindWordsLibrary.KAHF_ID)?.let {
                    add("friday-kahf", at(8 * 60, date, zone), AppText.t(lang, "It’s Friday — Al-Kahf today"), it.notificationBody)
                }
                val maghrib = schedule.time(Prayer.MAGHRIB)
                val lastHour = library.word(KindWordsLibrary.LAST_HOUR_ID)
                if (maghrib != null && lastHour != null) {
                    add("friday-lasthour", maghrib.minusSeconds(3600), AppText.t(lang, "The last hour of Friday"), lastHour.notificationBody)
                }
            }

            if (k.workDuas && date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                val starts = library.duas(DuaMoment.BEFORE_WORK)
                val ends = library.duas(DuaMoment.AFTER_WORK)
                val s = QuietHours.minutes(k.workStart)
                if (s != null && starts.isNotEmpty()) {
                    add("work-start", at(s, date, zone), AppText.t(lang, "Before you begin"), pick(starts, date, 0).notificationBody)
                }
                val e = QuietHours.minutes(k.workEnd)
                if (e != null && ends.isNotEmpty()) {
                    add("work-end", at(e, date, zone), AppText.t(lang, "Close the day gently"), pick(ends, date, 1).notificationBody)
                }
            }

            out += fixed

            val prayerTimes = Prayer.prayers.mapNotNull { schedule.time(it) }
            val words = library.words(KindWordCategory.KIND_WORD)
            val placed = fixed.map { it.fireDate }.toMutableList()
            var sentSalawat = false
            if (words.isNotEmpty()) {
                for ((i, minute) in k.cadence.slots.withIndex()) {
                    val fire = at(minute, date, zone)
                    if (prayerTimes.any { abs(it.epochSecond - fire.epochSecond) < PRAYER_GAP_MINUTES * 60L }) continue
                    if (placed.any { abs(it.epochSecond - fire.epochSecond) < KIND_GAP_MINUTES * 60L }) continue
                    placed += fire
                    var word = pick(words, date, i)
                    // The first kind word on a Friday is the salawat reminder.
                    val salawat = library.word(KindWordsLibrary.SALAWAT_ID)
                    if (k.friday && date.isFriday && !sentSalawat && salawat != null) {
                        word = salawat
                        sentSalawat = true
                    }
                    out += PlannedNotification(
                        id = identifier(date, "word-%02d%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)),
                        fireDate = fire, prayer = null, prayerTime = null, leadMinutes = 0, date = date,
                        title = title(minute, lang), body = word.notificationBody, silent = k.silent,
                    )
                }
            }
        }
        return out.filter { it.fireDate > now }
    }

    /**
     * The line added to an early prayer reminder, or null when Kind Words or this option is off.
     * Jumu'ah gets Al-Jumu'ah 62:9.
     */
    fun beforePrayerLine(
        prayer: Prayer,
        schedule: DaySchedule,
        config: SalahConfig,
        library: KindWordsLibrary = KindWordsLibrary.BUNDLED,
        force: Boolean = false,
    ): String? {
        if (!force && (!config.kindWords.enabled || !config.kindWords.beforePrayer)) return null
        if (prayer == Prayer.DHUHR && schedule.isFriday) {
            library.word(KindWordsLibrary.JUMUAH_ID)?.let { return it.notificationBody }
        }
        val pool = library.words(KindWordCategory.BEFORE_PRAYER)
        if (pool.isEmpty()) return null
        val salt = Prayer.prayers.indexOf(prayer).coerceAtLeast(0)
        return pick(pool, schedule.date, salt).notificationBody
    }

    fun identifier(date: LocalDate, slot: String): String = "${NotificationPlanner.ID_PREFIX}$date.kind.$slot"

    /** "Good morning", "Good afternoon"… by the slot's hour. */
    fun title(minute: Int, lang: AppLanguage = AppLanguage.EN): String = AppText.t(
        lang,
        when (minute / 60) {
            in 0 until 12 -> "Good morning"
            in 12 until 15 -> "A moment of calm"
            in 15 until 19 -> "Good afternoon"
            else -> "Rest well"
        },
    )

    /**
     * Stable across launches and replans, so a replan never swaps the text of a pending
     * notification. Rotates through the pool day by day, the same picks as the macOS app.
     */
    fun <T> pick(pool: List<T>, date: LocalDate, salt: Int): T {
        val n = date.toEpochDay() * 3 + salt
        return pool[(((n % pool.size) + pool.size) % pool.size).toInt()]
    }

    private fun at(minute: Int, date: LocalDate, zone: ZoneId): Instant =
        date.atTime(minute / 60, minute % 60).atZone(zone).toInstant()

    private fun nextTime(prayer: Prayer, schedule: DaySchedule): Instant? {
        val i = Prayer.prayers.indexOf(prayer)
        if (i < 0 || i + 1 >= Prayer.prayers.size) return null
        return schedule.time(Prayer.prayers[i + 1])
    }
}
