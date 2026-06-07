# Zebra Android App — Design Document

**Project:** StoreLense — RFID Store Operations Platform
**Component:** Zebra Handheld Android Application (`com.storelense.zebra`)
**Version:** 1.0
**Date:** 2026-06-06

---

## 1. Overview

The StoreLense Android app runs on Zebra TC-series handheld computers (TC21, TC26, TC57) and is the primary field tool for store associates performing:

- **SOH Counting** — RFID-scan inventory (Stock-on-Hand)
- **Refill Management** — view and fulfil replenishment tasks

The app is offline-first: all RFID reads are buffered to a local Room database and uploaded when connectivity is restored. It communicates with the StoreLense backend via the nginx API Gateway (`http://server:8080/api`).

---

## 2. Architecture

### 2.1 Architectural Pattern — MVVM + Clean

The app follows MVVM with a Clean Architecture layering:

```
┌──────────────────────────────────────────────────────────┐
│                      UI Layer                            │
│   Compose Screens  ←→  ViewModels  (StateFlow, Events)  │
└────────────────────────┬─────────────────────────────────┘
                         │ collects / calls
┌────────────────────────▼─────────────────────────────────┐
│                   Domain Layer                           │
│   Repository Interfaces  |  Domain Models                │
│   (pure Kotlin, no Android deps)                        │
└────────────────────────┬─────────────────────────────────┘
                         │ implements
┌────────────────────────▼─────────────────────────────────┐
│                    Data Layer                            │
│   RepositoryImpl  ←→  Room DB  |  Retrofit API           │
│   TokenManager    ←→  EncryptedSharedPreferences         │
│   RfidModule      ←→  EmDkRfidReader | MockRfidReader    │
└──────────────────────────────────────────────────────────┘
```

**Key constraint:** The domain layer has zero Android framework dependencies. ViewModels hold `StateFlow`, not Compose State — Compose screens observe via `collectAsStateWithLifecycle()`.

### 2.2 Dependency Injection — Hilt

All wiring is Hilt-managed with `@Singleton` scope for shared resources:

```
@HiltAndroidApp StoreLenseApp
│
├── NetworkModule   → OkHttpClient, Retrofit, ApiService
├── DatabaseModule  → AppDatabase, all DAOs
├── RepositoryModule → *RepositoryImpl bound to interfaces
└── RfidModule      → RfidReader (Mock or EmDk)
```

Workers use `@HiltWorker` + `@AssistedInject`; Hilt `WorkerFactory` replaces the default WorkManager factory.

---

## 3. RFID Abstraction Design

### 3.1 RfidReader Interface

```
┌─────────────────────────────────────────────┐
│              «interface» RfidReader          │
│                                             │
│  + isConnected: Boolean                     │
│  + reads: Flow<EpcRead>                     │
│  + readCount: Flow<Int>                     │
│                                             │
│  + connect(): suspend                       │
│  + disconnect(): suspend                    │
│  + startScan()                              │
│  + stopScan()                               │
│  + setTxPower(dbm: Int)                     │
└─────────┬───────────────────┬───────────────┘
          │                   │
          ▼                   ▼
  EmDkRfidReader        MockRfidReader
  (Zebra EMDK)          (Coroutine simulator)
```

**Selection at compile time** via `BuildConfig.USE_MOCK_RFID`:
- `debug` → `true` → `MockRfidReader` (no hardware dependency)
- `release` → `false` → `EmDkRfidReader`

The `RfidModule` Hilt provider selects the implementation — the rest of the app is unaware of which is active.

### 3.2 EpcRead Data Class

```kotlin
data class EpcRead(
    val epc: String,         // SGTIN-96 hex string
    val rssi: Double?,       // signal strength dBm (null in mock)
    val antennaPort: Int?,   // antenna port 1–4 (null in mock)
    val readAt: String       // ISO-8601 timestamp
)
```

### 3.3 EmDkRfidReader — EMDK Integration

