# Detailed Design Document

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.0
**Date:** 2026-06-06

---

## 1. Purpose

This document provides the low-level design for StoreLense: component interactions, sequence flows, class responsibilities, and key design decisions not captured in the architecture overview.

---

## 2. Authentication Design

### 2.1 JWT Token Strategy

| Token | Lifetime | Storage | Purpose |
|---|---|---|---|
| Access token | 15 minutes | In-memory (JS variable) | Authorise every API call |
| Refresh token | 7 days | Room DB (encrypted) on mobile; server-side session on web | Renew access token silently |

Access tokens are **never stored in localStorage** to prevent XSS theft. The access token is held in a React context variable and lost on page refresh — the refresh token is used to silently re-obtain it.

### 2.2 Login Sequence

```
Client                  nginx                 auth-service         Redis/PostgreSQL
  │                       │                        │                      │
  │ POST /api/auth/login   │                        │                      │
  │──────────────────────>│                        │                      │
  │                       │ proxy_pass              │                      │
  │                       │──────────────────────>│                      │
  │                       │                        │ validate credentials │
  │                       │                        │─────────────────────>│
  │                       │                        │<─────────────────────│
  │                       │                        │ generate JWT         │
  │                       │                        │ store refresh_token  │
  │                       │                        │─────────────────────>│
  │                       │                        │<─────────────────────│
  │                       │<──────────────────────│                      │
  │<──────────────────────│                        │                      │
  │ { accessToken, refreshToken }                  │                      │
```

### 2.3 Request Authentication Sequence

JWT validation is done **in each microservice** (not at nginx), using a shared `JwtFilter` from the `common` module.

```
Client                  nginx                 microservice          Redis
  │                       │                        │                  │
  │ GET /api/stores        │                        │                  │
  │ Authorization: Bearer {token}                  │                  │
  │──────────────────────>│                        │                  │
  │                       │ proxy_pass              │                  │
  │                       │──────────────────────>│                  │
  │                       │                        │ JwtFilter        │
  │                       │                        │ - verify signature
  │                       │                        │ - check expiry    │
  │                       │                        │ - check blacklist │
  │                       │                        │────────────────>│
  │                       │                        │<────────────────│
  │                       │                        │ populate SecurityContext
  │                       │                        │ extract storeId, roles
  │                       │                        │ proceed to controller
  │                       │<──────────────────────│                  │
  │<──────────────────────│                        │                  │
```

### 2.4 Token Refresh Sequence

```
Client                         auth-service
  │                                 │
  │ POST /api/auth/refresh           │
  │ { refreshToken: "eyJ..." }       │
  │────────────────────────────────>│
  │                                 │ verify refresh token hash
  │                                 │ check not revoked
  │                                 │ issue new access token
  │                                 │ rotate refresh token (revoke old, issue new)
  │<────────────────────────────────│
  │ { accessToken, refreshToken }   │
```

---

## 3. RFID Processing Pipeline Design

### 3.1 End-to-End Sequence

```
ZebraApp         rfid-ingest        Kafka            rfid-processing      product-service    Redis
    │                 │               │                     │                    │              │
    │ POST /rfid/ingest/batch         │                     │                    │              │
    │────────────────>│               │                     │                    │              │
    │                 │ validate       │                     │                    │              │
    │                 │ batch         │                     │                    │              │
    │                 │ publish ──────>│ rfid.reads.raw      │                    │              │
    │<────────────────│ { accepted }  │                     │                    │              │
    │                 │               │ consume ────────────>│                    │              │
    │                 │               │                     │ decode EPC          │              │
    │                 │               │                     │ lookup EPC→SKU ─────>│              │
    │                 │               │                     │ (cache miss)        │ GET /products│
    │                 │               │                     │                    │──────────────>│
    │                 │               │                     │<───────────────────│ (cache hit)   │
    │                 │               │                     │ dedup check ────────────────────>│
    │                 │               │                     │ (already seen in session?)       │
    │                 │               │                     │<────────────────────────────────│
    │                 │               │                     │ persist rfid_reads  │              │
    │                 │               │ rfid.soh.updated ───<│                    │              │
    │                 │               │                     │                    │              │
```

### 3.2 EPC Deduplication Design

Each SOH session maintains a Redis SET: `rfid:dedup:{sessionId}:{storeId}`.

