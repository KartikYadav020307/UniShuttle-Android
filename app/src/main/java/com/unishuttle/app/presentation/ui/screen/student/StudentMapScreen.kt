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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unishuttle.app.config.MapProviderConfig
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.CampusRoute
import com.unishuttle.app.domain.model.RouteGeometry
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
    shuttles: List<ShuttleLocation>,
    seatAvailability: List<SeatAvailability>,
    selectedRoute: CampusRoute?,
    allRoutes: List<CampusRoute>,
): CampusStop {
    val etasHere = stopEtas.filter { it.stopId == id }
    val shuttleLabels = shuttles.associate { it.id to it.displayLabel }
    val arrivals = etasHere
        .sortedBy { it.durationSeconds }
        .map { eta ->
            val seats = seatAvailability.firstOrNull { it.shuttleId == eta.shuttleId && !it.isOffDuty }
            StopArrival(
                shuttleId = shuttleLabels[eta.shuttleId] ?: ("SH-" + eta.shuttleId),
                seatsFree = seats?.availableSeats ?: 0,
            )
        }
    val best = etasHere.minByOrNull { it.durationSeconds }
    val bestSeats = best?.let { seatAvailability.firstOrNull { s -> s.shuttleId == it.shuttleId && !s.isOffDuty } }

    val positionLabel = if (selectedRoute != null) {
        val idx = selectedRoute.stopIds.indexOf(id)
        if (idx >= 0) {
            "Stop ${idx + 1} of ${selectedRoute.stopIds.size}"
        } else {
            val stopIdx = allStops.indexOfFirst { it.id == id }
            val displayOrder = if (order in 1..allStops.size) order else (stopIdx + 1).coerceAtLeast(1)
            "Stop $displayOrder of ${allStops.size}"
        }
    } else {
        val stopIdx = allStops.indexOfFirst { it.id == id }
        val displayOrder = if (order in 1..allStops.size) order else (stopIdx + 1).coerceAtLeast(1)
        "Stop $displayOrder of ${allStops.size}"
    }

    val routeNameLabel = if (selectedRoute != null) {
        selectedRoute.name
    } else {
        val serving = allRoutes.filter { id in it.stopIds }
        if (serving.isNotEmpty()) serving.joinToString(" & ") { it.name } else "Unassigned"
    }

    return CampusStop(
        id = id,
        name = name,
        nextShuttle = best?.shuttleId?.let { shuttleLabels[it] ?: ("SH-" + it) }.orEmpty(),
        routeName = routeNameLabel,
        eta = best?.etaText ?: "—",
        availableSeats = bestSeats?.availableSeats ?: 0,
        totalSeats = bestSeats?.totalSeats ?: 24,
        routePosition = positionLabel,
        trafficLabel = "",
        arrivals = arrivals,
    )
}

data class RouteOptionItem(
    val name: String,
    val id: String?,
)

@Composable
fun StudentMapScreen(
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampusMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val cutoff = System.currentTimeMillis() - SHUTTLE_FRESHNESS_MILLIS
    val activeShuttles = remember(uiState.filteredShuttles, uiState.stopEtas.size) {
        uiState.filteredShuttles.filter { it.updatedAtMillis >= cutoff }
    }

    StudentMapContent(
        stops = uiState.filteredStops,
        shuttles = activeShuttles,
        routes = uiState.routes,
        selectedRouteId = uiState.selectedRouteId,
        routeGeometries = uiState.filteredGeometries,
        seatAvailability = uiState.seatAvailability,
        stopEtas = uiState.filteredStopEtas,
        activeAlerts = uiState.activeAlerts,
        selectedStopId = uiState.selectedStopId,
        isUsingFallbackStops = uiState.isUsingFallbackStops,
        isUsingFallbackRoutes = uiState.isUsingFallbackRoutes,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onRouteChange = viewModel::selectRoute,
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
    routes: List<CampusRoute>,
    selectedRouteId: String?,
    routeGeometries: List<RouteGeometry>,
    seatAvailability: List<SeatAvailability>,
    stopEtas: List<StopEta>,
    selectedStopId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onRouteChange: (String?) -> Unit,
    onStopSelected: (String) -> Unit,
    onStopSheetDismiss: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    isUsingFallbackStops: Boolean = false,
    isUsingFallbackRoutes: Boolean = false,
    activeAlerts: List<StopAlert> = emptyList(),
    onSubscribeAlert: (stopId: String, stopName: String, thresholdMinutes: Int) -> Unit = { _, _, _ -> },
    onUnsubscribeAlert: (stopId: String) -> Unit = {},
    isMapPreview: Boolean = false,
) {
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    val routeOptions = remember(routes) {
        listOf(RouteOptionItem("All routes", null)) +
            routes.map { RouteOptionItem(it.name, it.id) }
    }

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

        // ── 2. Interactive Route Filter Chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            routeOptions.forEach { option ->
                AlertChip(
                    text = option.name,
                    selected = option.id == selectedRouteId,
                    onClick = { onRouteChange(option.id) },
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
        LaunchedEffect(routeGeometries, routes) {
            if (routeGeometries.isNotEmpty()) {
                controller.setRoutes(routeGeometries, routes)
            } else if (stops.size >= 2) {
                controller.setRoute(
                    stops.map { CampusLatLng(it.latitude, it.longitude) },
                    isFallback = true,
                )
            } else {
                controller.setRoute(emptyList(), isFallback = false)
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
            } else if (routeGeometries.any { it.isFallback }) {
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
                        text = "Approximate cached route paths · live ETAs may be unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = UniColors.Ink,
                    )
                }
            } else if (isUsingFallbackStops || isUsingFallbackRoutes) {
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
                        text = "⚡ Default campus routes & stops",
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
        val nextShuttleLabel = bestEta?.let { eta ->
            shuttles.firstOrNull { it.id == eta.shuttleId }?.displayLabel ?: ("SH-" + eta.shuttleId)
        }
        val displaySeats = bestEta?.let { eta ->
            seatAvailability.firstOrNull { it.shuttleId == eta.shuttleId && !it.isOffDuty }
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
                            text = nextShuttleLabel?.let { "Next: $it" } ?: "Waiting for live shuttles",
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
            val selectedRoute = routes.firstOrNull { it.id == selectedRouteId }
            StopDetailSheet(
                stop = sheetStop.toSheetModel(
                    stops,
                    stopEtas,
                    shuttles,
                    seatAvailability,
                    selectedRoute,
                    routes,
                ),
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
            routes = CampusMapDefaults.DefaultRoutes,
            selectedRouteId = null,
            routeGeometries = emptyList(),
            seatAvailability = PreviewSeats,
            stopEtas = PreviewEtas,
            selectedStopId = null,
            isLoading = false,
            errorMessage = null,
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
            routes = CampusMapDefaults.DefaultRoutes,
            selectedRouteId = null,
            routeGeometries = emptyList(),
            seatAvailability = PreviewSeats,
            stopEtas = PreviewEtas,
            selectedStopId = null,
            isLoading = false,
            errorMessage = null,
            onRouteChange = {},
            onStopSelected = {},
            onStopSheetDismiss = {},
            onNavigateToNotifications = {},
            onLogout = {},
            isMapPreview = true,
        )
    }
}
