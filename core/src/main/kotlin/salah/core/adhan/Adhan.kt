/*
 * Kotlin port of adhan-swift 1.4.0 by Batoul Apps (https://github.com/batoulapps/adhan-swift).
 *
 * Copyright (c) 2016 Batoul Apps. MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * The port keeps the Swift library's arithmetic step for step (including its
 * second-level truncation and half-away-from-zero rounding), so the Windows app
 * produces the same minute as the macOS app for every method.
 */
package salah.core.adhan

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

enum class CalculationMethod {
    MUSLIM_WORLD_LEAGUE, EGYPTIAN, KARACHI, UMM_AL_QURA, DUBAI, MOONSIGHTING_COMMITTEE,
    NORTH_AMERICA, KUWAIT, QATAR, SINGAPORE, TEHRAN, TURKEY, OTHER;

    val params: CalculationParameters
        get() = when (this) {
            MUSLIM_WORLD_LEAGUE -> CalculationParameters(18.0, 17.0, method = this, methodAdjustments = PrayerAdjustments(dhuhr = 1))
            EGYPTIAN -> CalculationParameters(19.5, 17.5, method = this, methodAdjustments = PrayerAdjustments(dhuhr = 1))
            KARACHI -> CalculationParameters(18.0, 18.0, method = this, methodAdjustments = PrayerAdjustments(dhuhr = 1))
            UMM_AL_QURA -> CalculationParameters(18.5, 0.0, ishaInterval = 90, method = this)
            DUBAI -> CalculationParameters(
                18.2, 18.2, method = this,
                methodAdjustments = PrayerAdjustments(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
            )
            MOONSIGHTING_COMMITTEE -> CalculationParameters(18.0, 18.0, method = this, methodAdjustments = PrayerAdjustments(dhuhr = 5, maghrib = 3))
            NORTH_AMERICA -> CalculationParameters(15.0, 15.0, method = this, methodAdjustments = PrayerAdjustments(dhuhr = 1))
            KUWAIT -> CalculationParameters(18.0, 17.5, method = this)
            QATAR -> CalculationParameters(18.0, 0.0, ishaInterval = 90, method = this)
            SINGAPORE -> CalculationParameters(
                20.0, 18.0, method = this, methodAdjustments = PrayerAdjustments(dhuhr = 1), rounding = Rounding.UP,
            )
            TEHRAN -> CalculationParameters(17.7, 14.0, maghribAngle = 4.5, method = this)
            TURKEY -> CalculationParameters(
                18.0, 17.0, method = this,
                methodAdjustments = PrayerAdjustments(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 7),
            )
            OTHER -> CalculationParameters(0.0, 0.0, method = this)
        }
}

enum class Madhab(val shadowLength: Double) { SHAFI(1.0), HANAFI(2.0) }

enum class HighLatitudeRule {
    MIDDLE_OF_THE_NIGHT, SEVENTH_OF_THE_NIGHT, TWILIGHT_ANGLE;

