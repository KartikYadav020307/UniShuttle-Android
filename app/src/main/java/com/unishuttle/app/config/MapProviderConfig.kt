package com.unishuttle.app.config

/**
 * Central configuration for map and routing providers.
 *
 * Switching providers later means changing these values (or the repository
 * implementations they feed) — screen code never hardcodes provider URLs.
 *
 * Map styles: OpenFreeMap (https://openfreemap.org), no API key, no billing.
 * Routing: OSRM public demo server (https://project-osrm.org) — demonstration
 * service only; see docs/map-and-routing.md for limitations and alternatives.
 */
object MapProviderConfig {
    /** OpenFreeMap "Liberty" style — light theme. */
    const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** OpenFreeMap "Dark" style — dark theme. */
    const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

    /** OSRM public demo server. Rate-limited; not SLA-backed. */
    const val OSRM_BASE_URL = "https://router.project-osrm.org/"

    /**
     * The OSRM demo server documents a one-request-per-second limit. The
     * routing repository serializes requests at least this far apart.
     */
    const val OSRM_MIN_REQUEST_INTERVAL_MILLIS = 1_000L

    /** Attribution required by OpenFreeMap / OpenMapTiles / OpenStreetMap. */
    const val ATTRIBUTION_HTML =
        "<a href=\"https://openfreemap.org\" target=\"_blank\">OpenFreeMap</a> " +
            "<a href=\"https://www.openmaptiles.org/\" target=\"_blank\">© OpenMapTiles</a> " +
            "<a href=\"https://www.openstreetmap.org/copyright\" target=\"_blank\">© OpenStreetMap contributors</a>"
}
