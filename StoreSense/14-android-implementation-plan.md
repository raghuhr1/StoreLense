# Document 14 — Android Implementation Plan & Setup Guide

**Project:** StoreLense Mobile (android-soh)  
**Version:** 1.0  
**Status:** Ready for Development

---

## 1. Overview

The `android-soh` project is a fully-functional Android app for Zebra RFID handheld devices. It covers three store workflows:

| Workflow | Description |
|---|---|
| **SOH Count** | Scan entire store floor, compare against server-expected items, upload EPC count |
| **Inbound Receive** | Scan incoming DC shipment, verify against shipment manifest, mark received |
| **Replenishment** | View tasks assigned by server, manually move stockroom stock to sales floor |

---

## 2. Prerequisites

| Requirement | Version / Notes |
|---|---|
| Android Studio | Ladybug (2024.2.1) or newer |
| Android SDK | API 26 minimum, API 34 target |
| JDK | 17 (bundled with Android Studio) |
| Kotlin | 1.9.25 |
| Gradle | 8.7 |
| EMDK JAR | Required for physical Zebra device (not for emulator) |

---

## 3. Project Structure

```
android-soh/
├── app/
│   ├── build.gradle.kts          ← Build config, BASE_URL, USE_MOCK_RFID
│   ├── proguard-rules.pro        ← ProGuard rules for release APK
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/storelense/mobile/
│       │   ├── StoreLenseApp.kt          ← Application class (Hilt + WorkManager)
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── entity/Entities.kt   ← Room entities
│       │   │   │   ├── dao/Daos.kt          ← Room DAOs
│       │   │   │   └── AppDatabase.kt       ← Room database
│       │   │   ├── remote/
│       │   │   │   ├── dto/Dtos.kt          ← API request/response types
│       │   │   │   ├── ApiService.kt        ← Retrofit interface
│       │   │   │   ├── TokenManager.kt      ← EncryptedSharedPreferences
│       │   │   │   └── AuthInterceptor.kt   ← OkHttp auto-refresh
│       │   │   └── repository/
│       │   │       ├── AuthRepository.kt
│       │   │       ├── SohRepository.kt
│       │   │       ├── InboundRepository.kt
│       │   │       └── ReplenishRepository.kt
│       │   ├── di/
│       │   │   ├── AppModule.kt       ← Retrofit, OkHttp, TokenManager
│       │   │   ├── DatabaseModule.kt  ← Room DB, DAOs
│       │   │   └── RfidModule.kt      ← RfidReader (Mock vs EMDK)
│       │   ├── rfid/
│       │   │   ├── RfidReader.kt         ← Interface
│       │   │   ├── RfidRead.kt           ← Data class
│       │   │   ├── MockRfidReader.kt     ← Emulator/debug reader
│       │   │   └── EmDkRfidReader.kt     ← Zebra EMDK (stubs until JAR placed)
│       │   ├── ui/
│       │   │   ├── navigation/AppNavigation.kt
│       │   │   ├── login/
│       │   │   ├── home/
│       │   │   ├── soh/
│       │   │   ├── inbound/
│       │   │   └── replenish/
│       │   └── work/
│       │       ├── SohUploadWorker.kt
│       │       ├── InboundUploadWorker.kt
│       │       └── RefillSyncWorker.kt
│       └── res/
│           ├── values/strings.xml
│           └── xml/network_security_config.xml
├── gradle/
│   └── libs.versions.toml        ← Version catalog
├── build.gradle.kts
├── settings.gradle.kts
└── local.properties              ← Created by developer (not committed)
```

---

## 4. Opening in Android Studio

1. Open Android Studio.
2. Select **File → Open** and navigate to `d:\StoreLense\android-soh\`.
3. Click **OK** — Android Studio will detect the Gradle project.
4. Wait for Gradle sync to complete (first sync downloads ~500 MB of dependencies).
5. If prompted to upgrade AGP, **decline** — use the version specified in `libs.versions.toml`.

---

## 5. Configure `local.properties`

This file is never committed to git. Create it at the project root (`android-soh/local.properties`):

```properties
# Android SDK location (set automatically by Android Studio)
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# StoreLense backend URL — use your server's IP or hostname
# For local Docker deployment:
BASE_URL=http://192.168.1.100:80/
# For cloud deployment:
# BASE_URL=https://storelense.example.com/
```

> **Important:** The URL must end with a trailing slash. Do not use `localhost` — Android emulators use `10.0.2.2` to reach the host machine.

For emulator testing:
```properties
BASE_URL=http://10.0.2.2:80/
```

---

## 6. EMDK JAR Setup (Physical Zebra Device Only)

EMDK is a Zebra-proprietary library not available on Maven Central. The app compiles without it (all EMDK calls are commented with `//EMDK:` prefix), but real RFID scanning requires it.

**Steps to enable real EMDK:**

