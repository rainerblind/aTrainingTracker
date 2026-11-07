# Stage 3: Implementation Plan - ATT-5660: Stable device list sorting without position jumping

**Ticket**: [ATT-5660](https://rainerblind.atlassian.net/browse/ATT-5660)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5660`  
**Requirement Mapping**: `REQ-UI-334` | **Test Mapping**: `TST-UI-294`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `DeviceListViewModel.kt` (`getFilteredDevices`), candidate devices are ordered with `.thenByDescending { it.isConnected }`. When an athlete toggles a sensor's pairing state in the *Bekannt* or *Gekoppelt* tab, internal smartphone sensors (GPS, Barometer, Google Fused) or reachable devices instantly switch connection state (`isConnected = true/false`), causing cards to jump across the screen under the user's touch. The sorting comparator must be made stable by eliminating `isConnected` sorting and ordering deterministically by `deviceName` (with `id` tie-breaker).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-334` (*Newly Discovered Sensor Prioritization & Stable List Ordering*, Clause 2)
* **Test Mapping**: `TST-UI-294` (`TST-UI-294.2` Scan Priority, `TST-UI-294.7` Stable Sorting Contract)

---

## 3. System Invariants & Preserved Behavior
1. **Newly Discovered Priority Preserved**: `isNewlyDiscovered == true` sensors remain pinned at index 0 in recency order (`newlyFoundIds.indexOf(it.id)`).
2. **Stable Deterministic Baseline**: All non-new devices sort strictly by `deviceName` (case-insensitive) with `id` as tie-breaker.
3. **No In-List Jumping**: Toggling pairing or connection state leaves device index positions completely stable.
4. **Connected Tab Preserved**: Dedicated filtering in `DeviceFilterType.CONNECTED` (`devices.filter { it.isConnected }`) remains unchanged.
5. **Zero Data/Database Regressions**: `DeviceFilterCriteria` matching and repository subscriptions remain intact.

---

## 4. UI Consistency & Architectural Strategy
* **Reference Screen**: `DeviceListScreen.kt` and `DevicesTabbedScreen.kt` in `banalservice/ui/devices/`.
* **Reused Components**: Existing `LazyColumn`, `DeviceItem`, and `MappableListItem`.
* **Theme Tokens**: Standard M3 typography and color tokens.
* **New One-Off Styles**: None. Logic/comparator refinement within ViewModel.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Comparator Update in `DeviceListViewModel.kt` (`REQ-UI-334`)
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModel.kt`
* **Changes**: In `getFilteredDevices()`, remove `.thenByDescending { it.isConnected }`.
* **New Comparator**:
  ```kotlin
  filtered.sortedWith(
      compareByDescending<DeviceUiData> { it.isNewlyDiscovered }
          .thenByDescending { if (it.isNewlyDiscovered) newlyFoundIds.indexOf(it.id) else -1 }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { it.deviceName }
          .thenBy { it.id }
  )
  ```

### Step 2: Unit Test Updates & Stability Assertions in `DeviceListViewModelPriorityTest.kt` (`TST-UI-294`)
* **File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListViewModelPriorityTest.kt`
* **Changes**:
  * In `testNewlyDiscoveredSensors_areSortedAtTopOfListInRecencyOrder`: update assertions so `Alpha Sensor` (disconnected, id 1) is at index 2 and `Beta Sensor` (connected, id 2) is at index 3 per alphabetical order.
  * Add `testPairingToggle_sortingRemainsStableWithoutConnectionJumping`: assert that changing `isConnected` on a device does not change its list index in `getFilteredDevices(ALL_KNOWN)`.

### Step 3: Targeted Verification Suite (`TST-UI-294`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and governance checks; verify with Gradle runner.
* **Rollback**: Clean git branch revert on `feature/ATT-5660` with zero impact on `sprint/2026-45.9`.
