# UniShuttle Phase 1 Core Map + Tracking

## Firebase Realtime Database

Live shuttle locations are read and written at `shuttles/{shuttleId}`:

```json
{
  "shuttles": {
    "blue-1": {
      "latitude": 37.4221,
      "longitude": -122.0841,
      "heading": 92.5,
      "updatedAtMillis": 1799870000000
    }
  }
}
```

## Firestore Stops

Static stop markers are read from the `stops` collection:

```text
stops/{stopId}
├── name: string
├── latitude: number
├── longitude: number
└── order: number
```

## Driver GPS Writer

Future driver UI can start the foreground GPS writer like this:

```kotlin
ContextCompat.startForegroundService(
    context,
    DriverLocationService.startIntent(context, shuttleId = "blue-1"),
)
```

Request location permission before starting the service. Android 13+ also needs notification permission for the foreground notification.

## Directions API

Add both keys to local `local.properties`:

```properties
MAPS_API_KEY=your_android_restricted_maps_key
DIRECTIONS_API_KEY=your_directions_api_key
```

For production, move the Directions API call behind a backend or Cloud Function so the web-service key is not shipped in the APK.
