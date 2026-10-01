package salah.core

import java.nio.file.Files
import java.time.LocalDate
import kotlin.concurrent.thread
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTest {
    @Test
    fun roundTrip() {
        val store = ConfigStore(Fixtures.tempPath())
        val c = Fixtures.config(Fixtures.singapore, MethodID.CUSTOM).let {
            it.copy(
                calculation = it.calculation.withOffset(2, Prayer.ISHA),
                display = it.display.copy(use24HourClock = false),
                reminders = it.reminders.withPausedUntil(Fixtures.date("2026-09-28T00:00:00+08:00"))
                    .copy(quietHours = QuietHours(true, "22:30", "05:00")),
            )
        }
        store.save(c)
        assertEquals(c, store.load())
    }

    @Test
    fun missingFileLoadsDefaults() = assertEquals(SalahConfig.DEFAULT, ConfigStore(Fixtures.tempPath()).load())

    /** A config.json written by the macOS app must load unchanged. */
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
        val path = Fixtures.tempPath()
        Files.createDirectories(path.parent)
        Files.writeString(path, "{ not json")
        val e = assertFailsWith<ConfigException.Corrupt> { ConfigStore(path).load() }
        assertEquals(path.toString(), e.path)
        Files.writeString(path, """{"location":{"latitude":"north"}}""")
        assertFailsWith<ConfigException.Corrupt> { ConfigStore(path).load() }
    }

    @Test
    fun atomicWriteSurvivesConcurrentReads() {
        val store = ConfigStore(Fixtures.tempPath())
        store.save(Fixtures.config(Fixtures.singapore))
        val writer = thread {
            for (i in 0 until 200) {
                val c = Fixtures.config(if (i % 2 == 0) Fixtures.singapore else Fixtures.jakarta)
                store.save(c.copy(calculation = c.calculation.withOffset(i % 30, Prayer.ISHA)))
            }
        }
        repeat(500) { store.load() }
        writer.join()
        assertEquals(listOf("config.json"), store.path.parent.listDirectoryEntries().map { it.name })
    }

    @Test
    fun configKeysGetAndSet() {
        var c = SalahConfig.DEFAULT
        c = ConfigKeys.find("calculation.method").set(c, "singapore")
        assertEquals(MethodID.SINGAPORE, c.calculation.method)
        c = ConfigKeys.find("calculation.method").set(c, "auto")
        assertNull(c.calculation.method)
        c = ConfigKeys.find("calculation.offsets.isha").set(c, "-3")
        assertEquals(-3, c.calculation.offset(Prayer.ISHA))
        c = ConfigKeys.find("reminders.asr.leadMinutes").set(c, "15")
        assertEquals(15, c.reminders.reminder(Prayer.ASR).leadMinutes)
        c = ConfigKeys.find("display.clock").set(c, "12")
        assertFalse(c.display.use24HourClock)
        c = ConfigKeys.find("reminders.quietHours.start").set(c, "9:05")
        assertEquals("09:05", c.reminders.quietHours.start)

        assertFailsWith<ConfigKeyException.UnknownKey> { ConfigKeys.find("nope") }
        assertFailsWith<ConfigKeyException.InvalidValue> { ConfigKeys.find("display.clock").set(c, "13") }
        assertFailsWith<ConfigKeyException.InvalidValue> { ConfigKeys.find("reminders.asr.leadMinutes").set(c, "7") }
        assertFailsWith<ConfigKeyException.InvalidValue> { ConfigKeys.find("display.hijriAdjustment").set(c, "3") }
        assertFailsWith<ConfigKeyException.ReadOnly> { ConfigKeys.find("location.name").set(c, "X") }
    }

    @Test
    fun exporters() {
        val days = PrayerSchedule.range(LocalDate.of(2026, 9, 25), 2, Fixtures.singapore, CalculationSettings())
        val rows = ScheduleExporter.csv(days).split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, rows.size)
        assertTrue(rows[1].startsWith("2026-09-25,Friday,05:37,06:54,12:58,"), rows[1])
        assertTrue(rows[1].endsWith(",Asia/Singapore,Singapore (MUIS)"), rows[1])
        val ics = ScheduleExporter.ics(days, Fixtures.singapore, true, Fixtures.date("2026-09-25T00:00:00Z"))
        assertEquals(10, ics.split("BEGIN:VEVENT").size - 1)
        assertTrue(ics.contains("SUMMARY:Jumu'ah"))
        assertTrue(ics.contains("UID:salah-2026-09-25-dhuhr@salah.local"))
    }

    @Test
    fun semanticVersions() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 0), SemanticVersion.parse("1.2.0-beta.1"))
        assertEquals(SemanticVersion(2), SemanticVersion.parse("2"))
        assertNull(SemanticVersion.parse("1.x"))
        assertNull(SemanticVersion.parse(""))
        assertTrue(SemanticVersion.parse("1.10.0")!! > SemanticVersion.parse("1.9.9")!!)
    }
}
