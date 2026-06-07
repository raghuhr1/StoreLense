# L1 and L2 Support Guide — Troubleshooting Runbook

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.0
**Date:** 2026-06-06

---

## 1. Support Tiers

| Tier | Who | Scope | Escalation |
|---|---|---|---|
| **L1** | Store IT staff / Helpdesk | User access issues, portal navigation, login problems, "data not showing" | Escalate to L2 if not resolved in 30 min |
| **L2** | Platform IT admin | Service failures, Docker issues, DB problems, API errors | Escalate to Dev team if not resolved in 2 hours |
| **Dev** | Development team | Code bugs, data corruption, Kafka failures, integration failures | P1 issues within 4 hours |

---

## 2. Quick Reference — System Status Check

Run this on the server to get an instant system snapshot:

```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml ps --format "table {{.Name}}\t{{.Status}}"
```

All 16 containers should show `(healthy)` or `Up`. Any `(unhealthy)` or `Exited` indicates a problem.

```bash
# Check nginx gateway is responding
curl -s http://localhost:8080/health
# Expected: {"status":"UP"}

# Check frontend is responding
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000
# Expected: 200
```

---

## 3. L1 Issues — User-Reported Problems

### L1-01: User Cannot Log In

**Symptoms:** "Invalid username or password" error on login page

**Check 1 — Is the username correct?**
- Admin provides list of valid usernames created during seed
- For P037: `admin`, `mgr_p037`, `asc_p037`, `asc2_p037`, `rfl_p037`

**Check 2 — Is the account locked?**
```bash
# On server: check if user is locked
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "SELECT username, is_active, failed_login_attempts, locked_until FROM auth.users WHERE username = 'mgr_p037';"
```
If `locked_until` is in the future: wait until the lockout expires, or reset:
```bash
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "UPDATE auth.users SET failed_login_attempts=0, locked_until=NULL WHERE username='mgr_p037';"
```

**Check 3 — Is auth-service healthy?**
```bash
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
```
If not healthy → escalate to L2.

**Check 4 — Correct password?**
Passwords set by seed script:
- `admin` → `Admin@StoreLense1`
- `mgr_p037` → `Manager@P0371!`
- `asc_p037` → `Assoc@P0371!`
- `rfl_p037` → `Refill@P0371!`
- `asc2_p037` → `Assoc2@P0371!`

**Resolution:** Reset password via Admin portal or SQL:
```bash
# Generate bcrypt hash for new password (use bcrypt tool or Java utility)
# Then update:
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "UPDATE auth.users SET password_hash='\$2a\$12\$...' WHERE username='mgr_p037';"
```

---

### L1-02: "Products Menu Not Visible" or Page Not Loading

**Symptoms:** User says they can't see a menu item (Products, Users, Stores)

**Check:** Role-based visibility — Products and Users menus are ADMIN only. Confirm the user's role.

```bash
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "SELECT u.username, r.name FROM auth.users u 
   JOIN auth.user_roles ur ON u.id = ur.user_id 
   JOIN auth.roles r ON ur.role_id = r.id 
   WHERE u.username = 'mgr_p037';"
```

If the role is wrong, update in Admin portal (Users page) or contact L2.

---

### L1-03: Data Not Showing / Empty Tables

**Symptoms:** User sees empty lists on Stores, Products, SOH, or Refill pages

**Check 1 — Was seed data loaded?**
```bash
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "SELECT COUNT(*) FROM stores.stores; SELECT COUNT(*) FROM products.products; SELECT COUNT(*) FROM auth.users;"
```
If all 0 → seed has not been run. Run seed script (see Deployment Guide, Step 6).

**Check 2 — Is the user's store assigned correctly?**
A STORE_MANAGER sees only their store's data. If `store_id` is null for their user, they won't see any data.

**Check 3 — Filter state**
Ask user to clear any active filters (search box, date filters) and refresh.

---

### L1-04: SOH Session Not Completing

**Symptoms:** Associate says they scanned items but session won't complete; still shows "in progress"

**Check 1 — Is rfid-processing-service healthy?**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs rfid-processing-service --tail=30
```
Look for errors.

**Check 2 — Was `POST .../complete` called?**
The session doesn't auto-complete on scan; the user must explicitly press "Complete Session" in the app.

**Check 3 — Cancel and restart if stuck**
If session is genuinely stuck (no updates for > 10 minutes):
```bash
# Cancel stuck session
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@StoreLense1"}' | jq -r '.data.accessToken')

