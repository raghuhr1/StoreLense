# Document 15 — Android Test Cases

**Project:** StoreLense Mobile (android-soh)  
**Version:** 1.0  
**Status:** Ready for Testing

---

## Test Environment

| Parameter | Value |
|---|---|
| Target Device | Zebra TC53 / TC52 / TC57 (Android 11+) |
| Emulator | Pixel 6 API 34 (for non-RFID flows) |
| Backend | StoreLense Docker stack running locally or on staging server |
| Build Variant | `debug` for emulator, `release` for Zebra device |
| RFID Mode | MockRfidReader (emulator) / EmDkRfidReader (Zebra) |

---

## Authentication

### TC-MOB-AUTH-01 — Successful Login
**Precondition:** Backend is running; user exists in the system  
**Steps:**
1. Launch app
2. Enter valid username and password
3. Tap **Login**

**Expected:**
- Loading spinner shows during login
- Home screen appears with user's name and store name
- Token is persisted — app does not prompt login again after backgrounding

---

### TC-MOB-AUTH-02 — Invalid Credentials
**Precondition:** Backend is running  
**Steps:**
1. Launch app
2. Enter incorrect password
3. Tap **Login**

**Expected:**
- Error message displayed: "Invalid credentials" (or equivalent from server)
- User remains on login screen
- No navigation to home

---

### TC-MOB-AUTH-03 — Network Unavailable on Login
**Precondition:** Device has no network connectivity  
**Steps:**
1. Disable Wi-Fi and mobile data
2. Enter credentials
3. Tap **Login**

**Expected:**
- Error message displayed: "Network error. Check connection."
- App remains on login screen

---

### TC-MOB-AUTH-04 — Token Auto-Refresh on 401
**Precondition:** User is logged in; access token is about to expire  
**Steps:**
1. Simulate access token expiry (backend-side or modify token TTL to 1 minute)
2. Wait for token to expire
3. Perform any API action (e.g., navigate to SOH list)

**Expected:**
- App transparently refreshes the token using refresh token
- Requested action completes successfully — user sees no error or login prompt

---

### TC-MOB-AUTH-05 — Session Expired (Refresh Token Invalid)
**Precondition:** User is logged in; both tokens expired or invalidated server-side  
**Steps:**
1. Invalidate both tokens server-side
2. Perform any API action

**Expected:**
- App redirects to login screen
- No crash; clean logout

---

## SOH Count Workflow

### TC-MOB-SOH-01 — View SOH Session List
**Precondition:** Store has at least one SOH session in `New` or `InProgress` status  
**Steps:**
1. Log in
2. Tap **SOH Count** on home screen

**Expected:**
- List of SOH sessions shown with name, zone, status, and date
- Sessions in `InProgress` highlighted or marked

---

### TC-MOB-SOH-02 — Start Scanning a Session
**Precondition:** SOH session exists in `New` status  
**Steps:**
1. Open SOH session list
2. Tap a session
3. Tap **Start Scanning**

**Expected:**
- Scan screen opens
- RFID reader connects (status changes to "Scanning")
- EPC count starts incrementing as tags are read
- Last EPC (last 8 chars) shown on screen
- Matched count increments for EPCs in the expected set

---

### TC-MOB-SOH-03 — Pause and Resume Scanning
**Precondition:** Scan session is active  
**Steps:**
1. During active scan, tap **Pause**
2. RFID emissions stop
3. Tap **Resume**

**Expected:**
- Scanning pauses — counter stops incrementing
- On resume, scanning continues from where it left off
- Duplicate EPCs are not double-counted (dedup via scannedSet)

---

### TC-MOB-SOH-04 — EPC Deduplication
**Precondition:** RFID reader is active (MockRfidReader generates repeated EPCs)  
**Steps:**
1. Allow scan to run for 30+ seconds (MockRfidReader cycles through 200 EPCs)

