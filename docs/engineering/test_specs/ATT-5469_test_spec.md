# Stage 2: Requirement & Test Specification - ATT-5469: Subscription Downgrade Sensor Quota Reconciliation Triggered Globally

**Ticket**: [ATT-5469](https://rainerblind.atlassian.net/browse/ATT-5469)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5469`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.5`, `REQ-SET-084.7`) | **Test Spec ID**: `TST-SET-084`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-084.5` (*Subscription Downgrade Sensor Quota Reconciliation*), targeting `DeviceListViewModel.kt`, `KeepTwoSensorsBottomSheet.kt`, `DeviceDataRepository.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, and `DeviceManager.java`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-45.6 under `ATT-5398` (commit `d287b0f6`).
* **Root Reason for Existing Formulation**: Scoped initially to `DeviceListViewModel` where sensor pairing management was already implemented, assuming athletes manage sensors on the Sensors screen.
* **Preservation of Core Invariants**: Zero disruption during active tracking (`!isTracking()` guard strictly preserved); non-destructive unpairing (`PAIRED = 0` only, calibration and equipment links 100% intact); 100% backward compatibility for existing `DeviceListViewModelQuotaTest.kt`.

---

## 1. Requirement Specification (REQ-SET-084)

### 1.1 Functional & Architectural Requirements
* `REQ-SET-084.5` (Global Downgrade Reconciliation): When returning to Free tier (`!isProActive()`) with $> 2$ paired sensors in `Devices.db` while not actively recording (`!isTracking()`), `SensorQuotaReconciliationManager` SHALL emit a reconciliation event presenting `KeepTwoSensorsBottomSheet` globally across the application (`ATrainingTrackerApp`).
* Reconciliation SHALL be triggered on app resume/start (`MainActivityWithNavigation.onResume`), tracking cockpit display (`TrackingTabsScreen`), reactive transition to Free tier (`!isProActive()`), and pre-tracking actions in `ControlTrackingViewModel`.
* `REQ-SET-084.7` (BANALService Hardware Connection Clamping): In `DeviceManager.startSearchForPairedDevices()`, when `!isProActive.value`, connecting remote hardware devices SHALL be clamped to `MAX_FREE_PAIRED_DEVICES = 2` as defense-in-depth against connecting excess sensors prior to UI reconciliation.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (App Start Reconciliation)**: Given a Free-tier athlete with 4 paired devices, when the app starts or resumes outside active tracking, then `KeepTwoSensorsBottomSheet` is presented globally.
* **AC-2 (Tracking Screen Interception)**: Given a Free-tier athlete with 3 paired devices, when tapping Start Tracking or Search Sensors on `ControlTrackingScreen`, then reconciliation is displayed and tracking start is prevented until resolved.
* **AC-3 (Service-Level Clamping)**: Given a Free-tier state with $> 2$ paired sensors, when `BANALService` searches for paired devices, then at most 2 remote hardware devices are connected.

---

## 2. Test Specification (TST-SET-084)

### Test Cases
* **`TST-SET-084.9` (Global Manager Unit Tests)**: In `SensorQuotaReconciliationManagerTest`, verify downgrade detection, default keep IDs, confirm/dismiss pruning, and `isTracking()` suppression.
* **`TST-SET-084.10` (Lifecycle & Navigation Triggers)**: In `MainActivityWithNavigationInteropTest` and `ATrainingTrackerAppQuotaContractTest`, verify reconciliation check is called on resume and sheet is hosted at root.
* **`TST-SET-084.11` (Pre-Tracking Quota Interception)**: In `ControlTrackingViewModelQuotaTest`, verify tapping start tracking or sensor search with $> 2$ paired sensors on Free tier triggers reconciliation and blocks intent dispatch.
* **`TST-SET-084.12` (DeviceManager Clamping)**: In `DeviceManagerQuotaClampingTest`, verify `startSearchForPairedDevices()` clamps remote device creation to `MAX_FREE_PAIRED_DEVICES` on Free tier.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-084.9` | Unit | `SensorQuotaReconciliationManager.checkDowngradeReconciliation` | `REQ-SET-084.5` | Specified |
| `TST-SET-084.10` | Contract/UI | `MainActivityWithNavigation.onResume` / `ATrainingTrackerApp` | `REQ-SET-084.5` | Specified |
| `TST-SET-084.11` | Unit/UI | `ControlTrackingViewModel.onStartTracking` / `onSearchClicked` | `REQ-SET-084.5` | Specified |
| `TST-SET-084.12` | Service/Unit | `DeviceManager.createPairedRemoteDevices` | `REQ-SET-084.7` | Specified |
