package com.zainkhalid.salah.ui

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zainkhalid.salah.focus.FocusCardActivity
import com.zainkhalid.salah.focus.FocusModeRunner
import com.zainkhalid.salah.reminders.Notifications
import com.zainkhalid.salah.repo
import salah.core.Prayer
import salah.core.QuietHours
import salah.core.focusmode.CallBusyMode
import salah.core.focusmode.FocusModeCardSettings
import salah.core.focusmode.FocusModeSettings
import salah.core.kindwords.DuaMoment
import salah.core.kindwords.KindWordCategory
import salah.core.kindwords.KindWordsCadence
import salah.core.kindwords.KindWordsLibrary
import salah.core.kindwords.KindWordsSettings
import java.time.Instant
import java.time.LocalDate

/** Title row for screens under More, with a back arrow. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
    val c = palette
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"), tint = c.text) }
        }
        Text(title, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SubScreen(vm: SalahViewModel, modifier: Modifier, title: String, content: @Composable () -> Unit) {
    val c = palette
    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(title) { vm.more = null }
        content()
    }
}

@Composable
fun MoreScreen(vm: SalahViewModel, modifier: Modifier) {
    val c = palette
    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(tr("More"), null)
        Panel {
            for (d in MoreDest.entries) {
                SettingRow(tr(d.title), tr(d.subtitle), onClick = { vm.more = d }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.secondary)
                }
            }
        }
    }
}

@Composable
fun FocusModeScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = palette
    val f = config.focusMode
    fun set(body: (FocusModeSettings) -> FocusModeSettings) = vm.update { it.copy(focusMode = body(it.focusMode)) }

    SubScreen(vm, modifier, tr("Focus Mode")) {
        Text(
            tr("At prayer time, Salah can show a calm full-screen card, pause what's playing and turn on Do Not Disturb. On a call it only sends a small nudge."),
            color = c.secondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Panel {
            SwitchRow(tr("Focus Mode"), f.enabled) { on -> set { it.copy(enabled = on) } }
            Text(tr("Prayers"), color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            // Multi-select: each pill toggles its prayer.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                for (p in Prayer.prayers) {
                    val on = p in f.prayers
                    Segmented(listOf(on to tr(p.displayName)), selected = true, onSelect = { set { s -> s.withPrayer(p, !on) } })
                }
            }
        }
        Panel(title = tr("CARD")) {
            SwitchRow(tr("Full-screen card"), f.card.enabled, tr("Shows over the lock screen")) { on -> set { it.copy(card = it.card.copy(enabled = on)) } }
            Text(tr("Closes by itself after"), color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            Segmented(
                FocusModeCardSettings.ALLOWED_AUTO_CLOSE_MINUTES.map { it to tr("{0} min", it) },
                f.card.autoCloseMinutes,
                { m -> set { it.copy(card = it.card.copy(autoCloseMinutes = m)) } },
                Modifier.padding(bottom = 8.dp),
            )
            if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
                SettingRow(tr("Allow full-screen cards"), tr("Android needs your permission to show the card over the lock screen"), onClick = {
                    openSettings(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                })
            }
            TextButton(onClick = {
                context.startActivity(FocusCardActivity.intent(context, Prayer.DHUHR, Instant.now(), null))
            }) { Text(tr("Preview the card"), color = c.accent) }
        }
        Panel(title = tr("AUDIO")) {
            SwitchRow(tr("Pause what's playing"), f.pauseMedia.enabled, tr("The Quran player and other apps")) { on -> set { it.copy(pauseMedia = it.pauseMedia.copy(enabled = on)) } }
            SwitchRow(tr("Resume when I'm done"), f.pauseMedia.resumeOnDone, enabled = f.pauseMedia.enabled) { on -> set { it.copy(pauseMedia = it.pauseMedia.copy(resumeOnDone = on)) } }
        }
        Panel(title = tr("DO NOT DISTURB")) {
            val granted = FocusModeRunner.canControlDnd(context)
            SwitchRow(tr("Turn on Do Not Disturb"), f.focus.enabled, if (granted) tr("Priority only, until you're done") else tr("Needs Do Not Disturb access")) { on ->
                set { it.copy(focus = it.focus.copy(enabled = on)) }
                if (on && !granted) openSettings(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            SwitchRow(tr("Turn it off when I'm done"), f.focus.turnOffOnDone, enabled = f.focus.enabled) { on -> set { it.copy(focus = it.focus.copy(turnOffOnDone = on)) } }
            if (!granted) {
                SettingRow(tr("Allow Do Not Disturb access"), tr("Opens Android settings"), onClick = {
                    openSettings(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                })
            }
        }
        Panel(title = tr("WHEN I'M BUSY")) {
            Text(tr("On a call"), color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Segmented(
                CallBusyMode.entries.map { it to tr(it.displayName) },
                f.whenBusy.onCall,
                { m -> set { it.copy(whenBusy = it.whenBusy.copy(onCall = m)) } },
                Modifier.padding(bottom = 8.dp),
            )
            SwitchRow(tr("Do Not Disturb counts as busy"), f.whenBusy.focusIsBusy, tr("When you turned it on yourself")) { on ->
                set { it.copy(whenBusy = it.whenBusy.copy(focusIsBusy = on)) }
            }
        }
    }
}

@Composable
fun KindWordsScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = palette
    val k = config.kindWords
    fun set(body: (KindWordsSettings) -> KindWordsSettings) = vm.update { it.copy(kindWords = body(it.kindWords)) }

    SubScreen(vm, modifier, tr("Kind Words")) {
        Text(
            tr("Gentle verses, hadith and du'as through the day, never close to a prayer time. They follow your quiet hours and pause."),
            color = c.secondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Panel {
            SwitchRow(tr("Kind Words"), k.enabled) { on -> set { it.copy(enabled = on) } }
            Text(tr("Daytime words"), color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Segmented(KindWordsCadence.entries.map { it to tr(it.displayName) }, k.cadence, { v -> set { it.copy(cadence = v) } }, Modifier.padding(bottom = 8.dp))
            SwitchRow(tr("Before prayer"), k.beforePrayer, tr("A verse or hadith with the early reminder")) { on -> set { it.copy(beforePrayer = on) } }
            SwitchRow(tr("After prayer"), k.afterPrayer, tr("\"Have you prayed?\" after each prayer")) { on -> set { it.copy(afterPrayer = on) } }
            if (k.afterPrayer) {
                Segmented(
                    KindWordsSettings.ALLOWED_AFTER_PRAYER_MINUTES.map { it to tr("{0} min", it) },
                    k.afterPrayerMinutes,
                    { m -> set { it.copy(afterPrayerMinutes = m) } },
                    Modifier.padding(bottom = 8.dp),
                )
            }
            SwitchRow(tr("Friday"), k.friday, tr("Al-Kahf in the morning, the last hour after Asr")) { on -> set { it.copy(friday = on) } }
            SwitchRow(tr("Work du'as"), k.workDuas, tr("At the start and end of the work day, Monday to Friday")) { on -> set { it.copy(workDuas = on) } }
            if (k.workDuas) {
                SettingRow(tr("Work starts")) { HalfHourStepper(k.workStart) { v -> set { it.copy(workStart = v) } } }
                SettingRow(tr("Work ends")) { HalfHourStepper(k.workEnd) { v -> set { it.copy(workEnd = v) } } }
            }
            SwitchRow(tr("Silent"), k.silent, tr("No sound, whatever your reminder sound is")) { on -> set { it.copy(silent = on) } }
            TextButton(onClick = {
                val w = KindWordsLibrary.BUNDLED.kindWordOfTheDay(LocalDate.now()) ?: return@TextButton
                Notifications.show(context, "salah.preview.kind", tr(context, "A kind word"), w.notificationBody, config.reminders.sound, silent = k.silent)
            }) { Text(tr("Send a preview"), color = c.accent) }
        }
        for (cat in KindWordCategory.entries) {
            val words = KindWordsLibrary.BUNDLED.words(cat)
            if (words.isEmpty()) continue
            Panel(title = tr(cat.displayName).uppercase()) {
                for (w in words) {
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Text(w.text, color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
                        Text("— ${w.source}", color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Text(tr("Kind Words and du'as are in English."), color = c.secondary, fontSize = 12.sp)
    }
}

/** "−  09:00  +" in 30 minute steps. */
@Composable
private fun HalfHourStepper(value: String, onChange: (String) -> Unit) {
    val m = QuietHours.minutes(value) ?: 9 * 60
    fun fmt(x: Int) = "%02d:%02d".format(java.util.Locale.ROOT, x / 60, x % 60)
    Stepper(value, onMinus = { onChange(fmt((m - 30 + 1440) % 1440)) }, onPlus = { onChange(fmt((m + 30) % 1440)) })
}

