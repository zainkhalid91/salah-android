package com.zainkhalid.salah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zainkhalid.salah.quran.QuranPlaybackState
import kotlinx.coroutines.delay
import salah.core.TimeFormatting
import salah.core.quran.BuiltInPlaylist
import salah.core.quran.QueueSource
import salah.core.quran.QuranCatalog
import salah.core.quran.QuranPlaylist
import salah.core.quran.QuranRepeatMode
import salah.core.quran.Surah

/** Material icons the core icon set lacks, drawn from their standard paths. */
object PlayerIcons {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (p in paths) addPath(addPathNodes(p), fill = SolidColor(Color.Black))
    }.build()

    val Pause = icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    val Next = icon("Next", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    val Previous = icon("Previous", "M6,6h2v12H6zM9.5,12l8.5,6V6z")
    val Repeat = icon("Repeat", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z")
    val RepeatOne = icon("RepeatOne", "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z", "M13,15V9h-1l-2,1v1h1.5v4H13z")
    val Shuffle = icon(
        "Shuffle",
        "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,13.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z",
    )
    val Queue = icon(
        "Queue",
        "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z",
    )
    val Book = icon(
        "Book",
        "M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,4h5v8l-2.5,-1.5L6,12V4z",
    )
}

private enum class QuranView { SURAHS, PLAYLISTS }

@Composable
fun QuranScreen(vm: SalahViewModel, modifier: Modifier) {
    val config by vm.config.collectAsStateWithLifecycle()
    val playback by vm.quran.state.collectAsStateWithLifecycle()
    val c = palette
    val q = config.quran
    var view by rememberSaveable { mutableStateOf(QuranView.SURAHS) }
    var query by rememberSaveable { mutableStateOf("") }
    var addTo by remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(tr("Quran"), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            DropdownRow(
                tr("Reciter"),
                QuranCatalog.reciters.map { it.id to it.displayName },
                q.reciter.id,
            ) { id ->
                vm.update { it.copy(quran = it.quran.copy(reciterId = id)) }
                vm.quran.reciterChanged()
            }
            Segmented(
                options = listOf(QuranView.SURAHS to tr("Surahs"), QuranView.PLAYLISTS to tr("Playlists")),
                selected = view,
                onSelect = { view = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            when (view) {
                QuranView.SURAHS -> SurahList(vm, query, { query = it }, playback.current, onAddToPlaylist = { addTo = it })
                QuranView.PLAYLISTS -> PlaylistsView(vm)
            }
        }
        if (playback.queue != null) FullPlayer(vm, playback)
    }
    if (vm.showQueue) QueueSheet(vm, playback)
    addTo?.let { surah -> AddToPlaylistDialog(vm, surah) { addTo = null } }
}

@Composable
private fun SurahList(vm: SalahViewModel, query: String, onQuery: (String) -> Unit, current: Int?, onAddToPlaylist: (Int) -> Unit) {
    val c = palette
    val shown = QuranCatalog.surahs.filter { QuranCatalog.matches(it, query) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(tr("Search by name, meaning or number"), color = c.secondary) },
                colors = fieldColors(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        itemsIndexed(shown, key = { _, s -> s.number }) { i, s ->
            SurahRow(
                s,
                playing = s.number == current,
                onPlay = { vm.quran.play(shown.map { it.number }, i, QueueSource.SurahList) },
                onPlayNext = { vm.quran.playNext(s.number) },
                onAppend = { vm.quran.append(s.number) },
                onAddToPlaylist = { onAddToPlaylist(s.number) },
            )
        }
        if (shown.isEmpty()) item(key = "none") { Text(tr("No surah matches."), color = c.secondary, modifier = Modifier.padding(12.dp)) }
        item(key = "credit") {
            Text(tr("Recitations stream from mp3quran.net."), color = c.secondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun SurahRow(
    s: Surah,
    playing: Boolean,
    onPlay: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAppend: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = palette
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (playing) c.timeline else c.display)
            .clickable(onClick = onPlay)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (playing) c.onTimeline else c.text
        val dim = if (playing) c.onTimelineDim else c.secondary
        Text(s.number.toString(), style = pixelStyle(15f), color = if (playing) fg else c.accent, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${tr(s.meaning)} · ${tr("{0} ayahs", s.ayahCount)} · ${tr(s.revelation.displayName)}", color = dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(s.arabicName, color = fg, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
        trailing?.invoke()
        if (onPlayNext != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = tr("More"), tint = dim) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Play next")) }, onClick = { menu = false; onPlayNext() })
                    if (onAppend != null) DropdownMenuItem(text = { Text(tr("Add to queue")) }, onClick = { menu = false; onAppend() })
                    if (onAddToPlaylist != null) DropdownMenuItem(text = { Text(tr("Add to playlist…")) }, onClick = { menu = false; onAddToPlaylist() })
                }
            }
        }
    }
}

@Composable
private fun PlaylistsView(vm: SalahViewModel) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    val q = config.quran
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<QuranPlaylist?>(null) }
    var creating by remember { mutableStateOf(false) }
    fun playlists(body: (List<QuranPlaylist>) -> List<QuranPlaylist>) = vm.update { it.copy(quran = it.quran.copy(playlists = body(it.quran.playlists))) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "builtin") {
            Panel(title = tr("BUILT IN")) {
                for (b in BuiltInPlaylist.entries) {
                    SettingRow(tr(b.displayName), tr("{0} surahs", b.surahs.size), onClick = { vm.quran.play(b.surahs, 0, QueueSource.BuiltIn(b)) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = tr("Play"), tint = c.accent)
                    }
                }
            }
        }
        item(key = "mine") {
            Panel(title = tr("YOUR PLAYLISTS")) {
                if (q.playlists.isEmpty()) {
                    Text(tr("Make a playlist, then add surahs from the list with ⋮."), color = c.secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
                for (p in q.playlists) {
                    SettingRow(p.name, tr("{0} surahs", p.surahs.size), onClick = { open = if (open == p.id) null else p.id }) {
                        Row {
                            IconButton(onClick = { rename = p }) { Icon(Icons.Filled.Edit, contentDescription = tr("Rename"), tint = c.secondary) }
                            IconButton(
                                onClick = { if (p.surahs.isNotEmpty()) vm.quran.play(p.surahs, 0, QueueSource.Playlist(p.id)) },
                            ) { Icon(Icons.Filled.PlayArrow, contentDescription = tr("Play"), tint = c.accent) }
                        }
                    }
                    if (open == p.id) {
                        p.surahs.forEachIndexed { i, n ->
                            val s = QuranCatalog.surah(n) ?: return@forEachIndexed
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${s.number}. ${s.name}", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f).clickable { vm.quran.play(p.surahs, i, QueueSource.Playlist(p.id)) })
                                IconButton(onClick = {
                                    playlists { all -> all.map { if (it.id == p.id) it.copy(surahs = it.surahs.filterIndexed { j, _ -> j != i }) else it } }
                                }) { Icon(Icons.Filled.Close, contentDescription = tr("Remove"), tint = c.secondary) }
                            }
                        }
                        TextButton(onClick = {
                            playlists { all -> all.filter { it.id != p.id } }
                            open = null
                        }) { Icon(Icons.Filled.Delete, contentDescription = null, tint = c.accent); Text(" " + tr("Delete playlist"), color = c.accent) }
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = c.accent)
                    Text(" " + tr("New playlist"), color = c.accent)
                }
            }
        }
        item(key = "player") {
            Panel(title = tr("PLAYER")) {
                SwitchRow(tr("Pause at prayer time"), q.pauseAtPrayerTime, tr("Recitation pauses when each prayer begins")) { on ->
                    vm.update { it.copy(quran = it.quran.copy(pauseAtPrayerTime = on)) }
                }
                SwitchRow(tr("Mini player"), q.showInMenuBar, tr("Shows what's playing above the tabs")) { on ->
                    vm.update { it.copy(quran = it.quran.copy(showInMenuBar = on)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (creating) NameDialog(tr("New playlist"), "") { name ->
        creating = false
        if (name != null) playlists { it + QuranPlaylist(name = name.ifBlank { "Playlist" }) }
    }
    rename?.let { p ->
        NameDialog(tr("Rename"), p.name) { name ->
            rename = null
            if (name != null) playlists { all -> all.map { if (it.id == p.id) it.copy(name = name.ifBlank { p.name }) else it } }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    val c = palette
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = c.background,
        title = { Text(title, color = c.text) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, colors = fieldColors()) },
        confirmButton = { TextButton(onClick = { onDone(text.trim()) }) { Text(tr("Save"), color = c.accent) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(tr("Cancel"), color = c.secondary) } },
    )
}

@Composable
private fun AddToPlaylistDialog(vm: SalahViewModel, surah: Int, onDone: () -> Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    var naming by remember { mutableStateOf(false) }
    fun add(id: String) = vm.update { cfg ->
        cfg.copy(quran = cfg.quran.copy(playlists = cfg.quran.playlists.map { if (it.id == id) it.copy(surahs = it.surahs + surah) else it }))
    }
    if (naming) {
        NameDialog(tr("New playlist"), "") { name ->
            if (name != null) vm.update { it.copy(quran = it.quran.copy(playlists = it.quran.playlists + QuranPlaylist(name = name.ifBlank { "Playlist" }, surahs = listOf(surah)))) }
            onDone()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = c.background,
        title = { Text(tr("Add to playlist"), color = c.text) },
        text = {
            Column {
                for (p in config.quran.playlists) {
                    SettingRow(p.name, tr("{0} surahs", p.surahs.size), onClick = { add(p.id); onDone() })
                }
                SettingRow(tr("New playlist"), onClick = { naming = true })
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(tr("Cancel"), color = c.secondary) } },
    )
}

/** Reads the player position twice a second while shown; nothing recomposes otherwise. */
@Composable
private fun rememberPosition(vm: SalahViewModel): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(vm.quran.positionMs()) }
    var dur by remember { mutableLongStateOf(vm.quran.durationMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            pos = vm.quran.positionMs()
            dur = vm.quran.durationMs()
            delay(500)
        }
    }
    return pos to dur
}

@Composable
private fun FullPlayer(vm: SalahViewModel, playback: QuranPlaybackState) {
    val config by vm.config.collectAsStateWithLifecycle()
    val c = palette
    val surah = playback.current?.let { QuranCatalog.surah(it) } ?: return
    val (pos, dur) = rememberPosition(vm)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(c.display)
            .border(1.dp, c.line, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${surah.number}. ${surah.name}", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sourceLine(vm, playback), color = c.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(surah.arabicName, color = c.text, fontSize = 20.sp)
        }
        val fraction = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        Slider(
            value = if (dragging) dragValue else fraction,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (dur > 0) vm.quran.seekTo((dragValue * dur).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
        )
        Row {
            Text(TimeFormatting.mediaTime((if (dragging) (dragValue * dur).toLong() else pos) / 1000), color = c.secondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(if (dur > 0) TimeFormatting.mediaTime(dur / 1000) else "--:--", color = c.secondary, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val repeat = config.quran.repeatMode
            IconButton(onClick = { vm.quran.setRepeat(repeat.next) }) {
                Icon(if (repeat == QuranRepeatMode.ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat, contentDescription = tr("Repeat"), tint = if (repeat == QuranRepeatMode.OFF) c.secondary else c.accent)
            }
            IconButton(onClick = { vm.quran.previous() }) { Icon(PlayerIcons.Previous, contentDescription = tr("Previous"), tint = c.text) }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(c.accent).clickable { vm.quran.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playback.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow, contentDescription = tr(if (playback.isPlaying) "Pause" else "Play"), tint = c.onAccent, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { vm.quran.next() }) { Icon(PlayerIcons.Next, contentDescription = tr("Next"), tint = c.text) }
            IconButton(onClick = { vm.quran.setShuffle(!config.quran.shuffle) }) {
                Icon(PlayerIcons.Shuffle, contentDescription = tr("Shuffle"), tint = if (config.quran.shuffle) c.accent else c.secondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val status = playback.error?.let { tr(it) } ?: if (playback.isBuffering) tr("Loading…") else null
            Text(status.orEmpty(), color = if (playback.error != null) c.accent else c.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.showQueue = true }) {
                Icon(PlayerIcons.Queue, contentDescription = null, tint = c.secondary)
                Text(" " + tr("Queue"), color = c.secondary)
            }
        }
    }
}

/** "Mishary Alafasy · Juz 'Amma" */
@Composable
private fun sourceLine(vm: SalahViewModel, playback: QuranPlaybackState): String {
    val config by vm.config.collectAsStateWithLifecycle()
    val source = when (val s = playback.queue?.source) {
        is QueueSource.BuiltIn -> tr(s.playlist.displayName)
        is QueueSource.Playlist -> config.quran.playlist(s.id)?.name
        else -> null
    }
    return listOfNotNull(config.quran.reciter.name, source).joinToString(" · ")
}

/** Above the tabs on other screens: what's playing, with play and next. */
@Composable
fun MiniPlayer(vm: SalahViewModel, playback: QuranPlaybackState, onOpen: () -> Unit) {
    val c = palette
    val surah = playback.current?.let { QuranCatalog.surah(it) } ?: return
    val (pos, dur) = rememberPosition(vm)
    Column(Modifier.fillMaxWidth().background(c.display).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
            Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).height(2.dp).background(c.accent))
        }
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${surah.number}. ${surah.name}", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sourceLine(vm, playback), color = c.secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.quran.togglePlay() }) {
                Icon(if (playback.isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow, contentDescription = tr(if (playback.isPlaying) "Pause" else "Play"), tint = c.text)
            }
            IconButton(onClick = { vm.quran.next() }) { Icon(PlayerIcons.Next, contentDescription = tr("Next"), tint = c.text) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: SalahViewModel, playback: QuranPlaybackState) {
    val c = palette
    val queue = playback.queue ?: return
    ModalBottomSheet(onDismissRequest = { vm.showQueue = false }, containerColor = c.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Queue"), color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (queue.hasNext) TextButton(onClick = { vm.quran.clearUpcoming() }) { Text(tr("Clear upcoming"), color = c.accent) }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue.items, key = { i, n -> "$i-$n-${playback.revision}" }) { i, n ->
                val s = QuranCatalog.surah(n) ?: return@itemsIndexed
                val isCurrent = i == queue.index
                val upcoming = i > queue.index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) c.timeline else c.display)
                        .clickable { vm.quran.jump(i) }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${s.number}. ${s.name}",
                        color = when {
                            isCurrent -> c.onTimeline
                            upcoming -> c.text
                            else -> c.secondary
                        },
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    )
                    if (upcoming) {
                        if (i > queue.index + 1) IconButton(onClick = { vm.quran.moveUpcoming(i, i - 1) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = tr("Move up"), tint = c.secondary) }
                        if (i < queue.items.lastIndex) IconButton(onClick = { vm.quran.moveUpcoming(i, i + 1) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("Move down"), tint = c.secondary) }
                        IconButton(onClick = { vm.quran.remove(i) }) { Icon(Icons.Filled.Close, contentDescription = tr("Remove"), tint = c.secondary) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = palette.text,
    unfocusedTextColor = palette.text,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.line,
    cursorColor = palette.accent,
)
