# UniShuttle — Master System Context & Design Architecture Specification

> **Target Audience:** Claude / AI System Architects & Senior UI/UX Engineers.  
> **Purpose:** Comprehensive source of truth for generating world-class Jetpack Compose UIs, establishing the complete design system, and implementing the end-to-end scalable architecture for the **UniShuttle** campus transit ecosystem.

---

## 1. Executive Summary & Vision

**UniShuttle** is a real-time smart campus transit and shuttle tracking platform designed for university campuses (specifically deployed for **Vellore Institute of Technology / VIT**, with institutional domain enforcement for `@vitstudent.ac.in`).

### Core Value Proposition
- **For Students:** Eliminates uncertainty about shuttle arrivals. Students track shuttles live on a campus map with real-time heading rotation, see live seat availability before walking to the stop, get automated arrival push notifications at 10, 5, or 2-minute thresholds, and check dynamic traffic-aware ETAs.
- **For Drivers:** A frictionless, battery-optimized dashboard with an explicit **Duty Switch** guaranteeing complete privacy off-duty, an instant single-tap seat occupancy counter, and an automated background foreground service that broadcasts high-frequency GPS telemetry without draining device battery.
- **For Campus Administrators:** Centralized visibility over active fleet operations, stop and route management, broadcast emergency announcements, and driver assignment.

---

## 2. Technical Stack & Architecture Overview

The app is built following **Clean Architecture + MVI/MVVM principles** with modern Android standards:

| Layer | Technology / Library | Version | Purpose |
|---|---|---|---|
| **Language & Toolchain** | Kotlin + Kotlin Symbol Processing (KSP) | Kotlin `2.3.21`, KSP `2.3.10` | Modern Kotlin compiler with KSP code-gen |
| **UI Framework** | Jetpack Compose + Material 3 | Compose BOM `2026.08.00` | Declarative UI, Material You, dynamic theming |
| **Architecture** | Clean Architecture (Domain, Data, Presentation) | — | Unidirectional Data Flow, separation of concerns |
| **Dependency Injection** | Dagger Hilt | `2.60.1` | Modular dependency graph & ViewModel scoping |
| **Target Platforms** | Android SDK | `minSdk = 24`, `targetSdk = 37`, `compileSdk = 37` | High Android OS compatibility |
| **Realtime Telemetry** | Firebase Realtime Database (RTDB) | Firebase BOM `34.18.0` | Sub-second shuttle GPS location stream (`shuttles/{id}`) |
| **Cloud Document Store** | Cloud Firestore | Firebase BOM `34.18.0` | User profiles, stops, routes, seat counts |
| **Authentication** | Android Credential Manager + Firebase Auth | Credentials `1.5.0`, GoogleID `1.1.1` | Native Google One-Tap sign-in + institutional domain lock |
| **Push Notifications** | Firebase Cloud Messaging (FCM) + Cloud Functions | Firebase Functions v2 (Node.js/TS) | Geofenced topic pushes (`stop_{stopId}_{threshold}min`) |
| **Local Persistence** | Android Room Database (SQLite) | `2.8.5` | Offline alerts, notification history, user preferences |
| **Maps & Routing** | Google Maps Compose + Directions API | Maps Compose `8.4.0`, Retrofit `3.0.0` | Interactive map rendering, polyline decoding, live ETAs |
| **Image & Asset Loading**| Coil 3 + Lottie Compose | Coil `3.5.0`, Lottie `6.7.1` | Smooth vector illustrations, animated transitions |
| **Hardware Integration** | ML Kit Barcode Scanning + ZXing | ML Kit `17.3.0` | Fast QR ticket validation and driver boarding passes |

---

## 3. Core Role Personas & User Journeys

```
                                ┌────────────────────────┐
                                │   Cold Launch Splash   │
                                └───────────┬────────────┘
                                            │ Auth Gate (AuthViewModel)
                     ┌──────────────────────┴──────────────────────┐
                     ▼                                             ▼
            [Not Authenticated]                             [Authenticated]
                     │                                             │
            ┌─────────────────┐                                    ├─ Role: STUDENT ──► StudentMapScreen
            │   LoginScreen   │                                    ├─ Role: DRIVER  ──► DriverDashboardScreen
            └────────┬────────┘                                    └─ Role: ADMIN   ──► AdminPanelScreen
                     │ Select Role & Google Sign-In
                     ▼
      Credential Manager (One-Tap)
                     │
         Email Domain Validation
      (@vitstudent.ac.in for Student)
                     │
            Firestore Profile Sync
```

