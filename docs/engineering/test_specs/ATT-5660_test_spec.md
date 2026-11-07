# Stage 2: Requirement & Test Specification - ATT-5660: Stable device list sorting without position jumping

**Ticket**: [ATT-5660](https://rainerblind.atlassian.net/browse/ATT-5660)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5660`  
**Requirement Mapping**: `REQ-UI-334` | **Test Spec ID**: `TST-UI-294`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-334`)

### 1.1 Problem Statement & Rationale
In `DeviceListViewModel.getFilteredDevices()`, the comparator previously ordered devices by `isNewlyDiscovered`, then `isConnected`, then `deviceName`. In the *Bekannt* (`ALL_KNOWN`) and *Gekoppelt* (`PAIRED`) tabs, toggling device pairing instantly changed `isConnected` (especially for smartphone sensors: GPS, Barometer, Google Fused), causing cards to jump across the screen right beneath the user's touch. The system SHALL eliminate sorting by `isConnected`, maintaining strictly stable alphabetical ordering by `deviceName` (with `id` tie-breaker) across pairing state transitions.

### 1.2 Functional Requirements & Sorting Contract
The system SHALL update `DeviceListViewModel.getFilteredDevices()` multi-tier comparator:
1. **Primary Tier (Newly Discovered)**: Devices with `isNewlyDiscovered == true` SHALL remain prioritized at index 0 in recency order (`newlyFoundIds.indexOf(it.id)`).
2. **Secondary Tier (Deterministic Baseline)**: All remaining devices SHALL be sorted deterministically by `deviceName` (case-insensitive alphabetical order) with `it.id` as tie-breaker.
3. **Elimination of `isConnected` Re-Sorting**: The comparator SHALL NOT re-order devices based on `isConnected`. Dedicated filtering for connected sensors in `DeviceFilterType.CONNECTED` SHALL remain unaffected.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Pairing Toggle Stability)**: *Given* an athlete viewing the *Bekannt* or *Gekoppelt* tab with multiple sensors, *When* toggling the pairing switch on any sensor, *Then* the sensor card retains its exact index position in the list without jumping.
* **AC-2 (Newly Discovered Priority Preserved)**: *Given* an active scan session discovering a new sensor, *When* `DeviceListViewModel.getFilteredDevices()` evaluates, *Then* newly discovered sensors remain pinned at index 0 in reverse-discovery recency order.
* **AC-3 (Connected Tab Preservation)**: *Given* the *Verbunden* tab (`DeviceFilterType.CONNECTED`), *When* evaluating devices, *Then* only sensors with `isConnected == true` are displayed.
* **Invariants**: `DeviceFilterCriteria` matching unchanged; `newlyFoundDevicesIds` pipeline unchanged; zero database mutations.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-334` (*Newly Discovered Sensor Prioritization*, Clause 2), targeting `DeviceListViewModel.kt`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-3099` (commit `7df8d5b0`, Sprint `2026-41.8`) to keep active sensors visible above idle sensors during scanning.
* `Root Reason for Existing Formulation`: Assumed active sensors provided better immediate context at top, overlooking that pairing internal smartphone sensors toggles `isConnected` instantly, causing cards to jump during user interaction.
* `Preservation of Core Invariants`: Top-of-list `isNewlyDiscovered` prioritization preserved; `CONNECTED` tab filtering preserved; stable alphabetical sorting guarantees zero jumping.

---

## 2. Test Specification (`TST-UI-294`)

### Test Case 1: Stable Sorting During Pairing / Connection Toggle (`TST-UI-294.7`)
* **Scope**: ViewModel Unit Test (`DeviceListViewModelPriorityTest.kt`)
* **Setup**: Mock list containing 4 devices with varied pairing and connection states.
* **Action**: Toggle `isConnected` on a device while keeping `deviceName` and `isNewlyDiscovered` constant; observe `getFilteredDevices(ALL_KNOWN)`.
* **Expected Result**: Item index positions remain 100% identical before and after `isConnected` change.

### Test Case 2: Newly Discovered Prioritization Recency Contract (`TST-UI-294.2`)
* **Scope**: ViewModel Unit Test (`DeviceListViewModelPriorityTest.kt`)
* **Action**: Verify `isNewlyDiscovered` sensors sort ahead of existing devices, ordered by discovery recency, with baseline devices ordered alphabetically.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-294.2` | ViewModel | `DeviceListViewModel.getFilteredDevices` | `REQ-UI-334.1` | Specified |
| `TST-UI-294.7` | ViewModel | `DeviceListViewModel.getFilteredDevices` | `REQ-UI-334.2` | Specified |