EMDK is a Zebra system-level library, present only on Zebra devices. It is declared `compileOnly` in Gradle — it is on the device classpath at runtime but must not be bundled in the APK.

```
EmDkRfidReader implements:
  • RfidReader
  • EMDKManager.EMDKListener
  • RfidEventsListener
```

**Lifecycle:**

```
connect()
  └→ EMDKManager.getEMDKManager(context, this)
       └→ callback: onOpened(emdkManager)
            ├── emdkManager.getInstance(RFID) → RFIDManager
            ├── rfidManager.getDevice(RFID1, this)  → RFIDReader
            ├── rfidReader.connect()
            └── rfidReader.Events.addEventsListener(this)

startScan()
  └→ rfidReader.Actions.Inventory.perform()

[hardware fires tags]
  └→ eventReadNotify(e: RfidReadEvents)
       └→ rfidReader.Actions.Tag.getReadTags(100) → List<TagData>
            └→ _readChannel.send(EpcRead(...))   [Channel → Flow]

stopScan()
  └→ rfidReader.Actions.Inventory.stop()

disconnect()
  └→ rfidReader.Events.removeEventsListener()
  └→ rfidReader.disconnect()
  └→ emdkManager.release()
```

**callbackFlow bridge** — EMDK callbacks arrive on a hardware thread. `callbackFlow` wraps the channel, allowing coroutines downstream to collect on any dispatcher:

```kotlin
val reads: Flow<EpcRead> = _readChannel.receiveAsFlow()
```

### 3.4 MockRfidReader — Simulator Design

```
startScan()
  └→ coroutineScope.launch {
       while (scanning) {
         val epc = epcPool[Random.nextInt(epcPool.size)]
         _reads.emit(EpcRead(epc, null, null, now()))
         _readCount.update { it + 1 }
         delay(Random.nextLong(80, 200))   // 5–12 reads/sec
       }
     }
```

Pool: 200 pre-defined SGTIN EPCs covering a mix of apparel categories. Controlled randomness makes UI testing deterministic when pool is seeded.

---

## 4. State Management

### 4.1 ViewModel → Compose Flow

```kotlin
// ViewModel
private val _uiState = MutableStateFlow(SohUiState.Loading)
val uiState: StateFlow<SohUiState> = _uiState.asStateFlow()

// Composable
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

`collectAsStateWithLifecycle()` (from `lifecycle-runtime-compose`) automatically cancels collection when the Composable is not in the STARTED state — correct behaviour when the app goes to background.

### 4.2 UI State Sealed Classes

Each screen has a sealed `UiState` class:

```
SohUiState
├── Loading
├── Empty (no sessions)
├── Success(sessions: List<SohSession>)
└── Error(message: String)