- On every EPC from a batch: `SADD rfid:dedup:{sessionId}:{storeId} {epc}` — returns 1 (new) or 0 (duplicate).
- Only new EPCs are persisted to `rfid_reads` and counted.
- SET expires after session duration + 5 minutes.
- On session close, the key is explicitly deleted.

### 3.3 SOH Result Calculation

After `POST /soh/sessions/{id}/complete`:

1. Aggregate `rfid_reads` for the session → counted quantity per `product_id` × `zone_id`.
2. Load `inventory.inventory_state` for same store + products → `quantity_expected`.
3. Compute `variance = counted - expected` per product.
4. Write `soh.soh_results` (session summary) and `soh.soh_variance` (per-product detail).
5. Update `inventory.inventory_state` with new `quantity_on_hand` and `accuracy_pct`.
6. Publish `soh.session.updated` Kafka event.

---

## 4. Frontend Design

### 4.1 AuthContext and Token Management

```
AuthContext (React Context)
├── accessToken: string | null       — held in memory only
├── user: { id, username, roles[] }
├── login(username, password)        — calls POST /api/auth/login
├── logout()                         — calls POST /api/auth/logout, clears context
└── refreshAccessToken()             — called silently by axios interceptor on 401

Axios request interceptor:
  - Injects Authorization: Bearer {accessToken} on every request

Axios response interceptor:
  - On 401: calls refreshAccessToken() once, retries original request
  - On second 401: calls logout() and redirects to /login
```

### 4.2 Role-Based Navigation

```
Sidebar
├── Dashboard        — ALL roles
├── SOH Sessions     — STORE_MANAGER, STORE_ASSOCIATE
├── Refill Tasks     — STORE_MANAGER, REFILL_ASSOCIATE
├── Inventory        — STORE_MANAGER
├── Reporting        — ADMIN, STORE_MANAGER
├── Stores           — ADMIN only
├── Products         — ADMIN only
└── Users            — ADMIN only

AuthGuard (wraps each (protected) route):
  - Reads roles from AuthContext
  - If user lacks required role → redirects to /dashboard
  - If not logged in → redirects to /login
```

### 4.3 React Query Patterns

All data fetching uses TanStack Query v5:

```typescript
// Standard list query
const { data, isLoading } = useQuery({
  queryKey: ['stores', searchTerm],
  queryFn: () => storesApi.list({ search: searchTerm, size: 50 }),
})

// Mutation with cache invalidation
const createMutation = useMutation({
  mutationFn: (body) => storesApi.create(body),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stores'] }),
})
```

Cache keys are arrays: `['resource', filterParams]`. Mutations invalidate the parent list key.

### 4.4 DataTable Component

Reusable `DataTable<T>` built on `@tanstack/react-table`:
- Column definitions passed as `ColumnDef<T>[]`
- Built-in client-side search (when `searchable={true}`)
- Loading skeleton via `isLoading` prop
- Empty state message when `data.length === 0`

---

## 5. Backend Service Design Patterns

### 5.1 Common Module (`common`)

All microservices import the `common` Maven module which provides:

| Component | Purpose |
|---|---|
| `JwtFilter` | `OncePerRequestFilter` — validates JWT, populates `SecurityContext` |
| `ApiResponse<T>` | Standard response envelope with `success`, `data`, `message`, `code` |
| `PageResponse<T>` | Pagination wrapper: `content`, `totalElements`, `totalPages`, `page`, `size` |
| `KafkaTopics` | String constants for all Kafka topic names |
| `JwtUtil` | Token parsing and claim extraction |
| `GlobalExceptionHandler` | `@ControllerAdvice` — maps exceptions to standard error responses |

### 5.2 Service Layer Pattern

Each service follows:

```
Controller → Service → Repository (Spring Data JPA)
               │
               └── Kafka producer (for domain events)
               └── Redis operations (for caching/dedup)
               └── REST client (for cross-service calls — product lookups, etc.)
```

Cross-service REST calls use Spring's `RestClient` (Spring 6.1) with:
- Base URL injected via `@ConfigurationProperties`
- Circuit breaker via Resilience4j `@CircuitBreaker`
- Retry via Resilience4j `@Retry`

### 5.3 Kafka Topic Design

| Topic | Producer | Consumers | Purpose |
|---|---|---|---|
| `rfid.reads.raw` | rfid-ingest-service | rfid-processing-service | Raw EPC reads for processing |
| `rfid.soh.updated` | rfid-processing-service | soh-service | Trigger SOH delta calculation |
| `soh.session.updated` | soh-service | notification-service | Push WebSocket event to UI |
| `erp.product.sync` | erp-integration-service | product-service | Sync product master from ERP |
| `erp.inventory.expected` | erp-integration-service | inventory-service | Update expected inventory quantities |
| `soh.result.completed` | soh-service | erp-integration-service | Push SOH results back to ERP |
| `refill.task.created` | refill-service | notification-service | Notify assigned associate |

