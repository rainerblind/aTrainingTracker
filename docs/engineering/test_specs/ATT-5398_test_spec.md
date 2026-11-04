# Stage 2: Requirement & Test Specification - ATT-5398: Subscription Downgrade Sensor Quota Reconciliation

**Ticket**: [ATT-5398](https://rainerblind.atlassian.net/browse/ATT-5398)  
**Sub-task**: [Req & Test Spec]  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5398`  
**Requirement Mapping**: `REQ-SET-084` | **Test Spec ID**: `TST-SET-084`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-04  

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-084` (One-Tap Swap-or-Upgrade Dialog on 3rd Paired Device, 2-device Free quota).
* **Historical Origin & Commit Trace**: Commit `de0e1d55` (`ATT-51`, Sprint `2026-45.4`).
* **Root Reason for Existing Formulation**: The initial monetization MVP addressed pairing-time quota interception, assuming an athlete in Free mode starts with $\le 2$ paired devices, without handling post-expiration subscription downgrade reconciliation.
* **Preservation of Core Invariants**: Retains the 2-sensor Free quota (`REQ-SET-083`), prevents workout disruption (`!isTracking()`), guarantees zero sensor metadata deletion in `Devices.db`, and prevents quota bypass on dismissal.

---

## 1. Requirement Specification (REQ-SET-084)

### 1.1 Functional & Architectural Requirements
* `REQ-SET-084.5` (Downgrade Reconciliation): When `!isProActive() && pairedDevicesCount > 2 && !TrainingApplication.isTracking()`, `DeviceListViewModel` SHALL emit a reconciliation event to present `KeepTwoSensorsBottomSheet` (`AppModalBottomSheet`).
* The sheet SHALL list paired devices, preselect 2 sensible defaults (`GPS` + `ALTITUDE_FROM_PRESSURE` or 2 most recent), enforce max 2 selections, and offer an unlock action to `SupporterScreen`.
* Confirming or dismissing the sheet SHALL call `reconcilePairedDevices(keepIds)` setting `PAIRED = 0` on unselected devices in `Devices.db` while keeping all custom names, calibration, and equipment links intact.
* `REQ-SET-084.6` (Multi-Sensor Swap Hardening): In Free tier, `executeSwapAndPair()` SHALL prune all excess paired devices, ensuring total paired count is strictly $\le 2$.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (Reconciliation Prompt)**: Given a Free-tier athlete with 4 paired devices and no active workout, when opening the sensor list, then `KeepTwoSensorsBottomSheet` is displayed with 2 defaults preselected.
* **AC-2 (Metadata Preservation)**: Given the reconciliation sheet, when confirmed or dismissed, then unselected sensors have `PAIRED = 0`, zero rows are deleted from `Devices.db`, and total paired sensors equals 2.
* **AC-3 (Swap Hardening)**: Given a Free-tier state with $> 2$ paired sensors, when swapping a sensor, then excess paired devices are pruned and total paired count is strictly $\le 2$.

---

## 2. Test Specification (TST-SET-084)

### Test Cases
* **`TST-SET-084.5` (Reconciliation Trigger)**: Unit test in `DeviceListViewModelQuotaTest`: verify `!isProActive() && pairedCount > 2` emits `KeepTwoSensorsEvent` with 2 sensible defaults.
* **`TST-SET-084.6` (Batch Prune & Data Invariant)**: Repository test verifying unselected sensors are marked `isPaired = false` without deleting rows or wiping calibration/equipment data.
* **`TST-SET-084.7` (Swap Hardening)**: Unit test asserting `executeSwapAndPair()` when `pairedCount > 2` results in total paired count $\le 2$.
* **`TST-SET-084.8` (9-Locale Localization Parity)**: Audit strings (`keep_two_sensors_title`, `keep_two_sensors_message`, `keep_two_sensors_action_confirm`, `keep_two_sensors_action_unlock`) across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-084.5` | Unit | `DeviceListViewModel.checkDowngradeReconciliation` | `REQ-SET-084.5` | Specified |
| `TST-SET-084.6` | Unit/Repo | `DeviceDataRepository.reconcilePairedDevices` | `REQ-SET-084.5` | Specified |
| `TST-SET-084.7` | Unit | `DeviceListViewModel.executeSwapAndPair` | `REQ-SET-084.6` | Specified |
| `TST-SET-084.8` | Localization | `TranslationParityTest` | `REQ-SET-084.5` | Specified |
