package salah.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/** The app's own language, picked on first launch. */
@Serializable
enum class AppLanguage(val nativeName: String, val rtl: Boolean) {
    @SerialName("en") EN("English", false),
    @SerialName("ar") AR("العربية", true);

    val raw: String get() = serialName(this)

    val calLang: CalLang get() = if (this == AR) CalLang.AR else CalLang.EN

    /** This language with the phone's region, so the week still starts on the local day. */
    fun locale(region: Locale = Locale.getDefault()): Locale = Locale.Builder().setLanguage(raw).setRegion(region.country.takeIf { it.length == 2 } ?: "").build()
}

/**
 * App strings in English and Arabic, shared by the Android and Windows apps.
 * The English text is the key, so call sites stay readable and an untranslated
 * string falls back to English. `{0}` and `{1}` are placeholders.
 */
object AppText {
    fun t(lang: AppLanguage, en: String, vararg args: Any): String {
        var s = if (lang == AppLanguage.AR) AR[en] ?: en else en
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }

    fun extra(e: ExtraTime, lang: AppLanguage): String = t(lang, e.displayName)

    /** "AM"/"PM" become "ص"/"م" in Arabic. */
    fun period(p: String, lang: AppLanguage): String = if (lang == AppLanguage.AR && p.isNotEmpty()) (if (p == "AM") "ص" else "م") else p

    /** "Saturday, 3 October" for the timeline header. */
    fun longDate(d: java.time.LocalDate, lang: AppLanguage, includeYear: Boolean = false): String =
        if (lang == AppLanguage.EN) TimeFormatting.longDate(d, includeYear) else {
            val loc = lang.locale()
            val text = "${CalendarText.weekdayLong(d.dayOfWeek, loc)}، ${d.dayOfMonth} ${CalendarText.gregorianMonth(d.monthValue, loc)}" + if (includeYear) " ${d.year}" else ""
            CalendarText.digits(text, lang.calLang)
        }

    /** "Fri, 25 Sep" or "الجمعة ٢٥ سبتمبر". */
    fun shortDate(d: java.time.LocalDate, lang: AppLanguage): String =
        if (lang == AppLanguage.EN) TimeFormatting.shortDate(d) else {
            val loc = lang.locale()
            CalendarText.digits("${CalendarText.weekdayLong(d.dayOfWeek, loc)}، ${d.dayOfMonth} ${CalendarText.gregorianMonth(d.monthValue, loc)}", lang.calLang)
        }

    /** "October 2026" */
    fun monthYear(d: java.time.LocalDate, lang: AppLanguage): String =
        CalendarText.digits("${CalendarText.gregorianMonth(d.monthValue, lang.locale())} ${d.year}", lang.calLang)

    /** "12 Rabi' al-Awwal 1448" */
    fun hijri(h: HijriDate, lang: AppLanguage): String =
        if (lang == AppLanguage.EN) h.formatted
        else "${CalendarText.number(h.day, lang.calLang)} ${CalendarText.hijriMonth(h.month, lang.calLang)} ${CalendarText.number(h.year, lang.calLang)}"

