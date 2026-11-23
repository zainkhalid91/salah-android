package salah.core.focusmode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import salah.core.Prayer

/** How Salah behaves when the user is on a call or in Do Not Disturb at prayer time. */
@Serializable
enum class CallBusyMode(val displayName: String) {
    /** Show the nudge now; show the full-screen card once the user is free. */
    @SerialName("nudgeThenCard") NUDGE_THEN_CARD("Nudge, card after"),
    /** Show only the nudge. Never show the card for this prayer. */
    @SerialName("nudgeOnly") NUDGE_ONLY("Just nudge"),
}

@Serializable
data class FocusModeCardSettings(
    val enabled: Boolean = true,
    val autoCloseMinutes: Int = 15,
) {
    internal fun normalized() = if (autoCloseMinutes in ALLOWED_AUTO_CLOSE_MINUTES) this else copy(autoCloseMinutes = 15)

    companion object {
        val ALLOWED_AUTO_CLOSE_MINUTES = listOf(10, 15, 30)
    }
}

@Serializable
data class FocusModePauseMediaSettings(
    val enabled: Boolean = true,
    val resumeOnDone: Boolean = true,
)

/** On macOS: a Focus. On Android: Do Not Disturb (priority only). Same JSON key, `focus`. */
@Serializable
data class FocusModeDndSettings(
    val enabled: Boolean = false,
    val turnOffOnDone: Boolean = true,
)

@Serializable
data class FocusModeBusySettings(
    val onCall: CallBusyMode = CallBusyMode.NUDGE_THEN_CARD,
    /** Do Not Disturb turned on by the user (not by Salah) counts as busy. */
    val focusIsBusy: Boolean = true,
)

/**
 * Focus Mode: a calm full-screen card, pausing audio and turning on Do Not Disturb at prayer
 * time, skipped (with a small nudge instead) when the user is busy. Off by default.
 */
@Serializable
data class FocusModeSettings(
    val enabled: Boolean = false,
    /** Raw prayer names; never contains sunrise. */
    @SerialName("prayers") val prayerNames: List<String> = DEFAULT_PRAYERS.map { it.raw },
    val card: FocusModeCardSettings = FocusModeCardSettings(),
    val pauseMedia: FocusModePauseMediaSettings = FocusModePauseMediaSettings(),
    val focus: FocusModeDndSettings = FocusModeDndSettings(),
    val whenBusy: FocusModeBusySettings = FocusModeBusySettings(),
) {
    @Transient
    val prayers: Set<Prayer> = prayerNames.mapNotNull { Prayer.fromRaw(it) }.filter { it.isPrayer }.toSet()

    /** Whether any of the three actions is on. If none are, Focus Mode has nothing to do. */
    val hasAnyAction: Boolean get() = card.enabled || pauseMedia.enabled || focus.enabled

    fun withPrayer(p: Prayer, on: Boolean): FocusModeSettings {
        val set = if (on) prayers + p else prayers - p
        return copy(prayerNames = Prayer.prayers.filter { it in set }.map { it.raw })
    }

    internal fun normalized() = copy(
        prayerNames = Prayer.prayers.filter { it in prayers }.map { it.raw },
        card = card.normalized(),
    )

    companion object {
        val DEFAULT_PRAYERS: List<Prayer> = listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    }
}
