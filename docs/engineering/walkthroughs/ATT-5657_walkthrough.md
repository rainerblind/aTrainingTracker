# Stage 5: Walkthrough & Verification - ATT-5657: Never recommend Google Fused or sole Location sensor for unpairing

**Ticket**: [ATT-5657](https://rainerblind.atlassian.net/browse/ATT-5657)  
**Parent Epic**: Standalone | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5657`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.2`) | **Test Mapping**: `TST-SET-084` (`TST-SET-084.14`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary & Verification Overview
Resolved an issue where `SwapOrUpgradeDialog` recommended `Google Fused` for unpairing when an external sensor (e.g. cadence or HRM) was paired on Free tier. In `SensorQuotaReconciliationManager.determineRecommendedUnpairDeviceId`, updated the recommendation priority after Barometer and Dual Location checks to filter for non-location sensors (disconnected first, oldest `lastSeen` second), ensuring `Google Fused` and any sole paired location sensor are NEVER recommended for unpairing when non-location sensors exist. Also hardened `SwapOrUpgradeDialog.kt` fallback selection to prioritize non-location sensors.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-084.2` | `TST-SET-084.14a` | `SensorQuotaReconciliationManagerTest` (Fused + Remote) | **PASSED** | `Verified` |
| `REQ-SET-084.2` | `TST-SET-084.14b` | `SensorQuotaReconciliationManagerTest` (Sole GPS + Remote) | **PASSED** | `Verified` |
| `REQ-SET-084.2` | `TST-SET-084.14c` | `SensorQuotaReconciliationManagerTest` (Remote Priority) | **PASSED** | `Verified` |
| `REQ-SET-084.2` | `TST-SET-084.14d` | `DeviceListViewModelQuotaTest` (VM & Dialog Fallback) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | `verify_requirement_governance.py` (Chesterton's Fence) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~1s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*"`
  * `--tests "com.atrainingtracker.trainingtracker.monetization.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL in 1s`, 32 actionable tasks (100% PASS).

### Living Documentation Governance Check
```text
python3 tools/verify_requirement_governance.py
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
Modified/Altered existing requirement(s) detected: REQ-SET-084.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Key Implementation Diffs

### `SensorQuotaReconciliationManager.kt`
```kotlin
// Non-location sensor filtering and unpair activity prioritization (ATT-5657):
val nonLocationDevices = pairedDevices.filter {
    it.deviceType != DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED &&
        it.deviceType != DeviceType.SPEED_AND_LOCATION_GPS
}
if (nonLocationDevices.isNotEmpty()) {
    val candidate = nonLocationDevices.sortedWith(
        compareBy<DeviceUiData> { it.isConnected }
            .thenBy { parseLastSeenTimestamp(it.lastSeen) }
    ).firstOrNull()
    if (candidate != null) return candidate.id
}
return pairedDevices.firstOrNull()?.id ?: -1L
```

---

## 5. Invariants & Backward Compatibility
1. **Free Tier Quota Invariant**: Maximum 2 paired devices limit (`MAX_FREE_PAIRED_DEVICES = 2`) strictly preserved.
2. **Barometer Tier 1 Priority**: `ALTITUDE_FROM_PRESSURE` remains the first recommendation to unpair if present.
3. **Dual Location Preservation**: When both Google Fused and GPS are paired, GPS is recommended, retaining Google Fused.
4. **Sole Location Protection**: Google Fused and sole GPS are never recommended when non-location sensors are paired.
5. **Zero Schema/Service Mutations**: SQLite schemas, DAO mapping, and `BANALService` lifecycles remain completely untouched.
