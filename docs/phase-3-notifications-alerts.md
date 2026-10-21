# UniShuttle Phase 3 — Notifications + Alerts

Phase 3 adds client-side FCM integration, stop arrival alerts with configurable thresholds, a Notification Center screen, and a server-side Cloud Function that triggers push notifications when a shuttle approaches a stop.

## FCM Client Setup

### Notification Channel

The `shuttle_alerts` channel is created in `UniShuttleApplication.onCreate()` with `IMPORTANCE_HIGH`. On Android 8+ all notifications require a channel.

### FCM Token

The FCM token is logged at app launch. Future phases can send this token to a backend for per-user targeted pushes.

### Messaging Service

`UniShuttleMessagingService` extends `FirebaseMessagingService` and:

- Persists every incoming message to Room (appears in Notification Center history)
- Shows a system notification via `NotificationCompat` (works foreground + background)
- Handles both `notification` and `data` payloads

### POST_NOTIFICATIONS Permission

Requested in `MainActivity.onCreate()` on Android 13+. The app works without it — notifications just won't show in the system tray.

## FCM Topic Convention

When a user sets an alert for a stop, the app subscribes to an FCM topic:

```
stop_{stopId}_{thresholdMinutes}min
```

Examples:
- `stop_library_10min`
- `stop_library_5min`
- `stop_engineering_2min`

## Room Database

`UniShuttleDatabase` (version 1) contains three tables:

| Table                      | Purpose                            |
|----------------------------|------------------------------------|
| `stop_alerts`              | Active alert subscriptions         |
| `notifications`            | Notification history               |
| `notification_preferences` | Per-stop/route toggle preferences  |

## Stop Alert Flow

1. User taps a stop marker → Bottom sheet opens
2. User taps **"Alert Me"** → AlertTimePicker appears (10 / 5 / 2 min chips)
3. User selects a threshold → `LocalNotificationRepository`:
   - Subscribes to the FCM topic
   - Persists `StopAlertEntity` to Room
   - Creates a `NotificationPreferenceEntity` for the stop

## Notification Center

Two tabs:

- **History**: `LazyColumn` of past `NotificationItem` cards with relative timestamps
- **Preferences**: `Switch` toggles per stop/route that subscribe/unsubscribe from FCM topics

## Firebase Cloud Function

### Trigger

RTDB `onValueWritten` on `shuttles/{shuttleId}`

### Logic

1. Read the shuttle's new lat/lng
2. Fetch all stops from Firestore `stops` collection
3. Compute haversine distance from shuttle to each stop
4. For each stop within a threshold, read seat availability from Firestore
5. Send FCM to the matching topic(s)

### Distance Thresholds

| Alert     | Distance     |
|-----------|-------------|
| 10 min    | ≤ 2,500 m   |
| 5 min     | ≤ 1,200 m   |
| 2 min     | ≤ 500 m     |

### Notification Format

```
Title: 🚐 Shuttle approaching Library
Body:  Shuttle blue-1 is ~5 min away – 3 seats left
```

### Deployment

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

## Phase 3 Files

```text
# Domain
domain/model/StopAlert.kt
domain/model/NotificationItem.kt
domain/model/NotificationPreference.kt
domain/repository/NotificationRepository.kt

# Data — Room
data/local/entity/NotificationEntity.kt
data/local/entity/StopAlertEntity.kt
data/local/entity/NotificationPreferenceEntity.kt
data/local/dao/NotificationDao.kt
data/local/database/UniShuttleDatabase.kt

# Data — FCM + Repository
data/remote/UniShuttleMessagingService.kt
data/repository/LocalNotificationRepository.kt

# DI
di/DatabaseModule.kt
di/FirebaseModule.kt          (modified)
di/RepositoryModule.kt        (modified)

# Presentation
presentation/navigation/NavRoute.kt
presentation/ui/component/AlertTimePicker.kt
presentation/ui/screen/map/StopDetailBottomSheet.kt       (modified)
presentation/ui/screen/map/CampusMapScreen.kt              (modified)
presentation/ui/screen/notifications/NotificationCenterScreen.kt
presentation/viewmodel/CampusMapUiState.kt                 (modified)
presentation/viewmodel/CampusMapViewModel.kt               (modified)
presentation/viewmodel/NotificationCenterUiState.kt
presentation/viewmodel/NotificationCenterViewModel.kt

# App
UniShuttleApplication.kt      (modified)
MainActivity.kt               (modified)
AndroidManifest.xml            (modified)

# Cloud Function
functions/package.json
functions/tsconfig.json
functions/src/index.ts
```
