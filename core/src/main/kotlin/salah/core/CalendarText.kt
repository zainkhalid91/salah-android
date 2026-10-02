package salah.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Languages the calendar is translated into. Anything else falls back to English. */
enum class CalLang(val rtl: Boolean = false) { EN, AR(rtl = true), UR(rtl = true), ID, MS, TR, FR }

/** Calendar UI strings. */
enum class CalKey {
    CALENDAR, HIJRI, GREGORIAN, TODAY, CONVERTER, HIJRI_TO_GREGORIAN, GREGORIAN_TO_HIJRI, CONVERT,
    DAY, MONTH, YEAR, EVENTS_THIS_YEAR, NO_EVENTS, TOMORROW, IN_DAYS, DAYS_AGO, WHITE_DAYS,
    NEW_MONTH_BEGINS, BEGINS_TONIGHT, ALERTS, ALERT_NEW_MONTH, ALERT_SPECIAL_DAYS, ALERT_WHITE_DAYS,
    ALERT_TIME, EVENING_BEFORE, MORNING_OF, MAIN_CALENDAR, OUT_OF_RANGE, NEXT_EVENT,
}

/**
 * Localised calendar text: Hijri month and event names, UI labels, and digits.
 * Gregorian month and weekday names come from java.time, which already knows
 * every locale. Shared by the Android and Windows apps.
 */
object CalendarText {
    fun lang(locale: Locale = Locale.getDefault()): CalLang = when (locale.language) {
        "ar" -> CalLang.AR
        "ur" -> CalLang.UR
        "id", "in" -> CalLang.ID
        "ms" -> CalLang.MS
        "tr" -> CalLang.TR
        "fr" -> CalLang.FR
        else -> CalLang.EN
    }

    /** First day of the week for a locale: Saturday in much of the Arab world, Sunday or Monday elsewhere. */
    fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    /** Replaces 0-9 with Arabic-Indic digits for Arabic and Extended Arabic-Indic for Urdu. */
    fun digits(text: String, lang: CalLang): String {
        val zero = when (lang) {
            CalLang.AR -> '٠'
            CalLang.UR -> '۰'
            else -> return text
        }
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(if (ch in '0'..'9') zero + (ch - '0') else ch)
        return sb.toString()
    }

    fun number(n: Int, lang: CalLang): String = digits(n.toString(), lang)

    fun hijriMonth(month: Int, lang: CalLang): String = HIJRI_MONTHS.getValue(lang)[month - 1]

    fun gregorianMonth(month: Int, locale: Locale): String =
        Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    /** Very short weekday names for a grid header ("M", "ن"). */
    fun weekdayShort(day: DayOfWeek, locale: Locale): String {
        val style = if (lang(locale).rtl) TextStyle.NARROW else TextStyle.SHORT
        return day.getDisplayName(style, locale)
    }

    fun weekdayLong(day: DayOfWeek, locale: Locale): String =
        day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    /** "12 Ramadan 1448 AH" */
    fun hijri(h: HijriDate, lang: CalLang): String =
        "${number(h.day, lang)} ${hijriMonth(h.month, lang)} ${number(h.year, lang)} ${ERA.getValue(lang)}"

    /** "Rajab 1448" */
    fun hijriMonthYear(year: Int, month: Int, lang: CalLang): String = "${hijriMonth(month, lang)} ${number(year, lang)}"

    /** "Friday, 2 October 2026" in the locale's words and digits. */
    fun gregorian(date: LocalDate, locale: Locale): String {
        val lang = lang(locale)
        val comma = if (lang.rtl) "،" else ","
        return "${weekdayLong(date.dayOfWeek, locale)}$comma ${number(date.dayOfMonth, lang)} ${gregorianMonth(date.monthValue, locale)} ${number(date.year, lang)}"
    }

    fun event(e: IslamicEvent, lang: CalLang): String = EVENTS.getValue(e)[lang.ordinal]

    fun text(key: CalKey, lang: CalLang): String = digits(UI.getValue(key)[lang.ordinal], lang)

