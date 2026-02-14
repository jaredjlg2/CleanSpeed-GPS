package com.cleanspeed

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanspeed.location.TripStatus
import com.cleanspeed.service.TripTrackingService
import com.cleanspeed.ui.MainViewModel
import com.cleanspeed.ui.theme.CleanSpeedTheme
import com.cleanspeed.util.SpeedUnit
import com.cleanspeed.util.Units

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setPermissionDenied(!granted)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(state.keepScreenOn) {
                if (state.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            CleanSpeedTheme {
                MainScreen(
                    state = state,
                    onUnitSelected = viewModel::setUnit,
                    onToggleKeepScreenOn = viewModel::setKeepScreenOn,
                    onStartTrip = {
                        if (ensureLocationPermission()) {
                            maybeRequestNotificationPermission()
                            val intent = Intent(this, TripTrackingService::class.java).apply {
                                action = TripTrackingService.ACTION_START
                            }
                            startForegroundService(this, intent)
                        }
                    },
                    onPauseResume = viewModel::pauseOrResume,
                    onStopSave = {
                        viewModel.saveStoppedTrip()
                        stopService(Intent(this, TripTrackingService::class.java).apply {
                            action = TripTrackingService.ACTION_STOP
                        })
                    },
                    onReset = viewModel::resetAfterStop,
                    onTripSelected = viewModel::loadTrip,
                    onDismissTripDialog = viewModel::clearSelectedTrip
                )
            }
        }
    }

    private fun ensureLocationPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.setPermissionDenied(!granted)
        return granted
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun MainScreen(
    state: com.cleanspeed.ui.MainUiState,
    onUnitSelected: (SpeedUnit) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onStartTrip: () -> Unit,
    onPauseResume: () -> Unit,
    onStopSave: () -> Unit,
    onReset: () -> Unit,
    onTripSelected: (Long) -> Unit,
    onDismissTripDialog: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = String.format("%.1f", Units.speedFromMps(state.tracking.currentSpeedMps, state.unit)),
                fontSize = 84.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = state.unit.label,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedUnit.values().forEach { unit ->
                    FilterChip(
                        selected = state.unit == unit,
                        onClick = { onUnitSelected(unit) },
                        label = { Text(unit.label) }
                    )
                }
            }

            if (state.tracking.gpsWeak) {
                Text("GPS weak", color = MaterialTheme.colorScheme.error)
            }
            if (state.permissionDenied) {
                Text("Location permission is required. CleanSpeed cannot function without location access.", color = MaterialTheme.colorScheme.error)
            }

            StatsRow(state)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep screen on")
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = state.keepScreenOn, onCheckedChange = onToggleKeepScreenOn)
            }

            Button(onClick = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                when (state.tracking.status) {
                    TripStatus.IDLE, TripStatus.STOPPED -> onStartTrip()
                    TripStatus.RUNNING, TripStatus.PAUSED -> onPauseResume()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (state.tracking.status) {
                        TripStatus.IDLE, TripStatus.STOPPED -> "Start Trip"
                        TripStatus.RUNNING -> "Pause"
                        TripStatus.PAUSED -> "Resume"
                    }
                )
            }

            Button(
                onClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onStopSave()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.tracking.status == TripStatus.RUNNING || state.tracking.status == TripStatus.PAUSED
            ) { Text("Stop & Save") }

            if (state.tracking.status == TripStatus.STOPPED) {
                Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
            }

            TextButton(onClick = { showHistory = true }) { Text("History") }
        }
    }

    if (showHistory) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showHistory = false }, sheetState = sheetState) {
            LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.recentTrips) { trip ->
                    Card(onClick = { onTripSelected(trip.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(trip.startTime)))
                            Text("Duration: ${formatDuration(trip.durationSeconds)}")
                            Text("Distance: ${"%.2f".format(Units.distanceFromMeters(trip.distanceMeters, state.unit))} ${Units.distanceLabel(state.unit)}")
                            Text("Avg: ${"%.1f".format(Units.speedFromMps(trip.avgSpeedMps, state.unit))} ${state.unit.label} • Max: ${"%.1f".format(Units.speedFromMps(trip.maxSpeedMps, state.unit))} ${state.unit.label}")
                        }
                    }
                }
            }
        }
    }

    state.selectedTrip?.let { selected ->
        AlertDialog(
            onDismissRequest = onDismissTripDialog,
            title = { Text("Saved Trip Summary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Start: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(selected.trip.startTime))}")
                    Text("End: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(selected.trip.endTime))}")
                    Text("Duration: ${formatDuration(selected.trip.durationSeconds)}")
                    Text("Distance: ${"%.2f".format(Units.distanceFromMeters(selected.trip.distanceMeters, state.unit))} ${Units.distanceLabel(state.unit)}")
                    Text("Avg: ${"%.1f".format(Units.speedFromMps(selected.trip.avgSpeedMps, state.unit))} ${state.unit.label}")
                    Text("Max: ${"%.1f".format(Units.speedFromMps(selected.trip.maxSpeedMps, state.unit))} ${state.unit.label}")
                    Text("Sampled points: ${selected.points.size}")
                }
            },
            confirmButton = { TextButton(onClick = onDismissTripDialog) { Text("Close") } }
        )
    }
}

@Composable
private fun StatsRow(state: com.cleanspeed.ui.MainUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatBlock("Distance", "${"%.2f".format(Units.distanceFromMeters(state.tracking.distanceMeters, state.unit))} ${Units.distanceLabel(state.unit)}")
        StatBlock("Trip time", formatDuration(state.tracking.elapsedSeconds))
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatBlock("Avg", "${"%.1f".format(Units.speedFromMps(state.tracking.avgSpeedMps, state.unit))} ${state.unit.label}")
        StatBlock("Max", "${"%.1f".format(Units.speedFromMps(state.tracking.maxSpeedMps, state.unit))} ${state.unit.label}")
    }
}

@Composable
private fun StatBlock(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
