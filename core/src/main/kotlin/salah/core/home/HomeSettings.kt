package salah.core.home

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** A card on the Home screen. Keys match the macOS app's `home.widgets`. */
enum class HomeWidget(val raw: String, val displayName: String) {
    /** Countdown to the next prayer; on Friday after Asr, the last hour before Maghrib. */
    NEXT_PRAYER("nextPrayer", "Next prayer"),
    PRAYER_TIMES("prayerTimes", "Prayer times"),
    DUA_OF_THE_DAY("duaOfTheDay", "Du'a of the day"),
    /** What's playing, or a surah suggested for the moment (Al-Kahf on Friday, Al-Mulk at night). */
    QURAN("quran", "Quran"),
    /** Today's or tomorrow's sunnah fast; hidden when there's none. */
    SUNNAH_FAST("sunnahFast", "Sunnah fasting"),
    KIND_WORD("kindWord", "Kind word of the day"),
    /** How Kind Words decides when to send. */
    KIND_WORDS_SCHEDULE("kindWordsSchedule", "Kind Words schedule");

    companion object {
        fun fromRaw(raw: String): HomeWidget? = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * The Home screen's widgets, in order. Users turn them on and off and reorder them. Stored as
 * raw names so unknown names (from a newer app) are dropped instead of failing the whole file.
 */
@Serializable
data class HomeSettings(
    @SerialName("widgets") val widgetNames: List<String> = DEFAULT_WIDGETS.map { it.raw },
) {
    @Transient
    val widgets: List<HomeWidget> = widgetNames.mapNotNull { HomeWidget.fromRaw(it) }.distinct()

    fun isOn(w: HomeWidget): Boolean = w in widgets

    fun set(w: HomeWidget, on: Boolean): HomeSettings =
        of(if (on) (if (w in widgets) widgets else widgets + w) else widgets - w)

    /** Moves a shown widget up (-1) or down (+1). */
    fun move(w: HomeWidget, by: Int): HomeSettings {
        val i = widgets.indexOf(w)
        val j = i + by
        if (i < 0 || j !in widgets.indices) return this
        val l = widgets.toMutableList()
        l[i] = l[j]
        l[j] = w
        return of(l)
    }

    internal fun normalized() = of(widgets)

    companion object {
        val DEFAULT_WIDGETS = listOf(
            HomeWidget.NEXT_PRAYER, HomeWidget.PRAYER_TIMES, HomeWidget.DUA_OF_THE_DAY, HomeWidget.QURAN, HomeWidget.SUNNAH_FAST,
        )

        fun of(list: List<HomeWidget>) = HomeSettings(list.map { it.raw })
    }
}
