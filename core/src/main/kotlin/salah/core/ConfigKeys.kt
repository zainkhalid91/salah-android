package salah.core

import java.util.Locale

sealed class ConfigKeyException(message: String) : Exception(message) {
    class UnknownKey(key: String) : ConfigKeyException("Unknown config key “$key”. Run `salah config get` to list keys.")
    class ReadOnly(key: String) : ConfigKeyException("“$key” is read-only here. Use `salah location set` to change the location.")
    class InvalidValue(key: String, value: String, expected: String) :
        ConfigKeyException("Invalid value “$value” for $key. Expected $expected.")
}

/** Dotted-path access to config values for `salah config get/set`. Keys match the macOS CLI. */
class ConfigKey(
    val key: String,
    val expected: String,
    private val getter: (SalahConfig) -> String,
    private val setter: ((SalahConfig, String) -> SalahConfig)?,
) {
    val isReadOnly: Boolean get() = setter == null

    fun get(c: SalahConfig): String = getter(c)

    fun set(c: SalahConfig, value: String): SalahConfig = (setter ?: throw ConfigKeyException.ReadOnly(key))(c, value)
}

object ConfigKeys {
    fun find(key: String): ConfigKey = all.firstOrNull { it.key == key } ?: throw ConfigKeyException.UnknownKey(key)

    val all: List<ConfigKey> by lazy {
        val keys = mutableListOf(
            readOnly("location.name") { it.location?.name ?: "" },
            readOnly("location.latitude") { c -> c.location?.let { swiftDouble(it.latitude) } ?: "" },
            readOnly("location.longitude") { c -> c.location?.let { swiftDouble(it.longitude) } ?: "" },
            ConfigKey(
                "location.timeZone", "an IANA time zone, e.g. Asia/Singapore",
                { it.location?.timeZone ?: "" },
                { c, v ->
                    if (SavedLocation.zoneOrNull(v) == null || !v.contains('/') && v != "UTC") {
                        throw ConfigKeyException.InvalidValue("location.timeZone", v, "an IANA time zone, e.g. Asia/Singapore")
                    }
                    val loc = c.location ?: throw ConfigKeyException.ReadOnly("location.timeZone")
                    c.copy(location = loc.copy(timeZone = v))
                },
            ),
            enumKey(
                "calculation.method", listOf("auto") + MethodID.entries.map { it.raw },
                { it.calculation.method?.raw ?: "auto" },
                { c, v -> c.copy(calculation = c.calculation.copy(method = if (v == "auto") null else MethodID.fromRaw(v))) },
            ),
            enumKey(
                "calculation.madhab", MadhabSetting.entries.map { it.raw },
                { it.calculation.madhab.raw },
                { c, v -> c.copy(calculation = c.calculation.copy(madhab = MadhabSetting.fromRaw(v)!!)) },
            ),
            enumKey(
                "calculation.highLatitudeRule", listOf("auto") + HighLatitudeSetting.entries.map { it.raw },
                { it.calculation.highLatitudeRule?.raw ?: "auto" },
                { c, v -> c.copy(calculation = c.calculation.copy(highLatitudeRule = if (v == "auto") null else HighLatitudeSetting.fromRaw(v))) },
            ),
            doubleKey("calculation.customFajrAngle", 0.0..30.0, { it.calculation.customFajrAngle }) { c, v ->
                c.copy(calculation = c.calculation.copy(customFajrAngle = v))
            },
            doubleKey("calculation.customIshaAngle", 0.0..30.0, { it.calculation.customIshaAngle }) { c, v ->
                c.copy(calculation = c.calculation.copy(customIshaAngle = v))
            },
        )
        for (p in Prayer.entries) {
            keys += intKey("calculation.offsets.${p.raw}", -60..60, { it.calculation.offset(p) }) { c, v ->
                c.copy(calculation = c.calculation.withOffset(v, p))
            }
        }
        keys += listOf(
            enumKey(
                "display.clock", listOf("12", "24"),
                { if (it.display.use24HourClock) "24" else "12" },
                { c, v -> c.copy(display = c.display.copy(use24HourClock = v == "24")) },
            ),
            enumKey(
                "display.theme", ThemeSetting.entries.map { it.raw },
                { it.display.theme.raw },
                { c, v -> c.copy(display = c.display.copy(theme = ThemeSetting.entries.first { it.raw == v })) },
            ),
            boolKey("display.jumuahRelabel", { it.display.jumuahRelabel }) { c, v -> c.copy(display = c.display.copy(jumuahRelabel = v)) },
            intKey("display.hijriAdjustment", -2..2, { it.display.hijriAdjustment }) { c, v -> c.copy(display = c.display.copy(hijriAdjustment = v)) },
            intKey("display.nowWindowMinutes", 0..60, { it.display.nowWindowMinutes }) { c, v -> c.copy(display = c.display.copy(nowWindowMinutes = v)) },
            boolKey("display.showMenuBarExtra", { it.display.showMenuBarExtra }) { c, v -> c.copy(display = c.display.copy(showMenuBarExtra = v)) },
            enumKey(
                "display.menuBarStyle", MenuBarStyle.entries.map { it.raw },
                { it.display.menuBarStyle.raw },
                { c, v -> c.copy(display = c.display.copy(menuBarStyle = MenuBarStyle.entries.first { it.raw == v })) },
            ),
            boolKey("reminders.enabled", { it.reminders.enabled }) { c, v -> c.copy(reminders = c.reminders.copy(enabled = v)) },
            ConfigKey(
                "reminders.pausedUntil", "an ISO 8601 date-time, or “none”",
                { c -> c.reminders.pausedUntil?.let { Iso.instant(it) } ?: "none" },
                { c, v ->
                    if (v == "none" || v.isEmpty()) {
                        c.copy(reminders = c.reminders.withPausedUntil(null))
                    } else {
                        val d = Iso.parse(v)
                            ?: throw ConfigKeyException.InvalidValue("reminders.pausedUntil", v, "an ISO 8601 date-time, or “none”")
                        c.copy(reminders = c.reminders.withPausedUntil(d))
                    }
                },
            ),
            enumKey(
                "reminders.sound", ReminderSound.entries.map { it.raw },
                { it.reminders.sound.raw },
                { c, v -> c.copy(reminders = c.reminders.copy(sound = ReminderSound.entries.first { it.raw == v })) },
            ),
            boolKey("reminders.quietHours.enabled", { it.reminders.quietHours.enabled }) { c, v ->
                c.copy(reminders = c.reminders.copy(quietHours = c.reminders.quietHours.copy(enabled = v)))
            },
            timeKey("reminders.quietHours.start", { it.reminders.quietHours.start }) { c, v ->
                c.copy(reminders = c.reminders.copy(quietHours = c.reminders.quietHours.copy(start = v)))
            },
            timeKey("reminders.quietHours.end", { it.reminders.quietHours.end }) { c, v ->
                c.copy(reminders = c.reminders.copy(quietHours = c.reminders.quietHours.copy(end = v)))
            },
        )
        for (p in Prayer.prayers) {
            val base = "reminders.${p.raw}"
            keys += boolKey("$base.enabled", { it.reminders.reminder(p).enabled }) { c, v ->
                c.copy(reminders = c.reminders.update(p) { it.copy(enabled = v) })
            }
            keys += enumKey(
                "$base.leadMinutes", PrayerReminder.ALLOWED_LEADS.map { it.toString() },
                { it.reminders.reminder(p).leadMinutes.toString() },
                { c, v -> c.copy(reminders = c.reminders.update(p) { it.copy(leadMinutes = v.toInt()) }) },
            )
            keys += boolKey("$base.atTime", { it.reminders.reminder(p).atTime }) { c, v ->
                c.copy(reminders = c.reminders.update(p) { it.copy(atTime = v) })
            }
        }
        keys += boolKey("launchAtLogin", { it.launchAtLogin }) { c, v -> c.copy(launchAtLogin = v) }
        keys
    }

