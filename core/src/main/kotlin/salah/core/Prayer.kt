package salah.core

/** The six daily times shown on the timeline. Sunrise is a time marker, not a prayer. */
enum class Prayer(val raw: String, val displayName: String) {
    FAJR("fajr", "Fajr"),
    SUNRISE("sunrise", "Sunrise"),
    DHUHR("dhuhr", "Dhuhr"),
    ASR("asr", "Asr"),
    MAGHRIB("maghrib", "Maghrib"),
    ISHA("isha", "Isha");

    val isPrayer: Boolean get() = this != SUNRISE

    /** Display label, relabelling Dhuhr as Jumuah on Fridays when enabled. */
    fun label(isFriday: Boolean, jumuahRelabel: Boolean, lang: AppLanguage = AppLanguage.EN): String =
        AppText.t(lang, if (this == DHUHR && isFriday && jumuahRelabel) JUMUAH else displayName)

    companion object {
        const val JUMUAH = "Jumuah"

        /** The five obligatory prayers, in order. */
        val prayers: List<Prayer> = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)

        fun fromRaw(raw: String): Prayer? = entries.firstOrNull { it.raw == raw }
    }
}
