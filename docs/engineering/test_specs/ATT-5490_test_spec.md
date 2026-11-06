# Stage 2: Requirement & Test Specification - ATT-5490: Preselect Google Fused & Active Remote Sensor

**Ticket**: [ATT-5490](https://rainerblind.atlassian.net/browse/ATT-5490)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5490`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.2`, `REQ-SET-084.5`) | **Test Spec ID**: `TST-SET-084`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SET-084`)

### 1.1 Problem Statement & Rationale
When an athlete downgrades to Free tier with $> 2$ paired sensors, `KeepTwoSensorsBottomSheet` currently preselects GPS and Barometer, discarding the athlete's most recently active external sensors (HRM, Power, Speed) and ignoring `SPEED_AND_LOCATION_GOOGLE_FUSED`.

### 1.2 Functional & Architectural Requirements
1. **Slot 1 Location Sensor Selection (`REQ-SET-084.5`)**: If both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` are paired, preselect `SPEED_AND_LOCATION_GOOGLE_FUSED`. If only one location sensor is paired, preserve that single paired sensor.
2. **Slot 2 Active Remote Sensor Selection (`REQ-SET-084.5`)**: Preselect the most recently active remote sensor (`protocol != Protocol.SMARTPHONE`), prioritized by:
   - `isConnected == true` first,
   - Most recent parsed `lastSeen` timestamp second,
   - Original list order if timestamps are null or equal.
3. **Smartphone Fallback (`REQ-SET-084.5`)**: If no remote sensor is paired, fall back to `ALTITUDE_FROM_PRESSURE` and remaining paired devices in order.
4. **Smart Unpair Recommendation (`REQ-SET-084.2`)**: In `determineRecommendedUnpairDeviceId`, recommend `ALTITUDE_FROM_PRESSURE` first; next, if both Google Fused and GPS are paired, recommend `SPEED_AND_LOCATION_GPS` (preserving Google Fused).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Dual Location + Remote)**: *Given* both Google Fused and GPS paired plus remote sensors, *When* `determineDefaultKeepDeviceIds` is called, *Then* Google Fused and the most recently active remote sensor are preselected.
* **AC-2 (Single Location + Remote)**: *Given* only GPS (or only Google Fused) paired alongside remote sensors, *When* evaluated, *Then* that location sensor and the most active remote sensor are preselected.
* **AC-3 (Smartphone Only Fallback)**: *Given* only GPS and Barometer paired, *When* evaluated, *Then* GPS and Barometer are preselected.
* **AC-4 (Unpair Recommendation)**: *Given* both Google Fused and GPS paired without Barometer, *When* `determineRecommendedUnpairDeviceId` is evaluated, *Then* `SPEED_AND_LOCATION_GPS` is recommended.

### 1.4 System Invariants
Free-tier quota $\le 2$ sensors; non-destructive unpairing (names and calibration intact); active tracking suppression (`!isTracking()`); 100% test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-SET-084` (`REQ-SET-084.2`, `REQ-SET-084.5`) targeting `SensorQuotaReconciliationManager.kt` and `DeviceListViewModel.kt`.
* `Historical Origin & Commit Trace`: `ATT-51` (Free-tier quota), `ATT-5469` (`29251f2d`, global manager), refined in `ATT-5490` (Sprint `2026-45.8`).
* `Root Reason for Existing Formulation`: GPS and Barometer were historically assumed as baseline defaults before external activity tracking and Google Fused prioritization (`ATT-5491`).
* `Preservation of Core Invariants`: $\le 2$ sensor limit strictly enforced; unselected sensors remain intact in SQLite; Barometer fallback preserved when no remote sensors exist.

---

## 2. Test Specification (`TST-SET-084`)

### Test Case 1: Reconciliation Default Keep Selection Suite (`TST-SET-084.12`)
* **Scope**: Unit tests in `SensorQuotaReconciliationManagerTest.kt`.
* **Procedure**: Test dual location + active remote sensor; single location + remote sensor; multiple remote sensors sorted by connection and `lastSeen`; and smartphone fallback.

### Test Case 2: Recommended Unpair Device ID Selection (`TST-SET-084.2`)
* **Scope**: Unit tests in `DeviceListViewModelQuotaTest.kt`.
* **Procedure**: Test recommendation sequence: Barometer first, then GPS when both Google Fused and GPS are paired, then first paired device.

### Test Case 3: Clean-Room Governance & Regression (`TST-SET-084.13`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManagerTest" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModelQuotaTest"`
* **Expected Result**: Exit code 0, 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-084.5` | Preselect Google Fused over GPS when both paired | `TST-SET-084.12` | Unit Test (`SensorQuotaReconciliationManagerTest`) |
| `REQ-SET-084.5` | Preselect most recently active remote sensor | `TST-SET-084.12` | Unit Test (`SensorQuotaReconciliationManagerTest`) |
| `REQ-SET-084.5` | Fall back to Barometer when no remote sensors | `TST-SET-084.12` | Unit Test (`SensorQuotaReconciliationManagerTest`) |
| `REQ-SET-084.2` | Recommend unpairing GPS over Google Fused | `TST-SET-084.2` | Unit Test (`DeviceListViewModelQuotaTest`) |
| `REQ-SET-084` | Governance & Regression Invariance | `TST-SET-084.13` | Clean-Room Suite |
