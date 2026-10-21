# Role-Based Auth + Navigation + Driver Privacy

## What was built

### 1. Login System (3 roles)

Three distinct user roles: `STUDENT`, `DRIVER`, `ADMIN`.

| Screen | File |
|--------|------|
| Login | `presentation/ui/screen/auth/LoginScreen.kt` |
| Register | `presentation/ui/screen/auth/RegisterScreen.kt` |
| ViewModel | `presentation/viewmodel/AuthViewModel.kt` |

**Registration flow:**
1. User fills name / email / password, picks Student or Driver.
2. Firebase Auth creates the account.
3. `FirebaseAuthRepository` writes to Firestore `users/{uid}`:
   ```json
   { "email": "...", "displayName": "...", "role": "DRIVER", "shuttleId": "shuttle-abc123" }
   ```

### 2. Navigation Routing

`UniShuttleNavGraph` is the single root graph. It:
- Shows a spinner while Firebase resolves auth state (prevents flicker).
- Routes to the correct home screen based on role.
- Handles logout by clearing the back-stack and sending to Login.

```
NavGraph
├── login           → LoginScreen
├── register        → RegisterScreen
├── student_map     → StudentMapScreen    (STUDENT role)
├── driver_dashboard→ DriverDashboardScreen (DRIVER role)
├── admin_panel     → AdminPanelScreen    (ADMIN role)
└── notification_center → NotificationCenterScreen (shared)
```

### 3. Driver Privacy + Duty Toggle

`DriverDashboardScreen` has a **"Start Duty / End Duty"** switch:

- **On Duty** → starts `DriverLocationService` foreground service which writes GPS coordinates to Firebase RTDB `shuttles/{shuttleId}` every 5 seconds.
- **Off Duty** → stops the service. **No coordinates are ever written when off-duty.** Location is completely private.

`StudentMapViewModel` also filters out any driver location entry older than 5 minutes as a secondary guard.

### 4. Student Map — Live Driver Tracking

`StudentMapScreen` observes `ShuttleLocationRepository` (backed by Firebase RTDB) and renders a live shuttle marker for each active (on-duty) driver.

---

## Firebase Console Checklist

### Authentication
- [ ] Enable **Email/Password** sign-in provider in Firebase Console → Authentication → Sign-in method.

### Firestore
- [ ] Create Firestore database in **production mode**.
- [ ] Deploy security rules from `firestore.rules`:
  ```bash
  firebase deploy --only firestore:rules
  ```

### Realtime Database
- [ ] Create RTDB instance.
- [ ] Deploy rules from `database.rules.json`:
  ```bash
  firebase deploy --only database
  ```

### Creating an Admin account
Admin accounts are not self-registerable (the Register screen only shows Student/Driver).
Create an admin manually:
1. Register a normal account via the app.
2. Go to Firebase Console → Firestore → `users/{uid}`.
3. Edit the `role` field from `"STUDENT"` to `"ADMIN"`.
4. Sign out and sign back in → you'll be routed to AdminPanelScreen.

---

## New Files

```text
domain/model/UserRole.kt
domain/model/AppUser.kt
domain/repository/AuthRepository.kt
data/repository/FirebaseAuthRepository.kt
di/AuthModule.kt
di/RepositoryModule.kt (modified — AuthRepository binding added)
presentation/viewmodel/AuthViewModel.kt
presentation/viewmodel/StudentMapViewModel.kt
presentation/viewmodel/DriverDashboardViewModel.kt
presentation/ui/screen/auth/LoginScreen.kt
presentation/ui/screen/auth/RegisterScreen.kt
presentation/ui/screen/student/StudentMapScreen.kt
presentation/ui/screen/driver/DriverDashboardScreen.kt
presentation/ui/screen/admin/AdminPanelScreen.kt
presentation/navigation/NavRoute.kt (modified — roles added)
presentation/navigation/UniShuttleNavGraph.kt
presentation/ui/screen/map/MapMarkerIcons.kt (modified — object added)
MainActivity.kt (modified — uses UniShuttleNavGraph)
firestore.rules
storage.rules
database.rules.json
```
