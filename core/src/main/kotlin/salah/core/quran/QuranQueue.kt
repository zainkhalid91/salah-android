package salah.core.quran

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class QuranRepeatMode {
    @SerialName("off") OFF,
    @SerialName("all") ALL,
    @SerialName("one") ONE;

    /** Off → all → one → off, like the repeat button in a music app. */
    val next: QuranRepeatMode
        get() = when (this) {
            OFF -> ALL
            ALL -> ONE
            ONE -> OFF
        }
}

/** Where the queue came from, shown under the surah name in the player. */
@Serializable
sealed class QueueSource {
    @Serializable @SerialName("surahList")
    data object SurahList : QueueSource()

    @Serializable @SerialName("playlist")
    data class Playlist(val id: String) : QueueSource()

    @Serializable @SerialName("builtIn")
    data class BuiltIn(val playlist: BuiltInPlaylist) : QueueSource()
}

/**
 * The play queue, ported from the macOS app's QuranQueue so every rule in its
 * roadmap/quran-player.md §6 behaves the same and is unit-tested. The player asks it what to
 * play next; it never touches audio itself.
 */
@Serializable
class QuranQueue private constructor(
    /** Surah numbers in play order. */
    private var _items: MutableList<Int>,
    private var _index: Int,
    var source: QueueSource,
    private var _shuffled: Boolean,
    /** The order before shuffling, restored when shuffle turns off. */
    private var unshuffledItems: MutableList<Int>,
) {
    constructor(items: List<Int>, startAt: Int = 0, source: QueueSource = QueueSource.SurahList) : this(
        items.toMutableList(),
        if (startAt in items.indices) startAt else 0,
        source,
        false,
        items.toMutableList(),
    )

    val items: List<Int> get() = _items
    val index: Int get() = _index
    val shuffled: Boolean get() = _shuffled

    val current: Int? get() = _items.getOrNull(_index)
    val isEmpty: Boolean get() = _items.isEmpty()

    /** Surahs after the current one, in play order. */
    val upcoming: List<Int> get() = if (_items.isEmpty()) emptyList() else _items.subList(_index + 1, _items.size).toList()

    val hasNext: Boolean get() = _index + 1 < _items.size
    val hasPrevious: Boolean get() = _index > 0

    /**
     * Moves to the next playable surah and returns it, or null at the end of the queue.
     * ONE and ALL only apply when a surah ends by itself (`userInitiated` false); pressing Next
     * always moves on, wrapping with ALL or ONE.
     */
    fun next(repeatMode: QuranRepeatMode, userInitiated: Boolean, unavailable: Set<Int> = emptySet()): Int? {
        if (_items.isEmpty()) return null
        val cur = current
        if (repeatMode == QuranRepeatMode.ONE && !userInitiated && cur != null && cur !in unavailable) return cur
        val wraps = repeatMode != QuranRepeatMode.OFF
        var i = _index
        repeat(_items.size) {
            i += 1
            if (i >= _items.size) {
                if (!wraps) return null
                i = 0
            }
            if (_items[i] !in unavailable) {
                _index = i
                return _items[i]
            }
        }
        return null
    }

    /**
     * Restarts the current surah (returns it) if [elapsedSeconds] is past the threshold or there's
     * nothing before it; otherwise moves to the previous playable surah.
     */
    fun previous(elapsedSeconds: Double, unavailable: Set<Int> = emptySet()): Int? {
        val cur = current ?: return null
        if (elapsedSeconds > RESTART_THRESHOLD) return cur
        var i = _index - 1
        while (i >= 0) {
            if (_items[i] !in unavailable) {
                _index = i
                return _items[i]
            }
            i -= 1
        }
        return cur
    }

    /** Jumps to the item at [position] in play order. */
    fun jump(position: Int) {
        if (position in _items.indices) _index = position
    }

    /**
     * Shuffles the items after the current one, which keeps playing. Turning shuffle off
     * restores the original order and keeps the current surah current.
     */
    fun setShuffle(on: Boolean, seed: Long) {
        if (on == _shuffled) return
        _shuffled = on
        val cur = current ?: return
        if (on) {
            unshuffledItems = _items.toMutableList()
            val rng = SplitMix64(seed)
            val rest = _items.subList(_index + 1, _items.size).toMutableList()
            // Fisher–Yates, from the end, like Swift's shuffled(using:).
            for (i in rest.indices.reversed()) {
                if (i == 0) break
                val j = rng.nextInt(i + 1)
                val t = rest[i]; rest[i] = rest[j]; rest[j] = t
            }
            _items = (_items.subList(0, _index + 1) + rest).toMutableList()
        } else {
            _items = unshuffledItems.toMutableList()
            // With duplicate surahs this lands on the first occurrence, which is close enough.
            _index = _items.indexOf(cur).coerceAtLeast(0)
        }
    }

    /** Inserts a surah right after the current one. */
    fun playNext(surah: Int) {
        val at = if (_items.isEmpty()) 0 else _index + 1
        _items.add(at, surah)
        unshuffledItems.add(minOf(at, unshuffledItems.size), surah)
    }

    fun append(surah: Int) {
        _items.add(surah)
        unshuffledItems.add(surah)
    }

    /** Removes the upcoming item at [position] (play order). The current surah can't be removed. */
    fun remove(position: Int) {
        if (position !in _items.indices || position == _index) return
        val surah = _items.removeAt(position)
        if (position < _index) _index -= 1
        val u = unshuffledItems.indexOf(surah)
        if (u >= 0) unshuffledItems.removeAt(u)
    }

    /** Moves one upcoming item from [from] to [to]; both are play-order positions after the current one. */
    fun moveUpcoming(from: Int, to: Int) {
        if (from <= _index || to <= _index || from !in _items.indices || to !in _items.indices) return
        val s = _items.removeAt(from)
        _items.add(to, s)
        if (!_shuffled) unshuffledItems = _items.toMutableList()
    }

    /** Drops everything after the current surah. */
    fun clearUpcoming() {
        if (_items.isEmpty()) return
        _items = _items.subList(0, _index + 1).toMutableList()
        unshuffledItems = _items.toMutableList()
    }

    fun copy(): QuranQueue = QuranQueue(_items.toMutableList(), _index, source, _shuffled, unshuffledItems.toMutableList())

    override fun equals(other: Any?): Boolean =
        other is QuranQueue && other._items == _items && other._index == _index && other.source == source &&
            other._shuffled == _shuffled && other.unshuffledItems == unshuffledItems

    override fun hashCode(): Int = _items.hashCode() * 31 + _index

    companion object {
        /** Previous restarts the current surah when it's further in than this, in seconds. */
        const val RESTART_THRESHOLD = 3.0
    }
}

/** SplitMix64: a tiny deterministic generator so shuffle tests are repeatable. */
internal class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int = java.lang.Long.remainderUnsigned(nextLong(), bound.toLong()).toInt()
}
