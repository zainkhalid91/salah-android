package com.zainkhalid.salah.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zainkhalid.salah.R
import com.zainkhalid.salah.ui.MainActivity
import salah.core.ReminderSound

object Notifications {
    // Channel sounds can't change after creation, so there is one channel per sound.
    private fun channelId(sound: ReminderSound) = when (sound) {
        ReminderSound.SYSTEM_DEFAULT -> "reminders_default"
        ReminderSound.CHIME -> "reminders_chime"
        ReminderSound.SILENT -> "reminders_silent"
    }
    private const val AZAN_CHANNEL = "prayer_azan"
    const val FOCUS_CHANNEL = "focus_mode"
    const val NUDGE_CHANNEL = "focus_nudge"
    const val UPDATES_CHANNEL = "updates"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val name = context.getString(R.string.channel_reminders)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val chime = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.salah_chime}")
        nm.createNotificationChannel(
            NotificationChannel(channelId(ReminderSound.SYSTEM_DEFAULT), name, NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(channelId(ReminderSound.CHIME), "$name (soft chime)", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(chime, attrs) },
        )
        val azan = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.salah_azan}")
        nm.createNotificationChannel(
            NotificationChannel(AZAN_CHANNEL, context.getString(R.string.channel_azan), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(azan, attrs) },
        )
        nm.createNotificationChannel(
            NotificationChannel(channelId(ReminderSound.SILENT), "$name (silent)", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setSound(null, null); enableVibration(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(FOCUS_CHANNEL, context.getString(R.string.channel_focus), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) },
        )
        nm.createNotificationChannel(
            NotificationChannel(NUDGE_CHANNEL, context.getString(R.string.channel_nudge), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setSound(null, null) },
        )
        nm.createNotificationChannel(
            NotificationChannel(UPDATES_CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** [silent] (Kind Words) uses the silent channel whatever the reminder sound is. */
    fun show(context: Context, id: String, title: String, body: String, sound: ReminderSound, azan: Boolean = false, silent: Boolean = false) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val channel = when {
            silent -> channelId(ReminderSound.SILENT)
            azan -> AZAN_CHANNEL
            else -> channelId(sound)
        }
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_salah)
            .setColor(ContextCompat.getColor(context, R.color.crimson))
            .setContentTitle(title)
            .setContentText(body.substringBefore('\n'))
            // Kind Words and before-prayer lines run to several lines.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (silent) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), n)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }
}

/** Posts [n] as notification [id] if notifications are allowed. */
fun postNotification(context: Context, id: Int, n: android.app.Notification) {
    if (!Notifications.canPost(context)) return
    try {
        NotificationManagerCompat.from(context).notify(id, n)
    } catch (_: SecurityException) {
    }
}
