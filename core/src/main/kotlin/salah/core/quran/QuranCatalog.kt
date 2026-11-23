package salah.core.quran

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Revelation(val displayName: String) {
    MECCAN("Meccan"),
    MEDINAN("Medinan"),
}

data class Surah(
    val number: Int,
    /** Transliterated name, e.g. "Al-Faatiha". */
    val name: String,
    val arabicName: String,
    /** English meaning, e.g. "The Opening". */
    val meaning: String,
    val ayahCount: Int,
    val revelation: Revelation,
)

/** One recitation: a reciter in one style, served as `<serverBase><NNN>.mp3`. */
data class Reciter(
    val id: String,
    val name: String,
    /** "Murattal" or "Mujawwad". */
    val style: String,
    val serverBase: String,
    /** Surah numbers this recitation has. Most have all 114. */
    val surahs: Set<Int>,
) {
    val displayName: String get() = "$name · $style"
}

/** Playlists that ship with the app and can't be edited. */
@Serializable
enum class BuiltInPlaylist(val displayName: String) {
    @SerialName("fullQuran") FULL_QURAN("Full Quran"),
    @SerialName("juzAmma") JUZ_AMMA("Juz 'Amma");

    val surahs: List<Int> get() = if (this == FULL_QURAN) (1..114).toList() else (78..114).toList()
}

/**
 * The bundled, read-only catalog: the 114 surahs and a curated set of reciters, the same as the
 * macOS app's QuranCatalog. Surah metadata comes from Tanzil (via api.alquran.cloud). Audio
 * streams from mp3quran.net's CDN.
 */
object QuranCatalog {
    const val SURAH_COUNT = 114

    fun surah(number: Int): Surah? = if (number in 1..SURAH_COUNT) surahs[number - 1] else null

    /** The reciter with this ID, or the first reciter when it's unknown (e.g. removed from the catalog). */
    fun reciter(id: String): Reciter = reciters.firstOrNull { it.id == id } ?: reciters[0]

    /** The MP3 for this surah, or null when the reciter doesn't have it. */
    fun audioUrl(reciter: Reciter, surah: Int): String? =
        if (surah in reciter.surahs) reciter.serverBase + String.format(java.util.Locale.ROOT, "%03d.mp3", surah) else null

    val reciters: List<Reciter> = listOf(
        reciter("alafasy", "Mishary Alafasy", "Murattal", "mishary-alafasy/r1"),
        reciter("sudais", "Abdul Rahman Al-Sudais", "Murattal", "abdulrahman-sudais/r1"),
        reciter("ghamdi", "Saad Al-Ghamdi", "Murattal", "saad-ghamdi/r1"),
        reciter("husary", "Mahmoud Khalil Al-Husary", "Murattal", "mahmoud-husary/r1"),
        reciter("muaiqly", "Maher Al-Muaiqly", "Murattal", "maher-muaiqly/r1"),
        reciter("abdulbasit", "Abdul Basit Abdul Samad", "Murattal", "abdulbasit-abdulsamad/r3"),
    )

    private fun reciter(id: String, name: String, style: String, path: String) =
        Reciter(id, name, style, "https://cdn.mp3quran.net/audio/$path/", (1..SURAH_COUNT).toSet())

    /** Matches the number, transliterated name or English meaning, ignoring case and punctuation. */
    fun matches(surah: Surah, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (q.toIntOrNull() == surah.number) return true
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val n = norm(q)
        return norm(surah.name).contains(n) || norm(surah.meaning).contains(n) || surah.arabicName.contains(q)
    }

