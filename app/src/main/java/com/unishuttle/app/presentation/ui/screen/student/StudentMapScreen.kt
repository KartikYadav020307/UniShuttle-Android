package com.unishuttle.app.presentation.ui.screen.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unishuttle.app.config.MapProviderConfig
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.SeatAvailability
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.domain.model.StopAlert
import com.unishuttle.app.domain.model.StopEta
import com.unishuttle.app.presentation.ui.component.AlertChip
import com.unishuttle.app.presentation.ui.component.EtaBadge
import com.unishuttle.app.presentation.ui.component.MapFrame
import com.unishuttle.app.presentation.ui.component.SeatMeter
import com.unishuttle.app.presentation.ui.component.SignOutDialog
import com.unishuttle.app.presentation.ui.map.CampusMapController
import com.unishuttle.app.presentation.ui.map.MapLibreCampusMap
import com.unishuttle.app.presentation.ui.screen.map.CampusMapDefaults
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow
import com.unishuttle.app.presentation.viewmodel.CampusMapViewModel

/**
 * UI model consumed by [StopDetailSheet]; derived from live Firestore data.
 * Kept as a separate model so the sheet stays previewable in isolation.
 */
data class CampusStop(
    val id: String,
    val name: String,
    val nextShuttle: String = "",
    val routeName: String = "Campus loop",
    val eta: String = "—",
    val availableSeats: Int = 0,
    val totalSeats: Int = 24,
    val routePosition: String = "",
    val trafficLabel: String = "",
    val arrivals: List<StopArrival> = emptyList(),
)

private fun ShuttleStop.toSheetModel(
    allStops: List<ShuttleStop>,
    stopEtas: List<StopEta>,
    seatAvailability: List<SeatAvailability>,
): CampusStop {
    val etasHere = stopEtas.filter { it.stopId == id }
    val arrivals = etasHere
        .sortedBy { it.durationSeconds }
        .map { eta ->
            val seats = seatAvailability.firstOrNull { it.shuttleId == eta.shuttleId }
            StopArrival(
                shuttleId = "SH-${eta.shuttleId}",
                seatsFree = seats?.availableSeats ?: 0,
            )
        }
    val best = etasHere.minByOrNull { it.durationSeconds }
    val bestSeats = best?.let { seatAvailability.firstOrNull { s -> s.shuttleId == it.shuttleId } }

    return CampusStop(
        id = id,
        name = name,
        nextShuttle = best?.shuttleId?.let { "SH-$it" }.orEmpty(),
        eta = best?.etaText ?: "—",
        availableSeats = bestSeats?.availableSeats ?: 0,
        totalSeats = bestSeats?.totalSeats ?: 24,
        routePosition = "Stop ${order} of ${allStops.size}",
        trafficLabel = "",
        arrivals = arrivals,
    )
}

private val RouteOptions = listOf("All routes", "Loop A", "Loop B")

@Composable
fun StudentMapScreen(
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampusMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Mirror the on-duty guard: only show shuttles updated in the last 5 minutes.
    val activeShuttles = remember(uiState.shuttles, uiState.stopEtas.size) {
        val cutoff = System.currentTimeMillis() - SHUTTLE_FRESHNESS_MILLIS
        uiState.shuttles.filter { it.updatedAtMillis >= cutoff }
    }

    var selectedRoute by rememberSaveable { mutableStateOf(RouteOptions.first()) }

    StudentMapContent(
        stops = uiState.stops,
        shuttles = activeShuttles,
        seatAvailability = uiState.seatAvailability,
        stopEtas = uiState.stopEtas,
        routePoints = uiState.routePoints,
        activeAlerts = uiState.activeAlerts,
        selectedStopId = uiState.selectedStopId,
        isUsingFallbackStops = uiState.isUsingFallbackStops,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        selectedRoute = selectedRoute,
        onRouteChange = { selectedRoute = it },
        onStopSelected = viewModel::selectStop,
        onStopSheetDismiss = viewModel::dismissStopDetails,
        onSubscribeAlert = viewModel::subscribeToStopAlert,
        onUnsubscribeAlert = viewModel::unsubscribeFromStopAlert,
        onNavigateToNotifications = onNavigateToNotifications,
        onLogout = onLogout,
        modifier = modifier,
    )
}

