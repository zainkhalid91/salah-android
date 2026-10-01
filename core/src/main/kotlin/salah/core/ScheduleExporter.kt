package salah.core

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** Plain-text, CSV and iCalendar renderings of a schedule, shared by the app's export and copy actions. */
object ScheduleExporter {
    fun text(days: List<DaySchedule>, location: SavedLocation, display: DisplaySettings): String {
        val lines = mutableListOf("${location.name} · ${days.firstOrNull()?.methodName ?: ""}")
        for (d in days) {
            val cols = Prayer.entries.map { p ->
                val label = d.label(p, display.jumuahRelabel)
                val t = d.time(p)?.let { TimeFormatting.clock(it, d.zone, display.use24HourClock) } ?: "—"
                "$label $t"
            }
            lines += "${TimeFormatting.shortDate(d.date)}  " + cols.joinToString("  ")
        }
        return lines.joinToString("\n") + "\n"
    }

    /** Times in 24-hour local time; empty cells mean the time is undefined for that date. */
    fun csv(days: List<DaySchedule>): String {
        val rows = mutableListOf("date,weekday,fajr,sunrise,dhuhr,asr,maghrib,isha,timezone,method")
        for (d in days) {
            val times = Prayer.entries.map { p -> d.time(p)?.let { TimeFormatting.clock(it, d.zone, true) } ?: "" }
            val fields = listOf(d.date.toString(), d.date.weekdayName) + times + listOf(d.zone.id, d.methodName)
            rows += fields.joinToString(",") { csvField(it) }
        }
        return rows.joinToString("\r\n") + "\r\n"
    }

    /** One zero-duration event per prayer (Sunrise excluded), in UTC. */
    fun ics(days: List<DaySchedule>, location: SavedLocation, jumuahRelabel: Boolean, now: Instant = Instant.now()): String {
        val stamp = utcStamp(now)
        val lines = mutableListOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Salah//Prayer Times//EN", "CALSCALE:GREGORIAN",
            "X-WR-CALNAME:Prayer times · ${icsText(location.name)}",
        )
        for (d in days) {
            for (p in Prayer.prayers) {
                val t = d.time(p) ?: continue
                lines += listOf(
                    "BEGIN:VEVENT",
                    "UID:salah-${d.date}-${p.raw}@salah.local",
                    "DTSTAMP:$stamp",
                    "DTSTART:${utcStamp(t)}",
                    "DTEND:${utcStamp(t)}",
                    "SUMMARY:${icsText(d.label(p, jumuahRelabel))}",
                    "LOCATION:${icsText(location.name)}",
                    "DESCRIPTION:${icsText(d.methodName)}",
                    "TRANSP:TRANSPARENT",
                    "END:VEVENT",
                )
            }
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") + "\r\n"
    }

    internal fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    internal fun icsText(s: String): String =
        s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    internal fun utcStamp(t: Instant): String {
        val z = t.atZone(ZoneOffset.UTC)
        return String.format(Locale.ROOT, "%04d%02d%02dT%02d%02d%02dZ", z.year, z.monthValue, z.dayOfMonth, z.hour, z.minute, z.second)
    }
}
