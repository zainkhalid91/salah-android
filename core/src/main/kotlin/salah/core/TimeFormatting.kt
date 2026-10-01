package salah.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formatting shared by the app, tray, notifications and CLI so they read identically. */
object TimeFormatting {
    /** "20:10" or "8:10 PM" in the given zone. */
    fun clock(t: Instant, zone: ZoneId, use24Hour: Boolean, padHour: Boolean = false): String {
        val (time, period) = parts(t, zone, use24Hour, padHour)
        return if (period.isEmpty()) time else "$time $period"
    }

    /** Splits a clock time into its digits and AM/PM marker, so views can style them separately. */
    fun parts(t: Instant, zone: ZoneId, use24Hour: Boolean, padHour: Boolean = false): Pair<String, String> {
        val z = t.atZone(zone)
        val h = z.hour
        val m = z.minute
        if (use24Hour) return String.format(Locale.ROOT, "%02d:%02d", h, m) to ""
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.ROOT, if (padHour) "%02d:%02d" else "%d:%02d", h12, m) to (if (h < 12) "AM" else "PM")
    }

    /** "HH:MM:SS" with zero padding; negative values clamp to zero. */
    fun countdown(seconds: Double): String {
        val s = maxOf(0L, Math.floor(seconds).toLong())
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Compact form for the tray and shell prompts: "42m", "1h 25m", "<1m". Truncates, matching the countdown. */
    fun short(seconds: Double): String {
        val m = (maxOf(0.0, seconds) / 60).toLong()
        if (m == 0L) return "<1m"
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    /** Spoken form for screen readers: "1 hour 25 minutes". */
    fun spoken(seconds: Double): String {
        val m = Math.ceil(maxOf(0.0, seconds) / 60).toLong()
        val h = m / 60
        val mm = m % 60
        val out = mutableListOf<String>()
        if (h > 0) out += "$h hour${if (h == 1L) "" else "s"}"
        if (mm > 0 || h == 0L) out += "$mm minute${if (mm == 1L) "" else "s"}"
        return out.joinToString(" ")
    }

    /** ISO 8601 with the location's offset, e.g. "2026-09-27T20:10:00+08:00". */
    fun iso8601(t: Instant, zone: ZoneId): String =
        t.truncatedTo(ChronoUnit.SECONDS).atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .replace(Regex("Z$"), "Z")

    /** "Sunday, 27 September 2026" (callers upper-case as needed). */
    fun longDate(d: LocalDate, includeYear: Boolean = true): String =
        "${d.weekdayName}, ${d.dayOfMonth} ${d.monthName}" + if (includeYear) " ${d.year}" else ""

    /** "Fri, 25 Sep" */
    fun shortDate(d: LocalDate): String = "${d.weekdayName.take(3)}, ${d.dayOfMonth} ${d.monthName.take(3)}"
}

/** ISO 8601 instants the way Foundation's ISO8601DateFormatter writes them: whole seconds, Z suffix. */
object Iso {
    fun instant(t: Instant): String = DateTimeFormatter.ISO_INSTANT.format(t.truncatedTo(ChronoUnit.SECONDS))

    fun parse(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant() }.getOrNull()

    val utc: ZoneId = ZoneOffset.UTC
}
