# Stage 3: Implementation Plan - ATT-5490: Preselect Google Fused & Active Remote Sensor

**Ticket**: [ATT-5490](https://rainerblind.atlassian.net/browse/ATT-5490)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5490`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.2`, `REQ-SET-084.5`)  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.2`, `TST-SET-084.12`, `TST-SET-084.13`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
When an athlete returns to Free tier with $> 2$ paired sensors, `KeepTwoSensorsBottomSheet` currently preselects GPS and Barometer, discarding the athlete's most active external sensors (HRM, Power, Speed) and ignoring `SPEED_AND_LOCATION_GOOGLE_FUSED`. This plan specifies updating `determineDefaultKeepDeviceIds` to preselect Google Fused (or single paired location sensor) in Slot 1 and the most active remote sensor in Slot 2, falling back to Barometer only when no remote sensors exist, and updating `determineRecommendedUnpairDeviceId` to recommend unpairing GPS over Google Fused.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-084.5` (Slot 1 location preselection, Slot 2 active remote sensor, Barometer fallback), `REQ-SET-084.2` (Smart unpair recommendation).
* **Test Mapping**: `TST-SET-084.12` (`SensorQuotaReconciliationManagerTest.kt`), `TST-SET-084.2` (`DeviceListViewModelQuotaTest.kt`), `TST-SET-084.13` (Clean-room regression).

---

## 3. System Invariants & Preserved Behavior
1. **Numeric Timestamp Evaluation (Rule 38 / Coding Standards Sec. 1.3)**: Remote sensor recency MUST be compared using raw `Long` epoch milliseconds parsed from `lastSeen`, strictly prohibiting lexicographical display string comparisons.
2. **Quota Bounds**: Kept devices strictly bounded by `MAX_FREE_PAIRED_DEVICES = 2`.
3. **Non-Destructive Unpairing**: Unselected devices set to `PAIRED = 0` with names and calibration intact in SQLite.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit (`check-gate`).

---

## 4. Proposed Architectural Changes
### Component 1: `SensorQuotaReconciliationManager.kt` (`monetization/`)
* Implement `parseLastSeenTimestamp(lastSeen: String?): Long` parsing date/time instances and ISO formats with `0L` default.
* Refactor `determineDefaultKeepDeviceIds(pairedDevices: List<DeviceUiData>): Set<Long>`:
  * Slot 1: If both Google Fused and GPS paired -> Google Fused; else if one paired -> that sensor.
  * Slot 2: Filter `protocol != Protocol.SMARTPHONE`, sorted by `isConnected` descending then numeric epoch timestamp descending; add top remote sensor.
  * Fallback: If $< 2$ sensors kept, add `ALTITUDE_FROM_PRESSURE`, then remaining devices in list order.
* Centralize `determineRecommendedUnpairDeviceId(pairedDevices: List<DeviceUiData>): Long`: Barometer first, then GPS when both Google Fused and GPS are paired, then first paired device.

### Component 2: `DeviceListViewModel.kt` (`devicelist/`)
* Delegate `determineRecommendedUnpairDeviceId` directly to `reconciliationManager`.

### Component 3: SWE.2 Architecture (`docs/architecture.md`)
* Update Section 4.7 documenting the updated preselection heuristic and unpair recommendation order.

### UI Consistency (Rule 23)
* **No UI changes** (Business logic heuristic & ViewModel delegation only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `SensorQuotaReconciliationManager.kt` (`REQ-SET-084.2`, `REQ-SET-084.5`)
* Add `parseLastSeenTimestamp`, refactor `determineDefaultKeepDeviceIds`, and add `determineRecommendedUnpairDeviceId`.

### Step 2: Delegate in `DeviceListViewModel.kt` (`REQ-SET-084.2`)
* Delegate `determineRecommendedUnpairDeviceId` to `reconciliationManager`.

### Step 3: Update `docs/architecture.md` (SWE.2 Maintenance)
* Update Section 4.7 to document Google Fused priority and active remote sensor selection.

### Step 4: Author/Update Unit Test Suites (`TST-SET-084.2`, `TST-SET-084.12`)
* Update `SensorQuotaReconciliationManagerTest.kt` and `DeviceListViewModelQuotaTest.kt` covering all matrix combinations.

### Step 5: Verification & Governance Gate (`TST-SET-084.13`)
* Execute targeted tests and governance validator:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManagerTest" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceListViewModelQuotaTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass on targeted unit tests; clean-room full suite regression pass in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-5490` allows full git discard via `git checkout feature/ATT-5490 -- .`.
