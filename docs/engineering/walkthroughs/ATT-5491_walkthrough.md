# Stage 5 Walkthrough: ATT-5491 — Location Sensors: Prioritize Google Fused Over GPS & Fresh Install Default Pairing

**Ticket**: [ATT-5491](https://rainerblind.atlassian.net/browse/ATT-5491)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5491`  
**Requirement Mapping**: `REQ-TRK-024` | **Test Mapping**: `TST-TRK-016`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
On modern Android devices, Google Play Services Fused Location Provider (`SPEED_AND_LOCATION_GOOGLE_FUSED`) provides faster time-to-first-fix and superior trajectory stability compared to raw GPS (`SPEED_AND_LOCATION_GPS`). Previously, raw GPS was listed ahead of Google Fused in `MySensorManager` and paired by default on fresh database initialization. In this ticket:
1. Prioritized `SPEED_AND_LOCATION_GOOGLE_FUSED` over `SPEED_AND_LOCATION_GPS` across `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY` in `MySensorManager.java`, while 100% preserving hardware sensor precedence (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_POWER`, `ALTITUDE_FROM_PRESSURE`).
2. Conditioned fresh database pairing in `DevicesDatabaseManager.java` (`addLocationDevicesToDb`) on `GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS`, pairing Google Fused by default on fresh installs and falling back to raw GPS on de-Googled devices.
3. Preserved autoincrement sequence (`_id=1` GPS, `_id=2` Google Fused, `_id=3` Network) and left existing user databases and pairings completely untouched (zero DB version increment).
4. Synchronized SWE.2 architecture documentation in `docs/architecture.md` (Section 4.8) and living requirements/tests.

---

## 2. Key Architectural & Code Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `MySensorManager.java` | Reordered `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY` to place `SPEED_AND_LOCATION_GOOGLE_FUSED` ahead of `SPEED_AND_LOCATION_GPS`; updated JavaDoc (`REQ-TRK-024.1`). |
| `DevicesDatabaseManager.java` | In `addLocationDevicesToDb`, added Google Play Services availability check to set `PAIRED = true` for Google Fused and `PAIRED = false` for GPS when available, with GPS fallback (`REQ-TRK-024.6`). |
| `docs/architecture.md` | Added Section 4.8 documenting Google Fused precedence and fresh-install default pairing with de-Googled fallback (SWE.2 maintenance). |
| `MySensorManagerPriorityTest.kt` | Updated tests for priority arrays, dynamic upgrade/fallback (`BIKE_SPEED > GOOGLE_FUSED > GPS > NETWORK`), altitude switches, and canonical priority ranks (`TST-TRK-016.1`, `TST-TRK-016.3`, `TST-SEN-025.1`). |
| `DevicesDatabaseManagerOrderingTest.kt` | Added tests verifying Google Fused paired on Play Services available and GPS paired on Play Services unavailable fallback (`TST-TRK-016.4`). |
| `SensorSourceDialogPriorityTest.kt` | Updated `activeBackups` sorting test asserting Google Fused precedes GPS (`TST-UI-227.1`). |

---

## 3. Verification & Test Execution
* **Tier 1 Targeted Test Scope**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  -> `BUILD SUCCESSFUL in 1m 42s` (100% pass rate).
* **Resource Compilation Check**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL in 1s`.
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (100% master table parity and validated Chesterton's Fence archaeology for `REQ-TRK-024`).
* **Hardware / UI Verification**: Non-UI backend telemetry engine & database seeding change (zero UI layout modifications; physical device screenshots not required per Rule 60).

---

## 4. Requirement & Test Traceability Matrix
| Requirement ID | Test Case ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-024.1` | `TST-TRK-016.1` | `Verified` | Unit test verifying priority arrays order Google Fused ahead of GPS |
| `REQ-TRK-024.2` | `TST-TRK-016.3`, `TST-UI-227.1` | `Verified` | Unit test verifying dynamic upgrade/fallback and backup list sorting |
| `REQ-TRK-024.6` | `TST-TRK-016.4` | `Verified` | Unit test verifying Play Services available pairing and de-Googled fallback |
| `REQ-TRK-024` | `TST-TRK-016.9` | `Verified` | Clean-room targeted unit test suite and governance validator pass |
