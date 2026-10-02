package com.zainkhalid.salah.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zainkhalid.salah.data.AccentColor
import com.zainkhalid.salah.widget.CalendarWidgetReceiver
import com.zainkhalid.salah.widget.NextPrayerWidgetReceiver
import com.zainkhalid.salah.widget.TodayWidgetReceiver
import salah.core.CalculationSettings
import salah.core.DisplaySettings
import salah.core.HighLatitudeSetting
import salah.core.MadhabSetting
import salah.core.MethodID
import salah.core.Prayer
import salah.core.ThemeSetting

@Composable
fun SettingsScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val accent by vm.accent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = palette
    fun calc(body: (CalculationSettings) -> CalculationSettings) = vm.update { it.copy(calculation = body(it.calculation)) }
    fun display(body: (DisplaySettings) -> DisplaySettings) = vm.update { it.copy(display = body(it.display)) }
    val calculation = config.calculation
    val d = config.display

    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Settings", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

        Panel(title = "LOCATION") {
            val loc = config.location
            SettingRow(
                loc?.name ?: "No location set",
                loc?.let { "${it.timeZone} · ${it.coordinateDescription}" } ?: "Tap to set your location",
                onClick = { vm.showLocationSheet = true },
            )
        }

        Panel(title = "APPEARANCE") {
            Text("Theme", color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Segmented(
                options = listOf(ThemeSetting.SYSTEM to "System", ThemeSetting.LIGHT to "Light", ThemeSetting.DARK to "Dark"),
                selected = d.theme,
                onSelect = { t -> display { it.copy(theme = t) } },
            )
            Text("Colour", color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
            ColorSwatches(
                options = AccentColor.available,
                selected = accent,
                appAccent = accent,
                onSelect = { if (it != null) vm.setAccent(it) },
            )
            Column(Modifier.padding(top = 8.dp)) {
                SwitchRow("24-hour clock", d.use24HourClock) { on -> display { it.copy(use24HourClock = on) } }
                SwitchRow("Show Jumu'ah on Fridays", d.jumuahRelabel) { on -> display { it.copy(jumuahRelabel = on) } }
                SettingRow("Hijri date adjustment", "For local moon sighting") {
                    Stepper(
                        value = if (d.hijriAdjustment > 0) "+${d.hijriAdjustment}" else "${d.hijriAdjustment}",
                        onMinus = { display { it.copy(hijriAdjustment = (it.hijriAdjustment - 1).coerceAtLeast(-2)) } },
                        onPlus = { display { it.copy(hijriAdjustment = (it.hijriAdjustment + 1).coerceAtMost(2)) } },
                    )
                }
                SettingRow("Show NOW for", "After a prayer starts") {
                    Stepper(
                        value = "${d.nowWindowMinutes} min",
                        onMinus = { display { it.copy(nowWindowMinutes = (it.nowWindowMinutes - 5).coerceAtLeast(0)) } },
                        onPlus = { display { it.copy(nowWindowMinutes = (it.nowWindowMinutes + 5).coerceAtMost(60)) } },
                    )
                }
            }
        }

        Panel(title = "WIDGETS") {
            SettingRow("Next prayer", "Big dot matrix time with a live countdown", onClick = { pinWidget(context, NextPrayerWidgetReceiver::class.java) })
            SettingRow("Today's prayers", "All six times, the next one highlighted", onClick = { pinWidget(context, TodayWidgetReceiver::class.java) })
            SettingRow("Islamic calendar", "This month in Hijri and Gregorian, Islamic days marked", onClick = { pinWidget(context, CalendarWidgetReceiver::class.java) })
            Text(
                "Each widget can follow the app's colours or use its own. Long press a widget and pick Edit to change it.",
                color = c.secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Panel(title = "CALCULATION") {
            DropdownRow(
                "Method",
                listOf<MethodID?>(null).map { it to "Automatic (${MethodID.automatic(config.location).displayName})" } +
                    MethodID.entries.map { it to it.displayName },
                calculation.method,
            ) { m -> calc { it.copy(method = m) } }
            if (calculation.resolvedMethod(config.location) == MethodID.CUSTOM) {
                SettingRow("Fajr angle") {
                    Stepper("${CalculationSettings.angle(calculation.customFajrAngle)}°",
                        { calc { it.copy(customFajrAngle = (it.customFajrAngle - 0.5).coerceAtLeast(10.0)) } },
                        { calc { it.copy(customFajrAngle = (it.customFajrAngle + 0.5).coerceAtMost(25.0)) } })
                }
                SettingRow("Isha angle") {
                    Stepper("${CalculationSettings.angle(calculation.customIshaAngle)}°",
                        { calc { it.copy(customIshaAngle = (it.customIshaAngle - 0.5).coerceAtLeast(10.0)) } },
                        { calc { it.copy(customIshaAngle = (it.customIshaAngle + 0.5).coerceAtMost(25.0)) } })
                }
            }
            DropdownRow(
                "Asr",
                MadhabSetting.entries.map { it to it.displayName },
                calculation.madhab,
            ) { m -> calc { it.copy(madhab = m) } }
            DropdownRow(
                "High latitude rule",
                listOf<HighLatitudeSetting?>(null).map { it to "Automatic" } + HighLatitudeSetting.entries.map { it to it.displayName },
                calculation.highLatitudeRule,
            ) { h -> calc { it.copy(highLatitudeRule = h) } }
        }

        Panel(title = "ADJUSTMENTS (MINUTES)") {
            for (p in Prayer.entries) {
                val off = calculation.offset(p)
                SettingRow(p.displayName) {
                    Stepper(
                        if (off > 0) "+$off" else "$off",
                        { calc { it.withOffset((off - 1).coerceAtLeast(-30), p) } },
                        { calc { it.withOffset((off + 1).coerceAtMost(30), p) } },
                    )
                }
            }
        }

        Panel(title = "ABOUT") {
            SettingRow("Salah for Android", "Version ${appVersion(context)} · times by a Kotlin port of adhan-swift")
            SettingRow("Original macOS app", "Prima Yudantra · github.com/primayudantra/salah", onClick = {
                open(context, "https://github.com/primayudantra/salah")
            })
            SettingRow("Source code", "github.com/zainkhalid91/salah-android", onClick = {
                open(context, "https://github.com/zainkhalid91/salah-android")
            })
            SettingRow("Pixel font", "Doto, SIL Open Font License 1.1")
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun pinWidget(context: Context, receiver: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
    }
}
