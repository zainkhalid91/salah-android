package com.zainkhalid.salah.focus

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.zainkhalid.salah.R
import com.zainkhalid.salah.quran.QuranPlayer
import com.zainkhalid.salah.reminders.Notifications
import com.zainkhalid.salah.reminders.ReminderReceiver
import com.zainkhalid.salah.reminders.ReminderScheduler
import com.zainkhalid.salah.reminders.postNotification
import com.zainkhalid.salah.repo
import com.zainkhalid.salah.ui.MainActivity
import salah.core.AppText
import salah.core.Prayer
import salah.core.PrayerSchedule
import salah.core.focusmode.BusyReason
import salah.core.focusmode.BusyState
import salah.core.focusmode.CardNote
import salah.core.focusmode.FocusModeContext
import salah.core.focusmode.FocusModePlanner
import salah.core.focusmode.PlannedAction
import salah.core.localDate
import java.time.Duration
import java.time.Instant

/**
 * Carries out what [FocusModePlanner] decides at prayer time: pause audio, turn on Do Not
 * Disturb, show the full-screen card, or nudge when the user is on a call. Done (or the card's
 * auto close) undoes what Salah turned on, and only that.
 */
object FocusModeRunner {
    const val ACTION_RECHECK = "com.zainkhalid.salah.FOCUS_RECHECK"
    const val ACTION_DONE = "com.zainkhalid.salah.FOCUS_DONE"
    const val EXTRA_NOTE = "note"

    private const val PREFS = "salah_focus"
    private const val KEY_HANDLED = "handled"
    private const val KEY_OWNS_DND = "ownsDnd"
    private const val KEY_PAUSED_QURAN = "pausedQuran"
    private const val KEY_ACTIVE = "active"
    private const val CARD_ID = 0x5A1A
    private const val NUDGE_ID = 0x5A1B
    /** A deferred card stops waiting after this long, or when the next prayer begins. */
    private val DEFER_LIMIT = Duration.ofMinutes(60)
    private val RECHECK_EVERY = Duration.ofMinutes(2)

    private var focusRequest: AudioFocusRequest? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The prayer alarm fired. Also pauses the Quran player when "pause at prayer time" is on. */
    fun onPrayerTime(context: Context, prayer: Prayer, time: Instant) {
        val config = context.repo.config.value
        if (config.quran.pauseAtPrayerTime && busy(context) != BusyState.ON_CALL) {
            QuranPlayer.existing()?.pauseForPrayer()
        }
        val key = handledKey(context, prayer, time)
        val ctx = FocusModeContext(
            prayer = prayer, prayerTime = time, now = Instant.now(), settings = config.focusMode,
            busy = busy(context),
            // The card shows over the lock screen on Android, so locking never defers.
            isScreenLocked = false,
            alreadyHandled = prefs(context).getString(KEY_HANDLED, null) == key,
            salahOwnsFocus = prefs(context).getBoolean(KEY_OWNS_DND, false),
        )
        val actions = FocusModePlanner.decide(ctx)
        if (actions.isEmpty()) return
        prefs(context).edit { putString(KEY_HANDLED, key) }
        run(context, prayer, time, actions)
    }

    /** Re-checks a deferred card: show it once the call or Do Not Disturb ends, give up after an hour. */
    fun recheck(context: Context, prayer: Prayer, time: Instant, note: CardNote) {
        val now = Instant.now()
        val next = nextPrayerTime(context, prayer, time)
        if (now > time.plus(DEFER_LIMIT) || (next != null && now >= next)) return
        val config = context.repo.config.value
        val state = busy(context)
        val stillBusy = state == BusyState.ON_CALL ||
            (state == BusyState.FOCUS_ON && config.focusMode.whenBusy.focusIsBusy && !prefs(context).getBoolean(KEY_OWNS_DND, false))
        if (stillBusy) {
            scheduleRecheck(context, prayer, time, note)
            return
        }
        NotificationManagerCompat.from(context).cancel(NUDGE_ID)
        if (config.focusMode.card.enabled) showCard(context, prayer, time, note)
    }

