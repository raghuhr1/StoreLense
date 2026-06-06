# Rollout Plan

**Project:** StoreLense — RFID Store Operations Platform
**Version:** 1.1
**Date:** 2026-06-06

---

## 1. Overview

StoreLense is deployed in four phases over four months, starting with SOH module validation at one pilot store and scaling to the full 400-store network. RIOT decommission is the final milestone.

---

## 2. Phase Summary

| Phase | Month | Scope | Goal |
|---|---|---|---|
| 1 | Month 1 | Pilot store (P037) | SOH counting live; platform validated |
| 2 | Month 2 | Pilot + Refill + Mobile | Refill tasks and Zebra app in production |
| 3 | Month 3 | 110 stores | Regional rollout; ERP integration live |
| 4 | Month 4 | 400 stores | Full network; RIOT decommissioned |

---

## 3. Phase 1 — Pilot Store Validation (Month 1)

### Objective

Validate the core platform against a single real store (Pantaloons P037 — Jaipur) before any wider rollout.

### Services Active

- auth-service, store-service, product-service, inventory-service
- rfid-ingest-service, rfid-processing-service
- soh-service, notification-service
- nginx gateway, frontend web portal

### Activities

| Activity | Owner | Notes |
|---|---|---|
| Deploy Docker Compose on Linux VM | IT Team | Minimum 8 GB RAM, 4 vCPU |
| Run seed data script (store, zones, products, EPCs, users) | IT/Dev | `seed_pantaloons_p037.sh` — 1067 EPC registrations |
| ADMIN and STORE_MANAGER accounts provisioned | IT Team | Passwords per seed script output |
| Train Store Manager on web portal | Operations | SOH sessions, variance review |
| Train Store Associates on Zebra app | Operations | Scanning, session start/complete |
| Perform 3 SOH count sessions | Store Team | Validate EPC decode, accuracy %, result latency |
| Review variance reports | Store Manager | Confirm data accuracy vs physical count |
| Sign off Phase 1 | Retail Operations Director | |

### Success Criteria

- SOH result available within 30 seconds of session completion
- Inventory accuracy reading ≥ 95% on first session (after EPC registration complete)
- Zero unresolved EPC decode failures

---

## 4. Phase 2 — Refill Module + Zebra App (Month 2)

### Objective

Activate refill task management and full mobile app for all roles at the pilot store.

### Additional Services

- refill-service
- Zebra Android app deployed to associate devices

### Activities

| Activity | Owner | Notes |
|---|---|---|
| Deploy Zebra Android app to TC-series devices | IT Team | APK sideload or MDM distribution |
| Configure Bluetooth RFD40/RFD90 sleds | IT Team | Per store EMDK config |
| Train Refill Associates on task app | Operations | Assignment, fulfilment, offline mode |
| Enable auto-refill task creation from SOH variance | Dev/Config | SOH variance threshold configuration |
| Run 2-week operational trial | Store Team | 5+ refill tasks/day minimum |
| Measure refill completion rate and time | Operations | Target: > 90% within 60 min |
| Sign off Phase 2 | Store Manager + Operations Director | |

### Success Criteria

- Refill completion rate ≥ 90%
- Average refill time < 60 minutes
- No data loss during Wi-Fi interruption (offline sync verified)

---

## 5. Phase 3 — Regional Rollout + ERP Integration (Month 3)

### Objective

Expand to 110 stores across one region; bring ERP integration live.

### Additional Services

- erp-integration-service (product sync and SOH push)
- reporting-service (daily KPI aggregation)

### Activities

| Activity | Owner | Notes |
|---|---|---|
| ERP integration configuration and testing | ERP Team + Dev | REST endpoint or EDI format per deployment |
| Enable scheduled product sync (every 6 hours) | Dev/Config | `storelense.erp.product-sync-interval: PT6H` |
| Enable SOH push to ERP | Dev/Config | Per-store toggle in store_config |
| Provision 110 stores in platform | IT Team | Bulk store creation via Admin or import |
| Seed EPC data for 110 stores | IT Team | One `seed_*.sh` per store or bulk import |
| Deploy Zebra app to all store devices | IT MDM | APK distribution via MDM |
| IT helpdesk training for store onboarding support | IT Team | |
| Performance testing at 110-store load | Dev/QA | Kafka consumer lag, API p95 latency |
| Migrate RIOT data for 110 stores | IT/Dev | Product master, EPC registry, historical SOH |
| Sign off Phase 3 | Regional Operations Manager | |

