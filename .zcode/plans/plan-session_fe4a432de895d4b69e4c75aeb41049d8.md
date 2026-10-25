# Step 1 — Google sign-in, role routing, sign-out: implementation plan

## STEP 0 INVENTORY (read-only, verified)

- **IDs**: `applicationId = "com.unishuttle.app"` (debug suffix `.debug`), `namespace = "com.unishuttle.app"`, `MainActivity` in package `com.unishuttle.app` (`@AndroidEntryPoint`, hosts `UniShuttleNavGraph` as the single root).
- **DI**: Hilt 2.60.1 (KSP). Modules in `di/`: `AuthModule` (FirebaseAuth), `FirebaseModule` (Firestore/RTDB/Messaging), `RepositoryModule` (`@Binds` → `FirebaseAuthRepository`), plus Database/Location/Network.
- **Version catalog**: none (`gradle/libs.versions.toml` absent) → dependencies go directly in `app/build.gradle.kts`.
- **Existing auth**: substantially built already — `AuthViewModel` runs the full Credential Manager flow (`GetGoogleIdOption` + `BuildConfig.GOOGLE_WEB_CLIENT_ID`, cancellation handled), `FirebaseAuthRepository` does `signInWithCredential` + domain check + users-doc write, `UniShuttleNavGraph` gates start-destination by role with a loading spinner.
- **google-services.json**: PRESENT (`app/google-services.json`, project `unishuttle-f4d2c`, both package names) → not stopping. `local.properties` has `GOOGLE_WEB_CLIENT_ID` and `MAPS_API_KEY` (values not inspected); already gitignored along with `google-services.json`.
- **firestore.rules current content (verbatim)**:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() { return request.auth != null; }
    function userRole() { return get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role; }
    function isDriver()  { return isSignedIn() && userRole() == 'DRIVER'; }
    function isAdmin()   { return isSignedIn() && userRole() == 'ADMIN'; }
    function isStudent() { return isSignedIn() && userRole() == 'STUDENT'; }
    function isOwner(uid) { return isSignedIn() && request.auth.uid == uid; }

    match /users/{uid} {
      allow create: if isSignedIn() && isOwner(uid);
      allow read:   if isOwner(uid) || isAdmin();
      allow update: if isOwner(uid) || isAdmin();
      allow delete: if isAdmin();
    }
    match /stops/{stopId} { allow read: if isSignedIn(); allow write: if isAdmin(); }
    match /seatAvailability/{shuttleId} { allow read: if isSignedIn(); allow write: if isDriver() || isAdmin(); }
  }
}
```
- Note: no `AGENTS.md`/`DESIGN_SYSTEM.md` in the repo; the external copy's "UI-only, don't touch ViewModels/routes" rule is superseded by this step's explicit instructions. Also note the working tree has my earlier uncommitted StopDetail-sheet changes (`EtaBadge.kt`, `StudentMapScreen.kt` modified; `StopDetailSheet.kt` untracked) — this step builds on top; I won't commit anything.

## GAP ANALYSIS — what the spec requires vs what exists

Already satisfied (no changes): all dependencies, `GOOGLE_WEB_CLIENT_ID` BuildConfig wiring, Credential Manager request flow, picker-cancel handling, loading-while-auth-resolves, Hilt wiring, route names kept (`login`, `student_map`, `driver_dashboard`, …).

Gaps this step closes:
1. **Role comes from Firestore, not the login toggle** — today the selected role is written unconditionally via a non-merge `set()` that clobbers existing docs (an admin/driver doc gets overwritten on re-login). Spec: read-first, create-if-missing (student only, `role:"student"`, `photoUrl`, `createdAt: serverTimestamp()`), otherwise use the stored role.
2. **No `email_verified` check** → add.
3. **Domain check is case-sensitive and has the wrong message** → case-insensitive + "Sign in with your campus account (@vitstudent.ac.in)." + `clearCredentialState` on failure.
4. **Driver path**: doc missing + Driver → sign out + "Driver accounts aren't set up yet."; Login button disabled for Driver + "Driver sign-in is coming soon."
5. **Firestore read failure currently fakes a STUDENT user** → must instead not route and show "Couldn't load your account. Check your connection and try again." with a retry action.
6. **No sign-out button on the student map header** (the `onLogout` param exists but is unused); no driver placeholder.
7. **No missing-config developer message** on login.
8. **Admin/unknown role** currently routes to AdminPanel → per spec: sign out + error on login.
9. **firestore.rules** lack role-value constraints on create/update.

## FILE CHANGES

**1. `data/repository/FirebaseAuthRepository.kt`** (rewrite of sign-in + state flow)
- Inject `@ApplicationContext`; own a `CredentialManager` for `clearCredentialState` (works with app context; the account picker itself stays in the ViewModel with Activity context).
- `signInWithGoogle(idToken, role)`: sign in → **case-insensitive** domain check (message above, sign out + clear credentials) → `isEmailVerified` check ("Your Google account email isn't verified.") → read `users/{uid}`: if exists, use **its** role; if missing and Student, create `{uid, email, displayName, photoUrl, role:"student" (lowercase, matches rules), createdAt: serverTimestamp()}` (never any other role); if missing and Driver → sign out + `Driver accounts aren't set up yet.`; read/write failure → typed error for the retry message.
- Replace `currentUser: Flow<AppUser?>` with `authState: Flow<AuthState>` (`SignedOut` / `SignedIn(AppUser)` / `ProfileUnavailable`) — removes the fake-STUDENT fallback; add `reloadProfile()` for retry.
- `logout()`: `auth.signOut()` + `clearCredentialState(ClearCredentialStateRequest())`. No auth prefs exist to clear; nothing else is touched (map cache untouched).
- Typed exceptions: keep `DomainRestrictionException`, add `EmailNotVerifiedException`, `DriverNotEnabledException`, `ProfileUnavailableException`.

