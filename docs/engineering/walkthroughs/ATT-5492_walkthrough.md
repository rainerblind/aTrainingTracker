# Stage 5: Walkthrough & Verification - ATT-5492: Remove obsolete Network Location sensor (SPEED_AND_LOCATION_NETWORK / SpeedAndLocationDevice_Network) and migrate Devices.db

**Ticket**: [ATT-5492](https://rainerblind.atlassian.net/browse/ATT-5492)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5492`  
**Requirement Mapping**: `REQ-SEN-027`, `REQ-TRK-024` | **Test Mapping**: `TST-SEN-027`, `TST-TRK-016`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Excised obsolete `SpeedAndLocationDevice_Network.java` and `DeviceType.SPEED_AND_LOCATION_NETWORK` enum constant and all references across `banalservice` and `trainingtracker`. Upgraded `DevicesDbHelper.DB_VERSION` from 9 to 10 with `onCreate` case 10, an idempotent `onUpgrade` deletion (`oldVersion <= 9`) removing obsolete network location rows from `Devices.db`, and automatic `DatabaseSchemaRegistry` synchronization. Removed `devices_location_Network` across all 9 supported locales in `strings_devices.xml`. Verified zero regressions and 100% test pass across all affected packages.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-027.1` | `TST-SEN-027.1` | `DeviceTypeEnumOrderTest`, `TranslationParityTest` | **PASSED** | `Verified` |
| `REQ-SEN-027.2` | `TST-SEN-027.2` | `DevicesDatabaseManagerOrderingTest`, `DevicesDatabaseManagerTest` | **PASSED** | `Verified` |
| `REQ-SEN-027.3` | `TST-SEN-027.3` | `MySensorManagerPriorityTest`, `SensorSourceDialogPriorityTest` | **PASSED** | `Verified` |
| `REQ-SEN-027.4` | `TST-SEN-027.4` | `TranslationParityTest` (9 locales verified) | **PASSED** | `Verified` |
| `REQ-TRK-024.1` | `TST-TRK-016.1` | `MySensorManagerPriorityTest` (Priority arrays 6, 4, 2) | **PASSED** | `Verified` |
| `REQ-TRK-024.6` | `TST-TRK-016.4` | `FreshInstallLocationInitTest`, `DeviceManagerLocationInitTest` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-SEN-027.5` | Tier 1 Targeted Unit Test Suite (100% PASS) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | `verify_requirement_governance.py` (100% parity) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* **Scope**: `--tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogPriorityTest" --tests "com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManagerTest"`
```text
BUILD SUCCESSFUL in 1m 31s
32 actionable tasks: 1 executed, 31 up-to-date
All targeted unit tests completed with 0 failures (100% PASS).
```

### AAPT2 Resource Compilation
* **Scope**: `./gradlew processDebugResources`
```text
BUILD SUCCESSFUL in 1s
18 actionable tasks: 18 up-to-date
```

### Living Documentation Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Modified/Altered existing requirement(s) detected: REQ-TRK-024.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware Verification & On-Device Status
* Physical verification skipped per Rule 60 & 62: `adb devices` reported no connected physical devices (headless/disconnected environment). As a backend dead-code cleanup, DB migration, and translation pruning change, validation relies on targeted unit tests, Robolectric SQLite migration tests, and AAPT2 resource verification.

---

## 5. Invariants & Architecture Governance Verification
1. **Rule 62 & Rule 2.2 Database Schema Integrity**: `DevicesDbHelper.DB_VERSION` incremented to 10; `DatabaseSchemaRegistry` automatically synchronized with contract tests passing.
2. **Idempotent Upgrade**: Version 10 migration removes `SPEED_AND_LOCATION_NETWORK` cleanly on existing user databases without affecting paired GPS, Fused Location, or barometric altimeters.
3. **Sensor Priority Hierarchy**: `SPEED_PRIORITY` (6 items), `ALTITUDE_PRIORITY` (4 items), and `LOCATION_PRIORITY` (2 items) strictly preserved with `GOOGLE_FUSED > GPS` precedence.
4. **9-Language Localization Parity**: Obsolete string `devices_location_Network` pruned symmetrically across all 9 locales.