### Success Criteria

- All 110 stores active with at least one completed SOH session
- ERP product sync running without failures for 7 consecutive days
- No performance degradation vs Phase 1 baseline (API p95 < 500 ms)
- Reporting KPIs populated daily for all 110 stores

---

## 6. Phase 4 — Full Network + RIOT Decommission (Month 4)

### Objective

Complete rollout to all 400 stores; decommission RIOT.

### Activities

| Activity | Owner | Notes |
|---|---|---|
| Provision remaining 290 stores | IT Team | |
| Seed EPC data for all stores | IT Team | |
| Deploy Zebra app to all remaining devices | IT MDM | |
| Validate Kafka autoscaling at full 400-store load | Dev/Infra | HPA on rfid-processing-service |
| Final RIOT data migration (290 stores) | IT/Dev | |
| Parallel run period (RIOT + StoreLense) | IT/Ops | 2-week overlap for validation |
| RIOT read access decommission | IT | After audit sign-off |
| RIOT system shutdown | IT | |
| Post-launch hypercare support window | Dev/IT | 4 weeks on-call |
| Sign off Phase 4 | CTO / Retail Operations Director | |

### Success Criteria

- 400 stores active, each with ≥ 1 completed SOH session per week
- Inventory accuracy across network ≥ 98%
- RIOT system shut down with data archived
- No open P1/P2 incidents at end of hypercare window

---

## 7. Infrastructure Milestones

| Milestone | Phase | Notes |
|---|---|---|
| Docker Compose on-premise VM (pilot) | Phase 1 | 8 GB RAM, 4 vCPU minimum |
| Kubernetes migration (optional Phase 2/3) | Phase 3 | Enables HPA on rfid-processing-service |
| PostgreSQL Multi-AZ or read replica | Phase 3 | Before 110-store load |
| Kafka cluster validation under burst load | Phase 3 | 50,000 reads/sec aggregate |
| CDN / load balancer for web portal | Phase 3 | If serving 400 stores concurrently |

---

## 8. Training Plan

| Role | Training Content | Format | When |
|---|---|---|---|
| IT Admin | Server setup, Docker Compose, seed scripts, log monitoring | 1-day workshop | Before Phase 1 |
| ADMIN (portal) | User management, store/product admin, ERP sync | 2-hour guided session | Phase 1 |
| Store Manager | SOH sessions, variance review, refill tasks, KPI dashboard | 2-hour guided session | Phase 1–3 |
| Store Associate | Zebra app — scanning, SOH session, offline mode | 30-min hands-on | Phase 2 |
| Refill Associate | Zebra app — task list, fulfilment, offline sync | 30-min hands-on | Phase 2 |

---

## 9. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ERP API format differs from assumed | Medium | High | Spike in Phase 1; adapt erp-integration-service adapter |
| Zebra EMDK compatibility issue on specific device models | Medium | Medium | Test against TC21, TC26, TC57 in Phase 1 |
| Low EPC registration quality (wrong EPC→SKU mappings) | High | High | Data quality check script during seed; reconcile vs ERP product list |
| Wi-Fi dead zones in store | High | Medium | Offline-first design; WorkManager sync on reconnect |
| Store onboarding throughput (110 stores in one month) | Medium | Medium | Automated seed scripts; bulk store import API |
| Kafka consumer lag under full load | Low | High | HPA on rfid-processing-service; load test in Phase 3 |
| RIOT data migration incomplete | Medium | High | Identify minimum viable history (90 days); archive rest |

---

## 10. Contacts

| Role | Responsibility |
|---|---|
| Engineering Lead | Platform development and deployment |
| IT Infrastructure | Server provisioning, network, MDM |
| Retail Operations Director | Business sign-off and UAT |
| ERP Team | ERP integration API / EDI spec |
| Store Managers | UAT, training coordination at each store |
