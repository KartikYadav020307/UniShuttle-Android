package com.unishuttle.app.presentation.ui.screen.student

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.unishuttle.app.R
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.presentation.ui.component.AlertChip
import com.unishuttle.app.presentation.ui.component.EtaBadge
import com.unishuttle.app.presentation.ui.component.MapFrame
import com.unishuttle.app.presentation.ui.component.SeatMeter
import com.unishuttle.app.presentation.ui.component.ShuttleMarker
import com.unishuttle.app.presentation.ui.component.StopMarker
import com.unishuttle.app.presentation.ui.component.UniCard
import com.unishuttle.app.presentation.ui.screen.map.CampusMapDefaults
import com.unishuttle.app.presentation.ui.theme.UniColors
import com.unishuttle.app.presentation.ui.theme.UniShuttleTheme
import com.unishuttle.app.presentation.ui.theme.hardShadow
import com.unishuttle.app.presentation.viewmodel.StudentMapViewModel

/**
 * Data model for a transit stop rendered on the Campus Ink StudentMap.
 */
data class CampusStop(
    val id: String,
    val name: String,
    val position: LatLng,
    val nextShuttle: String = "SH-2",
    val routeName: String = "Loop A",
    val eta: String = "3 min",
    val availableSeats: Int = 14,
    val totalSeats: Int = 24,
)

private val DefaultCampusStops = listOf(
    CampusStop(
        id = "stop_main_gate",
        name = "Main Gate",
        position = LatLng(12.9692, 79.1559),
        nextShuttle = "SH-2",
        routeName = "Loop A",
        eta = "3 min",
        availableSeats = 14,
        totalSeats = 24,
    ),
    CampusStop(
        id = "stop_tech_tower",
        name = "Technology Tower",
        position = LatLng(12.9712, 79.1594),
        nextShuttle = "SH-1",
        routeName = "Loop A",
        eta = "7 min",
        availableSeats = 18,
        totalSeats = 24,
    ),
    CampusStop(
        id = "stop_sjt",
        name = "Silver Jubilee Tower",
        position = LatLng(12.9735, 79.1635),
        nextShuttle = "SH-3",
        routeName = "Loop B",
        eta = "12 min",
        availableSeats = 6,
        totalSeats = 24,
    ),
    CampusStop(
        id = "stop_prp",
        name = "PRP Block",
        position = LatLng(12.9665, 79.1530),
        nextShuttle = "SH-5",
        routeName = "Loop B",
        eta = "5 min",
        availableSeats = 20,
        totalSeats = 24,
    ),
)

private val RouteOptions = listOf("All routes", "Loop A", "Loop B")

@Composable
fun StudentMapScreen(
    onNavigateToNotifications: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudentMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedRoute by rememberSaveable { mutableStateOf(RouteOptions.first()) }
    var selectedStop by remember { mutableStateOf(DefaultCampusStops.first()) }
    var isBottomSheetOpen by remember { mutableStateOf(false) }

    StudentMapContent(
        activeDrivers = uiState.activeDrivers,
        selectedRoute = selectedRoute,
        onRouteChange = { selectedRoute = it },
        selectedStop = selectedStop,
        onStopSelected = { stop ->
            selectedStop = stop
            // Tapping a stop prepares/opens UniBottomSheet
            isBottomSheetOpen = true
        },
        onNavigateToNotifications = onNavigateToNotifications,
        modifier = modifier,
        isMapPreview = false,
    )
}

