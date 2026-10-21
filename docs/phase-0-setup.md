# UniShuttle Phase 0 Setup

## Folder Tree

```text
UniShuttle Android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.defaults.properties
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/unishuttle/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── UniShuttleApplication.kt
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── dao/
│       │   │   │   │   ├── database/
│       │   │   │   │   └── entity/
│       │   │   │   ├── remote/
│       │   │   │   │   ├── api/
│       │   │   │   │   └── dto/
│       │   │   │   ├── repository/
│       │   │   │   └── mapper/
│       │   │   ├── di/
│       │   │   ├── domain/
│       │   │   │   ├── model/
│       │   │   │   ├── repository/
│       │   │   │   └── usecase/
│       │   │   └── presentation/
│       │   │       ├── navigation/
│       │   │       ├── ui/
│       │   │       │   ├── component/
│       │   │       │   ├── screen/
│       │   │       │   └── theme/
│       │   │       └── viewmodel/
│       │   └── res/values/
│       ├── test/
│       └── androidTest/
```

## Project-Level build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}
```

## App-Level build.gradle.kts

See `app/build.gradle.kts` for the exact dependency set.

## Manual Console Checklist

1. Create a Firebase project named `UniShuttle`.
2. Add an Android app with package name `com.unishuttle.app`.
3. Add a second Android app for debug builds with package name `com.unishuttle.app.debug`.
4. Generate `google-services.json` for each app and place the correct file at `app/google-services.json` before syncing Gradle.
5. In Firebase Authentication, enable the sign-in providers planned for UniShuttle.
6. In Firebase Realtime Database, create the database in locked mode first; add development rules only when the auth model is ready.
7. In Cloud Firestore, create the database in production mode; choose the region closest to campus/users.
8. In Cloud Messaging, keep the generated sender ID handy for notification testing.
9. In Analytics, confirm Google Analytics is enabled for the Firebase project.
10. In Crashlytics, finish setup after the first successful app run by sending a test crash in a later phase.
11. In Google Cloud Console, enable Maps SDK for Android for the Firebase-linked Google Cloud project.
12. Create or select an Android-restricted API key for Maps.
13. Restrict the Maps API key to Android apps only, using package names plus SHA-1 fingerprints.
14. Restrict the Maps API key API scope to Maps SDK for Android unless another Google Maps API is explicitly needed.
15. Add `MAPS_API_KEY=your_real_key_here` to local `local.properties`; do not commit that file.
16. Get the debug SHA-1 with `./gradlew signingReport` or Android Studio's Gradle signing report task.
17. Add SHA-1 fingerprints for both `com.unishuttle.app` and `com.unishuttle.app.debug` in Firebase project settings.
18. Repeat SHA-1 setup later for release and Play App Signing certificates.
19. Keep Firebase security rules locked until repositories and auth claims are designed.
20. Verify Android Studio uses JDK 17 or newer before syncing.