    /** Swift's `String(Double)`: "1.3521", "20.0". */
    internal fun swiftDouble(v: Double): String = v.toString()

    private fun readOnly(key: String, get: (SalahConfig) -> String) = ConfigKey(key, "", get, null)

    private fun enumKey(
        key: String, values: List<String>,
        get: (SalahConfig) -> String, set: (SalahConfig, String) -> SalahConfig,
    ): ConfigKey {
        val expected = "one of: " + values.joinToString(", ")
        return ConfigKey(key, expected, get) { c, v ->
            if (v !in values) throw ConfigKeyException.InvalidValue(key, v, expected)
            set(c, v)
        }
    }

    private fun boolKey(key: String, get: (SalahConfig) -> Boolean, set: (SalahConfig, Boolean) -> SalahConfig): ConfigKey {
        val expected = "true or false"
        return ConfigKey(key, expected, { get(it).toString() }) { c, v ->
            when (v.lowercase()) {
                "true", "on", "yes", "1" -> set(c, true)
                "false", "off", "no", "0" -> set(c, false)
                else -> throw ConfigKeyException.InvalidValue(key, v, expected)
            }
        }
    }

    private fun intKey(key: String, range: IntRange, get: (SalahConfig) -> Int, set: (SalahConfig, Int) -> SalahConfig): ConfigKey {
        val expected = "a whole number from ${range.first} to ${range.last}"
        return ConfigKey(key, expected, { get(it).toString() }) { c, v ->
            val n = v.toIntOrNull()
            if (n == null || n !in range) throw ConfigKeyException.InvalidValue(key, v, expected)
            set(c, n)
        }
    }

    private fun doubleKey(
        key: String, range: ClosedFloatingPointRange<Double>,
        get: (SalahConfig) -> Double, set: (SalahConfig, Double) -> SalahConfig,
    ): ConfigKey {
        val expected = "a number from ${range.start.toInt()} to ${range.endInclusive.toInt()}"
        return ConfigKey(key, expected, { swiftDouble(get(it)) }) { c, v ->
            val n = v.toDoubleOrNull()
            if (n == null || n !in range) throw ConfigKeyException.InvalidValue(key, v, expected)
            set(c, n)
        }
    }

    private fun timeKey(key: String, get: (SalahConfig) -> String, set: (SalahConfig, String) -> SalahConfig): ConfigKey {
        val expected = "a 24-hour time HH:MM"
        return ConfigKey(key, expected, get) { c, v ->
            val m = QuietHours.minutes(v) ?: throw ConfigKeyException.InvalidValue(key, v, expected)
            set(c, String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60))
        }
    }
}