ScanUiState
├── Initialising (connecting RFID reader)
├── Scanning(count: Int, lastEpc: String?)
├── Paused(count: Int)
├── Uploading
├── Complete(uploadedCount: Int)
└── Error(message: String, retryable: Boolean)
```

### 4.3 One-Shot Events (Side Effects)

Navigation events and toasts are `Channel`-based one-shot events (not StateFlow, which replays the latest value on resubscription):

```kotlin
private val _events = Channel<ScanEvent>(Channel.BUFFERED)
val events: Flow<ScanEvent> = _events.receiveAsFlow()
```

`LaunchedEffect(Unit) { viewModel.events.collect { ... } }` in Composable.

---

## 5. Data Layer Design

### 5.1 Room Database Schema

**AppDatabase** — version 1, 4 entities

#### `soh_sessions`
```sql
CREATE TABLE soh_sessions (
    id          TEXT PRIMARY KEY,
    store_id    TEXT NOT NULL,
    zone_id     TEXT,
    status      TEXT NOT NULL,   -- OPEN, COMPLETED
    started_at  TEXT NOT NULL,
    ended_at    TEXT,
    cached_at   INTEGER NOT NULL
);
```

#### `rfid_reads`
```sql
CREATE TABLE rfid_reads (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT NOT NULL,
    epc         TEXT NOT NULL,
    rssi        REAL,
    antenna     INTEGER,
    read_at     TEXT NOT NULL,
    uploaded    INTEGER NOT NULL DEFAULT 0,

    UNIQUE (session_id, epc)    -- dedup: same EPC in same session is one read
);
```

#### `refill_tasks`
```sql
CREATE TABLE refill_tasks (
    id           TEXT PRIMARY KEY,
    store_id     TEXT NOT NULL,
    status       TEXT NOT NULL,   -- PENDING, IN_PROGRESS, COMPLETED
    created_at   TEXT NOT NULL,
    due_date     TEXT,
    cached_at    INTEGER NOT NULL
);
```

#### `refill_task_items`
```sql
CREATE TABLE refill_task_items (
    id                TEXT PRIMARY KEY,
    task_id           TEXT NOT NULL,
    product_id        TEXT NOT NULL,
    product_name      TEXT NOT NULL,
    sku               TEXT,
    required_quantity INTEGER NOT NULL,
    fulfilled_quantity INTEGER NOT NULL DEFAULT 0,
    pending_fulfil    INTEGER DEFAULT -1,   -- -1 = no pending change
    FOREIGN KEY (task_id) REFERENCES refill_tasks(id)
);
```

### 5.2 EPC Deduplication Strategy

Deduplication operates at two levels:

| Level | Mechanism | Scope |
|---|---|---|
| In-memory (during scan) | `MutableSet<String>` in `RfidRepository.bufferRead()` | Current scan session, within a single app run |
| At rest (Room) | `UNIQUE(session_id, epc)` + `OnConflictStrategy.IGNORE` | Across restarts; prevents duplicate uploads |

The in-memory set is cleared when `stopScan()` is called. Re-reads of the same EPC during a session are ignored with no log — expected behaviour (Gen2 inventory reads a tag ~3×/sec).

### 5.3 Repository Pattern

```kotlin
// Domain (interface)
interface SohRepository {
    suspend fun getSessions(storeId: String): Result<List<SohSession>>
    suspend fun createSession(storeId: String, zoneId: String?): Result<SohSession>
    suspend fun completeSession(sessionId: String): Result<SohSession>
}

// Data (implementation)
class SohRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val dao: SohSessionDao
) : SohRepository {

    override suspend fun getSessions(storeId: String): Result<List<SohSession>> =
        safeApiCall {
            val sessions = api.getSessions(storeId)
            dao.replaceAll(sessions.map { it.toEntity() })  // cache
            sessions.map { it.toDomain() }
        }
}
```

`safeApiCall` is a shared helper that wraps API calls in `try/catch`, maps network exceptions to typed `Result.Failure`.

---

## 6. Authentication — Mobile Flow

### 6.1 Token Storage

`TokenManager` uses `EncryptedSharedPreferences` backed by Android Keystore:

| Key | Value |
|---|---|
| `access_token` | JWT access token (15-min expiry) |
| `refresh_token` | JWT refresh token (7-day expiry) |
| `user_id` | UUID |
| `username` | Login username |
| `role` | `ADMIN`, `STORE_MANAGER`, `STORE_ASSOCIATE`, `REFILL_ASSOCIATE` |
| `store_id` | UUID (null for ADMIN) |

Tokens survive app restarts and device reboots — store associates stay logged in until token expires or they explicitly log out.

### 6.2 Login Flow

```
LoginScreen → LoginViewModel.login(username, password)
  └→ AuthRepository.login(username, password)
       └→ POST /api/auth/login
            └→ 200 OK { accessToken, refreshToken, user }
                 └→ TokenManager.saveTokens(...)
                      └→ emit LoginSuccess event
                           └→ navController.navigate("dashboard") + clear back stack
