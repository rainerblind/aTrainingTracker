# Stage 5 Walkthrough: ATT-5490 — Supporter to Free Downgrade Default Sensor Preselection & Unpair Recommendations

**Ticket**: [ATT-5490](https://rainerblind.atlassian.net/browse/ATT-5490)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5490`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.2`, `REQ-SET-084.5`) | **Test Mapping**: `TST-SET-084` (`TST-SET-084.12`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
When an athlete downgrades from Supporter (Pro) to Free tier while having $>2$ sensors paired, `SensorQuotaReconciliationManager` displays the `KeepTwoSensorsBottomSheet`. Previously, the dialog preselected GPS and Barometer, or the first two paired devices, frequently dropping external sensors (HRM, Power meter) and preserving Barometer over vital telemetry. Additionally, `DeviceListViewModel`'s unpair recommendation recommended GPS ahead of remote sensors when Barometer was absent.

In this ticket:
1. **Slot 1 Location Preselection**: Preselects Google Fused (`SPEED_AND_LOCATION_GOOGLE_FUSED`) when both Google Fused and GPS are paired. If only a single location sensor is paired, that sensor is preserved.
2. **Slot 2 Remote Sensor Prioritization**: Preselects the most recently active remote sensor (`protocol != Protocol.SMARTPHONE`), prioritized by `isConnected == true` first, latest raw epoch `lastSeen` timestamp second, and original list order third.
3. **Graceful Fallback**: If fewer than 2 sensors are retained, falls back to `ALTITUDE_FROM_PRESSURE` and remaining devices in list order.
4. **Numeric Timestamp Invariant**: Implemented `parseLastSeenTimestamp` strictly comparing raw epoch millisecond primitives (conforming to `docs/coding_standards.md` Section 1.3).
5. **Smart Unpair Recommendation**: Recommends `ALTITUDE_FROM_PRESSURE` first; if both Google Fused and GPS are paired, recommends `SPEED_AND_LOCATION_GPS` (retaining Google Fused); delegates directly from `DeviceListViewModel`.
6. **SWE.2 Architecture Documentation**: Synchronized `docs/architecture.md` (Section 4.7).

---

## 2. Key Architectural & Code Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `SensorQuotaReconciliationManager.kt` | Implemented `parseLastSeenTimestamp()`; refactored `determineDefaultKeepDeviceIds()` to preselect Google Fused and active remote sensor; added `determineRecommendedUnpairDeviceId()` prioritizing Barometer and GPS unpairing. |
| `DeviceListViewModel.kt` | Delegated `determineRecommendedUnpairDeviceId()` to `SensorQuotaReconciliationManager`. |
| `docs/architecture.md` | Updated Section 4.7 documenting preselection and unpair recommendation hierarchy for Free tier quota reconciliation. |
| `docs/requirements/req_settings_profiles.md` | Updated `REQ-SET-084.2` and `REQ-SET-084.5` with Chesterton's Fence archaeology and preselection specifications. |
| `docs/tests/tst_settings_profiles.md` | Added Clause 12 to `TST-SET-084` covering Google Fused and remote sensor preselection contracts. |
| `SensorQuotaReconciliationManagerTest.kt` | Added unit tests for Fused/GPS coexistence, remote sensor epoch timestamp sorting, single location preservation, fallback, and unpair recommendations. |
| `DeviceListViewModelQuotaTest.kt` | Added tests verifying view model delegation for default keep preselection and unpair recommendation. |

---

## 3. Verification & Test Execution
* **Tier 1 Targeted Test Scope**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.*" --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  -> `BUILD SUCCESSFUL in 18s` (100% pass rate).
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (100% master table parity and validated Chesterton's Fence archaeology for `REQ-SET-084`).
* **Hardware / UI Verification**: Non-UI backend quota reconciliation and algorithm change (no layout/composable modifications; physical device screenshots not required per Rule 60).

---

## 4. Requirement & Test Traceability Matrix
| Requirement ID | Test Case ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-084.2` | `TST-SET-084.12` | `Verified` | Unit test verifying `determineRecommendedUnpairDeviceId` recommends Barometer first, then GPS when both location sensors are paired |
| `REQ-SET-084.5` | `TST-SET-084.12` | `Verified` | Unit test verifying `determineDefaultKeepDeviceIds` preselects Google Fused in Slot 1 and active remote sensor in Slot 2 |
| `REQ-SET-084` | `TST-SET-084.9` | `Verified` | Targeted unit test suite (`SensorQuotaReconciliationManagerTest`, `DeviceListViewModelQuotaTest`) and governance verification pass |