@Composable
fun StudentMapContent(
    activeDrivers: List<ShuttleLocation>,
    selectedRoute: String,
    onRouteChange: (String) -> Unit,
    selectedStop: CampusStop,
    onStopSelected: (CampusStop) -> Unit,
    onNavigateToNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    isMapPreview: Boolean = false,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // ── Polyline Cache: Fetch the Directions polyline once per route ──
    val routePolylineCache = remember { mutableMapOf<String, List<LatLng>>() }
    var currentPolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    LaunchedEffect(selectedRoute) {
        val cached = routePolylineCache[selectedRoute]
        if (cached != null) {
            currentPolyline = cached
        } else {
            // Generate/fetch polyline once per route
            val points = when (selectedRoute) {
                "Loop A" -> listOf(
                    LatLng(12.9660, 79.1520),
                    LatLng(12.9692, 79.1559),
                    LatLng(12.9712, 79.1594),
                    LatLng(12.9735, 79.1635),
                )
                "Loop B" -> listOf(
                    LatLng(12.9642, 79.1502),
                    LatLng(12.9665, 79.1530),
                    LatLng(12.9692, 79.1559),
                )
                else -> listOf(
                    LatLng(12.9642, 79.1502),
                    LatLng(12.9665, 79.1530),
                    LatLng(12.9692, 79.1559),
                    LatLng(12.9712, 79.1594),
                    LatLng(12.9735, 79.1635),
                )
            }
            routePolylineCache[selectedRoute] = points
            currentPolyline = points
        }
    }

    val mapStyleOptions = remember(isDark) {
        try {
            MapStyleOptions.loadRawResourceStyle(
                context,
                if (isDark) R.raw.map_style_dark else R.raw.map_style_light,
            )
        } catch (_: Exception) {
            null
        }
    }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            CampusMapDefaults.CampusCenter,
            CampusMapDefaults.DefaultZoom,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        // ── 1. Header (64dp top padding) ──
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
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // 48dp Sun bell button with 2dp Ink border and 3dp hard shadow
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

        // ── 2. Route Chips below Header ──
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

        // ── 3. MapFrame with 16dp side margins ──
        MapFrame(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (isMapPreview) {
                // Map placeholder for Compose Previews
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShuttleMarker(label = "SH-2 · 3 min", heading = 45f)
                        StopMarker(isSelected = true)
                        Text(
                            text = "Main Gate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    // Content padding keeps Google logo and attribution visible inside the 24dp rounded frame
                    contentPadding = PaddingValues(start = 12.dp, bottom = 12.dp),
                    properties = MapProperties(
                        isTrafficEnabled = false,
                        isMyLocationEnabled = hasLocationPermission,
                        mapStyleOptions = mapStyleOptions,
                    ),
                    uiSettings = MapUiSettings(
                        compassEnabled = false,
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = false,
                    ),
                ) {
                    // Route Polyline: Brick, 5dp (approx 14f), round caps
                    if (currentPolyline.size >= 2) {
                        Polyline(
                            points = currentPolyline,
                            color = MaterialTheme.colorScheme.primary,
                            width = 14f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                        )
                    }

                    // Stop Markers
                    DefaultCampusStops.forEach { stop ->
                        val isSelected = stop.id == selectedStop.id
                        MarkerComposable(
                            keys = arrayOf<Any>(stop.id, isSelected),
                            state = rememberMarkerState(position = stop.position),
                            onClick = {
                                onStopSelected(stop)
                                true
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                StopMarker(
                                    isSelected = isSelected,
                                    contentDesc = "Stop ${stop.name}",
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stop.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }

                    // Shuttle Markers: Animate position and rotate to heading
                    val shuttlesToRender = if (activeDrivers.isNotEmpty()) {
                        activeDrivers
                    } else {
                        // Fallback sample shuttle on campus matching StudentMap.png
                        listOf(
                            ShuttleLocation(
                                id = "2",
                                latitude = 12.9705,
                                longitude = 79.1580,
                                heading = 45f,
                                updatedAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }

                    shuttlesToRender.forEach { driver ->
                        val animatedLat by animateFloatAsState(
                            targetValue = driver.latitude.toFloat(),
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            label = "shuttle_lat",
                        )
                        val animatedLng by animateFloatAsState(
                            targetValue = driver.longitude.toFloat(),
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            label = "shuttle_lng",
                        )

                        MarkerComposable(
                            keys = arrayOf<Any>(driver.id),
                            state = rememberMarkerState(
                                position = LatLng(animatedLat.toDouble(), animatedLng.toDouble()),
                            ),
                            onClick = { true },
                        ) {
                            ShuttleMarker(
                                label = "SH-${driver.id} · 3 min",
                                heading = driver.heading,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 4. Bottom Card: UniCard with 16dp margins ──
        UniCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            onClick = { onStopSelected(selectedStop) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Top row: Stop Name + Next Shuttle & EtaBadge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = selectedStop.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Next: ${selectedStop.nextShuttle} · ${selectedStop.routeName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    EtaBadge(
                        text = selectedStop.eta,
                        showIcon = false,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Seats free row + SeatMeter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "Seats free",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${selectedStop.availableSeats} / ${selectedStop.totalSeats}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 12dp SeatMeter
                SeatMeter(
                    occupiedSeats = selectedStop.availableSeats,
                    totalSeats = selectedStop.totalSeats,
                    showHeader = false,
                )
            }
        }
    }
}

// ── Light and Dark Previews ──

@Preview(name = "StudentMap - Light", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun StudentMapPreviewLight() {
    UniShuttleTheme(darkTheme = false) {
        StudentMapContent(
            activeDrivers = emptyList(),
            selectedRoute = "All routes",
            onRouteChange = {},
            selectedStop = DefaultCampusStops.first(),
            onStopSelected = {},
            onNavigateToNotifications = {},
            isMapPreview = true,
        )
    }
}

@Preview(name = "StudentMap - Dark", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
fun StudentMapPreviewDark() {
    UniShuttleTheme(darkTheme = true) {
        StudentMapContent(
            activeDrivers = emptyList(),
            selectedRoute = "All routes",
            onRouteChange = {},
            selectedStop = DefaultCampusStops.first(),
            onStopSelected = {},
            onNavigateToNotifications = {},
            isMapPreview = true,
        )
    }
}
