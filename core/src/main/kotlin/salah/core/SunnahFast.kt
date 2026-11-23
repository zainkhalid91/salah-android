package salah.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A recommended (sunnah) fast on a given day, shown on the Schedule and Home. Ramadan and the
 * days on which fasting is not allowed (the two Eids and the days of Tashriq) never get one.
 * Ported from the macOS app's SunnahFast.
 */
enum class SunnahFast {
    MONDAY_THURSDAY, WHITE_DAYS, ARAFAH, ASHURA;

    /** The day's special name, or null for a plain Monday or Thursday. */
    val dayName: String?
        get() = when (this) {
            MONDAY_THURSDAY -> null
            WHITE_DAYS -> "White day"
            ARAFAH -> "Arafah"
            ASHURA -> "Ashura"
        }

    /** Why, with its source. */
    val reason: String
        get() = when (this) {
            MONDAY_THURSDAY ->
                "“Deeds are presented on Monday and Thursday, and I love that my deeds are presented while I am fasting.” — At-Tirmidhi 747"
            WHITE_DAYS -> "The White Days, the 13th, 14th and 15th of each Hijri month. — An-Nasa’i 2420"
            ARAFAH -> "Fasting the Day of Arafah expiates the sins of the past year and the coming year. — Muslim 1162"
            ASHURA -> "Fasting Ashura expiates the sins of the past year; fast the 9th with it. — Muslim 1162, 1134"
        }

    companion object {
        const val FOOTNOTE =
            "Sunnah fast: Mondays and Thursdays, the White Days (13–15 of each Hijri month), Arafah and Ashura."

        /**
         * Every reason to fast [date], most notable first; empty when there's none. Uses the same
         * Hijri adjustment as the rest of the app, so it follows local moon sighting.
         */
        fun reasons(date: LocalDate, hijriAdjustment: Int = 0): List<SunnahFast> {
            val h = runCatching { HijriDate.of(date, hijriAdjustment) }.getOrNull() ?: return emptyList()
            // Ramadan is obligatory, not sunnah.
            if (h.month == 9) return emptyList()
            // No fasting on Eid al-Fitr, Eid al-Adha or the days of Tashriq.
            if (h.month == 10 && h.day == 1) return emptyList()
            if (h.month == 12 && h.day in 10..13) return emptyList()

            val out = mutableListOf<SunnahFast>()
            if (h.month == 12 && h.day == 9) out += ARAFAH
            if (h.month == 1 && (h.day == 9 || h.day == 10)) out += ASHURA
            if (h.day in 13..15) out += WHITE_DAYS
            if (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY) out += MONDAY_THURSDAY
            return out
        }

        /** The Schedule tag: "Sunnah fast", plus the day's names, e.g. "Sunnah fast · White day". */
        fun tag(reasons: List<SunnahFast>, lang: AppLanguage = AppLanguage.EN): String? {
            if (reasons.isEmpty()) return null
            return (listOf("Sunnah fast") + reasons.mapNotNull { it.dayName }).joinToString(" · ") { AppText.t(lang, it) }
        }
    }
}
