# Stage 2: Requirement & Test Specification - ATT-5657: Never recommend Google Fused or sole Location sensor for unpairing

**Ticket**: [ATT-5657](https://rainerblind.atlassian.net/browse/ATT-5657)  
**Parent Epic**: Standalone | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5657`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.2`) | **Test Spec ID**: `TST-SET-084` (`TST-SET-084.14`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-SET-084.2`)

### 1.1 Problem Statement & Rationale
When a Free tier athlete (`!isProActive()`) attempts to pair a 3rd sensor with Google Fused and an external sensor (e.g. cadence, HRM) already paired, `SwapOrUpgradeDialog` previously recommended `Google Fused` for unpairing. In `SensorQuotaReconciliationManager.determineRecommendedUnpairDeviceId()`, falling back to `pairedDevices.firstOrNull()` selected index 0, which is always the internal smartphone location sensor. Unpairing the athlete's primary location provider degrades tracking capability. The system SHALL guarantee that Google Fused and any sole paired location sensor are never recommended for unpairing when non-location sensors are available.

### 1.2 Functional Requirements & Recommendation Priority
The system SHALL evaluate unpairing recommendation in `determineRecommendedUnpairDeviceId()` using this deterministic hierarchy:
1. **Tier 1 (Barometer)**: Recommend `ALTITUDE_FROM_PRESSURE` first if paired.
2. **Tier 2 (Dual Location)**: If both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` are paired, recommend `SPEED_AND_LOCATION_GPS` (retaining Google Fused).
3. **Tier 3 (Non-Location Sensor Prioritization)**: If any non-location sensor (`it.deviceType` not in location types) is paired alongside a location sensor, recommend the non-location sensor, prioritized by disconnected state first (`!isConnected`), oldest parsed `lastSeen` timestamp second, and original list order third.
4. **Tier 4 (Absolute Location Protection)**: The system SHALL NEVER recommend `SPEED_AND_LOCATION_GOOGLE_FUSED` or a sole paired location sensor for unpairing when any non-location sensor is paired.
5. **Tier 5 (SwapOrUpgradeDialog Fallback)**: In `SwapOrUpgradeDialog.kt`, fallback preselection SHALL filter for non-location sensors before defaulting to list order.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Google Fused + Remote Sensor)**: *Given* Google Fused and an external sensor (e.g. HRM, cadence) paired on Free tier, *When* `determineRecommendedUnpairDeviceId()` evaluates, *Then* the external sensor ID is returned and Google Fused is NOT recommended.
* **AC-2 (Sole GPS + Remote Sensor)**: *Given* sole GPS and an external sensor paired on Free tier, *When* `determineRecommendedUnpairDeviceId()` evaluates, *Then* the external sensor ID is returned and GPS is NOT recommended.
* **AC-3 (Dual Location Preserved)**: *Given* Google Fused and GPS paired, *When* `determineRecommendedUnpairDeviceId()` evaluates, *Then* GPS is recommended, preserving Google Fused.
* **Invariants**: Free tier 2-device pairing quota preserved; unselected sensors remain intact in SQLite; atomic swap execution intact.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-SET-084` (`REQ-SET-084.2`), targeting `SensorQuotaReconciliationManager.kt` and `SwapOrUpgradeDialog.kt`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-51`, refactored in `ATT-5490` (`3db0fc22`), refined in `ATT-5661`, amended in `ATT-5657`.
* `Root Reason for Existing Formulation`: `pairedDevices.firstOrNull()` was used as a generic fallback, overlooking that index 0 in `Devices.db` is always the internal location sensor.
* `Preservation of Core Invariants`: Barometer unpairing priority preserved; dual-location tie-breaking (drop GPS, keep Google Fused) preserved; base-tier quota ($\le 2$ sensors) strictly preserved.

---

## 2. Test Specification (`TST-SET-084.14`)

### Test Case 1: Google Fused + Remote Sensor Recommendation (`TST-SET-084.14a`)
* **Scope**: Unit Test (`SensorQuotaReconciliationManagerTest.kt`)
* **Setup**: `pairedDevices = listOf(pairedFused, pairedHrm)`.
* **Action**: Invoke `determineRecommendedUnpairDeviceId(pairedDevices)`.
* **Expected Result**: Returns `pairedHrm.id` (3L), NOT `pairedFused.id` (2L).

### Test Case 2: Sole GPS + Remote Sensor Recommendation (`TST-SET-084.14b`)
* **Scope**: Unit Test (`SensorQuotaReconciliationManagerTest.kt`)
* **Setup**: `pairedDevices = listOf(pairedGps, pairedCadence)`.
* **Action**: Invoke `determineRecommendedUnpairDeviceId(pairedDevices)`.
* **Expected Result**: Returns `pairedCadence.id`, NOT `pairedGps.id`.

### Test Case 3: Multiple Remote Sensors Prioritization (`TST-SET-084.14c`)
* **Scope**: Unit Test (`SensorQuotaReconciliationManagerTest.kt`)
* **Setup**: Google Fused paired with connected HRM and disconnected Power.
* **Expected Result**: Returns disconnected Power ID.

### Test Case 4: ViewModel Delegation & Dialog Fallback Protection (`TST-SET-084.14d`)
* **Scope**: Unit Test (`DeviceListViewModelQuotaTest.kt` & `SwapOrUpgradeDialogContractTest.kt`)
* **Action**: Verify `DeviceListViewModel.determineRecommendedUnpairDeviceId` returns remote sensor ID; verify dialog fallback does not select sole location sensor.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-084.14a` | Unit Test | `SensorQuotaReconciliationManager.kt` | `REQ-SET-084.2` | Specified |
| `TST-SET-084.14b` | Unit Test | `SensorQuotaReconciliationManager.kt` | `REQ-SET-084.2` | Specified |
| `TST-SET-084.14c` | Unit Test | `SensorQuotaReconciliationManager.kt` | `REQ-SET-084.2` | Specified |
| `TST-SET-084.14d` | Contract Test | `DeviceListViewModel.kt` / `SwapOrUpgradeDialog.kt` | `REQ-SET-084.2` | Specified |
