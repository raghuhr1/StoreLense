# Zebra Android App — Development Guide

**Project:** StoreLense — RFID Store Operations Platform
**App:** `com.storelense.zebra`
**Version:** 1.0
**Date:** 2026-06-06

---

## 1. Overview

The StoreLense Android app runs on Zebra TC-series handheld scanners (TC21, TC26, TC57) and supports Zebra RFD40/RFD90 Bluetooth RFID sleds. It is built with Kotlin + Jetpack Compose, uses the Zebra EMDK SDK for RFID hardware access, and is offline-first using Room + WorkManager.

---

## 2. Prerequisites

### Development Machine

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Hedgehog 2023.1.1+ | |
| JDK | 17 | Set in `JAVA_HOME`; Android Studio bundles one |
| Kotlin | 1.9+ | Defined in `libs.versions.toml` |
| Gradle | 8.x | Wrapper included in repo |
| Android SDK | API 35 (compile), 29 (min) | Install via SDK Manager |

### Zebra EMDK SDK

The EMDK JAR is **not included in the repo** (Zebra licensing). Download from [developer.zebra.com](https://developer.zebra.com):

1. Log in to Zebra Developer Portal
2. Download: **EMDK for Android** → extract `com.symbol.emdk.jar`
3. Place the JAR at: `android/app/libs/com.symbol.emdk.jar`

> Without the JAR, the project still compiles — EMDK is declared `compileOnly`, and `MockRfidReader` is used automatically in debug builds.

---

## 3. Project Setup

### Clone and Open

```bash
git clone https://github.com/raghuhr1/StoreLense.git
cd StoreLense/android
```

Open `android/` as a project in Android Studio (File → Open → select `android/` directory).

### local.properties

Create `android/local.properties` (gitignored):

```properties
sdk.dir=/Users/you/Library/Android/sdk   # or your SDK path

# API base URL for debug builds on physical Zebra device
# (device and server must be on the same Wi-Fi network)
storelense.debug.url=http://192.168.1.100:8080/

# API base URL for release APK
storelense.release.url=https://api.storelense.internal/
```

> **Emulator:** The default fallback URL is `http://10.0.2.2:8080/` which maps to `localhost:8080` on the host machine — no `local.properties` needed for emulator development.

### Build Configuration

`build.gradle.kts` injects `BASE_URL` and `USE_MOCK_RFID` into `BuildConfig` at compile time:

| Build Type | `USE_MOCK_RFID` | `BASE_URL` source |
|---|---|---|
| `debug` | `true` | `local.properties:storelense.debug.url` or `10.0.2.2:8080` |
| `release` | `false` | `local.properties:storelense.release.url` |

---

## 4. Running the App

### On Android Emulator (Development)

1. Start the StoreLense backend: `docker compose -f deploy/docker-compose.yml up -d` (on your dev machine)
2. Run the app from Android Studio → AVD with API 29+
3. `MockRfidReader` is used automatically — no RFID hardware needed
4. Login: `admin` / `Admin@StoreLense1` (or seeded user credentials)

### On Physical Zebra Device (Integration)

1. Enable USB debugging: Settings → Developer Options → USB Debugging
2. Connect device via USB
3. Set `storelense.debug.url` in `local.properties` to the server IP on your Wi-Fi
4. Run from Android Studio → select the Zebra device
5. In debug build, `MockRfidReader` is still used — to test real EMDK, switch `USE_MOCK_RFID` to `false` in `buildTypes.debug`

### Enabling Real RFID in Debug Build

Edit `app/build.gradle.kts`:
```kotlin
buildTypes {
    debug {
        buildConfigField("Boolean", "USE_MOCK_RFID", "false")  // ← change to false
        ...
    }
}
```

The `RfidModule` (Hilt) selects the implementation:
```kotlin
@Provides
fun provideRfidReader(context: Context): RfidReader =
    if (BuildConfig.USE_MOCK_RFID) MockRfidReader()
    else EmDkRfidReader(context)
```

---

## 5. Project Structure

```
android/
├── app/
│   ├── build.gradle.kts              — dependencies, build config
│   ├── libs/                         — place com.symbol.emdk.jar here
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/storelense/zebra/
│           ├── MainActivity.kt       — Hilt entry point, splash screen
│           ├── StoreLenseApp.kt      — @HiltAndroidApp, WorkManager init
│           │
│           ├── domain/               — pure Kotlin, no Android deps
│           │   ├── model/Models.kt   — User, SohSession, RefillTask, RfidRead, ...
│           │   └── repository/Repositories.kt  — interfaces
│           │
│           ├── data/
│           │   ├── local/
│           │   │   ├── AppDatabase.kt
│           │   │   ├── dao/          — SohSessionDao, RfidReadDao, RefillTaskDao
│           │   │   └── entity/       — Room @Entity classes
│           │   ├── remote/
│           │   │   ├── ApiService.kt         — Retrofit interface
│           │   │   ├── AuthInterceptor.kt    — OkHttp interceptor (JWT + auto-refresh)
│           │   │   ├── TokenManager.kt       — encrypted token storage
│           │   │   └── dto/                  — JSON DTOs (Moshi)
│           │   └── repository/       — *RepositoryImpl.kt (Room + API)
│           │
│           ├── rfid/
│           │   ├── RfidReader.kt     — interface + EpcRead data class
│           │   ├── EmDkRfidReader.kt — Zebra EMDK implementation
│           │   └── MockRfidReader.kt — simulated reader (5–12 EPC/sec)
│           │
│           ├── di/                   — Hilt modules
│           │   ├── NetworkModule.kt
│           │   ├── DatabaseModule.kt
│           │   ├── RepositoryModule.kt
│           │   └── RfidModule.kt
│           │
│           ├── ui/
│           │   ├── login/            — LoginScreen + LoginViewModel
│           │   ├── dashboard/        — DashboardScreen + DashboardViewModel
│           │   ├── soh/              — SohListScreen, SohScanScreen, SohViewModel
│           │   ├── refill/           — RefillListScreen, RefillDetailScreen, RefillViewModel
│           │   ├── navigation/       — AppNavigation.kt (NavHost)
│           │   └── theme/            — Theme.kt (Material Design 3)
│           │
│           └── work/
│               └── RfidSyncWorker.kt — RfidSyncWorker, RefillSyncWorker
│
├── gradle/
│   └── libs.versions.toml            — version catalog
├── build.gradle.kts                  — root build file
├── settings.gradle.kts
├── local.properties                  — gitignored; set sdk.dir, URLs here
└── SETUP.md
```

---

## 6. Key Dependencies

From `libs.versions.toml`:

| Library | Purpose |
|---|---|
| `hilt-android` | Dependency injection |
| `room-runtime` + `room-ktx` | Local SQLite database (Room) |
| `retrofit` + `retrofit-moshi` | REST API client |
| `okhttp` + `okhttp-logging` | HTTP client with JWT interceptor |
| `moshi-kotlin` + `moshi-codegen` | JSON serialization |
| `workmanager-ktx` + `hilt-work` | Background sync (Hilt-integrated WorkManager) |
| `lifecycle-viewmodel-compose` | ViewModel + Compose integration |
| `navigation-compose` | Screen navigation |
| `security-crypto` | Encrypted SharedPreferences for token storage |
| `datastore-prefs` | Preferences (non-sensitive config) |
| `timber` | Structured logging |
| `compose-bom` | Compose Bill of Materials — locks all Compose library versions |
| `compose-material3` | Material Design 3 UI components |
| `splash-screen` | Android 12+ splash screen API |

---

## 7. Authentication Implementation

### Token Storage

`TokenManager` uses `EncryptedSharedPreferences` (Android Keystore-backed):

```kotlin
// Keys stored:
"access_token"  — JWT access token
"refresh_token" — JWT refresh token
"user_id"       — UUID
"username"      — login name
"role"          — ADMIN, STORE_MANAGER, STORE_ASSOCIATE, REFILL_ASSOCIATE
"store_id"      — UUID (null for ADMIN)
```

### Auto-Refresh Flow

`AuthInterceptor` wraps OkHttp to silently refresh expired tokens:

```
Request →
  Inject Authorization: Bearer {accessToken}
    ↓
Response received
  If HTTP 401 AND not already retrying:
    Call POST /api/auth/refresh
    If success → update tokens in TokenManager → retry original request
    If failure → call onTokenExpired() → navigate to login
  Else → return response as-is
```

The interceptor uses `synchronized` to prevent multiple concurrent refresh calls.

---

## 8. RFID Implementation

### RfidReader Interface

```kotlin
interface RfidReader {
    val isConnected: Boolean
    val reads: Flow<EpcRead>        // emits each EPC as it arrives
    val readCount: Flow<Int>        // total reads (including duplicates)
    
    suspend fun connect()
    suspend fun disconnect()
    fun startScan()
    fun stopScan()
    fun setTxPower(dbm: Int)
}
```

### EmDkRfidReader (Production)

1. `connect()` → calls `EMDKManager.getEMDKManager(context, this)` → triggers `onOpened()` callback
2. `onOpened()` → gets `RFIDManager` → gets first `RFIDReader` → calls `reader.connect()` → registers `RfidEventsListener`
3. `startScan()` → calls `reader.Actions.Inventory.perform()` (Gen2 continuous inventory)
4. `eventReadNotify()` → calls `getReadTags(100)` → emits each `TagData` as `EpcRead` to Channel
5. `stopScan()` → calls `reader.Actions.Inventory.stop()`
6. `disconnect()` → removes listener → `reader.disconnect()` → `emdkManager.release()`

The `reads` Flow is a `callbackFlow` that bridges the EMDK callback thread to Kotlin coroutines.

### MockRfidReader (Development/Testing)

Emits random EPCs from a pool of 200 simulated SGTIN tags at 5–12 reads/second using a `CoroutineScope` + `delay()` loop. Useful for:
- Development without Zebra hardware
- UI testing with predictable scan behaviour
- CI pipeline automated tests

### TX Power Configuration

```kotlin
rfidReader.setTxPower(27)  // 27 dBm — typical for floor scanning
rfidReader.setTxPower(15)  // 15 dBm — close-range spot check
```

---

## 9. Offline-First Design

### Room Database Schema

| Table | Purpose | Key columns |
|---|---|---|
| `soh_sessions` | Cached SOH sessions from API | `id`, `storeId`, `status`, `startedAt` |
| `rfid_reads` | Buffered RFID reads pending upload | `sessionId`, `epc`, `uploaded` (bool) |
| `refill_tasks` | Cached task list from API | `id`, `storeId`, `status`, `cachedAt` |
| `refill_task_items` | Task line items with optimistic update | `id`, `taskId`, `pendingFulfil` |

### Deduplication at Rest

`rfid_reads` has a composite unique index on `(sessionId, epc)`. `RfidReadDao.insertRead` uses `OnConflictStrategy.IGNORE` — duplicate EPCs for the same session are silently dropped at the database level, no application logic needed.

### Sync Strategy

| Data | Strategy | Worker |
|---|---|---|
| RFID reads | Upload pending batch on session complete; retry via `RfidSyncWorker` if offline | `RfidSyncWorker` (one-time, triggered on failure) |
| Refill tasks | Pull from API every 15 minutes when connected | `RefillSyncWorker` (periodic) |
| SOH sessions | Pull on screen open (pull-to-refresh) and on dashboard load | None (manual pull) |

`WorkManager` constraints: `NetworkType.CONNECTED`. Workers use `BackoffPolicy.EXPONENTIAL` with 15-second base delay and maximum 4 retries.

### Optimistic Updates (Refill Fulfilment)

When an associate fulfils a task item offline:
1. `RefillTaskDao.setPendingFulfil(itemId, quantity)` — stores quantity locally
2. UI shows the fulfilled quantity immediately from `pendingFulfil`
3. On reconnect, `RefillRepository.fulfilItem()` sends to API
4. On success: `confirmFulfil()` clears `pendingFulfil`, updates `fulfilledQuantity`
5. On failure: `setPendingFulfil(itemId, -1)` rolls back (UI reverts)

---

## 10. Background Sync Setup

`StoreLenseApp.kt` enqueues the periodic refill sync at app startup:

```kotlin
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "refill_sync",
    ExistingPeriodicWorkPolicy.KEEP,
    RfidSyncWorker.periodicRefillSync()
)
```

`ExistingPeriodicWorkPolicy.KEEP` — if already scheduled, don't reschedule. The worker runs every 15 minutes when the device is online.

Hilt-WorkManager integration requires:
- `@HiltAndroidApp` on Application class
- Manual `WorkManager` configuration (default initialiser removed from Manifest)
- Workers annotated `@HiltWorker` with `@AssistedInject` constructor

---

## 11. Navigation

`AppNavigation.kt` defines the `NavHost` with 6 routes:

| Route | Screen | Auth Required |
|---|---|---|
| `login` | `LoginScreen` | No |
| `dashboard` | `DashboardScreen` | Yes |
| `soh_list` | `SohListScreen` | Yes |
| `soh_scan/{sessionId}` | `SohScanScreen` | Yes |
| `refill_list` | `RefillListScreen` | Yes |
| `refill_detail/{taskId}` | `RefillDetailScreen` | Yes |

On app start, `MainActivity` checks `TokenManager.isLoggedIn()` and navigates directly to `dashboard` if already authenticated (skips login).

`SavedStateHandle` is used to pass route arguments to ViewModels:
```kotlin
@HiltViewModel
class SohViewModel @Inject constructor(
    savedState: SavedStateHandle,
    ...
) : ViewModel() {
    private val sessionId: String? = savedState["sessionId"]
}
```

---

## 12. Network Configuration

### Base URL Resolution

The base URL is injected at build time via `BuildConfig.BASE_URL`. Never hardcode URLs in source files.

| Scenario | Value |
|---|---|
| Emulator | `http://10.0.2.2:8080/` (host machine's localhost) |
| Physical device on same LAN | `http://192.168.X.X:8080/` (set in `local.properties`) |
| Production | `https://api.storelense.internal/` |

### Plain HTTP in Debug

`res/xml/network_security_config.xml` allows cleartext HTTP to `10.0.2.2` (emulator) and `192.168.0.0/16` (LAN). For production, HTTPS only — remove these exceptions.

### Logging

OkHttp `HttpLoggingInterceptor` logs request/response bodies in `DEBUG` builds. Remove or set to `HEADERS` for release builds.

---

## 13. Adding a New Screen

1. **Create ViewModel** in `ui/<feature>/<Feature>ViewModel.kt` — annotate `@HiltViewModel`, inject repository
2. **Create Screen Composable** in `ui/<feature>/<Feature>Screen.kt` — accept `viewModel: FeatureViewModel = hiltViewModel()`
3. **Add route** in `AppNavigation.kt`:
   ```kotlin
   composable("feature_screen") {
       FeatureScreen(navController = navController)
   }
   ```
4. **Add navigation** from other screens: `navController.navigate("feature_screen")`
5. **Add API endpoint** to `ApiService.kt` if needed
6. **Add repository method** to the relevant `Repositories.kt` interface and `*RepositoryImpl.kt`

---

## 14. Adding a New API Endpoint

1. Add the Retrofit function to `ApiService.kt`
2. Add the DTO data class to `data/remote/dto/` (annotate with `@JsonClass(generateAdapter = true)`)
3. Add the interface method to `domain/repository/Repositories.kt`
4. Implement in `data/repository/*RepositoryImpl.kt` using `safeApiCall { api.newEndpoint(...) }`
5. Inject the repository into your ViewModel via Hilt (already bound in `RepositoryModule.kt`)

---

## 15. Modifying RFID Behaviour

### Change Scan Power

In `SohViewModel.initScanScreen()`:
```kotlin
rfidReader.setTxPower(27)   // call before startScan()
rfidReader.startScan()
```

### Change Mock Scan Rate

In `MockRfidReader.startScan()`:
```kotlin
delay(Random.nextLong(80, 200))   // 80–200ms → 5–12 reads/sec
// Change to:
delay(Random.nextLong(50, 100))   // 100–200 reads/sec (stress test)
```

### Change MockRfidReader EPC Pool

Replace `epcPool` in `MockRfidReader` with EPCs from your test environment:
```kotlin
private val epcPool = listOf(
    "3034257BF400B71400000064",
    "3034257BF400B71400000065",
    // ...
)
```

---

## 16. Building the Release APK

### APK Build

```bash
cd android
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

Requires signing config. Add to `app/build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file(localProps["KEY_STORE_FILE"] as String)
        storePassword = localProps["KEY_STORE_PASS"] as String
        keyAlias = localProps["KEY_ALIAS"] as String
        keyPassword = localProps["KEY_PASS"] as String
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        ...
    }
}
```

Add to `local.properties`:
```properties
KEY_STORE_FILE=../keystore/storelense.jks
KEY_STORE_PASS=your_store_password
KEY_ALIAS=storelense
KEY_PASS=your_key_password
```

### Sideload to Zebra Device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 17. Deploying via Zebra MDM (Staging/Production)

Use **Zebra StageNow** or **SOTI MobiControl** / **VMware Workspace ONE**:

1. Build signed APK
2. Upload to MDM server
3. Create deployment policy targeting device groups (TC21, TC26, TC57 fleet)
4. Deploy silently with `INSTALL_EXISTING_APKS` or package name-based replacement
5. Configure `BASE_URL` via MDM app config (if using Managed Config / OEMConfig)

For `storelense.release.url`, set it in `local.properties` before the release build, or use a flavour per environment (staging, production).

---

## 18. Logging

`Timber` is used throughout. In `StoreLenseApp.kt`:
```kotlin
if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
// In release, plant a crashlytics or file-based tree
```

Key log tags to monitor during development:
- `[Mock]` — MockRfidReader events
- `EMDK` — EmDkRfidReader lifecycle
- `RfidSyncWorker` — sync upload results
- `AuthInterceptor` — token refresh events

---

## 19. Common Development Issues

| Issue | Cause | Fix |
|---|---|---|
| `BUILD FAILED: com.symbol.emdk.jar not found` | JAR not in `app/libs/` | Download from developer.zebra.com and place in `app/libs/` |
| `Unable to connect to 10.0.2.2:8080` | Backend not running | Start Docker Compose on host machine |
| `Unable to connect to 192.168.X.X:8080` | Wrong IP or firewall | Verify server IP; check `local.properties`; check firewall allows port 8080 |
| Login returns 401 on device | Wrong BASE_URL (hitting wrong server) | Print `BuildConfig.BASE_URL` in logcat to verify |
| `WorkManager not initialised` | Hilt WorkManager setup missing | Ensure `HiltWorkerFactory` is set in `StoreLenseApp` |
| `Room IllegalStateException: Migration not provided` | DB schema changed without migration | Increment `version` in `@Database` and provide `Migration` object, OR clear app data in dev |
| RFID scan starts but no reads | EMDK not available (non-Zebra device in release build) | Use debug build (`USE_MOCK_RFID=true`) on non-Zebra device |
