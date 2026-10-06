package salah.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QiblaTest {
    private fun near(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    @Test
    fun knownBearings() {
        // Published Qibla directions, to within half a degree.
        near(58.5, Qibla.bearing(40.7128, -74.0060), 0.5) // New York
        near(119.0, Qibla.bearing(51.5074, -0.1278), 0.5) // London
        near(295.2, Qibla.bearing(-6.2088, 106.8456), 0.5) // Jakarta
        near(267.5, Qibla.bearing(24.8607, 67.0011), 0.8) // Karachi
    }

    @Test
    fun distances() {
        near(2800.0, Qibla.distanceKm(24.8607, 67.0011), 30.0) // Karachi to Makkah, about 2,800 km
        near(0.0, Qibla.distanceKm(Qibla.KAABA_LATITUDE, Qibla.KAABA_LONGITUDE), 0.001)
    }

    @Test
    fun turnsTheShortWay() {
        assertEquals(20.0, Qibla.turn(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Qibla.turn(10.0, 350.0), 1e-9)
        assertEquals(0.0, Qibla.turn(292.0, 292.0), 1e-9)
    }

    @Test
    fun compassPoints() {
        assertEquals("N", Qibla.compassPoint(359.0))
        assertEquals("W", Qibla.compassPoint(267.5))
        assertEquals("ش غ", Qibla.compassPoint(300.0, AppLanguage.AR))
    }
}