```

### 6.3 Auto-Refresh Flow (AuthInterceptor)

```
Any API request
  └→ AuthInterceptor.intercept(chain)
       ├── Add: Authorization: Bearer {accessToken}
       └→ Execute request
            ├── 200–499 (not 401) → return response
            └── 401 →
                 ├── If already retrying → emit tokenExpired → navigate login
                 └── Else:
                      └→ POST /api/auth/refresh (synchronized block — one refresh at a time)
                           ├── Success → TokenManager.update() → retry original request
                           └── Failure → emit tokenExpired → navigate login
```

The `synchronized` guard prevents a thundering-herd of concurrent 401s each triggering a refresh. The first call wins; others wait and then use the new token.

---

## 7. SOH Scan — Full Flow

### 7.1 Sequence Diagram

```
Associate         SohScanScreen      SohViewModel      RfidReader      RfidRepository      ApiService
    │                  │                  │                 │                 │                 │
    │  Tap "Start"     │                  │                 │                 │                 │
    │─────────────────>│                  │                 │                 │                 │
    │                  │  startScan()     │                 │                 │                 │
    │                  │─────────────────>│                 │                 │                 │
    │                  │                  │  connect()      │                 │                 │
    │                  │                  │────────────────>│                 │                 │
    │                  │                  │  isConnected=T  │                 │                 │
    │                  │                  │<────────────────│                 │                 │
    │                  │                  │  startScan()    │                 │                 │
    │                  │                  │────────────────>│                 │                 │
    │                  │                  │                 │                 │                 │
    │                  │   [EpcRead emitted every 80–200ms] │                 │                 │
    │                  │                  │  reads: Flow<>  │                 │                 │
    │                  │                  │<────────────────│                 │                 │
    │                  │                  │  bufferRead(epc)│                 │                 │
    │                  │                  │─────────────────────────────────>│                 │
    │                  │                  │                 │   INSERT OR IGNORE rfid_reads     │
    │                  │                  │                 │                 │                 │
    │  Tap "Stop"      │                  │                 │                 │                 │
    │─────────────────>│                  │                 │                 │                 │
    │                  │  stopScan()      │                 │                 │                 │
    │                  │─────────────────>│                 │                 │                 │
    │                  │                  │  stopScan()     │                 │                 │
    │                  │                  │────────────────>│                 │                 │
    │                  │                  │                 │                 │                 │
    │                  │                  │ completeSession()                 │                 │
    │                  │                  │────────────────────────────────────────────────────>│
    │                  │                  │                 │                 │                 │ POST /soh/sessions/{id}/complete
    │                  │                  │                 │                 │ uploadPending()  │
    │                  │                  │────────────────────────────────────────────────────>│
    │                  │                  │                 │                 │                 │ POST /rfid/ingest/batch
    │                  │                  │ Complete event  │                 │                 │
    │                  │<─────────────────│                 │                 │                 │
    │  "Upload done"   │                  │                 │                 │                 │
```

### 7.2 Offline Upload — RfidSyncWorker

If the batch upload fails (network error), `SohViewModel` enqueues a one-time `RfidSyncWorker`:

```
WorkManager.enqueueUniqueWork(
    "rfid_upload_{sessionId}",
    ExistingWorkPolicy.REPLACE,
    RfidSyncWorker.buildRequest(sessionId)
)

Worker retries:
  Attempt 1: immediate
  Attempt 2: +15 sec
  Attempt 3: +30 sec
  Attempt 4: +60 sec (exponential backoff)
  After 4 failures: WorkInfo.State.FAILED (user must manually retry)
```

On success, worker marks all `rfid_reads.uploaded = 1` for that session.

---

## 8. Refill Management — Design

### 8.1 Task List Flow

```
RefillListScreen loads → RefillViewModel.loadTasks()
  └→ RefillRepository.getTasks(storeId)
       ├── Emit cached tasks from Room immediately (optimistic load)
       └→ GET /api/refill/tasks?storeId={id}
            ├── Success → Room.replaceAll() → emit fresh list
            └── Failure → emit cached list (offline mode) + show "Offline" banner
