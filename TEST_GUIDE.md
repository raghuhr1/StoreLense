# StoreLense — Test Guide

> Covers every testing layer: integration tests, API smoke tests, backend unit tests, frontend type-checking, manual test scenarios, and Android testing.

---

## Table of Contents

1. [Testing Overview](#1-testing-overview)
2. [Integration Test Suite (test.ps1)](#2-integration-test-suite-testps1)
3. [Backend Unit & Integration Tests](#3-backend-unit--integration-tests)
4. [API Manual Testing (curl)](#4-api-manual-testing-curl)
5. [Frontend Testing](#5-frontend-testing)
6. [Android Testing](#6-android-testing)
7. [End-to-End Workflow Tests](#7-end-to-end-workflow-tests)
8. [Performance & Load Testing](#8-performance--load-testing)
9. [CI Test Pipeline](#9-ci-test-pipeline)
10. [Test Data Reference](#10-test-data-reference)

---

## 1. Testing Overview

| Layer | Tool | When to Run |
|---|---|---|
| Integration test suite | PowerShell `test.ps1` | After every deployment |
| Backend unit tests | JUnit 5 + Mockito | `mvn test` during development |
| Backend integration tests | JUnit 5 + TestContainers | `mvn verify` in CI |
| Frontend type-check | TypeScript compiler | `npm run type-check` |
| Frontend lint | ESLint | `npm run lint` |
| API manual testing | curl / Postman | Ad-hoc during development |
| Android unit tests | JUnit + MockK | `./gradlew test` |
| Android instrumented | Espresso / Compose UI | On physical Zebra device |
| Smoke tests | curl (in CD pipeline) | After every deploy |
| Load testing | k6 / JMeter | Before production releases |

---

## 2. Integration Test Suite (test.ps1)

The primary integration test script is `deploy/test.ps1`. It runs against a live deployment and tests the full request path through Nginx → microservice → database.

### Prerequisites

- All services must be running and healthy (`docker compose ps`)
- PowerShell 7+ installed (`pwsh --version`)
- Network access to the gateway (default: `http://localhost:8080`)

### Running the Tests

```powershell
cd deploy

# Against local deployment (default)
pwsh ./test.ps1

# Against a remote server
pwsh ./test.ps1 -Gateway "http://150.241.247.238:8080" `
                -AuthUrl  "http://150.241.247.238:8081" `
                -Username "admin" `
                -Password "Admin@StoreLense1"
```

### Parameters

| Parameter | Default | Description |
|---|---|---|
| `-Gateway` | `http://localhost:8080` | Nginx API Gateway URL |
| `-AuthUrl` | `http://localhost:8081` | Auth service direct URL (bypasses gateway for health checks) |
| `-Username` | `admin` | Login username |
| `-Password` | `Admin@StoreLense1` | Login password |

### What It Tests

The script runs **11 tests** in sequence:

| # | Test | Endpoint | Expected |
|---|---|---|---|
| 1 | Gateway health | `GET /health` | 200 `{"status":"UP"}` |
| 2 | Auth service health | `GET /actuator/health` (direct) | 200 |
| 3 | Admin login | `POST /api/auth/login` | 200 + JWT token |
| 4 | List stores | `GET /api/stores` | 200 + store list |
| 5 | List products | `GET /api/products` | 200 + product list |
| 6 | List SOH sessions | `GET /api/soh/sessions?storeId=<id>` | 200 |
| 7 | List refill tasks | `GET /api/refill/tasks?storeId=<id>` | 200 |
| 8 | Inventory state | `GET /api/inventory/state?storeId=<id>` | 200 |
| 9 | RFID ingest batch | `POST /api/rfid/ingest/batch` | 202 Accepted |
| 10 | KPI range report | `GET /api/reporting/kpi/range?...` | 200 |
| 11 | Token refresh | `POST /api/auth/refresh` | 200 + new token |

### Expected Output

```
=== StoreLense Integration Tests ===
Gateway : http://localhost:8080
Auth    : http://localhost:8081

>> Infrastructure
  [PASS] Gateway /health (200)
  [PASS] Auth actuator/health (200)

>> Authentication
  [PASS] POST /api/auth/login (200)
    JWT: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

>> Stores API
  [PASS] GET /api/stores (200)

>> Products API
  [PASS] GET /api/products (200)

>> SOH / Refill / Inventory APIs
  [PASS] GET /api/soh/sessions (200)
  [PASS] GET /api/refill/tasks (200)
  [PASS] GET /api/inventory/state (200)

>> RFID Ingest API
  [PASS] POST /api/rfid/ingest/batch (202)

>> Reporting API
  [PASS] GET /api/reporting/kpi/range (200)

>> Token Refresh
  [PASS] POST /api/auth/refresh (200)

=== Results: 11/11 passed ===
All tests PASSED
```

### Interpreting Failures

| Failure | Likely Cause |
|---|---|
| `Gateway /health` fails | Nginx not running or port 8080 blocked |
| `Auth actuator/health` fails | auth-service not healthy; check `docker compose logs auth-service` |
| `POST /api/auth/login` fails | Wrong credentials, or DB not seeded with admin user |
| SOH/Refill/Inventory SKIP | No stores found in the database — seed data missing |
| `POST /api/rfid/ingest/batch` fails | rfid-ingest-service or Kafka not ready |
| `GET /api/reporting/kpi/range` fails | reporting-service not ready or no KPI data for range |
| `POST /api/auth/refresh` fails | Redis blacklist issue or refresh token already revoked |

---

## 3. Backend Unit & Integration Tests

### Running Tests

```bash
cd backend

# Unit tests only (all services)
mvn test

# Unit tests for a specific service
mvn test -pl soh-service

# Integration tests (all services) — requires Docker (TestContainers)
mvn verify

# Integration tests for a specific service
mvn verify -pl auth-service

# Skip tests (for faster builds)
mvn package -DskipTests

# Run a specific test class
mvn test -pl auth-service -Dtest=AuthServiceTest

# Run a specific test method
mvn test -pl auth-service -Dtest=AuthServiceTest#shouldIssueTokenOnValidLogin
```

### Test Dependencies

Each service inherits from root POM:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <!-- Includes: JUnit 5, Mockito, AssertJ, MockMvc, JsonPath -->
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-bom</artifactId>
    <version>1.19.8</version>
    <type>pom</type>
    <scope>import</scope>
    <!-- PostgreSQL, Kafka, Redis containers for integration tests -->
</dependency>
```

### Writing Unit Tests

```java
// auth-service/src/test/java/.../AuthServiceTest.java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock JwtTokenProvider jwtProvider;
    @InjectMocks AuthService authService;

    @Test
    void shouldIssueTokenOnValidLogin() {
        // Arrange
        User user = new User("admin", "hashed_pw", Set.of(new Role("ADMIN")));
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(any())).thenReturn("jwt-token");

        // Act
        LoginResponse response = authService.login(new LoginRequest("admin", "Admin@StoreLense1"));

        // Assert
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo("ADMIN");
    }

    @Test
    void shouldThrowOnInvalidPassword() {
        User user = new User("admin", "hashed_pw", Set.of());
        when(userRepo.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Invalid credentials");
    }
}
```

### Writing Integration Tests (TestContainers)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SohSessionControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("storelense_test")
        .withUsername("postgres")
        .withPassword("postgres");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired TestRestTemplate restTemplate;

    @Test
    void shouldCreateAndCompleteSession() {
        // Login
        LoginResponse login = restTemplate.postForObject(
            "/api/auth/login",
            new LoginRequest("admin", "Admin@StoreLense1"),
            LoginResponse.class
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());

        // Create session
        StartSessionRequest req = new StartSessionRequest(storeId, null, "manual", "IT test");
        ResponseEntity<ApiResponse> created = restTemplate.postForEntity(
            "/api/soh/sessions",
            new HttpEntity<>(req, headers),
            ApiResponse.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Complete session
        UUID sessionId = extractId(created);
        ResponseEntity<ApiResponse> completed = restTemplate.postForEntity(
            "/api/soh/sessions/" + sessionId + "/complete",
            new HttpEntity<>(headers),
            ApiResponse.class
        );
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

### MockMvc Tests (Controller Layer)

```java
@WebMvcTest(RefillTaskController.class)
class RefillTaskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RefillTaskService service;

    @Test
    @WithMockUser(roles = "STORE_MANAGER")
    void shouldReturnTaskList() throws Exception {
        when(service.list(any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/refill/tasks")
                .param("storeId", UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "STORE_ASSOCIATE")
    void shouldRejectCreateForAssociate() throws Exception {
        mockMvc.perform(post("/api/refill/tasks")
                .content("{}")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }
}
```

---

## 4. API Manual Testing (curl)

Use these curl commands to test individual endpoints. Replace `TOKEN` and UUIDs with real values.

### Authentication

```bash
# Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' | jq

# Save token to variable
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' \
  | jq -r '.data.accessToken')

echo "Token: $TOKEN"

# Get current user
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/auth/me | jq

# Refresh token
REFRESH=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' \
  | jq -r '.data.refreshToken')

curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq

# Logout
curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Stores & Zones

```bash
# List stores
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stores | jq

# Save first store ID
STORE_ID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stores \
  | jq -r '.data.content[0].id')

echo "Store ID: $STORE_ID"

# Get store detail
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/stores/$STORE_ID" | jq

# List zones
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/stores/$STORE_ID/zones" | jq

# Create zone
curl -s -X POST "http://localhost:8080/api/stores/$STORE_ID/zones" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"zoneCode":"TEST-01","name":"Test Zone","zoneType":"floor","displayOrder":99}' | jq
```

### Products

```bash
# List products
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/products?size=5" | jq

# Search products
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/products?search=denim" | jq

# Get product by SKU
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/products/by-sku/APP-DNM-001" | jq

# EPC lookup
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/products/epc/3034257BF400B71400000001" | jq

# Create product (ADMIN only)
curl -s -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "sku":"TEST-001",
    "name":"Test Product",
    "rfidEnabled":true,
    "unitOfMeasure":"EACH"
  }' | jq
```

### SOH Sessions

```bash
# List sessions
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/soh/sessions?storeId=$STORE_ID" | jq

# Start a new session
SESSION_RESP=$(curl -s -X POST http://localhost:8080/api/soh/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"storeId\":\"$STORE_ID\",\"sessionType\":\"manual\",\"notes\":\"curl test\"}")

echo "$SESSION_RESP" | jq
SESSION_ID=$(echo "$SESSION_RESP" | jq -r '.data.id')
echo "Session ID: $SESSION_ID"

# Complete the session
curl -s -X POST "http://localhost:8080/api/soh/sessions/$SESSION_ID/complete" \
  -H "Authorization: Bearer $TOKEN" | jq

# Cancel a session
curl -s -X POST "http://localhost:8080/api/soh/sessions/$SESSION_ID/cancel?reason=Test+cancelled" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### RFID Ingest

```bash
# Submit a batch of EPC reads (returns 202)
curl -s -X POST http://localhost:8080/api/rfid/ingest/batch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"rfidSessionId\": \"$(uuidgen)\",
    \"storeId\": \"$STORE_ID\",
    \"deviceId\": \"test-curl-device\",
    \"reads\": [
      {\"epc\":\"3034257BF400B71400000001\",\"rssi\":-68.5,\"antennaPort\":0},
      {\"epc\":\"3034257BF400B71400000002\",\"rssi\":-72.1,\"antennaPort\":0},
      {\"epc\":\"3034257BF400B71400000003\",\"rssi\":-65.0,\"antennaPort\":1}
    ]
  }" | jq
```

### Refill Tasks

```bash
# List tasks
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/refill/tasks?storeId=$STORE_ID" | jq

# Create task
PRODUCT_ID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/products?size=1" | jq -r '.data.content[0].id')

TASK_RESP=$(curl -s -X POST http://localhost:8080/api/refill/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"storeId\":\"$STORE_ID\",
    \"taskType\":\"replenishment\",
    \"priority\":5,
    \"notes\":\"curl test task\",
    \"items\":[{\"productId\":\"$PRODUCT_ID\",\"requestedQuantity\":10}]
  }")

echo "$TASK_RESP" | jq
TASK_ID=$(echo "$TASK_RESP" | jq -r '.data.id')
ITEM_ID=$(echo "$TASK_RESP" | jq -r '.data.items[0].id')

# Fulfil an item
curl -s -X PATCH \
  "http://localhost:8080/api/refill/tasks/$TASK_ID/items/$ITEM_ID/fulfil?quantity=10" \
  -H "Authorization: Bearer $TOKEN" | jq

# Cancel task
curl -s -X POST \
  "http://localhost:8080/api/refill/tasks/$TASK_ID/cancel?reason=Test+done" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Inventory & Reporting

```bash
# Inventory state
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/inventory/state?storeId=$STORE_ID" | jq

# Low accuracy items
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/inventory/low-accuracy?storeId=$STORE_ID&threshold=95.0" | jq

# EPC summary
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/inventory/epc-summary?storeId=$STORE_ID" | jq

# KPI range
FROM=$(date -d "7 days ago" +%Y-%m-%d)
TO=$(date +%Y-%m-%d)
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/reporting/kpi/range?storeId=$STORE_ID&from=$FROM&to=$TO" | jq

# Trigger KPI aggregation (Admin)
curl -s -X POST \
  "http://localhost:8080/api/reporting/kpi/aggregate?storeId=$STORE_ID&date=$TO" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Error Cases to Verify

```bash
# 401 — no token
curl -s http://localhost:8080/api/stores | jq

# 403 — wrong role (Associate trying to create store)
ASSOC_TOKEN="<associate-jwt>"
curl -s -X POST http://localhost:8080/api/stores \
  -H "Authorization: Bearer $ASSOC_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"storeCode":"X","name":"X"}' | jq

# 400 — validation error
curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"ab","email":"not-an-email"}' | jq

# 404 — not found
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/stores/00000000-0000-0000-0000-000000000000" | jq

# Rate limit — send many rapid requests
for i in {1..70}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    http://localhost:8080/api/stores
done
# After 60 requests/minute: expect 503 from Nginx rate limiter
```

---

## 5. Frontend Testing

### Type Checking

```bash
cd frontend

# Full TypeScript type check (no output emitted)
npm run type-check

# Watch mode during development
npx tsc --noEmit --watch
```

### Linting

```bash
cd frontend
npm run lint

# Auto-fix fixable issues
npx eslint src/ --fix
```

### Production Build Validation

```bash
cd frontend
npm run build
# Must complete with 0 errors and 0 type errors
# Check .next/ directory is created
```

### Manual Browser Testing Checklist

Run through these scenarios after any frontend change:

**Authentication**
- [ ] Login with correct credentials → redirects to `/dashboard`
- [ ] Login with wrong password → shows error message
- [ ] Accessing `/dashboard` without login → redirects to `/login`
- [ ] Logout → redirects to `/login`, subsequent requests are 401

**Dashboard**
- [ ] KPI cards load with numbers
- [ ] Charts render with data (or empty state if no data)
- [ ] Recent sessions list shows sessions
- [ ] Pending tasks list shows tasks
- [ ] Auto-refresh every 30 seconds (check Network tab)

**Cycle Count**
- [ ] Session list loads
- [ ] Status filter dropdown works
- [ ] "Start New Count" modal opens and closes
- [ ] Creating a session → appears in list with `created` status
- [ ] Opening a session detail shows tabs (Summary, Items, Variances)

**Receiving**
- [ ] Task list loads sorted by priority
- [ ] "New Task" form opens, validates required fields
- [ ] Creating a task → appears in list
- [ ] Opening a task shows line items
- [ ] Fulfilling an item updates status

**Reports**
- [ ] 7d/30d/90d buttons switch the date range
- [ ] Charts redraw on date range change
- [ ] KPI table updates

**Responsive / Edge Cases**
- [ ] All screens render correctly on 1024px width
- [ ] Empty state messages show when there's no data
- [ ] Network errors show an error toast (not a blank screen)

---

## 6. Android Testing

### Unit Tests

```bash
cd android

# Run all unit tests (JVM, no device needed)
./gradlew test

# Run a specific test
./gradlew test --tests "com.storelense.zebra.SohViewModelTest"

# Generate HTML report
./gradlew test
open app/build/reports/tests/testDebugUnitTest/index.html
```

### Key Unit Test Areas

**MockRfidReader tests**
```kotlin
@Test
fun `should emit fake EPCs at regular interval`() = runTest {
    val reader = MockRfidReader()
    val emitted = mutableListOf<String>()
    val job = launch { reader.epcFlow.take(5).toList(emitted) }
    reader.startScan()
    job.join()
    assertThat(emitted).hasSize(5)
    assertThat(emitted.first()).matches("[0-9A-F]{24}")
}
```

**WorkManager sync test**
```kotlin
@Test
fun `RfidSyncWorker uploads pending reads`() {
    val worker = TestListenableWorkerBuilder<RfidSyncWorker>(context)
        .setWorkerFactory(factory)
        .build()
    val result = runBlocking { worker.doWork() }
    assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
}
```

**Room DAO tests**
```kotlin
@RunWith(AndroidJUnit4::class)
class SohSessionDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SohSessionDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = db.sohSessionDao()
    }

    @Test fun insertAndRetrieve() = runTest {
        val session = SohSessionEntity(id = UUID.randomUUID().toString(), ...)
        dao.insert(session)
        val retrieved = dao.getById(session.id)
        assertThat(retrieved?.id).isEqualTo(session.id)
    }

    @After fun teardown() { db.close() }
}
```

### Instrumented Tests (Physical Zebra Device)

Connect a Zebra TC-series device via USB.

```bash
cd android

# Install and run instrumented tests
./gradlew connectedAndroidTest

# Results
open app/build/reports/androidTests/connected/index.html
```

**Test scenarios on physical device:**
- [ ] Login with real credentials → success
- [ ] Start scan session → EMDK activates, tags read
- [ ] Walk past RFID fixture → tags appear on screen
- [ ] Disconnect WiFi during scan → reads buffered in Room
- [ ] Reconnect WiFi → WorkManager syncs automatically
- [ ] Complete session → result syncs to server

---

## 7. End-to-End Workflow Tests

These scenarios test the complete business flow across all services.

### Scenario 1 — Full SOH Count Cycle

**Steps:**
1. Login as STORE_ASSOCIATE
2. `POST /api/soh/sessions` → note `sessionId`
3. `POST /api/rfid/ingest/batch` with `rfidSessionId = sessionId` and 3 EPC reads
4. Wait 5 seconds for Kafka processing
5. `GET /api/soh/sessions/{sessionId}` → verify `totalEpcReads = 3`, `uniqueEpcCount = 3`
6. `POST /api/soh/sessions/{sessionId}/complete` → verify `accuracyPct` is set
7. Login as STORE_MANAGER
8. `GET /api/inventory/state?storeId=...` → verify `lastCountedAt` updated for counted products

**Expected result:** Session completes with accuracy result; inventory state updated.

```bash
# Automated version
SESSION_ID=$(curl -s -X POST http://localhost:8080/api/soh/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"storeId\":\"$STORE_ID\",\"sessionType\":\"manual\"}" \
  | jq -r '.data.id')

curl -s -X POST http://localhost:8080/api/rfid/ingest/batch \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"rfidSessionId\":\"$SESSION_ID\",
    \"storeId\":\"$STORE_ID\",
    \"reads\":[
      {\"epc\":\"3034257BF400B71400000001\",\"rssi\":-68},
      {\"epc\":\"3034257BF400B71400000002\",\"rssi\":-70},
      {\"epc\":\"3034257BF400B71400000003\",\"rssi\":-65}
    ]
  }" | jq

sleep 5  # Wait for Kafka processing

curl -s "http://localhost:8080/api/soh/sessions/$SESSION_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.data | {status, totalEpcReads, uniqueEpcCount}'

curl -s -X POST "http://localhost:8080/api/soh/sessions/$SESSION_ID/complete" \
  -H "Authorization: Bearer $TOKEN" | jq '.data | {accuracyPct, varianceCount}'
```

---

### Scenario 2 — Refill Task Lifecycle

**Steps:**
1. Login as STORE_MANAGER
2. `POST /api/refill/tasks` with 2 line items → note `taskId`, `itemId1`, `itemId2`
3. `POST /api/refill/tasks/{taskId}/assign?assignedTo=<refill-associate-id>`
4. Login as REFILL_ASSOCIATE
5. `GET /api/refill/tasks/{taskId}` → verify status is `assigned`
6. `PATCH /api/refill/tasks/{taskId}/items/{itemId1}/fulfil?quantity=10` (full fulfil)
7. `PATCH /api/refill/tasks/{taskId}/items/{itemId2}/fulfil?quantity=5` (partial fulfil, requested was 10)
8. `GET /api/refill/tasks/{taskId}` → verify task status = `completed`; item 1 = `fulfilled`, item 2 = `partial`

**Expected result:** Task completes automatically when all items reach terminal state.

---

### Scenario 3 — Token Security

**Steps:**
1. Login → get access token + refresh token
2. Logout → tokens revoked
3. Try authenticated request with old access token → expect `401 Unauthorized`
4. Try to refresh with old refresh token → expect `401 Unauthorized`

```bash
# Step 1: Login
RESP=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}')
TOKEN=$(echo $RESP | jq -r '.data.accessToken')
REFRESH=$(echo $RESP | jq -r '.data.refreshToken')

# Step 2: Logout
curl -s -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"

# Step 3: Old token should fail
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/stores | jq '.success'
# Expected: false (401)

# Step 4: Old refresh should fail
curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}" | jq '.success'
# Expected: false (401)
```

---

### Scenario 4 — Multi-Tenancy Enforcement

**Steps:**
1. Create a STORE_MANAGER for Store A (with `storeId = store-a-id`)
2. Login as that manager
3. `GET /api/soh/sessions?storeId=<store-b-id>` → expect `403 Forbidden` or empty result
4. `GET /api/inventory/state?storeId=<store-b-id>` → expect `403 Forbidden`

**Expected result:** Non-admin users cannot access data from other stores.

---

### Scenario 5 — RFID Duplicate Deduplication

**Steps:**
1. Start a SOH session → note `sessionId`
2. Submit batch with EPC `AAAA1111BBBB2222CCCC3333` ten times in the same batch
3. Wait for processing
4. `GET /api/soh/sessions/{sessionId}` → verify `totalEpcReads = 10`, `uniqueEpcCount = 1`

**Expected result:** Deduplication within session window keeps unique count at 1.

---

## 8. Performance & Load Testing

### k6 Basic Load Test

Create `tests/load/rfid-ingest.js`:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up
    { duration: '60s', target: 50 },   // Sustained load
    { duration: '10s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests < 500ms
    http_req_failed: ['rate<0.01'],    // < 1% failure rate
  },
};

export function setup() {
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    username: 'admin', password: 'Admin@StoreLense1'
  }), { headers: { 'Content-Type': 'application/json' } });
  return { token: res.json('data.accessToken') };
}

export default function (data) {
  const headers = {
    'Authorization': `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  const batch = {
    rfidSessionId: '00000000-0000-0000-0000-000000000001',
    storeId: __ENV.STORE_ID,
    reads: Array.from({ length: 50 }, (_, i) => ({
      epc: `3034257BF400B714${String(i).padStart(8, '0')}`,
      rssi: -65.0,
      antennaPort: 0,
    })),
  };

  const res = http.post(`${BASE}/api/rfid/ingest/batch`, JSON.stringify(batch), { headers });
  check(res, { 'status is 202': (r) => r.status === 202 });
  sleep(0.1);
}
```

Run:
```bash
k6 run -e STORE_ID=<store-uuid> tests/load/rfid-ingest.js
```

### Performance Targets

| Endpoint | p50 | p95 | p99 |
|---|---|---|---|
| `POST /api/auth/login` | < 100ms | < 300ms | < 500ms |
| `GET /api/stores` | < 50ms | < 200ms | < 400ms |
| `POST /api/rfid/ingest/batch` (50 reads) | < 100ms | < 300ms | < 500ms |
| `GET /api/inventory/state` | < 200ms | < 500ms | < 1000ms |
| `GET /api/reporting/kpi/range` | < 300ms | < 800ms | < 2000ms |
| `POST /api/soh/sessions/{id}/complete` | < 500ms | < 1500ms | < 3000ms |

---

## 9. CI Test Pipeline

Tests run automatically on every push/PR via `.github/workflows/ci.yml`.

### What Runs in CI

| Job | Trigger | Commands |
|---|---|---|
| **backend** | All pushes to develop + PRs | `mvn verify -DskipTests` (unit only in CI; integration needs TestContainers) |
| **frontend** | All pushes + PRs | `npm ci && npm run type-check && npm run build` |
| **docker-build** | After backend + frontend pass | `docker buildx build` for all 12 images |

### CI Service Containers

The backend CI job spins up PostgreSQL and Redis as service containers:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    env:
      POSTGRES_DB: storelense_test
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
```

### Smoke Tests in CD

After each deployment to staging, the CD pipeline runs:

```bash
HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}')
[ "$HTTP" = "200" ] || exit 1
```

### Checking CI Results

1. Go to `https://github.com/raghuhr1/StoreLense/actions`
2. Click the latest workflow run
3. Expand each job to see logs

---

## 10. Test Data Reference

### Default Seeded Users

| Username | Password | Role | Store |
|---|---|---|---|
| `admin` | `Admin@StoreLense1` | ADMIN | None (all stores) |

Additional test users can be created via `POST /api/users` (ADMIN token required).

### Default Seeded Roles

| Role Name | Description |
|---|---|
| `ADMIN` | Full system access |
| `STORE_MANAGER` | Store-level management |
| `STORE_ASSOCIATE` | SOH scanning |
| `REFILL_ASSOCIATE` | Task fulfilment |

### Sample EPC Values (for RFID testing)

These are valid GS1 SGTIN-96 encoded EPCs:

```
3034257BF400B71400000001
3034257BF400B71400000002
3034257BF400B71400000003
3034257BF400B71400000004
3034257BF400B71400000005
```

Use any 24-character uppercase hex string as a test EPC. Unknown EPCs go to the DLQ and can be viewed at `GET /api/rfid/dlq`.

### Product Categories (Seeded)

| Code | Name |
|---|---|
| APPAREL | Apparel (Tops, Bottoms, Outerwear, Underwear) |
| FOOTWEAR | Footwear (Casual, Formal, Sport, Sandals) |
| ACCESSORIES | Accessories |
| HOMEWARES | Homewares |
| ELECTRONICS | Electronics |

### Health Check URLs

| Service | URL |
|---|---|
| API Gateway | `http://localhost:8080/health` |
| auth-service | `http://localhost:8081/actuator/health` |
| store-service | `http://localhost:8082/actuator/health` |
| product-service | `http://localhost:8083/actuator/health` |
| inventory-service | `http://localhost:8084/actuator/health` |
| soh-service | `http://localhost:8085/actuator/health` |
| refill-service | `http://localhost:8086/actuator/health` |
| rfid-ingest-service | `http://localhost:8087/actuator/health` |
| rfid-processing-service | `http://localhost:8088/actuator/health` |
| reporting-service | `http://localhost:8089/actuator/health` |
| erp-integration-service | `http://localhost:8090/actuator/health` |
| notification-service | `http://localhost:8091/actuator/health` |
