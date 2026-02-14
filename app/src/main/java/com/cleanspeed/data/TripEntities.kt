package com.cleanspeed.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    val unitsUsed: String
)

@Entity(tableName = "trip_points")
data class TripPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val speedMps: Double
)

data class TripWithPoints(
    val trip: TripEntity,
    val points: List<TripPointEntity>
)
