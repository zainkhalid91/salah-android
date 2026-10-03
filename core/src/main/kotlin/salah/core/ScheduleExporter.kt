package salah.core

/** The schedule as plain text, for the share sheet. */
object ScheduleExporter {
    fun text(days: List<DaySchedule>, location: SavedLocation, display: DisplaySettings): String {
        val lang = display.lang
        val lines = mutableListOf("${location.name} · ${AppText.t(lang, days.firstOrNull()?.methodName ?: "")}")
        for (d in days) {
            val cols = Prayer.entries.map { p ->
                val label = d.label(p, display.jumuahRelabel, lang)
                val t = d.time(p)?.let { TimeFormatting.clock(it, d.zone, display.use24HourClock, lang = lang) } ?: "—"
                "$label $t"
            }
            lines += "${AppText.shortDate(d.date, lang)}  " + cols.joinToString("  ")
        }
        return lines.joinToString("\n") + "\n"
    }
}