**2. `domain/repository/AuthRepository.kt`** — interface updated to match (authState flow, reloadProfile, logout now clears Credential Manager).

**3. `presentation/viewmodel/AuthViewModel.kt`** — sealed `AuthUiState`: `Loading`, `SignedOut(error, isBusy, showRetry)`, `SignedIn(role)`. Hoist `selectedRole` (STUDENT default) + `selectRole()` into the VM. `retryLoadProfile()`. Guard: blank `BuildConfig.GOOGLE_WEB_CLIENT_ID` → dev message, flow never starts. Picker cancel → quiet return. Credential Manager picker stays here (needs Activity context).

**4. `presentation/ui/screen/auth/LoginScreen.kt`** — keep layout/styling. Changes only: role from VM; Google button disabled when Driver selected (with a subdued disabled look) + "Driver sign-in is coming soon." shown under the button; "Missing GOOGLE_WEB_CLIENT_ID in local.properties" banner when config is blank; retry button when `showRetry`; keep "Requires a @vitstudent.ac.in email" helper; light/dark previews updated.

**5. `presentation/navigation/UniShuttleNavGraph.kt`** — guard: `Loading` → plain full-screen theme-background view (no map flash); reactive `LaunchedEffect(state)`: SignedIn(STUDENT)→`student_map`, SignedIn(DRIVER)→`driver_dashboard`, any other role → sign out with "This account type isn't available yet.", SignedOut→`login`; every navigation `popUpTo(0) { inclusive }` + `launchSingleTop`. No deep links exist in the manifest/nav (verified), so guard-only enforcement holds. `driver_dashboard` destination now hosts the new placeholder. `register` and `notification_center` kept. Logout callbacks just call `authViewModel.logout()` — the guard does the redirect.

**6. `presentation/ui/screen/student/StudentMapScreen.kt`** — add a 48dp circular sign-out button immediately left of the bell, built exactly like the bell (2dp outline border, 3dp hard shadow, CircleShape) with a `Surface` fill and `Icons.AutoMirrored.Filled.Logout` (icons-extended already a dependency; no new library; bell untouched). `contentDescription = "Sign out"`. On press → `AlertDialog` ("Sign out?" / "You'll need to sign in with your campus account again." / "Cancel" | "Sign out") → `onLogout()`. Map, chips, card, sheet untouched.

**7. `presentation/ui/screen/driver/DriverHomeScreen.kt`** (NEW) — placeholder: "Driver home (coming soon)" title + primary `UniButton` "Sign out" with the same dialog path. Theme tokens only; light + dark previews. Legacy `DriverDashboardScreen`/`DriverSeatCounterScreen` files stay untouched (simply unreachable).

**8. `firestore.rules`** (update in place, no second file; NOT deployed) — `users/{uid}`: `create` requires `isOwner(uid) && request.resource.data.role == "student"`; `update` requires role unchanged (`request.resource.data.role == resource.data.role`) for owner **and** admin; keep `read: isOwner || isAdmin` and `delete: isAdmin`. **Kept rules + reasons**: `stops` read (map/stop list reads them), `seatAvailability` read + driver/admin write (driver seat updates), admin read/update/delete on users (legacy AdminPanel + manual ops), all helper functions (used by kept rules). Add explicit deny-all `match /{document=**}`. Role casing: new client writes lowercase `"student"`; `UserRole.fromString` is case-insensitive so existing uppercase docs still parse; kept rules compare uppercase for driver/admin which only ever come from manual console assignment (per docs) — consistent.

**9. No dependency changes** — spec-listed artifacts already present: firebase-bom 34.18.0 (auth, firestore, no -ktx), credentials 1.5.0, credentials-play-services-auth 1.5.0, googleid 1.1.1, navigation-compose 2.10.1, lifecycle-viewmodel-compose 2.11.0. I'll report these as "already satisfied".

**10. `local.properties.example` does not exist** → per spec I will tell you, not create: the key to have in `local.properties` is `GOOGLE_WEB_CLIENT_ID=<Web client ID from Firebase Console → Authentication → Google provider>` (already present on this machine; `local.defaults.properties` already carries the placeholder).

## TODO comments to add (out-of-scope items)
- Server-side domain blocking via Firebase blocking function (needs Blaze) — at the domain check in `FirebaseAuthRepository`.
- Driver sign-in + driver-only checks (Step 3) — in `LoginScreen` disable branch and repo driver branch.
- Admin role setup — in the NavGraph admin handling.

## Testing
I can run: `compileDebugKotlin`, `assembleDebug`, install + launch on your connected POCO, and verify by code: config guard, domain/verified checks, role-source, rules consistency, deep-link absence. I cannot exercise: real Google account picker, a live `@vitstudent.ac.in` sign-in, non-campus rejection, Firestore-failure retry, and role-g routing — those need your manual passes (a–h); SHA-1 must be registered in Firebase per `docs/google-sign-in-setup.md`.

Not starting Step 2. No commits; secrets stay in `local.properties`/`google-services.json` (both gitignored).