package com.zainkhalid.salah.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import salah.core.DaySchedule
import salah.core.Prayer
import salah.core.PrayerSchedule
import salah.core.ScheduleExporter
import salah.core.TimeFormatting
import salah.core.localDate
import salah.core.monthName
import java.time.Instant
import java.time.LocalDate

private enum class Span { WEEK, MONTH }

@Composable
fun ScheduleScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    val context = LocalContext.current
    val location = config.location?.takeIf { it.isValid }
    var span by rememberSaveable { mutableStateOf(Span.WEEK) }
    var offset by rememberSaveable { mutableStateOf(0) }

    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding().padding(horizontal = 14.dp)) {
        if (location == null) {
            Text("Set a location on the Today tab to see the schedule.", color = c.secondary, modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        val today = localDate(Instant.now(), location.zone)
        val days: List<DaySchedule> = when (span) {
            Span.WEEK -> PrayerSchedule.range(today.plusWeeks(offset.toLong()), 7, location, config.calculation)
            Span.MONTH -> PrayerSchedule.month(today.plusMonths(offset.toLong()), location, config.calculation)
        }
        val title = when (span) {
            Span.WEEK -> "${TimeFormatting.shortDate(days.first().date)} – ${TimeFormatting.shortDate(days.last().date)}"
            Span.MONTH -> days.first().date.let { "${it.monthName} ${it.year}" }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedule", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val text = ScheduleExporter.text(days, location, config.display)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share schedule"))
            }) { Icon(Icons.Filled.Share, contentDescription = "Share", tint = c.text) }
        }
        Segmented(
            options = listOf(Span.WEEK to "Week", Span.MONTH to "Month"),
            selected = span,
            onSelect = {
                span = it
                offset = 0
            },
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous", tint = c.text) }
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { offset++ }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next", tint = c.text) }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("", modifier = Modifier.width(DATE_W))
            for (p in Prayer.entries) {
                Text(p.displayName.take(3).uppercase(), color = c.secondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(days, key = { it.date.toEpochDay() }) { day -> DayRow(day, today, config.display.use24HourClock) }
        }
    }
}

private val DATE_W = 64.dp

@Composable
private fun DayRow(day: DaySchedule, today: LocalDate, use24: Boolean) {
    val c = palette
    val isToday = day.date == today
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) c.timeline else c.display)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (isToday) c.onTimeline else c.text
        val dim = if (isToday) c.onTimelineDim else c.secondary
        Column(Modifier.width(DATE_W)) {
            Text(TimeFormatting.shortDate(day.date).substringBefore(","), color = if (day.isFriday) (if (isToday) fg else c.accent) else dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(day.date.dayOfMonth.toString(), color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        for (p in Prayer.entries) {
            val t = day.time(p)
            val text = t?.let { TimeFormatting.parts(it, day.zone, use24).first } ?: "--"
            Text(
                text,
                style = pixelStyle(13f),
                color = if (p == Prayer.SUNRISE) dim else fg,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

