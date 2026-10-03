# Salah for Android

Prayer times with the Salah dot matrix clock, reminders and home screen widgets, written in Kotlin and Jetpack Compose.

This is the Android edition of [Salah](https://github.com/primayudantra/salah) by Prima Yudantra. It follows the [Windows edition](https://github.com/zainkhalid91/salah-windows-) and shares its prayer calculation code, so all three apps give the same times to the minute.

**[Download the APK](https://github.com/zainkhalid91/salah-android/releases/latest/download/Salah.apk)** (Android 8.0 or newer)

## Features

**Today**
- The next prayer in large dot matrix type, with a live countdown. It switches to NOW when a prayer begins.
- A coloured timeline of the day: Fajr, Sunrise, Dhuhr, Asr, Maghrib and Isha.
- The sunnah times on the same timeline: Tahajjud (the last third of the night), Ishraq, Duha (Chasht), Zawal, Awwabin and Islamic midnight. They can be hidden in Settings.
- Tap a prayer to see its reminder, method and offset. Tap the date to look at any other day.
- Hijri date, with Jumuah shown on Fridays.

**Islamic calendar**
- A month view in Hijri or Gregorian, with both dates in every day. Swipe between months.
- Islamic days are marked: Islamic New Year, Ashura, Mawlid, Isra and Mi'raj, Shab-e-Barat, Ramadan, the last ten nights, Laylat al-Qadr, both Eids, the first ten days of Dhu al-Hijjah, Arafah, Tashreeq, and the white days.
- A converter that works both ways: Hijri to Gregorian and Gregorian to Hijri.
- Every Islamic date of the year with its Gregorian date and a countdown.
- The selected day shows the Hijri month's number too, e.g. 22 Rabi' al-Thani (4) 1448 AH.
- Alerts for each new Islamic month and for special days, at Maghrib the evening before (when the Islamic day begins) or on the morning of the day.

**Schedule**
- The week or the month at a glance, with today highlighted.
- Share the schedule as text.

**Language**
- English or Arabic (العربية), picked the first time the app opens and changeable in Settings.
- Arabic switches the whole app to a right-to-left layout, including the calendar, widgets and notifications.

**Reminders**
- Per prayer: an early reminder (5, 10, 15 or 30 minutes before) and one at the prayer time.
- The azan plays when each of the five prayers begins. Early reminders and Islamic date alerts keep the normal sound.
- Quiet hours, and pause for 1 hour, 3 hours or until tomorrow.
- Sounds: system default, the Salah soft chime, or silent.
- Exact alarms, re-planned after a reboot or a time zone change.

**Widgets**
- **Next prayer** (2×2): the prayer and its time in dot matrix type, with a live countdown that keeps ticking without draining the battery.
- **Today's prayers** (4×3): all six times, with the next one highlighted.
- **Islamic calendar** (4×4): this month with today and the Islamic days marked, plus the next event.
- Every widget has its own style. It can follow the app's colours, or use any of the 10 colours with a light, dark, coloured or glass background. Long-press a widget and choose Edit to change it.

**Colours and theme**
- Light, dark or follow the system.
- 10 colour themes: Crimson (the original), Emerald, Teal, Sapphire, Indigo, Amethyst, Rose, Amber, Olive and Graphite.
- On Android 12 and newer there is also **Wallpaper**, which uses your Material You colours.

**Calculation**
- 13 methods (Muslim World League, ISNA, Umm al-Qura, Karachi, Egyptian, Dubai, Singapore and more), or custom angles.
- Hanafi or standard Asr, rules for high latitudes, and a per-prayer offset in minutes.
- Location from the phone, or search for any city. Nothing else leaves your phone: no accounts and no tracking.

## Install

1. On your phone, open the [latest release](https://github.com/zainkhalid91/salah-android/releases/latest) and download `Salah.apk`.
2. Open it, and allow installs from your browser or file manager when Android asks.
3. Open Salah, set your location, and allow notifications for reminders.

The APK is signed with a debug key so it installs straight from GitHub. That is fine for personal use, but it is not a Play Store build.

## Build it yourself

You need Android Studio (a recent stable or newer version) and JDK 21.

```bash
git clone https://github.com/zainkhalid91/salah-android.git
cd salah-android
./gradlew :core:test        # prayer calculation tests
./gradlew installDebug      # build and install on a connected phone
```

Or open the folder in Android Studio and press Run.

## Project layout

```
core/   Pure Kotlin, shared with the Windows edition: the adhan-swift port,
        config, schedules, sunnah times, Hijri dates, English and Arabic text,
        the reminder planner, city search. The Windows core also has the
        config file store and the command line keys, which Android doesn't need.
app/    The Android app
  ui/         Compose screens: Today, Calendar, Schedule, Reminders, Settings
  widget/     Glance widgets, per widget styles, the configure screen
  reminders/  Exact alarms, notifications, boot and time change handling
  location/   One-off location through the platform LocationManager
  data/       Config storage and the colour themes
```

The config is the same JSON file format as the macOS and Windows apps.

## Credits

- **Salah for macOS:** [Prima Yudantra](https://github.com/primayudantra/salah)
- **Windows and Android editions:** [Zain Khalid](https://github.com/zainkhalid91)
- **Prayer calculation:** ported from [adhan-swift](https://github.com/batoulapps/adhan-swift) by Batoul Apps (MIT License). The notice is in `core/src/main/kotlin/salah/core/adhan/Adhan.kt`.
- **Pixel font:** [Doto](https://github.com/oliverlalan/Doto), SIL Open Font License 1.1 (`docs/OFL-Doto.txt`)
- **Azan clip:** from islamcan.com, as supplied by the project owner (`app/src/main/res/raw/salah_azan.mp3`)
- **Location search:** [Open-Meteo](https://open-meteo.com/) geocoding and [BigDataCloud](https://www.bigdatacloud.com/) reverse geocoding

## License

MIT, see [LICENSE](LICENSE). The bundled Doto font keeps its own SIL Open Font License, and the adhan-swift port keeps its MIT notice.
