# StoreLense Zebra Android App — Setup Guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Hedgehog+ | Ladybug recommended |
| JDK | 17 | Bundled with Android Studio |
| Android SDK | API 29–35 | Installed via SDK Manager |
| Zebra EMDK SDK | 9.x | Download from developer.zebra.com |

---

## 1. Clone and open

Open `d:\StoreLense\android` in Android Studio.

---

## 2. Zebra EMDK SDK (for RFID on real hardware)

1. Create a free account at [developer.zebra.com](https://developer.zebra.com)
2. Download **EMDK for Android** (latest stable, 9.x)
3. Copy `com.symbol.emdk.jar` into `app/libs/`
4. Android Studio will pick it up via `compileOnly(fileTree("libs"))`

> **On non-Zebra hardware (emulator / regular phone):**  
> The `debug` build uses `MockRfidReader` automatically — no EMDK needed.  
> Set `USE_MOCK_RFID = true` in `app/build.gradle.kts` for `release` builds on non-Zebra devices.

---

## 3. Backend URL configuration

| Build | `BASE_URL` | Notes |
|---|---|---|
| `debug`   | `http://10.0.2.2:8081/` | Android Emulator → localhost |
| `release` | `https://api.storelense.internal/` | Production API Gateway |

To point `debug` at a physical device's backend, edit `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://192.168.1.X:8081/\"")
```

The auth service must be running (`npm run dev` + auth-service JAR).

---

## 4. Run the app

```
# Emulator / USB device
./gradlew installDebug

# Or press ▶ in Android Studio
```

**Login credentials** (matches seeded backend):
- Username: `admin`
- Password: `Admin@StoreLense1`

---

## 5. Project structure

```
app/src/main/java/com/storelense/zebra/
├── data/
│   ├── local/          Room database (entities, DAOs, AppDatabase)
│   ├── remote/         Retrofit (ApiService, DTOs, AuthInterceptor, TokenManager)
│   └── repository/     Repository implementations (Auth, Soh, Rfid, Refill)
├── domain/
│   ├── model/          Domain models (User, SohSession, RefillTask, ...)
│   └── repository/     Repository interfaces
├── rfid/
│   ├── RfidReader.kt           Interface
│   ├── EmDkRfidReader.kt       Real EMDK implementation (needs emdk JAR)
│   └── MockRfidReader.kt       Simulator for dev/testing
├── work/
│   ├── RfidSyncWorker.kt       Upload RFID reads on reconnect
│   └── RefillSyncWorker.kt     Periodic refill task sync
├── di/                 Hilt modules (Network, Database, Repository, Rfid)
└── ui/
    ├── login/          Login screen + ViewModel
    ├── dashboard/      Dashboard (KPI + quick actions)
    ├── soh/            SOH session list + RFID scan screen
    ├── refill/         Refill task list + detail/fulfilment screen
    ├── navigation/     AppNavigation (Compose NavHost)
    └── theme/          Material 3 colours
```

---

## 6. RFID scan flow

```
SohScanScreen
  │  viewModel.startScan()
  ▼
SohViewModel → rfidReader.startScan()
  │
  │  EmDkRfidReader (real)           MockRfidReader (debug)
  │  EMDK streams TagData events      Emits random EPCs @ 8/sec
  ▼
rfidRepo.bufferRead()
  │  Room INSERT — deduplicated by (sessionId, epc)
  ▼
viewModel.completeSession()
  │  rfidRepo.uploadPendingReads() → POST /api/rfid/ingest/batch
  │  sohRepo.completeSession()     → POST /api/soh/sessions/{id}/complete
  ▼
ScanState.Completed → shows accuracy % and variance count
```

---

## 7. Offline behaviour

- RFID reads are **written to Room first** — network is never on the hot path.
- On `completeSession()`, reads are uploaded synchronously if online; otherwise queued to `WorkManager`.
- `RfidSyncWorker` retries with exponential backoff (15 s → 30 s → 60 s → 120 s).
- `RefillSyncWorker` pulls updated tasks every 15 minutes when online.