**Expected:**
- Scanned count does not exceed the number of unique EPCs
- Same EPC scanned twice is counted only once
- Room DB shows each EPC stored once (UNIQUE constraint enforced)

---

### TC-MOB-SOH-05 — Complete Session and Upload
**Precondition:** At least 10 EPCs scanned  
**Steps:**
1. Tap **Complete & Upload**

**Expected:**
- Upload progress shown
- EPCs uploaded in chunks of 300 to `POST /api/rfid/ingest/batch`
- Session marked complete via `POST /api/soh/sessions/{id}/complete`
- Result screen shows: scanned count, matched count, unmatched count, surplus count

---

### TC-MOB-SOH-06 — Upload with No Network
**Precondition:** Scan is complete; device network is disabled  
**Steps:**
1. Disable Wi-Fi
2. Tap **Complete & Upload**

**Expected:**
- App shows "Offline — will upload when connected"
- WorkManager queues `SohUploadWorker`
- When network is restored, upload completes automatically in background

---

### TC-MOB-SOH-07 — Large Scan (500+ EPCs)
**Precondition:** MockRfidReader or large store with 500+ tags  
**Steps:**
1. Allow scan to run collecting 500+ unique EPCs
2. Complete and upload

**Expected:**
- EPCs chunked into batches of 300 (2 API calls for 500 EPCs)
- All batches succeed
- UI shows final count correctly

---

### TC-MOB-SOH-08 — SOH Result Screen Accuracy
**Precondition:** Known expected set from server (e.g., 100 items expected)  
**Steps:**
1. Complete scan with known results
2. Navigate to result screen

**Expected:**
- Scanned count = total unique EPCs read
- Matched = EPCs in both scanned and expected sets
- Unmatched = expected EPCs not scanned (shrinkage)
- Surplus = scanned EPCs not in expected set

---

## Inbound DC Receiving Workflow

### TC-MOB-INB-01 — View Inbound Shipments
**Precondition:** At least one shipment exists with status `Pending`  
**Steps:**
1. Log in
2. Tap **Inbound Receive** on home screen

**Expected:**
- List of pending shipments with supplier name, PO number, expected count, and ETA

---

### TC-MOB-INB-02 — Start Scanning a Shipment
**Precondition:** Shipment in `Pending` status selected  
**Steps:**
1. Tap a shipment
2. RFID scanner activates automatically

**Expected:**
- Scan screen opens showing shipment details
- RFID reader connects
- Received EPC count increments as cartons/items are scanned
- Expected count shown alongside received count

---

### TC-MOB-INB-03 — Scan Completion — Full Receipt
**Precondition:** Scanning ongoing; received count approaches expected count  
**Steps:**
1. Allow scan to capture expected number of EPCs
2. Tap **Mark Received**

**Expected:**
- `POST /api/inbound/shipments/{id}/receive` called with EPC list
- Result screen shows: received=N, expected=N, shortage=0
- Shipment status updated to `Received` on server

---

### TC-MOB-INB-04 — Scan Completion — Shortage
**Precondition:** Some expected EPCs are not present in shipment  
**Steps:**
1. Scan only a subset of cartons
2. Tap **Mark Received**

**Expected:**
- Result screen shows: received < expected, shortage = difference
- Server records the shortage against the shipment

---

### TC-MOB-INB-05 — Duplicate EPC in Shipment Scan
**Precondition:** RFID picks up same tag twice (common in dense packing)  
**Steps:**
1. Simulate duplicate EPC via MockRfidReader
2. Note received count

**Expected:**
- Received count does not increment for duplicate EPC
- Room DB `inbound_reads` UNIQUE constraint (shipmentId, epc) prevents duplicate insert

---

### TC-MOB-INB-06 — Offline Inbound Scan
**Precondition:** Shipment data loaded; network drops mid-scan  
**Steps:**
1. Open a shipment (data cached in Room)
2. Disable network
3. Continue scanning
4. Tap **Mark Received**

