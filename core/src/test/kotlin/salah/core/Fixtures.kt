package salah.core

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime

object Fixtures {
    val singapore = SavedLocation("Singapore", 1.3521, 103.8198, "Asia/Singapore", "SG")
    val jakarta = SavedLocation("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", "ID")
    val mecca = SavedLocation("Mecca", 21.4225, 39.8262, "Asia/Riyadh", "SA")
    val newYork = SavedLocation("New York", 40.7128, -74.0060, "America/New_York", "US")
    val london = SavedLocation("London", 51.5074, -0.1278, "Europe/London", "GB")
    val oslo = SavedLocation("Oslo", 59.9139, 10.7522, "Europe/Oslo", "NO")
    val tromso = SavedLocation("Tromsø", 69.6492, 18.9553, "Europe/Oslo", "NO")
    val karachi = SavedLocation("Karachi", 24.8607, 67.0011, "Asia/Karachi", "PK")
    val cairo = SavedLocation("Cairo", 30.0444, 31.2357, "Africa/Cairo", "EG")
    val dubai = SavedLocation("Dubai", 25.2048, 55.2708, "Asia/Dubai", "AE")

    /** Parses an ISO 8601 instant, e.g. "2026-09-27T19:27:42+08:00". */
    fun date(s: String): Instant = OffsetDateTime.parse(s).toInstant()

    fun config(location: SavedLocation?, method: MethodID? = null) =
        SalahConfig(location = location, calculation = CalculationSettings(method = method), launchAtLogin = false)

    fun tempPath(name: String = "config.json"): Path = Files.createTempDirectory("salah-tests-").resolve(name)
}
