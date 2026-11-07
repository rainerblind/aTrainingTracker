# Stage 5: Walkthrough & Verification - ATT-5660: Stable device list sorting without position jumping

**Ticket**: [ATT-5660](https://rainerblind.atlassian.net/browse/ATT-5660)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5660`  
**Requirement Mapping**: `REQ-UI-334` | **Test Mapping**: `TST-UI-294`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Resolved an erratic UI jumping issue in `DeviceListViewModel.kt` (`getFilteredDevices`). Removed `.thenByDescending { it.isConnected }` so toggling pairing on internal smartphone sensors (GPS, Barometer, Google Fused) or nearby devices no longer causes cards to jump across the screen underneath the user's touch. Implemented stable, case-insensitive alphabetical sorting by `deviceName` (with `id` tie-breaker) while strictly preserving top-of-list `isNewlyDiscovered` prioritization and dedicated filtering in the *Verbunden* (`CONNECTED`) tab.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-334.1` | `TST-UI-294.2` | `DeviceListViewModelPriorityTest` (Recency Priority) | **PASSED** | `Verified` |
| `REQ-UI-334.2` | `TST-UI-294.7` | `DeviceListViewModelPriorityTest` (Stable Sorting) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-294` | Tier 1 Targeted Test Suite (Build & Theme) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | `verify_requirement_governance.py` (100% parity) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~1–2s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**:
```text
BUILD SUCCESSFUL in 1s
32 actionable tasks: 1 from cache, 31 up-to-date
All targeted tests passed (100% PASS).
```

### Living Documentation Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Modified/Altered existing requirement(s) detected: REQ-UI-334.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Comparator Implementation Diff

```kotlin
// Before (unstable jumping on pairing toggle):
filtered.sortedWith(
    compareByDescending<DeviceUiData> { it.isNewlyDiscovered }
        .thenByDescending { if (it.isNewlyDiscovered) newlyFoundIds.indexOf(it.id) else -1 }
        .thenByDescending { it.isConnected }
        .thenBy { it.deviceName }
)

// After (stable alphabetical baseline without connection jumping):
filtered.sortedWith(
    compareByDescending<DeviceUiData> { it.isNewlyDiscovered }
        .thenByDescending { if (it.isNewlyDiscovered) newlyFoundIds.indexOf(it.id) else -1 }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.deviceName }
        .thenBy { it.id }
)
```

---

## 5. Invariants & Backward Compatibility
1. **Newly Discovered Priority Preserved**: Sensors flagged with `isNewlyDiscovered == true` remain pinned at index 0 in recency order.
2. **Stable Alphabetical Ordering**: Baseline devices remain in deterministic order regardless of connection/pairing state transitions.
3. **Connected Filter Preserved**: `DeviceFilterType.CONNECTED` continues to filter `devices.filter { it.isConnected }`.
4. **Zero Protocol/Database Mutations**: BLE/ANT+ scanning, GATT state flows, and SQLite pairing persistence remain completely unaffected.