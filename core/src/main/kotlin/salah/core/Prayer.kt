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

    /** Display label, relabelling Dhuhr as Jumu'ah on Fridays when enabled. */
    fun label(isFriday: Boolean, jumuahRelabel: Boolean): String =
        if (this == DHUHR && isFriday && jumuahRelabel) "Jumu'ah" else displayName

    companion object {
        /** The five obligatory prayers, in order. */
        val prayers: List<Prayer> = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)

        fun fromRaw(raw: String): Prayer? = entries.firstOrNull { it.raw == raw }

        /** Case-insensitive lookup that also accepts "jumuah"/"jumu'ah" for Dhuhr. */
        fun fromName(name: String): Prayer? {
            val key = name.lowercase().replace("'", "")
            if (key == "jumuah" || key == "jummah") return DHUHR
            return fromRaw(key)
        }
    }
}
