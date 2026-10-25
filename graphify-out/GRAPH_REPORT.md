# Graph Report - C:/Users/yamle/OneDrive/Desktop/UniShuttle Android  (2026-10-25)

## Corpus Check
- Corpus is ~26,044 words - fits in a single context window. You may not need a graph.

## Summary
- 594 nodes · 1148 edges · 31 communities (28 shown, 3 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 126 edges (avg confidence: 0.82)
- Token cost: 2,789,453 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Login UI & Components|Login UI & Components]]
- [[_COMMUNITY_Local Notification Storage|Local Notification Storage]]
- [[_COMMUNITY_Campus Map Screen|Campus Map Screen]]
- [[_COMMUNITY_Backend Rules & Build Config|Backend Rules & Build Config]]
- [[_COMMUNITY_Auth DI & User Model|Auth DI & User Model]]
- [[_COMMUNITY_Driver Location Service|Driver Location Service]]
- [[_COMMUNITY_Directions & ETA Network|Directions & ETA Network]]
- [[_COMMUNITY_Firestore DTOs & Repos|Firestore DTOs & Repos]]
- [[_COMMUNITY_Auth State & Admin Panel|Auth State & Admin Panel]]
- [[_COMMUNITY_Campus Ink Components|Campus Ink Components]]
- [[_COMMUNITY_App Entry & Messaging|App Entry & Messaging]]
- [[_COMMUNITY_Navigation Routes|Navigation Routes]]
- [[_COMMUNITY_Cloud Functions Package|Cloud Functions Package]]
- [[_COMMUNITY_Map Data ViewModels|Map Data ViewModels]]
- [[_COMMUNITY_TypeScript Config|TypeScript Config]]
- [[_COMMUNITY_Driver Duty & Seats|Driver Duty & Seats]]
- [[_COMMUNITY_Firebase Providers|Firebase Providers]]
- [[_COMMUNITY_Campus Ink Theme|Campus Ink Theme]]
- [[_COMMUNITY_Arrival Cloud Function|Arrival Cloud Function]]
- [[_COMMUNITY_Driver Seat Counter|Driver Seat Counter]]
- [[_COMMUNITY_Transit Domain Models|Transit Domain Models]]
- [[_COMMUNITY_Driver Cockpit Screens|Driver Cockpit Screens]]
- [[_COMMUNITY_Campus Map Defaults|Campus Map Defaults]]
- [[_COMMUNITY_Notification Cards|Notification Cards]]
- [[_COMMUNITY_Seat Meter Widgets|Seat Meter Widgets]]
- [[_COMMUNITY_Alert Pickers|Alert Pickers]]
- [[_COMMUNITY_Color Tokens|Color Tokens]]

## God Nodes (most connected - your core abstractions)
1. `CampusMapViewModel` - 25 edges
2. `AuthViewModel` - 24 edges
3. `UniShuttleTheme()` - 23 edges
4. `LocalNotificationRepository` - 20 edges
5. `DriverDashboardViewModel` - 20 edges
6. `StudentMapContent()` - 18 edges
7. `NotificationDao` - 16 edges
8. `FirebaseAuthRepository` - 16 edges
9. `SeatAvailability` - 16 edges
10. `StudentMapContent` - 15 edges

## Surprising Connections (you probably didn't know these)
- `NotificationCenterViewModel` --implements--> `Clean Architecture + MVI/MVVM (Unidirectional Data Flow)`  [INFERRED]
  app/src/main/java/com/unishuttle/app/presentation/viewmodel/NotificationCenterViewModel.kt → UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md
- `StudentMapViewModel` --implements--> `Clean Architecture + MVI/MVVM (Unidirectional Data Flow)`  [INFERRED]
  app/src/main/java/com/unishuttle/app/presentation/viewmodel/StudentMapViewModel.kt → UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md
- `Google Maps Dark Style (map_style_dark.json)` --conceptually_related_to--> `UniShuttle Master System Context & Design Specification`  [INFERRED]
  app/src/main/res/raw/map_style_dark.json → UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md
- `Google Maps Light Style (map_style_light.json)` --conceptually_related_to--> `UniShuttle Master System Context & Design Specification`  [INFERRED]
  app/src/main/res/raw/map_style_light.json → UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md
