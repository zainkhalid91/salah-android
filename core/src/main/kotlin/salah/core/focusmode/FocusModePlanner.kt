package salah.core.focusmode

import salah.core.Prayer
import java.time.Duration
import java.time.Instant

/** Why Salah is holding off: on a call, or in Do Not Disturb. */
enum class BusyReason { CALL, FOCUS }

/** What the busy check currently reports. */
enum class BusyState { FREE, ON_CALL, FOCUS_ON }

/** Why the card, when it finally shows, carries a "just ended" pill. */
enum class CardNote(val raw: String) {
    CALL_ENDED("callEnded"), FOCUS_ENDED("focusEnded");

    companion object {
        fun fromRaw(raw: String?): CardNote? = entries.firstOrNull { it.raw == raw }
    }
}

/** One thing the app should do, in the order the planner returns them. */
sealed class PlannedAction {
    data object PauseMedia : PlannedAction()
    data object FocusOn : PlannedAction()
    data class ShowCard(val note: CardNote?) : PlannedAction()
    data class ShowNudge(val reason: BusyReason, val cardLater: Boolean) : PlannedAction()
    data object DeferUntilUnlock : PlannedAction()
    data object DeferUntilFree : PlannedAction()
}

/** Everything the planner needs to decide what happens for one prayer, at one instant. */
data class FocusModeContext(
    val prayer: Prayer,
    val prayerTime: Instant,
    val now: Instant,
    val settings: FocusModeSettings,
    val busy: BusyState,
    val isScreenLocked: Boolean,
    /** Already ran the at-time actions for this prayer (key `<date>.<prayer>`). */
    val alreadyHandled: Boolean,
    /** Salah itself turned on the current Do Not Disturb; that doesn't count as busy. */
    val salahOwnsFocus: Boolean,
    /** Set only when replaying a deferred card after a call or DND ended; carries its pill. */
    val pendingNote: CardNote? = null,
) {
    /** How late [now] is relative to prayer time; negative before it. */
    val lateness: Duration get() = Duration.between(prayerTime, now)
}

/**
 * The single pure decision function for Focus Mode, ported from the macOS app. Everything about
 * what to do at prayer time is decided here, with no side effects, so it is fully unit-testable.
 */
object FocusModePlanner {
    /** Actions are skipped once a trigger is more than this late (e.g. the phone was off). */
    val LATE_THRESHOLD: Duration = Duration.ofMinutes(10)

    fun decide(ctx: FocusModeContext): List<PlannedAction> {
        val s = ctx.settings
        if (!s.enabled || ctx.prayer !in s.prayers || ctx.alreadyHandled || !s.hasAnyAction) return emptyList()
        if (ctx.isScreenLocked) return listOf(PlannedAction.DeferUntilUnlock)

        if (ctx.busy == BusyState.ON_CALL) return busyActions(BusyReason.CALL, s)
        if (ctx.busy == BusyState.FOCUS_ON && s.whenBusy.focusIsBusy && !ctx.salahOwnsFocus) return busyActions(BusyReason.FOCUS, s)

        val isLate = ctx.lateness > LATE_THRESHOLD
        val withinAutoClose = ctx.now < ctx.prayerTime.plus(Duration.ofMinutes(s.card.autoCloseMinutes.toLong()))

        val actions = mutableListOf<PlannedAction>()
        if (!isLate) {
            if (s.pauseMedia.enabled) actions += PlannedAction.PauseMedia
            if (s.focus.enabled) actions += PlannedAction.FocusOn
        }
        if (s.card.enabled && (!isLate || withinAutoClose)) actions += PlannedAction.ShowCard(ctx.pendingNote)
        return actions
    }

    private fun busyActions(reason: BusyReason, s: FocusModeSettings): List<PlannedAction> {
        val cardLater = s.card.enabled && s.whenBusy.onCall == CallBusyMode.NUDGE_THEN_CARD
        return buildList {
            add(PlannedAction.ShowNudge(reason, cardLater))
            if (cardLater) add(PlannedAction.DeferUntilFree)
        }
    }
}
