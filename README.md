# Salah for Android

Prayer times with the Salah dot matrix clock, reminders and home screen widgets, written in Kotlin and Jetpack Compose.

This is the Android edition of [Salah](https://github.com/primayudantra/salah) by Prima Yudantra. It follows the [Windows edition](https://github.com/zainkhalid91/salah-windows-) and shares its prayer calculation code, so all three apps give the same times to the minute.

**[Download the APK](https://github.com/zainkhalid91/salah-android/releases/latest/download/Salah.apk)** (Android 8.0 or newer)

## Features

**Home**
- A greeting, today's Gregorian and Hijri date, and the moment's main card: the next prayer with a countdown and a verse or hadith, or on Friday after Asr, the countdown to Maghrib and the last hour.
- Widgets you choose and reorder: prayer times, du'a of the day, Quran (what's playing, or Al-Kahf on Friday, Al-Mulk at night, a short surah otherwise), sunnah fasting, kind word of the day and the Kind Words schedule.

**Quran player**
- All 114 surahs from six reciters (Mishary Alafasy, Abdul Rahman Al-Sudais, Saad Al-Ghamdi, Mahmoud Khalil Al-Husary, Maher Al-Muaiqly, Abdul Basit), streamed from mp3quran.net.
- Search by name, meaning or number. Full Quran and Juz 'Amma playlists, plus your own.
- Queue with play next, reorder and remove; repeat one or all; shuffle.
- Keeps playing with the screen off, with lock screen, headphone and Bluetooth controls, and a mini player above the tabs.
- Pauses when each prayer begins (optional, never during a call). Picks up where you left off.

**Focus Mode**
- At prayer time: a calm full-screen card over the lock screen, pause what's playing, and turn on Do Not Disturb. Done (or the auto close) undoes it.
- On a call or already in Do Not Disturb, only a small nudge, then the card once you're free.
- Choose the prayers, the auto close (10, 15 or 30 minutes) and each action. Off by default.

**Kind Words and Du'a**
- Gentle verses, hadith and du'as through the day, never within 45 minutes of a prayer, silent by default.
- A verse with each early reminder, "Have you prayed?" after each prayer, Al-Kahf and the last hour on Friday, and du'as for the start and end of the work day.
- A Du'a screen grouped by moment, with copy and notify.

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
- The selected day and the converter show the Hijri month's number too, e.g. 7 Sha'ban (8) 1448 AH.
- Alerts for each new Islamic month and for special days, at Maghrib the evening before (when the Islamic day begins) or on the morning of the day.

**Qibla**
- A compass that points to the Kaaba from wherever you are, with the bearing from true north and the distance to Makkah.
- The dial turns with your phone. The ring lights up and the phone gives a short buzz when you face the Qibla.
- Corrects for magnetic declination, and tells you when the compass needs calibrating.
- Uses the phone's motion sensors. No extra permission, and it uses the location you already set.

**Schedule**
- The week or the month at a glance, with today highlighted.
- Share the schedule as text.
- Sunnah fasts are tagged: Mondays and Thursdays, the White Days, Arafah and Ashura (never in Ramadan, on Eid or the days of Tashriq).

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
- 13 colour themes: Crimson (the original), Emerald, Teal, Sapphire, Indigo, Amethyst, Rose, Amber, Olive, Graphite, and the earth tones Forest, Earth and Clay.
- On Android 12 and newer there is also **Wallpaper**, which uses your Material You colours.

**Calculation**
- 13 methods (Muslim World League, ISNA, Umm al-Qura, Karachi, Egyptian, Dubai, Singapore and more), or custom angles.
- Hanafi or standard Asr, rules for high latitudes, and a per-prayer offset in minutes.
- Location from the phone, or search for any city. Nothing else leaves your phone: no accounts and no tracking.

## Install

1. On your phone, open the [latest release](https://github.com/zainkhalid91/salah-android/releases/latest) and download `Salah.apk`.
2. Open it, and allow installs from your browser or file manager when Android asks.
3. Open Salah, set your location, and allow notifications for reminders.

**Updates are automatic.** Every push to `main` publishes a new signed release. The app checks when you open it and once a day, downloads the update in the background, and shows a banner to install it (or installs it a few minutes after you leave the app). The first update asks you to allow installs from Salah once; after that Android 12 and newer update it without asking. Settings → Updates has Check now and both switches.

> **Upgrading from 1.3.0 or earlier:** those builds were signed with a throwaway key, so Android won't install the new version over them. Uninstall the old app once, then install the latest `Salah.apk`. Updates are automatic from then on.

## Build it yourself

You need Android Studio (a recent stable or newer version) and JDK 21.

```bash
git clone https://github.com/zainkhalid91/salah-android.git
cd salah-android
./gradlew :core:test        # prayer calculation tests
./gradlew installDebug      # build and install on a connected phone
```

Or open the folder in Android Studio and press Run.

### Releases and signing

`.github/workflows/android.yml` builds every push to `main` with versionCode `100 + run number`, signs it with the release key and publishes a GitHub release with `Salah.apk` and `latest.json` (version, download URL, SHA-256 and the commit list). Installed apps read `releases/latest/download/latest.json`.

The key lives in four repository secrets: `SALAH_KEYSTORE_BASE64` (the `.jks`, base64), `SALAH_KEYSTORE_PASSWORD`, `SALAH_KEY_ALIAS` and `SALAH_KEY_PASSWORD`. Without them CI still builds, with a debug key, but doesn't publish. Locally, `keystore.properties` (gitignored) points at `signing/salah-release.jks`. Keep a backup of both: a release signed with any other key can't update installed apps.

## Project layout

```
core/   Pure Kotlin, shared with the Windows edition: the adhan-swift port,
        config, schedules, sunnah times, Hijri dates, English and Arabic text,
        the reminder planner, city search. The Windows core also has the
        config file store and the command line keys, which Android doesn't need.
app/    The Android app
  ui/         Compose screens: Today, Calendar, Qibla, Schedule, Reminders, Settings
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
- **Quran recitations:** streamed from [mp3quran.net](https://mp3quran.net); surah names and meanings from Tanzil, as in the macOS app
- **Focus Mode, Kind Words, Quran player and the Home screen:** ported from Salah for macOS 1.3–1.5
- **Location search:** [Open-Meteo](https://open-meteo.com/) geocoding and [BigDataCloud](https://www.bigdatacloud.com/) reverse geocoding

## License

MIT, see [LICENSE](LICENSE). The bundled Doto font keeps its own SIL Open Font License, and the adhan-swift port keeps its MIT notice.
