package com.cleanspeed.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TripPointEntity>)

    @Query("SELECT * FROM trips ORDER BY startTime DESC LIMIT 20")
    fun observeRecentTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: Long): TripEntity?

    @Query("SELECT * FROM trip_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPointsForTrip(tripId: Long): List<TripPointEntity>
}