All topics use 3 partitions, replication factor 1 (single-broker MVP; increase for production).

### 5.4 ConfigurationProperties Pattern

Each service defines a `@ConfigurationProperties` record for its config:

```java
@ConfigurationProperties(prefix = "storelense.service")
public record ServiceProperties(
    String upstreamUrl,
    Duration timeout,
    int pageSize
) { ... }
```

`@ConfigurationPropertiesScan` on the main class registers all properties beans. **Note:** SpEL expressions referencing these beans must use the property key directly (`${storelense.service.timeout}`) — the bean name assigned by Spring's scanner is the FQCN, not the simple camelCase name.

### 5.5 Error Handling

`GlobalExceptionHandler` in `common` maps:

| Exception | HTTP Status | Code |
|---|---|---|
| `EntityNotFoundException` | 404 | `NOT_FOUND` |
| `DuplicateResourceException` | 409 | `DUPLICATE` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `AccessDeniedException` | 403 | `FORBIDDEN` |
| `JwtException` | 401 | `INVALID_TOKEN` |
| `RuntimeException` (unhandled) | 500 | `INTERNAL_ERROR` |

---

## 6. Database Design Patterns

### 6.1 Flyway Migration Structure

Each service has:
```
src/main/resources/db/migration/
├── V1_0__initial_schema.sql       — schema + tables + indexes
├── V1_1__seed_roles.sql           — seed ADMIN/STORE_MANAGER etc. roles
└── V1_2__add_feature_x.sql        — incremental changes
```

Naming: `V{major}_{minor}__{description}.sql`. Forward-only in production.

### 6.2 Soft Delete Pattern

Master data tables (stores, products, users, zones) use `is_active BOOLEAN DEFAULT true`. Deactivation sets `is_active = false`. All queries for active records add `WHERE is_active = true`.

### 6.3 Audit Trigger Pattern

Critical tables emit to `audit.audit_log` via a PostgreSQL trigger:

```sql
CREATE OR REPLACE FUNCTION fn_audit_log()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO audit.audit_log (schema_name, table_name, operation, record_id, old_values, new_values)
  VALUES (TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP,
          COALESCE(NEW.id, OLD.id),
          CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE row_to_json(OLD) END,
          CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE row_to_json(NEW) END);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

## 7. Notification Service Design

### 7.1 WebSocket Architecture

The notification-service uses Spring WebSocket with STOMP over SockJS:

```
Client (browser/mobile)
  └── ws://gateway:8080/ws  (nginx proxied → notification-service:8091)
       └── STOMP CONNECT
            └── SUBSCRIBE /topic/store/{storeId}/soh
            └── SUBSCRIBE /topic/user/{userId}/tasks

Kafka Consumer (notification-service)
  └── Consumes: soh.session.updated, refill.task.created
  └── SimpMessagingTemplate.convertAndSend("/topic/store/{storeId}/...", payload)
```

### 7.2 Session Authentication

WebSocket handshake uses JWT from query parameter: `ws://host/ws?token={accessToken}`. The `ChannelInterceptor` validates the token and populates the session principal.

---

## 8. Mobile App Design

### 8.1 Architecture Layers

```
UI Layer (Jetpack Compose)
    └── ViewModels (StateFlow)
         └── Use Cases / Repositories
              ├── Local: Room DAO
              └── Remote: Retrofit API + OkHttp interceptors
                   └── JWT Authenticator (auto-refresh on 401)

WorkManager Workers:
  ├── SyncPendingReadsWorker    — upload buffered EPC reads on reconnect
  ├── SyncTasksWorker           — pull latest task assignments
  └── Constraints: NetworkType.CONNECTED
```

### 8.2 RFID SDK Integration

```
RfidReaderInterface (interface)
├── ZebraRfidReader           — real implementation using EMDK SDK
│    └── RfidEventsListener
│         └── eventReadNotify(readEvents[]) → emit to StateFlow
└── MockRfidReader            — dev/test implementation
     └── emits pre-configured EPC list on trigger

Selection at runtime: if EMDK service available → ZebraRfidReader else MockRfidReader
```

---

