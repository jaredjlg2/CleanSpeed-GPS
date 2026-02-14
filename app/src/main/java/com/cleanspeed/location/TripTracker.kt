package com.cleanspeed.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.cleanspeed.util.GeoUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

class TripTracker private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private var lastAcceptedLocation: Location? = null
    private var pausedAtMillis: Long? = null
    private var accumulatedPausedMillis: Long = 0
    private var tickerJob: Job? = null
    private var callback: LocationCallback? = null

    companion object {
        @Volatile private var instance: TripTracker? = null
        fun get(context: Context): TripTracker = instance ?: synchronized(this) {
            instance ?: TripTracker(context).also { instance = it }
        }

        private const val MAX_POINTS = 10_000
        private const val SAMPLE_EVERY_MS = 2_000L
    }

    fun startTrip() {
        _state.value = TrackingState(status = TripStatus.RUNNING, startTimeMillis = System.currentTimeMillis())
        lastAcceptedLocation = null
        pausedAtMillis = null
        accumulatedPausedMillis = 0
        startTicker()
        startLocationUpdates()
    }

    fun pauseTrip() {
        if (_state.value.status != TripStatus.RUNNING) return
        _state.update { it.copy(status = TripStatus.PAUSED, currentSpeedMps = 0.0) }
        pausedAtMillis = System.currentTimeMillis()
        stopLocationUpdates()
    }

    fun resumeTrip() {
        if (_state.value.status != TripStatus.PAUSED) return
        pausedAtMillis?.let { accumulatedPausedMillis += System.currentTimeMillis() - it }
        pausedAtMillis = null
        _state.update { it.copy(status = TripStatus.RUNNING) }
        startLocationUpdates()
    }

    fun stopTrip(): TrackingState {
        stopLocationUpdates()
        tickerJob?.cancel()
        _state.update { it.copy(status = TripStatus.STOPPED, currentSpeedMps = 0.0) }
        return _state.value
    }

    fun reset() {
        stopLocationUpdates()
        tickerJob?.cancel()
        _state.value = TrackingState(status = TripStatus.IDLE)
        lastAcceptedLocation = null
        pausedAtMillis = null
        accumulatedPausedMillis = 0
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                val start = _state.value.startTimeMillis ?: continue
                if (_state.value.status == TripStatus.RUNNING || _state.value.status == TripStatus.PAUSED) {
                    val pausedNow = if (_state.value.status == TripStatus.PAUSED) {
                        (System.currentTimeMillis() - (pausedAtMillis ?: System.currentTimeMillis()))
                    } else 0L
                    val elapsed = ((System.currentTimeMillis() - start - accumulatedPausedMillis - pausedNow) / 1000).coerceAtLeast(0)
                    _state.update {
                        val avg = if (elapsed > 0) it.distanceMeters / elapsed else 0.0
                        it.copy(elapsedSeconds = elapsed, avgSpeedMps = avg)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (callback != null) return
        val request = LocationRequest.Builder(1000L)
            .setMinUpdateIntervalMillis(500L)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::processLocation)
            }
        }
        client.requestLocationUpdates(request, callback!!, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    private fun processLocation(location: Location) {
        if (_state.value.status != TripStatus.RUNNING) return

        val poorAccuracy = location.hasAccuracy() && location.accuracy > 25f
        if (poorAccuracy) {
            _state.update { it.copy(gpsWeak = true) }
            return
        }

        val previous = lastAcceptedLocation
        val deltaTimeSec = previous?.let { (location.time - it.time) / 1000.0 } ?: 0.0
        if (deltaTimeSec > 15.0) return

        var speedMps = if (location.hasSpeed() && location.speed >= 0f) location.speed.toDouble() else 0.0
        var distanceAdd = 0.0
        if (previous != null) {
            distanceAdd = GeoUtils.haversineDistanceMeters(
                previous.latitude,
                previous.longitude,
                location.latitude,
                location.longitude
            )
            if (speedMps <= 0.0 && deltaTimeSec > 0.0) speedMps = distanceAdd / deltaTimeSec
        }

        lastAcceptedLocation = location
        _state.update {
            val updatedDistance = it.distanceMeters + distanceAdd
            val updatedMax = max(it.maxSpeedMps, speedMps)
            val points = maybeAddPoint(it.sampledPoints, location, speedMps)
            it.copy(
                gpsWeak = false,
                distanceMeters = updatedDistance,
                currentSpeedMps = speedMps,
                maxSpeedMps = updatedMax,
                sampledPoints = points
            )
        }
    }

    private fun maybeAddPoint(existing: List<SamplePoint>, location: Location, speedMps: Double): List<SamplePoint> {
        val last = existing.lastOrNull()
        if (last != null && location.time - last.timeMillis < SAMPLE_EVERY_MS) return existing
        if (existing.size >= MAX_POINTS) return existing
        return existing + SamplePoint(location.latitude, location.longitude, location.time, speedMps)
    }
}
