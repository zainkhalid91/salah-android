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

        // Windows
        "About Salah" to "حول التطبيق",
        "Appearance" to "المظهر",
        "Calculation" to "الحساب",
        "Calculation method" to "طريقة الحساب",
        "Clock" to "الساعة",
        "12-hour" to "١٢ ساعة",
        "24-hour" to "٢٤ ساعة",
        "Change…" to "تغيير…",
        "Change location…" to "تغيير الموقع…",
        "Back to next prayer" to "العودة إلى الصلاة القادمة",
        "Back to today" to "العودة إلى اليوم",
        "Previous month" to "الشهر السابق",
        "Next month" to "الشهر التالي",
        "Enter manually" to "إدخال يدوي",
        "Locating…" to "جارٍ تحديد الموقع…",
        "Location access is off for Salah." to "الوصول إلى الموقع متوقف لهذا التطبيق.",
        "Open Windows Settings" to "فتح إعدادات ويندوز",
        "End of Fajr time; not a prayer" to "نهاية وقت الفجر، وليس صلاة",
        "Calculated times are approximations. Your local authority may differ by a few minutes; adjust per prayer if needed." to
            "الأوقات المحسوبة تقريبية. قد تختلف الجهة المحلية ببضع دقائق؛ عدّل كل صلاة عند الحاجة.",
        "Check a reminder looks right" to "تحقق من شكل التذكير",
        "Check for updates automatically" to "التحقق من التحديثات تلقائيًا",
        "Check now" to "تحقق الآن",
        "Checking…" to "جارٍ التحقق…",
        "City search" to "البحث عن مدينة",
        "City, e.g. Singapore or Jakarta" to "مدينة، مثل الرياض أو القاهرة",
        "Command line tool" to "أداة سطر الأوامر",
        "Config file" to "ملف الإعدادات",
        "Coordinates" to "الإحداثيات",
        "Copy to clipboard" to "نسخ إلى الحافظة",
        "Custom angles" to "زوايا مخصصة",
        "Custom…" to "مخصص…",
        "Date" to "التاريخ",
        "Day" to "اليوم",
        "Export  ⌄" to "تصدير  ⌄",
        "Export CSV…" to "تصدير CSV…",
        "Export calendar (ICS)…" to "تصدير التقويم (ICS)…",
        "Follows the location. Times are always shown in this zone." to "يتبع الموقع. تُعرض الأوقات دائمًا بهذا التوقيت.",
        "Font" to "الخط",
        "Hanafi places Asr later in the afternoon" to "المذهب الحنفي يؤخر العصر",
        "High-latitude rule" to "قاعدة خطوط العرض العليا",
        "How Fajr and Isha are estimated when twilight never fully ends" to "كيف يُقدّر الفجر والعشاء عندما لا ينتهي الشفق",
        "How long the display shows NOW after a prayer starts" to "مدة عرض «الآن» بعد دخول وقت الصلاة",
        "Install…" to "تثبيت…",
        "Install {0}…" to "تثبيت {0}…",
        "Keeps reminders coming; Salah starts quietly in the notification area" to "لتصل التذكيرات؛ يبدأ التطبيق بهدوء في منطقة الإشعارات",
        "Last check failed: {0}" to "فشل آخر تحقق: {0}",
        "Latitude" to "خط العرض",
        "Longitude" to "خط الطول",
        "Latitude must be a number from -90 to 90." to "يجب أن يكون خط العرض رقمًا من -90 إلى 90.",
        "Longitude must be a number from -180 to 180." to "يجب أن يكون خط الطول رقمًا من -180 إلى 180.",
        "NEXT · TOMORROW" to "القادمة · غدًا",
        "NOW display" to "عرض «الآن»",
        "Name" to "الاسم",
        "e.g. Home" to "مثل المنزل",
        "Next (→)" to "التالي (→)",
        "Previous (←)" to "السابق (←)",
        "Next: {0} in {1}" to "القادمة: {0} بعد {1}",
        "No early reminder" to "بدون تذكير مبكر",
        "No reminders are shown in this window" to "لا تظهر تذكيرات في هذه الفترة",
        "Not set" to "غير محدد",
        "Notification area" to "منطقة الإشعارات",
        "Offsets" to "التعديلات",
        "Once a day, from GitHub Releases" to "مرة يوميًا، من إصدارات GitHub",
        "Open Salah" to "فتح التطبيق",
        "Pause" to "إيقاف مؤقت",
        "Pause reminders until" to "إيقاف التذكيرات حتى",
        "Pause until…  ⌄" to "إيقاف حتى…  ⌄",
        "Paused until {0}" to "متوقف حتى {0}",
        "Prayer times can't be calculated for this location today." to "لا يمكن حساب أوقات الصلاة لهذا الموقع اليوم.",
        "Privacy" to "الخصوصية",
        "Quit Salah completely" to "إغلاق التطبيق تمامًا",
        "Quit completely" to "إغلاق تمامًا",
        "Relabels Dhuhr on Fridays" to "تسمية الظهر بالجمعة يوم الجمعة",
        "Reminders off" to "التذكيرات متوقفة",
        "Reminders on" to "التذكيرات مفعلة",
        "Reminders paused" to "التذكيرات متوقفة مؤقتًا",
        "Reset to defaults" to "استعادة الإعدادات الافتراضية",
        "Resetting replaces the file with defaults. You'll need to set your location again." to "الاستعادة تستبدل الملف بالإعدادات الافتراضية. ستحتاج إلى تحديد موقعك مجددًا.",
        "Resume" to "استئناف",
        "SETTINGS ERROR" to "خطأ في الإعدادات",
        "Salah couldn't read its settings." to "تعذر على التطبيق قراءة إعداداته.",
        "Salah plans the next 3 days of reminders and shows them from the notification area. Keep it starting with Windows so they're never missed." to
            "يخطط التطبيق تذكيرات الأيام الثلاثة القادمة ويعرضها من منطقة الإشعارات. اتركه يبدأ مع ويندوز كي لا يفوتك شيء.",
        "Salah {0} is available" to "الإصدار {0} متاح",
        "Save" to "حفظ",
        "Search city" to "ابحث عن مدينة",
        "Search, e.g. Jakarta" to "ابحث، مثل الرياض",
        "Send test notification" to "إرسال إشعار تجريبي",
        "Set a location first" to "حدد الموقع أولًا",
        "Set a location to see the schedule." to "حدد الموقع لرؤية الجدول.",
        "Set your location to see prayer times." to "حدد موقعك لرؤية أوقات الصلاة.",
        "Show in Explorer" to "عرض في المستكشف",
        "Shown as Salah notifications" to "تظهر كإشعارات من التطبيق",
        "Shows the next prayer when you point at the ☾ icon" to "يعرض الصلاة القادمة عند الإشارة إلى أيقونة ☾",
        "Sound" to "الصوت",
        "Start with Windows" to "البدء مع ويندوز",
        "Sunrise marks the end of Fajr and is not a prayer." to "الشروق نهاية وقت الفجر وليس صلاة.",
        "Test notification" to "إشعار تجريبي",
        "This week" to "هذا الأسبوع",
        "Time zone" to "المنطقة الزمنية",
        "Times update as you change these." to "تتحدث الأوقات عند تغيير هذه الإعدادات.",
        "Turn reminders off" to "إيقاف التذكيرات",
        "Turn reminders on" to "تشغيل التذكيرات",
        "Up to date ({0})" to "محدّث ({0})",
        "Update" to "تحديث",
        "Updates" to "التحديثات",
        "Downloading {0}…" to "جارٍ تنزيل {0}…",
        "Version {0} is available" to "الإصدار {0} متاح",
        "Version {0} for Windows" to "الإصدار {0} لويندوز",
        "You have version {0}" to "لديك الإصدار {0}",
        "Windows releases" to "إصدارات ويندوز",
        "{0} planned, through {1}" to "{0} مجدولة، حتى {1}",
        "— means the time can't be calculated here on that date." to "— تعني أنه لا يمكن حساب الوقت هنا في ذلك التاريخ.",
        "Use `salah` in Terminal or PowerShell. Shares settings with this app." to "استخدم `salah` في الطرفية أو PowerShell. يشارك الإعدادات مع هذا التطبيق.",
        "Umm al-Qura calendar from Java's java.time, with a manual ±2 day adjustment for local moon sighting." to "تقويم أم القرى من java.time، مع تعديل يدوي ±٢ يوم حسب رؤية الهلال المحلية.",
        "Close the window and Salah keeps running in the notification area so reminders arrive. Use “Quit completely” from the ☾ icon's menu to stop it." to
            "عند إغلاق النافذة يبقى التطبيق في منطقة الإشعارات لتصل التذكيرات. استخدم «إغلاق تمامًا» من قائمة أيقونة ☾ لإيقافه.",
        "Everything stays on this PC. No accounts, analytics or sync. Your location is requested only when you choose “Use my location”. Update checks ask GitHub's public API for the latest release and send nothing about you." to
            "كل شيء يبقى على هذا الحاسوب. لا حسابات ولا تحليلات ولا مزامنة. يُطلب موقعك فقط عند اختيار «استخدم موقعي». يسأل التحقق من التحديثات واجهة GitHub العامة عن أحدث إصدار ولا يرسل شيئًا عنك.",
        "City search uses Open-Meteo's geocoding service, and naming a coordinate uses BigDataCloud, so search queries and a coordinate lookup are sent to them." to
            "يستخدم البحث عن المدن خدمة Open-Meteo، وتسمية الإحداثيات تستخدم BigDataCloud، لذا تُرسل إليهما عبارات البحث والإحداثيات.",
        "City search uses Open-Meteo's geocoding service, so your query is sent to Open-Meteo. Coordinates you type are never sent anywhere." to
            "يستخدم البحث عن المدن خدمة Open-Meteo، لذا تُرسل عبارة البحث إليها. الإحداثيات التي تكتبها لا تُرسل إلى أي مكان.",
        "Doto by The Doto Project Authors, licensed under the SIL Open Font License 1.1. The license is included with the app (fonts/OFL.txt)." to
            "خط Doto من مؤلفي مشروع Doto، مرخص بموجب SIL Open Font License 1.1. الترخيص مرفق مع التطبيق (fonts/OFL.txt).",
        "Prayer times are calculated by {0}, matching the macOS app minute for minute. Calculated times are approximations; your local authority may differ by several minutes, which is what the per-prayer offsets are for." to
            "تُحسب أوقات الصلاة بواسطة {0}، مطابقة لتطبيق macOS دقيقة بدقيقة. الأوقات المحسوبة تقريبية؛ قد تختلف الجهة المحلية ببضع دقائق، ولهذا توجد تعديلات لكل صلاة.",
        "e.g. Kemenag Indonesia uses Fajr 20°, Isha 18°" to "مثلًا وزارة الشؤون الدينية الإندونيسية تستخدم الفجر ٢٠° والعشاء ١٨°",
        "⌖  Use my location" to "⌖  استخدم موقعي",
        "Current location" to "الموقع الحالي",
        "Name and countdown" to "الاسم والعد التنازلي",
        "Time only" to "الوقت فقط",
        "Icon only" to "الأيقونة فقط",
        "can't be calculated" to "لا يمكن حسابه",
        "from Windows location" to "من موقع ويندوز",
        "entered manually" to "أُدخل يدويًا",
        "Location access is off for desktop apps." to "الوصول إلى الموقع متوقف لتطبيقات سطح المكتب.",
        "Couldn't reach Windows Location Services." to "تعذر الوصول إلى خدمات الموقع في ويندوز.",
        "Windows couldn't determine your location. Enter your city instead." to "تعذر على ويندوز تحديد موقعك. أدخل مدينتك بدلًا من ذلك.",
        "{0} day" to "{0} يوم",
        "{0} days" to "{0} أيام",
        "{0} hour" to "{0} ساعة",
        "{0} hours" to "{0} ساعات",

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
