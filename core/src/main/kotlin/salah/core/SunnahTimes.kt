package salah.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Voluntary prayers and the times around the five daily prayers, in timeline order. */
enum class ExtraTime(val raw: String, val displayName: String, val isPrayer: Boolean = true) {
    TAHAJJUD("tahajjud", "Tahajjud"),
    ISHRAQ("ishraq", "Ishraq"),
    DUHA("duha", "Duha"),
    ZAWAL("zawal", "Zawal", isPrayer = false),
    AWWABIN("awwabin", "Awwabin"),
    MIDNIGHT("midnight", "Midnight", isPrayer = false),
}

/** When an extra time begins and ends. */
data class ExtraWindow(val time: ExtraTime, val start: Instant, val end: Instant)

/**
 * Times for the sunnah prayers, worked out from the obligatory ones:
 *
 * - Tahajjud: the last third of the night, from Maghrib to Fajr, until Fajr.
 * - Ishraq: 20 minutes after sunrise, when the sun has fully risen.
 * - Duha (Chasht): once a quarter of the day has passed, until zawal.
 * - Zawal: solar noon. No prayer from 5 minutes before it until Dhuhr.
 * - Awwabin: after Maghrib and its sunnah (about 15 minutes), until Isha.
 * - Midnight: halfway between Maghrib and Fajr, the end of the preferred time for Isha.
 *
 * Tahajjud uses last night (yesterday's Maghrib to today's Fajr), so every
 * time on a day's timeline falls on that day's clock.
 */
object SunnahTimes {
    private val ISHRAQ_AFTER_SUNRISE: Duration = Duration.ofMinutes(20)
    private val ZAWAL_MARGIN: Duration = Duration.ofMinutes(5)
    private val AWWABIN_AFTER_MAGHRIB: Duration = Duration.ofMinutes(15)

    fun forDate(date: LocalDate, location: SavedLocation, settings: CalculationSettings): List<ExtraWindow> {
        val today = PrayerSchedule.forDate(date, location, settings)
        val yesterday = PrayerSchedule.forDate(date.minusDays(1), location, settings)
        val tomorrow = PrayerSchedule.forDate(date.plusDays(1), location, settings)
        return of(today, yesterday.time(Prayer.MAGHRIB), tomorrow.time(Prayer.FAJR))
    }

    /** Leaves out anything that can't be worked out, as the prayer times do. */
    fun of(day: DaySchedule, lastMaghrib: Instant?, nextFajr: Instant?): List<ExtraWindow> {
        val fajr = day.time(Prayer.FAJR)
        val sunrise = day.time(Prayer.SUNRISE)
        val dhuhr = day.time(Prayer.DHUHR)
        val maghrib = day.time(Prayer.MAGHRIB)
        val isha = day.time(Prayer.ISHA)
        val noon = day.noon
        val out = mutableListOf<ExtraWindow>()

        if (lastMaghrib != null && fajr != null) {
            out += ExtraWindow(ExtraTime.TAHAJJUD, minute(lastMaghrib, fajr, 2.0 / 3), fajr)
        }
        if (sunrise != null && noon != null && maghrib != null) {
            val zawal = noon.minus(ZAWAL_MARGIN)
            val duha = minute(sunrise, maghrib, 0.25)
            out += ExtraWindow(ExtraTime.ISHRAQ, sunrise.plus(ISHRAQ_AFTER_SUNRISE), duha)
            out += ExtraWindow(ExtraTime.DUHA, duha, zawal)
            out += ExtraWindow(ExtraTime.ZAWAL, zawal, dhuhr?.takeIf { it > noon } ?: noon.plus(ZAWAL_MARGIN))
        }
        if (maghrib != null && isha != null) {
            out += ExtraWindow(ExtraTime.AWWABIN, maghrib.plus(AWWABIN_AFTER_MAGHRIB), isha)
        }
        if (maghrib != null && nextFajr != null) {
            val midnight = minute(maghrib, nextFajr, 0.5)
            out += ExtraWindow(ExtraTime.MIDNIGHT, midnight, midnight)
        }
        return out.filter { it.start <= it.end }
    }

    /** The instant [fraction] of the way from [a] to [b], to the minute. */
    private fun minute(a: Instant, b: Instant, fraction: Double): Instant {
        val seconds = a.epochSecond + ((b.epochSecond - a.epochSecond) * fraction).toLong()
        return Instant.ofEpochSecond(Math.floorDiv(seconds, 60L) * 60)
    }
}

/** One row on a day's timeline: an obligatory prayer (or sunrise), or an extra time. */
sealed interface TimelineItem {
    val start: Instant?

    data class Fard(val prayer: Prayer, override val start: Instant?) : TimelineItem
    data class Extra(val window: ExtraWindow) : TimelineItem {
        override val start: Instant get() = window.start
    }

    companion object {
        /** Where each extra time sits between the prayers. */
        private val ORDER: List<Any> = listOf(
            ExtraTime.TAHAJJUD, Prayer.FAJR, Prayer.SUNRISE, ExtraTime.ISHRAQ, ExtraTime.DUHA, ExtraTime.ZAWAL,
            Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, ExtraTime.AWWABIN, Prayer.ISHA, ExtraTime.MIDNIGHT,
        )

        /** The prayers in order, with [extras] merged in when given. */
        fun of(day: DaySchedule?, extras: List<ExtraWindow>): List<TimelineItem> = ORDER.mapNotNull { key ->
            when (key) {
                is Prayer -> Fard(key, day?.time(key))
                is ExtraTime -> extras.firstOrNull { it.time == key }?.let { Extra(it) }
                else -> null
            }
        }
    }
}
