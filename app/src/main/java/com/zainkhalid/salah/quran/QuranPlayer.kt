package com.zainkhalid.salah.quran

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zainkhalid.salah.repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import salah.core.quran.QueueSource
import salah.core.quran.QuranCatalog
import salah.core.quran.QuranQueue
import salah.core.quran.QuranRepeatMode
import salah.core.quran.QuranSettings
import salah.core.quran.Reciter

/** What the Quran screens draw. Position is read from [QuranPlayer.positionMs] while visible. */
data class QuranPlaybackState(
    val queue: QuranQueue? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** Shown under the player, e.g. when the recitation server can't be reached. */
    val error: String? = null,
    /** Bumped on every queue change, since [QuranQueue] is mutable. */
    val revision: Int = 0,
) {
    val current: Int? get() = queue?.current
}

/**
 * The app's single Quran player. It owns an ExoPlayer whose playlist mirrors a [QuranQueue] (one
 * MediaItem per queue entry, in play order), so the lock screen, headphones and Bluetooth controls
 * all work through [QuranPlaybackService]'s media session, while the queue rules stay in core.
 *
 * Nothing is fetched until the user presses play. The queue and position are saved, so the next
 * launch shows where they left off, paused.
 */
class QuranPlayer private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("salah_quran", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val main = Handler(Looper.getMainLooper())

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private var queue: QuranQueue? = null
    /** The saved position, applied when a restored queue is first played. */
    private var restoredPositionMs = 0L
    private var prepared = false
    private var controller: ListenableFuture<MediaController>? = null

    private val _state = MutableStateFlow(QuranPlaybackState())
    val state: StateFlow<QuranPlaybackState> = _state.asStateFlow()

    private val settings: QuranSettings get() = context.repo.config.value.quran

    init {
        restore()
        player.repeatMode = repeatModeFor(settings.repeatMode)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (!isPlaying) save()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) publish(error = null) else publish()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                queue?.jump(player.currentMediaItemIndex)
                publish(error = null, bump = true)
                save()
            }

            override fun onPlayerError(error: PlaybackException) {
                publish(error = "Can't reach the recitation server. Check your connection.")
            }
        })
        // Save the position every 10 seconds while playing, so a killed process resumes close by.
        main.postDelayed(object : Runnable {
            override fun run() {
                if (player.isPlaying) save()
                main.postDelayed(this, 10_000)
            }
        }, 10_000)
    }

    fun positionMs(): Long = if (prepared) player.currentPosition else restoredPositionMs

    fun durationMs(): Long = if (prepared) player.duration.takeIf { it != C.TIME_UNSET } ?: 0L else 0L

    /** Starts a new queue at [startAt] and plays it. */
    fun play(items: List<Int>, startAt: Int = 0, source: QueueSource = QueueSource.SurahList) {
        if (items.isEmpty()) return
        val q = QuranQueue(items, startAt, source)
        if (settings.shuffle) q.setShuffle(true, System.nanoTime())
        queue = q
        prepared = true
        restoredPositionMs = 0
        player.setMediaItems(q.items.map { mediaItem(it) }, q.index, 0L)
        player.prepare()
        startPlaying()
        publish(error = null, bump = true)
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else resume()
    }

    fun resume() {
        val q = queue ?: return
        if (!prepared) {
            prepared = true
            player.setMediaItems(q.items.map { mediaItem(it) }, q.index, restoredPositionMs)
            player.prepare()
        } else if (player.playbackState == Player.STATE_IDLE) {
            // After an error: try again from where it stopped.
            player.prepare()
        }
        startPlaying()
    }

    fun pause() = player.pause()

    fun next() {
        if (!prepared) resume()
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        else if (settings.repeatMode != QuranRepeatMode.OFF) player.seekTo(0, 0L)
    }

    fun previous() {
        if (!prepared) resume()
        // Restart past three seconds, else the previous surah (QuranQueue.RESTART_THRESHOLD).
        if (player.currentPosition > (QuranQueue.RESTART_THRESHOLD * 1000).toLong() || !player.hasPreviousMediaItem()) player.seekTo(0L)
        else player.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        if (prepared) player.seekTo(ms) else {
            restoredPositionMs = ms
            publish()
        }
    }

    /** Jumps to the entry at [position] in the queue. */
    fun jump(position: Int) {
        val q = queue ?: return
        q.jump(position)
        if (!prepared) resume()
        player.seekTo(position, 0L)
        startPlaying()
        publish(bump = true)
    }

    fun playNext(surah: Int) {
        val q = queue ?: return play(listOf(surah))
        val at = q.index + 1
        q.playNext(surah)
        if (prepared) player.addMediaItem(at, mediaItem(surah))
        changed()
    }

    fun append(surah: Int) {
        val q = queue ?: return play(listOf(surah))
        q.append(surah)
        if (prepared) player.addMediaItem(mediaItem(surah))
        changed()
    }

    fun remove(position: Int) {
        val q = queue ?: return
        if (position == q.index) return
        q.remove(position)
        if (prepared) player.removeMediaItem(position)
        changed()
    }

    fun moveUpcoming(from: Int, to: Int) {
        val q = queue ?: return
        if (from <= q.index || to <= q.index) return
        q.moveUpcoming(from, to)
        if (prepared) player.moveMediaItem(from, to)
        changed()
    }

    fun clearUpcoming() {
        val q = queue ?: return
        val from = q.index + 1
        val count = q.items.size - from
        q.clearUpcoming()
        if (prepared && count > 0) player.removeMediaItems(from, from + count)
        changed()
    }

    fun setRepeat(mode: QuranRepeatMode) {
        context.repo.update { it.copy(quran = it.quran.copy(repeatMode = mode)) }
        player.repeatMode = repeatModeFor(mode)
        publish()
    }

    /** Shuffles what's after the current surah (or restores the order), keeping the current one playing. */
    fun setShuffle(on: Boolean) {
        context.repo.update { it.copy(quran = it.quran.copy(shuffle = on)) }
        val q = queue ?: return publish()
        q.setShuffle(on, System.nanoTime())
        if (prepared) {
            val keep = player.currentMediaItemIndex
            val before = q.items.take(q.index)
            val after = q.items.drop(q.index + 1)
            // Replace around the current item so it never stops.
            if (player.mediaItemCount > keep + 1) player.removeMediaItems(keep + 1, player.mediaItemCount)
            if (keep > 0) player.removeMediaItems(0, keep)
            player.addMediaItems(0, before.map { mediaItem(it) })
            player.addMediaItems(after.map { mediaItem(it) })
        }
        changed()
    }

    /** A new reciter applies from the current surah, at the same spot when it was paused. */
    fun reciterChanged() {
        val q = queue ?: return
        if (!prepared) return
        val wasPlaying = player.isPlaying
        val pos = player.currentPosition
        player.setMediaItems(q.items.map { mediaItem(it) }, q.index, pos)
        player.prepare()
        if (wasPlaying) player.play()
        publish(bump = true)
    }

    /** Called at prayer time: pauses if playing and the user wants that. Returns true if it paused. */
    fun pauseForPrayer(): Boolean {
        if (!player.isPlaying) return false
        player.pause()
        return true
    }

    private fun startPlaying() {
        connectSession()
        player.play()
    }

    /** Binding a controller starts [QuranPlaybackService], which keeps playback alive in the background. */
    private fun connectSession() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, QuranPlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync()
    }

    private fun changed() {
        publish(bump = true)
        save()
    }

    private fun publish(error: String? = _state.value.error, bump: Boolean = false) {
        val s = _state.value
        _state.value = QuranPlaybackState(
            queue = queue?.copy(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            error = error,
            revision = if (bump) s.revision + 1 else s.revision,
        )
    }

    private fun mediaItem(surah: Int): MediaItem {
        val reciter: Reciter = settings.reciter
        val s = QuranCatalog.surah(surah)
        val meta = MediaMetadata.Builder()
            .setTitle(s?.let { "${it.number}. ${it.name}" } ?: "Surah $surah")
            .setArtist(reciter.name)
            .setAlbumTitle(s?.meaning)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setMediaId(surah.toString())
            .setUri(QuranCatalog.audioUrl(reciter, surah).orEmpty())
            .setMediaMetadata(meta)
            .build()
    }

    private fun save() {
        val q = queue
        prefs.edit {
            if (q == null) remove(KEY_QUEUE) else putString(KEY_QUEUE, json.encodeToString(QuranQueue.serializer(), q))
            putLong(KEY_POSITION, positionMs())
        }
    }

    private fun restore() {
        val text = prefs.getString(KEY_QUEUE, null) ?: return
        queue = runCatching { json.decodeFromString(QuranQueue.serializer(), text) }.getOrNull()?.takeUnless { it.isEmpty }
        restoredPositionMs = prefs.getLong(KEY_POSITION, 0L)
        publish(bump = true)
    }

    companion object {
        private const val KEY_QUEUE = "queue"
        private const val KEY_POSITION = "position"

        @Volatile private var instance: QuranPlayer? = null

        /** Must be called on the main thread (ExoPlayer is single-threaded). */
        fun get(context: Context): QuranPlayer =
            instance ?: synchronized(this) { instance ?: QuranPlayer(context.applicationContext).also { instance = it } }

        /** The player only if something already created it, so alarms don't spin one up for nothing. */
        fun existing(): QuranPlayer? = instance

        fun repeatModeFor(mode: QuranRepeatMode): Int = when (mode) {
            QuranRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            QuranRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            QuranRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }
}