curl -s -X POST http://localhost:8080/api/soh/sessions/{SESSION_ID}/cancel \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Admin cancelled stuck session"}'
```

---

### L1-05: Refill Task Not Appearing on Associate's Device

**Symptoms:** Task assigned in portal but associate cannot see it on Zebra device

**Check 1 — Is the task actually assigned?**
```bash
# Verify in portal: Refill → Tasks → filter by assignee
```

**Check 2 — Has the device synced?**
- Open app → pull-to-refresh or wait for WorkManager sync (triggers when connected)
- Check device is on Wi-Fi

**Check 3 — Correct user?**
Confirm the task was assigned to the associate's username, not another user.

---

### L1-06: Web Portal Not Loading (Blank Page / 404)

**Check:** Is frontend container running?
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml ps frontend
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000
```
If HTTP 200 but blank page → likely a browser cache issue. Ask user to hard-refresh (`Ctrl+Shift+R`).
If not running → escalate to L2.

---

## 4. L2 Issues — Technical Platform Problems

### L2-01: Service Showing "Unhealthy" or "Exited"

**Step 1 — Check container status**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml ps -a
```

**Step 2 — View logs**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs <service-name> --tail=50
```

**Step 3 — Identify error type**

| Log Pattern | Cause | Fix |
|---|---|---|
| `Connection refused ... postgres` | DB not ready | Wait; check postgres health |
| `Communications link failure` / `HikariPool ... timeout` | DB connectivity issue | Check DB container is running |
| `APPLICATION FAILED TO START` | Spring Boot startup error | Check full logs; likely config/migration issue |
| `UnsatisfiedDependencyException` | Missing Spring bean | Check application.yml, fix and redeploy |
| `Flyway ... Unable to obtain Jdbc Connection` | DB not available at startup | DB container unhealthy; restart DB first |
| `host not found in upstream` | nginx DNS issue | Verify `resolver 127.0.0.11` in nginx.conf |
| `Exception parsing failed ... NumberFormatException` | Invalid property format | Check application.yml duration format (use ISO-8601: PT6H not 6h) |

**Step 4 — Restart the service**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml restart <service-name>
# If restart doesn't help:
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml up -d --build <service-name>
```

---

### L2-02: nginx-gateway Showing "Unhealthy" (But Routing Works)

**Symptom:** `nginx-gateway` shows `(unhealthy)` in `docker compose ps`, but API calls succeed.

**Cause:** Docker's internal healthcheck probe (`wget -qO- http://localhost:8080/health`) may be failing due to network timing in the container, but nginx itself is running fine.

**Verify nginx is functional:**
```bash
curl -s http://localhost:8080/health
# If returns {"status":"UP"} — nginx is fine. Healthcheck is a cosmetic issue.
```

**Resolution:** No action needed if requests succeed. To investigate the probe:
```bash
docker exec storelense-nginx-gateway-1 wget -qO- http://localhost:8080/health; echo "exit: $?"
```
If exit code is non-zero, check nginx error logs:
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs nginx-gateway --tail=20 | grep -i error
```

---

### L2-03: Spring Boot Service Takes Too Long to Start

**Symptom:** Service shows `health: starting` for > 5 minutes

**Check:** On a low-RAM server, Spring Boot can take 3–4 minutes. The healthcheck `start_period` is 180s (3 min) — if it's still starting after that, Flyway migrations may be running.

**View logs to see progress:**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs store-service --tail=20
```

Look for `Flyway Community Edition ... Starting...` — migrations are running. Wait for them to complete.

If service fails after migrations:
```bash
# Check Flyway migration errors
docker compose logs <service-name> 2>&1 | grep -E "Flyway|ERROR|WARN"
```

**Resolution:** If Flyway fails due to a bad migration, contact Dev team. Do NOT manually alter DB tables without Dev approval.

---

### L2-04: erp-integration-service or rfid-processing-service Crashes at Startup

**Check logs:**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs erp-integration-service --tail=30
```

**Known issue — `@Scheduled` expression error:**
```
NumberFormatException: For input string: "6h"
```
**Fix:** Ensure `application.yml` uses `product-sync-interval: PT6H` (ISO-8601), not `6h`.
```bash
grep "product-sync-interval" /opt/StoreLense-master/backend/erp-integration-service/src/main/resources/application.yml
# Should show: product-sync-interval: PT6H
```
If wrong, pull latest code: `git pull origin master` and rebuild.

**Known issue — `erpProperties` bean not found:**
If SpEL expression `#{@erpProperties...}` appears in logs, this is a bean naming issue.
Fix: replace SpEL with `${storelense.erp.property-name}` in `@Scheduled` annotation.

---

### L2-05: Kafka Consumer Not Processing Messages

**Symptom:** RFID reads submitted via ingest API but SOH counts not updating.

**Check consumer lag:**
```bash
docker exec storelense-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group rfid-processing-service \
  --describe
```
Look for `LAG` column. High lag (> 10,000) means consumer is behind.

