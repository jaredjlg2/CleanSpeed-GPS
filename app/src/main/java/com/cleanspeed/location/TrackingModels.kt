package com.cleanspeed.location

enum class TripStatus { IDLE, RUNNING, PAUSED, STOPPED }

data class SamplePoint(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val speedMps: Double
)

data class TrackingState(
    val status: TripStatus = TripStatus.IDLE,
    val startTimeMillis: Long? = null,
    val elapsedSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val gpsWeak: Boolean = false,
    val sampledPoints: List<SamplePoint> = emptyList()
)
