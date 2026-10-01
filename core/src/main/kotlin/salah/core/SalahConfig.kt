package salah.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.time.Instant

@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified E : Enum<E>> serialName(e: E): String = serializer<E>().descriptor.getElementName(e.ordinal)

@Serializable
enum class ThemeSetting {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK;

    val raw: String get() = serialName(this)
}

/** What the tray icon's tooltip shows (the macOS menu bar label). Key names match the macOS config. */
@Serializable
enum class MenuBarStyle(val displayName: String) {
    /** "☾ Asr · 12m" */
    @SerialName("nameAndCountdown") NAME_AND_COUNTDOWN("Name and countdown"),
    /** "☾ 4:05 PM" */
    @SerialName("timeOnly") TIME_ONLY("Time only"),
    @SerialName("iconOnly") ICON_ONLY("Icon only");

    val raw: String get() = serialName(this)
}

@Serializable
data class DisplaySettings(
    val use24HourClock: Boolean = true,
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val jumuahRelabel: Boolean = true,
    /** Manual Hijri adjustment for local moon sighting, clamped to -2..2. */
    val hijriAdjustment: Int = 0,
    /** How long the display shows NOW after a prayer starts, 0..60 minutes. */
    val nowWindowMinutes: Int = 15,
    /** On Windows: the notification-area (tray) icon. */
    val showMenuBarExtra: Boolean = true,
    val menuBarStyle: MenuBarStyle = MenuBarStyle.NAME_AND_COUNTDOWN,
) {
    internal fun normalized() = copy(
        hijriAdjustment = hijriAdjustment.coerceIn(-2, 2),
        nowWindowMinutes = nowWindowMinutes.coerceIn(0, 60),
    )
}

@Serializable
enum class ReminderSound(val displayName: String) {
    @SerialName("systemDefault") SYSTEM_DEFAULT("System default"),
    @SerialName("chime") CHIME("Soft chime"),
    @SerialName("silent") SILENT("Silent");

    val raw: String get() = serialName(this)
}

@Serializable
data class PrayerReminder(
    val enabled: Boolean = true,
    /** Minutes before the prayer for the early reminder; 0 means no early reminder. */
    val leadMinutes: Int = 10,
    /** Also notify at the prayer time itself. */
    val atTime: Boolean = true,
) {
    /** Lead times this reminder fires at, largest first; 0 is "at prayer time". */
    val leads: List<Int>
        get() = buildList {
            if (leadMinutes > 0) add(leadMinutes)
            if (atTime) add(0)
        }

    internal fun normalized() = if (leadMinutes in ALLOWED_LEADS) this else copy(leadMinutes = 10)

    companion object {
        val ALLOWED_LEADS = listOf(0, 5, 10, 15, 30)
    }
}

/** A minutes-since-midnight window, e.g. 23:00–04:30. May wrap past midnight. */
@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val start: String = "23:00",
    val end: String = "04:30",
) {
    /** True when the minute-of-day falls in [start, end), wrapping past midnight. */
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val s = minutes(start) ?: return false
        val e = minutes(end) ?: return false
        if (s == e) return false
        return if (s < e) minuteOfDay in s until e else (minuteOfDay >= s || minuteOfDay < e)
    }

    internal fun normalized() = copy(
        start = if (minutes(start) == null) "23:00" else start,
        end = if (minutes(end) == null) "04:30" else end,
    )

    companion object {
        fun minutes(hhmm: String): Int? {
            val parts = hhmm.split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
    }
}

@Serializable
data class ReminderSettings(
    val enabled: Boolean = true,
    /** ISO 8601 instant, as written by the macOS app. */
    @SerialName("pausedUntil") val pausedUntilRaw: String? = null,
    val sound: ReminderSound = ReminderSound.SYSTEM_DEFAULT,
    val quietHours: QuietHours = QuietHours(),
    /** Keyed by [Prayer.raw] for the five prayers. */
    val prayers: Map<String, PrayerReminder> = DEFAULT_PRAYERS,
) {
    val pausedUntil: Instant? get() = pausedUntilRaw?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun withPausedUntil(i: Instant?) = copy(pausedUntilRaw = i?.let { Iso.instant(it) })

    fun reminder(prayer: Prayer): PrayerReminder = prayers[prayer.raw] ?: PrayerReminder(enabled = false)

    fun update(prayer: Prayer, body: (PrayerReminder) -> PrayerReminder) =
        copy(prayers = prayers + (prayer.raw to body(reminder(prayer))))

    fun isPaused(now: Instant): Boolean = pausedUntil?.let { it > now } ?: false

    internal fun normalized(): ReminderSettings {
        val merged = DEFAULT_PRAYERS.toMutableMap()
        for ((k, v) in prayers) if (Prayer.fromRaw(k)?.isPrayer == true) merged[k] = v.normalized()
        return copy(
            pausedUntilRaw = pausedUntil?.let { Iso.instant(it) },
            quietHours = quietHours.normalized(),
            prayers = merged,
        )
    }

    companion object {
        val DEFAULT_PRAYERS: Map<String, PrayerReminder> = mapOf(
            "fajr" to PrayerReminder(leadMinutes = 15),
            "dhuhr" to PrayerReminder(leadMinutes = 10),
            "asr" to PrayerReminder(leadMinutes = 10),
            "maghrib" to PrayerReminder(leadMinutes = 5),
            "isha" to PrayerReminder(leadMinutes = 10),
        )
    }
}

/** The single shared configuration, read and written by both the app and the CLI. */
@Serializable
data class SalahConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val location: SavedLocation? = null,
    val calculation: CalculationSettings = CalculationSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val reminders: ReminderSettings = ReminderSettings(),
    val launchAtLogin: Boolean = true,
) {
    val methodName: String get() = calculation.methodName(location)

    internal fun normalized() = copy(
        location = location?.normalized(),
        display = display.normalized(),
        reminders = reminders.normalized(),
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val DEFAULT = SalahConfig()
    }
}
