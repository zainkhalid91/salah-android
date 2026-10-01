package salah.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

@Serializable
data class SavedLocation(
    val name: String = "Custom location",
    val latitude: Double,
    val longitude: Double,
    /** IANA identifier. All times are computed and shown in this zone, never the machine's. */
    val timeZone: String,
    val countryCode: String? = null,
    val source: Source = Source.MANUAL,
) {
    @Serializable
    enum class Source {
        @SerialName("automatic") AUTOMATIC,
        @SerialName("manual") MANUAL,
    }

    /** The location's zone; falls back to the machine zone only if the stored identifier is unknown. */
    val zone: ZoneId get() = zoneOrNull(timeZone) ?: ZoneId.systemDefault()

    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0 && zoneOrNull(timeZone) != null

    /** e.g. "1.3521° N, 103.8198° E" */
    val coordinateDescription: String
        get() {
            val lat = String.format(Locale.ROOT, "%.4f° %s", abs(latitude), if (latitude >= 0) "N" else "S")
            val lon = String.format(Locale.ROOT, "%.4f° %s", abs(longitude), if (longitude >= 0) "E" else "W")
            return "$lat, $lon"
        }

    internal fun normalized() = this

    companion object {
        fun zoneOrNull(id: String): ZoneId? = if (id.isBlank()) null else runCatching { ZoneId.of(id) }.getOrNull()
    }
}
