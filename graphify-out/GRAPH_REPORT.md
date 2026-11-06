# Graph Report - .  (2026-11-01)

## Corpus Check
- Corpus is ~26,645 words - fits in a single context window. You may not need a graph.

## Summary
- 482 nodes · 904 edges · 38 communities (25 shown, 13 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 63 edges (avg confidence: 0.8)
- Token cost: 1,530 input · 1,661 output

## Community Hubs (Navigation)
- [[_COMMUNITY_UI Components & Authentication Screens|UI Components & Authentication Screens]]
- [[_COMMUNITY_Local Notification Storage & Alerts|Local Notification Storage & Alerts]]
- [[_COMMUNITY_Notification State & Models|Notification State & Models]]
- [[_COMMUNITY_Map Controller & Location Services|Map Controller & Location Services]]
- [[_COMMUNITY_Seat Availability & Route Data|Seat Availability & Route Data]]
- [[_COMMUNITY_Map & Driver ViewModels|Map & Driver ViewModels]]
- [[_COMMUNITY_User Authentication & Domain Models|User Authentication & Domain Models]]
- [[_COMMUNITY_Role-Based Access & Admin Panel|Role-Based Access & Admin Panel]]
- [[_COMMUNITY_Driver Location Service & Tracking|Driver Location Service & Tracking]]
- [[_COMMUNITY_Driver Dashboard & Seat Controls|Driver Dashboard & Seat Controls]]
- [[_COMMUNITY_Cloud Functions Configuration|Cloud Functions Configuration]]
- [[_COMMUNITY_OSRM Routing & Network Client|OSRM Routing & Network Client]]
- [[_COMMUNITY_Repository Dependency Injection|Repository Dependency Injection]]
- [[_COMMUNITY_TypeScript Build Configuration|TypeScript Build Configuration]]
- [[_COMMUNITY_Navigation Routes & Destinations|Navigation Routes & Destinations]]
- [[_COMMUNITY_Backend Geo-proximity Cloud Functions|Backend Geo-proximity Cloud Functions]]
- [[_COMMUNITY_Map Provider & OpenFreeMap Config|Map Provider & OpenFreeMap Config]]
- [[_COMMUNITY_Room Database & Persistence Module|Room Database & Persistence Module]]
- [[_COMMUNITY_Firebase Infrastructure Providers|Firebase Infrastructure Providers]]
- [[_COMMUNITY_Main Application Activity & Permissions|Main Application Activity & Permissions]]
- [[_COMMUNITY_Application Initialization & FCM Setup|Application Initialization & FCM Setup]]
- [[_COMMUNITY_Firebase Auth Injection|Firebase Auth Injection]]
- [[_COMMUNITY_Domain Auth Specifications & Plans|Domain Auth Specifications & Plans]]
- [[_COMMUNITY_Firestore Seat Availability DTO|Firestore Seat Availability DTO]]
- [[_COMMUNITY_Firestore Shuttle Stop DTO|Firestore Shuttle Stop DTO]]
- [[_COMMUNITY_Campus Map Coordinates Defaults|Campus Map Coordinates Defaults]]
- [[_COMMUNITY_Role Auth & Driver Privacy Spec|Role Auth & Driver Privacy Spec]]
- [[_COMMUNITY_Theme Colors & Palette|Theme Colors & Palette]]
- [[_COMMUNITY_Google Maps Migration Architecture|Google Maps Migration Architecture]]
- [[_COMMUNITY_Phase 0 Project Setup Roadmap|Phase 0 Project Setup Roadmap]]
- [[_COMMUNITY_Phase 1 Map Tracking Roadmap|Phase 1 Map Tracking Roadmap]]
- [[_COMMUNITY_Phase 2 Seat & ETA Roadmap|Phase 2 Seat & ETA Roadmap]]
- [[_COMMUNITY_Phase 3 Notifications Roadmap|Phase 3 Notifications Roadmap]]
- [[_COMMUNITY_Clean Architecture & MVI Guidelines|Clean Architecture & MVI Guidelines]]

## God Nodes (most connected - your core abstractions)
1. `CampusMapController` - 26 edges
2. `UniShuttleTheme()` - 23 edges
3. `StudentMapContent()` - 22 edges
4. `AuthViewModel` - 21 edges
5. `CampusMapViewModel` - 16 edges
6. `NotificationDao` - 14 edges
7. `SeatAvailability` - 13 edges
8. `FirebaseAuthRepository` - 12 edges
9. `DriverLocationService` - 12 edges
10. `LoginContent()` - 12 edges

## Surprising Connections (you probably didn't know these)
- `MapProviderConfig` --references--> `OpenFreeMap`  [EXTRACTED]
  app/src/main/java/com/unishuttle/app/config/MapProviderConfig.kt → docs/map-and-routing.md
- `MapProviderConfig` --references--> `OSRM (Open Source Routing Machine)`  [EXTRACTED]
  app/src/main/java/com/unishuttle/app/config/MapProviderConfig.kt → docs/map-and-routing.md
- `Google Sign-in, Role Routing, Sign-out Implementation Plan` --references--> `UniShuttle Master System Context & Design Specification`  [INFERRED]
  .zcode/plans/plan-session_fe4a432de895d4b69e4c75aeb41049d8.md → UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md
- `DriverDashboardScreen()` --calls--> `SeatAvailability`  [INFERRED]
  app/src/main/java/com/unishuttle/app/presentation/ui/screen/driver/DriverDashboardScreen.kt → app/src/main/java/com/unishuttle/app/domain/model/SeatAvailability.kt
- `DriverSeatCounterScreen()` --calls--> `SeatAvailability`  [INFERRED]
  app/src/main/java/com/unishuttle/app/presentation/ui/screen/driver/DriverSeatCounterScreen.kt → app/src/main/java/com/unishuttle/app/domain/model/SeatAvailability.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Realtime Shuttle Tracking Flow (driver RTDB writes feed student map and Cloud Function under RTDB rules)** — index_rtdb_shuttles_stream, studentmapviewmodel_studentmapviewmodel, index_onshuttlelocationupdate, database_rules_json [INFERRED 0.85]
- **Map and Routing Infrastructure** — map_and_routing_maplibre_native_android, map_and_routing_osrm, map_and_routing_openfreemap, mapproviderconfig_map_provider_config [EXTRACTED 1.00]

## Communities (38 total, 13 thin omitted)

### Community 0 - "UI Components & Authentication Screens"
Cohesion: 0.06
Nodes (52): GoogleSignInButton(), LoginContent(), LoginPreviewDark(), LoginPreviewLight(), LoginScreen(), ShuttleIcon(), RegisterScreen(), BoxScope (+44 more)

### Community 1 - "Local Notification Storage & Alerts"
Cohesion: 0.07
Nodes (17): AlertTimePicker(), NotificationDao, Double, FirebaseShuttleLocationDto, NotificationPreferenceEntity, StopAlertEntity, FirebaseMessagingService, Float (+9 more)

### Community 2 - "Notification State & Models"
Cohesion: 0.11
Nodes (15): NotificationEntity, Flow, List, NotificationItem, NotificationType, NotificationPreference, StopAlert, EmptyState() (+7 more)

### Community 3 - "Map Controller & Location Services"
Cohesion: 0.09
Nodes (14): Bitmap, Boolean, Context, LocationModule, Feature, FusedLocationProviderClient, LatLng, LineString (+6 more)

### Community 4 - "Seat Availability & Route Data"
Cohesion: 0.11
Nodes (16): com, SeatMeter(), SeatMeterPreviewDark(), toDomain(), SeatAvailability, ShuttleLocation, ShuttleStop, StopEta (+8 more)

### Community 5 - "Map & Driver ViewModels"
Cohesion: 0.08
Nodes (9): Job, StateFlow, ViewModel, CampusMapUiState, CampusMapViewModel, DriverSeatUiState, DriverSeatViewModel, NotificationCenterUiState (+1 more)

### Community 6 - "User Authentication & Domain Models"
Cohesion: 0.16
Nodes (12): Exception, AppUser, AuthRepository, AuthState, ProfileUnavailable, SignedIn, SignedOut, DomainRestrictionException (+4 more)

### Community 7 - "Role-Based Access & Admin Panel"
Cohesion: 0.15
Nodes (9): AdminPanelScreen(), AdminTile(), GetCredentialResponse, UserRole, AuthUiState, AuthViewModel, Loading, SignedIn (+1 more)

### Community 8 - "Driver Location Service & Tracking"
Cohesion: 0.14
Nodes (8): DriverLocationService, startIntent(), IBinder, Intent, Notification, FirebaseShuttleLocationRepository, ShuttleLocationRepository, Service

### Community 9 - "Driver Dashboard & Seat Controls"
Cohesion: 0.15
Nodes (9): AndroidViewModel, labelText(), meterColor(), SeatAvailabilityMeter(), statusText(), DriverDashboardScreen(), DriverSeatCounterScreen(), DriverDashboardUiState (+1 more)

### Community 10 - "Cloud Functions Configuration"
Cohesion: 0.12
Nodes (16): dependencies, firebase-admin, firebase-functions, description, devDependencies, typescript, engines, node (+8 more)

### Community 11 - "OSRM Routing & Network Client"
Cohesion: 0.15
Nodes (8): OsrmApiService, NetworkModule, OsrmGeometryDto, OsrmLegDto, OsrmRouteDto, OsrmRouteResponseDto, OkHttpClient, Retrofit

### Community 12 - "Repository Dependency Injection"
Cohesion: 0.16
Nodes (5): RepositoryModule, FirebaseSeatAvailabilityRepository, FirebaseShuttleStopRepository, SeatAvailabilityRepository, ShuttleStopRepository

### Community 13 - "TypeScript Build Configuration"
Cohesion: 0.14
Nodes (13): compileOnSave, compilerOptions, esModuleInterop, forceConsistentCasingInFileNames, module, noImplicitReturns, noUnusedLocals, outDir (+5 more)

### Community 14 - "Navigation Routes & Destinations"
Cohesion: 0.42
Nodes (8): AdminPanel, DriverDashboard, Login, Map, NavRoute, NotificationCenter, Register, StudentMap

### Community 15 - "Backend Geo-proximity Cloud Functions"
Cohesion: 0.22
Nodes (7): firestore, messaging, onShuttleLocationUpdate, SeatData, ShuttleData, StopDoc, THRESHOLD_MAP

### Community 16 - "Map Provider & OpenFreeMap Config"
Cohesion: 0.25
Nodes (7): MapProviderConfig, MapLibre Native Android, OpenFreeMap, OSRM (Open Source Routing Machine), OsrmApiService, OsrmRoutingRepository, RouteRepository

### Community 17 - "Room Database & Persistence Module"
Cohesion: 0.29
Nodes (3): UniShuttleDatabase, DatabaseModule, RoomDatabase

### Community 18 - "Firebase Infrastructure Providers"
Cohesion: 0.25
Nodes (4): FirebaseModule, FirebaseDatabase, FirebaseFirestore, FirebaseMessaging

### Community 19 - "Main Application Activity & Permissions"
Cohesion: 0.40
Nodes (3): MainActivity, Bundle, ComponentActivity

### Community 22 - "Domain Auth Specifications & Plans"
Cohesion: 0.67
Nodes (4): Institutional Domain Lock (@vitstudent.ac.in), Google Sign-In Setup Guide (Credential Manager), Google Sign-in, Role Routing, Sign-out Implementation Plan, UniShuttle Master System Context & Design Specification

## Knowledge Gaps
- **49 isolated node(s):** `FirestoreSeatAvailabilityDto`, `FirestoreShuttleStopDto`, `OsrmRouteDto`, `OsrmGeometryDto`, `OsrmLegDto` (+44 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuthViewModel` connect `Role-Based Access & Admin Panel` to `UI Components & Authentication Screens`, `Local Notification Storage & Alerts`, `Map Controller & Location Services`, `Map & Driver ViewModels`, `Driver Dashboard & Seat Controls`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Why does `StudentMapContent()` connect `Seat Availability & Route Data` to `UI Components & Authentication Screens`, `Local Notification Storage & Alerts`, `Notification State & Models`, `Map Controller & Location Services`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Why does `CampusMapController` connect `Map Controller & Location Services` to `UI Components & Authentication Screens`, `Local Notification Storage & Alerts`, `Notification State & Models`, `Seat Availability & Route Data`?**
  _High betweenness centrality (0.053) - this node is a cross-community bridge._
- **What connects `FirestoreSeatAvailabilityDto`, `FirestoreShuttleStopDto`, `OsrmRouteDto` to the rest of the system?**
  _51 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `UI Components & Authentication Screens` be split into smaller, more focused modules?**
  _Cohesion score 0.05924978687127025 - nodes in this community are weakly interconnected._
- **Should `Local Notification Storage & Alerts` be split into smaller, more focused modules?**
  _Cohesion score 0.06972789115646258 - nodes in this community are weakly interconnected._
- **Should `Notification State & Models` be split into smaller, more focused modules?**
  _Cohesion score 0.1066066066066066 - nodes in this community are weakly interconnected._