    /** "in 3 days", "tomorrow", "today" or "5 days ago". */
    fun relative(days: Long, lang: CalLang): String = when {
        days == 0L -> text(CalKey.TODAY, lang)
        days == 1L -> text(CalKey.TOMORROW, lang)
        days > 1 -> text(CalKey.IN_DAYS, lang).replace("%d", number(days.toInt(), lang))
        else -> text(CalKey.DAYS_AGO, lang).replace("%d", number((-days).toInt(), lang))
    }

    private val ERA = mapOf(
        CalLang.EN to "AH", CalLang.AR to "هـ", CalLang.UR to "ھ", CalLang.ID to "H",
        CalLang.MS to "H", CalLang.TR to "H", CalLang.FR to "AH",
    )

    private val HIJRI_MONTHS = mapOf(
        CalLang.EN to listOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"),
        CalLang.AR to listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"),
        CalLang.UR to listOf("محرم", "صفر", "ربیع الاول", "ربیع الثانی", "جمادی الاول", "جمادی الثانی", "رجب", "شعبان", "رمضان", "شوال", "ذوالقعدہ", "ذوالحجہ"),
        CalLang.ID to listOf("Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir", "Rajab", "Sya'ban", "Ramadan", "Syawal", "Dzulqa'dah", "Dzulhijjah"),
        CalLang.MS to listOf("Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"),
        CalLang.TR to listOf("Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce"),
        CalLang.FR to listOf("Mouharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Joumada al-Oula", "Joumada al-Thania", "Rajab", "Chaabane", "Ramadan", "Chawwal", "Dhou al-Qi'da", "Dhou al-Hijja"),
    )

    // Order: EN, AR, UR, ID, MS, TR, FR
    private val EVENTS = mapOf(
        IslamicEvent.NEW_YEAR to listOf("Islamic New Year", "رأس السنة الهجرية", "اسلامی نیا سال", "Tahun Baru Islam", "Awal Muharam", "Hicri Yılbaşı", "Nouvel An de l'Hégire"),
        IslamicEvent.ASHURA to listOf("Day of Ashura", "يوم عاشوراء", "یوم عاشور", "Hari Asyura", "Hari Asyura", "Aşure Günü", "Jour d'Achoura"),
        IslamicEvent.MAWLID to listOf("Mawlid an-Nabi", "المولد النبوي", "میلاد النبی", "Maulid Nabi", "Maulidur Rasul", "Mevlid Kandili", "Mawlid an-Nabi"),
        IslamicEvent.ISRA_MIRAJ to listOf("Isra and Mi'raj", "الإسراء والمعراج", "شب معراج", "Isra Mi'raj", "Israk dan Mikraj", "Miraç Kandili", "Al-Isra wal-Miraj"),
        IslamicEvent.MID_SHABAN to listOf("Mid-Sha'ban (Shab-e-Barat)", "ليلة النصف من شعبان", "شب برات", "Nisfu Sya'ban", "Nisfu Syaaban", "Berat Kandili", "Mi-Chaabane"),
        IslamicEvent.RAMADAN_START to listOf("First day of Ramadan", "أول أيام رمضان", "رمضان کا پہلا دن", "Awal Ramadan", "Awal Ramadan", "Ramazan'ın ilk günü", "Premier jour du Ramadan"),
        IslamicEvent.LAST_TEN_NIGHTS to listOf("Last ten nights of Ramadan", "العشر الأواخر من رمضان", "رمضان کا آخری عشرہ", "Sepuluh malam terakhir Ramadan", "Sepuluh malam terakhir Ramadan", "Ramazan'ın son on gecesi", "Dix dernières nuits du Ramadan"),
        IslamicEvent.LAYLAT_AL_QADR to listOf("Laylat al-Qadr (27th night)", "ليلة القدر", "شب قدر", "Lailatul Qadar", "Lailatulqadar", "Kadir Gecesi", "Laylat al-Qadr"),
        IslamicEvent.EID_AL_FITR to listOf("Eid al-Fitr", "عيد الفطر", "عید الفطر", "Idul Fitri", "Hari Raya Aidilfitri", "Ramazan Bayramı", "Aïd al-Fitr"),
        IslamicEvent.DHUL_HIJJAH_TEN to listOf("First ten days of Dhu al-Hijjah", "العشر الأوائل من ذي الحجة", "ذوالحجہ کا پہلا عشرہ", "Sepuluh hari pertama Dzulhijjah", "Sepuluh hari pertama Zulhijah", "Zilhicce'nin ilk on günü", "Dix premiers jours de Dhou al-Hijja"),
        IslamicEvent.ARAFAH to listOf("Day of Arafah", "يوم عرفة", "یوم عرفہ", "Hari Arafah", "Hari Arafah", "Arefe Günü", "Jour d'Arafat"),
        IslamicEvent.EID_AL_ADHA to listOf("Eid al-Adha", "عيد الأضحى", "عید الاضحیٰ", "Idul Adha", "Hari Raya Aidiladha", "Kurban Bayramı", "Aïd al-Adha"),
        IslamicEvent.TASHREEQ to listOf("Days of Tashreeq", "أيام التشريق", "ایام تشریق", "Hari Tasyrik", "Hari Tasyrik", "Teşrik Günleri", "Jours de Tachriq"),
    )