### 1. Student Persona
1. **Login:** Selects "Student" chip and signs in with Google (`@vitstudent.ac.in` domain enforced). Non-institutional emails are rejected with clear user feedback.
2. **Campus Map Navigation:** Views live Google Map centered on campus with custom stop markers and moving shuttle icons.
3. **Stop & Shuttle Inspection:** Taps any stop marker to pull up an expandable Bottom Sheet displaying:
   - Next approaching shuttles with live ETA (e.g., "3 min away").
   - Live seat capacity meter (e.g., "14/24 seats available").
   - **"Alert Me"** button with quick chips (10 min, 5 min, 2 min).
4. **Notification Center:**
   - **History Tab:** View past incoming shuttle alerts with relative timestamps ("2m ago").
   - **Preferences Tab:** Quick toggle switches to enable/disable alerts per stop or route.

### 2. Driver Persona
1. **Login:** Selects "Driver" chip and signs in via Google account.
2. **Driver Dashboard:**
   - **Duty Switch:**
     - **ON DUTY:** Starts Android foreground service (`DriverLocationService`), activates persistent notification, streams GPS coordinates to Firebase RTDB every 5 seconds, and enables seat controls.
     - **OFF DUTY:** Immediately stops service, kills GPS collection, and guarantees complete location privacy. Stale locations (>5 min) are automatically hidden from student maps.
   - **Seat Counter:** Large tactile `+` and `−` buttons with color-coded occupancy meter allowing the driver to adjust seat availability with one hand during stops.

### 3. Administrator Persona
1. **Access:** Assigned by updating `role` to `ADMIN` in Firestore `users/{uid}`.
2. **Admin Panel:**
   - Fleet oversight (all active drivers and their statuses).
   - Route and stop configuration (coordinates, sequences, geofence radius).
   - Campus broadcast notification center (system announcements, delays, weather alerts).

---

## 4. End-to-End System Architecture

```mermaid
flowchart TD
    subgraph Driver Device
        D1[Driver Dashboard UI] -->|Toggle On Duty| D2[DriverLocationService]
        D2 -->|Fused Location Provider| D3[GPS Coordinates]
        D1 -->|Adjust Seats| D4[Seat Counter UI]
    end

    subgraph Firebase Cloud
        D3 -->|Every 5s| RTDB[Firebase Realtime Database\n/shuttles/{shuttleId}]
        D4 -->|Firestore Write| FS_SEATS[Firestore\n/seatAvailability/{shuttleId}]
        
        RTDB -->|onValueWritten| CF[Cloud Functions v2\nonShuttleLocationUpdate]
        FS_STOPS[Firestore\n/stops/{stopId}] -->|Read Lat/Lng| CF
        FS_SEATS -->|Read Seats| CF
        CF -->|Haversine Distance Check| CF_EVAL{Within Threshold?}
        CF_EVAL -- Yes --> FCM[Firebase Cloud Messaging\nTopic: stop_{stopId}_{min}min]
    end

    subgraph Student Device
        FCM -->|Push Notification| S1[UniShuttleMessagingService]
        S1 -->|Store| S2[Room Database\nNotificationEntity]
        S1 -->|Alert User| S3[System Notification Tray]
        RTDB -->|Realtime Stream| S4[StudentMapScreen\nLive Shuttle Markers]
        FS_SEATS -->|Realtime Stream| S5[StopDetailBottomSheet\nSeat Availability Meter]
        MAPS[Google Directions API] -->|Traffic ETA| S5
    end
```

---

## 5. Detailed Data Schemas & API Contracts

### 5.1. Firebase Realtime Database (RTDB)
- **Path:** `/shuttles/{shuttleId}`
```json
{
  "latitude": 12.9698,
  "longitude": 79.1559,
  "heading": 184.2,
  "updatedAtMillis": 1799870000000
}
```
- **Rules (`database.rules.json`):**
  - Read: All authenticated users (`auth != null`).
  - Write: Authenticated drivers/admins.

### 5.2. Cloud Firestore Collections

#### `users/{uid}`
```json
{
  "uid": "k83hsd923jf98234",
  "email": "student@vitstudent.ac.in",
  "displayName": "Alex Sharma",
  "role": "STUDENT", // "STUDENT" | "DRIVER" | "ADMIN"
  "shuttleId": "",    // Filled for drivers, e.g. "shuttle-blue-1"
  "createdAtMillis": 1789330000000
}
```

#### `stops/{stopId}`
```json
{
  "id": "stop-main-gate",
  "name": "Main Gate Stop",
  "latitude": 12.9712,
  "longitude": 79.1594,
  "order": 1
}
```

