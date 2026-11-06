# Stage 2: Requirement & Test Specification - ATT-5491: Location Sensor Priority & Default Pairing

**Ticket**: [ATT-5491](https://rainerblind.atlassian.net/browse/ATT-5491)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5491`  
**Requirement Mapping**: `REQ-TRK-024` | **Test Spec ID**: `TST-TRK-016`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-TRK-024`)

### 1.1 Problem Statement & Rationale
On modern Android devices, Google Play Services Fused Location Provider (`SPEED_AND_LOCATION_GOOGLE_FUSED`) provides faster initial fixes and superior position/speed stability compared to raw GPS (`SPEED_AND_LOCATION_GPS`). However, `MySensorManager` lists GPS ahead of Google Fused across `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`, and `DevicesDatabaseManager` defaults GPS to `PAIRED = true` and Google Fused to `PAIRED = false` on fresh installs.

### 1.2 Functional & Architectural Requirements
1. **Sensor Priority Hierarchy (`REQ-TRK-024.1`)**: In `MySensorManager.java`, `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` SHALL precede `DeviceType.SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`.
2. **Fresh Installation Default Pairing (`REQ-TRK-024.6`)**: In `DevicesDatabaseManager.addLocationDevicesToDb`, if `GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS`, `SPEED_AND_LOCATION_GOOGLE_FUSED` SHALL be initialized with `PAIRED = true` and `SPEED_AND_LOCATION_GPS` with `PAIRED = false`. If Play Services is unavailable, `SPEED_AND_LOCATION_GPS` SHALL remain `PAIRED = true` and `SPEED_AND_LOCATION_GOOGLE_FUSED` with `PAIRED = false`.
3. **Existing Installation Preservation (`REQ-TRK-024.6`)**: Existing database installations and upgrades (`oldVersion > 6`) SHALL NOT overwrite or alter existing user pairings.
4. **Physical Sensor Precedence Invariance (`REQ-TRK-024.2`)**: Dedicated wheel/footpod sensors (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_POWER`) and barometric altimeters (`ALTITUDE_FROM_PRESSURE`) SHALL continue to strictly supersede location-derived metrics.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Priority Ordering)**: *Given* `MySensorManager`, *When* priority lists are inspected, *Then* `SPEED_AND_LOCATION_GOOGLE_FUSED` precedes `SPEED_AND_LOCATION_GPS` across speed, altitude, and location priorities.
* **AC-2 (Dynamic Upgrade/Fallback)**: *Given* active speed sensors, *When* evaluating `getBestSensor(SPEED_mps)`, *Then* resolution follows `BIKE_SPEED > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_NETWORK`.
* **AC-3 (Fresh Install Google Play Services Available)**: *Given* fresh DB creation with Play Services available, *When* querying location devices, *Then* `SPEED_AND_LOCATION_GOOGLE_FUSED` is paired (`1`) and `SPEED_AND_LOCATION_GPS` is unpaired (`0`).
* **AC-4 (Fresh Install Fallback)**: *Given* fresh DB creation with Play Services unavailable, *When* querying location devices, *Then* `SPEED_AND_LOCATION_GPS` is paired (`1`) and `SPEED_AND_LOCATION_GOOGLE_FUSED` is unpaired (`0`).

### 1.4 System Invariants
Physical wheel speed/power and barometric pressure precedence; zero schema bump or existing user pairing overwrite; fallback to raw GPS on de-Googled devices; 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-TRK-024` (`REQ-TRK-024.1`, `REQ-TRK-024.6`) targeting `MySensorManager.java` and `DevicesDatabaseManager.java`.
* `Historical Origin & Commit Trace`: Commit `fc28443c` (`ATT-3961`, Sprint `2026-43.1`), refined in `ATT-4375` (Sprint `2026-43.2`), amended in `ATT-5491` (Sprint `2026-45.8`).
* `Root Reason for Existing Formulation`: In `ATT-3961` and `ATT-4375`, GPS was placed ahead of Google Fused because raw GPS was the historical baseline and fresh DB initialization lacked runtime Google Play Services availability checks.
* `Preservation of Core Invariants`: Hardware speed/power and barometric sensors retain top precedence; de-Googled devices fall back to raw GPS; existing user pairings are 100% untouched.

---

## 2. Test Specification (`TST-TRK-016`)

### Test Case 1: MySensorManager Location Priority Invariants (`TST-TRK-016.1`)
* **Scope**: Unit test in `MySensorManagerPriorityTest.kt`.
* **Procedure**: Assert `SPEED_AND_LOCATION_GOOGLE_FUSED` precedes `SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`. Verify dynamic upgrade/fallback and canonical rank helper.

### Test Case 2: Fresh Install Pairing Conditioned on Play Services (`TST-TRK-016.4`)
* **Scope**: Unit test in `DevicesDatabaseManagerOrderingTest.kt`.
* **Procedure**: Mock `GooglePlayServicesUtil.isGooglePlayServicesAvailable` returning `SUCCESS` vs non-zero; assert `SPEED_AND_LOCATION_GOOGLE_FUSED.paired == 1` / `GPS.paired == 0` for `SUCCESS`, and `GPS.paired == 1` / `GOOGLE_FUSED.paired == 0` for non-zero.

### Test Case 3: SensorSourceDialog Priority Sorting Parity (`TST-TRK-016.8`)
* **Scope**: Unit test in `SensorSourceDialogPriorityTest.kt`.
* **Procedure**: Assert `activeBackups` and `notConnected` lists order `SPEED_AND_LOCATION_GOOGLE_FUSED` before `SPEED_AND_LOCATION_GPS`.

### Test Case 4: Clean-Room Governance & Regression (`TST-TRK-016.9`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerPriorityTest" --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerOrderingTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialogPriorityTest"`
* **Expected Result**: Exit code 0, 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-024.1` | Prioritize Google Fused over GPS in priority arrays | `TST-TRK-016.1` | Unit Test (`MySensorManagerPriorityTest`) |
| `REQ-TRK-024.2` | Dynamic upgrade/fallback & rank order | `TST-TRK-016.1`, `TST-TRK-016.8` | Unit Test (`MySensorManagerPriorityTest`, `SensorSourceDialogPriorityTest`) |
| `REQ-TRK-024.6` | Default pair Google Fused when Play Services available | `TST-TRK-016.4` | Unit Test (`DevicesDatabaseManagerOrderingTest`) |
| `REQ-TRK-024.6` | Fallback to GPS when Play Services unavailable | `TST-TRK-016.4` | Unit Test (`DevicesDatabaseManagerOrderingTest`) |
| `REQ-TRK-024` | Governance & Regression Invariance | `TST-TRK-016.9` | Clean-Room Suite |
