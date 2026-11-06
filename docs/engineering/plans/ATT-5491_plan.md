# Stage 3: Implementation Plan - ATT-5491: Location Sensor Priority & Default Pairing

**Ticket**: [ATT-5491](https://rainerblind.atlassian.net/browse/ATT-5491)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5491`  
**Requirement Mapping**: `REQ-TRK-024` (`REQ-TRK-024.1`, `REQ-TRK-024.2`, `REQ-TRK-024.6`)  
**Test Mapping**: `TST-TRK-016` (`TST-TRK-016.1`, `TST-TRK-016.4`, `TST-TRK-016.8`, `TST-TRK-016.9`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Google Play Services Fused Location Provider (`SPEED_AND_LOCATION_GOOGLE_FUSED`) provides faster time-to-first-fix and greater trajectory stability than raw GPS (`SPEED_AND_LOCATION_GPS`). However, `MySensorManager` lists GPS ahead of Google Fused across `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`, and `DevicesDatabaseManager` pairs GPS by default on fresh installs. This plan specifies prioritizing Google Fused in `MySensorManager`, conditionally pairing Google Fused on fresh install when Play Services is available (with raw GPS fallback), and aligning unit tests and SWE.2 architecture documentation.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-TRK-024.1` (Sensor priority hierarchy swap), `REQ-TRK-024.2` (Dynamic upgrade/fallback), `REQ-TRK-024.6` (Fresh install conditional pairing & existing install preservation).
* **Test Mapping**: `TST-TRK-016.1` (`MySensorManagerPriorityTest.kt`), `TST-TRK-016.4` (`DevicesDatabaseManagerOrderingTest.kt`), `TST-TRK-016.8` (`SensorSourceDialogPriorityTest.kt`), `TST-TRK-016.9` (Clean-room regression).

---

## 3. System Invariants & Preserved Behavior
1. **Dedicated Physical Sensor Precedence**: Wheel speed/power (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_POWER`) and barometer (`ALTITUDE_FROM_PRESSURE`) strictly supersede location-derived metrics.
2. **De-Googled Fallback Parity**: Devices without Google Play Services automatically fall back to raw GPS pairing on fresh install.
3. **Existing User Database Preservation**: Zero database schema bump (`DB_VERSION` remains 9); existing user pairings in SQLite are 100% untouched.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit (`check-gate`).

---

## 4. Proposed Architectural Changes
### Component 1: `MySensorManager.java` (Priority Hierarchies)
* In `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`, swap `SPEED_AND_LOCATION_GOOGLE_FUSED` ahead of `SPEED_AND_LOCATION_GPS`. Update JavaDoc.

### Component 2: `DevicesDatabaseManager.java` (Fresh Install Seeding)
* In `DevicesDbHelper.addLocationDevicesToDb`, check `GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS`. If available, insert `SPEED_AND_LOCATION_GOOGLE_FUSED` with `PAIRED = true` and `GPS` with `PAIRED = false`. If unavailable, insert `GPS` with `PAIRED = true` and `GOOGLE_FUSED` with `PAIRED = false`. Preserve autoincrement sequence (GPS `_id=1`, Google Fused `_id=2`, Network `_id=3`).

### Component 3: SWE.2 Architecture & Living Docs (`docs/architecture.md`)
* Update telemetry priority and default pairing descriptions in Section 4 (*Telemetry & Sensor Subsystem*).

### UI Consistency (Rule 23)
* **No UI changes** (Core telemetry engine & database seeding only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `MySensorManager.java` Priority Arrays (`REQ-TRK-024.1`, `REQ-TRK-024.2`)
* Reorder `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`.

### Step 2: Update `DevicesDatabaseManager.java` Seeding (`REQ-TRK-024.6`)
* Add Play Services check in `addLocationDevicesToDb` to conditionally set `PAIRED` flag.

### Step 3: Update SWE.2 Architecture Documentation (`docs/architecture.md`)
* Document Google Fused precedence and fresh-install conditional pairing contract.

### Step 4: Author/Update Unit Test Suites (`TST-TRK-016.1`, `TST-TRK-016.4`, `TST-TRK-016.8`)
* Update `MySensorManagerPriorityTest.kt`, `DevicesDatabaseManagerOrderingTest.kt`, and `SensorSourceDialogPriorityTest.kt`.

### Step 5: Verification & Governance Gate (`TST-TRK-016.9`)
* Execute targeted test suite and governance validator:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerPriorityTest" --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerOrderingTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogPriorityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass on targeted unit tests; clean-room full suite regression pass in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-5491` allows full git discard via `git checkout feature/ATT-5491 -- .`.
