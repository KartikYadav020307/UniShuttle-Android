# UniShuttle Phase 2 - Seat Availability + ETA

Phase 2 adds live seat counts, stop ETAs, stop details, and a driver seat counter. It does not add push notifications, QR reservations, or login screens.

## Firestore Shape

Create a `seatAvailability` collection in Firestore. Each document id should match the shuttle id already used in Realtime Database.

Example document path:

```text
seatAvailability/demo-shuttle
```

Example fields:

```json
{
  "availableSeats": 18,
  "totalSeats": 24,
  "updatedAtMillis": 1789334400000
}
```

The passenger map listens to this collection in real time. The driver seat counter writes to this same collection.

## ETA Behavior

ETAs are computed in `GoogleDirectionsRouteRepository` with the Google Directions API:

- Origin: current shuttle latitude/longitude from Realtime Database.
- Destination: the last Firestore stop.
- Waypoints: all previous Firestore stops.
- Duration source: `duration_in_traffic` when Google returns it, otherwise `duration`.

The map ViewModel refreshes ETAs when shuttle positions or stops change, throttled to once every 15 seconds to keep API calls reasonable.

## Phase 2 Files

```text
app/src/main/java/com/unishuttle/app/domain/model/SeatAvailability.kt
app/src/main/java/com/unishuttle/app/domain/model/StopEta.kt
app/src/main/java/com/unishuttle/app/domain/repository/SeatAvailabilityRepository.kt
app/src/main/java/com/unishuttle/app/domain/repository/RouteRepository.kt
app/src/main/java/com/unishuttle/app/data/remote/dto/FirestoreSeatAvailabilityDto.kt
app/src/main/java/com/unishuttle/app/data/remote/dto/DirectionsResponseDto.kt
app/src/main/java/com/unishuttle/app/data/remote/api/DirectionsApiService.kt
app/src/main/java/com/unishuttle/app/data/mapper/FirebaseMappers.kt
app/src/main/java/com/unishuttle/app/data/repository/FirebaseSeatAvailabilityRepository.kt
app/src/main/java/com/unishuttle/app/data/repository/GoogleDirectionsRouteRepository.kt
app/src/main/java/com/unishuttle/app/di/RepositoryModule.kt
app/src/main/java/com/unishuttle/app/presentation/viewmodel/CampusMapUiState.kt
app/src/main/java/com/unishuttle/app/presentation/viewmodel/CampusMapViewModel.kt
app/src/main/java/com/unishuttle/app/presentation/viewmodel/DriverSeatUiState.kt
app/src/main/java/com/unishuttle/app/presentation/viewmodel/DriverSeatViewModel.kt
app/src/main/java/com/unishuttle/app/presentation/ui/component/SeatAvailabilityMeter.kt
app/src/main/java/com/unishuttle/app/presentation/ui/screen/map/CampusMapScreen.kt
app/src/main/java/com/unishuttle/app/presentation/ui/screen/map/StopDetailBottomSheet.kt
app/src/main/java/com/unishuttle/app/presentation/ui/screen/driver/DriverSeatCounterScreen.kt
```