## 9. Deployment Architecture (Docker Compose)

### 9.1 Container Topology

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Docker Compose Network                       │
│                                                                     │
│  ┌─────────┐  ┌───────┐  ┌──────────────────────────────────────┐  │
│  │postgres │  │ redis │  │              kafka                   │  │
│  │:5432    │  │:6379  │  │  :9092 (internal) :29092 (external)  │  │
│  └────┬────┘  └───┬───┘  └──────────────────┬───────────────────┘  │
│       │           │                          │                      │
│  ┌────▼───────────▼──────────────────────────▼────────────────┐    │
│  │                    Spring Boot Services                     │    │
│  │  auth:8081  store:8082  product:8083  inventory:8084        │    │
│  │  soh:8085   refill:8086  rfid-ingest:8087                   │    │
│  │  rfid-processing:8088  reporting:8089  erp:8090  notif:8091 │    │
│  └────────────────────────────────┬────────────────────────────┘    │
│                                   │                                 │
│  ┌────────────────────────────────▼────────────────────────────┐    │
│  │              nginx-gateway  :8080                           │    │
│  │  resolver 127.0.0.11; lazy DNS → no startup ordering needed │    │
│  └────────────────────────────────┬────────────────────────────┘    │
│                                   │                                 │
│  ┌────────────────────────────────▼────────────────────────────┐    │
│  │              frontend (Next.js)  :3000                      │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.2 Build Strategy

The backend uses a **multi-module Maven monorepo**. A single `Dockerfile` at the root `backend/` directory builds any service via `--build-arg SERVICE_NAME=auth-service`. This means one Docker image build invocation per service, sharing the Maven dependency cache layer.

The frontend uses a **multi-stage Docker build**:
1. Stage 1 (`deps`): install npm dependencies
2. Stage 2 (`builder`): run `npm run build`
3. Stage 3 (`runner`): copy `.next/standalone` only — minimal runtime image

---

## 10. Security Design

### 10.1 JWT Claims Structure

```json
{
  "sub": "user-uuid",
  "username": "mgr_p037",
  "roles": ["STORE_MANAGER"],
  "storeId": "store-uuid",
  "iat": 1717660000,
  "exp": 1717660900,
  "jti": "unique-token-id"
}
```

`jti` is used as the Redis blacklist key on logout: `SET jwt:blacklist:{jti} 1 EX {remaining_ttl}`.

### 10.2 Store-Scoped Data Access

Every service enforces store isolation:
- Store-scoped roles have `storeId` in JWT.
- Extracted by `JwtFilter` and available via `SecurityContext`.
- Service methods check: if current user's `storeId != null` and `storeId != requestedStoreId` → throw `AccessDeniedException`.
- ADMIN users have `storeId = null` — bypasses store check.

### 10.3 Password Policy

- Minimum 8 characters
- Must contain: uppercase letter, lowercase letter, digit, special character
- Hashed with bcrypt (cost factor 12)
- Password change timestamp tracked; forced rotation policy configurable

---

## 11. Configuration Management

### 11.1 Environment Variable Reference

All sensitive config is injected via environment variables in `deploy/docker-compose.yml`. Defaults are safe for development but must be overridden for production:

| Variable | Service | Default | Production Note |
|---|---|---|---|
| `DB_PASSWORD` | All services | `changeme` | Use strong password; store in secret manager |
| `JWT_SECRET` | auth-service | `storelense-dev-secret-key-minimum-32-chars` | Minimum 32 chars; rotate periodically |
| `REDIS_PASSWORD` | auth, product, rfid | _(empty)_ | Set password in production |
| `POSTGRES_PASSWORD` | postgres | `postgres` | Superuser password; use strong value |
| `ERP_BASE_URL` | erp-integration | `http://localhost:9000` | Real ERP endpoint |
| `ERP_API_KEY` | erp-integration | _(empty)_ | ERP API key |
| `ERP_PUSH_SOH_ENABLED` | erp-integration | `false` | Set `true` when ERP ready |

### 11.2 deploy/.env File

Create `deploy/.env` (gitignored) for production secrets:
```
DB_PASSWORD=your-strong-db-password
JWT_SECRET=your-minimum-32-char-secret-key-here
REDIS_PASSWORD=your-redis-password
POSTGRES_PASSWORD=your-postgres-superuser-password
ERP_BASE_URL=https://erp.yourdomain.com/api
ERP_API_KEY=your-erp-api-key
ERP_PUSH_SOH_ENABLED=true
```
