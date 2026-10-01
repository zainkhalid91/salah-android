# Salah for Android

Prayer times with the Salah dot matrix clock, reminders and home screen widgets, written in Kotlin and Jetpack Compose.

This is the Android edition of [Salah](https://github.com/primayudantra/salah) by Prima Yudantra. It follows the [Windows edition](https://github.com/zainkhalid91/salah-windows-) and shares its prayer calculation code, so all three apps give the same times to the minute.

**[Download the APK](https://github.com/zainkhalid91/salah-android/releases/latest/download/Salah.apk)** (Android 8.0 or newer)

## Features

**Today**
- The next prayer in large dot matrix type, with a live countdown. It switches to NOW when a prayer begins.
- A coloured timeline of the day: Fajr, Sunrise, Dhuhr, Asr, Maghrib and Isha.
- Tap a prayer to see its reminder, method and offset. Tap the date to look at any other day.
- Hijri date, with Jumu'ah shown on Fridays.

**Schedule**
- The week or the month at a glance, with today highlighted.
- Share the schedule as text.

**Reminders**
- Per prayer: an early reminder (5, 10, 15 or 30 minutes before) and one at the prayer time.
- Quiet hours, and pause for 1 hour, 3 hours or until tomorrow.
- Sounds: system default, the Salah soft chime, or silent.
- Exact alarms, re-planned after a reboot or a time zone change.

**Widgets**
- **Next prayer** (2×2): the prayer and its time in dot matrix type, with a live countdown that keeps ticking without draining the battery.
- **Today's prayers** (4×3): all six times, with the next one highlighted.
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
        config, schedules, Hijri dates, the reminder planner, city search.
app/    The Android app
  ui/         Compose screens: Today, Schedule, Reminders, Settings
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
- **Location search:** [Open-Meteo](https://open-meteo.com/) geocoding and [BigDataCloud](https://www.bigdatacloud.com/) reverse geocoding