    // Order: EN, AR, UR, ID, MS, TR, FR
    private val UI = mapOf(
        CalKey.CALENDAR to listOf("Calendar", "التقويم", "کیلنڈر", "Kalender", "Kalendar", "Takvim", "Calendrier"),
        CalKey.HIJRI to listOf("Hijri", "هجري", "ہجری", "Hijriah", "Hijrah", "Hicri", "Hégire"),
        CalKey.GREGORIAN to listOf("Gregorian", "ميلادي", "عیسوی", "Masehi", "Masihi", "Miladi", "Grégorien"),
        CalKey.TODAY to listOf("Today", "اليوم", "آج", "Hari ini", "Hari ini", "Bugün", "Aujourd'hui"),
        CalKey.CONVERTER to listOf("Date converter", "تحويل التاريخ", "تاریخ تبدیل کریں", "Konversi tanggal", "Penukar tarikh", "Tarih çevirici", "Convertisseur de dates"),
        CalKey.HIJRI_TO_GREGORIAN to listOf("Hijri to Gregorian", "من الهجري إلى الميلادي", "ہجری سے عیسوی", "Hijriah ke Masehi", "Hijrah ke Masihi", "Hicri'den Miladi'ye", "Hégire vers grégorien"),
        CalKey.GREGORIAN_TO_HIJRI to listOf("Gregorian to Hijri", "من الميلادي إلى الهجري", "عیسوی سے ہجری", "Masehi ke Hijriah", "Masihi ke Hijrah", "Miladi'den Hicri'ye", "Grégorien vers Hégire"),
        CalKey.CONVERT to listOf("Convert", "تحويل", "تبدیل کریں", "Konversi", "Tukar", "Çevir", "Convertir"),
        CalKey.DAY to listOf("Day", "اليوم", "دن", "Hari", "Hari", "Gün", "Jour"),
        CalKey.MONTH to listOf("Month", "الشهر", "مہینہ", "Bulan", "Bulan", "Ay", "Mois"),
        CalKey.YEAR to listOf("Year", "السنة", "سال", "Tahun", "Tahun", "Yıl", "Année"),
        CalKey.EVENTS_THIS_YEAR to listOf("Islamic dates this year", "المناسبات الإسلامية هذا العام", "اس سال کی اسلامی تاریخیں", "Hari besar Islam tahun ini", "Tarikh penting Islam tahun ini", "Bu yılın İslami günleri", "Dates islamiques de l'année"),
        CalKey.NO_EVENTS to listOf("No special dates", "لا توجد مناسبات", "کوئی خاص دن نہیں", "Tidak ada hari besar", "Tiada tarikh istimewa", "Özel gün yok", "Aucune date particulière"),
        CalKey.TOMORROW to listOf("Tomorrow", "غدًا", "کل", "Besok", "Esok", "Yarın", "Demain"),
        CalKey.IN_DAYS to listOf("In %d days", "بعد %d يوم", "%d دن میں", "%d hari lagi", "%d hari lagi", "%d gün sonra", "Dans %d jours"),
        CalKey.DAYS_AGO to listOf("%d days ago", "قبل %d يوم", "%d دن پہلے", "%d hari lalu", "%d hari lalu", "%d gün önce", "Il y a %d jours"),
        CalKey.WHITE_DAYS to listOf("White days (sunnah fast)", "الأيام البيض", "ایام بیض", "Ayyamul Bidh", "Hari Putih (Ayyamul Bidh)", "Eyyâm-ı Bîz", "Jours blancs"),
        CalKey.NEW_MONTH_BEGINS to listOf("%s begins", "بداية شهر %s", "%s کا آغاز", "Awal bulan %s", "Awal bulan %s", "%s ayı başlıyor", "Début de %s"),
        CalKey.BEGINS_TONIGHT to listOf("Begins tonight at Maghrib", "يبدأ الليلة عند المغرب", "آج مغرب سے شروع", "Dimulai malam ini saat Magrib", "Bermula malam ini waktu Maghrib", "Bu akşam akşam namazıyla başlar", "Commence ce soir au Maghrib"),
        CalKey.ALERTS to listOf("Islamic date alerts", "تنبيهات التواريخ الإسلامية", "اسلامی تاریخوں کی اطلاعات", "Pengingat hari Islam", "Peringatan tarikh Islam", "İslami gün bildirimleri", "Alertes des dates islamiques"),
        CalKey.ALERT_NEW_MONTH to listOf("New Islamic month", "بداية الشهر الهجري", "نیا اسلامی مہینہ", "Awal bulan Hijriah", "Awal bulan Hijrah", "Yeni Hicri ay", "Nouveau mois islamique"),
        CalKey.ALERT_SPECIAL_DAYS to listOf("Special days", "المناسبات", "خاص دن", "Hari besar", "Hari istimewa", "Özel günler", "Jours particuliers"),
        CalKey.ALERT_WHITE_DAYS to listOf("White days (13th to 15th)", "الأيام البيض (13 إلى 15)", "ایام بیض (13 سے 15)", "Ayyamul Bidh (13 sampai 15)", "Hari Putih (13 hingga 15)", "Eyyâm-ı Bîz (13 ile 15)", "Jours blancs (13 au 15)"),
        CalKey.ALERT_TIME to listOf("Alert time", "وقت التنبيه", "اطلاع کا وقت", "Waktu pengingat", "Masa peringatan", "Bildirim zamanı", "Heure de l'alerte"),
        CalKey.EVENING_BEFORE to listOf("Evening before, at Maghrib", "مساء اليوم السابق عند المغرب", "ایک دن پہلے مغرب کے وقت", "Malam sebelumnya saat Magrib", "Malam sebelumnya waktu Maghrib", "Bir önceki akşam, akşam namazında", "La veille, au Maghrib"),
        CalKey.MORNING_OF to listOf("Morning of the day", "صباح اليوم نفسه", "اسی دن صبح", "Pagi harinya", "Pagi hari tersebut", "O günün sabahı", "Le matin même"),
        CalKey.MAIN_CALENDAR to listOf("Main calendar", "التقويم الرئيسي", "بنیادی کیلنڈر", "Kalender utama", "Kalendar utama", "Ana takvim", "Calendrier principal"),
        CalKey.OUT_OF_RANGE to listOf("Outside the supported range (1300 to 1600 AH)", "خارج النطاق المدعوم (1300 إلى 1600 هـ)", "حد سے باہر (1300 سے 1600 ھ)", "Di luar rentang (1300 sampai 1600 H)", "Di luar julat (1300 hingga 1600 H)", "Desteklenen aralık dışında (1300 ile 1600 H)", "Hors de la plage (1300 à 1600 AH)"),
        CalKey.NEXT_EVENT to listOf("Next", "القادم", "اگلا", "Berikutnya", "Seterusnya", "Sıradaki", "Prochain"),
    )
}