**Expected:**
- Scan continues offline (EPC data buffered in Room)
- Mark Received queues `InboundUploadWorker`
- When network restored, server receives the batch automatically

---

## Replenishment Workflow

### TC-MOB-REP-01 — View Replenishment Tasks
**Precondition:** Server has assigned refill tasks to the store  
**Steps:**
1. Log in
2. Tap **Replenish** on home screen

**Expected:**
- List of replenishment tasks shown with product name, zone, and required quantity
- Tasks sorted by priority or creation time

---

### TC-MOB-REP-02 — View Task Items
**Precondition:** Task has multiple line items  
**Steps:**
1. Tap a task

**Expected:**
- Task detail screen shows all items: product name, SKU, from/to zone, required qty, fulfilled qty
- Items not yet fulfilled show "Enter Qty Moved" button
- Items fully fulfilled shown with green background

---

### TC-MOB-REP-03 — Fulfil a Line Item
**Precondition:** Task is open; item has qty to fulfil  
**Steps:**
1. Open a task
2. Tap **Enter Qty Moved** on an item
3. Enter quantity in dialog
4. Tap **Confirm**

**Expected:**
- Fulfilled qty updates immediately on screen (optimistic update)
- `POST /api/refill/tasks/{taskId}/items/{itemId}/fulfil` called
- If API succeeds, update is confirmed
- Item turns green when fulfilledQty >= requiredQty

---

### TC-MOB-REP-04 — Fulfil Item — Rollback on API Failure
**Precondition:** Backend returns error on fulfil endpoint  
**Steps:**
1. Simulate API failure (take backend offline or mock error)
2. Enter qty for an item
3. Confirm

**Expected:**
- Optimistic update applied instantly
- Error snackbar appears: "Failed to update — reverting"
- Fulfilled qty reverts to previous value

---

### TC-MOB-REP-05 — Complete Task
**Precondition:** All items in a task are fulfilled (green)  
**Steps:**
1. Tap **Mark Task Complete**

**Expected:**
- `POST /api/refill/tasks/{id}/complete` called
- Result screen shown: "Task Complete! Stock has been moved to the sales floor."
- Navigation returns to home

---

### TC-MOB-REP-06 — Complete Task with Partial Fulfilment
**Precondition:** Some items not fully fulfilled  
**Steps:**
1. Fulfil only 2 of 4 items
2. Tap **Mark Task Complete**

**Expected:**
- Task completes (partial completion allowed by design)
- Server records partial fulfilment quantities
- Result screen shows task as complete

---

### TC-MOB-REP-07 — Background Task Sync
**Precondition:** New refill tasks assigned on server after app is open  
**Steps:**
1. Open app to replenishment list — note current tasks
2. Assign new task on server
3. Wait 15 minutes (or manually trigger via WorkManager)

**Expected:**
- `RefillSyncWorker` runs and refreshes tasks from server
- New task appears in list without manual refresh

---

## Navigation & General UI

### TC-MOB-NAV-01 — Home Screen Shows All Workflows
**Precondition:** User is logged in  
**Expected:**
- Home screen shows three workflow cards: SOH Count, Inbound Receive, Replenish
- User name and store name visible in header

---

### TC-MOB-NAV-02 — Back Navigation
**Precondition:** User has navigated into a workflow  
**Steps:**
1. Navigate to SOH Scan screen
2. Press back

**Expected:**
- Returns to SOH List (not home)
- RFID reader disconnects when leaving scan screen

---

### TC-MOB-NAV-03 — Deep Link Stability
**Precondition:** App is in background  
**Steps:**
1. Background the app mid-scan
2. Reopen the app

**Expected:**
- App restores to the scan screen
- Scan state (count, session ID) is preserved via ViewModel + SavedStateHandle

---

### TC-MOB-NAV-04 — Logout (If Implemented)
**Precondition:** User is logged in  
**Steps:**
1. From home, clear app data (or trigger token expiry)

