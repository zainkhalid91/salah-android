package salah.core.kindwords

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a kind word is shown. */
@Serializable
enum class KindWordCategory(val displayName: String) {
    /** A gentle line during the day, every few hours. */
    @SerialName("kindWord") KIND_WORD("Kind words"),
    /** Added to the early prayer reminder ("Asr in 10 minutes"). */
    @SerialName("beforePrayer") BEFORE_PRAYER("Before salah"),
    /** The "Have you prayed?" check-in after a prayer. */
    @SerialName("afterPrayer") AFTER_PRAYER("After salah"),
    /** Friday: Al-Kahf, Jumu'ah, salawat and the last hour after Asr. */
    @SerialName("friday") FRIDAY("Friday"),
}

/** One line in the library: a verse, a hadith or a short reminder, always with its source. */
@Serializable
data class KindWord(val id: String, val category: KindWordCategory, val text: String, val source: String) {
    /** Notification body: the line, then its source. */
    val notificationBody: String get() = "$text\n— $source"
}

/** When a du'a is said. */
@Serializable
enum class DuaMoment(val displayName: String) {
    @SerialName("morningEvening") MORNING_EVENING("Morning & evening"),
    @SerialName("beforeWork") BEFORE_WORK("Before work"),
    @SerialName("afterWork") AFTER_WORK("After work"),
    @SerialName("afterActivity") AFTER_ACTIVITY("After any activity"),
    @SerialName("everyday") EVERYDAY("Through the day"),
    @SerialName("hardship") HARDSHIP("In hardship"),
}

@Serializable
data class Dua(
    val id: String,
    val moment: DuaMoment,
    val label: String,
    val english: String,
    val transliteration: String,
    val source: String,
) {
    val notificationBody: String get() = "$english\n— $source"
}

/**
 * The bundled library, the same entries as the macOS app (roadmap/kind-words.md). English only.
 * Every entry names its source so it can be reviewed; hadith gradings differ between scholars.
 */
@Serializable
data class KindWordsLibrary(val words: List<KindWord>, val duas: List<Dua>) {
    fun words(category: KindWordCategory): List<KindWord> = words.filter { it.category == category }
    fun duas(moment: DuaMoment): List<Dua> = duas.filter { it.moment == moment }
    fun word(id: String): KindWord? = words.firstOrNull { it.id == id }

    /** The same du'a all day, a different one each day. */
    fun duaOfTheDay(date: java.time.LocalDate): Dua? = if (duas.isEmpty()) null else KindWordsPlanner.pick(duas, date, 7)

    /** The same kind word all day, a different one each day. */
    fun kindWordOfTheDay(date: java.time.LocalDate): KindWord? =
        words(KindWordCategory.KIND_WORD).takeIf { it.isNotEmpty() }?.let { KindWordsPlanner.pick(it, date, 11) }

