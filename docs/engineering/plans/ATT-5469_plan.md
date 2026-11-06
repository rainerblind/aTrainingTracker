# Stage 3: Implementation Plan - ATT-5469: Global Subscription Downgrade Sensor Quota Reconciliation

**Ticket**: [ATT-5469](https://rainerblind.atlassian.net/browse/ATT-5469)  
**Sub-task**: `[Impl-Plan]` (To be created via `submit-stage`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5469`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.5`, `REQ-SET-084.7`)  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.9`, `TST-SET-084.10`, `TST-SET-084.11`, `TST-SET-084.12`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
During the Sprint 2026-45.6 review of ATT-5398, the user observed that `checkDowngradeReconciliation()` is only triggered when navigating to `DeviceListViewModel` / `DevicesTabbedScreen`. When a Supporter subscription expires (downgrade to Free tier with > 2 paired sensors), an athlete who never navigates to the Sensors screen can continue tracking indefinitely with all previously paired sensors. The reconciliation flow must be triggered globally on app start, on the tracking screen, and before sensor search / workout recording, with defense-in-depth connection clamping in `BANALService`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-084.5` (Global Downgrade Reconciliation & Lifecycle Triggers), `REQ-SET-084.7` (BANALService Hardware Connection Clamping)
* **Test Mapping**: `TST-SET-084.9` (Global Manager Unit Tests), `TST-SET-084.10` (Lifecycle & Navigation Triggers), `TST-SET-084.11` (Pre-Tracking Quota Interception), `TST-SET-084.12` (DeviceManager Clamping)

---

## 3. System Invariants & Preserved Behavior
1. **Active Workout Non-Interference**: Reconciliation dialogs and sensor unpairing are strictly suppressed while `TrainingApplication.isTracking() == true`.
2. **Non-Destructive Pruning**: Unselected sensors have `PAIRED = 0` set in `Devices.db`; custom names, calibration factors, and equipment links remain 100% intact.
3. **Quota Bypass Prevention**: Dismissing `KeepTwoSensorsBottomSheet` applies default keep IDs (GPS + Barometer).
4. **Backward Compatibility**: `DeviceListViewModel` delegates to the centralized manager, preserving 100% parity with `DeviceListViewModelQuotaTest.kt`.
5. **Continuous ASPICE SWE.2 Maintenance**: `docs/architecture.md` updated to document `SensorQuotaReconciliationManager`.

---

## 4. Proposed Architectural Changes

### Component 1: Centralized Reconciliation Manager (`SensorQuotaReconciliationManager.kt`)
* Encapsulate `keepTwoSensorsDialogEvent: StateFlow<KeepTwoSensorsDialogEvent?>`, `checkDowngradeReconciliation(): Boolean`, `confirmKeepTwoSensors()`, `dismissKeepTwoSensors()`, and `determineDefaultKeepDeviceIds()`.
* Expose singleton access via `getInstance(context)`. Delegate calls from `DeviceListViewModel.kt`.

### Component 2: Global Root Composition (`ATrainingTrackerApp.kt` & `MainActivityWithNavigation.kt`)
* Hoist `KeepTwoSensorsBottomSheet` into `ATrainingTrackerApp.kt` collecting from `SensorQuotaReconciliationManager`.
* Trigger `checkDowngradeReconciliation()` on `MainActivityWithNavigation.onResume()` and reactive `isProActive` transitions outside active tracking.

### Component 3: Pre-Tracking Interception (`ControlTrackingViewModel.kt` & `TrackingTabsScreen.kt`)
* Intercept `onStartTracking()` and `onSearchClicked()`: if `!isProActive && pairedCount > 2`, trigger reconciliation and suppress intent dispatch until resolved.
* Trigger reconciliation check upon entering `TrackingTabsScreen`.

### Component 4: Defense-in-Depth Connection Clamping (`DeviceManager.java`)
* In `createPairedRemoteDevices()`, when `!isProActive.value`, clamp paired remote devices to `MAX_FREE_PAIRED_DEVICES = 2`.

### UI Consistency (Rule 23)
* Reuses existing canonical `KeepTwoSensorsBottomSheet` (`AppModalBottomSheet`, `Icons.Default.Devices`, localized strings across 9 languages from ATT-5398). No new styling required.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Centralized `SensorQuotaReconciliationManager` & Update `docs/architecture.md` (`REQ-SET-084.5`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/monetization/SensorQuotaReconciliationManager.kt`, `docs/architecture.md`
* Extract reconciliation logic from `DeviceListViewModel.kt` into `SensorQuotaReconciliationManager`. Update `docs/architecture.md` (SWE.2).

### Step 2: Refactor `DeviceListViewModel` & Mount Globally in `ATrainingTrackerApp` (`REQ-SET-084.5`)
* Files: `DeviceListViewModel.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`
* Delegate `DeviceListViewModel` to manager. Mount `KeepTwoSensorsBottomSheet` in `ATrainingTrackerApp.kt` and trigger on resume/transitions.

### Step 3: Implement Pre-Tracking Interception & Hardware Clamping (`REQ-SET-084.5`, `REQ-SET-084.7`)
* Files: `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `DeviceManager.java`
* Add quota check to `onStartTracking()` / `onSearchClicked()`. Clamp `createPairedRemoteDevices()` on Free tier.

### Step 4: Unit & Contract Tests (`TST-SET-084.9` - `TST-SET-084.12`)
* Files: `SensorQuotaReconciliationManagerTest.kt`, `ControlTrackingViewModelQuotaTest.kt`, `DeviceManagerQuotaClampingTest.kt`
* Run targeted verification: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*" --tests "com.atrainingtracker.banalservice.devices.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests across monetization, device list, tracking control, and BANALService device manager packages.
* **Rollback**: Discard `feature/ATT-5469` working branch (`git checkout sprint/2026-45.8`) without side effects.
