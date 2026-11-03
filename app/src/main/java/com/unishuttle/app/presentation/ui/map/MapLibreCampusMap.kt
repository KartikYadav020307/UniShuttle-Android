package com.unishuttle.app.presentation.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import com.unishuttle.app.domain.model.CampusLatLng

/**
 * Embeds a MapLibre [MapView] in Compose with correct lifecycle management.
 *
 * - MapLibre is initialized before the MapView is constructed.
 * - One MapView per composition; data updates flow through [controller]
 *   without recreating the view.
 * - Lifecycle events are forwarded; the view is destroyed on disposal, so
 *   navigating away and back cannot leak or stack duplicate maps.
 */
@Composable
fun MapLibreCampusMap(
    controller: CampusMapController,
    styleUri: String,
    initialCenter: CampusLatLng,
    initialZoom: Double,
    modifier: Modifier = Modifier,
    onMapReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.getMapAsync { map ->
                controller.attach(
                    map = map,
                    context = context,
                    styleUrl = styleUri,
                    initialCenter = initialCenter,
                    initialZoom = initialZoom,
                    onReady = onMapReady,
                )
            }
        }
    }

    // Reload only the style (not the view) when the theme changes; the
    // controller re-applies markers and route data to the new style.
    LaunchedEffect(styleUri) {
        controller.loadStyle(styleUri)
    }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // If composition happens while the host is already resumed, catch up.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onStart()
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}
