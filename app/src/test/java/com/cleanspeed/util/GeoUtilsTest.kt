package com.cleanspeed.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {
    @Test
    fun haversineDistance_sanityCheck() {
        val sfLat = 37.7749
        val sfLon = -122.4194
        val oakLat = 37.8044
        val oakLon = -122.2711

        val distance = GeoUtils.haversineDistanceMeters(sfLat, sfLon, oakLat, oakLon)

        assertTrue(distance in 12_000.0..15_000.0)
    }

    @Test
    fun unitConversion_speedAndDistance() {
        assertEquals(36.0, Units.speedFromMps(10.0, SpeedUnit.KMH), 0.0001)
        assertEquals(22.3693, Units.speedFromMps(10.0, SpeedUnit.MPH), 0.001)
        assertEquals(19.4384, Units.speedFromMps(10.0, SpeedUnit.KNOTS), 0.001)

        assertEquals(1.0, Units.distanceFromMeters(1000.0, SpeedUnit.KMH), 0.0001)
        assertEquals(0.62137, Units.distanceFromMeters(1000.0, SpeedUnit.MPH), 0.0001)
    }
}