**Check rfid-processing-service health:**
```bash
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs rfid-processing-service --tail=30
```

**Check Kafka topics exist:**
```bash
docker exec storelense-kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```
Expected topics: `rfid.reads.raw`, `rfid.soh.updated`, `soh.session.updated`, etc.

**Resolution:**
- Restart rfid-processing-service: `docker compose restart rfid-processing-service`
- If lag is permanent: scale consumer: `docker compose up -d --scale rfid-processing-service=2`

---

### L2-06: PostgreSQL Out of Disk Space

**Symptom:** Services fail with `could not write to file` or `no space left on device`

**Check disk usage:**
```bash
df -h
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "SELECT schemaname, pg_size_pretty(SUM(pg_total_relation_size(schemaname||'.'||tablename))::bigint) 
   FROM pg_tables GROUP BY schemaname ORDER BY 2 DESC;"
```

**rfid_reads table is the most likely culprit** — it grows with every RFID scan.

**Free space:**
```bash
# Vacuum and analyze
docker exec storelense-postgres-1 psql -U postgres -d storelense -c "VACUUM ANALYZE rfid.rfid_reads;"

# Check and drop old partitions (> 12 months)
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "SELECT tablename FROM pg_tables WHERE schemaname='rfid' AND tablename LIKE 'rfid_reads_%';"
```

---

### L2-07: Redis Memory Full

**Symptom:** Services fail with `OOM command not allowed` from Redis

**Check:**
```bash
docker exec storelense-redis-1 redis-cli info memory | grep used_memory_human
docker exec storelense-redis-1 redis-cli dbsize
```

**Clear stale session dedup keys:**
```bash
# Count dedup keys
docker exec storelense-redis-1 redis-cli --scan --pattern "rfid:dedup:*" | wc -l

# Flush (CAUTION: only if all SOH sessions are completed)
docker exec storelense-redis-1 redis-cli --scan --pattern "rfid:dedup:*" | xargs docker exec storelense-redis-1 redis-cli DEL
```

---

### L2-08: JWT Tokens Being Rejected After Server Restart

**Symptom:** All users get 401 after a server restart even with valid tokens

**Cause:** If `JWT_SECRET` env variable changed (or is not persisted), new secrets invalidate old tokens.

**Fix:** Ensure `JWT_SECRET` is set in `deploy/.env` and doesn't change between restarts.

```bash
# Verify secret is loaded
docker exec storelense-auth-service-1 env | grep JWT_SECRET
```

---

### L2-09: Seed Script Fails Mid-Run

**Symptom:** `seed_pantaloons_p037.sh` exits with an error

**Common causes:**
1. **Login failed** — auth-service not healthy. Check: `curl -s http://localhost:8080/api/auth/login ...`
2. **Store already exists** — script handles this automatically (gets existing P037). If it fails: check store list: `curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/stores`
3. **Token expired** — script auto-relogins at Steps 4→5 and before Step 8. If it fails here, re-run the script; EPC registrations are idempotent (duplicates are skipped).

**Re-run seed safely** — the script is idempotent for stores and EPCs (skips duplicates).

---

### L2-10: seed_from_xls.py — XLS Seeder Failures

**Symptom:** `seed_from_xls.py` exits with error or produces no data.

**Issue: `openpyxl` not installed**
```
ERROR: openpyxl not installed.  Run: pip install openpyxl
```
Fix: `pip install openpyxl`

**Issue: SQL execution fails — `psql: command not found` inside container**
The container may not have `psql` in PATH.
```bash
# Find the postgres binary path:
docker exec deploy-postgres-1 find /usr -name psql 2>/dev/null
# Then write SQL to file and execute:
python3 tools/seed_from_xls.py --sql --sql-out /tmp/seed.sql --dir /path/to/xls ...
cat /tmp/seed.sql | docker exec -i deploy-postgres-1 psql -U postgres -d storelense
```

**Issue: SQL completes but no data visible in portal**
The SQL uses `BEGIN; ... COMMIT;` — check for a `ROLLBACK` in the output:
```bash
python3 tools/seed_from_xls.py --sql --sql-out /tmp/seed.sql ...
cat /tmp/seed.sql | docker exec -i deploy-postgres-1 psql -U postgres -d storelense 2>&1 | grep -E "ERROR|ROLLBACK|COMMIT"
```
Expected last line: `COMMIT`. If `ROLLBACK` — look for the `ERROR` line above it.

**Issue: `products.products` table not found**
The Flyway migrations for `product-service` haven't run yet.
Fix: Ensure product-service is healthy before running the seeder:
```bash
curl -s http://localhost:8083/actuator/health | python3 -m json.tool
```

