package salah.core

import salah.core.adhan.Coordinates
import salah.core.adhan.PrayerTimes
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

// region Local dates

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June", "July",
    "August", "September", "October", "November", "December",
)

val LocalDate.weekdayName: String get() = WEEKDAYS[dayOfWeek.value - 1]
val LocalDate.monthName: String get() = MONTHS[monthValue - 1]
val LocalDate.isFriday: Boolean get() = dayOfWeek == DayOfWeek.FRIDAY

/** The local calendar day containing [instant] in [zone]. */
fun localDate(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

fun LocalDate.startOfDay(zone: ZoneId): Instant = atStartOfDay(zone).toInstant()

/** Parses "YYYY-MM-DD", rejecting impossible dates like 2026-02-30. */
fun parseLocalDate(s: String): LocalDate? =
    if (Regex("""\d{1,4}-\d{1,2}-\d{1,2}""").matches(s)) {
        val (y, m, d) = s.split("-").map { it.toInt() }
        runCatching { LocalDate.of(y, m, d) }.getOrNull()
    } else null

// endregion

/**
 * Hijri date via the Umm al-Qura calendar (java.time's Hijrah-umalqura), with a manual ±2 day
 * adjustment for local moon sighting. The Hijri day does not roll over at Maghrib.
 */
data class HijriDate(val day: Int, val month: Int, val year: Int) {
    val monthName: String get() = MONTH_NAMES[month - 1]

    /** e.g. "12 Rabi' al-Awwal 1448" */
    val formatted: String get() = "$day $monthName $year"

    companion object {
        val MONTH_NAMES = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah",
        )

        fun of(date: LocalDate, adjustment: Int = 0): HijriDate {
            val h = HijrahDate.from(date.plusDays(adjustment.coerceIn(-2, 2).toLong()))
            return HijriDate(h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.YEAR))
        }
    }
}

/**
 * One day's times for a location. A missing entry means the calculation could not produce that
 * time (e.g. polar day or night); it is never filled with a guess.
 */
data class DaySchedule(
    val date: LocalDate,
    val zone: ZoneId,
    val times: Map<Prayer, Instant>,
    val methodName: String,
) {
    fun time(prayer: Prayer): Instant? = times[prayer]

    val isFriday: Boolean get() = date.isFriday

    /** Prayers (and sunrise) that could not be calculated for this date. */
    val undefined: List<Prayer> get() = Prayer.entries.filter { it !in times }

    val hasUndefined: Boolean get() = undefined.isNotEmpty()

    /** Why times are missing, and what the user can do. Null when every time is defined. */
    val undefinedExplanation: Pair<String, String?>?
        get() {
            if (!hasUndefined) return null
            if (times.isEmpty()) {
                return "The sun doesn't rise or set here on this date, so prayer times can't be calculated. Salah never invents a time." to
                    "Follow a nearby city or your local authority's timetable for these days."
            }
            val names = undefined.joinToString(" and ") { it.displayName }
            return "$names can't be calculated here on this date." to "Choose a high-latitude rule in Settings."
        }

    fun label(prayer: Prayer, jumuahRelabel: Boolean): String = prayer.label(isFriday, jumuahRelabel)
}

object PrayerSchedule {
    /** Computes the schedule for a local date. Always per date, so DST changes are handled by construction. */
    fun forDate(date: LocalDate, location: SavedLocation, settings: CalculationSettings): DaySchedule {
        val pt = PrayerTimes.compute(
            Coordinates(location.latitude, location.longitude), date, settings.adhanParameters(location),
        )
        val times = if (pt == null) emptyMap() else mapOf(
            Prayer.FAJR to pt.fajr, Prayer.SUNRISE to pt.sunrise, Prayer.DHUHR to pt.dhuhr,
            Prayer.ASR to pt.asr, Prayer.MAGHRIB to pt.maghrib, Prayer.ISHA to pt.isha,
        )
        return DaySchedule(date, location.zone, times, settings.methodName(location))
    }

    /** Consecutive days starting at [start]. */
    fun range(start: LocalDate, days: Int, location: SavedLocation, settings: CalculationSettings): List<DaySchedule> =
        (0 until maxOf(0, days)).map { forDate(start.plusDays(it.toLong()), location, settings) }

    /** Every day of the month containing [date]. */
    fun month(date: LocalDate, location: SavedLocation, settings: CalculationSettings): List<DaySchedule> =
        range(date.withDayOfMonth(1), date.lengthOfMonth(), location, settings)
}

data class NextPrayer(
    val prayer: Prayer,
    val time: Instant,
    /** True when the next prayer falls on a following local day (e.g. after Isha). */
    val isTomorrow: Boolean,
    /** The local day the prayer belongs to; used for the Jumu'ah label. */
    val date: LocalDate,
) {
    fun label(jumuahRelabel: Boolean): String = prayer.label(date.isFriday, jumuahRelabel)

    fun secondsRemaining(now: Instant): Double = maxOf(0.0, (time.toEpochMilli() - now.toEpochMilli()) / 1000.0)
}

object NextPrayerResolver {
    /**
     * The first prayer strictly after [now], looking into the following days if needed.
     * Returns null only if no prayer can be calculated within a week (polar regions).
     */
    fun resolve(now: Instant, location: SavedLocation, settings: CalculationSettings): NextPrayer? {
        val today = localDate(now, location.zone)
        for (offset in 0 until 7) {
            val s = PrayerSchedule.forDate(today.plusDays(offset.toLong()), location, settings)
            next(s, now, offset > 0)?.let { return it }
        }
        return null
    }

    internal fun next(schedule: DaySchedule, now: Instant, isTomorrow: Boolean): NextPrayer? {
        for (p in Prayer.prayers) {
            val t = schedule.time(p) ?: continue
            if (t > now) return NextPrayer(p, t, isTomorrow, schedule.date)
        }
        return null
    }
}

/** Everything the dashboard, tray and CLI need at one instant. */
data class PrayerClockState(
    val now: Instant,
    val today: DaySchedule,
    val next: NextPrayer?,
    /**
     * The prayer period [now] falls in, if any. Fajr's period ends at sunrise;
     * before today's Fajr there is no current period on today's timeline.
     */
    val current: Prayer?,
    /** Set while inside the NOW window after a prayer starts. */
    val nowPrayer: Prayer?,
) {
    val secondsToNext: Double? get() = next?.secondsRemaining(now)

    /** Seconds since [nowPrayer] began. */
    val secondsSinceNow: Double?
        get() {
            val p = nowPrayer ?: return null
            val t = today.time(p) ?: return null
            return maxOf(0.0, (now.toEpochMilli() - t.toEpochMilli()) / 1000.0)
        }
}

object PrayerClock {
    fun state(now: Instant, location: SavedLocation, settings: CalculationSettings, nowWindowMinutes: Int): PrayerClockState {
        val date = localDate(now, location.zone)
        val today = PrayerSchedule.forDate(date, location, settings)
        val next = NextPrayerResolver.resolve(now, location, settings)

        var current: Prayer? = null
        for (p in Prayer.prayers) {
            val t = today.time(p)
            if (t != null && t <= now) current = p
        }
        val sunrise = today.time(Prayer.SUNRISE)
        if (current == Prayer.FAJR && sunrise != null && now >= sunrise) current = null

        var nowPrayer: Prayer? = null
        if (nowWindowMinutes > 0) {
            for (p in Prayer.prayers) {
                val t = today.time(p) ?: continue
                if (t <= now && now < t.plusSeconds(nowWindowMinutes * 60L)) nowPrayer = p
            }
        }
        return PrayerClockState(now, today, next, current, nowPrayer)
    }
}