#### `seatAvailability/{shuttleId}`
```json
{
  "shuttleId": "shuttle-blue-1",
  "availableSeats": 14,
  "totalSeats": 24,
  "updatedAtMillis": 1789334400000
}
```

### 5.3. Local Room Database Schema (`UniShuttleDatabase`)
1. **`notifications` Table:**
   - `id`: String (UUID primary key)
   - `title`: String
   - `body`: String
   - `receivedAtMillis`: Long
   - `isRead`: Boolean
2. **`stop_alerts` Table:**
   - `stopId`: String (Primary key)
   - `stopName`: String
   - `thresholdMinutes`: Int (10, 5, or 2)
   - `topicId`: String (`stop_{stopId}_{thresholdMinutes}min`)
   - `createdAtMillis`: Long
3. **`notification_preferences` Table:**
   - `topicId`: String (Primary key)
   - `label`: String
   - `enabled`: Boolean

### 5.4. Cloud Function FCM Topic Protocol
- Topics: `stop_{stopId}_{thresholdMinutes}min` (e.g., `stop_main-gate_5min`)
- Distance Thresholds:
  - **10 min alert:** Shuttle distance $\le$ 2,500 m
  - **5 min alert:** Shuttle distance $\le$ 1,200 m
  - **2 min alert:** Shuttle distance $\le$ 500 m

---

## 6. Current UI Audit & Pain Points

The current Compose UI is functionally operative but visually basic. Claude should prioritize redesigning these screens:

| Screen | Current Implementation | UX Deficiencies / Missing Elements | Claude Redesign Objective |
|---|---|---|---|
| **LoginScreen** | Simple centered column with basic Material buttons & chips | Lacks campus branding, illustrations, smooth entry animations, glassmorphism card container. | Create a high-end onboarding / auth screen with glowing campus visual, modern tabbed role toggle, and sleek Google Auth button. |
| **StudentMapScreen** | Fullscreen Google Map with floating action button | Standard map pin icons, plain default Google Maps styling, lacks top search/filter bar, route polyline overlay, or quick-glance shuttle status cards. | Premium custom dark/light map styling, animated pulse on driver markers with heading indicators, top route selector chips, and bottom persistent preview carousel. |
| **StopDetailBottomSheet** | Standard `ModalBottomSheet` with simple text and divider rows | Functional but looks like a developer wireframe. Plain buttons for alerts. | Modern floating rounded card sheet with interactive seat progress donut/bar, live ETA badge with traffic color codes, and animated alert picker chips. |
| **DriverDashboardScreen** | Card with duty switch and `+`/`−` buttons | Basic cards, buttons lack tactile feedback or bold driver-oriented ergonomics for high visibility while driving. | High-contrast "Cockpit" UI: giant toggle with glowing on-duty badge, prominent speed & telemetry status, massive thumb-friendly seat adjustment buttons with haptic feedback. |
| **NotificationCenterScreen** | TabRow with History and Preferences lists | Flat monochrome cards, basic empty states. | Polished notification feed with category icons (Arrivals, System, Delays), swipe-to-dismiss, and modern toggle switches. |
| **AdminPanelScreen** | Placeholder list of 4 static tiles | Informational only, non-interactive. | Executive dashboard with active fleet cards, live driver metric counters, map snapshot, and quick announcement dialog. |

---

## 7. Design System Guidelines for Claude

When designing UIs and components for UniShuttle, adhere to these aesthetic standards:

### 1. Color Palette Tokens
- **Primary Brand:** Deep Shuttle Blue (`0xFF0057B8`), Radiant Electric Blue (`0xFF2563EB`)
- **Accent & Alerts:** Stop Amber (`0xFFFFB020`), Alert Orange (`0xFFF97316`), Crimson Error (`0xFFEF4444`)
- **Campus & Success:** Campus Emerald (`0xFF10B981`), Bus Green (`0xFF1B7F4C`)
- **Surfaces & Backgrounds:**
  - *Light:* Ultra-soft Slate Surface (`0xFFF8FAFC`), Elevated Card (`0xFFFFFFFF`), Border (`0xFFE2E8F0`)
  - *Dark:* Midnight Navy (`0xFF0B0F17`), Elevated Dark Surface (`0xFF161F2E`), Card Surface (`0xFF1E293B`)

### 2. Typography Hierarchy
Use clean, modern sans-serif typography (Inter / Roboto) with strict weight hierarchy:
- **Display / Headers:** Bold / ExtraBold (28–34 sp) for screen titles and cockpit status.
- **Title / Cards:** SemiBold (18–22 sp) for stops and shuttle IDs.
- **Body & Captions:** Medium (14–16 sp) and Regular (12–13 sp) with subtle muted colors (`onSurfaceVariant`).

