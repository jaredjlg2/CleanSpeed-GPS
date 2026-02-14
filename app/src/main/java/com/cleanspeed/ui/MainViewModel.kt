package com.cleanspeed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.cleanspeed.data.AppDatabase
import com.cleanspeed.data.TripEntity
import com.cleanspeed.data.TripPointEntity
import com.cleanspeed.data.TripRepository
import com.cleanspeed.data.UserPrefs
import com.cleanspeed.location.TripStatus
import com.cleanspeed.location.TripTracker
import com.cleanspeed.util.SpeedUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val tracking: com.cleanspeed.location.TrackingState = com.cleanspeed.location.TrackingState(),
    val unit: SpeedUnit = SpeedUnit.MPH,
    val keepScreenOn: Boolean = false,
    val recentTrips: List<TripEntity> = emptyList(),
    val selectedTrip: com.cleanspeed.data.TripWithPoints? = null,
    val permissionDenied: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(application, AppDatabase::class.java, "cleanspeed.db").build()
    private val repository = TripRepository(db.tripDao())
    private val prefs = UserPrefs(application)
    private val tracker = TripTracker.get(application)

    private val selectedTrip = MutableStateFlow<com.cleanspeed.data.TripWithPoints?>(null)
    private val permissionDenied = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        tracker.state,
        prefs.speedUnit,
        prefs.keepScreenOn,
        repository.observeRecentTrips(),
        selectedTrip,
        permissionDenied
    ) { tracking, unit, keepOn, trips, selected, denied ->
        MainUiState(tracking, unit, keepOn, trips, selected, denied)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    fun setPermissionDenied(denied: Boolean) {
        permissionDenied.value = denied
    }

    fun setUnit(unit: SpeedUnit) = viewModelScope.launch { prefs.setSpeedUnit(unit) }

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { prefs.setKeepScreenOn(enabled) }

    fun pauseOrResume() {
        when (uiState.value.tracking.status) {
            TripStatus.RUNNING -> tracker.pauseTrip()
            TripStatus.PAUSED -> tracker.resumeTrip()
            else -> Unit
        }
    }

    fun saveStoppedTrip() = viewModelScope.launch {
        val state = tracker.stopTrip()
        val start = state.startTimeMillis ?: return@launch
        if (state.elapsedSeconds == 0L && state.distanceMeters <= 0.0) return@launch

        val trip = TripEntity(
            startTime = start,
            endTime = System.currentTimeMillis(),
            durationSeconds = state.elapsedSeconds,
            distanceMeters = state.distanceMeters,
            avgSpeedMps = state.avgSpeedMps,
            maxSpeedMps = state.maxSpeedMps,
            unitsUsed = uiState.value.unit.name
        )
        val points = state.sampledPoints.map {
            TripPointEntity(
                tripId = 0,
                timestamp = it.timeMillis,
                latitude = it.latitude,
                longitude = it.longitude,
                speedMps = it.speedMps
            )
        }
        repository.saveTrip(trip, points)
    }

    fun resetAfterStop() {
        tracker.reset()
    }

    fun loadTrip(tripId: Long) = viewModelScope.launch {
        selectedTrip.value = repository.loadTrip(tripId)
    }

    fun clearSelectedTrip() {
        selectedTrip.value = null
    }
}