```

### 8.2 Fulfilment — Optimistic Update

```
Associate enters fulfilled quantity → Confirm button
  └→ RefillViewModel.fulfilItem(taskId, itemId, quantity)
       │
       ├── 1. dao.setPendingFulfil(itemId, quantity)   [immediate UI update]
       │
       └→ 2. api.fulfilItem(taskId, itemId, quantity)
               ├── Success → dao.confirmFulfil(itemId, quantity)
               │             [clears pendingFulfil, sets fulfilledQuantity]
               └── Failure → dao.setPendingFulfil(itemId, -1)   [rollback]
                             [UI reverts to previous value + error toast]
```

Room `@Transaction` ensures `setPendingFulfil` and `confirmFulfil` are atomic.

---

## 9. Navigation Graph

```
                        [Start]
                           │
                    ┌──────▼──────┐
                    │  LoginScreen │  ← no auth required
                    └──────┬──────┘
                           │ LoginSuccess event
                    ┌──────▼──────┐
                    │  Dashboard  │ ← popUpTo("login") inclusive
                    └──┬──────┬──┘
                       │      │
            ┌──────────▼──┐  ┌▼────────────┐
            │  SohList    │  │ RefillList   │
            └──────┬──────┘  └──────┬───────┘
                   │                │
         ┌─────────▼────────┐  ┌────▼──────────────┐
         │ SohScan          │  │ RefillDetail       │
         │ {sessionId}      │  │ {taskId}           │
         └──────────────────┘  └────────────────────┘
```

**Deep link:** `storelense://soh/scan/{sessionId}` — supports push notification taps that jump directly to an in-progress session.

**Back stack policy:** Login is removed from back stack on successful login (`popUpTo("login") { inclusive = true }`). Hardware back from Dashboard shows exit confirmation dialog.

---

## 10. Background Sync Design

### 10.1 Worker Lifecycle

| Worker | Type | Trigger | Constraints |
|---|---|---|---|
| `RfidSyncWorker` | One-time | Session upload failure | `CONNECTED` network |
| `RefillSyncWorker` | Periodic (15 min) | App startup (enqueued once) | `CONNECTED` network |

### 10.2 Hilt-WorkManager Integration

Default WorkManager initialisation is disabled in `AndroidManifest.xml`:
```xml
<provider android:name="androidx.startup.InitializationProvider"
          tools:node="merge">
    <meta-data android:name="androidx.work.WorkManagerInitializer"
               tools:node="remove" />
</provider>
```

`StoreLenseApp` provides the `WorkManager` with Hilt's `WorkerFactory`:
```kotlin
override fun getWorkManagerConfiguration() = Configuration.Builder()
    .setWorkerFactory(workerFactory)
    .build()
```

This allows workers to inject `@Singleton` Hilt dependencies via `@AssistedInject`.

---

## 11. Security Design

### 11.1 Token Security

| Concern | Solution |
|---|---|
| Token at rest | `EncryptedSharedPreferences` (AES-256-GCM via Android Keystore) |
| Token in transit | HTTPS (prod); HTTP allowed on LAN (cleartext config) |
| Concurrent refresh race | `synchronized` block in `AuthInterceptor` |
| Expired token handling | Auto-refresh via interceptor; logout on refresh failure |
| Token on logout | `TokenManager.clear()` deletes all stored tokens |

### 11.2 Network Security Config

`res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <!-- Allow cleartext for local development (emulator + LAN) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
        <domain includeSubdomains="false">192.168.0.0</domain><!-- LAN range -->
    </domain-config>
    <!-- All other domains: HTTPS only -->
    <base-config cleartextTrafficPermitted="false"/>
</network-security-config>
```

**Production:** Remove the `<domain-config>` block entirely.

### 11.3 EMDK Permission

