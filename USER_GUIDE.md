# StoreLense — User Guide

> StoreLense is an RFID-powered retail operations platform. This guide covers every screen and workflow for all four user roles.

---

## Table of Contents

1. [Getting Started — Login](#1-getting-started--login)
2. [Your Role & What You Can See](#2-your-role--what-you-can-see)
3. [Dashboard](#3-dashboard)
4. [Cycle Count (SOH Sessions)](#4-cycle-count-soh-sessions)
5. [Receiving (Refill Tasks)](#5-receiving-refill-tasks)
6. [Inventory](#6-inventory)
7. [Reports](#7-reports)
8. [Devices (RFID Readers)](#8-devices-rfid-readers)
9. [Stores Management](#9-stores-management)
10. [Users Management](#10-users-management)
11. [ERP Sync](#11-erp-sync)
12. [Mobile App (Zebra)](#12-mobile-app-zebra)
13. [Notifications](#13-notifications)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. Getting Started — Login

Navigate to `http://<server-ip>:3000` in your browser.

**Enter your credentials:**

| Field | Value |
|---|---|
| Username | Provided by your Admin |
| Password | Provided by your Admin |

Click **Sign In**.

On your first login, change your password immediately via the menu at the top-right of the screen.

**Default admin credentials (change after first login):**

| Username | Password |
|---|---|
| `admin` | `Admin@StoreLense1` |

### Session Behaviour

- You stay logged in for **15 minutes** of inactivity. The app silently refreshes your token in the background during active use.
- Your full session lasts up to **7 days** before you need to log in again.
- After several consecutive failed login attempts, your account will be locked. Contact your Admin to unlock it.
- Clicking **Logout** from any screen immediately invalidates your session.

---

## 2. Your Role & What You Can See

Every user is assigned one role. The navigation menu shows only what your role can access.

| Role | Typical Person | Access |
|---|---|---|
| **ADMIN** | IT / Operations Manager | Everything — all stores, all users, all config |
| **STORE_MANAGER** | Store Manager | Dashboard, Cycle Count, Receiving, Inventory, Reports, Devices for their store |
| **STORE_ASSOCIATE** | Floor Staff | Dashboard, Cycle Count (start and run scans) |
| **REFILL_ASSOCIATE** | Stockroom Staff | Receiving (view and fulfil their assigned tasks) |

> **Important:** Non-admin roles only see data for their assigned store. It is not possible to view another store's data.

---

## 3. Dashboard

**Who sees this:** STORE_MANAGER, STORE_ASSOCIATE

The dashboard is the home screen after login. It refreshes automatically every 30 seconds.

### KPI Cards

Four cards at the top give a snapshot of today's operations:

| Card | What it means |
|---|---|
| **Inventory Accuracy %** | Percentage of scanned items matching ERP expected quantities in the last completed session. Target: ≥ 95% |
| **SOH Sessions (7 days)** | How many stock count sessions have been run in the last 7 days |
| **Refill Completion %** | Percentage of refill tasks completed vs created this week |
| **Variance Items** | Number of product lines where the scanned quantity differs from what the ERP says should be there |

A card turning red means it is below target — action may be required.

### Charts

- **Inventory Accuracy Trend** — 7-day line chart. A flat or rising line is good. A falling line means accuracy is degrading and more frequent counts may be needed.
- **Refill Completion Rate** — 7-day bar chart. Low bars mean tasks are not being completed on time.

### Quick-Access Lists

- **Recent SOH Sessions** — Last 5 sessions. Click any row to open the session detail.
- **Pending Refill Tasks** — Last 5 open tasks sorted by priority. Click to open the Receiving screen.

---

## 4. Cycle Count (SOH Sessions)

**Who sees this:** STORE_MANAGER, STORE_ASSOCIATE  
**Path:** `/cycle-count`

A cycle count session is a stock-take — you scan the store with an RFID reader and the system tells you how many items are present compared to what the ERP expects.

### Starting a New Count

Click **Start New Count**. A dialog appears:

| Field | Options | When to Use |
|---|---|---|
| **Session Type** | Full Store | Count every RFID-tagged item in the entire store |
| | Spot Check | Count a specific zone or product group only |
| | Manual | Ad-hoc count with no predefined scope |
| **Zone** (optional) | Any configured zone | Scope the count to one area (e.g. Backroom only) |
| **Notes** (optional) | Free text | Record context (e.g. "Post-weekend recount, Denim section") |

Click **Start**. The session is created with status `created` and appears in the Active Session banner at the top of the screen.

### Running the Scan

Open the Zebra mobile app on your scanner device. The app will show the active session. Walk through the store scanning products — the RFID antenna reads tags automatically as you move through the space.

As your device uploads data, the session's **EPC Reads** and **Unique EPCs** counts update in real time on the web screen.

### Session Statuses

| Status | Colour | Meaning |
|---|---|---|
| `created` | Grey | Session started, no scans yet received |
| `in_progress` | Blue | Scans are being received |
| `completed` | Green | Session finished, accuracy result calculated |
| `cancelled` | Orange | Manually cancelled — no result generated |
| `failed` | Red | A processing error occurred |

### Filtering the Session List

Use the **Status** dropdown above the table to filter by any status. The table shows:

| Column | Description |
|---|---|
| Session ID | Unique identifier — click to open detail |
| Type | full_store / spot_check / manual |
| Status | Current status with colour badge |
| Total EPC Reads | Raw RFID reads received (includes duplicates) |
| Unique EPCs | Distinct items found after deduplication |
| Started | When the session began |
| Completed | When it finished |

### Session Detail

Click any session row to open its detail page:

**Summary tab**
- Accuracy %
- Variance count (how many products had a mismatch)
- Over-count items (more found than expected)
- Under-count items (fewer found than expected)

**Items tab**
- Live list of EPC reads arriving from the scanner
- Updates in real time via WebSocket — no page refresh needed

**Variances tab**
- Products where scanned quantity ≠ ERP expected quantity
- Use this list to investigate discrepancies or trigger refill tasks

**Actions**
- **Complete Session** — Finalises the count, calculates accuracy, triggers variance analysis
- **Cancel Session** — Discards the session with an optional reason

### Full Workflow (End to End)

```
1. Store Associate opens Zebra app → starts new session
2. Walks the floor scanning with RFD40 Bluetooth sled
3. Device buffers EPC reads locally (works offline)
4. Batches are uploaded to the server in the background
5. Web UI shows incoming reads in real time
6. Associate completes the session on the app or web
7. System calculates accuracy % vs ERP expected quantities
8. Variances are listed → Store Manager reviews
9. Low-accuracy items automatically trigger Refill Tasks
10. KPI data is aggregated nightly for the Reports screen
```

---

## 5. Receiving (Refill Tasks)

**Who sees this:** STORE_MANAGER, REFILL_ASSOCIATE  
**Path:** `/receiving`

Refill tasks tell stockroom staff what products need to be moved from the backroom to the shop floor (or ordered from the warehouse). They can come from multiple sources and are assigned to individual associates.

### Task Sources

| Source | How it appears |
|---|---|
| **manual** | Created directly by a Store Manager in the web UI |
| **soh_trigger** | Automatically created when a completed SOH session finds low accuracy (< 95% by default) |
| **scheduled** | Created by a nightly scheduled job |
| **erp** | Pushed in from the ERP system |

### Task Types

| Type | Use Case |
|---|---|
| `replenishment` | Regular restocking — move items from backroom to sales floor |
| `urgency` | High-priority — needs immediate attention |
| `cycle_count` | Triggered by a SOH variance — recount and refill a specific product |

### Priority Scale

Tasks are prioritised from **1 (highest)** to **10 (lowest)**. The task list sorts by priority automatically. Work on priority 1 tasks first.

### Task Statuses

| Status | Meaning |
|---|---|
| `pending` | Created, not yet assigned to anyone |
| `assigned` | Assigned to a Refill Associate |
| `in_progress` | Associate has started working on it |
| `completed` | All line items are in a terminal state |
| `cancelled` | Cancelled by a Manager |

### Creating a Task (Store Manager)

Click **New Task** and fill in the form:

| Field | Required | Description |
|---|---|---|
| Task Type | yes | replenishment / urgency / cycle_count |
| Priority | yes | 1 (urgent) to 10 (low) |
| Due Date | no | Deadline for completion |
| Notes | no | Instructions for the associate |
| Line Items | yes | Add products: select product → enter quantity → select target zone |

Click **Save**. The task appears in the list with status `pending`.

### Assigning a Task (Store Manager)

Open a pending task → click **Assign** → select a Refill Associate from the dropdown → click **Confirm**.

The selected associate receives an instant notification on their device.

### Fulfilling a Task (Refill Associate)

Open your assigned task. You will see a list of line items:

| Column | Description |
|---|---|
| Product | Name and SKU |
| Target Zone | Where to place the stock (e.g. "Ground Floor") |
| Requested Qty | How many units are needed |
| Fulfilled Qty | How many you actually brought |
| Status | pending / fulfilled / partial / skipped |

For each item:
1. Go to the backroom and pick the stock
2. Enter the **actual quantity you are bringing** in the Fulfilled Qty field
3. Click **Confirm** on that item

**Item outcomes:**
- If fulfilled qty ≥ requested qty → item status: **fulfilled**
- If fulfilled qty > 0 but < requested qty → item status: **partial**
- If you cannot fulfil an item at all → mark it as **skipped** with a reason

When all items are in a terminal state (fulfilled / partial / skipped), the task automatically moves to `completed`.

### Cancelling a Task (Store Manager)

Open any non-completed task → click **Cancel** → enter an optional reason → confirm.

---

## 6. Inventory

**Who sees this:** STORE_MANAGER, ADMIN  
**Path:** `/inventory`

The Inventory screen shows the current known state of every RFID-enabled product in the store — how many are physically present vs how many the ERP says should be there.

### KPI Cards

| Card | Description |
|---|---|
| **Total SKUs** | Number of distinct product lines in the store |
| **In Store (EPC)** | Number of EPC tags currently confirmed present |
| **Missing (EPC)** | Tags not seen in the most recent scan |
| **Low Accuracy** | Products where accuracy is below 95% (highlighted in red) |

### Inventory State Table

| Column | Description |
|---|---|
| Product | Name and SKU |
| On Hand | Quantity scanned and confirmed present in the last count |
| Expected | Quantity the ERP says should be here |
| Accuracy % | (On Hand ÷ Expected) × 100 |
| Last Counted | Date of the last session that included this product |

**Products below 95% accuracy are highlighted in red.** Click any row to open the product detail.

### EPC Status Summary

Individual RFID tags can have the following statuses:

| Status | Meaning |
|---|---|
| `in_store` | Tag was seen in the most recent scan session |
| `missing` | Tag was previously registered but not seen in the last scan |
| `sold` | Tag has been decommissioned via a sale |
| `damaged` | Item marked as damaged or written off |
| `transferred` | Item sent to another store |

### Product Inventory Detail

Click a product row to open its detail:
- Full product info (SKU, name, category, brand, ERP code)
- Zone-level breakdown (floor, backroom, fitting room, etc.)
- List of individual EPC tags with status
- Link to the last SOH session that counted this product

---

## 7. Reports

**Who sees this:** STORE_MANAGER, ADMIN  
**Path:** `/reports`

The Reports screen provides historical KPI trending for performance review and planning.

### Date Range Selector

Choose from three preset windows at the top:

| Option | Data shown |
|---|---|
| **7 days** | Daily detail for the last week |
| **30 days** | Last month day-by-day |
| **90 days** | Quarter view |

### Charts

| Chart | What it shows |
|---|---|
| **Inventory Accuracy Trend** | Line chart, 0–100%. Dashed target line at 95%. A falling trend means accuracy is degrading. |
| **Refill Completion Rate** | Bar chart. Low bars mean tasks are not being completed on time. |
| **RFID Read Volume** | Bar chart showing daily scan activity (thousands of reads). |

### Daily KPI Table

Below the charts, a table shows the raw numbers for each day:

| Column | Description |
|---|---|
| Date | Calendar date |
| Accuracy % | Inventory accuracy for that day |
| SOH Sessions | Count sessions run |
| Refill Created | Tasks created that day |
| Refill Completed | Tasks completed that day |
| Completion % | Completion rate |
| EPC Reads | Total RFID tag reads |
| Variance Items | Products with quantity mismatches |

> **Note:** KPI data is aggregated nightly. Data for the current day appears the following morning. Admins can manually trigger a re-aggregation from the ERP admin tools if data needs to be recalculated.

---

## 8. Devices (RFID Readers)

**Who sees this:** STORE_MANAGER, ADMIN  
**Path:** `/devices`

This screen shows all RFID readers registered to the store and their live heartbeat status.

### KPI Cards

| Card | Description |
|---|---|
| **Total Readers** | All registered readers |
| **Online** | Readers that sent a heartbeat in the last 5 minutes |
| **Offline** | Readers that have not checked in for > 5 minutes |
| **Fixed Readers** | Count of permanently mounted (non-handheld) readers |

### Reader Table

| Column | Description |
|---|---|
| Reader Code | Unique identifier assigned during hardware installation |
| Type | `fixed` (mounted), `handheld`, or `bluetooth_sled` (RFD40 attached to mobile device) |
| IP Address | Network address (for fixed readers only) |
| Antenna Count | Number of antenna ports in use |
| Tx Power | Transmission power in dBm |
| Firmware | Installed firmware version |
| Status | **Online** (green) or **Offline** (red) |
| Last Seen | Timestamp of the most recent heartbeat |
| Enabled | Toggle to activate or deactivate the reader |

### Online / Offline Logic

A reader is shown as **Online** if it has sent a heartbeat within the last **5 minutes**. If no heartbeat is received after that, it flips to **Offline**. This usually means:
- The device is powered off
- Network connectivity was lost
- The RFID service on the device crashed

---

## 9. Stores Management

**Who sees this:** ADMIN only  
**Path:** `/stores`

### Store List

| Column | Description |
|---|---|
| Store Code | Short unique code (e.g. `SYD001`) — click to open detail |
| Name | Store display name |
| City / State / Country | Location |
| Timezone | Local timezone used for scheduling and reporting |
| Status | Active / Inactive |
| ERP Code | The store's identifier in the ERP system |
| Created | Date the store was onboarded |

### Store Detail

Click a store code to open its full configuration.

**Info tab:** All address fields, timezone, ERP code. Click **Edit** to update.

**Zones tab:**

Each store has zones that group physical areas for counting and refill routing.

| Zone Type | Typical Location |
|---|---|
| `floor` | Main retail sales floor |
| `backroom` | Storage or loading area |
| `fitting_room` | Changing rooms |
| `stockroom` | Central stock holding area |
| `display` | Display fixtures or windows |

Click **Add Zone** to create a new zone. Click any zone row to edit name, type, or display order.

**Readers tab:** All RFID readers assigned to this store. Shows which zone each reader is in and its current status.

**Config tab:** Per-store operational settings:
- RFID Tx Power
- Gen2 Session mode
- Refill auto-assign (if enabled, tasks are automatically assigned to available Refill Associates)

### Creating a Store

Click **New Store** on the store list page. Required fields: Store Code (unique), Name. Optional: full address, timezone, ERP code.

---

## 10. Users Management

**Who sees this:** ADMIN only  
**Path:** `/users`

### User List

| Column | Description |
|---|---|
| Username | Login name |
| Name | First and last name |
| Email | Contact email |
| Role | ADMIN / STORE_MANAGER / STORE_ASSOCIATE / REFILL_ASSOCIATE |
| Status | Active (green) / Inactive (red) |
| Last Login | Timestamp of most recent successful login |

### Creating a User

Click **New User**:

| Field | Required | Notes |
|---|---|---|
| First Name | yes | |
| Last Name | yes | |
| Username | yes | Must be unique across the system |
| Email | yes | Must be unique |
| Password | yes | Minimum 8 characters |
| Role | yes | Select from dropdown |
| Store | no | Leave blank for ADMIN; required for all other roles |

### Editing a User

Click any username to open the edit form. You can change name, email, role, and store assignment.

### Deactivating a User

Click the **Deactivate** button on a user. The user can no longer log in, but all their historical data (sessions, task records, audit logs) is preserved. This action is reversible — click **Activate** to restore access.

> You cannot permanently delete users — the system always preserves audit history.

---

## 11. ERP Sync

**Who sees this:** ADMIN only  
**Path:** Admin → ERP Sync (within Stores section)

StoreLense maintains a two-way sync with your ERP system.

### Inbound Syncs (ERP → StoreLense)

| Sync | What it does | When it runs |
|---|---|---|
| **Product Sync** | Imports product master from ERP: SKU, name, category, ERP code | Nightly + manual trigger |
| **Inventory Sync** | Imports expected stock quantities per store per product | Nightly + manual trigger |

To trigger manually: click **Sync Products** or **Sync Inventory** in the ERP Admin panel.

### Outbound Syncs (StoreLense → ERP)

| Sync | What it does | When it runs |
|---|---|---|
| **SOH Push** | Sends completed SOH session results (actual on-hand quantities) to ERP | After each session completes (if enabled) |

Enable outbound push by setting `ERP_PUSH_SOH_ENABLED=true` in your server configuration.

### Sync Logs

The **Sync Logs** table shows every sync attempt:

| Column | Description |
|---|---|
| Type | PRODUCT_INBOUND / INVENTORY_INBOUND / SOH_OUTBOUND |
| Status | SUCCESS or FAILED |
| Records | Count of records processed |
| Triggered By | `manual` or `scheduled` |
| Started / Completed | Timestamps |
| Error | Shown on failed syncs |

The **Latest Syncs** view shows only the most recent record per type — use this as a health check.

---

## 12. Mobile App (Zebra)

The Zebra Android app runs on Zebra TC-series or MC-series devices with an RFD40 Bluetooth RFID sled or integrated reader. The same login credentials as the web portal are used.

### Login

Enter your username and password. The app stores your session for up to 7 days — you will not need to log in every shift.

### SOH Count Flow

1. Tap **Start Count** on the home screen
2. Select session type: Full Store / Spot Check / Manual
3. Optionally select a Zone to scope the count
4. Tap **Start Scanning** — the RFID antenna activates
5. Walk through the area. Tags are read automatically and continuously
6. EPC reads are buffered locally on the device (works even without WiFi)
7. WorkManager syncs batches to the server in the background when connected
8. The web portal shows incoming reads in real time
9. When done, tap **Complete** on the app or on the web UI
10. Accuracy % and variance summary appear

**Offline support:** If the device loses network during scanning, all reads are stored locally. When connectivity is restored, WorkManager automatically uploads the queued batches. No data is lost.

### Refill Task Flow

1. Tap **Tasks** on the home screen
2. Your assigned tasks are shown, sorted by priority (1 = urgent)
3. Tap a task to open it
4. See the line items: product name, how many needed, which zone
5. Go to the backroom, pick the items
6. For each item, enter the fulfilled quantity and confirm
7. Mark items as partial or skipped if needed
8. When all items are resolved, tap **Complete Task**

---

## 13. Notifications

StoreLense pushes real-time alerts to your browser without requiring a page refresh.

| Event | Who receives it |
|---|---|
| SOH session completes | All Store Managers for that store |
| New EPC reads arrive in a session | Store Manager viewing that session |
| Refill task assigned to you | The assigned Refill Associate |
| Refill task completed | Store Manager for that store |
| RFID reader goes offline | Store Manager for that store |

Notifications appear as a banner at the top of the screen. Click the notification bell icon to see your notification history.

---

## 14. Troubleshooting

### Cannot log in

| Symptom | Cause | Action |
|---|---|---|
| "Invalid credentials" | Wrong username or password | Check caps lock; try again carefully |
| "Account locked" | Too many failed attempts | Contact your Admin to unlock the account |
| "Session expired" | Token expired after 7 days | Log in again |

### No data visible on Dashboard

| Symptom | Cause | Action |
|---|---|---|
| KPI cards show 0 | No sessions run yet | Start a Cycle Count session |
| Charts are empty | No data in selected date range | Expand the date range or run a session |
| All data is from yesterday | KPIs aggregate nightly | Check again tomorrow morning; Admin can trigger manual aggregation |

### SOH session stuck in "in_progress"

The session is waiting for EPC reads to arrive. Check:
- Is the Zebra device connected to WiFi?
- Is the RFID service running on the device?
- Check the mobile app — it should show "Syncing" if uploads are in progress

### Refill task not appearing

Check:
- Are you filtering by the correct status? (pending, assigned, all)
- Was the task assigned to a different associate?
- Did the task auto-complete already?

### Inventory accuracy looks wrong

| Possible cause | What to check |
|---|---|
| ERP expected quantities out of date | Trigger an **Inventory Sync** from the ERP admin panel |
| Last count was a spot check, not full store | Run a full-store session |
| Scanner didn't cover all zones | Check `uniqueEpcCount` vs total registered EPCs for the store |

### RFID reader showing Offline

- Check device power and battery
- Check network connectivity on the scanner device
- Restart the RFID service on the Zebra device
- If it's a fixed reader (FX9600), check the network cable and reader web UI

### ERP sync failed

1. Check the Sync Logs for the error message
2. Verify `ERP_BASE_URL` and `ERP_API_KEY` in the server `.env` file
3. Confirm the ERP API is reachable from the server: `curl -I $ERP_BASE_URL`
4. Re-trigger the sync manually after fixing the issue
