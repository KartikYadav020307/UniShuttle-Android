package com.unishuttle.app.presentation.ui.map

import android.content.Context
import android.graphics.PointF
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.unishuttle.app.config.MapProviderConfig
import com.unishuttle.app.domain.model.CampusLatLng
import com.unishuttle.app.domain.model.ShuttleLocation
import com.unishuttle.app.domain.model.ShuttleStop
import com.unishuttle.app.presentation.ui.theme.UniColors
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

/**
 * Owns all MapLibre state for the campus map: style, sources, layers,
 * markers, route geometry, and camera movements. Data updates go through
 * [setStops]/[setShuttles]/[setRoute] and only touch GeoJSON sources and
 * layer visibility — the map itself is never recreated by data changes.
 *
 * Stop and shuttle icons are chosen per feature via an "icon" property so no
 * layer filters are needed; real and fallback route lines use separate
 * sources and toggle visibility instead of filters.
 */
class CampusMapController {

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var context: Context? = null
    private var styleUrl: String = MapProviderConfig.LIGHT_STYLE_URL
    private var styleLoadCallback: (() -> Unit)? = null

    // Last known data, re-applied whenever a new style finishes loading.
    private var stopFeatures: List<Feature> = emptyList()
    private var shuttleFeatures: List<Feature> = emptyList()
    private var realRouteGeometry: LineString? = null
    private var fallbackRouteGeometry: LineString? = null
    private var shuttleIconIds: Set<String> = emptySet()

    private var onStopClick: ((String) -> Unit)? = null

