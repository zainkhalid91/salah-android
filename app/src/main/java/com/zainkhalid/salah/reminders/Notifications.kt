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
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, id: String, title: String, body: String, sound: ReminderSound, azan: Boolean = false) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, if (azan) AZAN_CHANNEL else channelId(sound))
            .setSmallIcon(R.drawable.ic_stat_salah)
            .setColor(ContextCompat.getColor(context, R.color.crimson))
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