    private fun run(context: Context, prayer: Prayer, time: Instant, actions: List<PlannedAction>) {
        for (a in actions) {
            when (a) {
                PlannedAction.PauseMedia -> pauseMedia(context)
                PlannedAction.FocusOn -> dndOn(context)
                is PlannedAction.ShowCard -> showCard(context, prayer, time, a.note)
                is PlannedAction.ShowNudge -> showNudge(context, prayer, time, a.reason, a.cardLater)
                PlannedAction.DeferUntilFree -> {
                    val note = if (busy(context) == BusyState.ON_CALL) CardNote.CALL_ENDED else CardNote.FOCUS_ENDED
                    scheduleRecheck(context, prayer, time, note)
                }
                PlannedAction.DeferUntilUnlock -> Unit
            }
        }
        if (actions.any { it == PlannedAction.PauseMedia || it == PlannedAction.FocusOn }) {
            prefs(context).edit { putBoolean(KEY_ACTIVE, true) }
            // Undo at auto close even if the card is never opened.
            val minutes = context.repo.config.value.focusMode.card.autoCloseMinutes.toLong()
            ReminderScheduler.setAt(context, time.plus(Duration.ofMinutes(minutes)), Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE), ACTION_DONE.hashCode())
        }
    }

    /** "I'm done" on the card, or its auto close. Safe to call more than once. */
    fun done(context: Context) {
        val p = prefs(context)
        NotificationManagerCompat.from(context).cancel(CARD_ID)
        if (!p.getBoolean(KEY_ACTIVE, false)) return
        val s = context.repo.config.value.focusMode
        focusRequest?.let { context.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        if (p.getBoolean(KEY_PAUSED_QURAN, false) && s.pauseMedia.resumeOnDone) QuranPlayer.existing()?.resume()
        if (p.getBoolean(KEY_OWNS_DND, false) && s.focus.turnOffOnDone) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        p.edit {
            putBoolean(KEY_ACTIVE, false)
            putBoolean(KEY_PAUSED_QURAN, false)
            if (s.focus.turnOffOnDone) putBoolean(KEY_OWNS_DND, false)
        }
    }

    /** On a call, or in Do Not Disturb (whoever turned it on; the planner checks who owns it). */
    fun busy(context: Context): BusyState {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION) return BusyState.ON_CALL
        val nm = context.getSystemService(NotificationManager::class.java)
        val filter = nm.currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return BusyState.FOCUS_ON
        // Salah's own DND has ended (the user turned it off), so it no longer owns one.
        prefs(context).edit { putBoolean(KEY_OWNS_DND, false) }
        return BusyState.FREE
    }

    fun canControlDnd(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun pauseMedia(context: Context) {
        val s = context.repo.config.value.focusMode.pauseMedia
        if (QuranPlayer.existing()?.pauseForPrayer() == true) prefs(context).edit { putBoolean(KEY_PAUSED_QURAN, true) }
        // Other apps: take audio focus. Transient focus hands it back on Done, so they resume.
        val audio = context.getSystemService(AudioManager::class.java)
        val req = AudioFocusRequest.Builder(if (s.resumeOnDone) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(req)
        if (s.resumeOnDone) focusRequest = req else audio.abandonAudioFocusRequest(req)
    }

    private fun dndOn(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        prefs(context).edit { putBoolean(KEY_OWNS_DND, true) }
    }

    /** Full-screen intent: the card opens over the lock screen, or shows as a heads-up while in use. */
    fun showCard(context: Context, prayer: Prayer, time: Instant, note: CardNote?) {
        val config = context.repo.config.value
        val lang = config.display.lang
        val label = prayerLabel(context, prayer, time)
        val intent = FocusCardActivity.intent(context, prayer, time, note)
        val pi = PendingIntent.getActivity(context, CARD_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, Notifications.FOCUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_salah)
            .setColor(ContextCompat.getColor(context, R.color.crimson))
            .setContentTitle(AppText.t(lang, "Time for {0}", label))
            .setContentText(AppText.t(lang, "Step away for a few minutes."))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(Duration.ofMinutes(config.focusMode.card.autoCloseMinutes.toLong()).toMillis())
            .build()
        postNotification(context, CARD_ID, n)
    }

    private fun showNudge(context: Context, prayer: Prayer, time: Instant, reason: BusyReason, cardLater: Boolean) {
        val lang = context.repo.config.value.display.lang
        val label = prayerLabel(context, prayer, time)
        val body = when {
            reason == BusyReason.CALL && cardLater -> AppText.t(lang, "You're on a call. The card will show when it ends.")
            reason == BusyReason.CALL -> AppText.t(lang, "You're on a call. Pray when you're free.")
            cardLater -> AppText.t(lang, "Do Not Disturb is on. The card will show when it ends.")
            else -> AppText.t(lang, "Do Not Disturb is on. Pray when you're free.")
        }
        val open = PendingIntent.getActivity(
            context, NUDGE_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.NUDGE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_salah)
            .setColor(ContextCompat.getColor(context, R.color.crimson))
            .setContentTitle(AppText.t(lang, "It's time for {0}", label))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        postNotification(context, NUDGE_ID, n)
    }

    private fun scheduleRecheck(context: Context, prayer: Prayer, time: Instant, note: CardNote) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_RECHECK)
            .setData(Uri.parse("salah://focus/${prayer.raw}"))
            .putExtra(ReminderScheduler.EXTRA_PRAYER, prayer.raw)
            .putExtra(ReminderScheduler.EXTRA_PRAYER_TIME, time.toEpochMilli())
            .putExtra(EXTRA_NOTE, note.raw)
        ReminderScheduler.setAt(context, Instant.now().plus(RECHECK_EVERY), intent, ACTION_RECHECK.hashCode())
    }

    fun prayerLabel(context: Context, prayer: Prayer, time: Instant): String {
        val config = context.repo.config.value
        val zone = config.location?.zone ?: java.time.ZoneId.systemDefault()
        val friday = localDate(time, zone).dayOfWeek == java.time.DayOfWeek.FRIDAY
        return prayer.label(friday, config.display.jumuahRelabel, config.display.lang)
    }

    private fun handledKey(context: Context, prayer: Prayer, time: Instant): String {
        val zone = context.repo.config.value.location?.zone ?: java.time.ZoneId.systemDefault()
        return "${localDate(time, zone)}.${prayer.raw}"
    }

    private fun nextPrayerTime(context: Context, prayer: Prayer, time: Instant): Instant? {
        val config = context.repo.config.value
        val location = config.location?.takeIf { it.isValid } ?: return null
        val date = localDate(time, location.zone)
        val i = Prayer.prayers.indexOf(prayer)
        return if (i + 1 < Prayer.prayers.size) {
            PrayerSchedule.forDate(date, location, config.calculation).time(Prayer.prayers[i + 1])
        } else {
            PrayerSchedule.forDate(date.plusDays(1), location, config.calculation).time(Prayer.FAJR)
        }
    }
}
