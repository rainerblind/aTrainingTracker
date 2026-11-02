# Stage 5: Walkthrough & Verification - ATT-4859: Pairable AltitudeFromPositionDevice & DEM Elevation Cache

**Ticket**: [ATT-4859](https://rainerblind.atlassian.net/browse/ATT-4859)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-SEN-025` (`REQ-SEN-025.1`..`REQ-SEN-025.5`)  
**Test Mapping**: `TST-SEN-025` (`TST-SEN-025.1`..`TST-SEN-025.5`)  
**Branch**: `feature/ATT-4859`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview

ATT-4859 introduces the pairable `AltitudeFromPositionDevice` (`DeviceType.ALTITUDE_FROM_POSITION`, `Protocol.SMARTPHONE`), backed by a dedicated spatial database `ElevationCache.db` (`DB_VERSION = 1`). It resolves continuous topographic DEM altitude from GPS fixes using quantized coordinates (`lat_e5`, `lon_e5`), nearest-neighbor spatial tolerance matching ($\approx 20\text{ m}$, $\Delta\text{E5} \le 2$), non-blocking route trackpoint and segment stream seeding, and asynchronous Open-Meteo DEM fetching on cache misses. Operations are strictly decoupled from *Lieblingsorte* (`KnownLocations.db`), with zero writes or queries.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-025.1` | `TST-SEN-025.1` | `AltitudeFromPositionDeviceTest` + `MySensorManagerPriorityTest` | **PASSED** | `Verified` |
| `REQ-SEN-025.2` | `TST-SEN-025.2` | `ElevationCacheDatabaseManagerTest` (CRUD, nearest neighbor, Rule 20) | **PASSED** | `Verified` |
| `REQ-SEN-025.3` | `TST-SEN-025.3` | `ElevationCacheDatabaseManagerTest` (route & segment seeding) + Async DEM | **PASSED** | `Verified` |
| `REQ-SEN-025.4` | `TST-SEN-025.4` | `ElevationCacheDatabaseManagerTest` & `AltitudeFromPositionDeviceTest` | **PASSED** | `Verified` |
| `REQ-SEN-025.5` | `TST-SEN-025.5` | `TranslationParityTest` (all 9 supported locales) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.banalservice.*` (including `AltitudeFromPositionDeviceTest`, `MySensorManagerPriorityTest`, `DeviceManager*`)
  - `com.atrainingtracker.trainingtracker.database.*` (including `ElevationCacheDatabaseManagerTest`)
  - `com.atrainingtracker.trainingtracker.migration.*`
  - `com.atrainingtracker.trainingtracker.ui.tracking.*` (including `SensorSourceDialog*`)
  - `com.atrainingtracker.trainingtracker.build.*`
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`
  - `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`
* **Execution**: `./gradlew testDebugUnitTest --tests ...` -> **BUILD SUCCESSFUL in 2m 25s** (32 tasks, 0 failures, 100% pass across targeted scope).
* **Resource Compilation**: `./gradlew processDebugResources` -> **BUILD SUCCESSFUL in 1s**.
* **Governance**: `python3 tools/verify_requirement_governance.py` -> Clean net-new pass (`REQ-SEN-025`).

---

## 4. Invariant & Governance Verification

1. **Existing User Invariant (Rule 66 Sec 5.6)**: New device defaults to `isPaired = false` in `Devices.db` / `ensureSmartphoneDeviceExists`; existing users' altitude sources and tracking behavior remain unchanged until explicitly paired.
2. **Database Schema Governance (Rules 20, 62, 64)**: `DatabaseSchemaRegistry.kt` references `ElevationCacheDbHelper.DB_VERSION` directly (never a raw hardcoded integer); symmetric cursor mappers (`fromCursor`, `fromCursorBatch`, `toContentValues`) enforced; schema parity maintained.
3. **Database Isolation (REQ-SEN-025.4)**: `ElevationCache.db` operations execute with zero interaction or mutation of `KnownLocations.db`.
4. **Altimeter Priority Hierarchy**: Configured in `MySensorManager.java`: `ALTITUDE_FROM_PRESSURE` (0) $\rightarrow$ `ALTITUDE_FROM_POSITION` (1) $\rightarrow$ `SPEED_AND_LOCATION_GPS` (2) $\rightarrow$ `SPEED_AND_LOCATION_GOOGLE_FUSED` (3) $\rightarrow$ `SPEED_AND_LOCATION_NETWORK` (4).
5. **No Intermediate Commits**: Branch `feature/ATT-4859` maintained uncommitted until final squash merge.
6. **Lösungsversion Governance (Core Rule 8)**: `fixVersion` not set during development (reserved for joint sprint review).