1. Download the EMDK for Android SDK from Zebra's developer portal.
2. Extract and locate `com.symbol.emdk.jar` (typically inside the SDK's `libs/` folder).
3. Copy the JAR to `android-soh/app/libs/com.symbol.emdk.jar`.
4. In `app/build.gradle.kts`, uncomment this line (line ~52):
   ```kotlin
   // EMDK: compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
   ```
   Change to:
   ```kotlin
   compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
   ```
5. In `EmDkRfidReader.kt`, uncomment all lines prefixed with `//EMDK:`.
6. In `local.properties`, add:
   ```properties
   USE_MOCK_RFID_DEBUG=false
   ```
   Or build the `release` variant directly (EMDK is enabled in release by default).
7. Re-sync Gradle and rebuild.

---

## 7. Build Variants

| Variant | `USE_MOCK_RFID` | RFID Source | Use For |
|---|---|---|---|
| `debug` | `true` | MockRfidReader | Emulator testing, UI development |
| `release` | `false` | EmDkRfidReader | Physical Zebra device deployment |

Select variant in Android Studio: **Build → Select Build Variant → debug / release**

---

## 8. Running on Android Emulator

1. Create an AVD: **Tools → Device Manager → Create Device**
   - Phone: Pixel 6
   - System Image: API 34 (x86_64)
   - RAM: 2048 MB minimum
2. Select `debug` build variant.
3. Click **Run** (▶) — the app launches with `MockRfidReader`.
4. MockRfidReader generates 200 random EPCs at 80–200 ms intervals.
5. The backend must be reachable at the URL in `local.properties`. If running Docker locally, use `10.0.2.2:80`.

---

## 9. Running on Physical Zebra Device

### Device Setup
1. Enable Developer Options: **Settings → About Phone → tap Build Number 7 times**
2. Enable USB Debugging: **Settings → Developer Options → USB Debugging**
3. Connect via USB and accept the RSA fingerprint prompt on the device.
4. Confirm device is detected: `adb devices` should list the device.

### EMDK Prerequisites
1. EMDK Service must be installed on the Zebra device (pre-installed on most Zebra Android devices).
2. Verify: **Settings → Apps → EMDK Service** must be present.

### Deploy
1. Select `release` build variant (or `debug` with EMDK JAR in place).
2. Click **Run** (▶) — Android Studio will install the APK directly.
3. First launch: grant EMDK permission when prompted.

---

## 10. Generating a Release APK

For MDM deployment or side-loading:

1. **Build → Generate Signed Bundle / APK → APK**
2. Create or select a keystore. Store the keystore file and password securely.
3. Select `release` variant.
4. APK is output to `app/release/app-release.apk`.

To distribute via MDM (SOTI MobiControl, VMware Workspace ONE):
- Upload the APK to the MDM console.
- Target Zebra device group.
- Set `android:required="false"` for `com.symbol.emdk` is already configured in the Manifest — devices without EMDK Service will install but RFID scanning is unavailable.

---

## 11. Adding a New API Endpoint

1. Add the DTO to `data/remote/dto/Dtos.kt`.
2. Add the method to `ApiService.kt` with Retrofit annotations.
3. Add the use-case method to the relevant Repository (`SohRepository`, `InboundRepository`, or `ReplenishRepository`).
4. Call from the ViewModel; the ViewModel updates the UI state.

---

## 12. Background Sync

Three WorkManager workers run automatically:

| Worker | Trigger | Network | Retries |
|---|---|---|---|
| `SohUploadWorker` | On session complete | Required | 3 × EXPONENTIAL 15s |
| `InboundUploadWorker` | On scan complete | Required | 3 × EXPONENTIAL 15s |
| `RefillSyncWorker` | Every 15 minutes | Required | None (periodic) |

WorkManager is initialized via `StoreLenseApp` (Hilt `Configuration.Provider` pattern) to support `@HiltWorker` injection.

---

## 13. Common Issues

| Issue | Cause | Fix |
|---|---|---|
| Gradle sync fails with "SDK not found" | `sdk.dir` wrong in `local.properties` | Let Android Studio auto-set by opening the file with Android Studio |
| `BASE_URL` connection refused on emulator | Using `localhost` or `127.0.0.1` | Use `10.0.2.2` for host machine |
| RFID not scanning on Zebra device | EMDK JAR not in `libs/`, EMDK code still commented | Follow Section 6 above |
| `EncryptedSharedPreferences` crash on first install | Android Keystore not initialized | Delete app data and reinstall |
| WorkManager not running | Battery optimization blocking background work | Add app to battery optimization whitelist on device |
| Hilt compile error: "cannot find symbol" | Missing `@HiltAndroidApp` or KSP version mismatch | Verify `ksp` version matches Kotlin version in `libs.versions.toml` |

---

## 14. Dependencies Summary

All versions are centrally managed in `gradle/libs.versions.toml`.

| Library | Version | Purpose |
|---|---|---|
| Compose BOM | 2024.06.00 | Jetpack Compose UI |
| Hilt | 2.51.1 | Dependency injection |
| Room | 2.6.1 | Local SQLite database |
| Retrofit | 2.11.0 | REST API client |
| Gson | 2.11.0 | JSON serialization |
| WorkManager | 2.9.0 | Background tasks |
| OkHttp Logging | 4.12.0 | HTTP request logging (debug) |
| Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| Timber | 5.0.1 | Logging |
| Navigation Compose | 2.7.7 | Screen navigation |
| Lifecycle ViewModel | 2.8.6 | MVVM state management |
| EMDK | Zebra proprietary | RFID hardware (not on Maven) |
