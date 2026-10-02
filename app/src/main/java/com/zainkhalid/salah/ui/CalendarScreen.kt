package com.zainkhalid.salah.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import salah.core.CalKey
import salah.core.CalLang
import salah.core.CalendarDay
import salah.core.CalendarText
import salah.core.IslamicAlertTime
import salah.core.IslamicCalendar
import salah.core.IslamicEvent
import salah.core.MonthGrid
import salah.core.PrimaryCalendar
import salah.core.SalahConfig
import salah.core.localDate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Locale of the phone, so the calendar speaks the user's language. */
@Composable
fun calendarLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** Pixel font for Latin digits; Arabic script falls back to a bold system face. */
fun calendarNumberStyle(lang: CalLang, size: Float): TextStyle =
    if (lang.rtl) TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold) else pixelStyle(size)

@Composable
fun CalendarScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    val locale = calendarLocale()
    val lang = CalendarText.lang(locale)
    val adj = config.display.hijriAdjustment
    val zone = config.location?.zone ?: ZoneId.systemDefault()
    val today = localDate(Instant.now(), zone)
    val todayHijri = IslamicCalendar.toHijri(today, adj)
    val primary = config.calendar.primary
    val firstDay = CalendarText.firstDayOfWeek(locale)

    // Months away from the current one; the grid is rebuilt from this.
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)

    val grid: MonthGrid? = when (primary) {
        PrimaryCalendar.HIJRI -> todayHijri?.let {
            val (y, m) = IslamicCalendar.shiftHijriMonth(it.year, it.month, offset)
            IslamicCalendar.hijriMonth(y, m, adj, firstDay)
        }
        PrimaryCalendar.GREGORIAN -> IslamicCalendar.gregorianMonth(YearMonth.from(today).plusMonths(offset.toLong()), adj, firstDay)
    }

    fun jumpTo(date: LocalDate) {
        selectedEpoch = date.toEpochDay()
        offset = when (primary) {
            PrimaryCalendar.GREGORIAN -> ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(date)).toInt()
            PrimaryCalendar.HIJRI -> {
                val h = IslamicCalendar.toHijri(date, adj)
                if (h == null || todayHijri == null) 0 else (h.year * 12 + h.month) - (todayHijri.year * 12 + todayHijri.month)
            }
        }
    }

    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(CalendarText.text(CalKey.CALENDAR, lang), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Segmented(
                options = listOf(
                    PrimaryCalendar.HIJRI to CalendarText.text(CalKey.HIJRI, lang),
                    PrimaryCalendar.GREGORIAN to CalendarText.text(CalKey.GREGORIAN, lang),
                ),
                selected = primary,
                onSelect = { p ->
                    vm.update { it.copy(calendar = it.calendar.copy(primary = p)) }
                    offset = 0
                },
            )
        }

        if (grid == null) {
            Text(CalendarText.text(CalKey.OUT_OF_RANGE, lang), color = c.secondary)
        } else {
            MonthCard(
                grid = grid, lang = lang, locale = locale, today = today, selected = selected,
                onSelect = { selectedEpoch = it.toEpochDay() },
                onShift = { offset += it },
                onToday = { jumpTo(today) },
            )
        }

        SelectedDay(selected, adj, today, lang, locale)
        Converter(adj, lang, locale, onShow = { jumpTo(it) })
        YearEvents(adj, today, lang, locale, onShow = { jumpTo(it) })
        Alerts(vm, config, lang)
    }
}

// Month grid