    companion object {
        fun recommended(coordinates: Coordinates): HighLatitudeRule =
            if (coordinates.latitude > 48) SEVENTH_OF_THE_NIGHT else MIDDLE_OF_THE_NIGHT
    }
}

enum class Rounding { NEAREST, UP, NONE }

enum class Shafaq { GENERAL, AHMER, ABYAD }

/** Adjustment values for prayer times, in minutes. */
data class PrayerAdjustments(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

data class Coordinates(val latitude: Double, val longitude: Double) {
    internal val latitudeAngle get() = Angle(latitude)
    internal val longitudeAngle get() = Angle(longitude)
}

data class CalculationParameters(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaInterval: Int = 0,
    val maghribAngle: Double? = null,
    val method: CalculationMethod = CalculationMethod.OTHER,
    val madhab: Madhab = Madhab.SHAFI,
    /** `null` means the recommendation for the latitude. */
    val highLatitudeRule: HighLatitudeRule? = null,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val rounding: Rounding = Rounding.NEAREST,
    val shafaq: Shafaq = Shafaq.GENERAL,
    internal val methodAdjustments: PrayerAdjustments = PrayerAdjustments(),
) {
    internal fun nightPortions(coordinates: Coordinates): Pair<Double, Double> =
        when (highLatitudeRule ?: HighLatitudeRule.recommended(coordinates)) {
            HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> 0.5 to 0.5
            HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> 1.0 / 7 to 1.0 / 7
            HighLatitudeRule.TWILIGHT_ANGLE -> fajrAngle / 60 to ishaAngle / 60
        }
}

/**
 * Prayer times for a location and a Gregorian date, as UTC instants. `compute` returns null when
 * any time can't be determined (polar day or night), exactly like adhan-swift's failable init.
 */
class PrayerTimes private constructor(
    val fajr: Instant,
    val sunrise: Instant,
    val dhuhr: Instant,
    val asr: Instant,
    val maghrib: Instant,
    val isha: Instant,
) {
    companion object {
        fun compute(coordinates: Coordinates, date: LocalDate, params: CalculationParameters): PrayerTimes? {
            val tomorrow = date.plusDays(1)
            val solar = SolarTime.of(date, coordinates) ?: return null
            val tomorrowSolar = SolarTime.of(tomorrow, coordinates) ?: return null
            val sunriseDate = solar.sunrise
            val sunsetDate = solar.sunset
            val tomorrowSunrise = tomorrowSolar.sunrise

            var fajr: Double? = null
            var isha: Double? = null
            var maghrib: Double? = sunsetDate
            val dhuhr: Double = solar.transit
            val asr: Double? = solar.afternoon(params.madhab.shadowLength)
            val night = tomorrowSunrise - sunsetDate
            val isMsc = params.method == CalculationMethod.MOONSIGHTING_COMMITTEE

            fajr = solar.timeForSolarAngle(Angle(-params.fajrAngle), afterTransit = false)
            if (isMsc && coordinates.latitude >= 55) fajr = sunriseDate - night / 7

            val safeFajr = if (isMsc) {
                Astronomical.seasonAdjustedMorningTwilight(coordinates.latitude, date.dayOfYear, date.year, sunriseDate)
            } else {
                sunriseDate - params.nightPortions(coordinates).first * night
            }
            if (fajr == null || fajr < safeFajr) fajr = safeFajr

            if (params.ishaInterval > 0) {
                isha = maghrib?.plus(params.ishaInterval * 60.0)
            } else {
                isha = solar.timeForSolarAngle(Angle(-params.ishaAngle), afterTransit = true)
                if (isMsc && coordinates.latitude >= 55) isha = sunsetDate + night / 7
                val safeIsha = if (isMsc) {
                    Astronomical.seasonAdjustedEveningTwilight(coordinates.latitude, date.dayOfYear, date.year, sunsetDate, params.shafaq)
                } else {
                    sunsetDate + params.nightPortions(coordinates).second * night
                }
                if (isha == null || isha > safeIsha) isha = safeIsha
            }

            params.maghribAngle?.let { angle ->
                val m = solar.timeForSolarAngle(Angle(-angle), afterTransit = true)
                if (m != null && sunsetDate < m && (isha == null || isha > m)) maghrib = m
            }

            if (asr == null || maghrib == null || isha == null) return null
            val a = params.adjustments
            val ma = params.methodAdjustments
            fun finish(t: Double, adj: Int, methodAdj: Int) =
                Instant.ofEpochSecond(roundedMinute(t + adj * 60.0 + methodAdj * 60.0, params.rounding))
            return PrayerTimes(
                fajr = finish(fajr, a.fajr, ma.fajr),
                sunrise = finish(sunriseDate, a.sunrise, ma.sunrise),
                dhuhr = finish(dhuhr, a.dhuhr, ma.dhuhr),
                asr = finish(asr, a.asr, ma.asr),
                maghrib = finish(maghrib!!, a.maghrib, ma.maghrib),
                isha = finish(isha, a.isha, ma.isha),
            )
        }

        /** Mirrors `Date.roundedMinute`: works on whole seconds, then rounds to the minute. */
        internal fun roundedMinute(t: Double, rounding: Rounding): Long {
            val s = floor(t).toLong()
            val sec = Math.floorMod(s, 60L)
            val base = s - sec
            return when (rounding) {
                Rounding.NEAREST -> base + 60L * swiftRound(sec / 60.0).toLong()
                Rounding.UP -> base + 60L * ceil(sec / 60.0).toLong()
                Rounding.NONE -> s
            }
        }
    }
}

// region Internals

/** Swift's `round`/`rounded()`: half away from zero (Kotlin's `round` is half-even). */
internal fun swiftRound(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

internal fun Double.normalizedToScale(max: Double): Double = this - max * floor(this / max)

internal data class Angle(val degrees: Double) {
    val radians: Double get() = degrees * PI / 180.0
    fun unwound() = Angle(degrees.normalizedToScale(360.0))
    fun quadrantShifted(): Angle =
        if (degrees >= -180 && degrees <= 180) this else Angle(degrees - 360 * swiftRound(degrees / 360))

    operator fun plus(o: Angle) = Angle(degrees + o.degrees)
    operator fun minus(o: Angle) = Angle(degrees - o.degrees)
    operator fun times(o: Angle) = Angle(degrees * o.degrees)
    operator fun div(o: Angle) = Angle(degrees / o.degrees)

    companion object {
        fun fromRadians(r: Double) = Angle(r * 180.0 / PI)
    }
}

/** Epoch seconds at 00:00 UTC of a date. */
private fun midnightUtc(date: LocalDate): Long = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

/** `DateComponents.settingHour`: hours since UTC midnight, truncated to whole seconds. */
private fun settingHour(date: LocalDate, value: Double): Double? {
    if (value.isNaN() || value.isInfinite() || value == 0.0 || abs(value) < java.lang.Double.MIN_NORMAL) return null
    val h = floor(value)
    val m = floor((value - h) * 60)
    val s = floor((value - (h + m / 60)) * 60 * 60)
    return midnightUtc(date) + h * 3600 + m * 60 + s
}

internal class SolarCoordinates(julianDay: Double) {
    val declination: Angle
    val rightAscension: Angle
    val apparentSiderealTime: Angle

    init {
        val t = Astronomical.julianCentury(julianDay)
        val l0 = Astronomical.meanSolarLongitude(t)
        val lp = Astronomical.meanLunarLongitude(t)
        val omega = Astronomical.ascendingLunarNodeLongitude(t)
        val lambda = Astronomical.apparentSolarLongitude(t, l0).radians
        val theta0 = Astronomical.meanSiderealTime(t)
        val dPsi = Astronomical.nutationInLongitude(l0, lp, omega)
        val dEpsilon = Astronomical.nutationInObliquity(l0, lp, omega)
        val epsilon0 = Astronomical.meanObliquityOfTheEcliptic(t)
        val epsilonApp = Astronomical.apparentObliquityOfTheEcliptic(t, epsilon0).radians
        declination = Angle.fromRadians(asin(sin(epsilonApp) * sin(lambda)))
        rightAscension = Angle.fromRadians(atan2(cos(epsilonApp) * sin(lambda), cos(lambda))).unwound()
        apparentSiderealTime = Angle(theta0.degrees + (((dPsi * 3600) * cos(Angle(epsilon0.degrees + dEpsilon).radians)) / 3600))
    }
}

/** Solar events for one UTC date, as epoch seconds. */
internal class SolarTime private constructor(
    private val date: LocalDate,
    private val observer: Coordinates,
    private val solar: SolarCoordinates,
    private val prevSolar: SolarCoordinates,
    private val nextSolar: SolarCoordinates,
    private val approxTransit: Double,
    val transit: Double,
    val sunrise: Double,
    val sunset: Double,
) {
    companion object {
        fun of(date: LocalDate, coordinates: Coordinates): SolarTime? {
            val jd = Astronomical.julianDay(date.year, date.monthValue, date.dayOfMonth, 0.0)
            val prev = SolarCoordinates(jd - 1)
            val solar = SolarCoordinates(jd)
            val next = SolarCoordinates(jd + 1)
            val m0 = Astronomical.approximateTransit(coordinates.longitudeAngle, solar.apparentSiderealTime, solar.rightAscension)
            val altitude = Angle(-50.0 / 60.0)
            val transitTime = Astronomical.correctedTransit(
                m0, coordinates.longitudeAngle, solar.apparentSiderealTime,
                solar.rightAscension, prev.rightAscension, next.rightAscension,
            )
            fun hourAngle(after: Boolean) = Astronomical.correctedHourAngle(
                m0, altitude, coordinates, after, solar.apparentSiderealTime,
                solar.rightAscension, prev.rightAscension, next.rightAscension,
                solar.declination, prev.declination, next.declination,
            )
            val transit = settingHour(date, transitTime) ?: return null
            val sunrise = settingHour(date, hourAngle(false)) ?: return null
            val sunset = settingHour(date, hourAngle(true)) ?: return null
            return SolarTime(date, coordinates, solar, prev, next, m0, transit, sunrise, sunset)
        }
    }

    fun timeForSolarAngle(angle: Angle, afterTransit: Boolean): Double? {
        val hours = Astronomical.correctedHourAngle(
            approxTransit, angle, observer, afterTransit, solar.apparentSiderealTime,
            solar.rightAscension, prevSolar.rightAscension, nextSolar.rightAscension,
            solar.declination, prevSolar.declination, nextSolar.declination,
        )
        return settingHour(date, hours)
    }

    fun afternoon(shadowLength: Double): Double? {
        val tangent = Angle(abs(observer.latitude - solar.declination.degrees))
        val inverse = shadowLength + tan(tangent.radians)
        return timeForSolarAngle(Angle.fromRadians(atan(1.0 / inverse)), afterTransit = true)
    }
}

internal object Astronomical {
    fun meanSolarLongitude(t: Double) = Angle(280.4664567 + 36000.76983 * t + 0.0003032 * t.pow(2)).unwound()

    fun meanLunarLongitude(t: Double) = Angle(218.3165 + 481267.8813 * t).unwound()

    fun ascendingLunarNodeLongitude(t: Double) =
        Angle(125.04452 - 1934.136261 * t + 0.0020708 * t.pow(2) + t.pow(3) / 450000).unwound()

    fun meanSolarAnomaly(t: Double) = Angle(357.52911 + 35999.05029 * t - 0.0001537 * t.pow(2)).unwound()

    fun solarEquationOfTheCenter(t: Double, m: Angle): Angle {
        val mr = m.radians
        val term1 = (1.914602 - (0.004817 * t) - (0.000014 * t.pow(2))) * sin(mr)
        val term2 = (0.019993 - (0.000101 * t)) * sin(2 * mr)
        val term3 = 0.000289 * sin(3 * mr)
        return Angle(term1 + term2 + term3)
    }

    fun apparentSolarLongitude(t: Double, l0: Angle): Angle {
        val longitude = l0 + solarEquationOfTheCenter(t, meanSolarAnomaly(t))
        val omega = Angle(125.04 - (1934.136 * t))
        return Angle(longitude.degrees - 0.00569 - (0.00478 * sin(omega.radians))).unwound()
    }

    fun meanObliquityOfTheEcliptic(t: Double) =
        Angle(23.439291 - 0.013004167 * t - 0.0000001639 * t.pow(2) + 0.0000005036 * t.pow(3))

    fun apparentObliquityOfTheEcliptic(t: Double, e0: Angle): Angle {
        val o = 125.04 - (1934.136 * t)
        return Angle(e0.degrees + (0.00256 * cos(Angle(o).radians)))
    }

    fun meanSiderealTime(t: Double): Angle {
        val jd = (t * 36525) + 2451545.0
        val theta = 280.46061837 + 360.98564736629 * (jd - 2451545) + 0.000387933 * t.pow(2) - t.pow(3) / 38710000
        return Angle(theta).unwound()
    }

    fun nutationInLongitude(l0: Angle, lp: Angle, omega: Angle): Double {
        val term1 = (-17.2 / 3600) * sin(omega.radians)
        val term2 = (1.32 / 3600) * sin(2 * l0.radians)
        val term3 = (0.23 / 3600) * sin(2 * lp.radians)
        val term4 = (0.21 / 3600) * sin(2 * omega.radians)
        return term1 - term2 - term3 + term4
    }

    fun nutationInObliquity(l0: Angle, lp: Angle, omega: Angle): Double {
        val term1 = (9.2 / 3600) * cos(omega.radians)
        val term2 = (0.57 / 3600) * cos(2 * l0.radians)
        val term3 = (0.10 / 3600) * cos(2 * lp.radians)
        val term4 = (0.09 / 3600) * cos(2 * omega.radians)
        return term1 + term2 + term3 - term4
    }

    fun altitudeOfCelestialBody(phi: Angle, delta: Angle, h: Angle): Angle {
        val term1 = sin(phi.radians) * sin(delta.radians)
        val term2 = cos(phi.radians) * cos(delta.radians) * cos(h.radians)
        return Angle.fromRadians(asin(term1 + term2))
    }

    fun approximateTransit(l: Angle, theta0: Angle, alpha2: Angle): Double {
        val lw = l * Angle(-1.0)
        return ((alpha2 + lw - theta0) / Angle(360.0)).degrees.normalizedToScale(1.0)
    }

    fun correctedTransit(m0: Double, l: Angle, theta0: Angle, alpha2: Angle, alpha1: Angle, alpha3: Angle): Double {
        val lw = l * Angle(-1.0)
        val theta = Angle(theta0.degrees + (360.985647 * m0)).unwound()
        val alpha = interpolateAngles(alpha2, alpha1, alpha3, m0).unwound()
        val h = (theta - lw - alpha).quadrantShifted()
        val dm = h / Angle(-360.0)
        return (m0 + dm.degrees) * 24
    }

    fun correctedHourAngle(
        m0: Double, h0: Angle, coordinates: Coordinates, afterTransit: Boolean, theta0: Angle,
        alpha2: Angle, alpha1: Angle, alpha3: Angle, delta2: Angle, delta1: Angle, delta3: Angle,
    ): Double {
        val lw = coordinates.longitudeAngle * Angle(-1.0)
        val term1 = sin(h0.radians) - (sin(coordinates.latitudeAngle.radians) * sin(delta2.radians))
        val term2 = cos(coordinates.latitudeAngle.radians) * cos(delta2.radians)
        val bigH0 = Angle.fromRadians(acos(term1 / term2))
        val m = if (afterTransit) m0 + (bigH0.degrees / 360) else m0 - (bigH0.degrees / 360)
        val theta = Angle(theta0.degrees + (360.985647 * m)).unwound()
        val alpha = interpolateAngles(alpha2, alpha1, alpha3, m).unwound()
        val delta = Angle(interpolate(delta2.degrees, delta1.degrees, delta3.degrees, m))
        val bigH = theta - lw - alpha
        val h = altitudeOfCelestialBody(coordinates.latitudeAngle, delta, bigH)
        val term3 = (h - h0).degrees
        val term4 = 360 * cos(delta.radians) * cos(coordinates.latitudeAngle.radians) * sin(bigH.radians)
        val dm = term3 / term4
        return (m + dm) * 24
    }

    fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = y2 - y1
        val b = y3 - y2
        val c = b - a
        return y2 + ((n / 2) * (a + b + (n * c)))
    }

    fun interpolateAngles(y2: Angle, y1: Angle, y3: Angle, n: Double): Angle {
        val a = (y2 - y1).unwound()
        val b = (y3 - y2).unwound()
        val c = b - a
        return Angle(y2.degrees + ((n / 2) * (a.degrees + b.degrees + (n * c.degrees))))
    }

    fun julianDay(year: Int, month: Int, day: Int, hours: Double): Double {
        val y = if (month > 2) year else year - 1
        val m = if (month > 2) month else month + 12
        val d = day + (hours / 24)
        val a = y / 100
        val b = 2 - a + (a / 4)
        val i0 = (365.25 * (y + 4716)).toInt()
        val i1 = (30.6001 * (m + 1)).toInt()
        return i0 + i1 + d + b - 1524.5
    }

    fun julianCentury(jd: Double) = (jd - 2451545.0) / 36525

    fun isLeapYear(year: Int): Boolean {
        if (year % 4 != 0) return false
        if (year % 100 == 0 && year % 400 != 0) return false
        return true
    }

    private fun seasonal(a: Double, b: Double, c: Double, d: Double, dyy: Double): Double = when {
        dyy < 91 -> a + (b - a) / 91.0 * dyy
        dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
        dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
        dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
        dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
        else -> b + (a - b) / 91.0 * (dyy - 275)
    }

    fun seasonAdjustedMorningTwilight(latitude: Double, day: Int, year: Int, sunrise: Double): Double {
        val lat = abs(latitude)
        val a = 75 + ((28.65 / 55.0) * lat)
        val b = 75 + ((19.44 / 55.0) * lat)
        val c = 75 + ((32.74 / 55.0) * lat)
        val d = 75 + ((48.10 / 55.0) * lat)
        val adj = seasonal(a, b, c, d, daysSinceSolstice(day, year, latitude).toDouble())
        return sunrise + swiftRound(adj * -60.0)
    }

    fun seasonAdjustedEveningTwilight(latitude: Double, day: Int, year: Int, sunset: Double, shafaq: Shafaq): Double {
        val lat = abs(latitude)
        val (a, b, c, d) = when (shafaq) {
            Shafaq.GENERAL -> listOf(75 + ((25.60 / 55.0) * lat), 75 + ((2.050 / 55.0) * lat), 75 - ((9.210 / 55.0) * lat), 75 + ((6.140 / 55.0) * lat))
            Shafaq.AHMER -> listOf(62 + ((17.40 / 55.0) * lat), 62 - ((7.160 / 55.0) * lat), 62 + ((5.120 / 55.0) * lat), 62 + ((19.44 / 55.0) * lat))
            Shafaq.ABYAD -> listOf(75 + ((25.60 / 55.0) * lat), 75 + ((7.160 / 55.0) * lat), 75 + ((36.84 / 55.0) * lat), 75 + ((81.84 / 55.0) * lat))
        }
        val adj = seasonal(a, b, c, d, daysSinceSolstice(day, year, latitude).toDouble())
        return sunset + swiftRound(adj * 60.0)
    }

    fun daysSinceSolstice(dayOfYear: Int, year: Int, latitude: Double): Int {
        val northernOffset = 10
        val southernOffset = if (isLeapYear(year)) 173 else 172
        val daysInYear = if (isLeapYear(year)) 366 else 365
        return if (latitude >= 0) {
            val d = dayOfYear + northernOffset
            if (d >= daysInYear) d - daysInYear else d
        } else {
            val d = dayOfYear - southernOffset
            if (d < 0) d + daysInYear else d
        }
    }
}

// endregion