```xml
<uses-permission android:name="com.symbol.emdk.permission.EMDK" />
<uses-library android:name="com.symbol.emdk" android:required="false" />
```

`android:required="false"` allows the APK to install on non-Zebra devices — the EMDK library is simply absent; `MockRfidReader` is used instead (debug builds).

---

## 12. Build Variants

| Config | `debug` | `release` |
|---|---|---|
| `USE_MOCK_RFID` | `true` | `false` |
| `BASE_URL` | `local.properties:storelense.debug.url` (fallback: `http://10.0.2.2:8080/`) | `local.properties:storelense.release.url` |
| Minification (R8) | Off | On |
| Debug logging | On (Timber DebugTree) | Off (no tree planted) |
| APK signing | Debug key | Release keystore |

---

## 13. Component Map

```
┌────────────────────────────────────────────────────────────────────────┐
│                              Zebra Device                              │
│                                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                      UI Layer (Compose)                         │  │
│  │  LoginScreen  Dashboard  SohListScreen  SohScanScreen           │  │
│  │  RefillListScreen  RefillDetailScreen  AppNavigation            │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │ observes StateFlow                       │
│  ┌──────────────────────────▼──────────────────────────────────────┐  │
│  │                    ViewModel Layer                              │  │
│  │  LoginVM  DashboardVM  SohVM  RefillVM                         │  │
│  └──┬──────────────────────────────┬──────────────────────────────┘  │
│     │ calls                        │ collects Flow<EpcRead>           │
│  ┌──▼───────────────────────┐  ┌───▼───────────────┐                 │
│  │  Data Layer              │  │  RFID Layer        │                 │
│  │  Auth/Soh/Refill/Rfid   │  │  RfidReader        │                 │
│  │  RepositoryImpl          │  │  (Mock|EmDk)       │                 │
│  │   ├── ApiService         │  └───────────────────┘                 │
│  │   │   (Retrofit)         │                                         │
│  │   └── Room DAOs          │                                         │
│  │       (AppDatabase)      │                                         │
│  └──────────┬───────────────┘                                         │
│             │                                                          │
│  ┌──────────▼───────────────┐  ┌───────────────────┐                 │
│  │  WorkManager             │  │  TokenManager      │                 │
│  │  RfidSyncWorker          │  │  (EncryptedPrefs)  │                 │
│  │  RefillSyncWorker        │  └───────────────────┘                 │
│  └──────────────────────────┘                                         │
│                                                                        │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │ HTTP/HTTPS
                    ┌─────────────▼─────────────┐
                    │   nginx API Gateway        │
                    │   port 8080                │
                    └─────────────┬─────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
         auth-service        soh-service        refill-service
         rfid-ingest-service  ...                ...
```

---

## 14. Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| RFID abstraction | `RfidReader` interface with compile-time selection | Allows full UI development without Zebra hardware; CI runs with mock |
| Offline-first | Room buffer + WorkManager sync | Associates work in areas with poor Wi-Fi (stock rooms, fitting floors) |
| EPC dedup | DB unique constraint + `IGNORE` conflict | Zero-cost dedup with no application logic; survives process restarts |
| Token storage | `EncryptedSharedPreferences` | Android Keystore-backed; survives app reinstalls, protected from root extraction |
| Auth refresh | OkHttp interceptor (not ViewModel) | Transparent to all screens; single place for token management |
| Concurrent refresh | `synchronized` block | One thread refreshes; others wait and reuse the result |
| WorkManager + Hilt | `@HiltWorker` + custom factory | Workers get full DI graph without static references |
| Compose + StateFlow | `collectAsStateWithLifecycle` | Lifecycle-safe collection; no memory leaks when app backgrounds |
| Navigation | Compose NavHost (no Fragments) | Single-activity model; clean back stack management |
| EMDK | `compileOnly` + `uses-library required=false` | APK installs on non-Zebra; EMDK present at runtime on actual devices |