@Composable
private fun MonthCard(
    grid: MonthGrid, lang: CalLang, locale: Locale, today: LocalDate, selected: LocalDate,
    onSelect: (LocalDate) -> Unit, onShift: (Int) -> Unit, onToday: () -> Unit,
) {
    val c = palette
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val days = grid.inMonth
    val title: String
    val subtitle: String
    if (grid.primary == PrimaryCalendar.HIJRI) {
        title = CalendarText.hijriMonth(grid.month, lang)
        val g1 = days.first().date
        val g2 = days.last().date
        subtitle = "${CalendarText.number(grid.year, lang)} · " + gregorianSpan(g1, g2, lang, locale)
    } else {
        title = CalendarText.gregorianMonth(grid.month, locale)
        val h1 = days.first().hijri
        val h2 = days.last().hijri
        subtitle = "${CalendarText.number(grid.year, lang)} · " + if (h1.month == h2.month) {
            CalendarText.hijriMonthYear(h1.year, h1.month, lang)
        } else {
            "${CalendarText.hijriMonth(h1.month, lang)} – ${CalendarText.hijriMonthYear(h2.year, h2.month, lang)}"
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.display)
            .border(1.dp, c.line, RoundedCornerShape(24.dp)),
    ) {
        DottedBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(targetState = title, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }, label = "month-title") { t ->
                        Text(
                            if (lang.rtl) t else t.uppercase(locale),
                            style = if (lang.rtl) TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold) else pixelStyle(30f),
                            color = c.text,
                            maxLines = 1,
                        )
                    }
                    Text(subtitle, color = c.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
                // Arrows follow reading direction: "back" points to the start of the line.
                IconButton(onClick = { onShift(-1) }) {
                    Icon(if (rtl) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = c.text)
                }
                IconButton(onClick = { onShift(1) }) {
                    Icon(if (rtl) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = c.text)
                }
            }
            Text(
                CalendarText.text(CalKey.TODAY, lang),
                color = c.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onToday).padding(2.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                for (cell in grid.weeks.first()) {
                    Text(
                        CalendarText.weekdayShort(cell.date.dayOfWeek, locale),
                        color = if (cell.date.dayOfWeek == java.time.DayOfWeek.FRIDAY) c.accent else c.secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AnimatedContent(
                targetState = grid,
                transitionSpec = {
                    val forward = targetState.year * 12 + targetState.month > initialState.year * 12 + initialState.month
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(200)))
                },
                label = "month-grid",
                modifier = Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -80f) onShift(if (rtl) -1 else 1)
                            if (total > 80f) onShift(if (rtl) 1 else -1)
                        },
                    ) { change, dx ->
                        change.consume()
                        total += dx
                    }
                },
            ) { g ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (week in g.weeks) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (cell in week) {
                                DayCell(cell, g.primary, lang, cell.date == today, cell.date == selected, Modifier.weight(1f)) { onSelect(cell.date) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun gregorianSpan(a: LocalDate, b: LocalDate, lang: CalLang, locale: Locale): String {
    val m1 = CalendarText.gregorianMonth(a.monthValue, locale)
    val m2 = CalendarText.gregorianMonth(b.monthValue, locale)
    return if (a.year == b.year) "$m1 – $m2 ${CalendarText.number(b.year, lang)}"
    else "$m1 ${CalendarText.number(a.year, lang)} – $m2 ${CalendarText.number(b.year, lang)}"
}

@Composable
private fun DayCell(
    cell: CalendarDay, primary: PrimaryCalendar, lang: CalLang, isToday: Boolean, isSelected: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val c = palette
    val main = if (primary == PrimaryCalendar.HIJRI) cell.hijri.day else cell.date.dayOfMonth
    val second = if (primary == PrimaryCalendar.HIJRI) cell.date.dayOfMonth else cell.hijri.day
    val major = cell.events.any { it.major }
    val bg = when {
        isToday -> c.accent
        major -> c.accent.copy(alpha = 0.16f)
        cell.events.isNotEmpty() || cell.whiteDay -> c.text.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val fg = when {
        isToday -> c.onAccent
        cell.date.dayOfWeek == java.time.DayOfWeek.FRIDAY -> c.accent
        else -> c.text
    }
    Box(
        modifier
            .aspectRatio(0.86f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (isSelected && !isToday) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .alpha(if (cell.inMonth) 1f else 0.32f),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(CalendarText.number(main, lang), style = calendarNumberStyle(lang, 17f), color = fg, maxLines = 1)
            Text(
                CalendarText.number(second, lang),
                color = if (isToday) c.onAccent.copy(alpha = 0.8f) else c.secondary,
                fontSize = 9.5.sp,
                maxLines = 1,
            )
        }
        if (cell.events.isNotEmpty() || cell.whiteDay) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isToday -> c.onAccent
                            major -> c.accent
                            else -> c.secondary
                        },
                    ),
            )
        }
    }
}

