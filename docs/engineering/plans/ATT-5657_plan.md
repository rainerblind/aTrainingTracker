# Stage 3: Implementation Plan - ATT-5657: Never recommend Google Fused or sole Location sensor for unpairing

**Ticket**: [ATT-5657](https://rainerblind.atlassian.net/browse/ATT-5657)  
**Parent Epic**: Standalone | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5657`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.2`)  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.14`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
On Free tier (`!isProActive()`), attempting to pair a 3rd sensor triggers `SwapOrUpgradeDialog`. When `Google Fused` and an external sensor (e.g. cadence or HRM) are paired, `determineRecommendedUnpairDeviceId()` previously returned `Google Fused` due to falling back to `pairedDevices.firstOrNull()`. Unpairing the sole location sensor degrades tracking. ATT-5657 enforces that `Google Fused` and any sole location sensor are never recommended for unpairing when non-location sensors are paired.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-084.2` (*Smart Recommendation Preselection & Sole Location Protection*)
* **Test Mapping**: `TST-SET-084.14` (`TST-SET-084.14a`–`d`) (*Never Recommend Google Fused or Sole Location Sensor for Unpairing*)

---

## 3. System Invariants & Preserved Behavior
1. **Free Tier Quota Invariant**: Maximum 2 paired devices limit (`MAX_FREE_PAIRED_DEVICES`) remains strictly enforced.
2. **Barometer Priority**: `ALTITUDE_FROM_PRESSURE` remains Tier 1 recommendation to unpair if present.
3. **Dual Location Preference**: If both `Google Fused` and `GPS` are paired, `GPS` is recommended for unpair (preserving `Google Fused`).
4. **Data Integrity**: Unpairing sets `PAIRED = 0` without deleting sensor configuration or calibrations in SQLite.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorQuotaReconciliationManager.kt`
* Update `determineRecommendedUnpairDeviceId()`: After Barometer and Dual Location checks, filter `pairedDevices` for non-location sensors (`it.deviceType` not Fused or GPS).
* If non-location sensors exist, recommend the non-location sensor (prioritizing `!isConnected` first, oldest `lastSeen` second, list order third).
* Fall back to `pairedDevices.firstOrNull()` only if only location sensors are paired.

### Component 2: `SwapOrUpgradeDialog.kt`
* Harden `selectedUnpairId` fallback: If `recommendedUnpairDeviceId` is missing/invalid, filter for non-location devices before defaulting to `firstOrNull()`.

### UI Consistency (Rule 23)
* **Reference screen**: `SwapOrUpgradeDialog.kt` (`AppModalBottomSheet` per Rule 73 stacked buttons).
* **Reused components**: Existing `SwapOrUpgradeDialog` composable, radio buttons, and typography.
* **Theme tokens**: `MaterialTheme.colorScheme.primaryContainer`, `TTColor` unchanged.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Unpair Priority in `SensorQuotaReconciliationManager.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/monetization/SensorQuotaReconciliationManager.kt`
* Implement non-location sensor filtering and activity-based prioritization in `determineRecommendedUnpairDeviceId()`.

### Step 2: Harden Fallback in `SwapOrUpgradeDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/SwapOrUpgradeDialog.kt`
* Prevent fallback selection from picking Google Fused / sole location sensor when non-location sensors exist.

### Step 3: Update and Expand Unit Tests (`TST-SET-084.14`)
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/monetization/SensorQuotaReconciliationManagerTest.kt`
  * `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModelQuotaTest.kt`
* Fix outdated assertion (fused + hrm -> returns hrm, not fused); add test cases for sole GPS + remote, and connected vs disconnected remote.

### Step 4: Targeted Test Suite Execution
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.SensorQuotaReconciliationManagerTest" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TST-SET-084.14`), `verify_requirement_governance.py`.
* **Rollback**: Clean git working tree revert on `feature/ATT-5657` branch.
