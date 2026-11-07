# Stage 1 Analysis: ATT-5660 - Sensor List: Stable sorting without position jumping during pairing toggle

**Ticket**: [ATT-5660](https://rainerblind.atlassian.net/browse/ATT-5660)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5660`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During user review of device management (`ATT-5490`), toggling device pairing in `DeviceListScreen` (e.g. in the *Bekannt* or *Gekoppelt* tabs) causes smartphone sensors (*Google Fused*, *GPS*, *Barometer*) and active nearby sensors to immediately jump to the top of the list upon pairing (`isConnected = true`) and drop down upon unpairing (`isConnected = false`).
This erratic list re-ordering shifts cards right beneath the athlete's finger, violating UI predictability and creating an unstable interaction experience.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * In `DeviceListViewModel.kt` (L150–L155), `getFilteredDevices()` orders candidate devices using:
    ```kotlin
    filtered.sortedWith(
        compareByDescending<DeviceUiData> { it.isNewlyDiscovered }
            .thenByDescending { if (it.isNewlyDiscovered) newlyFoundIds.indexOf(it.id) else -1 }
            .thenByDescending { it.isConnected }
            .thenBy { it.deviceName }
    )
    ```
  * When a device's pairing switch is toggled, its `isConnected` state toggles simultaneously (for internal smartphone or reachable sensors), causing the comparator to immediately re-sort the list and displace the clicked item.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * Verified that in the *Verbunden* tab (`DeviceFilterType.CONNECTED`), devices are already filtered by `it.isConnected` (L135).
  * In the *Bekannt* (`ALL_KNOWN`) and *Gekoppelt* (`PAIRED`) tabs, sorting by `isConnected` is completely unnecessary and harmful to interaction stability.
  * Removing `.thenByDescending { it.isConnected }` and relying on deterministic sorting by `deviceName` (with `id` tie-breaker) guarantees that device positions remain strictly stable during pairing transitions.
  * Audited `DeviceListViewModelPriorityTest.kt`: `testNewlyDiscoveredSensors_areSortedAtTopOfListInRecencyOrder` asserted `Beta Sensor` (connected) ahead of `Alpha Sensor` (disconnected); updating this test aligns with the new stable alphabetical sorting contract while preserving `isNewlyDiscovered` priority.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Remove `.thenByDescending { it.isConnected }` from `DeviceListViewModel.getFilteredDevices()`.
  * Ensure deterministic, stable ordering by `deviceName` (tie-breaking with `id`) for non-new sensors.
  * Update `DeviceListViewModelPriorityTest.kt` and add unit test verifying list position stability during `isConnected` state mutations.
  * Synchronize living documentation (`REQ-UI-334`, `TST-UI-294`).
* **Out-of-Scope Non-Goals**:
  * Do not alter newly discovered sensor prioritization (`isNewlyDiscovered` recency ordering remains intact).
  * Do not alter filtering logic (`DeviceFilterCriteria` or `DeviceFilterSpec`).
* **Complexity Rating**: **1 SP (Trivial)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-334` (*Newly Discovered Sensor Prioritization*, Clause 2), targeting `DeviceListViewModel.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3099` (commit `7df8d5b0`, Sprint `2026-41.8`) to keep active sensors visible above idle sensors during scanning.
* **Root Reason for Existing Formulation**: Assumed active sensors would provide better context at the top, without anticipating that pairing switches on internal smartphone sensors instantly mutate `isConnected`, causing jarring position jumping.
* **Preservation of Core Invariants**: `isNewlyDiscovered` top-of-list recency prioritization is 100% preserved; the *Connected* tab filter remains unaffected.

---

## 5. Architectural Strategy & High-Level Solution
* Update `DeviceListViewModel.getFilteredDevices()` comparator: keep `isNewlyDiscovered` priority, remove `isConnected` sorting, and order stably by `deviceName` then `id`.
* Update `DeviceListViewModelPriorityTest.kt` assertions and add `testPairingToggle_sortingRemainsStableWithoutConnectionJumping`.
* Validate with `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*"` and `verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `isNewlyDiscovered` sensors remain pinned at index 0 in recency order.
  2. Zero disruptions to `DeviceFilterCriteria` matching or repository subscriptions.
  3. Stable alphabetical ordering across pairing state transitions.
* **Risk Rating**: **LOW** (Comparator refinement in ViewModel; zero database or protocol mutations).