    val surahs: List<Surah> = listOf(
        Surah(1, "Al-Faatiha", "ٱلْفَاتِحَةِ", "The Opening", 7, Revelation.MECCAN),
        Surah(2, "Al-Baqara", "البَقَرَةِ", "The Cow", 286, Revelation.MEDINAN),
        Surah(3, "Aal-i-Imraan", "آلِ عِمۡرَانَ", "The Family of Imraan", 200, Revelation.MEDINAN),
        Surah(4, "An-Nisaa", "النِّسَاءِ", "The Women", 176, Revelation.MEDINAN),
        Surah(5, "Al-Maaida", "المَائـِدَةِ", "The Table", 120, Revelation.MEDINAN),
        Surah(6, "Al-An'aam", "الأَنۡعَامِ", "The Cattle", 165, Revelation.MECCAN),
        Surah(7, "Al-A'raaf", "الأَعۡرَافِ", "The Heights", 206, Revelation.MECCAN),
        Surah(8, "Al-Anfaal", "الأَنفَالِ", "The Spoils of War", 75, Revelation.MEDINAN),
        Surah(9, "At-Tawba", "التَّوۡبَةِ", "The Repentance", 129, Revelation.MEDINAN),
        Surah(10, "Yunus", "يُونُسَ", "Jonas", 109, Revelation.MECCAN),
        Surah(11, "Hud", "هُودٍ", "Hud", 123, Revelation.MECCAN),
        Surah(12, "Yusuf", "يُوسُفَ", "Joseph", 111, Revelation.MECCAN),
        Surah(13, "Ar-Ra'd", "الرَّعۡدِ", "The Thunder", 43, Revelation.MEDINAN),
        Surah(14, "Ibrahim", "إِبۡرَاهِيمَ", "Abraham", 52, Revelation.MECCAN),
        Surah(15, "Al-Hijr", "الحِجۡرِ", "The Rock", 99, Revelation.MECCAN),
        Surah(16, "An-Nahl", "النَّحۡلِ", "The Bee", 128, Revelation.MECCAN),
        Surah(17, "Al-Israa", "الإِسۡرَاءِ", "The Night Journey", 111, Revelation.MECCAN),
        Surah(18, "Al-Kahf", "الكَهۡفِ", "The Cave", 110, Revelation.MECCAN),
        Surah(19, "Maryam", "مَرۡيَمَ", "Mary", 98, Revelation.MECCAN),
        Surah(20, "Taa-Haa", "طه", "Taa-Haa", 135, Revelation.MECCAN),
        Surah(21, "Al-Anbiyaa", "الأَنبِيَاءِ", "The Prophets", 112, Revelation.MECCAN),
        Surah(22, "Al-Hajj", "الحَجِّ", "The Pilgrimage", 78, Revelation.MEDINAN),
        Surah(23, "Al-Muminoon", "المُؤۡمِنُونَ", "The Believers", 118, Revelation.MECCAN),
        Surah(24, "An-Noor", "النُّورِ", "The Light", 64, Revelation.MEDINAN),
        Surah(25, "Al-Furqaan", "الفُرۡقَانِ", "The Criterion", 77, Revelation.MECCAN),
        Surah(26, "Ash-Shu'araa", "الشُّعَرَاءِ", "The Poets", 227, Revelation.MECCAN),
        Surah(27, "An-Naml", "النَّمۡلِ", "The Ant", 93, Revelation.MECCAN),
        Surah(28, "Al-Qasas", "القَصَصِ", "The Stories", 88, Revelation.MECCAN),
        Surah(29, "Al-Ankaboot", "العَنكَبُوتِ", "The Spider", 69, Revelation.MECCAN),
        Surah(30, "Ar-Room", "الرُّومِ", "The Romans", 60, Revelation.MECCAN),
        Surah(31, "Luqman", "لُقۡمَانَ", "Luqman", 34, Revelation.MECCAN),
        Surah(32, "As-Sajda", "السَّجۡدَةِ", "The Prostration", 30, Revelation.MECCAN),
        Surah(33, "Al-Ahzaab", "الأَحۡزَابِ", "The Clans", 73, Revelation.MEDINAN),
        Surah(34, "Saba", "سَبَإٍ", "Sheba", 54, Revelation.MECCAN),
        Surah(35, "Faatir", "فَاطِرٍ", "The Originator", 45, Revelation.MECCAN),
        Surah(36, "Yaseen", "يسٓ", "Yaseen", 83, Revelation.MECCAN),
        Surah(37, "As-Saaffaat", "الصَّافَّاتِ", "Those drawn up in Ranks", 182, Revelation.MECCAN),
        Surah(38, "Saad", "صٓ", "The letter Saad", 88, Revelation.MECCAN),
        Surah(39, "Az-Zumar", "الزُّمَرِ", "The Groups", 75, Revelation.MECCAN),
        Surah(40, "Ghafir", "غَافِرٍ", "The Forgiver", 85, Revelation.MECCAN),
        Surah(41, "Fussilat", "فُصِّلَتۡ", "Explained in detail", 54, Revelation.MECCAN),
        Surah(42, "Ash-Shura", "الشُّورَىٰ", "Consultation", 53, Revelation.MECCAN),
        Surah(43, "Az-Zukhruf", "الزُّخۡرُفِ", "Ornaments of gold", 89, Revelation.MECCAN),
        Surah(44, "Ad-Dukhaan", "الدُّخَانِ", "The Smoke", 59, Revelation.MECCAN),
        Surah(45, "Al-Jaathiya", "الجَاثِيَةِ", "Crouching", 37, Revelation.MECCAN),
        Surah(46, "Al-Ahqaf", "الأَحۡقَافِ", "The Dunes", 35, Revelation.MECCAN),
        Surah(47, "Muhammad", "مُحَمَّدٍ", "Muhammad", 38, Revelation.MEDINAN),
        Surah(48, "Al-Fath", "الفَتۡحِ", "The Victory", 29, Revelation.MEDINAN),
        Surah(49, "Al-Hujuraat", "الحُجُرَاتِ", "The Inner Apartments", 18, Revelation.MEDINAN),
        Surah(50, "Qaaf", "قٓ", "The letter Qaaf", 45, Revelation.MECCAN),
        Surah(51, "Adh-Dhaariyat", "الذَّارِيَاتِ", "The Winnowing Winds", 60, Revelation.MECCAN),
        Surah(52, "At-Tur", "الطُّورِ", "The Mount", 49, Revelation.MECCAN),
        Surah(53, "An-Najm", "النَّجۡمِ", "The Star", 62, Revelation.MECCAN),
        Surah(54, "Al-Qamar", "القَمَرِ", "The Moon", 55, Revelation.MECCAN),
        Surah(55, "Ar-Rahmaan", "الرَّحۡمَٰن", "The Beneficent", 78, Revelation.MEDINAN),
        Surah(56, "Al-Waaqia", "الوَاقِعَةِ", "The Inevitable", 96, Revelation.MECCAN),
        Surah(57, "Al-Hadid", "الحَدِيدِ", "The Iron", 29, Revelation.MEDINAN),
        Surah(58, "Al-Mujaadila", "المُجَادلَةِ", "The Pleading Woman", 22, Revelation.MEDINAN),
        Surah(59, "Al-Hashr", "الحَشۡرِ", "The Exile", 24, Revelation.MEDINAN),
        Surah(60, "Al-Mumtahana", "المُمۡتَحنَةِ", "She that is to be examined", 13, Revelation.MEDINAN),
        Surah(61, "As-Saff", "الصَّفِّ", "The Ranks", 14, Revelation.MEDINAN),
        Surah(62, "Al-Jumu'a", "الجُمُعَةِ", "Friday", 11, Revelation.MEDINAN),
        Surah(63, "Al-Munaafiqoon", "المُنَافِقُونَ", "The Hypocrites", 11, Revelation.MEDINAN),
        Surah(64, "At-Taghaabun", "التَّغَابُنِ", "Mutual Disillusion", 18, Revelation.MEDINAN),
        Surah(65, "At-Talaaq", "الطَّلَاقِ", "Divorce", 12, Revelation.MEDINAN),
        Surah(66, "At-Tahrim", "التَّحۡرِيمِ", "The Prohibition", 12, Revelation.MEDINAN),
        Surah(67, "Al-Mulk", "المُلۡكِ", "The Sovereignty", 30, Revelation.MECCAN),
        Surah(68, "Al-Qalam", "القَلَمِ", "The Pen", 52, Revelation.MECCAN),
        Surah(69, "Al-Haaqqa", "الحَاقَّةِ", "The Reality", 52, Revelation.MECCAN),
        Surah(70, "Al-Ma'aarij", "المَعَارِجِ", "The Ascending Stairways", 44, Revelation.MECCAN),
        Surah(71, "Nooh", "نُوحٍ", "Noah", 28, Revelation.MECCAN),
        Surah(72, "Al-Jinn", "الجِنِّ", "The Jinn", 28, Revelation.MECCAN),
        Surah(73, "Al-Muzzammil", "المُزَّمِّلِ", "The Enshrouded One", 20, Revelation.MECCAN),
        Surah(74, "Al-Muddaththir", "المُدَّثِّرِ", "The Cloaked One", 56, Revelation.MECCAN),
        Surah(75, "Al-Qiyaama", "القِيَامَةِ", "The Resurrection", 40, Revelation.MECCAN),
        Surah(76, "Al-Insaan", "الإِنسَانِ", "Man", 31, Revelation.MEDINAN),
        Surah(77, "Al-Mursalaat", "المُرۡسَلَاتِ", "The Emissaries", 50, Revelation.MECCAN),
        Surah(78, "An-Naba", "النَّبَإِ", "The Announcement", 40, Revelation.MECCAN),
        Surah(79, "An-Naazi'aat", "النَّازِعَاتِ", "Those who drag forth", 46, Revelation.MECCAN),
        Surah(80, "Abasa", "عَبَسَ", "He frowned", 42, Revelation.MECCAN),
        Surah(81, "At-Takwir", "التَّكۡوِيرِ", "The Overthrowing", 29, Revelation.MECCAN),
        Surah(82, "Al-Infitaar", "الانفِطَارِ", "The Cleaving", 19, Revelation.MECCAN),
        Surah(83, "Al-Mutaffifin", "المُطَفِّفِينَ", "Defrauding", 36, Revelation.MECCAN),
        Surah(84, "Al-Inshiqaaq", "الانشِقَاقِ", "The Splitting Open", 25, Revelation.MECCAN),
        Surah(85, "Al-Burooj", "البُرُوجِ", "The Constellations", 22, Revelation.MECCAN),
        Surah(86, "At-Taariq", "الطَّارِقِ", "The Morning Star", 17, Revelation.MECCAN),
        Surah(87, "Al-A'laa", "الأَعۡلَىٰ", "The Most High", 19, Revelation.MECCAN),
        Surah(88, "Al-Ghaashiya", "الغَاشِيَةِ", "The Overwhelming", 26, Revelation.MECCAN),
        Surah(89, "Al-Fajr", "الفَجۡرِ", "The Dawn", 30, Revelation.MECCAN),
        Surah(90, "Al-Balad", "البَلَدِ", "The City", 20, Revelation.MECCAN),
        Surah(91, "Ash-Shams", "الشَّمۡسِ", "The Sun", 15, Revelation.MECCAN),
        Surah(92, "Al-Lail", "اللَّيۡلِ", "The Night", 21, Revelation.MECCAN),
        Surah(93, "Ad-Dhuhaa", "الضُّحَىٰ", "The Morning Hours", 11, Revelation.MECCAN),
        Surah(94, "Ash-Sharh", "الشَّرۡحِ", "The Consolation", 8, Revelation.MECCAN),
        Surah(95, "At-Tin", "التِّينِ", "The Fig", 8, Revelation.MECCAN),
        Surah(96, "Al-Alaq", "العَلَقِ", "The Clot", 19, Revelation.MECCAN),
        Surah(97, "Al-Qadr", "القَدۡرِ", "The Power, Fate", 5, Revelation.MECCAN),
        Surah(98, "Al-Bayyina", "البَيِّنَةِ", "The Evidence", 8, Revelation.MEDINAN),
        Surah(99, "Az-Zalzala", "الزَّلۡزَلَةِ", "The Earthquake", 8, Revelation.MEDINAN),
        Surah(100, "Al-Aadiyaat", "العَادِيَاتِ", "The Chargers", 11, Revelation.MECCAN),
        Surah(101, "Al-Qaari'a", "القَارِعَةِ", "The Calamity", 11, Revelation.MECCAN),
        Surah(102, "At-Takaathur", "التَّكَاثُرِ", "Competition", 8, Revelation.MECCAN),
        Surah(103, "Al-Asr", "العَصۡرِ", "The Declining Day, Epoch", 3, Revelation.MECCAN),
        Surah(104, "Al-Humaza", "الهُمَزَةِ", "The Traducer", 9, Revelation.MECCAN),
        Surah(105, "Al-Fil", "الفِيلِ", "The Elephant", 5, Revelation.MECCAN),
        Surah(106, "Quraish", "قُرَيۡشٍ", "Quraysh", 4, Revelation.MECCAN),
        Surah(107, "Al-Maa'un", "المَاعُونِ", "Almsgiving", 7, Revelation.MECCAN),
        Surah(108, "Al-Kawthar", "الكَوۡثَرِ", "Abundance", 3, Revelation.MECCAN),
        Surah(109, "Al-Kaafiroon", "الكَافِرُونَ", "The Disbelievers", 6, Revelation.MECCAN),
        Surah(110, "An-Nasr", "النَّصۡرِ", "Divine Support", 3, Revelation.MEDINAN),
        Surah(111, "Al-Masad", "المَسَدِ", "The Palm Fibre", 5, Revelation.MECCAN),
        Surah(112, "Al-Ikhlaas", "الإِخۡلَاصِ", "Sincerity", 4, Revelation.MECCAN),
        Surah(113, "Al-Falaq", "الفَلَقِ", "The Dawn", 5, Revelation.MECCAN),
        Surah(114, "An-Naas", "النَّاسِ", "Mankind", 6, Revelation.MECCAN),
    )
}