@Composable
fun StudentMapContent(
    stops: List<ShuttleStop>,
    shuttles: List<ShuttleLocation>,
    seatAvailability: List<SeatAvailability>,
    stopEtas: List<StopEta>,
    routePoints: List<CampusLatLng>,
    selectedStopId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    onStopSelected: (String) -> Unit,
    onStopSheetDismiss: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    isUsingFallbackStops: Boolean = false,
    activeAlerts: List<StopAlert> = emptyList(),
    onSubscribeAlert: (stopId: String, stopName: String, thresholdMinutes: Int) -> Unit = { _, _, _ -> },
    onUnsubscribeAlert: (stopId: String) -> Unit = {},
    isMapPreview: Boolean = false,
) {
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ── 1. Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Good morning",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "UniShuttle",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .hardShadow(
                            shape = CircleShape,
                            offset = 3.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = { showSignOutDialog = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .hardShadow(
                            shape = CircleShape,
                            offset = 3.dp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        .clip(CircleShape)
                        .background(UniColors.Sun)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = onNavigateToNotifications,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = UniColors.Ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // ── 2. Route chips (visual only — stops carry no routeId yet) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RouteOptions.forEach { route ->
                AlertChip(
                    text = route,
                    selected = route == selectedRoute,
                    onClick = { onRouteChange(route) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 3. Map ──
        val isDark = isSystemInDarkTheme()
        val controller = remember { CampusMapController() }
        val density = LocalDensity.current
        var mapReady by remember { mutableStateOf(false) }
        var cameraFitted by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(onStopSelected) {
            controller.setOnStopClick(onStopSelected)
        }
        LaunchedEffect(stops, selectedStopId) {
            controller.setStops(stops, selectedStopId)
        }
        LaunchedEffect(shuttles) {
            controller.setShuttles(shuttles)
        }
        LaunchedEffect(routePoints, stops) {
            when {
                routePoints.size >= 2 -> controller.setRoute(routePoints, isFallback = false)
                stops.size >= 2 -> controller.setRoute(
                    stops.map { CampusLatLng(it.latitude, it.longitude) },
                    isFallback = true,
                )
                else -> controller.setRoute(emptyList(), isFallback = false)
            }
        }
        // Fit the camera once when the map and stop data are both ready.
        LaunchedEffect(mapReady, stops) {
            if (mapReady && !cameraFitted && stops.isNotEmpty()) {
                cameraFitted = true
                val paddingPx = with(density) { 64.dp.roundToPx() }
                controller.fitCameraTo(stops.map { CampusLatLng(it.latitude, it.longitude) }, paddingPx)
            }
        }

        MapFrame(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (isMapPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "MapLibre campus map",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                MapLibreCampusMap(
                    controller = controller,
                    styleUri = if (isDark) {
                        MapProviderConfig.DARK_STYLE_URL
                    } else {
                        MapProviderConfig.LIGHT_STYLE_URL
                    },
                    initialCenter = CampusMapDefaults.CampusCenter,
                    initialZoom = CampusMapDefaults.DefaultZoom,
                    modifier = Modifier.fillMaxSize(),
                    onMapReady = { mapReady = true },
                )
            }

            // Loading / error status strip sits at the top of the map frame.
            val status = when {
                stops.isEmpty() && errorMessage != null -> errorMessage
                stops.isEmpty() && isLoading -> "Loading campus…"
                else -> null
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )
            } else if (isUsingFallbackStops) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(UniColors.Sun)
                        .border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "⚡ Offline Mode · Static campus stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = UniColors.Ink,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 4. Bottom card ──
        val displayStop = stops.firstOrNull { it.id == selectedStopId } ?: stops.firstOrNull()
        val bestEta = displayStop?.let { stop ->
            stopEtas.filter { it.stopId == stop.id }.minByOrNull { it.durationSeconds }
        }
        val displaySeats = bestEta?.let { eta ->
            seatAvailability.firstOrNull { it.shuttleId == eta.shuttleId }
        }

        com.unishuttle.app.presentation.ui.component.UniCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            onClick = displayStop?.let { stop -> { onStopSelected(stop.id) } },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = displayStop?.name ?: "No stops yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bestEta?.let { "Next: SH-${it.shuttleId}" } ?: "Waiting for live shuttles",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    EtaBadge(
                        text = bestEta?.etaText ?: "—",
                        showIcon = false,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "Seats free",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = displaySeats?.let { "${it.availableSeats} / ${it.totalSeats}" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (displaySeats != null) {
                    SeatMeter(
                        occupiedSeats = displaySeats.availableSeats,
                        totalSeats = displaySeats.totalSeats,
                        showHeader = false,
                    )
                }
            }
        }

        // ── 5. Stop detail sheet ──
        val sheetStop = stops.firstOrNull { it.id == selectedStopId }
        if (sheetStop != null) {
            val activeAlert = activeAlerts.firstOrNull { it.stopId == sheetStop.id }
            StopDetailSheet(
                stop = sheetStop.toSheetModel(stops, stopEtas, seatAvailability),
                isAlertArmed = activeAlert != null,
                armedThresholdMinutes = activeAlert?.thresholdMinutes,
                onSubscribeAlert = onSubscribeAlert,
                onUnsubscribeAlert = onUnsubscribeAlert,
                onDismiss = onStopSheetDismiss,
            )
        }

        if (showSignOutDialog) {
            SignOutDialog(
                onConfirm = {
                    showSignOutDialog = false
                    onLogout()
                },
                onDismiss = { showSignOutDialog = false },
            )
        }
    }
}

private const val SHUTTLE_FRESHNESS_MILLIS = 5 * 60 * 1_000L

// ── Light and Dark Previews ──

private val PreviewStops = listOf(
    ShuttleStop(id = "stop_main_gate", name = "Main Gate", latitude = 12.9692, longitude = 79.1559, order = 1),
    ShuttleStop(id = "stop_tech_tower", name = "Technology Tower", latitude = 12.9712, longitude = 79.1594, order = 2),
    ShuttleStop(id = "stop_sjt", name = "Silver Jubilee Tower", latitude = 12.9735, longitude = 79.1635, order = 3),
)

private val PreviewEtas = listOf(
    StopEta(shuttleId = "2", stopId = "stop_main_gate", etaText = "3 min", durationSeconds = 180),
    StopEta(shuttleId = "2", stopId = "stop_tech_tower", etaText = "7 min", durationSeconds = 420),
)

private val PreviewSeats = listOf(
    SeatAvailability(shuttleId = "2", availableSeats = 14, totalSeats = 24, updatedAtMillis = 0),
)

@Preview(name = "StudentMap - Light", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun StudentMapPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        StudentMapContent(
            stops = PreviewStops,
            shuttles = emptyList(),
            seatAvailability = PreviewSeats,
            stopEtas = PreviewEtas,
            routePoints = emptyList(),
            selectedStopId = null,
            isLoading = false,
            errorMessage = null,
            selectedRoute = "All routes",
            onRouteChange = {},
            onStopSelected = {},
            onStopSheetDismiss = {},
            onNavigateToNotifications = {},
            onLogout = {},
            isMapPreview = true,
        )
    }
}

@Preview(name = "StudentMap - Dark", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun StudentMapPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        StudentMapContent(
            stops = PreviewStops,
            shuttles = emptyList(),
            seatAvailability = PreviewSeats,
            stopEtas = PreviewEtas,
            routePoints = emptyList(),
            selectedStopId = null,
            isLoading = false,
            errorMessage = null,
            selectedRoute = "All routes",
            onRouteChange = {},
            onStopSelected = {},
            onStopSheetDismiss = {},
            onNavigateToNotifications = {},
            onLogout = {},
            isMapPreview = true,
        )
    }
}