**Issue: Login fails after 6 attempts**
All services must be healthy. Run after Step 4 of deployment (`docker compose ps` shows all healthy).

**Re-run safety:** The seeder in SQL mode does `DELETE + re-insert` for inventory and SOH sessions, and `ON CONFLICT DO NOTHING/UPDATE` for products and EPCs — safe to re-run with the same XLS files. For REST mode, the API is idempotent (existing stores/products returned by lookup).

---

## 5. Log Locations and Analysis

### Container Logs

```bash
# Real-time logs
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs -f

# Single service
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs auth-service --tail=100

# All services, last 20 lines each
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs --tail=20

# Filter for errors
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs 2>&1 | grep -E "ERROR|WARN|Exception"
```

### Key Log Patterns

| Pattern | Meaning |
|---|---|
| `Started *Application in * seconds` | Service successfully started |
| `Flyway Community Edition ... Successfully applied` | DB migration completed |
| `Kafka consumer started` | Consumer subscribed to topic |
| `JWT token is expired` | Client token needs refresh |
| `UnsatisfiedDependencyException` | Spring config error — check application.yml |
| `Connection refused` | Dependent service not ready |
| `HikariPool-1 - Connection is not available` | DB connection pool exhausted |
| `topic not present and sendDefault is disabled` | Kafka topic not created — auto-create should handle this |

### nginx Access Logs

```bash
docker exec storelense-nginx-gateway-1 tail -f /var/log/nginx/access.log
docker exec storelense-nginx-gateway-1 tail -f /var/log/nginx/error.log
```

---

## 6. Common Quick Fixes Summary

| Problem | Quick Fix |
|---|---|
| All services unhealthy on fresh start | Wait 4–5 min (Spring Boot startup + Flyway migrations) |
| Service exited immediately | `docker compose logs <service> --tail=30` → identify error |
| Login returns 401 | Check auth-service is healthy; verify username/password |
| User locked out | Reset `failed_login_attempts` and `locked_until` in `auth.users` |
| Empty data in portal | Check seed script was run; check user's store assignment |
| SOH session stuck | Cancel via admin API; start new session |
| nginx unhealthy but routing works | Cosmetic healthcheck issue; no action needed |
| erp-integration-service crashes | Check `product-sync-interval: PT6H` (not `6h`) in application.yml |
| RFID reads not processing | Check rfid-processing-service logs; check Kafka consumer lag |
| Seed script JWT expired | Script auto-relogins; if manual run needed: `bash seed_*.sh --url ...` |
| Out of disk | Vacuum `rfid.rfid_reads`; archive old partitions |

---

## 7. Escalation Contacts and Procedures

### Escalation Matrix

| Severity | Description | Response Time | Path |
|---|---|---|---|
| P1 | All users cannot log in; all data lost; security breach | 30 min | L2 → Dev immediately |
| P2 | One service down; core feature unavailable for > 1 store | 2 hours | L2 → Dev if not resolved |
| P3 | Non-critical feature broken; single user issue | 8 hours | L1 → L2 if not resolved in 30 min |
| P4 | Cosmetic issue; enhancement request | Next sprint | L1 creates ticket |

### When Escalating to Dev Team, Provide:

1. `docker compose ps` output (full)
2. Logs of failing service: `docker compose logs <service> --tail=100`
3. Time the issue started
4. What the user was doing when it occurred
5. Any recent deployments or changes

### Pre-Escalation Checklist

- [ ] All containers checked with `docker compose ps -a`
- [ ] Logs reviewed for obvious errors
- [ ] Basic network check: `curl -s http://localhost:8080/health`
- [ ] Restart of the affected service attempted
- [ ] Not a config/seed issue (new installation without seed data)

---

## 8. Maintenance Tasks

### Weekly

```bash
# Check disk usage
df -h && docker system df

# Check logs for recurring errors
docker compose -f /opt/StoreLense-master/deploy/docker-compose.yml logs --since 7d 2>&1 | grep -c ERROR
```

### Monthly

```bash
# Prune unused Docker images
docker image prune -a --filter "until=720h"

# Check PostgreSQL table sizes
docker exec storelense-postgres-1 psql -U postgres -d storelense -c \
  "SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) 
   FROM pg_tables ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC LIMIT 10;"

# Rotate DB password (update .env + restart services)
# Rotate JWT_SECRET (requires all users to re-login)
```

### Before Platform Updates

```bash
# Full DB backup
docker exec storelense-postgres-1 pg_dump -U postgres storelense > /opt/backups/storelense_$(date +%Y%m%d_%H%M).sql

# Tag current Docker images (optional rollback point)
docker tag storelense-auth-service storelense-auth-service:backup_$(date +%Y%m%d)
```