    companion object {
        // Friday entries the planner looks up by id.
        const val JUMUAH_ID = "friday.jumuah"
        const val KAHF_ID = "friday.kahf"
        const val LAST_HOUR_ID = "friday.lastHour"
        const val SALAWAT_ID = "friday.salawat"

        val BUNDLED = KindWordsLibrary(
            words = listOf(
            KindWord("kind.remembrance", KindWordCategory.KIND_WORD, "“Verily, in the remembrance of Allah do hearts find rest.”", "Ar-Ra‘d 13:28"),
            KindWord("kind.ease", KindWordCategory.KIND_WORD, "“For indeed, with hardship comes ease.”", "Ash-Sharh 94:5"),
            KindWord("kind.burden", KindWordCategory.KIND_WORD, "“Allah does not burden a soul beyond what it can bear.”", "Al-Baqarah 2:286"),
            KindWord("kind.consistent", KindWordCategory.KIND_WORD, "“The most beloved deeds to Allah are those done consistently, even if small.”", "Bukhari 6464"),
            KindWord("kind.smile", KindWordCategory.KIND_WORD, "“Your smile for your brother is charity.”", "At-Tirmidhi 1956"),
            KindWord("kind.rememberMe", KindWordCategory.KIND_WORD, "“So remember Me; I will remember you.”", "Al-Baqarah 2:152"),
            KindWord("kind.withYou", KindWordCategory.KIND_WORD, "“And He is with you wherever you are.”", "Al-Hadid 57:4"),
            KindWord("kind.hope", KindWordCategory.KIND_WORD, "“Do not lose heart, and do not grieve.”", "Ali ‘Imran 3:139"),
            KindWord("kind.sufficient", KindWordCategory.KIND_WORD, "“Whoever puts their trust in Allah, He is sufficient for them.”", "At-Talaq 65:3"),
            KindWord("kind.body", KindWordCategory.KIND_WORD, "“Your body has a right over you.” Rest your eyes, drink some water.", "Bukhari 1975"),
            KindWord("kind.gentle", KindWordCategory.KIND_WORD, "“Allah is gentle and loves gentleness in all matters.”", "Bukhari 6927"),
            KindWord("kind.forsaken", KindWordCategory.KIND_WORD, "“Your Lord has not forsaken you, nor is He displeased.”", "Ad-Duha 93:3"),
            KindWord("kind.twoWords", KindWordCategory.KIND_WORD, "Two words, light on the tongue, heavy on the scale: SubhanAllahi wa bihamdihi, SubhanAllahil-‘Azim.", "Bukhari 6406"),
            KindWord("kind.patient", KindWordCategory.KIND_WORD, "“Indeed, Allah is with the patient.”", "Al-Baqarah 2:153"),
            KindWord("before.guard", KindWordCategory.BEFORE_PRAYER, "“Guard the prayers, and the middle prayer, and stand before Allah devoutly.”", "Al-Baqarah 2:238"),
            KindWord("before.patience", KindWordCategory.BEFORE_PRAYER, "“Seek help through patience and prayer.”", "Al-Baqarah 2:45"),
            KindWord("before.remembrance", KindWordCategory.BEFORE_PRAYER, "“Establish prayer for My remembrance.”", "Ta-Ha 20:14"),
            KindWord("before.prohibits", KindWordCategory.BEFORE_PRAYER, "“Indeed, prayer keeps one away from indecency and wrongdoing.”", "Al-‘Ankabut 29:45"),
            KindWord("before.first", KindWordCategory.BEFORE_PRAYER, "“The first deed a servant will be asked about on the Day of Judgement is the prayer.”", "An-Nasa’i 465"),
            KindWord("before.humble", KindWordCategory.BEFORE_PRAYER, "“Successful indeed are the believers — those who are humble in their prayer.”", "Al-Mu’minun 23:1–2"),
            KindWord("before.coolness", KindWordCategory.BEFORE_PRAYER, "“The coolness of my eyes is in prayer.”", "An-Nasa’i 3939"),
            KindWord("before.light", KindWordCategory.BEFORE_PRAYER, "“Prayer is light.” Step away for a few minutes — the work will wait.", "Muslim 223"),
            KindWord("after.tasbih", KindWordCategory.AFTER_PRAYER, "May it be accepted. Sit a moment: SubhanAllah 33, Alhamdulillah 33, Allahu Akbar 33, and complete the hundred with la ilaha illallah.", "Muslim 597"),
            KindWord("after.peace", KindWordCategory.AFTER_PRAYER, "Astaghfirullah three times, then: “O Allah, You are Peace, and from You is peace. Blessed are You, Owner of majesty and honour.”", "Muslim 591"),
            KindWord("after.kursi", KindWordCategory.AFTER_PRAYER, "A small habit with a great reward: Ayat al-Kursi after each prayer.", "An-Nasa’i, as-Sunan al-Kubra 9928"),
            KindWord("after.help", KindWordCategory.AFTER_PRAYER, "“O Allah, help me to remember You, to thank You, and to worship You well.”", "Abu Dawud 1522"),
            KindWord("after.missed", KindWordCategory.AFTER_PRAYER, "Not yet? It’s not too late — “whoever forgets a prayer, let him pray it when he remembers.”", "Bukhari 597"),
            KindWord(JUMUAH_ID, KindWordCategory.FRIDAY, "“When the call is made for prayer on Friday, hasten to the remembrance of Allah and leave off trade.”", "Al-Jumu‘ah 62:9"),
            KindWord(KAHF_ID, KindWordCategory.FRIDAY, "“Whoever reads Surah Al-Kahf on Friday, a light will shine for him between the two Fridays.”", "Al-Hakim 3392"),
            KindWord(LAST_HOUR_ID, KindWordCategory.FRIDAY, "“There is an hour on Friday in which no Muslim asks Allah for good but He gives it.” Seek it in the last hour after Asr.", "Abu Dawud 1048"),
            KindWord(SALAWAT_ID, KindWordCategory.FRIDAY, "“Send abundant blessings upon me on Friday, for your blessings are presented to me.”", "Abu Dawud 1047"),
            KindWord("friday.disperse", KindWordCategory.FRIDAY, "“When the prayer is over, disperse in the land and seek the bounty of Allah, and remember Allah often.”", "Al-Jumu‘ah 62:10"),
            ),
            duas = listOf(
            Dua(
                "dua.leavingHome", DuaMoment.BEFORE_WORK, "Leaving home",
                "“In the name of Allah, I rely on Allah. There is no might nor power except by Allah.”",
                "Bismillah, tawakkaltu ‘ala-llah, wa la hawla wa la quwwata illa billah.",
                "Abu Dawud 5095",
            ),
            Dua(
                "dua.startDay", DuaMoment.BEFORE_WORK, "Starting the day",
                "“O Allah, I ask You for beneficial knowledge, good provision, and deeds that are accepted.”",
                "Allahumma inni as’aluka ‘ilman nafi‘an, wa rizqan tayyiban, wa ‘amalan mutaqabbalan.",
                "Ibn Majah 925",
            ),
            Dua(
                "dua.ease", DuaMoment.BEFORE_WORK, "A hard task ahead",
                "“O Allah, nothing is easy except what You make easy, and You make the difficult easy if You will.”",
                "Allahumma la sahla illa ma ja‘altahu sahlan, wa anta taj‘alul-hazna idha shi’ta sahlan.",
                "Ibn Hibban 974",
            ),
            Dua(
                "dua.closing", DuaMoment.AFTER_WORK, "Closing a meeting or task",
                "“Glory be to You, O Allah, and praise. I bear witness there is no god but You. I seek Your forgiveness and turn to You.”",
                "Subhanaka-llahumma wa bihamdik, ashhadu an la ilaha illa ant, astaghfiruka wa atubu ilayk.",
                "Abu Dawud 4859",
            ),
            Dua(
                "dua.goesWell", DuaMoment.AFTER_WORK, "When something goes well",
                "“All praise is for Allah, by whose grace good things are completed.”",
                "Alhamdu lillahil-ladhi bi ni‘matihi tatimmus-salihat.",
                "Ibn Majah 3803",
            ),
            Dua(
                "dua.worry", DuaMoment.AFTER_WORK, "Worry about tomorrow",
                "“O Allah, I seek refuge in You from worry and grief, from weakness and laziness.”",
                "Allahumma inni a‘udhu bika minal-hammi wal-hazan, wal-‘ajzi wal-kasal.",
                "Bukhari 6369",
            ),
            Dua(
                "dua.afterEating", DuaMoment.AFTER_ACTIVITY, "After eating",
                "“All praise is for Allah who fed me this and provided it for me, without any might or power from me.”",
                "Alhamdu lillahil-ladhi at‘amani hadha wa razaqanihi min ghayri hawlin minni wa la quwwah.",
                "At-Tirmidhi 3458",
            ),
            Dua(
                "dua.goodBoth", DuaMoment.AFTER_ACTIVITY, "For this life and the next",
                "“Our Lord, give us good in this world and good in the Hereafter, and protect us from the punishment of the Fire.”",
                "Rabbana atina fid-dunya hasanah, wa fil-akhirati hasanah, wa qina ‘adhaban-nar.",
                "Al-Baqarah 2:201",
            ),
            Dua(
                "dua.sleep", DuaMoment.MORNING_EVENING, "Before sleep",
                "“In Your name, O Allah, I die and I live.”",
                "Bismika-llahumma amutu wa ahya.",
                "Bukhari 6324",
            ),
            Dua(
                "dua.waking", DuaMoment.MORNING_EVENING, "Waking up",
                "“All praise is for Allah who gave us life after causing us to die, and to Him is the return.”",
                "Alhamdu lillahil-ladhi ahyana ba‘da ma amatana wa ilayhin-nushur.",
                "Bukhari 6312",
            ),
            Dua(
                "dua.morning", DuaMoment.MORNING_EVENING, "In the morning",
                "“O Allah, by You we enter the morning and by You we enter the evening, by You we live and by You we die, and to You is the resurrection.”",
                "Allahumma bika asbahna wa bika amsayna, wa bika nahya wa bika namutu, wa ilaykan-nushur.",
                "At-Tirmidhi 3391",
            ),
            Dua(
                "dua.evening", DuaMoment.MORNING_EVENING, "In the evening",
                "“O Allah, by You we enter the evening and by You we enter the morning, by You we live and by You we die, and to You is the final return.”",
                "Allahumma bika amsayna wa bika asbahna, wa bika nahya wa bika namutu, wa ilaykal-masir.",
                "At-Tirmidhi 3391",
            ),
            Dua(
                "dua.beforeEating", DuaMoment.EVERYDAY, "Forgot Bismillah before eating",
                "“In the name of Allah, at its beginning and at its end.”",
                "Bismillahi awwalahu wa akhirah.",
                "Abu Dawud 3767",
            ),
            Dua(
                "dua.afterWudu", DuaMoment.EVERYDAY, "After wudu",
                "“I bear witness that there is no god but Allah alone, without partner, and I bear witness that Muhammad is His servant and Messenger.”",
                "Ashhadu an la ilaha illallahu wahdahu la sharika lah, wa ashhadu anna Muhammadan ‘abduhu wa rasuluh.",
                "Muslim 234",
            ),
            Dua(
                "dua.travel", DuaMoment.EVERYDAY, "Setting off on a journey",
                "“Glory be to the One who has placed this at our service, for we could never have done it ourselves. And to our Lord we will surely return.”",
                "Subhanal-ladhi sakhkhara lana hadha wa ma kunna lahu muqrinin, wa inna ila rabbina lamunqalibun.",
                "Az-Zukhruf 43:13–14 · Muslim 1342",
            ),
            Dua(
                "dua.knowledge", DuaMoment.EVERYDAY, "Before study",
                "“My Lord, increase me in knowledge.”",
                "Rabbi zidni ‘ilma.",
                "Ta-Ha 20:114",
            ),
            Dua(
                "dua.parents", DuaMoment.EVERYDAY, "For your parents",
                "“My Lord, have mercy on them as they raised me when I was small.”",
                "Rabbir-hamhuma kama rabbayani saghira.",
                "Al-Isra 17:24",
            ),
            Dua(
                "dua.distress", DuaMoment.HARDSHIP, "In distress",
                "“There is no god but Allah, the Mighty, the Forbearing. There is no god but Allah, Lord of the Mighty Throne. There is no god but Allah, Lord of the heavens, Lord of the earth, and Lord of the Noble Throne.”",
                "La ilaha illallahul-‘Azimul-Halim, la ilaha illallahu Rabbul-‘Arshil-‘Azim, la ilaha illallahu Rabbus-samawati wa Rabbul-ardi wa Rabbul-‘Arshil-Karim.",
                "Bukhari 6346",
            ),
            Dua(
                "dua.yunus", DuaMoment.HARDSHIP, "The du'a of Yunus",
                "“There is no god but You. Glory be to You. Indeed, I have been of the wrongdoers.”",
                "La ilaha illa anta subhanaka inni kuntu minaz-zalimin.",
                "Al-Anbiya 21:87 · At-Tirmidhi 3505",
            ),
            Dua(
                "dua.sufficient", DuaMoment.HARDSHIP, "When it feels like too much",
                "“Allah is sufficient for us, and He is the best disposer of affairs.”",
                "Hasbunallahu wa ni‘mal-wakil.",
                "Ali ‘Imran 3:173",
            ),
            ),
        )
    }
}
