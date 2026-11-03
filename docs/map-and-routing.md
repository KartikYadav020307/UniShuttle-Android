# Map and routing providers

UniShuttle renders its campus map with **MapLibre Native Android** and routes with
**OSRM**. Neither requires an API key, an account, or billing.

## Map rendering

- SDK: `org.maplibre.gl:android-sdk` (see `app/build.gradle.kts` for the pinned version).
- Styles: [OpenFreeMap](https://openfreemap.org) — light `liberty`, dark `dark`.
- All provider URLs live in one place: `com.unishuttle.app.config.MapProviderConfig`.
  To switch tile providers later, change the style URLs there (or point them at a
  self-hosted style) — no screen code changes are needed.
- Theme switching reloads only the style; markers, route geometry, and camera
  position survive because they live in GeoJSON sources outside the style.
- Attribution for OpenFreeMap / OpenMapTiles / OpenStreetMap is shown by the
  MapLibre attribution UI and must remain visible.

OpenFreeMap is a donation-funded public service without an SLA. If reliability
becomes a concern, self-host tiles (OpenFreeMap publishes full-planet images) or
move to another provider by changing the URLs in `MapProviderConfig`.

## Routing and ETAs

- `RouteRepository` is implemented by `OsrmRoutingRepository`, which calls the
  public OSRM demo server (`router.project-osrm.org`).
- Requests are serialized to at most one per second (the demo server's documented
  policy) and route geometry is cached by rounded coordinates, so identical
  routes are fetched once.
- The demo server is **not** production-grade. Treat it as a development
  provider; a self-hosted OSRM instance or another routing service can replace
  it by changing `MapProviderConfig.OSRM_BASE_URL` (and, if needed, the
  `OsrmApiService`).
- ETA values are cumulative OSRM driving durations from the shuttle's current
  position through the stop sequence. They do not model traffic, dwell time, or
  campus constraints, and the UI presents them as approximate minutes.
- When routing is unavailable the route line falls back to **dashed** straight
  segments between stops in sequence order — visually distinct so it is never
  mistaken for road geometry — and ETAs display as "—".

## What was removed (Google Maps migration)

- `com.google.maps.android:maps-compose` and `com.google.android.gms:play-services-maps`
  dependencies, the `com.google.android.geo.API_KEY` manifest entry, the raw
  Google map style JSONs, and all Google Maps/Directions classes
  (`CampusMapScreen`, `GoogleDirectionsRouteRepository`, `DirectionsApiService`,
  the Directions DTOs, `PolylineDecoder`).
- Firebase (Auth, Firestore, Realtime Database, Messaging, Crashlytics),
  Google Sign-In, `google-services.json`, and `play-services-location`
  (driver location service) are intentionally retained.
- `MAPS_API_KEY` in `local.properties` is no longer read by the app and can be
  deleted from local setup once no other build of the project needs it.
