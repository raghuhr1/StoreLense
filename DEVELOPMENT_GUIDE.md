# StoreLense — Development Guide

> Complete guide for setting up a local development environment, understanding the codebase, and contributing to the project.

---

## Table of Contents

1. [Tech Stack Overview](#1-tech-stack-overview)
2. [Prerequisites](#2-prerequisites)
3. [Repository Structure](#3-repository-structure)
4. [Local Environment Setup](#4-local-environment-setup)
5. [Frontend Development](#5-frontend-development)
6. [Backend Development](#6-backend-development)
7. [Android Development](#7-android-development)
8. [Database & Migrations](#8-database--migrations)
9. [Kafka Topics](#9-kafka-topics)
10. [Service Configuration Reference](#10-service-configuration-reference)
11. [Adding a New Feature](#11-adding-a-new-feature)
12. [Code Conventions](#12-code-conventions)
13. [CI/CD Pipeline](#13-cicd-pipeline)
14. [Debugging & Monitoring](#14-debugging--monitoring)

---

## 1. Tech Stack Overview

| Layer | Technology | Version |
|---|---|---|
| **Frontend** | Next.js (App Router) | 15.0.0 |
| **Frontend UI** | React, Tailwind CSS, Lucide Icons | React 19 |
| **Frontend State** | TanStack React Query | 5.45.1 |
| **Frontend Forms** | React Hook Form + Zod | — |
| **Frontend Charts** | Recharts | — |
| **Frontend HTTP** | Axios (with auto-refresh interceptor) | — |
| **Backend** | Spring Boot 3.3.0, Java 21 | — |
| **Backend ORM** | Spring Data JPA + Hibernate | — |
| **Backend Migrations** | Flyway | — |
| **Backend DTO Mapping** | MapStruct | — |
| **Backend Auth** | JJWT 0.12.5 | — |
| **Backend API Docs** | Springdoc OpenAPI 2.5.0 | — |
| **Backend Resilience** | Resilience4j (circuit breakers) | — |
| **Database** | PostgreSQL 16 | — |
| **Cache** | Redis 7 | — |
| **Message Bus** | Apache Kafka (Bitnami) | — |
| **Android** | Kotlin + Jetpack Compose | API 29–35 |
| **Android RFID** | Zebra EMDK SDK 9.x | — |
| **Android Offline** | Room + WorkManager | — |
| **Android DI** | Hilt | — |
| **Android HTTP** | Retrofit + OkHttp + Moshi | — |
| **Infrastructure** | Docker Compose / Kubernetes | — |

---

## 2. Prerequisites

Install these before starting:

| Tool | Version | Download |
|---|---|---|
| Git | Latest | https://git-scm.com |
| Java (Temurin JDK 21) | 21 LTS | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org |
| Node.js | 20 LTS | https://nodejs.org |
| Docker Desktop | Latest | https://docker.com |
| Android Studio | Ladybug or newer | https://developer.android.com/studio |
| PowerShell | 7+ (for test scripts) | https://aka.ms/powershell |

Verify installations:
```bash
java -version        # openjdk 21.x.x
mvn -version         # Apache Maven 3.9.x
node --version       # v20.x.x
npm --version        # 10.x.x
docker --version     # Docker 27.x.x
docker compose version  # v2.x.x
```

---

## 3. Repository Structure

```
StoreLense/
├── frontend/                   # Next.js 15 web portal
│   ├── src/
│   │   ├── app/               # App Router pages
│   │   │   ├── (auth)/        # Public routes (login)
│   │   │   └── (protected)/   # Auth-guarded routes
│   │   ├── components/        # Shared UI components
│   │   ├── lib/
│   │   │   ├── api/           # API client modules (axios)
│   │   │   └── hooks/         # React Query hooks
│   │   └── types/             # TypeScript types
│   ├── package.json
│   └── Dockerfile
│
├── backend/                    # Spring Boot microservices (Maven multi-module)
│   ├── pom.xml                # Root POM
│   ├── common/                # Shared: JWT, exceptions, DTOs, events
│   ├── auth-service/          # Login, token management, users (port 8081)
│   ├── store-service/         # Store + zone master data (port 8082)
│   ├── product-service/       # Product master + EPC registry (port 8083)
│   ├── inventory-service/     # Inventory state + accuracy (port 8084)
│   ├── soh-service/           # Stock count sessions (port 8085)
│   ├── refill-service/        # Refill tasks (port 8086)
│   ├── rfid-ingest-service/   # RFID batch ingest endpoint (port 8087)
│   ├── rfid-processing-service/ # Kafka consumer, EPC decode (port 8088)
│   ├── reporting-service/     # KPI aggregation + reports (port 8089)
│   ├── erp-integration-service/ # ERP bidirectional sync (port 8090)
│   ├── notification-service/  # WebSocket push (port 8091)
│   └── Dockerfile             # Single multi-stage Dockerfile for all services
│
├── android/                    # Zebra Kotlin/Compose app
│   ├── app/
│   │   ├── src/main/java/com/storelense/zebra/
│   │   │   ├── data/          # Room DB, Retrofit, Repository implementations
│   │   │   ├── domain/        # Models + Repository interfaces
│   │   │   ├── rfid/          # EmDkRfidReader + MockRfidReader
│   │   │   ├── work/          # WorkManager sync workers
│   │   │   ├── di/            # Hilt modules
│   │   │   └── ui/            # Compose screens + ViewModels
│   │   └── build.gradle.kts
│   └── SETUP.md
│
├── deploy/                     # Deployment configuration
│   ├── docker-compose.yml     # Full-stack Docker Compose
│   ├── docker-compose.infra.yml  # Infrastructure only (Kafka for native dev)
│   ├── nginx/nginx.conf       # API gateway + routing rules
│   ├── postgres/init.sql      # DB bootstrap (schemas, app user)
│   ├── .env.example           # Template for environment variables
│   ├── test.ps1               # Integration test suite (PowerShell)
│   └── start-services.ps1     # Start services as native Java processes
│
├── StoreSense/                 # Product documentation
│   ├── 01-business-requirements.md
│   ├── 02-prd.md
│   ├── 03-architecture.md
│   ├── 04-database-design.md
│   ├── 05-api-specification.md
│   ├── 06-user-stories.md
│   └── sql/                   # Consolidated SQL schema files
│
├── .github/workflows/
│   ├── ci.yml                 # CI: build, test, Docker image validation
│   └── cd.yml                 # CD: push images to GHCR, deploy to K8s
│
├── deploy.sh                  # One-shot Ubuntu deployment script
├── DEPLOY.md                  # Deployment guide
├── USER_GUIDE.md              # End-user guide
├── DEVELOPMENT_GUIDE.md       # This file
├── API_GUIDE.md               # Full API reference
└── TEST_GUIDE.md              # Testing guide
```

---

## 4. Local Environment Setup

### Step 1 — Clone

```bash
git clone https://github.com/raghuhr1/StoreLense.git
cd StoreLense
```

### Step 2 — Start Infrastructure (Docker)

You need PostgreSQL, Redis, and Kafka running. Use Docker Compose:

```bash
cd deploy
cp .env.example .env
# Edit .env if needed (defaults work for local development)

# Start infrastructure only (fastest for local dev)
docker compose up -d postgres redis kafka

# Wait for health checks
docker compose ps   # all three should show "healthy"
```

### Step 3 — Initialize the Database

The database is bootstrapped by `deploy/postgres/init.sql` (runs automatically on first container start). Individual service schemas and tables are created by Flyway migrations on first service startup.

To verify the database is ready:
```bash
docker exec -it deploy-postgres-1 psql -U postgres -d storelense -c "\dn"
# Should list: auth, erp, inventory, products, refill, reporting, rfid, soh, stores
```

### Step 4 — Start Backend Services

**Option A — All in Docker (simplest):**
```bash
cd deploy
docker compose up -d --build
```

**Option B — Native Java (faster iteration):**

Requires Java 21, Maven, and infrastructure running from Step 2.

```bash
cd backend

# Build all services (skip tests for speed)
mvn clean package -DskipTests

# Start services (each in its own terminal or backgrounded)
cd deploy
.\start-services.ps1          # Windows PowerShell
# OR manually per service:
java -jar backend/auth-service/target/storelense-auth-service-*.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/storelense \
  --spring.datasource.username=postgres \
  --spring.datasource.password=postgres \
  --spring.kafka.bootstrap-servers=localhost:9092 \
  --server.port=8081
```

### Step 5 — Start Frontend

```bash
cd frontend
npm install
npm run dev
# Frontend available at http://localhost:3000
```

### Step 6 — Verify Everything

```bash
# Quick health checks
curl http://localhost:8080/health
curl http://localhost:8081/actuator/health

# Run full integration tests
cd deploy
pwsh ./test.ps1
```

You should see all tests pass.

---

## 5. Frontend Development

### Dev Server

```bash
cd frontend
npm run dev         # Hot reload on http://localhost:3000
```

### Available Scripts

| Script | Command | Purpose |
|---|---|---|
| Dev server | `npm run dev` | Hot-reload development |
| Production build | `npm run build` | Build optimised output |
| Run production | `npm run start` | Serve production build |
| Lint | `npm run lint` | ESLint check |
| Type check | `npm run type-check` | TypeScript validation (no emit) |

### Environment Variables

Create `frontend/.env.local` for local development:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_WS_URL=ws://localhost:8091/ws
NEXT_PUBLIC_APP_TITLE=StoreLense (Local)
INTERNAL_API_URL=http://localhost:8080
```

> `NEXT_PUBLIC_*` variables are embedded at build time and visible in the browser.  
> `INTERNAL_API_URL` is used by Next.js server-side rendering only.

### Page Structure (App Router)

All pages live under `src/app/`:

```
app/
├── page.tsx                          # Root → redirect to /dashboard
├── (auth)/
│   └── login/page.tsx                # Login page (public)
└── (protected)/                      # Auth-guarded layout
    ├── layout.tsx                    # Sidebar + header wrapper
    ├── dashboard/page.tsx
    ├── cycle-count/
    │   ├── page.tsx                  # Session list
    │   └── [id]/page.tsx             # Session detail
    ├── receiving/page.tsx            # Refill task list
    ├── transfers/page.tsx            # Inter-store transfers
    ├── inventory/
    │   ├── page.tsx
    │   └── [id]/page.tsx             # Product inventory detail
    ├── reports/page.tsx
    ├── devices/page.tsx
    ├── stores/
    │   ├── page.tsx
    │   └── [id]/page.tsx             # Store detail + zones + readers
    └── users/page.tsx
```

### API Client Pattern

All API calls go through `src/lib/api/client.ts` — an Axios instance with:
- `Authorization: Bearer {token}` added to every request
- Silent token refresh on 401 (using the refresh token, retries original request)
- Tokens stored in **memory only** (not localStorage — XSS protection)

```typescript
// src/lib/api/soh.ts — example module
import { apiClient } from './client';

export const listSessions = (storeId: string, status?: string) =>
  apiClient.get('/soh/sessions', { params: { storeId, status } });

export const startSession = (data: StartSessionRequest) =>
  apiClient.post('/soh/sessions', data);

export const completeSession = (id: string) =>
  apiClient.post(`/soh/sessions/${id}/complete`);
```

### React Query Hooks

Data fetching uses TanStack React Query. Add hooks in `src/lib/hooks/`:

```typescript
// src/lib/hooks/useSohSessions.ts
export const useSohSessions = (storeId: string, status?: string) =>
  useQuery({
    queryKey: ['soh-sessions', storeId, status],
    queryFn: () => listSessions(storeId, status),
    staleTime: 30_000,   // 30 seconds
  });
```

### Adding a New Page

1. Create `src/app/(protected)/<page-name>/page.tsx`
2. Add the route to the sidebar nav in `src/components/Layout.tsx`
3. Add an API module in `src/lib/api/<page-name>.ts` if needed
4. Add React Query hooks in `src/lib/hooks/use<PageName>.ts`

---

## 6. Backend Development

### Maven Multi-Module Structure

The backend is a Maven multi-module project. The root `backend/pom.xml` manages all dependencies and versions.

```xml
<!-- Build all modules -->
mvn clean package

<!-- Build a single service -->
mvn clean package -pl common,auth-service -am

<!-- Skip tests for faster builds -->
mvn clean package -DskipTests

<!-- Run tests for a specific service -->
mvn test -pl soh-service
```

### Service Template

Each service follows the same structure:

```
{service-name}/
├── pom.xml
└── src/
    └── main/
        ├── java/com/storelense/{service}/
        │   ├── config/          SecurityConfig, JPA config
        │   ├── controller/      REST controllers (@RestController)
        │   ├── domain/
        │   │   ├── entity/      JPA entities (@Entity)
        │   │   └── repository/  Spring Data repositories
        │   ├── dto/             Request/Response DTOs (records)
        │   ├── mapper/          MapStruct mappers (@Mapper)
        │   └── service/         Business logic
        └── resources/
            ├── application.yml
            └── db/migration/    Flyway SQL scripts
```

### Common Module

All services depend on `backend/common`. It provides:

| Package | Contents |
|---|---|
| `security` | `JwtTokenProvider`, `JwtAuthenticationFilter`, `StoreLensePrincipal` |
| `exception` | `ResourceNotFoundException`, `BusinessException`, `GlobalExceptionHandler` |
| `dto` | `ApiResponse<T>`, `PageResponse<T>` |
| `audit` | `AuditContext` |
| `event` | Kafka event POJOs (`RfidReadEvent`, `SohUpdatedEvent`, `RefillTaskCreatedEvent`) |

### Running a Single Service

```bash
# From the backend directory
mvn spring-boot:run -pl auth-service \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8081 -Dspring.datasource.url=jdbc:postgresql://localhost:5432/storelense"
```

### Adding a New Endpoint

1. Add the DTO record in `dto/`
2. Add the method to the service in `service/`
3. Add the endpoint to the controller in `controller/`
4. Add the `@PreAuthorize("hasAnyRole(...)")` annotation
5. If it needs a new database column: add a Flyway migration in `db/migration/`

### Security Configuration

Each service has a `SecurityConfig` that:
- Configures `JwtAuthenticationFilter` from the `common` module
- Defines which paths are public vs secured
- Sets the `@PreAuthorize` annotation processing enabled

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

### Inter-Service Communication

**Synchronous (REST):** Services call each other via RestTemplate or Feign:
```java
// product-service calls store-service to validate storeId
private final String storeServiceUrl;  // from env var STORE_SERVICE_URL

ResponseEntity<ApiResponse<StoreResponse>> response =
    restTemplate.getForEntity(storeServiceUrl + "/api/stores/" + storeId, ...);
```

**Asynchronous (Kafka):** Use the event POJOs from `common`:
```java
// Publishing
kafkaTemplate.send("rfid.reads.raw", correlationId, event);

// Consuming
@KafkaListener(topics = "rfid.reads.raw", groupId = "rfid-processing")
public void handle(RfidReadEvent event) { ... }
```

---

## 7. Android Development

### Setup

1. Open `android/` in Android Studio (Ladybug or newer)
2. Let Gradle sync complete
3. For **debug builds** (MockRfidReader, emulator): no extra setup needed
4. For **release builds** (real EMDK, physical Zebra device):
   - Create an account at developer.zebra.com
   - Download EMDK for Android 9.x
   - Copy `com.symbol.emdk.jar` to `android/app/libs/`

### Build Variants

| Variant | RFID | Backend URL | Use for |
|---|---|---|---|
| `debug` | MockRfidReader (simulated) | `http://10.0.2.2:8081/` | Emulator development |
| `release` | Real EMDK (Zebra hardware) | `https://api.storelense.internal/` | Physical device testing |

To point debug at a physical backend server:
```kotlin
// android/app/build.gradle.kts
debug {
    buildConfigField("String", "BASE_URL", "\"http://192.168.1.X:8080/\"")
    buildConfigField("Boolean", "USE_MOCK_RFID", "true")
}
```

### Key Classes

| Class | Location | Purpose |
|---|---|---|
| `RfidReader` | `rfid/RfidReader.kt` | Interface: `startScan()`, `stopScan()`, `epcFlow: Flow<String>` |
| `EmDkRfidReader` | `rfid/EmDkRfidReader.kt` | Real EMDK implementation |
| `MockRfidReader` | `rfid/MockRfidReader.kt` | Emits fake EPCs every 200ms (debug) |
| `RfidSyncWorker` | `work/RfidSyncWorker.kt` | Uploads buffered reads when connected |
| `RefillSyncWorker` | `work/RefillSyncWorker.kt` | Pulls refill tasks every 15 minutes |
| `AppDatabase` | `data/local/AppDatabase.kt` | Room database (sessions, tasks, reads) |
| `ApiService` | `data/remote/ApiService.kt` | Retrofit interface for all endpoints |

### WorkManager Retry Policy

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

val syncWork = OneTimeWorkRequestBuilder<RfidSyncWorker>()
    .setConstraints(constraints)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
    .build()
```

Retry schedule: 15s → 30s → 60s → 120s → 240s (max).

---

## 8. Database & Migrations

### Connection Details (Local)

| Parameter | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `storelense` |
| App user | `storelense_app` / `changeme` |
| Root user | `postgres` / `postgres` |

### Schema Layout

Each service owns its own schema. There are no cross-schema foreign key constraints — services resolve references via REST or Kafka events.

| Schema | Service | Key Tables |
|---|---|---|
| `auth` | auth-service | `users`, `roles`, `user_roles`, `refresh_tokens` |
| `stores` | store-service | `stores`, `zones`, `rfid_readers`, `store_config` |
| `products` | product-service | `products`, `epc_tags`, `barcodes`, `product_categories` |
| `inventory` | inventory-service | `inventory_state`, `epc_registry` |
| `soh` | soh-service | `soh_sessions`, `soh_session_items`, `soh_results`, `soh_variance` |
| `refill` | refill-service | `refill_tasks`, `refill_task_items`, `refill_assignments` |
| `rfid` | rfid-processing | `rfid_sessions`, `rfid_reads` (partitioned monthly) |
| `reporting` | reporting-service | `kpi_daily`, `report_snapshots` |
| `audit` | cross-cutting | `audit_log` (partitioned monthly, immutable) |

### Flyway Migrations

Each service manages its own schema migrations:

```
{service}/src/main/resources/db/migration/
├── V1_0__initial_schema.sql      # Creates tables
├── V1_1__seed_data.sql           # Seeds required data (roles, categories)
└── V1_2__add_index.sql           # Performance indexes
```

**Naming convention:** `V{major}_{minor}__{description}.sql`

To create a new migration:
1. Add a new file with the next version number
2. Write idempotent SQL (use `IF NOT EXISTS`, `CREATE OR REPLACE`)
3. Never modify existing migration files — always add new ones
4. Test locally: restart the service and check Flyway logs

### Useful Queries

```sql
-- Connect
psql -h localhost -U storelense_app -d storelense

-- List schemas
\dn

-- Check Flyway migration status for a service
SELECT version, description, success, installed_on
FROM auth.flyway_schema_history ORDER BY installed_rank;

-- Check recent SOH sessions
SELECT id, store_id, session_type, status, total_epc_reads, accuracy_pct
FROM soh.soh_results r JOIN soh.soh_sessions s ON r.session_id = s.id
ORDER BY r.result_generated_at DESC LIMIT 10;

-- Check inventory accuracy by store
SELECT p.sku, i.quantity_on_hand, i.quantity_expected, i.accuracy_pct
FROM inventory.inventory_state i
JOIN products.products p ON i.product_id = p.id
WHERE i.store_id = '<uuid>' AND i.accuracy_pct < 95
ORDER BY i.accuracy_pct ASC;
```

---

## 9. Kafka Topics

| Topic | Producer | Consumer | Message Schema |
|---|---|---|---|
| `rfid.reads.raw` | rfid-ingest-service | rfid-processing-service | `RfidReadEvent` |
| `rfid.soh.updated` | rfid-processing-service | soh-service, notification-service | `SohUpdatedEvent` |
| `soh.session.updated` | soh-service | notification-service, erp-integration | `SohSessionEvent` |
| `refill.task.created` | refill-service | notification-service | `RefillTaskCreatedEvent` |
| `erp.product.sync` | erp-integration-service | product-service | Product master payload |
| `erp.inventory.expected` | erp-integration-service | inventory-service | Expected qty payload |

### Kafka Dev Commands

```bash
# List all topics
docker exec deploy-kafka-1 kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Monitor RFID reads in real time
docker exec deploy-kafka-1 kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic rfid.reads.raw --from-beginning

# Check consumer group lag
docker exec deploy-kafka-1 kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group rfid-processing
```

### Dead Letter Queue (DLQ)

Failed RFID processing events are stored in memory (in-process DLQ map) and exposed via `GET /api/rfid/dlq`. Replay them with `POST /api/rfid/dlq/{key}/replay`.

---

## 10. Service Configuration Reference

All services share these environment variables (from `deploy/.env`):

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `postgres` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `storelense` | Database name |
| `DB_USERNAME` | `storelense_app` | App DB user |
| `DB_PASSWORD` | `changeme` | App DB password |
| `REDIS_HOST` | `redis` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(blank)_ | Redis password |
| `KAFKA_BOOTSTRAP` | `kafka:9092` | Kafka bootstrap server |
| `JWT_SECRET` | `storelense-dev-secret-key-minimum-32-chars` | JWT signing secret (min 32 chars) |
| `PRODUCT_SERVICE_URL` | `http://product-service:8083` | Product service base URL |
| `STORE_SERVICE_URL` | `http://store-service:8082` | Store service base URL |
| `ERP_BASE_URL` | `http://localhost:9000` | ERP API base URL |
| `ERP_API_KEY` | `dev-key` | ERP API authentication key |
| `ERP_PUSH_SOH_ENABLED` | `false` | Enable SOH result push to ERP |

### Service Ports

| Service | Port |
|---|---|
| nginx-gateway | 8080 |
| auth-service | 8081 |
| store-service | 8082 |
| product-service | 8083 |
| inventory-service | 8084 |
| soh-service | 8085 |
| refill-service | 8086 |
| rfid-ingest-service | 8087 |
| rfid-processing-service | 8088 |
| reporting-service | 8089 |
| erp-integration-service | 8090 |
| notification-service | 8091 |

---

## 11. Adding a New Feature

### Example: Add a "Transfer" endpoint to store-service

**1. Add the migration**
```sql
-- store-service/src/main/resources/db/migration/V1_2__add_transfers.sql
CREATE TABLE stores.transfers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_store  UUID NOT NULL REFERENCES stores.stores(id),
    to_store    UUID NOT NULL REFERENCES stores.stores(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**2. Add the JPA entity**
```java
// store-service/src/main/java/.../domain/entity/Transfer.java
@Entity
@Table(schema = "stores", name = "transfers")
public class Transfer {
    @Id @GeneratedValue UUID id;
    UUID fromStore;
    UUID toStore;
    String status;
    OffsetDateTime createdAt;
}
```

**3. Add the repository**
```java
public interface TransferRepository extends JpaRepository<Transfer, UUID> {
    List<Transfer> findByFromStoreOrToStore(UUID fromStore, UUID toStore);
}
```

**4. Add DTOs**
```java
public record CreateTransferRequest(
    @NotNull UUID fromStore,
    @NotNull UUID toStore,
    @NotEmpty List<TransferItemRequest> items
) {}
```

**5. Add service logic**
```java
@Service @RequiredArgsConstructor
public class TransferService {
    private final TransferRepository repo;

    public Transfer create(CreateTransferRequest req, StoreLensePrincipal principal) {
        // validate, create, return
    }
}
```

**6. Add the controller endpoint**
```java
@RestController
@RequestMapping("/api/transfers")
@PreAuthorize("hasAnyRole('ADMIN','STORE_MANAGER')")
public class TransferController {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransferResponse> create(@Valid @RequestBody CreateTransferRequest req) {
        return ApiResponse.success(service.create(req, principal()));
    }
}
```

**7. Add Nginx routing in `deploy/nginx/nginx.conf`**
```nginx
location /api/transfers {
    proxy_pass http://store-service;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

**8. Add the frontend API module**
```typescript
// frontend/src/lib/api/transfers.ts
export const createTransfer = (data: CreateTransferRequest) =>
  apiClient.post('/transfers', data);
```

---

## 12. Code Conventions

### Java

- **Java 21** — use records for DTOs, sealed classes where appropriate
- All controllers return `ApiResponse<T>` from the `common` module
- All entities use `UUID` primary keys (`gen_random_uuid()`)
- Use `@PreAuthorize` for role-based access — do not check roles in service methods
- Timestamps: `OffsetDateTime` (UTC), managed by database triggers on `updated_at`
- Soft delete: use `is_active BOOLEAN` — never hard-delete master data
- No `@Transactional` on controllers — apply it at the service method level

### TypeScript / React

- Use `const` + arrow functions for components
- Use TanStack React Query for all server state — no `useState` for async data
- Tokens go in memory only — never `localStorage` or `sessionStorage`
- Use Zod for all form validation schemas
- Use the `ApiResponse<T>` type wrapper matching the backend envelope

### SQL

- All primary keys: `UUID DEFAULT gen_random_uuid()`
- All timestamps: `TIMESTAMPTZ NOT NULL DEFAULT now()`
- All `updated_at` columns: managed by `public.fn_set_updated_at()` trigger
- Use `is_active BOOLEAN NOT NULL DEFAULT true` for soft-deletable rows
- No cross-schema foreign key constraints

### Git

- Branch from `develop` for features: `feature/soh-session-export`
- Branch from `main` for hotfixes: `hotfix/fix-token-refresh`
- Commit messages: imperative present tense (`Add`, `Fix`, `Remove`)
- Pull requests target `develop`; `main` is production-only

---

## 13. CI/CD Pipeline

### CI (`.github/workflows/ci.yml`)

Triggers on: pull requests to `main` or `develop`; pushes to `develop`.

| Job | What it does |
|---|---|
| **backend** | Builds all Java modules, runs tests. Needs PostgreSQL + Redis service containers. |
| **frontend** | Runs `npm ci`, `type-check`, `build`. |
| **docker-build** | Builds all Docker images (validates Dockerfiles). Runs after backend + frontend pass. |

### CD (`.github/workflows/cd.yml`)

Triggers on: pushes to `main`; published releases.

| Job | What it does |
|---|---|
| **build-push** | Builds multi-arch images (linux/amd64 + arm64), pushes to GHCR |
| **deploy-staging** | Updates K8s manifests with new image tag, applies to staging namespace, runs smoke tests |
| **deploy-production** | Manual approval gate → applies to production namespace (release events only) |

### Image Registry

Images are pushed to GitHub Container Registry:
```
ghcr.io/raghuhr1/storelense-auth-service:latest
ghcr.io/raghuhr1/storelense-frontend:latest
...
```

### Required Secrets

Set these in GitHub → Settings → Secrets:

| Secret | Description |
|---|---|
| `KUBE_CONFIG_STAGING` | Base64-encoded kubeconfig for staging cluster |
| `KUBE_CONFIG_PROD` | Base64-encoded kubeconfig for production cluster |

---

## 14. Debugging & Monitoring

### Backend Service Logs

```bash
# Docker Compose
docker compose logs -f auth-service --tail=100

# Native process (from start-services.ps1)
# Logs go to: %TEMP%\storelense-logs\{service-name}.log
```

### Actuator Endpoints

Every Spring Boot service exposes:

| Path | Purpose |
|---|---|
| `/actuator/health` | Service + dependency health |
| `/actuator/metrics` | Available metric names |
| `/actuator/prometheus` | Prometheus scrape endpoint |

```bash
curl http://localhost:8081/actuator/health | jq
```

### Redis Inspection

```bash
redis-cli -h localhost

# JWT blacklist entries
KEYS jwt:blacklist:*

# EPC lookup cache
KEYS epc:*

# Session dedup sets
KEYS rfid:session:*
```

### Kafka Debugging

```bash
# Consumer group lag (is processing keeping up?)
docker exec deploy-kafka-1 kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group rfid-processing

# Consume from DLQ topic
docker exec deploy-kafka-1 kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic rfid.reads.dlq --from-beginning
```

### Frontend Debugging

- Open browser DevTools → Network tab: inspect API calls, JWT token in request headers
- React Query DevTools: add `ReactQueryDevtools` component to layout in development
- Next.js error overlay: shown automatically in dev mode on runtime errors

### Common Issues

| Problem | Likely Cause | Fix |
|---|---|---|
| `401 Unauthorized` | JWT expired or wrong secret | Check `JWT_SECRET` matches across services; re-login |
| `503 Service Unavailable` | Downstream service not healthy | Check `docker compose ps`; look at service logs |
| `FlywayException` | Migration conflict or version mismatch | `docker compose down -v` → restart to reinitialise |
| Kafka `LEADER_NOT_AVAILABLE` | Kafka still starting | Wait 30s; Kafka needs time to elect leader |
| RFID reads not appearing | Processing service not consuming | Check `rfid-processing-service` logs for consumer errors |
| Frontend 404 on refresh | Next.js static export routing | Use `output: 'standalone'` in `next.config.js` |