### 3. Micro-Animations & Interactivity
- **Live Marker Movement:** Smooth interpolation (`animateFloatAsState` / `Animatable`) for lat/lng changes to avoid jittery shuttle jumps.
- **Heading Rotation:** Smooth rotation bearing transition so shuttles point in their actual direction of travel.
- **Seat Progress Bars:** Smooth animated fill width and color shift (Green $>50\%$ $\to$ Amber $20\text{--}50\%$ $\to$ Red $<20\%$).
- **Haptics:** Haptic feedback on driver seat increment/decrement and alert chip selection.

---

## 8. Directory & File Navigation Reference

```text
UniShuttle Android/
├── UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md   <-- THIS MASTER SPECIFICATION FILE
├── build.gradle.kts                         <-- Root build script (AGP 9.4.0, Kotlin 2.3.21)
├── settings.gradle.kts                      <-- Repository & project settings
├── local.properties                         <-- Local secrets (MAPS_API_KEY, GOOGLE_WEB_CLIENT_ID)
├── firestore.rules                          <-- Firestore security rules (Role-based RBAC)
├── database.rules.json                      <-- Realtime Database rules (Driver write / Student read)
├── functions/                               <-- Cloud Functions v2 (TypeScript)
│   └── src/index.ts                         <-- Proximity calculations & FCM push dispatcher
└── app/
    ├── build.gradle.kts                     <-- Compose BOM, Hilt, Room, Firebase, Maps
    └── src/main/
        ├── AndroidManifest.xml              <-- Permissions (Location, Foreground, Notifications)
        └── java/com/unishuttle/app/
            ├── MainActivity.kt              <-- Single-activity host with UniShuttleNavGraph
            ├── UniShuttleApplication.kt     <-- Hilt root & FCM notification channel setup
            ├── data/
            │   ├── local/                   <-- Room database, DAOs, entities
            │   ├── remote/                  <-- FCM messaging service, Directions API DTOs
            │   └── repository/              <-- Firebase & Room repository implementations
            ├── di/                          <-- Hilt modules (Auth, Firebase, Network, Database)
            ├── domain/
            │   ├── model/                   <-- AppUser, UserRole, ShuttleLocation, Stop, SeatAvailability
            │   └── repository/              <-- Clean architecture repository interfaces
            ├── driver/
            │   └── DriverLocationService.kt <-- Foreground GPS streaming service (every 5s)
            └── presentation/
                ├── navigation/
                │   ├── NavRoute.kt          <-- Route definitions (Login, StudentMap, Driver, Admin)
                │   └── UniShuttleNavGraph.kt<-- Auth-gated root navigation graph
                ├── ui/
                │   ├── component/           <-- SeatAvailabilityMeter, AlertTimePicker
                │   ├── screen/
                │   │   ├── auth/            <-- LoginScreen, RegisterScreen
                │   │   ├── student/         <-- StudentMapScreen
                │   │   ├── driver/          <-- DriverDashboardScreen, DriverSeatCounterScreen
                │   │   ├── admin/           <-- AdminPanelScreen
                │   │   ├── map/             <-- StopDetailBottomSheet, MapMarkerIcons
                │   │   └── notifications/   <-- NotificationCenterScreen
                │   └── theme/               <-- Color.kt, Theme.kt, Type.kt
                └── viewmodel/               <-- Auth, StudentMap, DriverDashboard, NotificationCenter
```

---

## 9. Prompt Template for Claude

When starting a conversation with Claude to design UIs or implement system features, paste the following prompt:

```markdown
Hello Claude! I am developing UniShuttle, a modern native Android campus shuttle tracking application built with Jetpack Compose, Material 3, Kotlin Coroutines, and Firebase.

I have attached the complete system specification in `UNISHUTTLE_CONTEXT_AND_SYSTEM_DESIGN.md`.

Please review the architecture, schemas, and design guidelines outlined in this document. I want you to act as our Principal Android UI/UX Architect and System Designer. 

Let's begin by:
1. Reviewing the current UI deficiencies in `presentation/ui/screen/`
2. Proposing a cohesive Material 3 Design System (Color tokens, Elevation, Shapes, and Typography in `Theme.kt` and `Color.kt`)
3. Creating state-of-the-art Jetpack Compose implementations for [choose screen: StudentMapScreen / StopDetailBottomSheet / DriverDashboardScreen / LoginScreen]
```
