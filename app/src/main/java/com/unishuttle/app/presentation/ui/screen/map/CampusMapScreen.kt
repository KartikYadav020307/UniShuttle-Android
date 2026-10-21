package com.unishuttle.app.presentation.ui.screen.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.unishuttle.app.R
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopEta
import com.unishuttle.app.presentation.viewmodel.CampusMapViewModel
import kotlin.math.abs

@Composable
fun CampusMapScreen(
    onNavigateToNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampusMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            CampusMapDefaults.CampusCenter,
            CampusMapDefaults.DefaultZoom,
        )
    }
    var mapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded) {
        if (mapLoaded) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(
                    CampusMapDefaults.CampusBounds,
                    80,
                ),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapLoaded = { mapLoaded = true },
            properties = MapProperties(
                isTrafficEnabled = false,
                latLngBoundsForCameraTarget = CampusMapDefaults.CampusBounds,
            ),
            uiSettings = MapUiSettings(
                compassEnabled = true,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
            ),
        ) {
            ShuttleRoutePolyline(routePoints = uiState.routePoints)
            uiState.stops.forEach { stop ->
                StopMarker(
                    stop = stop,
                    stopEtas = uiState.stopEtas,
                    seatAvailability = uiState.seatAvailability,
                    onClick = { viewModel.selectStop(stop.id) },
                )
            }
            uiState.shuttles.forEach { shuttle ->
                AnimatedShuttleMarker(shuttle = shuttle)
            }
        }

        // Notification bell FAB
        FloatingActionButton(
            onClick = onNavigateToNotifications,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notification Center",
                modifier = Modifier.size(24.dp),
            )
        }

        uiState.errorMessage?.let { message ->
            MapStatusPill(message = message)
        }

        uiState.selectedStop?.let { stop ->
            val alertForStop = uiState.activeAlerts.firstOrNull { it.stopId == stop.id }

            StopDetailBottomSheet(
                stop = stop,
                shuttles = uiState.shuttles,
                seatAvailability = uiState.seatAvailability,
                stopEtas = uiState.stopEtas,
                activeAlert = alertForStop,
                onDismiss = viewModel::dismissStopDetails,
                onSetAlert = { minutes ->
                    viewModel.subscribeToStopAlert(stop.id, stop.name, minutes)
                },
                onClearAlert = {
                    viewModel.unsubscribeFromStopAlert(stop.id)
                },
            )
        }
    }
}

@Composable
private fun ShuttleRoutePolyline(routePoints: List<CampusLatLng>) {
    if (routePoints.size < 2) return

    Polyline(
        points = routePoints.map { LatLng(it.latitude, it.longitude) },
        color = Color(0xFF0057B8),
        width = 12f,
        geodesic = true,
    )
}

@Composable
private fun StopMarker(
    stop: ShuttleStop,
    stopEtas: List<StopEta>,
    seatAvailability: List<SeatAvailability>,
    onClick: () -> Unit,
) {
    val bestEta = stopEtas
        .filter { it.stopId == stop.id }
        .minByOrNull { it.durationSeconds }
    val seats = bestEta?.let { eta ->
        seatAvailability.firstOrNull { it.shuttleId == eta.shuttleId }
    }

    Marker(
        state = rememberUpdatedMarkerState(
            position = LatLng(stop.latitude, stop.longitude),
        ),
        contentDescription = "Stop ${stop.name}",
        title = stop.name,
        snippet = markerSnippet(bestEta, seats),
        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
        onClick = {
            onClick()
            true
        },
    )
}

@Composable
private fun AnimatedShuttleMarker(shuttle: ShuttleLocation) {
    val context = LocalContext.current
    val shuttleIcon = remember {
        bitmapDescriptorFromVector(context, R.drawable.ic_shuttle_marker)
    }

    val animatedLatitude by animateFloatAsState(
        targetValue = shuttle.latitude.toFloat(),
        animationSpec = tween(durationMillis = 1000),
        label = "shuttle-latitude-${shuttle.id}",
    )
    val animatedLongitude by animateFloatAsState(
        targetValue = shuttle.longitude.toFloat(),
        animationSpec = tween(durationMillis = 1000),
        label = "shuttle-longitude-${shuttle.id}",
    )

    var targetHeading by remember(shuttle.id) { mutableFloatStateOf(shuttle.heading) }
    LaunchedEffect(shuttle.heading) {
        targetHeading = shortestRotationTarget(targetHeading, shuttle.heading)
    }

    val animatedHeading by animateFloatAsState(
        targetValue = targetHeading,
        animationSpec = tween(durationMillis = 1000),
        label = "shuttle-heading-${shuttle.id}",
    )

    Marker(
        state = rememberUpdatedMarkerState(
            position = LatLng(
                animatedLatitude.toDouble(),
                animatedLongitude.toDouble(),
            ),
        ),
        title = "Shuttle ${shuttle.id}",
        icon = shuttleIcon,
        anchor = Offset(0.5f, 0.5f),
        flat = true,
        rotation = normalizeHeading(animatedHeading),
    )
}

@Composable
private fun BoxScope.MapStatusPill(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 24.dp, start = 16.dp, end = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun shortestRotationTarget(current: Float, next: Float): Float {
    val normalizedCurrent = normalizeHeading(current)
    val normalizedNext = normalizeHeading(next)
    val clockwise = normalizedNext - normalizedCurrent
    val counterClockwise = if (clockwise > 0) clockwise - 360f else clockwise + 360f

    val delta = if (abs(clockwise) <= abs(counterClockwise)) {
        clockwise
    } else {
        counterClockwise
    }

    return current + delta
}

private fun normalizeHeading(heading: Float): Float =
    ((heading % 360f) + 360f) % 360f

private fun markerSnippet(
    bestEta: StopEta?,
    seats: SeatAvailability?,
): String {
    val etaText = bestEta?.let { "ETA ${it.etaText}" } ?: "ETA calculating"
    val seatText = seats?.let { "${it.availableSeats}/${it.totalSeats} seats" } ?: "seats pending"
    return "$etaText | $seatText"
}