- `Phase 3: Notifications + Alerts` --references--> `functions package.json (unishuttle-functions)`  [EXTRACTED]
  docs/phase-3-notifications-alerts.md → functions/package.json

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Seat Occupancy Display Family (threshold-colored animated bars)** — seatmeter, seatavailabilitymeter, seatavailability [INFERRED 0.85]
- **Neo-Brutalist Component Kit (2dp ink borders, hard offset shadows, pill shapes, press-offset animation)** — unicard, unibutton, alertchip, roletoggle, shuttlemarker, unibottomsheet [INFERRED 0.85]
- **Campus Ink design system (palette, type, shapes, hard shadows)** — color_UniColors, theme_UniTypography, theme_UniShapes, theme_UniShuttleTheme, theme_hardShadow, theme_campus_ink_design [EXTRACTED 1.00]
- **Proximity Alert Pipeline (RTDB trigger, Haversine check, threshold map, FCM topic push)** — index_onshuttlelocationupdate, index_haversinedistance, index_thresholdmap, index_fcm_topic_convention [EXTRACTED 0.95]

## Communities (31 total, 3 thin omitted)

### Community 0 - "Login UI & Components"
Cohesion: 0.05
Nodes (65): GoogleSignInButton(), LoginContent(), LoginPreviewDark(), LoginPreviewLight(), LoginScreen(), ShuttleIcon(), RegisterScreen(), Boolean (+57 more)

### Community 1 - "Local Notification Storage"
Cohesion: 0.06
Nodes (26): NotificationDao, UniShuttleDatabase, DatabaseModule, NotificationEntity, NotificationPreferenceEntity, StopAlertEntity, FirebaseMessagingService, NotificationItem (+18 more)

### Community 2 - "Campus Map Screen"
Cohesion: 0.08
Nodes (23): Float, Flow, List, AnimatedShuttleMarker(), CampusMapScreen(), MapStatusPill(), markerSnippet(), normalizeHeading() (+15 more)

### Community 3 - "Backend Rules & Build Config"
Cohesion: 0.07
Nodes (36): Root build.gradle.kts (AGP 9.4.0, Kotlin 2.3.21, Hilt 2.60.1), RTDB Security Rules (database.rules.json), FirebaseDatabase singleton, FirebaseShuttleLocationRepository, functions package.json (unishuttle-functions), functions tsconfig.json (strict, outDir lib), Institutional Domain Lock (@vitstudent.ac.in), Google Sign-In Setup Guide (Credential Manager) (+28 more)

### Community 4 - "Auth DI & User Model"
Cohesion: 0.09
Nodes (17): FirebaseAuth singleton, AuthModule, RepositoryModule, Exception, FirebaseAuth, AppUser, UserRole, AuthRepository (+9 more)

### Community 5 - "Driver Location Service"
Cohesion: 0.09
Nodes (17): BitmapDescriptor, AnimatedShuttleMarker, AlertTimePicker(), Context, LocationModule, DriverLocationService, startIntent(), FusedLocationProviderClient (+9 more)

### Community 6 - "Directions & ETA Network"
Cohesion: 0.07
Nodes (18): DirectionsApiService, App Build Configuration (build.gradle.kts), Campus Domain Lock (@vitstudent.ac.in), Cumulative Leg-Duration ETA Computation, NetworkModule, DirectionsLegDto, DirectionsPolylineDto, DirectionsResponseDto (+10 more)

### Community 7 - "Firestore DTOs & Repos"
Cohesion: 0.10
Nodes (12): Double, FirebaseShuttleLocationDto, FirestoreSeatAvailabilityDto, FirestoreShuttleStopDto, FirebaseMappers, Long, FirebaseSeatAvailabilityRepository, FirebaseShuttleLocationRepository (+4 more)

### Community 8 - "Auth State & Admin Panel"
Cohesion: 0.13
Nodes (14): AdminPanelScreen(), AdminTile(), AppUser (domain model), GetCredentialResponse, LoginScreen, RegisterScreen, Google Sign-In Auto-Registration, SignOutDialog (+6 more)

### Community 9 - "Campus Ink Components"
Cohesion: 0.13
Nodes (21): AlertChip, AlertTimePicker, CampusMapDefaults (VIT Vellore), CampusMapScreen, ShuttleRoutePolyline, StopMarker (CampusMapScreen private), DriverHomeScreen, EtaBadge (+13 more)

### Community 10 - "App Entry & Messaging"
Cohesion: 0.12
Nodes (13): MainActivity, UniShuttleApplication, Application, Bundle, ComponentActivity, DriverLocationService, EtaBadge, MapFrame (+5 more)

### Community 11 - "Navigation Routes"
Cohesion: 0.17
Nodes (19): AdminPanelScreen, AuthUiState (Loading/SignedIn/SignedOut), LoginScreen / LoginContent, GoogleSignInButton, ShuttleIcon (canvas illustration), AdminPanel, DriverDashboard, Login (+11 more)