// Selected day

@Composable
private fun SelectedDay(date: LocalDate, adj: Int, today: LocalDate, lang: CalLang, locale: Locale) {
    val c = palette
    val day = IslamicCalendar.day(date, adj) ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.timeline)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        val rel = ChronoUnit.DAYS.between(today, date)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                CalendarText.weekdayLong(date.dayOfWeek, locale).uppercase(locale),
                color = c.onTimelineDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            if (rel != 0L) Text(CalendarText.relative(rel, lang), color = c.onTimelineDim, fontSize = 12.sp)
        }
        Text(CalendarText.hijri(day.hijri, lang), color = c.onTimeline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(CalendarText.gregorian(date, locale), color = c.onTimelineDim, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        val names = day.events.map { CalendarText.event(it, lang) } + if (day.whiteDay) listOf(CalendarText.text(CalKey.WHITE_DAYS, lang)) else emptyList()
        if (names.isEmpty()) {
            Text(CalendarText.text(CalKey.NO_EVENTS, lang), color = c.onTimelineDim, fontSize = 13.sp)
        } else {
            for (n in names) {
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.onTimeline))
                    Text(n, color = c.onTimeline, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

// Converter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Converter(adj: Int, lang: CalLang, locale: Locale, onShow: (LocalDate) -> Unit) {
    val c = palette
    var toHijri by rememberSaveable { mutableStateOf(true) }
    var gEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val start = remember { IslamicCalendar.toHijri(LocalDate.now(), adj) }
    var hDay by rememberSaveable { mutableIntStateOf(start?.day ?: 1) }
    var hMonth by rememberSaveable { mutableIntStateOf(start?.month ?: 1) }
    var hYear by rememberSaveable { mutableIntStateOf(start?.year ?: 1447) }
    var picking by remember { mutableStateOf(false) }

    Panel(title = CalendarText.text(CalKey.CONVERTER, lang).uppercase(locale)) {
        Segmented(
            options = listOf(true to CalendarText.text(CalKey.GREGORIAN_TO_HIJRI, lang), false to CalendarText.text(CalKey.HIJRI_TO_GREGORIAN, lang)),
            selected = toHijri,
            onSelect = { toHijri = it },
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        val result: LocalDate?
        if (toHijri) {
            val g = LocalDate.ofEpochDay(gEpoch)
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(CalendarText.gregorian(g, locale))
            }
            result = g
            val h = IslamicCalendar.toHijri(g, adj)
            ResultLine(h?.let { CalendarText.hijri(it, lang) } ?: CalendarText.text(CalKey.OUT_OF_RANGE, lang), lang)
        } else {
            val maxDay = IslamicCalendar.monthLength(hYear, hMonth) ?: 30
            if (hDay > maxDay) hDay = maxDay
            SettingRow(CalendarText.text(CalKey.DAY, lang)) {
                Stepper(CalendarText.number(hDay, lang), { hDay = if (hDay <= 1) maxDay else hDay - 1 }, { hDay = if (hDay >= maxDay) 1 else hDay + 1 })
            }
            DropdownRow(
                CalendarText.text(CalKey.MONTH, lang),
                (1..12).map { it to CalendarText.hijriMonth(it, lang) },
                hMonth,
            ) { hMonth = it }
            SettingRow(CalendarText.text(CalKey.YEAR, lang)) {
                Stepper(
                    CalendarText.number(hYear, lang),
                    { hYear = (hYear - 1).coerceAtLeast(IslamicCalendar.MIN_HIJRI_YEAR) },
                    { hYear = (hYear + 1).coerceAtMost(IslamicCalendar.MAX_HIJRI_YEAR - 1) },
                )
            }
            result = IslamicCalendar.toGregorian(hYear, hMonth, hDay, adj)
            ResultLine(result?.let { CalendarText.gregorian(it, locale) } ?: CalendarText.text(CalKey.OUT_OF_RANGE, lang), lang)
        }
        if (result != null) {
            Text(
                CalendarText.text(CalKey.CALENDAR, lang) + if (lang.rtl) " ←" else " →",
                color = c.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp).clickable { onShow(result) },
            )
        }
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(gEpoch).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { gEpoch = Math.floorDiv(it, 86_400_000L) }
                    picking = false
                }) { Text(CalendarText.text(CalKey.CONVERT, lang)) }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ResultLine(text: String, lang: CalLang) {
    Text(
        text,
        color = palette.text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

// Year events

@Composable
private fun YearEvents(adj: Int, today: LocalDate, lang: CalLang, locale: Locale, onShow: (LocalDate) -> Unit) {
    val c = palette
    val year = IslamicCalendar.hijriYear(today, adj) ?: return
    val events = remember(year, adj) { IslamicCalendar.yearEvents(year, adj) }
    Panel(title = "${CalendarText.text(CalKey.EVENTS_THIS_YEAR, lang).uppercase(locale)} · ${CalendarText.number(year, lang)}") {
        val next = events.firstOrNull { !it.second.isBefore(today) }
        for ((event, date) in events) {
            val past = date.isBefore(today)
            val days = ChronoUnit.DAYS.between(today, date)
            Row(
                Modifier.fillMaxWidth().clickable { onShow(date) }.padding(vertical = 10.dp).alpha(if (past) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(CalendarText.number(event.day, lang), style = calendarNumberStyle(lang, 20f), color = if (event.major) c.accent else c.text)
                    Text(CalendarText.hijriMonth(event.month, lang), color = c.secondary, fontSize = 10.sp, maxLines = 1)
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(CalendarText.event(event, lang), color = c.text, fontSize = 15.sp, fontWeight = if (event.major) FontWeight.SemiBold else FontWeight.Normal)
                    Text(CalendarText.gregorian(date, locale), color = c.secondary, fontSize = 12.sp)
                }
                if (!past) {
                    val isNext = event == next?.first
                    Text(
                        CalendarText.relative(days, lang),
                        color = if (isNext) c.onAccent else c.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isNext) c.accent else c.text.copy(alpha = 0.07f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// Alerts

@Composable
private fun Alerts(vm: SalahViewModel, config: SalahConfig, lang: CalLang) {
    val cal = config.calendar
    fun set(body: (salah.core.CalendarSettings) -> salah.core.CalendarSettings) = vm.update { it.copy(calendar = body(it.calendar)) }
    Panel(title = CalendarText.text(CalKey.ALERTS, lang).uppercase()) {
        SwitchRow(CalendarText.text(CalKey.ALERT_NEW_MONTH, lang), cal.notifyNewMonth) { on -> set { it.copy(notifyNewMonth = on) } }
        SwitchRow(CalendarText.text(CalKey.ALERT_SPECIAL_DAYS, lang), cal.notifySpecialDays) { on -> set { it.copy(notifySpecialDays = on) } }
        SwitchRow(CalendarText.text(CalKey.ALERT_WHITE_DAYS, lang), cal.notifyWhiteDays) { on -> set { it.copy(notifyWhiteDays = on) } }
        Text(CalendarText.text(CalKey.ALERT_TIME, lang), color = palette.text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Segmented(
            options = listOf(
                IslamicAlertTime.MAGHRIB_BEFORE to CalendarText.text(CalKey.EVENING_BEFORE, lang),
                IslamicAlertTime.MORNING to CalendarText.text(CalKey.MORNING_OF, lang),
            ),
            selected = cal.alertTime,
            onSelect = { t -> set { it.copy(alertTime = t) } },
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
}
