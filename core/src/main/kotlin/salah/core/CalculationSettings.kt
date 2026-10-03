package salah.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import salah.core.adhan.CalculationMethod
import salah.core.adhan.CalculationParameters
import salah.core.adhan.HighLatitudeRule
import salah.core.adhan.Madhab
import salah.core.adhan.PrayerAdjustments

/** Calculation methods exposed to users. Raw values match the macOS app's config file. */
@Serializable
enum class MethodID(val displayName: String, val shortName: String) {
    @SerialName("singapore") SINGAPORE("Singapore (MUIS)", "MUIS"),
    @SerialName("muslimWorldLeague") MUSLIM_WORLD_LEAGUE("Muslim World League", "MWL"),
    @SerialName("northAmerica") NORTH_AMERICA("North America (ISNA)", "ISNA"),
    @SerialName("egyptian") EGYPTIAN("Egyptian General Authority of Survey", "Egypt"),
    @SerialName("ummAlQura") UMM_AL_QURA("Umm al-Qura", "Umm al-Qura"),
    @SerialName("karachi") KARACHI("Karachi", "Karachi"),
    @SerialName("dubai") DUBAI("Dubai", "Dubai"),
    @SerialName("kuwait") KUWAIT("Kuwait", "Kuwait"),
    @SerialName("qatar") QATAR("Qatar", "Qatar"),
    @SerialName("moonsightingCommittee") MOONSIGHTING_COMMITTEE("Moonsighting Committee", "MSC"),
    @SerialName("turkey") TURKEY("Turkey", "Turkey"),
    @SerialName("tehran") TEHRAN("Tehran", "Tehran"),
    @SerialName("custom") CUSTOM("Custom", "Custom");

    val raw: String get() = serialName(this)

    internal val adhanMethod: CalculationMethod
        get() = when (this) {
            SINGAPORE -> CalculationMethod.SINGAPORE
            MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA
            EGYPTIAN -> CalculationMethod.EGYPTIAN
            UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
            KARACHI -> CalculationMethod.KARACHI
            DUBAI -> CalculationMethod.DUBAI
            KUWAIT -> CalculationMethod.KUWAIT
            QATAR -> CalculationMethod.QATAR
            MOONSIGHTING_COMMITTEE -> CalculationMethod.MOONSIGHTING_COMMITTEE
            TURKEY -> CalculationMethod.TURKEY
            TEHRAN -> CalculationMethod.TEHRAN
            CUSTOM -> CalculationMethod.OTHER
        }

    companion object {
        fun fromRaw(raw: String): MethodID? = entries.firstOrNull { it.raw == raw }

        /** Default method for a location: MUIS in Singapore, Muslim World League elsewhere. */
        fun automatic(location: SavedLocation?): MethodID {
            location ?: return MUSLIM_WORLD_LEAGUE
            if (location.timeZone == "Asia/Singapore" || location.countryCode?.uppercase() == "SG") return SINGAPORE
            return MUSLIM_WORLD_LEAGUE
        }
    }
}

@Serializable
enum class MadhabSetting(val displayName: String) {
    @SerialName("shafi") SHAFI("Shafi'i, Maliki, Hanbali"),
    @SerialName("hanafi") HANAFI("Hanafi");

    val raw: String get() = serialName(this)

    companion object {
        fun fromRaw(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
enum class HighLatitudeSetting(val displayName: String) {
    @SerialName("middleOfTheNight") MIDDLE_OF_THE_NIGHT("Middle of the night"),
    @SerialName("seventhOfTheNight") SEVENTH_OF_THE_NIGHT("Seventh of the night"),
    @SerialName("twilightAngle") TWILIGHT_ANGLE("Twilight angle");

    val raw: String get() = serialName(this)

    companion object {
        fun fromRaw(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
data class CalculationSettings(
    /** `null` means automatic: chosen from the location (see [MethodID.automatic]). */
    val method: MethodID? = null,
    val madhab: MadhabSetting = MadhabSetting.SHAFI,
    /** `null` means the library's recommendation for the latitude. */
    val highLatitudeRule: HighLatitudeSetting? = null,
    val customFajrAngle: Double = 20.0,
    val customIshaAngle: Double = 18.0,
    /** Manual per-prayer offsets in minutes, keyed by [Prayer.raw]. */
    val offsets: Map<String, Int> = emptyMap(),
) {
    fun offset(prayer: Prayer): Int = offsets[prayer.raw] ?: 0

    fun withOffset(minutes: Int, prayer: Prayer): CalculationSettings =
        copy(offsets = if (minutes == 0) offsets - prayer.raw else offsets + (prayer.raw to minutes))

    fun resolvedMethod(location: SavedLocation?): MethodID = method ?: MethodID.automatic(location)

    /** Human-readable method name, e.g. "Singapore (MUIS)" or "Custom (20° / 18°)". */
    fun methodName(location: SavedLocation?): String {
        val m = resolvedMethod(location)
        if (m == MethodID.CUSTOM) return "Custom (${angle(customFajrAngle)}° / ${angle(customIshaAngle)}°)"
        return m.displayName
    }


    internal fun adhanParameters(location: SavedLocation): CalculationParameters {
        val m = resolvedMethod(location)
        var params = m.adhanMethod.params
        if (m == MethodID.CUSTOM) {
            params = params.copy(fajrAngle = customFajrAngle, ishaAngle = customIshaAngle, ishaInterval = 0)
        }
        return params.copy(
            madhab = if (madhab == MadhabSetting.HANAFI) Madhab.HANAFI else Madhab.SHAFI,
            highLatitudeRule = when (highLatitudeRule) {
                HighLatitudeSetting.MIDDLE_OF_THE_NIGHT -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
                HighLatitudeSetting.SEVENTH_OF_THE_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
                HighLatitudeSetting.TWILIGHT_ANGLE -> HighLatitudeRule.TWILIGHT_ANGLE
                null -> null
            },
            adjustments = PrayerAdjustments(
                fajr = offset(Prayer.FAJR), sunrise = offset(Prayer.SUNRISE), dhuhr = offset(Prayer.DHUHR),
                asr = offset(Prayer.ASR), maghrib = offset(Prayer.MAGHRIB), isha = offset(Prayer.ISHA),
            ),
        )
    }

    companion object {
        /** "20" for whole numbers, "19.5" otherwise. */
        fun angle(v: Double): String = if (Math.rint(v) == v) v.toLong().toString() else v.toString()
    }
}
