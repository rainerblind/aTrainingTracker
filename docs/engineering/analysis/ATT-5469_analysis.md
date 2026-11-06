# Stage 1 Analysis: ATT-5469 - Subscription Downgrade Sensor Quota: Trigger Reconciliation Globally on App Start and Tracking Screen

**Ticket**: [ATT-5469](https://rainerblind.atlassian.net/browse/ATT-5469)  
**Sub-task**: `[Analysis]` (To be created via `submit-stage`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5469`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-45.6 review of ATT-5398, the user observed that sensor quota downgrade reconciliation (`checkDowngradeReconciliation()`) is currently only invoked inside `DeviceListViewModel` when opening `DevicesTabbedScreen`. When a Supporter subscription expires (downgrade to Free tier with > 2 paired sensors), an athlete who never navigates to the Sensors screen can continue tracking indefinitely with all previously paired sensors. The reconciliation flow must be triggered globally on app start, on the tracking screen, and before sensor search / workout recording.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Siloed ViewModel Invocation**: `checkDowngradeReconciliation()` is called only in `DeviceListViewModel.init` and `DevicesTabbedScreen.onResume`. Neither `MainActivityWithNavigation`, `ATrainingTrackerApp`, nor `TrackingTabsScreen` / `SensorGridScreen` instantiate `DeviceListViewModel` or trigger the check.
* **Unrestricted Sensor Search**: `BANALService.startSearchForPairedDevices()` and `DeviceManager.createPairedRemoteDevices()` query all devices with `PAIRED > 0` without evaluating `ProFeatureManager.isProActive`, allowing downgraded Free athletes to connect > 2 paired devices during workouts.
* **Independent Peer Review & Verification**:
  * Audited `DeviceListViewModel.kt:99` (`init { checkDowngradeReconciliation() }`) and `DevicesTabbedScreen.kt:660`: bottom sheet is isolated to sensor tabs.
  * Audited `ATrainingTrackerApp.kt`: already hosts global overlays (`AntMissingAdapterDialog`, `FirstJubileeCelebrationDialog`, `PermissionOnboardingSheet`) at root level; hoisting `KeepTwoSensorsBottomSheet` here enables universal presentation across any screen.
  * Audited `ControlTrackingViewModel.kt:168,178`: `onSearchClicked()` and `onStartTracking()` dispatch broadcasts without verifying Free-tier sensor quota.
  * Audited `DeviceManager.java:1117`: creates remote devices for all rows where `PAIRED > 0` without checking `ProFeatureManager.isDevicePairingAllowed()`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Centralize reconciliation state in a shared manager (`SensorQuotaReconciliationManager`) used by `MainActivityWithNavigation`, `ATrainingTrackerApp`, and `DeviceListViewModel`.
  * Hoist `KeepTwoSensorsBottomSheet` into `ATrainingTrackerApp.kt` for universal presentation outside active tracking.
  * Trigger `checkDowngradeReconciliation()` on App Start (`MainActivityWithNavigation.onResume` / `ATrainingTrackerApp`), on Tracking Screen (`TrackingTabsScreen` / `SensorGridScreen`), and before sensor search / workout start (`ControlTrackingViewModel`).
  * Enforce defense-in-depth quota clamping in `DeviceManager.startSearchForPairedDevices()` to limit remote connections to `MAX_FREE_PAIRED_DEVICES` on Free tier.
* **Out-of-Scope Non-Goals**:
  * Modifying `KeepTwoSensorsBottomSheet` UI layout or strings (already localized across 9 languages in ATT-5398).
  * Changing `MAX_FREE_PAIRED_DEVICES = 2` baseline or Supporter monetization tiers.
  * Disrupting or unpairing sensors during an active workout (`isTracking() == true`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-084.5` (*Subscription Downgrade Sensor Quota Reconciliation*), targeting `DeviceListViewModel.kt`, `KeepTwoSensorsBottomSheet.kt`, `DeviceDataRepository.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, and `DeviceManager.java`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-45.6 under `ATT-5398` (commit `d287b0f6`).
* **Root Reason for Existing Formulation**: Scoped initially to `DeviceListViewModel` where sensor pairing management was already implemented, assuming athletes manage sensors on the Sensors screen.
* **Preservation of Core Invariants**: Zero disruption during active tracking (`!isTracking()` guard strictly preserved); non-destructive unpairing (`PAIRED = 0` only, calibration and equipment links 100% intact); 100% backward compatibility for existing `DeviceListViewModelQuotaTest.kt`.

---

## 5. Architectural Strategy & High-Level Solution
* **Centralized Reconciliation Manager (`SensorQuotaReconciliationManager`)**: Encapsulates `keepTwoSensorsDialogEvent: StateFlow<KeepTwoSensorsDialogEvent?>`, `checkDowngradeReconciliation(): Boolean`, `confirmKeepTwoSensors()`, and `dismissKeepTwoSensors()`. Injected into `ATrainingTrackerApp` and delegated from `DeviceListViewModel`.
* **Global Presentation & Lifecycle Hooks**: Mount `KeepTwoSensorsBottomSheet` in `ATrainingTrackerApp.kt`. Invoke `checkDowngradeReconciliation()` in `MainActivityWithNavigation.onResume()`, on `isProActive` transitions, and in `TrackingTabsScreen`.
* **Pre-Tracking Interception**: In `ControlTrackingViewModel`, gate `onStartTracking()` and `onSearchClicked()` by verifying sensor quota before dispatching intents.
* **Service-Level Clamping**: In `DeviceManager.startSearchForPairedDevices()`, clamp paired remote devices to `MAX_FREE_PAIRED_DEVICES` when `!proFeatureManager.isProActive.value`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Quota reconciliation MUST NOT trigger or disconnect sensors during active workout tracking (`TrainingApplication.isTracking() == true`).
  2. Unpaired devices retain all database records, custom names, wheel circumferences, calibration factors, and equipment associations (`PAIRED = 0` only).
  3. Dismissing `KeepTwoSensorsBottomSheet` applies default keep IDs to prevent bypassing the Free quota.
* **Risk Rating**: **MEDIUM** (Touches global app navigation root, BANALService connection lifecycle, and tracking start flow; mitigated by strict `!isTracking()` gating, comprehensive unit tests, and defense-in-depth clamping).