@Composable
fun DuaScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = palette
    SubScreen(vm, modifier, tr("Du'a")) {
        for (moment in DuaMoment.entries) {
            val duas = KindWordsLibrary.BUNDLED.duas(moment)
            if (duas.isEmpty()) continue
            Panel(title = tr(moment.displayName).uppercase()) {
                for (d in duas) {
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Text(d.label, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(d.english, color = c.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        if (d.transliteration.isNotBlank()) Text(d.transliteration, color = c.secondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(d.source, color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Row {
                            TextButton(onClick = {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm.setPrimaryClip(ClipData.newPlainText(d.label, "${d.english}\n${d.transliteration}\n— ${d.source}".replace("\n\n", "\n")))
                                if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, tr(context, "Copied"), Toast.LENGTH_SHORT).show()
                            }) { Text(tr("Copy"), color = c.accent) }
                            TextButton(onClick = {
                                Notifications.show(context, "salah.dua.${d.id}", d.label, d.notificationBody, config.reminders.sound, silent = true)
                            }) { Text(tr("Notify me"), color = c.accent) }
                        }
                    }
                }
            }
        }
    }
}

/** Translation outside composition, in the app's current language. */
fun tr(context: Context, en: String, vararg args: Any): String =
    salah.core.AppText.t(context.repo.config.value.display.lang, en, *args)

private fun openSettings(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
