# StoreLense — Test Case Document

> **Version:** 1.0  
> **Coverage:** All modules — Authentication, Users, Stores, Zones, Products, Inventory, SOH Sessions, Refill Tasks, RFID Ingest, Reporting, ERP Integration, Notifications, Security  
> **Format:** TC-ID | Module | Title | Priority | Preconditions | Steps | Expected Result | Pass Criteria

---

## Test Case Index

| Module | TC Range | Count |
|---|---|---|
| [Authentication](#1-authentication) | TC-AUTH-001 – TC-AUTH-020 | 20 |
| [User Management](#2-user-management) | TC-USER-001 – TC-USER-015 | 15 |
| [Store Management](#3-store-management) | TC-STR-001 – TC-STR-015 | 15 |
| [Zone Management](#4-zone-management) | TC-ZONE-001 – TC-ZONE-010 | 10 |
| [Product Management](#5-product-management) | TC-PROD-001 – TC-PROD-015 | 15 |
| [Inventory](#6-inventory) | TC-INV-001 – TC-INV-010 | 10 |
| [SOH Sessions (Cycle Count)](#7-soh-sessions-cycle-count) | TC-SOH-001 – TC-SOH-020 | 20 |
| [Refill Tasks](#8-refill-tasks) | TC-RFILL-001 – TC-RFILL-020 | 20 |
| [RFID Ingest](#9-rfid-ingest) | TC-RFID-001 – TC-RFID-015 | 15 |
| [Reporting](#10-reporting) | TC-RPT-001 – TC-RPT-010 | 10 |
| [ERP Integration](#11-erp-integration) | TC-ERP-001 – TC-ERP-010 | 10 |
| [Security & Access Control](#12-security--access-control) | TC-SEC-001 – TC-SEC-015 | 15 |
| [Notifications](#13-notifications) | TC-NOTIF-001 – TC-NOTIF-008 | 8 |
| [Android Mobile App](#14-android-mobile-app) | TC-MOB-001 – TC-MOB-015 | 15 |
| **Total** | | **198** |

---

## Legend

| Priority | Description |
|---|---|
| **P1** | Critical — system cannot function without this |
| **P2** | High — major feature, must work for release |
| **P3** | Medium — important but workaround exists |
| **P4** | Low — nice to have |

| Status |
|---|
| PASS |
| FAIL |
| BLOCKED |
| NOT RUN |

---

## 1. Authentication

---

**TC-AUTH-001**
- **Title:** Successful login with valid admin credentials
- **Priority:** P1
- **Preconditions:** Application deployed; admin user seeded (`admin` / `Admin@StoreLense1`)
- **Steps:**
  1. Send `POST /api/auth/login` with `{"username":"admin","password":"Admin@StoreLense1"}`
- **Expected Result:** HTTP 200; response contains `accessToken`, `refreshToken`, `tokenType:"Bearer"`, `role:"ADMIN"`, `storeId:null`, `expiresIn:900`
- **Pass Criteria:** Status 200; `accessToken` is a valid non-empty JWT string

---

**TC-AUTH-002**
- **Title:** Login fails with incorrect password
- **Priority:** P1
- **Preconditions:** Admin user exists
- **Steps:**
  1. Send `POST /api/auth/login` with `{"username":"admin","password":"WrongPass"}`
- **Expected Result:** HTTP 401; `success:false`; `code:"UNAUTHORIZED"`; no token issued
- **Pass Criteria:** Status 401; no `accessToken` in response

---

**TC-AUTH-003**
- **Title:** Login fails with non-existent username
- **Priority:** P1
- **Preconditions:** None
- **Steps:**
  1. Send `POST /api/auth/login` with `{"username":"ghost","password":"any"}`
- **Expected Result:** HTTP 401; `success:false`; generic error message (must not reveal whether username exists)
- **Pass Criteria:** Status 401; error message does not say "username not found"

---

**TC-AUTH-004**
- **Title:** Login fails with empty credentials
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Send `POST /api/auth/login` with `{"username":"","password":""}`
- **Expected Result:** HTTP 400; `code:"VALIDATION_ERROR"`; field errors for `username` and `password`
- **Pass Criteria:** Status 400

---

**TC-AUTH-005**
- **Title:** Access token can be used to call authenticated endpoint
- **Priority:** P1
- **Preconditions:** Valid access token obtained from TC-AUTH-001
- **Steps:**
  1. Send `GET /api/auth/me` with `Authorization: Bearer <accessToken>`
- **Expected Result:** HTTP 200; returns user info matching the logged-in user
- **Pass Criteria:** Status 200; `username` matches "admin"

---

**TC-AUTH-006**
- **Title:** Authenticated endpoint rejects request with no token
- **Priority:** P1
- **Preconditions:** None
- **Steps:**
  1. Send `GET /api/stores` with no `Authorization` header
- **Expected Result:** HTTP 401; `success:false`
- **Pass Criteria:** Status 401

---

**TC-AUTH-007**
- **Title:** Authenticated endpoint rejects expired token
- **Priority:** P1
- **Preconditions:** An expired JWT token (can be crafted with past `exp` claim or wait 15 min after login)
- **Steps:**
  1. Send `GET /api/stores` with `Authorization: Bearer <expiredToken>`
- **Expected Result:** HTTP 401; `success:false`
- **Pass Criteria:** Status 401

---

**TC-AUTH-008**
- **Title:** Authenticated endpoint rejects malformed token
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Send `GET /api/stores` with `Authorization: Bearer not.a.valid.jwt`
- **Expected Result:** HTTP 401; `success:false`
- **Pass Criteria:** Status 401

---

**TC-AUTH-009**
- **Title:** Refresh token issues a new access token
- **Priority:** P1
- **Preconditions:** Valid refresh token obtained from TC-AUTH-001
- **Steps:**
  1. Send `POST /api/auth/refresh` with `{"refreshToken":"<refreshToken>"}`
- **Expected Result:** HTTP 200; new `accessToken` and `refreshToken` returned; original refresh token is no longer valid
- **Pass Criteria:** Status 200; new tokens different from original

---

**TC-AUTH-010**
- **Title:** Refresh token fails with invalid value
- **Priority:** P1
- **Preconditions:** None
- **Steps:**
  1. Send `POST /api/auth/refresh` with `{"refreshToken":"made-up-token"}`
- **Expected Result:** HTTP 401; no new tokens issued
- **Pass Criteria:** Status 401

---

**TC-AUTH-011**
- **Title:** Logout blacklists access token
- **Priority:** P1
- **Preconditions:** Valid session (access + refresh token)
- **Steps:**
  1. Send `POST /api/auth/logout` with `Authorization: Bearer <accessToken>` and `{"refreshToken":"<refreshToken>"}`
  2. Send `GET /api/stores` with the same `accessToken`
- **Expected Result:** Step 1 → HTTP 200. Step 2 → HTTP 401 (blacklisted)
- **Pass Criteria:** Subsequent request with old token returns 401

---

**TC-AUTH-012**
- **Title:** Logout revokes refresh token
- **Priority:** P1
- **Preconditions:** Valid session
- **Steps:**
  1. Logout (TC-AUTH-011 Step 1)
  2. Attempt `POST /api/auth/refresh` with old refresh token
- **Expected Result:** HTTP 401; refresh token is invalid
- **Pass Criteria:** Status 401

---

**TC-AUTH-013**
- **Title:** Store Associate can login and access allowed endpoints
- **Priority:** P1
- **Preconditions:** STORE_ASSOCIATE user exists with assigned store
- **Steps:**
  1. Login as STORE_ASSOCIATE
  2. Send `GET /api/soh/sessions?storeId=<assignedStoreId>`
- **Expected Result:** Login succeeds; SOH sessions returns HTTP 200
- **Pass Criteria:** Status 200; data contains only sessions for assigned store

---

**TC-AUTH-014**
- **Title:** Store Associate cannot access admin-only endpoints
- **Priority:** P1
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. Login as STORE_ASSOCIATE
  2. Send `POST /api/stores` (Admin-only)
- **Expected Result:** HTTP 403 Forbidden
- **Pass Criteria:** Status 403

---

**TC-AUTH-015**
- **Title:** Token contains correct role and storeId claims
- **Priority:** P2
- **Preconditions:** STORE_MANAGER user exists with store assigned
- **Steps:**
  1. Login as STORE_MANAGER
  2. Decode the JWT payload (base64)
- **Expected Result:** Payload contains `role:"STORE_MANAGER"`, `storeId:"<uuid>"`, `sub:"<username>"`, `exp` in the future
- **Pass Criteria:** All claims present and correct

---

**TC-AUTH-016**
- **Title:** Concurrent sessions are independent
- **Priority:** P3
- **Preconditions:** Admin user
- **Steps:**
  1. Login twice → get Token A and Token B
  2. Use Token A for an authenticated request
  3. Use Token B for an authenticated request
- **Expected Result:** Both tokens work independently; HTTP 200 for both
- **Pass Criteria:** Both requests succeed

---

**TC-AUTH-017**
- **Title:** Login with missing request body fields
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Send `POST /api/auth/login` with `{"username":"admin"}` (missing password)
- **Expected Result:** HTTP 400; validation error for missing `password`
- **Pass Criteria:** Status 400; `code:"VALIDATION_ERROR"`

---

**TC-AUTH-018**
- **Title:** GET /api/auth/me returns correct user profile
- **Priority:** P2
- **Preconditions:** Logged-in admin token
- **Steps:**
  1. Send `GET /api/auth/me` with admin token
- **Expected Result:** HTTP 200; `username:"admin"`, `role:"ADMIN"`, `storeId:null`
- **Pass Criteria:** Status 200; role = ADMIN; storeId = null

---

**TC-AUTH-019**
- **Title:** Token refresh rotates the refresh token (old token invalidated)
- **Priority:** P2
- **Preconditions:** Valid refresh token
- **Steps:**
  1. Call `POST /api/auth/refresh` → get new refreshToken2
  2. Call `POST /api/auth/refresh` again with original refreshToken1
- **Expected Result:** Step 2 returns HTTP 401 — original refresh token is invalidated after rotation
- **Pass Criteria:** Status 401 on second use of original refresh token

---

**TC-AUTH-020**
- **Title:** REFILL_ASSOCIATE role can only access refill endpoints
- **Priority:** P2
- **Preconditions:** REFILL_ASSOCIATE user with assigned store
- **Steps:**
  1. Login as REFILL_ASSOCIATE
  2. `GET /api/refill/tasks?storeId=<id>` → expect 200
  3. `GET /api/reporting/kpi/daily?storeId=<id>` → expect 403
  4. `POST /api/soh/sessions` → expect 403
- **Expected Result:** Only refill endpoints accessible
- **Pass Criteria:** Step 2 = 200; Steps 3,4 = 403

---

## 2. User Management

---

**TC-USER-001**
- **Title:** Admin creates a new Store Manager
- **Priority:** P1
- **Preconditions:** Admin token; at least one store exists
- **Steps:**
  1. Send `POST /api/users` with `{"username":"mgr001","email":"mgr@test.com","password":"Pass1234!","firstName":"Joe","lastName":"Mgr","storeId":"<storeId>","roles":["STORE_MANAGER"]}`
- **Expected Result:** HTTP 201; user created with `active:true`, `roles:["STORE_MANAGER"]`, correct `storeId`
- **Pass Criteria:** Status 201; user appears in `GET /api/users`

---

**TC-USER-002**
- **Title:** Creating user with duplicate username fails
- **Priority:** P1
- **Preconditions:** User "admin" already exists
- **Steps:**
  1. Send `POST /api/users` with `username:"admin"` (duplicate)
- **Expected Result:** HTTP 409; `code:"CONFLICT"`
- **Pass Criteria:** Status 409

---

**TC-USER-003**
- **Title:** Creating user with duplicate email fails
- **Priority:** P1
- **Preconditions:** User with `admin@storelense.internal` exists
- **Steps:**
  1. Send `POST /api/users` with `email:"admin@storelense.internal"` (duplicate)
- **Expected Result:** HTTP 409; `code:"CONFLICT"`
- **Pass Criteria:** Status 409

---

**TC-USER-004**
- **Title:** Creating user with short password fails
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. Send `POST /api/users` with `"password":"abc"` (< 8 chars)
- **Expected Result:** HTTP 400; validation error for `password`
- **Pass Criteria:** Status 400; password field error mentioned

---

**TC-USER-005**
- **Title:** Admin retrieves user by ID
- **Priority:** P2
- **Preconditions:** Admin token; user created (TC-USER-001)
- **Steps:**
  1. Send `GET /api/users/<userId>`
- **Expected Result:** HTTP 200; correct user data returned
- **Pass Criteria:** Status 200; `id` matches requested UUID

---

**TC-USER-006**
- **Title:** Admin lists all users with pagination
- **Priority:** P2
- **Preconditions:** Admin token; at least 2 users exist
- **Steps:**
  1. Send `GET /api/users?page=0&size=1`
- **Expected Result:** HTTP 200; `content` has 1 item; `totalElements >= 2`; `last:false`
- **Pass Criteria:** Status 200; pagination metadata correct

---

**TC-USER-007**
- **Title:** Store Manager lists only users in their store
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token; users exist in their store and in another store
- **Steps:**
  1. Send `GET /api/users` with STORE_MANAGER token
- **Expected Result:** HTTP 200; only returns users assigned to the manager's store
- **Pass Criteria:** All returned users have `storeId` matching the manager's store

---

**TC-USER-008**
- **Title:** Admin updates user role
- **Priority:** P2
- **Preconditions:** Admin token; existing user with STORE_ASSOCIATE role
- **Steps:**
  1. Send `PUT /api/users/<userId>` with `{"roles":["STORE_MANAGER"]}`
- **Expected Result:** HTTP 200; user now has role `STORE_MANAGER`
- **Pass Criteria:** `GET /api/users/<userId>` returns `roles:["STORE_MANAGER"]`

---

**TC-USER-009**
- **Title:** Admin deactivates a user
- **Priority:** P1
- **Preconditions:** Admin token; active user exists
- **Steps:**
  1. Send `DELETE /api/users/<userId>`
  2. Attempt login with deactivated user credentials
- **Expected Result:** Step 1 → HTTP 200. Step 2 → HTTP 401 (inactive user cannot login)
- **Pass Criteria:** User `active:false`; login blocked

---

**TC-USER-010**
- **Title:** Non-admin cannot create users
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. Send `POST /api/users` with STORE_MANAGER token
- **Expected Result:** HTTP 403 Forbidden
- **Pass Criteria:** Status 403

---

**TC-USER-011**
- **Title:** Creating user without roles fails
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. Send `POST /api/users` with `"roles":[]` (empty)
- **Expected Result:** HTTP 400; validation error for `roles`
- **Pass Criteria:** Status 400

---

**TC-USER-012**
- **Title:** Admin updates user store assignment
- **Priority:** P2
- **Preconditions:** Admin token; 2 stores exist; user assigned to Store A
- **Steps:**
  1. Send `PUT /api/users/<userId>` with `{"storeId":"<storeBId>"}`
- **Expected Result:** HTTP 200; user now assigned to Store B
- **Pass Criteria:** `storeId` in response = Store B UUID

---

**TC-USER-013**
- **Title:** Admin reactivates a deactivated user
- **Priority:** P3
- **Preconditions:** Deactivated user (TC-USER-009)
- **Steps:**
  1. Send `PUT /api/users/<userId>` with `{"active":true}`
  2. Login with reactivated user
- **Expected Result:** Status 200; user can login
- **Pass Criteria:** Login succeeds with HTTP 200

---

**TC-USER-014**
- **Title:** Creating user with invalid email format fails
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. Send `POST /api/users` with `"email":"not-an-email"`
- **Expected Result:** HTTP 400; validation error for `email`
- **Pass Criteria:** Status 400

---

**TC-USER-015**
- **Title:** User list filtered by storeId (Admin)
- **Priority:** P3
- **Preconditions:** Admin token; users in multiple stores
- **Steps:**
  1. Send `GET /api/users?storeId=<storeId>`
- **Expected Result:** HTTP 200; only users with matching `storeId` returned
- **Pass Criteria:** All users in response have `storeId` = requested store

---

## 3. Store Management

---

**TC-STR-001**
- **Title:** Admin creates a new store
- **Priority:** P1
- **Preconditions:** Admin token
- **Steps:**
  1. Send `POST /api/stores` with `{"storeCode":"TST001","name":"Test Store","city":"Sydney","countryCode":"AU","timezone":"Australia/Sydney"}`
- **Expected Result:** HTTP 201; store created with `active:true`, assigned UUID
- **Pass Criteria:** Status 201; store appears in `GET /api/stores`

---

**TC-STR-002**
- **Title:** Store list is public (no auth required)
- **Priority:** P1
- **Preconditions:** At least one active store exists
- **Steps:**
  1. Send `GET /api/stores` with no Authorization header
- **Expected Result:** HTTP 200; store list returned
- **Pass Criteria:** Status 200

---

**TC-STR-003**
- **Title:** Creating store with duplicate storeCode fails
- **Priority:** P1
- **Preconditions:** Store "TST001" already exists (TC-STR-001)
- **Steps:**
  1. Send `POST /api/stores` with `storeCode:"TST001"`
- **Expected Result:** HTTP 409; `code:"CONFLICT"`
- **Pass Criteria:** Status 409

---

**TC-STR-004**
- **Title:** Admin updates store details
- **Priority:** P2
- **Preconditions:** Admin token; store exists
- **Steps:**
  1. Send `PUT /api/stores/<storeId>` with `{"name":"Updated Store Name","active":true}`
- **Expected Result:** HTTP 200; store name updated; other fields unchanged
- **Pass Criteria:** `name` in response matches "Updated Store Name"

---

**TC-STR-005**
- **Title:** Admin deactivates a store
- **Priority:** P2
- **Preconditions:** Admin token; active store exists
- **Steps:**
  1. Send `DELETE /api/stores/<storeId>`
  2. Send `GET /api/stores`
- **Expected Result:** HTTP 200; deactivated store no longer in active list
- **Pass Criteria:** Store `active:false`; not in GET /api/stores results

---

**TC-STR-006**
- **Title:** Get store by ID returns correct store
- **Priority:** P2
- **Preconditions:** Store exists
- **Steps:**
  1. Send `GET /api/stores/<storeId>`
- **Expected Result:** HTTP 200; correct store data returned
- **Pass Criteria:** `id` matches; all fields present

---

**TC-STR-007**
- **Title:** Non-admin cannot create store
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. Send `POST /api/stores` with STORE_MANAGER token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-STR-008**
- **Title:** Creating store with missing required field fails
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. Send `POST /api/stores` with `{"name":"No Code Store"}` (missing `storeCode`)
- **Expected Result:** HTTP 400; validation error for `storeCode`
- **Pass Criteria:** Status 400

---

**TC-STR-009**
- **Title:** Store list is paginated
- **Priority:** P3
- **Preconditions:** 3+ stores exist
- **Steps:**
  1. Send `GET /api/stores?page=0&size=2`
- **Expected Result:** HTTP 200; `content` has 2 items; `totalElements >= 3`
- **Pass Criteria:** Pagination metadata correct

---

**TC-STR-010**
- **Title:** Store with invalid countryCode fails validation
- **Priority:** P3
- **Preconditions:** Admin token
- **Steps:**
  1. Send `POST /api/stores` with `"countryCode":"AUSTRALIA"` (not 2 chars)
- **Expected Result:** HTTP 400; validation error for `countryCode`
- **Pass Criteria:** Status 400

---

**TC-STR-011**
- **Title:** GET /api/stores/{id} returns 404 for non-existent store
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Send `GET /api/stores/00000000-0000-0000-0000-000000000000`
- **Expected Result:** HTTP 404; `code:"NOT_FOUND"`
- **Pass Criteria:** Status 404

---

**TC-STR-012**
- **Title:** Store timezone field accepts valid IANA timezone
- **Priority:** P3
- **Preconditions:** Admin token
- **Steps:**
  1. Create store with `"timezone":"America/New_York"`
- **Expected Result:** HTTP 201; store created with timezone stored
- **Pass Criteria:** `GET /api/stores/<id>` returns `timezone:"America/New_York"`

---

**TC-STR-013**
- **Title:** ERP store code is saved and retrievable
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. Create store with `"erpStoreCode":"ERP-SYD-001"`
  2. `GET /api/stores/<id>`
- **Expected Result:** `erpStoreCode:"ERP-SYD-001"` in response
- **Pass Criteria:** ERP code matches

---

**TC-STR-014**
- **Title:** Store Manager cannot delete a store
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. Send `DELETE /api/stores/<storeId>` with STORE_MANAGER token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-STR-015**
- **Title:** Store update preserves unspecified fields
- **Priority:** P3
- **Preconditions:** Store with all fields set
- **Steps:**
  1. `PUT /api/stores/<id>` with only `{"name":"New Name"}`
- **Expected Result:** HTTP 200; `name` updated; all other fields (city, timezone, erpCode) unchanged
- **Pass Criteria:** Other fields unchanged

---

## 4. Zone Management

---

**TC-ZONE-001**
- **Title:** Admin creates a zone in a store
- **Priority:** P1
- **Preconditions:** Admin token; store exists
- **Steps:**
  1. `POST /api/stores/<storeId>/zones` with `{"zoneCode":"FLOOR-A","name":"Ground Floor","zoneType":"floor","displayOrder":1}`
- **Expected Result:** HTTP 201; zone created with `active:true`
- **Pass Criteria:** Status 201; zone in `GET /api/stores/<storeId>/zones`

---

**TC-ZONE-002**
- **Title:** Zone list is public
- **Priority:** P2
- **Preconditions:** Zone exists
- **Steps:**
  1. `GET /api/stores/<storeId>/zones` — no auth header
- **Expected Result:** HTTP 200; zone list returned
- **Pass Criteria:** Status 200

---

**TC-ZONE-003**
- **Title:** Store Manager can create a zone
- **Priority:** P2
- **Preconditions:** STORE_MANAGER token for the same store
- **Steps:**
  1. `POST /api/stores/<storeId>/zones` with STORE_MANAGER token
- **Expected Result:** HTTP 201
- **Pass Criteria:** Status 201

---

**TC-ZONE-004**
- **Title:** Zone types are validated (floor/backroom/fitting_room/stockroom/display)
- **Priority:** P3
- **Preconditions:** Admin token
- **Steps:**
  1. Create zone with `"zoneType":"parking_lot"` (invalid)
- **Expected Result:** HTTP 400; validation error for `zoneType`
- **Pass Criteria:** Status 400

---

**TC-ZONE-005**
- **Title:** Admin updates a zone
- **Priority:** P2
- **Preconditions:** Admin token; zone exists
- **Steps:**
  1. `PUT /api/stores/<storeId>/zones/<zoneId>` with `{"name":"Updated Name","displayOrder":5}`
- **Expected Result:** HTTP 200; `name` and `displayOrder` updated
- **Pass Criteria:** Updated values in response

---

**TC-ZONE-006**
- **Title:** Store Associate cannot create a zone
- **Priority:** P2
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. `POST /api/stores/<storeId>/zones` with STORE_ASSOCIATE token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-ZONE-007**
- **Title:** Zone can be deactivated via update
- **Priority:** P3
- **Preconditions:** Admin token; zone exists
- **Steps:**
  1. `PUT /api/stores/<storeId>/zones/<zoneId>` with `{"active":false}`
- **Expected Result:** HTTP 200; `active:false`
- **Pass Criteria:** Zone `active:false`

---

**TC-ZONE-008**
- **Title:** Zones for non-existent store returns 404
- **Priority:** P3
- **Preconditions:** None
- **Steps:**
  1. `GET /api/stores/00000000-0000-0000-0000-000000000000/zones`
- **Expected Result:** HTTP 404 or empty list
- **Pass Criteria:** No server error (5xx)

---

**TC-ZONE-009**
- **Title:** Multiple zones can be created in one store
- **Priority:** P2
- **Preconditions:** Admin token; store exists
- **Steps:**
  1. Create zones: FLOOR-A, FLOOR-B, BACKROOM-1
  2. `GET /api/stores/<storeId>/zones`
- **Expected Result:** All 3 zones returned
- **Pass Criteria:** 3 zones in response

---

**TC-ZONE-010**
- **Title:** displayOrder controls zone sort order
- **Priority:** P4
- **Preconditions:** Multiple zones with different displayOrder values
- **Steps:**
  1. `GET /api/stores/<storeId>/zones`
- **Expected Result:** Zones returned sorted by `displayOrder` ascending
- **Pass Criteria:** `displayOrder` ascending in response

---

## 5. Product Management

---

**TC-PROD-001**
- **Title:** Admin creates a product with RFID enabled
- **Priority:** P1
- **Preconditions:** Admin token
- **Steps:**
  1. `POST /api/products` with `{"sku":"TEST-001","name":"Test Jeans","rfidEnabled":true,"unitOfMeasure":"EACH"}`
- **Expected Result:** HTTP 201; product created with `rfidEnabled:true`, `active:true`
- **Pass Criteria:** Status 201; product in `GET /api/products`

---

**TC-PROD-002**
- **Title:** Product list is public
- **Priority:** P2
- **Preconditions:** Products exist
- **Steps:**
  1. `GET /api/products` — no auth header
- **Expected Result:** HTTP 200; product list returned
- **Pass Criteria:** Status 200

---

**TC-PROD-003**
- **Title:** Product search filters by name
- **Priority:** P2
- **Preconditions:** Products including "Denim Jeans" exist
- **Steps:**
  1. `GET /api/products?search=denim`
- **Expected Result:** HTTP 200; only products with "denim" in name/SKU/brand returned
- **Pass Criteria:** All returned products match search term

---

**TC-PROD-004**
- **Title:** Get product by SKU
- **Priority:** P2
- **Preconditions:** Product with SKU "TEST-001" exists
- **Steps:**
  1. `GET /api/products/by-sku/TEST-001`
- **Expected Result:** HTTP 200; correct product returned
- **Pass Criteria:** `sku:"TEST-001"` in response

---

**TC-PROD-005**
- **Title:** EPC tag association with product
- **Priority:** P1
- **Preconditions:** Admin token; product exists
- **Steps:**
  1. `POST /api/products/<productId>/epc?epc=3034257BF400B71400000099`
- **Expected Result:** HTTP 201; EPC associated
- **Pass Criteria:** `GET /api/products/epc/3034257BF400B71400000099` returns the product

---

**TC-PROD-006**
- **Title:** EPC lookup returns product with cache indicator
- **Priority:** P1
- **Preconditions:** EPC registered (TC-PROD-005)
- **Steps:**
  1. `GET /api/products/epc/3034257BF400B71400000099` (first call — not cached)
  2. `GET /api/products/epc/3034257BF400B71400000099` (second call — should be cached)
- **Expected Result:** Both return HTTP 200; second call has `fromCache:true`
- **Pass Criteria:** Second call `fromCache:true`

---

**TC-PROD-007**
- **Title:** EPC lookup for unknown EPC returns 404
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. `GET /api/products/epc/FFFFFFFFFFFFFFFFFFFFFFFF`
- **Expected Result:** HTTP 404
- **Pass Criteria:** Status 404

---

**TC-PROD-008**
- **Title:** Duplicate SKU on create fails
- **Priority:** P1
- **Preconditions:** Product "TEST-001" exists
- **Steps:**
  1. `POST /api/products` with `"sku":"TEST-001"`
- **Expected Result:** HTTP 409
- **Pass Criteria:** Status 409

---

**TC-PROD-009**
- **Title:** Admin updates a product
- **Priority:** P2
- **Preconditions:** Admin token; product exists
- **Steps:**
  1. `PUT /api/products/<id>` with `{"name":"Updated Name","rfidEnabled":false}`
- **Expected Result:** HTTP 200; name and rfidEnabled updated
- **Pass Criteria:** Updated values in response

---

**TC-PROD-010**
- **Title:** Admin deactivates a product
- **Priority:** P2
- **Preconditions:** Admin token; product exists
- **Steps:**
  1. `PUT /api/products/<id>` with `{"active":false}`
- **Expected Result:** HTTP 200; `active:false`
- **Pass Criteria:** Product `active:false`

---

**TC-PROD-011**
- **Title:** Non-admin cannot create products
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. `POST /api/products` with STORE_MANAGER token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-PROD-012**
- **Title:** Product list pagination works correctly
- **Priority:** P3
- **Preconditions:** 10+ products exist
- **Steps:**
  1. `GET /api/products?page=0&size=5`
  2. `GET /api/products?page=1&size=5`
- **Expected Result:** Page 0 and Page 1 return different 5 products each; total > 5
- **Pass Criteria:** No overlap between pages

---

**TC-PROD-013**
- **Title:** STORE_MANAGER can associate EPC to product
- **Priority:** P2
- **Preconditions:** STORE_MANAGER token; product exists
- **Steps:**
  1. `POST /api/products/<id>/epc?epc=AAAA1111BBBB2222CCCC3333`
- **Expected Result:** HTTP 201
- **Pass Criteria:** Status 201

---

**TC-PROD-014**
- **Title:** Creating product with invalid EPC format fails
- **Priority:** P3
- **Preconditions:** Admin token; product exists
- **Steps:**
  1. `POST /api/products/<id>/epc?epc=INVALID-EPC` (not hex)
- **Expected Result:** HTTP 400; validation error
- **Pass Criteria:** Status 400

---

**TC-PROD-015**
- **Title:** Get product by non-existent ID returns 404
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. `GET /api/products/00000000-0000-0000-0000-000000000000`
- **Expected Result:** HTTP 404
- **Pass Criteria:** Status 404

---

## 6. Inventory

---

**TC-INV-001**
- **Title:** Store Manager views inventory state for their store
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token; inventory data exists for their store
- **Steps:**
  1. `GET /api/inventory/state?storeId=<theirStoreId>`
- **Expected Result:** HTTP 200; list of inventory state records with `productId`, `quantityOnHand`, `quantityExpected`, `accuracyPct`
- **Pass Criteria:** Status 200; data present

---

**TC-INV-002**
- **Title:** Store Manager cannot view inventory for another store
- **Priority:** P1
- **Preconditions:** STORE_MANAGER for Store A; Store B exists
- **Steps:**
  1. `GET /api/inventory/state?storeId=<storeBId>` with Store A manager token
- **Expected Result:** HTTP 403 or empty result (no Store B data leaked)
- **Pass Criteria:** Store B inventory not returned

---

**TC-INV-003**
- **Title:** Low accuracy endpoint returns products below threshold
- **Priority:** P2
- **Preconditions:** Inventory data with some products below 95% accuracy
- **Steps:**
  1. `GET /api/inventory/low-accuracy?storeId=<id>&threshold=95.0`
- **Expected Result:** HTTP 200; all returned records have `accuracyPct < 95`
- **Pass Criteria:** All records below threshold

---

**TC-INV-004**
- **Title:** Low accuracy with custom threshold
- **Priority:** P3
- **Preconditions:** Inventory data with varied accuracy values
- **Steps:**
  1. `GET /api/inventory/low-accuracy?storeId=<id>&threshold=80.0`
- **Expected Result:** HTTP 200; only records with accuracy < 80
- **Pass Criteria:** All returned records `accuracyPct < 80`

---

**TC-INV-005**
- **Title:** EPC summary returns counts by status
- **Priority:** P2
- **Preconditions:** EPC registry has entries with various statuses
- **Steps:**
  1. `GET /api/inventory/epc-summary?storeId=<id>`
- **Expected Result:** HTTP 200; `in_store`, `missing`, `sold`, `damaged` counts returned
- **Pass Criteria:** All status keys present; values are non-negative integers

---

**TC-INV-006**
- **Title:** Inventory state reflects completed SOH session
- **Priority:** P1
- **Preconditions:** SOH session completed with known EPC reads
- **Steps:**
  1. Complete a SOH session with 3 known EPC reads
  2. Wait 5 seconds for Kafka processing
  3. `GET /api/inventory/state?storeId=<id>`
- **Expected Result:** Products matching the EPCs have updated `quantityOnHand` and `lastCountedAt`
- **Pass Criteria:** `quantityOnHand` reflects the scan; `lastCountedAt` is recent

---

**TC-INV-007**
- **Title:** Store Associate cannot access inventory endpoints
- **Priority:** P2
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. `GET /api/inventory/state?storeId=<id>` with STORE_ASSOCIATE token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-INV-008**
- **Title:** Inventory state requires storeId parameter
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. `GET /api/inventory/state` (no storeId)
- **Expected Result:** HTTP 400; missing required parameter error
- **Pass Criteria:** Status 400

---

**TC-INV-009**
- **Title:** Accuracy percentage is correctly calculated
- **Priority:** P1
- **Preconditions:** Product expected qty = 10; SOH session found qty = 8
- **Steps:**
  1. After SOH session completing with 8 of 10 expected EPCs scanned
  2. `GET /api/inventory/state?storeId=<id>` for that product
- **Expected Result:** `accuracyPct: 80.00` (8/10 × 100)
- **Pass Criteria:** Accuracy = 80.00

---

**TC-INV-010**
- **Title:** Admin can view inventory for any store
- **Priority:** P2
- **Preconditions:** Admin token; inventory in multiple stores
- **Steps:**
  1. `GET /api/inventory/state?storeId=<storeAId>` — expect data
  2. `GET /api/inventory/state?storeId=<storeBId>` — expect data
- **Expected Result:** Both return HTTP 200 with respective store data
- **Pass Criteria:** Both requests succeed with different data

---

## 7. SOH Sessions (Cycle Count)

---

**TC-SOH-001**
- **Title:** Store Associate starts a full-store SOH session
- **Priority:** P1
- **Preconditions:** STORE_ASSOCIATE token; store exists
- **Steps:**
  1. `POST /api/soh/sessions` with `{"storeId":"<id>","sessionType":"full_store","notes":"Weekly count"}`
- **Expected Result:** HTTP 201; session created with `status:"created"`, `storeId` correct
- **Pass Criteria:** Status 201; `status:"created"`

---

**TC-SOH-002**
- **Title:** Store Associate starts a spot-check session for a specific zone
- **Priority:** P2
- **Preconditions:** STORE_ASSOCIATE token; store + zone exist
- **Steps:**
  1. `POST /api/soh/sessions` with `{"storeId":"<id>","zoneId":"<zoneId>","sessionType":"spot_check"}`
- **Expected Result:** HTTP 201; `zoneId` present in response
- **Pass Criteria:** Status 201; `zoneId` matches

---

**TC-SOH-003**
- **Title:** Completing a session generates accuracy result
- **Priority:** P1
- **Preconditions:** Active SOH session with RFID reads (TC-SOH-001 + TC-RFID-001)
- **Steps:**
  1. `POST /api/soh/sessions/<sessionId>/complete`
- **Expected Result:** HTTP 200; `SohResultResponse` with `accuracyPct`, `totalProductsCounted`, `varianceCount`, `resultGeneratedAt`
- **Pass Criteria:** All result fields present; `accuracyPct` between 0–100

---

**TC-SOH-004**
- **Title:** Session list filtered by status
- **Priority:** P2
- **Preconditions:** Sessions in various statuses exist
- **Steps:**
  1. `GET /api/soh/sessions?storeId=<id>&status=completed`
- **Expected Result:** HTTP 200; only sessions with `status:"completed"` returned
- **Pass Criteria:** All returned sessions have `status:"completed"`

---

**TC-SOH-005**
- **Title:** Cancelling a session sets status to cancelled
- **Priority:** P2
- **Preconditions:** Active session exists
- **Steps:**
  1. `POST /api/soh/sessions/<id>/cancel?reason=Battery+died`
- **Expected Result:** HTTP 200; no result generated
- **Pass Criteria:** `GET /api/soh/sessions/<id>` shows `status:"cancelled"`

---

**TC-SOH-006**
- **Title:** Completed session cannot be cancelled
- **Priority:** P2
- **Preconditions:** Completed session
- **Steps:**
  1. `POST /api/soh/sessions/<id>/cancel`
- **Expected Result:** HTTP 422; `code:"BUSINESS_ERROR"`
- **Pass Criteria:** Status 422; session remains `completed`

---

**TC-SOH-007**
- **Title:** Session list returns only current store's sessions for Store Manager
- **Priority:** P1
- **Preconditions:** Sessions in Store A and Store B; STORE_MANAGER for Store A
- **Steps:**
  1. `GET /api/soh/sessions?storeId=<storeAId>` with Store A manager token
- **Expected Result:** HTTP 200; only Store A sessions
- **Pass Criteria:** All sessions have `storeId` = Store A

---

**TC-SOH-008**
- **Title:** SOH session list is paginated
- **Priority:** P3
- **Preconditions:** 5+ sessions exist
- **Steps:**
  1. `GET /api/soh/sessions?storeId=<id>&page=0&size=2`
- **Expected Result:** 2 sessions returned; `totalElements >= 5`
- **Pass Criteria:** Pagination metadata correct

---

**TC-SOH-009**
- **Title:** Session EPC count updates after RFID ingest
- **Priority:** P1
- **Preconditions:** Session in `created` status; Kafka processing healthy
- **Steps:**
  1. `POST /api/rfid/ingest/batch` with 5 EPCs for this session
  2. Wait 5 seconds
  3. `GET /api/soh/sessions/<sessionId>`
- **Expected Result:** `totalEpcReads >= 5`; `uniqueEpcCount >= 1`; `status:"in_progress"`
- **Pass Criteria:** Counts updated; status moved to in_progress

---

**TC-SOH-010**
- **Title:** REFILL_ASSOCIATE cannot start a SOH session
- **Priority:** P2
- **Preconditions:** REFILL_ASSOCIATE token
- **Steps:**
  1. `POST /api/soh/sessions` with REFILL_ASSOCIATE token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-SOH-011**
- **Title:** Creating session with non-existent storeId fails
- **Priority:** P3
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. `POST /api/soh/sessions` with `"storeId":"00000000-0000-0000-0000-000000000000"`
- **Expected Result:** HTTP 404 or 422
- **Pass Criteria:** No session created; 4xx response

---

**TC-SOH-012**
- **Title:** SOH result contains overcount and undercount breakdown
- **Priority:** P2
- **Preconditions:** SOH session where some products have more than expected and some less
- **Steps:**
  1. Complete session with mixed variance
  2. Check result
- **Expected Result:** `overcountItems` and `undercountItems` both > 0; `varianceCount = overcountItems + undercountItems`
- **Pass Criteria:** Math checks out

---

**TC-SOH-013**
- **Title:** 100% accuracy result when all expected EPCs found
- **Priority:** P2
- **Preconditions:** Session where all expected EPCs are scanned
- **Steps:**
  1. Complete session
- **Expected Result:** `accuracyPct:100.00`, `varianceCount:0`
- **Pass Criteria:** Accuracy = 100

---

**TC-SOH-014**
- **Title:** Session storeId required on create
- **Priority:** P2
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. `POST /api/soh/sessions` with no `storeId` field
- **Expected Result:** HTTP 400; validation error
- **Pass Criteria:** Status 400

---

**TC-SOH-015**
- **Title:** Get session by ID returns correct session
- **Priority:** P2
- **Preconditions:** Session exists
- **Steps:**
  1. `GET /api/soh/sessions/<sessionId>`
- **Expected Result:** HTTP 200; `id` matches requested UUID
- **Pass Criteria:** Correct session returned

---

**TC-SOH-016**
- **Title:** Get non-existent session returns 404
- **Priority:** P3
- **Preconditions:** None
- **Steps:**
  1. `GET /api/soh/sessions/00000000-0000-0000-0000-000000000000`
- **Expected Result:** HTTP 404
- **Pass Criteria:** Status 404

---

**TC-SOH-017**
- **Title:** Session cannot be completed twice
- **Priority:** P2
- **Preconditions:** Already-completed session
- **Steps:**
  1. `POST /api/soh/sessions/<id>/complete` again
- **Expected Result:** HTTP 422; `code:"BUSINESS_ERROR"`
- **Pass Criteria:** Status 422

---

**TC-SOH-018**
- **Title:** Session status transitions follow defined flow
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Create session → status = `created`
  2. Submit RFID batch → status = `in_progress`
  3. Complete session → status = `completed`
- **Expected Result:** Each transition correct
- **Pass Criteria:** All status transitions match spec

---

**TC-SOH-019**
- **Title:** Multiple concurrent sessions can exist for different zones
- **Priority:** P3
- **Preconditions:** Store with 2 zones
- **Steps:**
  1. Create session for Zone A
  2. Create session for Zone B
- **Expected Result:** Both sessions exist simultaneously
- **Pass Criteria:** Both sessions in session list; different `zoneId`

---

**TC-SOH-020**
- **Title:** Session notes are stored and returned
- **Priority:** P4
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. Create session with `"notes":"Post-sale recount, check displays"`
  2. `GET /api/soh/sessions/<id>`
- **Expected Result:** `notes:"Post-sale recount, check displays"` in response
- **Pass Criteria:** Notes match

---

## 8. Refill Tasks

---

**TC-RFILL-001**
- **Title:** Store Manager creates a manual replenishment task
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token; product + store exist
- **Steps:**
  1. `POST /api/refill/tasks` with `{"storeId":"<id>","taskType":"replenishment","priority":3,"items":[{"productId":"<id>","requestedQuantity":20}]}`
- **Expected Result:** HTTP 201; task created with `status:"pending"`, item with `status:"pending"`
- **Pass Criteria:** Status 201; task in task list

---

**TC-RFILL-002**
- **Title:** Task assignment notifies the associate
- **Priority:** P1
- **Preconditions:** Task exists; REFILL_ASSOCIATE user exists
- **Steps:**
  1. `POST /api/refill/tasks/<id>/assign?assignedTo=<associateId>`
- **Expected Result:** HTTP 200; task `status:"assigned"`; associate notified (WebSocket event triggered)
- **Pass Criteria:** Status 200; task `status:"assigned"`

---

**TC-RFILL-003**
- **Title:** Full item fulfilment completes the task
- **Priority:** P1
- **Preconditions:** Task assigned with 1 item (qty 10)
- **Steps:**
  1. `PATCH /api/refill/tasks/<taskId>/items/<itemId>/fulfil?quantity=10`
- **Expected Result:** HTTP 200; item `status:"fulfilled"`; task `status:"completed"`
- **Pass Criteria:** Task auto-completes

---

**TC-RFILL-004**
- **Title:** Partial item fulfilment sets partial status
- **Priority:** P2
- **Preconditions:** Task assigned with 1 item (qty 10)
- **Steps:**
  1. `PATCH /api/refill/tasks/<taskId>/items/<itemId>/fulfil?quantity=5`
- **Expected Result:** HTTP 200; item `status:"partial"`; task `status:"completed"` (single item terminal)
- **Pass Criteria:** Item = partial; task = completed

---

**TC-RFILL-005**
- **Title:** Task with multiple items completes only when all items terminal
- **Priority:** P2
- **Preconditions:** Task with 2 items
- **Steps:**
  1. Fulfil item 1 (quantity = requested)
  2. Check task status → should still be `in_progress`
  3. Fulfil item 2 (quantity = requested)
  4. Check task status → should be `completed`
- **Expected Result:** Task completes only after all items resolved
- **Pass Criteria:** Task `completed` only after step 3

---

**TC-RFILL-006**
- **Title:** Refill Associate can fulfil their assigned task
- **Priority:** P1
- **Preconditions:** REFILL_ASSOCIATE token; task assigned to them
- **Steps:**
  1. `PATCH /api/refill/tasks/<taskId>/items/<itemId>/fulfil?quantity=5` with REFILL_ASSOCIATE token
- **Expected Result:** HTTP 200
- **Pass Criteria:** Status 200

---

**TC-RFILL-007**
- **Title:** STORE_ASSOCIATE cannot view refill tasks
- **Priority:** P2
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. `GET /api/refill/tasks?storeId=<id>` with STORE_ASSOCIATE token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-RFILL-008**
- **Title:** Store Manager cancels a pending task
- **Priority:** P2
- **Preconditions:** STORE_MANAGER token; pending task
- **Steps:**
  1. `POST /api/refill/tasks/<id>/cancel?reason=Stock+arrived`
- **Expected Result:** HTTP 200; `status:"cancelled"`
- **Pass Criteria:** `GET /api/refill/tasks/<id>` shows `status:"cancelled"`

---

**TC-RFILL-009**
- **Title:** Completed task cannot be cancelled
- **Priority:** P2
- **Preconditions:** Completed task
- **Steps:**
  1. `POST /api/refill/tasks/<id>/cancel`
- **Expected Result:** HTTP 422
- **Pass Criteria:** Status 422

---

**TC-RFILL-010**
- **Title:** Task list filtered by status
- **Priority:** P2
- **Preconditions:** Tasks in various statuses
- **Steps:**
  1. `GET /api/refill/tasks?storeId=<id>&status=pending`
- **Expected Result:** Only `pending` tasks returned
- **Pass Criteria:** All returned tasks `status:"pending"`

---

**TC-RFILL-011**
- **Title:** Task priority 1 is higher urgency than priority 10
- **Priority:** P3
- **Preconditions:** Two tasks with priority 1 and priority 10
- **Steps:**
  1. `GET /api/refill/tasks?storeId=<id>`
- **Expected Result:** Priority 1 task appears before priority 10
- **Pass Criteria:** Task list sorted by priority ascending

---

**TC-RFILL-012**
- **Title:** SOH trigger automatically creates a refill task on low accuracy
- **Priority:** P1
- **Preconditions:** SOH session completed with product below 95% accuracy threshold
- **Steps:**
  1. Complete a SOH session where a product's accuracy < 95%
  2. `GET /api/refill/tasks?storeId=<id>`
- **Expected Result:** Refill task exists with `source:"soh_trigger"` for the low-accuracy product
- **Pass Criteria:** Auto-created task visible with correct source

---

**TC-RFILL-013**
- **Title:** Task creation requires at least one line item
- **Priority:** P3
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. `POST /api/refill/tasks` with `"items":[]`
- **Expected Result:** HTTP 400; validation error
- **Pass Criteria:** Status 400

---

**TC-RFILL-014**
- **Title:** Task item requestedQuantity must be positive
- **Priority:** P3
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. `POST /api/refill/tasks` with item `"requestedQuantity":-5`
- **Expected Result:** HTTP 400; validation error
- **Pass Criteria:** Status 400

---

**TC-RFILL-015**
- **Title:** Refill Associate cannot assign tasks
- **Priority:** P2
- **Preconditions:** REFILL_ASSOCIATE token
- **Steps:**
  1. `POST /api/refill/tasks/<id>/assign?assignedTo=<userId>` with REFILL_ASSOCIATE token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-RFILL-016**
- **Title:** Get task by ID returns full task with items
- **Priority:** P2
- **Preconditions:** Task with items exists
- **Steps:**
  1. `GET /api/refill/tasks/<taskId>`
- **Expected Result:** HTTP 200; `items` array populated with line items
- **Pass Criteria:** `items` not empty; each item has `productId`, `requestedQuantity`, `status`

---

**TC-RFILL-017**
- **Title:** Task completedAt timestamp set on completion
- **Priority:** P3
- **Preconditions:** Task that becomes completed
- **Steps:**
  1. Fulfil all items
  2. `GET /api/refill/tasks/<taskId>`
- **Expected Result:** `completedAt` is a valid timestamp
- **Pass Criteria:** `completedAt` not null

---

**TC-RFILL-018**
- **Title:** Task with dueDate stores and returns the date
- **Priority:** P3
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. Create task with `"dueDate":"2026-12-31"`
  2. `GET /api/refill/tasks/<id>`
- **Expected Result:** `dueDate:"2026-12-31"` in response
- **Pass Criteria:** Due date preserved

---

**TC-RFILL-019**
- **Title:** Store Manager sees only their store's tasks
- **Priority:** P1
- **Preconditions:** Tasks in Store A and Store B; STORE_MANAGER for Store A
- **Steps:**
  1. `GET /api/refill/tasks?storeId=<storeAId>` with Store A manager
- **Expected Result:** Only Store A tasks
- **Pass Criteria:** All returned tasks `storeId` = Store A

---

**TC-RFILL-020**
- **Title:** ERP-sourced task has source = erp
- **Priority:** P2
- **Preconditions:** ERP integration pushes a task (or manually create with `"source":"erp"`)
- **Steps:**
  1. `GET /api/refill/tasks?storeId=<id>` and find ERP task
- **Expected Result:** Task has `source:"erp"`
- **Pass Criteria:** `source` field = "erp"

---

## 9. RFID Ingest

---

**TC-RFID-001**
- **Title:** Submit a valid RFID batch — accepted and published to Kafka
- **Priority:** P1
- **Preconditions:** Valid session; STORE_ASSOCIATE token
- **Steps:**
  1. `POST /api/rfid/ingest/batch` with `rfidSessionId`, `storeId`, and 3 EPC reads
- **Expected Result:** HTTP 202; `{"published":3}`
- **Pass Criteria:** Status 202; `published:3`

---

**TC-RFID-002**
- **Title:** Batch with empty reads array fails validation
- **Priority:** P2
- **Preconditions:** Valid token
- **Steps:**
  1. `POST /api/rfid/ingest/batch` with `"reads":[]`
- **Expected Result:** HTTP 400; validation error for `reads`
- **Pass Criteria:** Status 400

---

**TC-RFID-003**
- **Title:** EPC with invalid format fails validation
- **Priority:** P2
- **Preconditions:** Valid token
- **Steps:**
  1. Submit batch with `"epc":"INVALID-NOT-HEX"`
- **Expected Result:** HTTP 400; EPC format error
- **Pass Criteria:** Status 400

---

**TC-RFID-004**
- **Title:** Duplicate EPCs in the same batch are published individually but deduplicated in processing
- **Priority:** P1
- **Preconditions:** Active session
- **Steps:**
  1. Submit batch with the same EPC 5 times in `reads` array
  2. Wait 5 seconds
  3. Check session `uniqueEpcCount`
- **Expected Result:** `published:5` (all published); session `uniqueEpcCount:1` (deduplicated)
- **Pass Criteria:** Deduplication occurs at processing layer

---

**TC-RFID-005**
- **Title:** Batch requires rfidSessionId
- **Priority:** P2
- **Preconditions:** Valid token
- **Steps:**
  1. Submit batch with no `rfidSessionId`
- **Expected Result:** HTTP 400; validation error
- **Pass Criteria:** Status 400

---

**TC-RFID-006**
- **Title:** Batch requires storeId
- **Priority:** P2
- **Preconditions:** Valid token
- **Steps:**
  1. Submit batch with no `storeId`
- **Expected Result:** HTTP 400
- **Pass Criteria:** Status 400

---

**TC-RFID-007**
- **Title:** Large batch of 1000 EPCs is processed successfully
- **Priority:** P2
- **Preconditions:** Active session; valid token
- **Steps:**
  1. Submit batch with 1000 unique EPC reads
- **Expected Result:** HTTP 202; `published:1000`; no timeout
- **Pass Criteria:** Status 202 within 5 seconds

---

**TC-RFID-008**
- **Title:** DLQ captures failed processing events
- **Priority:** P2
- **Preconditions:** Admin token; Submit an unknown EPC that has no product mapping
- **Steps:**
  1. Submit batch with EPC `FFFFFFFFFFFFFFFFFFFFFFFF` (not registered to any product)
  2. Wait 5 seconds
  3. `GET /api/rfid/dlq`
- **Expected Result:** DLQ contains entry for the unknown EPC with error message
- **Pass Criteria:** DLQ entry exists; `errorMessage` mentions EPC not found

---

**TC-RFID-009**
- **Title:** DLQ replay re-publishes the event
- **Priority:** P3
- **Preconditions:** DLQ entry exists (TC-RFID-008)
- **Steps:**
  1. Register the EPC to a product first
  2. `POST /api/rfid/dlq/<key>/replay`
- **Expected Result:** HTTP 200; event re-published and processed successfully
- **Pass Criteria:** Status 200; DLQ entry removed or resolved

---

**TC-RFID-010**
- **Title:** RFID batch without auth is rejected
- **Priority:** P1
- **Preconditions:** None
- **Steps:**
  1. `POST /api/rfid/ingest/batch` with no Authorization header
- **Expected Result:** HTTP 401
- **Pass Criteria:** Status 401

---

**TC-RFID-011**
- **Title:** Correlation ID header is accepted and processed
- **Priority:** P3
- **Preconditions:** Valid token
- **Steps:**
  1. Submit batch with `X-Correlation-Id: test-correlation-123`
- **Expected Result:** HTTP 202; no error
- **Pass Criteria:** Status 202

---

**TC-RFID-012**
- **Title:** RFID reads update session to in_progress status
- **Priority:** P1
- **Preconditions:** Session in `created` status
- **Steps:**
  1. Submit RFID batch for the session
  2. Wait 5 seconds
  3. `GET /api/soh/sessions/<id>`
- **Expected Result:** `status:"in_progress"`
- **Pass Criteria:** Status updated

---

**TC-RFID-013**
- **Title:** readAt timestamp is optional in batch
- **Priority:** P3
- **Preconditions:** Valid token and session
- **Steps:**
  1. Submit batch with reads that omit the `readAt` field
- **Expected Result:** HTTP 202; reads accepted; server assigns ingestion timestamp
- **Pass Criteria:** Status 202

---

**TC-RFID-014**
- **Title:** RSSI value is stored with the read
- **Priority:** P4
- **Preconditions:** Known EPC registered to a product
- **Steps:**
  1. Submit batch with `"rssi":-72.5` for an EPC
  2. Check `rfid.rfid_reads` table for the read
- **Expected Result:** `rssi:-72.5` stored in database
- **Pass Criteria:** RSSI value matches

---

**TC-RFID-015**
- **Title:** Rate limit applied to RFID ingest endpoint
- **Priority:** P3
- **Preconditions:** Valid token
- **Steps:**
  1. Submit 250 rapid requests to `/api/rfid/ingest/batch` in under 60 seconds (Nginx burst limit is 200)
- **Expected Result:** First 200 succeed; subsequent requests receive 503 from rate limiter
- **Pass Criteria:** Rate limiting activates after burst threshold

---

## 10. Reporting

---

**TC-RPT-001**
- **Title:** Store Manager retrieves KPI daily history
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token; KPI data exists (after SOH session)
- **Steps:**
  1. `GET /api/reporting/kpi/daily?storeId=<id>&size=7`
- **Expected Result:** HTTP 200; paginated list of KpiDaily records
- **Pass Criteria:** Status 200; records contain all KPI fields

---

**TC-RPT-002**
- **Title:** KPI range query returns data between from and to dates
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token; KPI data for past week
- **Steps:**
  1. `GET /api/reporting/kpi/range?storeId=<id>&from=2026-06-01&to=2026-06-07`
- **Expected Result:** HTTP 200; records only for dates 2026-06-01 to 2026-06-07
- **Pass Criteria:** All records within date range

---

**TC-RPT-003**
- **Title:** Admin triggers manual KPI aggregation
- **Priority:** P2
- **Preconditions:** Admin token
- **Steps:**
  1. `POST /api/reporting/kpi/aggregate?storeId=<id>&date=2026-06-04`
- **Expected Result:** HTTP 200; aggregation triggered
- **Pass Criteria:** Status 200; subsequent KPI query shows data for that date

---

**TC-RPT-004**
- **Title:** Non-admin cannot trigger KPI aggregation
- **Priority:** P2
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. `POST /api/reporting/kpi/aggregate?storeId=<id>&date=2026-06-04`
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-RPT-005**
- **Title:** Store Manager cannot see KPIs for another store
- **Priority:** P1
- **Preconditions:** STORE_MANAGER for Store A; Store B has KPI data
- **Steps:**
  1. `GET /api/reporting/kpi/daily?storeId=<storeBId>` with Store A manager
- **Expected Result:** HTTP 403 or empty result
- **Pass Criteria:** Store B KPI data not returned

---

**TC-RPT-006**
- **Title:** KPI fields are correctly calculated
- **Priority:** P1
- **Preconditions:** SOH session and refill tasks completed on a known date
- **Steps:**
  1. Trigger aggregation for that date
  2. `GET /api/reporting/kpi/range?from=<date>&to=<date>`
- **Expected Result:** `sohSessionCount`, `refillTasksCompleted`, `inventoryAccuracyPct` match actual data
- **Pass Criteria:** KPI values match expectations

---

**TC-RPT-007**
- **Title:** KPI range with from > to returns 400
- **Priority:** P3
- **Preconditions:** Admin token
- **Steps:**
  1. `GET /api/reporting/kpi/range?storeId=<id>&from=2026-06-10&to=2026-06-01`
- **Expected Result:** HTTP 400; invalid date range error
- **Pass Criteria:** Status 400

---

**TC-RPT-008**
- **Title:** STORE_ASSOCIATE cannot access reports
- **Priority:** P2
- **Preconditions:** STORE_ASSOCIATE token
- **Steps:**
  1. `GET /api/reporting/kpi/daily?storeId=<id>` with STORE_ASSOCIATE token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-RPT-009**
- **Title:** KPI daily pagination works correctly
- **Priority:** P3
- **Preconditions:** 30+ days of KPI data
- **Steps:**
  1. `GET /api/reporting/kpi/daily?storeId=<id>&page=0&size=7`
  2. `GET /api/reporting/kpi/daily?storeId=<id>&page=1&size=7`
- **Expected Result:** Different records on each page; no overlap
- **Pass Criteria:** No duplicate records across pages

---

**TC-RPT-010**
- **Title:** Refill completion rate calculated correctly
- **Priority:** P2
- **Preconditions:** 5 refill tasks created, 3 completed on a known date
- **Steps:**
  1. Trigger aggregation
  2. Check `refillCompletionRatePct`
- **Expected Result:** `refillCompletionRatePct: 60.00` (3/5 × 100)
- **Pass Criteria:** Value = 60.00

---

## 11. ERP Integration

---

**TC-ERP-001**
- **Title:** Admin triggers product master sync
- **Priority:** P2
- **Preconditions:** Admin token; ERP service configured
- **Steps:**
  1. `POST /api/erp/admin/sync/products`
- **Expected Result:** HTTP 200; response with `syncId`, `fetched`, `published` counts
- **Pass Criteria:** Status 200; `published > 0`

---

**TC-ERP-002**
- **Title:** Non-admin cannot trigger ERP sync
- **Priority:** P1
- **Preconditions:** STORE_MANAGER token
- **Steps:**
  1. `POST /api/erp/admin/sync/products` with STORE_MANAGER token
- **Expected Result:** HTTP 403
- **Pass Criteria:** Status 403

---

**TC-ERP-003**
- **Title:** Admin triggers expected inventory sync
- **Priority:** P2
- **Preconditions:** Admin token; ERP configured
- **Steps:**
  1. `POST /api/erp/admin/sync/inventory`
- **Expected Result:** HTTP 200; `fetched` and `published` > 0
- **Pass Criteria:** Status 200

---

**TC-ERP-004**
- **Title:** Sync logs are written after each sync
- **Priority:** P2
- **Preconditions:** At least one sync has run
- **Steps:**
  1. `GET /api/erp/admin/sync/logs`
- **Expected Result:** HTTP 200; log entries with `syncType`, `status`, `completedAt`
- **Pass Criteria:** Status 200; entries present

---

**TC-ERP-005**
- **Title:** Sync logs filterable by syncType
- **Priority:** P3
- **Preconditions:** Multiple sync types have run
- **Steps:**
  1. `GET /api/erp/admin/sync/logs?syncType=PRODUCT_INBOUND`
- **Expected Result:** Only `PRODUCT_INBOUND` log entries returned
- **Pass Criteria:** All entries have `syncType:"PRODUCT_INBOUND"`

---

**TC-ERP-006**
- **Title:** Latest sync endpoint returns most recent record per type
- **Priority:** P2
- **Preconditions:** Multiple syncs have run
- **Steps:**
  1. `GET /api/erp/admin/sync/logs/latest`
- **Expected Result:** HTTP 200; `productSync`, `inventorySync`, `sohPush` keys each with latest record
- **Pass Criteria:** All three keys present

---

**TC-ERP-007**
- **Title:** Failed sync is recorded in logs with error message
- **Priority:** P2
- **Preconditions:** ERP URL configured to an unreachable endpoint
- **Steps:**
  1. `POST /api/erp/admin/sync/products`
  2. `GET /api/erp/admin/sync/logs`
- **Expected Result:** Log entry with `status:"FAILED"` and non-empty `errorMessage`
- **Pass Criteria:** Failed entry with error message

---

**TC-ERP-008**
- **Title:** Product sync updates existing products (upsert)
- **Priority:** P2
- **Preconditions:** Product already in DB; ERP has updated name for same ERP code
- **Steps:**
  1. Trigger product sync
  2. Check product name
- **Expected Result:** Product name updated to match ERP
- **Pass Criteria:** Name matches ERP value

---

**TC-ERP-009**
- **Title:** Inventory sync updates quantityExpected
- **Priority:** P2
- **Preconditions:** ERP has expected inventory data
- **Steps:**
  1. `POST /api/erp/admin/sync/inventory`
  2. `GET /api/inventory/state?storeId=<id>`
- **Expected Result:** `quantityExpected` values updated from ERP
- **Pass Criteria:** Inventory records updated

---

**TC-ERP-010**
- **Title:** SOH outbound push enabled only when flag is true
- **Priority:** P2
- **Preconditions:** `ERP_PUSH_SOH_ENABLED=false`
- **Steps:**
  1. Complete a SOH session
  2. Check ERP sync logs
- **Expected Result:** No `SOH_OUTBOUND` sync log entry created
- **Pass Criteria:** No outbound push log when disabled

---

## 12. Security & Access Control

---

**TC-SEC-001**
- **Title:** SQL injection attempt in login body is rejected
- **Priority:** P1
- **Preconditions:** None
- **Steps:**
  1. `POST /api/auth/login` with `{"username":"admin' OR '1'='1","password":"x"}`
- **Expected Result:** HTTP 401; no SQL error; no data leak
- **Pass Criteria:** Status 401; no 500 error; no sensitive data in response

---

**TC-SEC-002**
- **Title:** XSS payload in product name is stored as plain text
- **Priority:** P1
- **Preconditions:** Admin token
- **Steps:**
  1. Create product with `"name":"<script>alert('xss')</script>"`
  2. Retrieve product
- **Expected Result:** Name returned as literal string, not executed HTML
- **Pass Criteria:** Response contains escaped string; no HTML interpretation

---

**TC-SEC-003**
- **Title:** Actuator endpoints blocked through API gateway
- **Priority:** P1
- **Preconditions:** None
- **Steps:**
  1. `GET http://localhost:8080/actuator` (through gateway)
- **Expected Result:** HTTP 403 from Nginx (blocked in nginx.conf)
- **Pass Criteria:** Status 403; actuator data not exposed via gateway

---

**TC-SEC-004**
- **Title:** Actuator health accessible directly on service port
- **Priority:** P2
- **Preconditions:** Service running
- **Steps:**
  1. `GET http://localhost:8081/actuator/health` (direct)
- **Expected Result:** HTTP 200; health data
- **Pass Criteria:** Status 200 on direct access (not via gateway)

---

**TC-SEC-005**
- **Title:** CORS headers present on API responses
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Send `OPTIONS /api/stores` with `Origin: http://example.com`
- **Expected Result:** HTTP 204; `Access-Control-Allow-Origin` header present
- **Pass Criteria:** CORS headers present

---

**TC-SEC-006**
- **Title:** JWT secret of less than 32 chars causes startup failure
- **Priority:** P2
- **Preconditions:** Ability to restart a service
- **Steps:**
  1. Set `JWT_SECRET=shortkey` (< 32 chars) and restart auth-service
- **Expected Result:** Service fails to start with configuration error
- **Pass Criteria:** Service does not start; appropriate error in logs

---

**TC-SEC-007**
- **Title:** Multi-tenancy: Store Manager cannot read another store's SOH sessions
- **Priority:** P1
- **Preconditions:** STORE_MANAGER for Store A; sessions exist in Store B
- **Steps:**
  1. `GET /api/soh/sessions?storeId=<storeBId>` with Store A token
- **Expected Result:** HTTP 403 or empty result — no Store B sessions
- **Pass Criteria:** Store B data not accessible

---

**TC-SEC-008**
- **Title:** Forge storeId in JWT — backend uses server-validated storeId
- **Priority:** P1
- **Preconditions:** STORE_MANAGER for Store A; crafted JWT with Store B's storeId
- **Steps:**
  1. Modify JWT payload to change `storeId` to Store B (signature will be invalid)
  2. Use forged token on `GET /api/soh/sessions?storeId=<storeBId>`
- **Expected Result:** HTTP 401 (invalid signature)
- **Pass Criteria:** Forged token rejected

---

**TC-SEC-009**
- **Title:** Password is not returned in any API response
- **Priority:** P1
- **Preconditions:** Admin token
- **Steps:**
  1. `GET /api/users` (Admin)
  2. `GET /api/users/<id>`
- **Expected Result:** No `password` or `passwordHash` field in any response
- **Pass Criteria:** Password field absent from all responses

---

**TC-SEC-010**
- **Title:** Rate limiting triggers after threshold
- **Priority:** P2
- **Preconditions:** Nginx running
- **Steps:**
  1. Send 70 rapid GET requests to `/api/stores` in under 60 seconds (limit = 60/min)
- **Expected Result:** First 60 requests return 200; subsequent return 503 (rate limited)
- **Pass Criteria:** Rate limiting activates

---

**TC-SEC-011**
- **Title:** Refresh token not reusable after rotation
- **Priority:** P1
- **Preconditions:** Valid refresh token
- **Steps:**
  1. Use refresh token → get new tokens
  2. Use original refresh token again
- **Expected Result:** Second use returns HTTP 401
- **Pass Criteria:** Old refresh token invalidated

---

**TC-SEC-012**
- **Title:** Sensitive env vars not exposed via API
- **Priority:** P1
- **Preconditions:** Application running
- **Steps:**
  1. Check all API responses for `JWT_SECRET`, `DB_PASSWORD`, `REDIS_PASSWORD`
- **Expected Result:** No sensitive configuration in any API response
- **Pass Criteria:** No secrets in API output

---

**TC-SEC-013**
- **Title:** HTTP method not allowed returns 405
- **Priority:** P3
- **Preconditions:** None
- **Steps:**
  1. `DELETE /api/auth/login` (DELETE not supported)
- **Expected Result:** HTTP 405 Method Not Allowed
- **Pass Criteria:** Status 405; no 500 error

---

**TC-SEC-014**
- **Title:** Very long username in login body does not cause server error
- **Priority:** P3
- **Preconditions:** None
- **Steps:**
  1. Submit login with `username` as a 10,000-character string
- **Expected Result:** HTTP 400 (validation) or HTTP 401; no 500 error
- **Pass Criteria:** 4xx response; server does not crash

---

**TC-SEC-015**
- **Title:** Token from one service is valid on all services (shared JWT secret)
- **Priority:** P1
- **Preconditions:** Token obtained from login (auth-service)
- **Steps:**
  1. Use token on store-service, product-service, soh-service
- **Expected Result:** All accept the token — HTTP 200 on allowed endpoints
- **Pass Criteria:** Single token works across all services

---

## 13. Notifications

---

**TC-NOTIF-001**
- **Title:** WebSocket connection established on valid token
- **Priority:** P2
- **Preconditions:** Valid JWT token; notification-service running
- **Steps:**
  1. Connect to `ws://localhost:8091/ws` with `Authorization: Bearer <token>` header
- **Expected Result:** WebSocket connection established (101 Switching Protocols)
- **Pass Criteria:** Connection open

---

**TC-NOTIF-002**
- **Title:** SOH session completion triggers WebSocket event
- **Priority:** P2
- **Preconditions:** WebSocket connected as STORE_MANAGER; active SOH session
- **Steps:**
  1. Connect to WebSocket
  2. Complete a SOH session via API
- **Expected Result:** WebSocket receives event with `type:"SOH_SESSION_COMPLETED"` and session data
- **Pass Criteria:** Event received within 5 seconds of completion

---

**TC-NOTIF-003**
- **Title:** Refill task assignment triggers WebSocket notification to assignee
- **Priority:** P1
- **Preconditions:** REFILL_ASSOCIATE connected to WebSocket; task exists
- **Steps:**
  1. Connect REFILL_ASSOCIATE to WebSocket
  2. Assign task to that associate via API
- **Expected Result:** Associate receives WebSocket event with task details
- **Pass Criteria:** Event received

---

**TC-NOTIF-004**
- **Title:** RFID reader offline event pushed to Store Manager
- **Priority:** P2
- **Preconditions:** STORE_MANAGER connected to WebSocket; reader heartbeat stops
- **Steps:**
  1. Connect to WebSocket
  2. Stop a reader's heartbeat (simulate by disabling reader)
  3. Wait 5 minutes (online threshold)
- **Expected Result:** WebSocket receives `type:"READER_OFFLINE"` event
- **Pass Criteria:** Offline event received

---

**TC-NOTIF-005**
- **Title:** Disconnected WebSocket client does not cause server error
- **Priority:** P3
- **Preconditions:** Client connected and then disconnected abruptly
- **Steps:**
  1. Connect WebSocket client
  2. Close connection without proper close handshake
  3. Complete a SOH session (would have triggered event)
- **Expected Result:** Server handles gracefully; no 500 error in logs
- **Pass Criteria:** No server errors

---

**TC-NOTIF-006**
- **Title:** Multiple clients receive the same broadcast event
- **Priority:** P3
- **Preconditions:** Two STORE_MANAGERs connected for same store
- **Steps:**
  1. Both connect to WebSocket
  2. Complete a SOH session
- **Expected Result:** Both clients receive the completion event
- **Pass Criteria:** Both clients receive event

---

**TC-NOTIF-007**
- **Title:** Unauthenticated WebSocket connection is rejected
- **Priority:** P2
- **Preconditions:** None
- **Steps:**
  1. Connect to `ws://localhost:8091/ws` with no token
- **Expected Result:** Connection rejected (401 or immediate close)
- **Pass Criteria:** Cannot establish unauthenticated WebSocket

---

**TC-NOTIF-008**
- **Title:** Refill task completion pushes event to Store Manager
- **Priority:** P2
- **Preconditions:** STORE_MANAGER connected; assigned task exists
- **Steps:**
  1. STORE_MANAGER connects to WebSocket
  2. REFILL_ASSOCIATE fulfils all items → task completes
- **Expected Result:** STORE_MANAGER receives `type:"REFILL_TASK_COMPLETED"` event
- **Pass Criteria:** Event received

---

## 14. Android Mobile App

---

**TC-MOB-001**
- **Title:** Login with valid credentials on Zebra device
- **Priority:** P1
- **Preconditions:** App installed; backend reachable; valid credentials
- **Steps:**
  1. Open app; enter username/password; tap Sign In
- **Expected Result:** Login succeeds; navigates to home screen
- **Pass Criteria:** Home screen displayed; user name shown

---

**TC-MOB-002**
- **Title:** Login with invalid credentials shows error
- **Priority:** P1
- **Preconditions:** App installed
- **Steps:**
  1. Enter wrong password; tap Sign In
- **Expected Result:** Error message displayed; no navigation
- **Pass Criteria:** Error toast shown; stays on login screen

---

**TC-MOB-003**
- **Title:** Start a SOH scan session from mobile
- **Priority:** P1
- **Preconditions:** Logged-in STORE_ASSOCIATE; backend reachable
- **Steps:**
  1. Tap Start Count; select Full Store; tap Start
- **Expected Result:** Session created; scanning screen shown; RFID antenna activates
- **Pass Criteria:** Session appears in web portal with `status:"created"` → `in_progress`

---

**TC-MOB-004**
- **Title:** RFID tags are read and displayed in real time
- **Priority:** P1
- **Preconditions:** Active scan session; RFID reader active (or MockRfidReader in debug)
- **Steps:**
  1. Move device near RFID-tagged items
- **Expected Result:** EPC reads appear on screen incrementally; count increments
- **Pass Criteria:** Reads visible; count increases

---

**TC-MOB-005**
- **Title:** EPC reads are uploaded to backend in batches
- **Priority:** P1
- **Preconditions:** Active scan; WiFi connected
- **Steps:**
  1. Scan items; wait 10 seconds
  2. Check web portal session `totalEpcReads`
- **Expected Result:** Reads appear in web portal
- **Pass Criteria:** `totalEpcReads > 0` on web

---

**TC-MOB-006**
- **Title:** Offline scan buffers reads and syncs on reconnect
- **Priority:** P1
- **Preconditions:** Active session; WiFi disabled
- **Steps:**
  1. Disable WiFi on device
  2. Scan items (reads buffered in Room)
  3. Re-enable WiFi
  4. Wait 30 seconds for WorkManager to sync
- **Expected Result:** All buffered reads uploaded; web portal session count updated
- **Pass Criteria:** No reads lost; counts match

---

**TC-MOB-007**
- **Title:** Complete session from mobile
- **Priority:** P1
- **Preconditions:** Active in-progress session
- **Steps:**
  1. Tap Complete on the scan screen; confirm
- **Expected Result:** Session marked complete; result screen shows accuracy %
- **Pass Criteria:** Session `status:"completed"` in web portal; accuracy shown

---

**TC-MOB-008**
- **Title:** View assigned refill tasks
- **Priority:** P1
- **Preconditions:** REFILL_ASSOCIATE logged in; task assigned to them
- **Steps:**
  1. Navigate to Tasks
- **Expected Result:** Assigned tasks listed, sorted by priority
- **Pass Criteria:** Task list shows correct tasks for the associate

---

**TC-MOB-009**
- **Title:** Fulfil a refill task item on mobile
- **Priority:** P1
- **Preconditions:** Task assigned; REFILL_ASSOCIATE on Tasks screen
- **Steps:**
  1. Open task; tap item; enter fulfilled quantity; confirm
- **Expected Result:** Item status updates; task progress reflected
- **Pass Criteria:** Item `status:"fulfilled"` in web portal

---

**TC-MOB-010**
- **Title:** Session persists across app restart (in-progress session resumed)
- **Priority:** P2
- **Preconditions:** Active scan session on device
- **Steps:**
  1. Close the app (background)
  2. Reopen the app
- **Expected Result:** App shows active session; can resume scanning
- **Pass Criteria:** Session data preserved; scanning can continue

---

**TC-MOB-011**
- **Title:** MockRfidReader emits fake EPCs in debug build
- **Priority:** P2
- **Preconditions:** Debug build installed on emulator or device
- **Steps:**
  1. Start scan session
- **Expected Result:** Fake EPCs appear on screen at regular intervals without real hardware
- **Pass Criteria:** EPC count increments automatically

---

**TC-MOB-012**
- **Title:** App handles network timeout gracefully
- **Priority:** P2
- **Preconditions:** Backend unreachable (network blocked)
- **Steps:**
  1. Attempt login with no backend
- **Expected Result:** Timeout error shown; app does not crash
- **Pass Criteria:** Friendly error message; no crash

---

**TC-MOB-013**
- **Title:** RFID antenna deactivates when app goes to background
- **Priority:** P2
- **Preconditions:** Active scan session; real EMDK
- **Steps:**
  1. Press Home button while scanning
- **Expected Result:** RFID antenna stops reading; reads resume when app comes back to foreground
- **Pass Criteria:** No reads emitted in background; antenna stops

---

**TC-MOB-014**
- **Title:** WorkManager retries upload with exponential backoff on failure
- **Priority:** P2
- **Preconditions:** Buffered reads in Room; backend temporarily unreachable
- **Steps:**
  1. Block backend network
  2. Wait for first WorkManager retry (15s)
  3. Second retry (30s)
  4. Restore network
- **Expected Result:** Retries attempted at 15s, 30s, 60s; reads uploaded on success
- **Pass Criteria:** Reads eventually uploaded; no data loss

---

**TC-MOB-015**
- **Title:** Refill tasks sync automatically every 15 minutes
- **Priority:** P3
- **Preconditions:** RefillSyncWorker scheduled; new task assigned to user after app started
- **Steps:**
  1. Log in; note task count
  2. New task assigned via web portal
  3. Wait 15 minutes (or force WorkManager run)
- **Expected Result:** New task appears in mobile task list without manual refresh
- **Pass Criteria:** Task appears within 15 minutes

---

*Document generated for StoreLense v1.0 — Total test cases: 198*
