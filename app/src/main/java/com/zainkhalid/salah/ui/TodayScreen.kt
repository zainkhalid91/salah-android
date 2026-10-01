package com.zainkhalid.salah.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import salah.core.DaySchedule
import salah.core.HijriDate
import salah.core.MadhabSetting
import salah.core.Prayer
import salah.core.PrayerClock
import salah.core.PrayerClockState
import salah.core.PrayerSchedule
import salah.core.SalahConfig
import salah.core.TimeFormatting
import salah.core.localDate
import salah.core.monthName
import salah.core.weekdayName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Ticks once a second, on the second. */
@Composable
fun rememberNow(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return now
}

@Composable
fun TodayScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val now = rememberNow()
    val location = config.location?.takeIf { it.isValid }
    val state = location?.let { PrayerClock.state(now, it, config.calculation, config.display.nowWindowMinutes) }

    Column(
        modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DisplayPanel(vm, config, state, now)
        if (location != null) Timeline(vm, config, state, now)
    }
}

// Display

@Composable
private fun DisplayPanel(vm: SalahViewModel, config: SalahConfig, state: PrayerClockState?, now: Instant) {
    val c = palette
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.display)
            .border(1.dp, c.line, RoundedCornerShape(22.dp)),
    ) {
        DottedBackground(Modifier.matchParentSize())
        val width = maxWidth - 48.dp
        val nameSize = (maxWidth.value * 0.17f).coerceIn(40f, 78f)
        val timeSize = (maxWidth.value * 0.25f).coerceIn(56f, 110f)
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            val preview = vm.previewDate
            val detail = vm.detailPrayer
            val d = config.display
            when {
                config.location == null -> SetLocation(vm, nameSize, width)
                state == null -> {
                    Eyebrow("LOCATION")
                    Text("The saved location is invalid.", color = c.text, modifier = Modifier.padding(top = 16.dp))
                    OutlinedButton(onClick = { vm.showLocationSheet = true }, modifier = Modifier.padding(top = 10.dp)) { Text("Change location") }
                }
                preview != null && preview != state.today.date -> Preview(vm, config, preview, nameSize, timeSize, width)
                detail != null -> Detail(vm, config, detail, state.today, nameSize, timeSize, width)
                state.nowPrayer != null && state.today.time(state.nowPrayer!!) != null -> {
                    val p = state.nowPrayer!!
                    Status("NOW")
                    PrayerName(state.today.label(p, d.jumuahRelabel), nameSize, c.accent, width)
                    PrayerTime(config, state.today.time(p)!!, timeSize)
                    Rule()
                    Countdown("STARTED", state.secondsSinceNow ?: 0.0)
                }
                state.next != null -> {
                    val n = state.next!!
                    Status("NEXT PRAYER", if (n.isTomorrow) "TOMORROW" else null)
                    PrayerName(n.label(d.jumuahRelabel), nameSize, c.text, width)
                    PrayerTime(config, n.time, timeSize)
                    Rule()
                    Countdown("IN", n.secondsRemaining(now))
                    state.today.undefinedExplanation?.let {
                        Text(it.first, color = c.secondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                else -> {
                    Status("NEXT PRAYER")
                    Text("--", style = pixelStyle(nameSize), color = c.text)
                    state.today.undefinedExplanation?.let {
                        Text(it.first, color = c.secondary, fontSize = 12.5.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (config.location != null) {
                Spacer(Modifier.height(20.dp))
                Footer(vm, config, now)
            }
        }
    }
}

@Composable
private fun Status(text: String, tag: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(text, Modifier.weight(1f))
        if (tag != null) Text(tag, color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

/** Big pixel name; shrinks to fit and cross-fades when it changes. */
@Composable
private fun PrayerName(name: String, size: Float, color: Color, width: Dp) {
    val fitted = fittedSize(name.uppercase(), size, width)
    AnimatedContent(
        targetState = name,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
        modifier = Modifier.padding(top = 18.dp),
        label = "prayer-name",
    ) { n ->
        PixelText(n.uppercase(), fitted, color = color)
    }
}

@Composable
private fun fittedSize(text: String, size: Float, width: Dp): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, size, width) {
        val maxPx = with(density) { width.toPx() }
        var s = size
        while (s > size * 0.45f) {
            if (measurer.measure(text, pixelStyle(s), softWrap = false).size.width <= maxPx) break
            s -= 2f
        }
        s
    }
}

@Composable
private fun PrayerTime(config: SalahConfig, t: Instant, size: Float) {
    val zone = config.location?.zone ?: return
    val (time, period) = TimeFormatting.parts(t, zone, config.display.use24HourClock, padHour = true)
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelText(time, size)
        if (period.isNotEmpty()) Text(period, color = palette.secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
    }
}

@Composable
private fun Rule() {
    Box(Modifier.padding(top = 18.dp, bottom = 14.dp).size(width = 72.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(palette.text))
}

@Composable
private fun Countdown(label: String, seconds: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = palette.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        PixelText(TimeFormatting.countdown(seconds), 26f)
    }
}

@Composable
private fun Footer(vm: SalahViewModel, config: SalahConfig, now: Instant) {
    val c = palette
    val loc = config.location ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).clickable { vm.showLocationSheet = true }) {
            Text(loc.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(loc.timeZone, color = c.secondary, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            val use24 = config.display.use24HourClock
            val (t, period) = TimeFormatting.parts(now, loc.zone, use24)
            val secs = now.atZone(loc.zone).second
            PixelText(if (use24) t + String.format(Locale.ROOT, ":%02d", secs) else "$t $period", 16f)
            if (vm.detailPrayer != null || vm.previewDate != null) {
                Text(
                    "Back to now",
                    color = c.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp).clickable {
                        vm.detailPrayer = null
                        vm.previewDate = null
                    },
                )
            }
        }
    }
}

@Composable
private fun Preview(vm: SalahViewModel, config: SalahConfig, d: LocalDate, nameSize: Float, timeSize: Float, width: Dp) {
    Status("PREVIEW")
    PrayerName(d.weekdayName.take(3), nameSize, palette.text, width)
    PixelText(String.format(Locale.ROOT, "%02d %s", d.dayOfMonth, d.monthName.take(3).uppercase()), timeSize * 0.62f, Modifier.padding(top = 4.dp))
    Rule()
    Text("${d.year} · ${HijriDate.of(d, config.display.hijriAdjustment).formatted}", color = palette.secondary)
}

@Composable
private fun Detail(vm: SalahViewModel, config: SalahConfig, p: Prayer, schedule: DaySchedule, nameSize: Float, timeSize: Float, width: Dp) {
    val c = palette
    val reminder = config.reminders.reminder(p)
    val offset = config.calculation.offset(p)
    Status(if (p == Prayer.SUNRISE) "SUNRISE" else "PRAYER DETAIL")
    PrayerName(schedule.label(p, config.display.jumuahRelabel), nameSize, c.text, width)
    schedule.time(p)?.let { PrayerTime(config, it, timeSize) }
    Rule()
    val reminderText = when {
        p == Prayer.SUNRISE -> "End of Fajr time, not a prayer"
        !config.reminders.enabled -> "Off (all reminders)"
        !reminder.enabled || reminder.leads.isEmpty() -> "Off"
        else -> buildList {
            if (reminder.leadMinutes > 0) add("${reminder.leadMinutes} min before")
            if (reminder.atTime) add("at prayer time")
        }.joinToString(" and ").replaceFirstChar { it.uppercase() }
    }
    val rows = listOf(
        (if (p == Prayer.SUNRISE) "Note" else "Reminder") to reminderText,
        "Method" to config.methodName,
        "Offset" to if (offset == 0) "None" else "${if (offset > 0) "+" else ""}$offset min",
    )
    for ((k, v) in rows) {
        Row(Modifier.padding(vertical = 3.dp)) {
            Text(k, color = c.secondary, fontSize = 13.sp, modifier = Modifier.width(80.dp))
            Text(v, color = c.text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SetLocation(vm: SalahViewModel, nameSize: Float, width: Dp) {
    val c = palette
    Eyebrow("WELCOME")
    PixelText("SET LOCATION", fittedSize("SET LOCATION", nameSize * 0.8f, width), Modifier.padding(top = 18.dp))
    Text("Prayer times are calculated for where you are.", color = c.secondary, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { vm.showLocationSheet = true }) {
            if (vm.locating) CircularProgressIndicator(Modifier.size(14.dp), color = c.onAccent, strokeWidth = 2.dp)
            else Text("Set location")
        }
    }
}

// Timeline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Timeline(vm: SalahViewModel, config: SalahConfig, state: PrayerClockState?, now: Instant) {
    val c = palette
    val location = config.location ?: return
    val today = localDate(now, location.zone)
    val date = vm.previewDate ?: today
    val isPreview = date != today
    val schedule = if (isPreview) PrayerSchedule.forDate(date, location, config.calculation) else state?.today
    val hijri = HijriDate.of(date, config.display.hijriAdjustment)
    var picking by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.timeline)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { picking = true }) {
            Text(TimeFormatting.longDate(date, includeYear = false).uppercase(), color = c.onTimeline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hijri.formatted.uppercase(), color = c.onTimelineDim, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(16.dp))
        Prayer.entries.forEachIndexed { i, p ->
            TimelineRow(vm, config, p, schedule, if (isPreview) null else state, now, i == 0, i == Prayer.entries.lastIndex)
        }
        val method = config.methodName + if (config.calculation.madhab == MadhabSetting.HANAFI) " · Hanafi Asr" else ""
        Text(method, color = c.onTimelineDim, fontSize = 11.5.sp, modifier = Modifier.padding(top = 14.dp))
    }

    if (picking) {
        val millis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = LocalDate.ofEpochDay(Math.floorDiv(ms, 86_400_000L))
                        vm.previewDate = if (picked == today) null else picked
                        vm.detailPrayer = null
                    }
                    picking = false
                }) { Text("Show") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.previewDate = null
                    picking = false
                }) { Text("Today") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun TimelineRow(
    vm: SalahViewModel, config: SalahConfig, prayer: Prayer, schedule: DaySchedule?, state: PrayerClockState?,
    now: Instant, isFirst: Boolean, isLast: Boolean,
) {
    val c = palette
    val time = schedule?.time(prayer)
    val isNext = state?.next?.let { !it.isTomorrow && it.prayer == prayer } ?: false
    val isCurrent = state?.let { it.nowPrayer == prayer || (it.nowPrayer == null && it.current == prayer) } ?: false
    val isPast = state != null && (time?.let { it <= now } ?: false) && !isCurrent
    val isSunrise = prayer == Prayer.SUNRISE
    val label = schedule?.label(prayer, config.display.jumuahRelabel) ?: prayer.displayName
    val fg = if (isNext) c.text else if (isSunrise) c.onTimelineDim else c.onTimeline
    val lineColor = c.onTimelineDim.copy(alpha = 0.55f)

    Box(
        Modifier.fillMaxWidth().drawBehind {
            // Line through the dots, joining the rows.
            val x = 16.dp.toPx()
            if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, size.height / 2), 1.dp.toPx())
            if (!isLast) drawLine(lineColor, Offset(x, size.height / 2), Offset(x, size.height), 1.dp.toPx())
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isNext) c.highlight else Color.Transparent)
                .clickable(enabled = schedule != null) {
                    vm.detailPrayer = prayer
                    vm.previewDate = null
                }
                .padding(horizontal = 10.dp, vertical = 11.dp)
                .alpha(if (isPast && !isNext) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
                if (isSunrise) {
                    Box(Modifier.padding(start = 1.dp).size(9.dp).background(c.timeline, CircleShape).border(1.5.dp, c.onTimelineDim, CircleShape))
                } else {
                    val ring = if (isNext) c.highlight else c.timeline
                    Box(Modifier.size(11.dp).drawBehind {
                        drawCircle(ring, radius = size.minDimension / 2 + 1.5.dp.toPx())
                        drawCircle(if (isNext) c.accent else c.onTimeline, radius = size.minDimension / 2)
                    })
                }
            }
            Text(label, color = fg, fontSize = if (isSunrise) 13.sp else 15.sp, fontWeight = FontWeight.Medium)
            if (isCurrent && !isNext) {
                Box(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("now", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.weight(1f))
            val zone = schedule?.zone
            if (time != null && zone != null) {
                val (t, period) = TimeFormatting.parts(time, zone, config.display.use24HourClock, padHour = true)
                PixelText(t, if (isSunrise) 15f else 19f, color = fg)
                if (period.isNotEmpty()) Text(" $period", color = fg.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            } else {
                PixelText("--:--", 19f, color = fg)
            }
        }
    }
}
