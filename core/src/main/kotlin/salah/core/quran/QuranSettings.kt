package salah.core.quran

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** A user playlist: a name and surah numbers in order. Duplicates are allowed. */
@Serializable
data class QuranPlaylist(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val name: String = "Playlist",
    val surahs: List<Int> = emptyList(),
) {
    internal fun normalized() = copy(surahs = surahs.filter { it in 1..QuranCatalog.SURAH_COUNT })
}

/**
 * The Quran player's settings, in the same JSON shape as the macOS app's `quran` key.
 * Runtime state (the queue and play position) is kept apart from the config.
 */
@Serializable
data class QuranSettings(
    val reciterId: String = QuranCatalog.reciters[0].id,
    val playlists: List<QuranPlaylist> = emptyList(),
    @SerialName("repeat") val repeatMode: QuranRepeatMode = QuranRepeatMode.OFF,
    val shuffle: Boolean = false,
    val pauseAtPrayerTime: Boolean = true,
    /** On macOS: the mini player in the menu bar. On Android: the mini player above the tabs. */
    val showInMenuBar: Boolean = true,
) {
    val reciter: Reciter get() = QuranCatalog.reciter(reciterId)

    fun playlist(id: String): QuranPlaylist? = playlists.firstOrNull { it.id == id }

    internal fun normalized() = copy(
        // An unknown reciter (e.g. removed from the catalog) falls back to the first one.
        reciterId = QuranCatalog.reciter(reciterId).id,
        playlists = playlists.map { it.normalized() },
    )
}
