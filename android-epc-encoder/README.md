# EPC Encoder (Chainway C66)

One-off internal tool: import a CSV of EAN/EPC pairs, then write each EPC onto
a physical RFID tag via the C66's UHF module, verify the write, and register
the EPC against the matching product in the catalog (`POST /api/products/{id}/epc`)
so the tag resolves correctly on the first SOH scan.

## Setup

1. Open this folder in Android Studio (it will prompt to create the Gradle
   wrapper jar automatically — `gradle/wrapper/gradle-wrapper.properties` is
   already configured for Gradle 8.9).
2. `app/build.gradle.kts` → `API_BASE_URL` defaults to `http://10.0.2.2:8080/`
   (emulator alias for host's `localhost:8080`, i.e. the `nginx-gateway`
   container). On a real C66 device, change this to the actual gateway
   host/IP reachable from the device's network before building.
3. The Chainway SDK (`DeviceAPI_ver20220518_release.aar`) is already copied
   into `app/libs/` from `android-c66/app/libs/` — same SDK, same device
   family.

## CSV format expected

Header row required, case-insensitive, at minimum: `Sr.No, EPC, EAN-13, dept,
style, description, color, size` (extra columns like `barcode`/`item barcode`
are ignored). Export your Excel as CSV before loading (File > Save As > CSV).

## Flow

1. **Login** — needs a user with `ADMIN` or `STORE_MANAGER` role (required by
   `POST /api/products/{id}/epc`).
2. **Import** — pick the CSV. Invalid rows (bad EPC format, blank EAN,
   duplicate EPC) are listed and skipped; valid rows load into an in-memory
   session that checkpoints to a JSON file (`filesDir/epc_session.json`), so
   killing/reopening the app resumes where you left off.
3. **Write** — shows the next pending row. Place one tag in range, press
   WRITE (or the C66's physical trigger — see keycode note below). The app:
   - reads the tag to confirm exactly one is in range
   - writes the target EPC to the EPC memory bank
   - reads back to verify
   - looks up the product via `GET /api/products/by-sku/{EAN}` (SKU holds the
     EAN value for this product batch — confirmed against prod DB, see
     project memory `erp_integration.md`)
   - calls `POST /api/products/{id}/epc` to register the EPC
   - auto-advances to the next pending row
   - Skip Row / retry-on-failure supported; Export Results produces a CSV
     with per-row status for your records.

## Things to verify on-device before relying on this

- **Write API call/params** (`ChainwayEpcWriter.kt`): `writeData(accessPwd,
  bank, wordPtr, wordCount, data)` uses the standard RSCJA convention
  (bank=1/EPC, access password `00000000`, word ptr=2). This matches common
  Chainway sample code for this SDK version but hasn't been run against real
  hardware from here — if writes fail with an SDK error code, check the
  actual `RFIDWithUHFUART` method signature in the aar/Chainway docs.
- **Trigger keycode** (`WriteActivity.kt`, `TRIGGER_KEYCODES`): guessed common
  values (F1–F4, 139, 293). Confirm via `adb logcat` while pressing the
  physical trigger and add the real keyCode if the guessed ones don't fire.
- **`by-sku/{ean}` lookup**: confirmed against production DB that `sku` holds
  the EAN value for the batch this was built for — re-verify if used against
  a different product batch where SKU might be a different convention.
