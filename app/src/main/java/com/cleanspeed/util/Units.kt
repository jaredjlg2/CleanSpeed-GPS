package com.cleanspeed.util

enum class SpeedUnit(val label: String) {
    MPH("mph"),
    KMH("km/h"),
    KNOTS("kn")
}

object Units {
    fun speedFromMps(speedMps: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.MPH -> speedMps * 2.23693629
        SpeedUnit.KMH -> speedMps * 3.6
        SpeedUnit.KNOTS -> speedMps * 1.94384449
    }

    fun distanceFromMeters(meters: Double, unit: SpeedUnit): Double = when (unit) {
        SpeedUnit.MPH -> meters / 1609.344
        SpeedUnit.KMH -> meters / 1000.0
        SpeedUnit.KNOTS -> meters / 1852.0
    }

    fun distanceLabel(unit: SpeedUnit): String = when (unit) {
        SpeedUnit.MPH -> "mi"
        SpeedUnit.KMH -> "km"
        SpeedUnit.KNOTS -> "nm"
    }
}
