package salah.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTest {
    @Test
    fun roundTrip() {
        val c = Fixtures.config(Fixtures.singapore, MethodID.CUSTOM).let {
            it.copy(
                calculation = it.calculation.withOffset(2, Prayer.ISHA),
                display = it.display.copy(use24HourClock = false),
                reminders = it.reminders.withPausedUntil(Fixtures.date("2026-09-28T00:00:00+08:00"))
                    .copy(quietHours = QuietHours(true, "22:30", "05:00")),
            )
        }
        assertEquals(c, ConfigStore.decode(ConfigStore.encode(c)))
    }

    @Test
    fun readsMacOsFile() {
        val json = """
        {
          "calculation" : {
            "customFajrAngle" : 20,
            "customIshaAngle" : 18,
            "madhab" : "shafi",
            "method" : "singapore",
            "offsets" : { "isha" : 2 }
          },
          "display" : {
            "hijriAdjustment" : 0, "jumuahRelabel" : true, "menuBarStyle" : "nameAndCountdown",
            "nowWindowMinutes" : 15, "showMenuBarExtra" : true, "theme" : "system", "use24HourClock" : true
          },
          "launchAtLogin" : true,
          "location" : {
            "countryCode" : "SG", "latitude" : 1.3521, "longitude" : 103.8198, "name" : "Singapore",
            "source" : "automatic", "timeZone" : "Asia/Singapore"
          },
          "reminders" : {
            "enabled" : true, "pausedUntil" : "2026-09-28T16:00:00Z",
            "prayers" : { "asr" : { "atTime" : true, "enabled" : true, "leadMinutes" : 15 } },
            "quietHours" : { "enabled" : false, "end" : "04:30", "start" : "23:00" },
            "sound" : "chime"
          },
          "schemaVersion" : 1
        }
        """.trimIndent()
        val c = ConfigStore.decode(json)
        assertEquals(MethodID.SINGAPORE, c.calculation.method)
        assertEquals(2, c.calculation.offset(Prayer.ISHA))
        assertEquals(SavedLocation.Source.AUTOMATIC, c.location?.source)
        assertEquals(Fixtures.date("2026-09-29T00:00:00+08:00"), c.reminders.pausedUntil)
        assertEquals(15, c.reminders.reminder(Prayer.ASR).leadMinutes)
        assertEquals(15, c.reminders.reminder(Prayer.FAJR).leadMinutes, "missing prayers keep their defaults")
        assertEquals(ReminderSound.CHIME, c.reminders.sound)
        // And what we write reads back the same, with the macOS key names.
        val written = ConfigStore.encode(c)
        assertTrue(written.contains("\"menuBarStyle\": \"nameAndCountdown\""))
        assertTrue(written.contains("\"pausedUntil\": \"2026-09-28T16:00:00Z\""))
        assertEquals(c, ConfigStore.decode(written))
    }

    @Test
    fun migrationFromUnversionedFile() {
        val c = ConfigStore.decode("""{"location":{"name":"Singapore","latitude":1.35,"longitude":103.82,"timeZone":"Asia/Singapore"}}""")
        assertEquals(SalahConfig.CURRENT_SCHEMA_VERSION, c.schemaVersion)
        assertEquals("Singapore", c.location?.name)
        assertEquals(SavedLocation.Source.MANUAL, c.location?.source)
        assertEquals(ReminderSettings(), c.reminders)
    }

    @Test
    fun unknownKeysAndValuesAreTolerated() {
        val json = """
        {"schemaVersion":1,"futureFeature":{"x":1},
         "calculation":{"method":"someNewMethod","madhab":"hanafi","unknown":true},
         "display":{"theme":"sepia","nowWindowMinutes":500},
         "reminders":{"prayers":{"asr":{"enabled":false,"leadMinutes":7},"sunrise":{"enabled":true}}}}
        """
        val c = ConfigStore.decode(json)
        assertNull(c.calculation.method, "unknown method falls back to automatic")
        assertEquals(MadhabSetting.HANAFI, c.calculation.madhab)
        assertEquals(ThemeSetting.SYSTEM, c.display.theme)
        assertEquals(60, c.display.nowWindowMinutes)
        assertFalse(c.reminders.reminder(Prayer.ASR).enabled)
        assertEquals(10, c.reminders.reminder(Prayer.ASR).leadMinutes, "invalid lead falls back")
        assertNull(c.reminders.prayers["sunrise"], "sunrise is not a prayer")
    }

    @Test
    fun corruptFileSurfacesClearError() {
        val e = assertFailsWith<ConfigException.Corrupt> { ConfigStore.decode("{ not json", "/data/config.json") }
        assertEquals("/data/config.json", e.path)
        assertFailsWith<ConfigException.Corrupt> { ConfigStore.decode("""{"location":{"latitude":"north"}}""") }
    }

    @Test
    fun exporters() {
        val days = PrayerSchedule.range(LocalDate.of(2026, 9, 25), 2, Fixtures.singapore, CalculationSettings())
        val text = ScheduleExporter.text(days, Fixtures.singapore, DisplaySettings()).lines()
        assertEquals("Singapore · Singapore (MUIS)", text[0])
        assertTrue(text[1].startsWith("Fri, 25 Sep  Fajr 05:37  Sunrise 06:54  Jumuah 12:58"), text[1])
        val ar = ScheduleExporter.text(days, Fixtures.singapore, DisplaySettings(language = AppLanguage.AR)).lines()
        assertTrue(ar[1].contains("الجمعة 12:58"), ar[1])
    }

}
