package com.cleanspeed.data

import kotlinx.coroutines.flow.Flow

class TripRepository(private val dao: TripDao) {
    fun observeRecentTrips(): Flow<List<TripEntity>> = dao.observeRecentTrips()

    suspend fun saveTrip(trip: TripEntity, points: List<TripPointEntity>) {
        val tripId = dao.insertTrip(trip)
        if (points.isNotEmpty()) {
            dao.insertPoints(points.map { it.copy(tripId = tripId) })
        }
    }

    suspend fun loadTrip(tripId: Long): TripWithPoints? {
        val trip = dao.getTripById(tripId) ?: return null
        return TripWithPoints(trip, dao.getPointsForTrip(tripId))
    }
}