### Community 12 - "Cloud Functions Package"
Cohesion: 0.12
Nodes (16): dependencies, firebase-admin, firebase-functions, description, devDependencies, typescript, engines, node (+8 more)

### Community 13 - "Map Data ViewModels"
Cohesion: 0.18
Nodes (4): ETA Refresh Throttling (15s), Job, ShuttleStopRepository, CampusMapViewModel

### Community 14 - "TypeScript Config"
Cohesion: 0.14
Nodes (13): compileOnSave, compilerOptions, esModuleInterop, forceConsistentCasingInFileNames, module, noImplicitReturns, noUnusedLocals, outDir (+5 more)

### Community 15 - "Driver Duty & Seats"
Cohesion: 0.23
Nodes (6): AndroidViewModel, Off-Duty Sentinel Write, StudentMapScreen, StudentMapViewModel, DriverDashboardUiState, DriverDashboardViewModel

### Community 16 - "Firebase Providers"
Cohesion: 0.18
Nodes (7): FirebaseModule, FirebaseDatabase, FirebaseFirestore, FirebaseMessaging, FirebaseMessaging singleton, FirebaseFirestore singleton, FirebaseShuttleStopRepository

### Community 17 - "Campus Ink Theme"
Cohesion: 0.28
Nodes (9): UniColors palette, UniDarkScheme, UniLightScheme, UniShapes, UniShuttleTheme, UniTypography, Campus Ink Design Language, Modifier.hardShadow (+1 more)

### Community 18 - "Arrival Cloud Function"
Cohesion: 0.22
Nodes (7): firestore, messaging, onShuttleLocationUpdate, SeatData, ShuttleData, StopDoc, THRESHOLD_MAP

### Community 19 - "Driver Seat Counter"
Cohesion: 0.32
Nodes (3): SeatAvailabilityRepository, StateFlow, DriverSeatViewModel

### Community 20 - "Transit Domain Models"
Cohesion: 0.29
Nodes (6): CampusLatLng (domain model), ShuttleLocation (domain model), ShuttleStop (domain model), StopAlert (domain model), StopEta (domain model), CampusMapUiState

### Community 21 - "Driver Cockpit Screens"
Cohesion: 0.47
Nodes (5): DriverDashboardScreen, DriverSeatCounterScreen, SeatAvailability (domain model), SeatAvailabilityMeter, DriverSeatUiState

### Community 23 - "Notification Cards"
Cohesion: 0.67
Nodes (3): NotificationRow, NotificationCategory (ARRIVAL/SYSTEM/DELAY), UniCard

### Community 24 - "Seat Meter Widgets"
Cohesion: 1.00
Nodes (3): SeatAvailability (domain model), SeatAvailabilityMeter, SeatMeter

## Knowledge Gaps
- **70 isolated node(s):** `DirectionsRouteDto`, `DirectionsPolylineDto`, `DirectionsLegDto`, `DirectionsTextValueDto`, `UniColors` (+65 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CampusMapViewModel` connect `Map Data ViewModels` to `Community None`, `Local Notification Storage`, `Campus Map Screen`, `Backend Rules & Build Config`, `Driver Location Service`, `Campus Ink Components`, `Driver Duty & Seats`, `Driver Seat Counter`, `Transit Domain Models`?**
  _High betweenness centrality (0.084) - this node is a cross-community bridge._
- **Why does `LocalNotificationRepository` connect `Local Notification Storage` to `Firebase Providers`, `Community None`, `Campus Map Screen`, `Auth DI & User Model`?**
  _High betweenness centrality (0.069) - this node is a cross-community bridge._
- **Why does `DriverDashboardViewModel` connect `Driver Duty & Seats` to `Login UI & Components`, `Auth DI & User Model`, `Driver Location Service`, `Auth State & Admin Panel`, `Map Data ViewModels`, `Driver Seat Counter`, `Driver Cockpit Screens`?**
  _High betweenness centrality (0.068) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `CampusMapViewModel` (e.g. with `DriverDashboardViewModel` and `DriverSeatViewModel`) actually correct?**
  _`CampusMapViewModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `DirectionsRouteDto`, `DirectionsPolylineDto`, `DirectionsLegDto` to the rest of the system?**
  _76 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Login UI & Components` be split into smaller, more focused modules?**
  _Cohesion score 0.05185779203421545 - nodes in this community are weakly interconnected._
- **Should `Local Notification Storage` be split into smaller, more focused modules?**
  _Cohesion score 0.059907834101382486 - nodes in this community are weakly interconnected._