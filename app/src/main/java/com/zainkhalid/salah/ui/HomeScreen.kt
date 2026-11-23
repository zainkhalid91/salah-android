package com.zainkhalid.salah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import salah.core.AppText
import salah.core.DaySchedule
import salah.core.HijriDate
import salah.core.NextPrayerResolver
import salah.core.Prayer
import salah.core.PrayerSchedule
import salah.core.SalahConfig
import salah.core.SavedLocation
import salah.core.SunnahFast
import salah.core.TimeFormatting
import salah.core.home.HomeWidget
import salah.core.isFriday
import salah.core.kindwords.KindWordsCadence
import salah.core.kindwords.KindWordsLibrary
import salah.core.kindwords.KindWordsPlanner
import salah.core.localDate
import salah.core.quran.QueueSource
import salah.core.quran.QuranCatalog
import java.time.Instant
import java.time.LocalDate

/**
 * Home: a greeting, the moment's main card (the next prayer, or Friday's last hour after Asr),
 * then the widgets the user picked, in their order.
 */
@Composable
fun HomeScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Instant.now()
        }
    }
    val location = config.location?.takeIf { it.isValid }

    Column(
        modifier.fillMaxSize().background(c.background).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (location == null) {
            Text(tr("Assalamu alaikum."), color = c.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Card(onClick = { vm.showLocationSheet = true }) {
                Text(tr("Set your location to see prayer times."), color = c.text, fontSize = 16.sp)
                Text(tr("Tap to set your location"), color = c.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            return@Column
        }
        val zone = location.zone
        val today = localDate(now, zone)
        val schedule = PrayerSchedule.forDate(today, location, config.calculation)
        val lang = config.display.lang

        Column(Modifier.padding(top = 8.dp)) {
            Text(tr(greeting(now.atZone(zone).hour)), color = c.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            val hijri = runCatching { HijriDate.of(today, config.display.hijriAdjustment) }.getOrNull()
            Text(
                listOfNotNull(AppText.longDate(today, lang), hijri?.let { AppText.hijri(it, lang) }).joinToString(" · "),
                color = c.secondary,
                fontSize = 13.sp,
            )
        }

        for (w in config.home.widgets) {
            when (w) {
                HomeWidget.NEXT_PRAYER -> FeaturedCard(config, location, schedule, now)
                HomeWidget.PRAYER_TIMES -> PrayerTimesCard(config, location, schedule, now) { vm.tab = Tab.TODAY }
                HomeWidget.DUA_OF_THE_DAY -> DuaCard(today) { vm.open(MoreDest.DUA) }
                HomeWidget.QURAN -> QuranCard(vm, now, location)
                HomeWidget.SUNNAH_FAST -> FastCard(config, location, schedule, now)
                HomeWidget.KIND_WORD -> KindWordCard(today) { vm.open(MoreDest.KIND_WORDS) }
                HomeWidget.KIND_WORDS_SCHEDULE -> KindScheduleCard(config) { vm.open(MoreDest.KIND_WORDS) }
            }
        }

        TextButton(onClick = { vm.showCustomizeHome = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(tr("Customize Home"), color = c.accent)
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 4 until 12 -> "Good morning."
    in 12 until 15 -> "A calm afternoon."
    in 15 until 18 -> "Good afternoon."
    else -> "Good evening."
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.display)
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        if (title != null) Eyebrow(title, Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** The next prayer with a countdown, or on Friday between Asr and Maghrib, the last hour. */
@Composable
private fun FeaturedCard(config: SalahConfig, location: SavedLocation, schedule: DaySchedule, now: Instant) {
    val c = palette
    val lang = config.display.lang
    val zone = location.zone
    val asr = schedule.time(Prayer.ASR)
    val maghrib = schedule.time(Prayer.MAGHRIB)
    val fridayAfterAsr = schedule.isFriday && asr != null && maghrib != null && now >= asr && now < maghrib
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.timeline),
    ) {
        DottedBackground(Modifier.matchParentSize())
        Column(Modifier.padding(18.dp)) {
            if (fridayAfterAsr) {
                Eyebrow(tr("FRIDAY · AFTER ASR"), color = c.onTimelineDim)
                Text(tr("Maghrib in"), color = c.onTimeline, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                PixelText(TimeFormatting.countdown((maghrib!!.toEpochMilli() - now.toEpochMilli()) / 1000.0), 44f, color = c.onTimeline)
                KindWordsLibrary.BUNDLED.word(KindWordsLibrary.LAST_HOUR_ID)?.let {
                    Text(it.notificationBody, color = c.onTimelineDim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
                }
            } else {
                val next = NextPrayerResolver.resolve(now, location, config.calculation) ?: return@Column
                Eyebrow(tr("NEXT PRAYER"), color = c.onTimelineDim)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                    Text(next.label(config.display.jumuahRelabel, lang), color = c.onTimeline, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PixelText(TimeFormatting.clock(next.time, zone, config.display.use24HourClock, lang = lang), 26f, color = c.onTimeline)
                }
                PixelText(TimeFormatting.countdown(next.secondsRemaining(now)), 44f, color = c.onTimeline, modifier = Modifier.padding(top = 6.dp))
                val day = if (next.date == schedule.date) schedule else PrayerSchedule.forDate(next.date, location, config.calculation)
                KindWordsPlanner.beforePrayerLine(next.prayer, day, config, force = true)?.let {
                    Text(it, color = c.onTimelineDim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun PrayerTimesCard(config: SalahConfig, location: SavedLocation, schedule: DaySchedule, now: Instant, onOpen: () -> Unit) {
    val c = palette
    val lang = config.display.lang
    val next = NextPrayerResolver.resolve(now, location, config.calculation)
    Card(onClick = onOpen, title = tr("PRAYER TIMES")) {
        for (p in Prayer.entries) {
            val t = schedule.time(p)
            val isNext = next != null && !next.isTomorrow && next.prayer == p
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isNext) c.accent.copy(alpha = 0.14f) else c.display)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    schedule.label(p, config.display.jumuahRelabel, lang),
                    color = if (p == Prayer.SUNRISE) c.secondary else c.text,
                    fontSize = 15.sp,
                    fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(t?.let { TimeFormatting.clock(it, location.zone, config.display.use24HourClock, lang = lang) } ?: "--", style = pixelStyle(16f), color = if (isNext) c.accent else c.text)
            }
        }
    }
}

@Composable
private fun DuaCard(today: LocalDate, onOpen: () -> Unit) {
    val c = palette
    val dua = KindWordsLibrary.BUNDLED.duaOfTheDay(today) ?: return
    Card(onClick = onOpen, title = tr("DU'A OF THE DAY")) {
        Text(dua.label, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(dua.english, color = c.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        dua.transliteration.takeIf { it.isNotBlank() }?.let { Text(it, color = c.secondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp)) }
        Text(dua.source, color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** What's playing, or a surah for the moment: Al-Kahf on Friday, Al-Mulk at night, else a short surah. */
@Composable
private fun QuranCard(vm: SalahViewModel, now: Instant, location: SavedLocation) {
    val c = palette
    val playback by vm.quran.state.collectAsStateWithLifecycle()
    val playing = playback.current?.let { QuranCatalog.surah(it) }
    val local = now.atZone(location.zone)
    val suggestion = when {
        local.toLocalDate().isFriday -> 18
        local.hour >= 19 || local.hour < 4 -> 67
        else -> 93 + (local.toLocalDate().toEpochDay() % 22).toInt()
    }
    val s = playing ?: QuranCatalog.surah(suggestion) ?: return
    Card(onClick = { vm.tab = Tab.QURAN }, title = tr(if (playing != null) "NOW PLAYING" else "QURAN")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${s.number}. ${s.name}", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (playing != null) vm.config.value.quran.reciter.name
                    else tr(
                        when (suggestion) {
                            18 -> "Al-Kahf on Friday"
                            67 -> "Al-Mulk before sleep"
                            else -> "A short surah for today"
                        },
                    ),
                    color = c.secondary,
                    fontSize = 13.sp,
                )
            }
            Text(s.arabicName, color = c.text, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.accent).clickable {
                    if (playing != null) vm.quran.togglePlay() else vm.quran.play(listOf(s.number), 0, QueueSource.SurahList)
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing != null && playback.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow, contentDescription = tr("Play"), tint = c.onAccent)
            }
        }
    }
}

/** Today's sunnah fast, or tomorrow's once Maghrib has passed (the fast starts at the next Fajr). */
@Composable
private fun FastCard(config: SalahConfig, location: SavedLocation, schedule: DaySchedule, now: Instant) {
    val c = palette
    val maghrib = schedule.time(Prayer.MAGHRIB)
    val tomorrow = maghrib != null && now >= maghrib
    val date = if (tomorrow) schedule.date.plusDays(1) else schedule.date
    val reasons = SunnahFast.reasons(date, config.display.hijriAdjustment)
    if (reasons.isEmpty()) return
    val lang = config.display.lang
    Card(title = tr(if (tomorrow) "SUNNAH FAST TOMORROW" else "SUNNAH FAST TODAY")) {
        Text(SunnahFast.tag(reasons, lang).orEmpty(), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(reasons.first().reason, color = c.secondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        if (!tomorrow) {
            val fajr = schedule.time(Prayer.FAJR)
            if (fajr != null && maghrib != null) {
                Text(
                    tr("Fajr {0} · Maghrib {1}", TimeFormatting.clock(fajr, location.zone, config.display.use24HourClock, lang = lang), TimeFormatting.clock(maghrib, location.zone, config.display.use24HourClock, lang = lang)),
                    color = c.accent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun KindWordCard(today: LocalDate, onOpen: () -> Unit) {
    val c = palette
    val w = KindWordsLibrary.BUNDLED.kindWordOfTheDay(today) ?: return
    Card(onClick = onOpen, title = tr("KIND WORD OF THE DAY")) {
        Text(w.text, color = c.text, fontSize = 15.sp, lineHeight = 21.sp)
        Text("— ${w.source}", color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** How Kind Words decides when to send, in plain words. */
@Composable
private fun KindScheduleCard(config: SalahConfig, onOpen: () -> Unit) {
    val c = palette
    val k = config.kindWords
    Card(onClick = onOpen, title = tr("KIND WORDS SCHEDULE")) {
        if (!k.enabled) {
            Text(tr("Kind Words is off."), color = c.text, fontSize = 15.sp)
            Text(tr("Tap to turn it on"), color = c.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            return@Card
        }
        @Composable
        fun line(text: String) = Text("• $text", color = c.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 2.dp))
        if (k.cadence != KindWordsCadence.OFF) {
            line(tr("{0}, never within {1} minutes of a prayer", tr(k.cadence.displayName), KindWordsPlanner.PRAYER_GAP_MINUTES))
        }
        if (k.beforePrayer) line(tr("A verse or hadith with each early reminder"))
        if (k.afterPrayer) line(tr("\"Have you prayed?\" {0} minutes after each prayer", k.afterPrayerMinutes))
        if (k.friday) line(tr("Friday: Al-Kahf at 8:00 and the last hour before Maghrib"))
        if (k.workDuas) line(tr("Work du'as at {0} and {1}, Monday to Friday", k.workStart, k.workEnd))
        Text(tr(if (k.silent) "Arrives silently." else "Arrives with your reminder sound."), color = c.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Turn Home widgets on or off and change their order. */
@Composable
fun CustomizeHomeDialog(vm: SalahViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    val home = config.home
    fun set(body: (salah.core.home.HomeSettings) -> salah.core.home.HomeSettings) = vm.update { it.copy(home = body(it.home)) }
    val ordered = home.widgets + HomeWidget.entries.filter { it !in home.widgets }
    AlertDialog(
        onDismissRequest = { vm.showCustomizeHome = false },
        containerColor = c.background,
        title = { Text(tr("Customize Home"), color = c.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (w in ordered) {
                    val on = home.isOn(w)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(w.displayName), color = if (on) c.text else c.secondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (on) {
                            IconButton(onClick = { set { it.move(w, -1) } }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = tr("Move up"), tint = c.secondary) }
                            IconButton(onClick = { set { it.move(w, 1) } }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("Move down"), tint = c.secondary) }
                        }
                        Switch(checked = on, onCheckedChange = { v -> set { it.set(w, v) } })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.showCustomizeHome = false }) { Text(tr("Done"), color = c.accent) } },
    )
}
