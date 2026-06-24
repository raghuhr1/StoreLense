Here are feature suggestions organized by priority and complexity, inspired by Item Optix, Truevue, and Tag ID:

Tier 1 — High Value, Core RFID Features
1. Item Locator (Find Item)
Like Sensormatic's "Find Item" mode. Associate enters a SKU or scans a barcode → app starts continuous RFID scan → shows a hot/cold proximity indicator (signal strength bar or color) as the associate walks toward the item. Stops when EPC is read within 1-2 meters. Used for:

Customer "do you have this in size M?" requests
Locating a specific item in backroom
2. Quick Spot Count
A lightweight count without creating a full SOH session. Associate selects a zone or gondola → scans for 30 seconds → sees a summary: "32 scanned, 35 expected, 3 missing". No session created. Used for mid-day checks between full cycle counts.

3. Product Search + Stock Check
Search by SKU, barcode, or product name → shows:

On-hand qty (floor vs backroom)
Last counted date
Zone location
Scan the EPC to jump straight to Item Locator
Tier 2 — Replenishment & Receiving
4. Smart Replenishment Pick List
Before an associate starts picking from backroom, app shows a prioritized list of items to move to floor — ranked by:

Items below minimum floor level
High-selling velocity items
Items flagged as "missing" in last SOH
Associate scans each item as they pick it → confirms movement to floor → updates inventory_state in real time.

5. Inbound Receiving / GRN Scan
When a delivery arrives, associate opens a GRN task (from erp-integration) and scans the carton. App compares scanned EPCs to the expected purchase order line items and shows:

Expected: 120 units | Received: 118 | Discrepancy: 2
Flags specific EPCs as over/short shipment
6. Cross-Dock / Transfer Scan
When moving stock between stores, scan outbound items → app creates a transfer manifest → receiving store confirms with a matching scan. Both inventory states update automatically via Kafka.

Tier 3 — Loss Prevention & Analytics (Truevue-style)
7. Exception / Ghost Read Alerts
Items that appear in RFID reads but have no EPC registered in the product master are flagged as ghost tags. Alert appears on device with the raw EPC value for investigation (possible counterfeit or unregistered item).

8. High-Shrinkage Watch List
Store manager configures a list of high-value or high-theft SKUs. During any scan session, if a watch-list item is scanned in an unexpected zone (e.g., fitting room to exit path without reaching POS), the app raises a silent alert.

9. Fitting Room Intelligence
Track items entering and leaving fitting rooms (requires fixed reader at fitting room entrance). Dashboard on the Android app shows:

Items currently in fitting rooms
Average dwell time
Items left behind (potential misplacement or theft)
10. Zone Transition Tracking
When a fixed reader detects an EPC moving from Backroom zone to Floor zone, the app (or background sync) automatically updates zone-level inventory_state — no manual scanning needed. Associate can confirm or reject transitions flagged as suspicious.

Tier 4 — Tag Operations (Tag ID-style)
11. Tag Commissioning / Encoding
For stores that apply their own RFID tags to untagged merchandise: associate enters SKU and quantity → app generates GS1-compliant SGTIN-96 EPCs → writes them to blank tags via the Zebra handheld's NFC/RFID write capability. Tags are pre-registered in the product master.

12. Tag Diagnostics
Scan a specific EPC and see its full lifecycle:

When it was first seen
Which store/zone it was last seen in
Whether it was ever sold
Alert if it appears at a different store than registered (intercept check)
13. Void / Re-encode
Replace a damaged or incorrectly encoded tag in the field. Old EPC is voided in the registry, new EPC is issued and linked to the same product/store.

Tier 5 — Customer-Facing / Assisted Selling
14. Associate Assist (Customer Request)
Customer asks: "Do you have this dress in a size 12 in blue?". Associate scans the item's hang tag → app searches inventory_state across:

Current store floor
Current store backroom
Nearby stores (if multi-store API is available) Shows availability instantly, with zone location if in-store.
15. Click & Collect Verification
When a customer arrives for a buy-online-pick-up-in-store order, associate scans the pick-up items → verifies all EPCs match the order → marks order as fulfilled.

Summary Priority Table
Feature	Value	Complexity	Similar To
Item Locator	⭐⭐⭐⭐⭐	Medium	Item Optix Find Item
Quick Spot Count	⭐⭐⭐⭐⭐	Low	Item Optix Spot Count
Product Search	⭐⭐⭐⭐	Low	Item Optix Search
Smart Replenishment	⭐⭐⭐⭐⭐	Medium	Item Optix Replen
Inbound GRN Scan	⭐⭐⭐⭐	Medium	Item Optix Receiving
Ghost Read Alerts	⭐⭐⭐	Low	Truevue Exception
Tag Commissioning	⭐⭐⭐	High	Tag ID Encode
Associate Assist	⭐⭐⭐⭐	Low	Item Optix Customer
Fitting Room Intel	⭐⭐	High	Truevue Fitting Room
Zone Transition	⭐⭐⭐	High	Truevue Visibility
Recommended build order: Item Locator → Product Search + Stock Check → Quick Spot Count → Smart Replenishment Pick List → Inbound GRN Scan. These five cover 80% of a typical store associate's daily RFID workflow with no fixed-reader hardware required.