**Expected:**
- App navigates to login screen
- Stored tokens cleared from EncryptedSharedPreferences

---

## Security

### TC-MOB-SEC-01 — Token Not in Plain Preferences
**Precondition:** App installed and user logged in  
**Steps:**
1. Use ADB to pull the app's shared preferences directory:
   ```bash
   adb shell run-as com.storelense.mobile ls /data/data/com.storelense.mobile/shared_prefs/
   adb shell run-as com.storelense.mobile cat /data/data/com.storelense.mobile/shared_prefs/storelense_prefs.xml
   ```

**Expected:**
- File contents are encrypted (AES-256-GCM via Android Keystore)
- No plaintext tokens visible

---

### TC-MOB-SEC-02 — Network Traffic Uses HTTPS (Production)
**Precondition:** Release build pointing to production URL  
**Steps:**
1. Capture network traffic using proxy tool (Charles, mitmproxy)

**Expected:**
- All API calls use HTTPS
- No cleartext HTTP in production
- `network_security_config.xml` allows cleartext only for development (`debug` variant)

---

## Performance

### TC-MOB-PERF-01 — RFID Throughput
**Precondition:** EmDkRfidReader connected on Zebra device  
**Steps:**
1. Start a SOH session in a zone with 500+ tagged items
2. Scan continuously for 2 minutes

**Expected:**
- UI remains responsive (no jank / frame drops)
- EPC count increments smoothly
- No memory leak (EPC reads processed via Flow, not stored unbounded in memory)
- SharedFlow `extraBufferCapacity=1024` prevents backpressure under rapid reads

---

### TC-MOB-PERF-02 — Startup Time
**Steps:**
1. Launch app from cold start (force stop first)

**Expected:**
- Login screen appears within 2 seconds
- No ANR (Application Not Responding)

---

## Edge Cases

### TC-MOB-EDGE-01 — Empty Session (No EPCs Scanned)
**Steps:**
1. Create SOH session
2. Tap Complete without scanning anything

**Expected:**
- App shows 0 scanned, 0 matched
- Upload sends empty batch or skips API call
- Session marked complete on server

---

### TC-MOB-EDGE-02 — Server Returns 500 During Upload
**Precondition:** Server is temporarily unavailable  
**Steps:**
1. Complete scan
2. Tap Upload while server returns 500

**Expected:**
- WorkManager retries with exponential backoff (15s, 30s, 60s)
- After 3 failures, worker marks as failure
- EPC data remains in Room DB (not deleted on failure)

---

### TC-MOB-EDGE-03 — Very Long EPC String
**Precondition:** MockRfidReader generates 96-bit EPCs  
**Expected:**
- UI truncates EPC display to last 8 characters
- Full EPC stored in Room and sent to server correctly

---

### TC-MOB-EDGE-04 — Multi-Zone Store — Session Isolation
**Precondition:** Two SOH sessions active for different zones  
**Steps:**
1. Scan Zone A session
2. Switch to Zone B session

**Expected:**
- EPC reads are stored keyed by `sessionId`
- Zone A and Zone B scans are completely isolated in Room DB

---

## Test Checklist Summary

| Test ID | Area | Platform | Priority |
|---|---|---|---|
| AUTH-01 to AUTH-05 | Authentication | Both | P1 |
| SOH-01 to SOH-08 | SOH Workflow | Both | P1 |
| INB-01 to INB-06 | Inbound Workflow | Both | P1 |
| REP-01 to REP-07 | Replenishment | Both | P1 |
| NAV-01 to NAV-04 | Navigation | Emulator | P2 |
| SEC-01 to SEC-02 | Security | Zebra | P1 |
| PERF-01 to PERF-02 | Performance | Zebra | P2 |
| EDGE-01 to EDGE-04 | Edge Cases | Both | P2 |

**P1** = Must pass before any user testing  
**P2** = Must pass before production release
