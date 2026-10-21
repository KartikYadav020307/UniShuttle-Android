# Google Sign-In Setup Guide (Credential Manager)

## 1. Get your Web Client ID

1. Open [Firebase Console](https://console.firebase.google.com) → your project → Authentication → Sign-in method.
2. Enable **Google** as a sign-in provider and click **Save**.
3. Under the Google provider row, click the **pencil icon** → copy the **Web SDK configuration → Web client ID**.

> This is the `clientId` ending in `.apps.googleusercontent.com`.

## 2. Add it to `local.properties`

`local.properties` is git-ignored and never committed. Add one line:

```
GOOGLE_WEB_CLIENT_ID=659345078360-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

Gradle injects this into `BuildConfig.GOOGLE_WEB_CLIENT_ID` at build time via the `buildConfigField` in `app/build.gradle.kts`. The `AuthViewModel` reads it as:

```kotlin
.setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
```

## 3. Add your SHA-1 fingerprint to Firebase

Credential Manager requires the app's SHA-1 to be registered, otherwise sign-in fails silently.

```bash
# Debug keystore (for development)
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android -keypass android
```

Copy the `SHA1:` line → Firebase Console → Project Settings → Your apps → Android app → Add fingerprint.

## 4. How the flow works

```
LoginScreen
  User picks Student or Driver chip
  Taps "Sign in with Google"
      │
  AuthViewModel.signInWithGoogle(context, role)
      │
  CredentialManager shows the system Google account picker
      │
  User selects an account
      │
  ID token extracted from GetCredentialResponse
      │
  FirebaseAuthRepository.signInWithGoogle(idToken, role)
      ├── auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken))
      ├── STUDENT role? → check email.endsWith("@vitstudent.ac.in")
      │       NO  → auth.signOut() + throw DomainRestrictionException
      │       YES → continue
      ├── DRIVER role → any email accepted
      └── setDoc(users/{uid}, { uid, email, role, shuttleId }, merge=true)
          │
  currentUser Flow emits AppUser
      │
  UniShuttleNavGraph routes to StudentMapScreen or DriverDashboardScreen
```

## 5. Files changed

| File | Change |
|------|--------|
| `app/build.gradle.kts` | +3 Credential Manager deps, +`buildConfigField` for `GOOGLE_WEB_CLIENT_ID` |
| `local.defaults.properties` | Added `GOOGLE_WEB_CLIENT_ID` placeholder |
| `domain/repository/AuthRepository.kt` | Replaced `login`/`register` with `signInWithGoogle(idToken, role)` |
| `data/repository/FirebaseAuthRepository.kt` | Implements Google Sign-In + domain check + Firestore merge |
| `presentation/viewmodel/AuthViewModel.kt` | Credential Manager orchestration, `DomainRestrictionException` handling |
| `presentation/ui/screen/auth/LoginScreen.kt` | Removed TextFields, added role chips + Google button |
| `presentation/ui/screen/auth/RegisterScreen.kt` | Replaced with informational stub |
| `presentation/navigation/UniShuttleNavGraph.kt` | Removed stale `onNavigateToRegister` param |