    private val AR: Map<String, String> = mapOf(
        // Prayers and times
        "Fajr" to "الفجر",
        "Sunrise" to "الشروق",
        "Dhuhr" to "الظهر",
        "Asr" to "العصر",
        "Maghrib" to "المغرب",
        "Isha" to "العشاء",
        "Jumuah" to "الجمعة",
        "Tahajjud" to "التهجد",
        "Ishraq" to "الإشراق",
        "Duha" to "الضحى",
        "Zawal" to "الزوال",
        "Awwabin" to "الأوابين",
        "Midnight" to "منتصف الليل",

        // Tabs and titles
        "Today" to "اليوم",
        "Calendar" to "التقويم",
        "Schedule" to "الجدول",
        "Reminders" to "التذكيرات",
        "Settings" to "الإعدادات",
        "Location" to "الموقع",
        "About" to "حول",

        // Language
        "Language" to "اللغة",
        "LANGUAGE" to "اللغة",
        "Choose your language" to "اختر لغتك",
        "You can change it later in Settings." to "يمكنك تغييرها لاحقًا من الإعدادات.",
        "Continue" to "متابعة",

        // Today
        "NOW" to "الآن",
        "now" to "الآن",
        "NEXT PRAYER" to "الصلاة القادمة",
        "TOMORROW" to "غدًا",
        "STARTED" to "منذ",
        "IN" to "بعد",
        "PREVIEW" to "معاينة",
        "PRAYER DETAIL" to "تفاصيل الصلاة",
        "SUNRISE" to "الشروق",
        "SUNNAH PRAYER" to "صلاة السنة",
        "WELCOME" to "مرحبًا",
        "LOCATION" to "الموقع",
        "SET LOCATION" to "حدد الموقع",
        "Set location" to "تحديد الموقع",
        "Change location" to "تغيير الموقع",
        "The saved location is invalid." to "الموقع المحفوظ غير صالح.",
        "Prayer times are calculated for where you are." to "تُحسب أوقات الصلاة حسب موقعك.",
        "Back to now" to "العودة إلى الآن",
        "Show" to "عرض",
        "Note" to "ملاحظة",
        "Reminder" to "التذكير",
        "Method" to "الطريقة",
        "Offset" to "التعديل",
        "Window" to "الوقت",
        "None" to "لا يوجد",
        "Off" to "إيقاف",
        "Off (all reminders)" to "متوقف (كل التذكيرات)",
        "{0} min before" to "قبل {0} دقيقة",
        "at prayer time" to "عند وقت الصلاة",
        "{0} and {1}" to "{0} و{1}",
        "{0} min" to "{0} د",
        "End of Fajr time, not a prayer" to "نهاية وقت الفجر، وليس صلاة",
        "Hanafi Asr" to "عصر حنفي",
        "until {0}" to "حتى {0}",
        "{0} to {1}" to "من {0} إلى {1}",
        "The last third of the night, the best time for night prayer." to "الثلث الأخير من الليل، أفضل وقت لقيام الليل.",
        "Once the sun has fully risen, about 20 minutes after sunrise." to "بعد ارتفاع الشمس، نحو ٢٠ دقيقة بعد الشروق.",
        "The forenoon prayer, once a quarter of the day has passed." to "صلاة الضحى، بعد مضي ربع النهار.",
        "The sun is at its height. Wait for Dhuhr before praying." to "الشمس في كبد السماء. انتظر الظهر قبل الصلاة.",
        "Between Maghrib and Isha, after the sunnah of Maghrib." to "بين المغرب والعشاء، بعد سنة المغرب.",
        "Halfway between Maghrib and Fajr. Pray Isha before it." to "منتصف ما بين المغرب والفجر. صلِّ العشاء قبله.",
        "The sun doesn't rise or set here on this date, so prayer times can't be calculated. Salah never invents a time." to
            "لا تشرق الشمس أو تغرب هنا في هذا التاريخ، لذلك لا يمكن حساب أوقات الصلاة. لا يخترع التطبيق وقتًا أبدًا.",
        "{0} can't be calculated here on this date." to "لا يمكن حساب {0} هنا في هذا التاريخ.",
        "Follow a nearby city or your local authority's timetable for these days." to "اتبع مدينة قريبة أو جدول الجهة المحلية في هذه الأيام.",
        "Choose a high-latitude rule in Settings." to "اختر قاعدة خطوط العرض العليا من الإعدادات.",

        // Schedule
        "Week" to "أسبوع",
        "Month" to "شهر",
        "Share" to "مشاركة",
        "Share schedule" to "مشاركة الجدول",
        "Previous" to "السابق",
        "Next" to "التالي",
        "Set a location on the Today tab to see the schedule." to "حدد موقعك في تبويب اليوم لرؤية الجدول.",

        // Reminders
        "Notifications are off" to "الإشعارات متوقفة",
        "Allow notifications for Salah to get reminders." to "اسمح بالإشعارات لتصلك التذكيرات.",
        "Reminders may arrive late" to "قد تتأخر التذكيرات",
        "Allow alarms and reminders so they fire on the minute." to "اسمح بالمنبهات والتذكيرات لتصل في وقتها تمامًا.",
        "Prayer reminders" to "تذكيرات الصلاة",
        "Paused" to "متوقف مؤقتًا",
        "Pause reminders" to "إيقاف التذكيرات مؤقتًا",
        "Until {0}" to "حتى {0}",
        "Silence them for a while" to "أسكتها لبعض الوقت",
        "1 hour" to "ساعة",
        "3 hours" to "٣ ساعات",
        "Until tomorrow" to "حتى الغد",
        "PRAYERS" to "الصلوات",
        "SOUND" to "الصوت",
        "QUIET HOURS" to "ساعات الهدوء",
        "Quiet hours" to "ساعات الهدوء",
        "No reminders in this window" to "لا تذكيرات في هذه الفترة",
        "Starts" to "تبدأ",
        "Ends" to "تنتهي",
        "No early" to "بدون تذكير مبكر",
        "At prayer time" to "عند وقت الصلاة",
        "Set" to "تعيين",
        "Cancel" to "إلغاء",
        "System default" to "الافتراضي",
        "Soft chime" to "رنين هادئ",
        "Silent" to "صامت",
        "Azan at prayer time" to "الأذان عند وقت الصلاة",
        "For the five prayers. Early reminders keep the sound above." to "للصلوات الخمس. التذكيرات المبكرة تبقى بالصوت أعلاه.",
        "Azan" to "الأذان",
        "{0} in {1} minutes" to "{0} بعد {1} دقيقة",
        "Time for {0}" to "حان وقت {0}",

        // Settings
        "No location set" to "لم يُحدد موقع",
        "Tap to set your location" to "اضغط لتحديد موقعك",
        "APPEARANCE" to "المظهر",
        "Theme" to "السمة",
        "System" to "النظام",
        "Light" to "فاتح",
        "Dark" to "داكن",
        "Colour" to "اللون",
        "24-hour clock" to "نظام ٢٤ ساعة",
        "Show Jumuah on Fridays" to "عرض الجمعة يوم الجمعة",
        "Sunnah prayers" to "صلوات السنة",
        "Tahajjud, Ishraq, Duha, Zawal, Awwabin and midnight on the timeline" to "التهجد والإشراق والضحى والزوال والأوابين ومنتصف الليل في الجدول اليومي",
        "Hijri date adjustment" to "تعديل التاريخ الهجري",
        "For local moon sighting" to "حسب رؤية الهلال المحلية",
        "Show NOW for" to "عرض «الآن» لمدة",
        "After a prayer starts" to "بعد دخول وقت الصلاة",
        "WIDGETS" to "الأدوات",
        "Next prayer" to "الصلاة القادمة",
        "Big dot matrix time with a live countdown" to "الوقت بخط نقطي كبير مع عد تنازلي مباشر",
        "Today's prayers" to "صلوات اليوم",
        "All six times, the next one highlighted" to "الأوقات الستة، مع إبراز القادمة",
        "Islamic calendar" to "التقويم الإسلامي",
        "This month in Hijri and Gregorian, Islamic days marked" to "هذا الشهر بالهجري والميلادي، مع المناسبات الإسلامية",
        "Each widget can follow the app's colours or use its own. Long press a widget and pick Edit to change it." to
            "يمكن لكل أداة أن تتبع ألوان التطبيق أو تستخدم ألوانها. اضغط مطولًا على الأداة واختر تعديل لتغييرها.",
        "CALCULATION" to "الحساب",
        "Automatic ({0})" to "تلقائي ({0})",
        "Automatic" to "تلقائي",
        "Fajr angle" to "زاوية الفجر",
        "Isha angle" to "زاوية العشاء",
        "High latitude rule" to "قاعدة خطوط العرض العليا",
        "ADJUSTMENTS (MINUTES)" to "التعديلات (بالدقائق)",
        "ABOUT" to "حول",
        "Salah for Android" to "صلاة لأندرويد",
        "Version {0} · times by a Kotlin port of adhan-swift" to "الإصدار {0} · الأوقات من نسخة Kotlin من adhan-swift",
        "Original macOS app" to "تطبيق macOS الأصلي",
        "Source code" to "الشيفرة المصدرية",
        "Pixel font" to "الخط النقطي",
        "Shafi'i, Maliki, Hanbali" to "الشافعي والمالكي والحنبلي",
        "Singapore (MUIS)" to "سنغافورة (MUIS)",
        "Muslim World League" to "رابطة العالم الإسلامي",
        "North America (ISNA)" to "أمريكا الشمالية (ISNA)",
        "Egyptian General Authority of Survey" to "الهيئة المصرية العامة للمساحة",
        "Umm al-Qura" to "أم القرى",
        "Karachi" to "كراتشي",
        "Dubai" to "دبي",
        "Kuwait" to "الكويت",
        "Qatar" to "قطر",
        "Moonsighting Committee" to "لجنة رؤية الهلال",
        "Turkey" to "تركيا",
        "Tehran" to "طهران",
        "Custom" to "مخصص",
        "Hanafi" to "الحنفي",
        "Middle of the night" to "منتصف الليل",
        "Seventh of the night" to "سُبع الليل",
        "Twilight angle" to "زاوية الشفق",

        // Location
        "Finding you…" to "جارٍ تحديد موقعك…",
        "Use my location" to "استخدم موقعي",
        "Search for a city" to "ابحث عن مدينة",
        "Search" to "بحث",
        "Couldn't find your location. Check that location is on, or search for your city." to
            "تعذر تحديد موقعك. تأكد من تشغيل الموقع، أو ابحث عن مدينتك.",

        // Widgets
        "Widget style" to "نمط الأداة",
        "Live countdown" to "عد تنازلي مباشر",
        "Hijri date" to "التاريخ الهجري",
        "Save widget" to "حفظ الأداة",
        "App theme" to "سمة التطبيق",
        "Done" to "تم",
        "App colours" to "ألوان التطبيق",
        "Background" to "الخلفية",
        "Coloured" to "ملون",
        "Glass" to "زجاجي",
        "Open Salah to set your location" to "افتح التطبيق لتحديد موقعك",
        "Open the app to set your location" to "افتح التطبيق لتحديد موقعك",
    )
}
