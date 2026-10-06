package salah.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Direction and distance to the Kaaba, on the great circle (the shortest path over the earth). */
object Qibla {
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Degrees clockwise from true north, 0 until 360. */
    fun bearing(latitude: Double, longitude: Double): Double {
        val p1 = Math.toRadians(latitude)
        val p2 = Math.toRadians(KAABA_LATITUDE)
        val dl = Math.toRadians(KAABA_LONGITUDE - longitude)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    /** Great circle distance to the Kaaba (haversine). */
    fun distanceKm(latitude: Double, longitude: Double): Double {
        val p1 = Math.toRadians(latitude)
        val p2 = Math.toRadians(KAABA_LATITUDE)
        val dp = p2 - p1
        val dl = Math.toRadians(KAABA_LONGITUDE - longitude)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360

    /** How far to turn from [heading] to face [target]: negative is left, positive is right, -180..180. */
    fun turn(heading: Double, target: Double): Double {
        val d = normalize(target - heading)
        return if (d > 180) d - 360 else d
    }

    /** "NW", or its Arabic name. Eight points of the compass. */
    fun compassPoint(degrees: Double, lang: AppLanguage = AppLanguage.EN): String {
        val i = ((normalize(degrees) / 45.0).roundToInt()) % 8
        return AppText.t(lang, POINTS[i])
    }

    private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
}