    fun attach(
        map: MapLibreMap,
        context: Context,
        styleUrl: String,
        initialCenter: CampusLatLng,
        initialZoom: Double,
        onReady: () -> Unit,
    ) {
        this.map = map
        this.context = context
        this.styleUrl = styleUrl
        this.styleLoadCallback = onReady

        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(initialCenter.latitude, initialCenter.longitude),
                initialZoom,
            ),
        )
        map.addOnMapClickListener { latLng -> handleClick(latLng) }
        loadStyle(styleUrl)
    }

    fun setOnStopClick(listener: (String) -> Unit) {
        onStopClick = listener
    }

    /** Loads (or reloads, on theme change) a style and re-applies all data. */
    fun loadStyle(url: String) {
        styleUrl = url
        val map = map ?: return
        map.setStyle(url) { loaded ->
            style = loaded
            installContent(loaded)
            styleLoadCallback?.invoke()
            styleLoadCallback = null
        }
    }

    fun setStops(stops: List<ShuttleStop>, selectedId: String?) {
        stopFeatures = stops.map { stop ->
            val isSelected = stop.id == selectedId
            Feature.fromGeometry(
                Point.fromLngLat(stop.longitude, stop.latitude),
            ).apply {
                addStringProperty(PROP_STOP_ID, stop.id)
                // The stops layer renders the icon named by this property.
                addStringProperty(
                    PROP_ICON,
                    if (isSelected) ICON_STOP_SELECTED else ICON_STOP,
                )
            }
        }
        pushStops()
    }

    fun setShuttles(shuttles: List<ShuttleLocation>) {
        shuttleFeatures = shuttles.mapNotNull { shuttle ->
            // Unusable coordinates: skip the marker entirely (graceful state)
            // instead of plotting an arbitrary position.
            if (!isUsableCoordinate(shuttle.latitude, shuttle.longitude)) {
                return@mapNotNull null
            }
            Feature.fromGeometry(
                Point.fromLngLat(shuttle.longitude, shuttle.latitude),
            ).apply {
                addStringProperty(PROP_SHUTTLE_ID, shuttle.id)
                addNumberProperty(PROP_HEADING, shuttle.heading.toDouble())
            }
        }
        pushShuttles()
    }

    /**
     * @param isFallback true when [points] are straight stop-to-stop segments
     * rather than real road geometry; the fallback line is dashed so it can
     * never be mistaken for a routed path.
     */
    fun setRoute(points: List<CampusLatLng>, isFallback: Boolean) {
        val geometry = if (points.size >= 2) {
            LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        } else {
            null
        }
        if (isFallback) {
            fallbackRouteGeometry = geometry
            realRouteGeometry = null
        } else {
            realRouteGeometry = geometry
            fallbackRouteGeometry = null
        }
        pushRoute()
    }

    /** Frames [points]; never called again after the first fit so user pan wins. */
    fun fitCameraTo(points: List<CampusLatLng>, paddingPx: Int, durationMs: Int = 900) {
        val map = map ?: return
        if (points.isEmpty()) return
        if (points.size < 2) {
            val only = points.first()
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(only.latitude, only.longitude), 16.0),
                durationMs,
            )
            return
        }
        val bounds = LatLngBounds.fromLatLngs(points.map { LatLng(it.latitude, it.longitude) })
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), durationMs)
    }

    fun isStyleReady(): Boolean = style != null

    // ── internals ────────────────────────────────────────────────────────────

    private fun isUsableCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            abs(latitude) > MIN_ABS_COORDINATE && abs(longitude) > MIN_ABS_COORDINATE

    private fun handleClick(latLng: LatLng): Boolean {
        val map = map ?: return false
        val screen: PointF = map.projection.toScreenLocation(latLng)
        val hits = map.queryRenderedFeatures(screen, LAYER_STOPS)
        val stopId = hits.firstOrNull()?.getStringProperty(PROP_STOP_ID)
        if (stopId != null) {
            onStopClick?.invoke(stopId)
            return true
        }
        return false
    }

    private fun installContent(style: Style) {
        val ctx = context ?: return

        style.addSource(GeoJsonSource(SOURCE_STOPS))
        style.addSource(GeoJsonSource(SOURCE_SHUTTLES))
        style.addSource(GeoJsonSource(SOURCE_ROUTE_REAL))
        style.addSource(GeoJsonSource(SOURCE_ROUTE_FALLBACK))

        style.addImage(ICON_STOP, CampusMarkerFactory.stopIcon(ctx, selected = false))
        style.addImage(ICON_STOP_SELECTED, CampusMarkerFactory.stopIcon(ctx, selected = true))

        style.addLayer(
            LineLayer(LAYER_ROUTE, SOURCE_ROUTE_REAL).withProperties(
                PropertyFactory.lineColor(UniColors.Brick.toArgb()),
                PropertyFactory.lineWidth(ROUTE_WIDTH_PX),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_ROUTE_FALLBACK, SOURCE_ROUTE_FALLBACK).withProperties(
                PropertyFactory.lineColor(UniColors.Brick.toArgb()),
                PropertyFactory.lineWidth(ROUTE_WIDTH_PX),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                PropertyFactory.visibility(Property.NONE),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_STOPS, SOURCE_STOPS).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_SHUTTLES, SOURCE_SHUTTLES).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_SHUTTLE_ID)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
            ),
        )

        pushStops()
        pushShuttles()
        pushRoute()
    }

    private fun pushStops() {
        val style = style ?: return
        (style.getSource(SOURCE_STOPS) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(stopFeatures),
        )
    }

    private fun pushShuttles() {
        val style = style ?: return
        val ctx = context ?: return

        // Symbol icon ids equal the shuttle id property value exactly.
        val wantedIds = shuttleFeatures
            .mapNotNull { it.getStringProperty(PROP_SHUTTLE_ID) }
            .toSet()

        // Drop stale shuttle icons so markers never linger for gone shuttles.
        shuttleIconIds.filter { it !in wantedIds }.forEach { stale ->
            runCatching { style.removeImage(stale) }
        }
        wantedIds.filter { it !in shuttleIconIds }.forEach { id ->
            style.addImage(id, CampusMarkerFactory.shuttleIcon(ctx, "SH-$id"))
        }
        shuttleIconIds = wantedIds

        (style.getSource(SOURCE_SHUTTLES) as? GeoJsonSource)?.setGeoJson(
            FeatureCollection.fromFeatures(shuttleFeatures),
        )
    }

    private fun pushRoute() {
        val style = style ?: return
        val real = realRouteGeometry
        val fallback = fallbackRouteGeometry

        (style.getSource(SOURCE_ROUTE_REAL) as? GeoJsonSource)?.setGeoJson(
            real?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) }
                ?: FeatureCollection.fromFeatures(emptyList()),
        )
        (style.getSource(SOURCE_ROUTE_FALLBACK) as? GeoJsonSource)?.setGeoJson(
            fallback?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it)) }
                ?: FeatureCollection.fromFeatures(emptyList()),
        )
        style.getLayer(LAYER_ROUTE)?.setProperties(
            PropertyFactory.visibility(if (real != null) Property.VISIBLE else Property.NONE),
        )
        style.getLayer(LAYER_ROUTE_FALLBACK)?.setProperties(
            PropertyFactory.visibility(if (fallback != null) Property.VISIBLE else Property.NONE),
        )
    }

    private companion object {
        // 5dp at ~xxhdpi; MapLibre line-width is pixel-based.
        const val ROUTE_WIDTH_PX = 14f

        // Coordinates below this magnitude are treated as missing (0,0).
        const val MIN_ABS_COORDINATE = 0.0001

        const val SOURCE_STOPS = "unishuttle-stops"
        const val SOURCE_SHUTTLES = "unishuttle-shuttles"
        const val SOURCE_ROUTE_REAL = "unishuttle-route-real"
        const val SOURCE_ROUTE_FALLBACK = "unishuttle-route-fallback"

        const val LAYER_STOPS = "unishuttle-stops-layer"
        const val LAYER_SHUTTLES = "unishuttle-shuttles-layer"
        const val LAYER_ROUTE = "unishuttle-route-layer"
        const val LAYER_ROUTE_FALLBACK = "unishuttle-route-fallback-layer"

        const val ICON_STOP = "unishuttle-stop"
        const val ICON_STOP_SELECTED = "unishuttle-stop-selected"

        const val PROP_STOP_ID = "stopId"
        const val PROP_ICON = "icon"
        const val PROP_SHUTTLE_ID = "shuttleId"
        const val PROP_HEADING = "heading"
    }
}
