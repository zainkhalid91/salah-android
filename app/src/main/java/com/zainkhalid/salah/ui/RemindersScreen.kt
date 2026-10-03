package com.zainkhalid.salah.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zainkhalid.salah.reminders.Notifications
import com.zainkhalid.salah.reminders.ReminderScheduler
import salah.core.Prayer
import salah.core.PrayerReminder
import salah.core.QuietHours
import salah.core.ReminderSound
import salah.core.SalahConfig
import salah.core.TimeFormatting
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun RemindersScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = palette
    val r = config.reminders
    val now = Instant.now()

    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(tr("Reminders"), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

        if (!Notifications.canPost(context)) {
            Panel {
                SettingRow(tr("Notifications are off"), tr("Allow notifications for Salah to get reminders."), onClick = { openAppSettings(context) })
            }
        }
        if (!ReminderScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Panel {
                SettingRow(tr("Reminders may arrive late"), tr("Allow alarms and reminders so they fire on the minute."), onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                })
            }
        }

        Panel {
            SwitchRow(tr("Prayer reminders"), r.enabled) { on -> vm.update { it.copy(reminders = it.reminders.copy(enabled = on)) } }
            val paused = r.isPaused(now)
            val until = r.pausedUntil
            val zone = config.location?.zone
            SettingRow(
                tr(if (paused) "Paused" else "Pause reminders"),
                if (paused && until != null && zone != null) tr("Until {0}", TimeFormatting.clock(until, zone, config.display.use24HourClock, lang = config.display.lang)) else tr("Silence them for a while"),
            )
            Segmented(
                options = listOf(0L to tr("Off"), 1L to tr("1 hour"), 3L to tr("3 hours"), 24L to tr("Until tomorrow")),
                selected = if (paused) -1L else 0L,
                onSelect = { hours ->
                    vm.update {
                        it.copy(reminders = it.reminders.withPausedUntil(if (hours == 0L) null else Instant.now().plus(hours, ChronoUnit.HOURS)))
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Panel(title = tr("PRAYERS")) {
            for (p in Prayer.prayers) PrayerRow(vm, config, p)
        }

        Panel(title = tr("SOUND")) {
            Segmented(
                options = ReminderSound.entries.map { it to tr(it.displayName) },
                selected = r.sound,
                onSelect = { s -> vm.update { it.copy(reminders = it.reminders.copy(sound = s)) } },
                modifier = Modifier.padding(vertical = 12.dp),
            )
            SwitchRow(
                tr("Azan at prayer time"),
                r.azan && r.sound != ReminderSound.SILENT,
                tr("For the five prayers. Early reminders keep the sound above."),
                enabled = r.sound != ReminderSound.SILENT,
            ) { on -> vm.update { it.copy(reminders = it.reminders.copy(azan = on)) } }
        }

        Panel(title = tr("QUIET HOURS")) {
            SwitchRow(tr("Quiet hours"), r.quietHours.enabled, tr("No reminders in this window")) { on -> vm.update { it.quiet { q -> q.copy(enabled = on) } } }
            TimeRow(tr("Starts"), r.quietHours.start, r.quietHours.enabled) { v -> vm.update { it.quiet { q -> q.copy(start = v) } } }
            TimeRow(tr("Ends"), r.quietHours.end, r.quietHours.enabled) { v -> vm.update { it.quiet { q -> q.copy(end = v) } } }
        }
    }
}

private fun SalahConfig.quiet(body: (QuietHours) -> QuietHours) = copy(reminders = reminders.copy(quietHours = body(reminders.quietHours)))

@Composable
private fun PrayerRow(vm: SalahViewModel, config: SalahConfig, p: Prayer) {
    val reminder = config.reminders.reminder(p)
    fun set(body: (PrayerReminder) -> PrayerReminder) = vm.update { it.copy(reminders = it.reminders.update(p, body)) }
    val enabled = config.reminders.enabled
    Column(Modifier.padding(bottom = 10.dp)) {
        SwitchRow(tr(p.displayName), reminder.enabled, enabled = enabled) { on -> set { it.copy(enabled = on) } }
        if (reminder.enabled) {
            Segmented(
                options = PrayerReminder.ALLOWED_LEADS.map { it to if (it == 0) tr("No early") else tr("{0} min", it) },
                selected = reminder.leadMinutes,
                onSelect = { m -> set { it.copy(leadMinutes = m) } },
            )
            SwitchRow(tr("At prayer time"), reminder.atTime, enabled = enabled) { on -> set { it.copy(atTime = on) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(title: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, value, onClick = if (enabled) ({ open = true }) else null)
    if (open) {
        val minutes = QuietHours.minutes(value) ?: 0
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                    open = false
                }) { Text(tr("Set")) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Cancel")) } },
            title = { Text(title) },
            text = { TimeInput(state = state) },
        )
    }
}

fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    context.startActivity(intent)
}
