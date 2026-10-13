# Stage 5: Walkthrough & Verification - ATT-2357: Require Precise Location permission and dynamically recheck permission state to instantiate GPS device

**Ticket**: [ATT-2357](https://atrainingtracker.atlassian.net/browse/ATT-2357)  
**Sub-task**: [ATT-2519](https://atrainingtracker.atlassian.net/browse/ATT-2519) (`[Test]`)  
**Parent Epic**: [ATT-1191](https://atrainingtracker.atlassian.net/browse/ATT-1191) (*[Epic] Tracking Tabs Enhancement*)  
**Active Sprint**: `Sprint 2026-41.1`  
**Requirement Mapping**: `REQ-PRI-004` (*Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation*)  
**Test Mapping**: `TST-PRI-003` (*Precise Location Gating and Dynamic Sensor Device Instantiation Verification*)  
**Branch**: `feature/ATT-2357`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Previously, the tracking permission check in `ControlTrackingScreen.kt` allowed either `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` to satisfy the location permission check (`checkHasLocation`). However, under Android 12+ (API 31+), athletes can grant only "Approximate Location". In that state, `DeviceManager.java` refused to instantiate `SpeedAndLocationDevice_GPS` because Android's `LocationManager.requestLocationUpdates` using `GPS_PROVIDER` strictly requires `ACCESS_FINE_LOCATION`. Consequently, workouts started in coarse-only state recorded no speed, distance, route coordinates, or elevation, leading to silent telemetry failure. Furthermore, if an athlete granted Precise Location permission mid-session or returned from system settings, `DeviceManager` did not lazily instantiate the GPS devices without a complete app/service restart.

ATT-2357 remediates this architecture by providing:
1. **Strict Precise Location Gating**: `checkHasLocation` in `ControlTrackingScreen.kt` strictly requires `ACCESS_FINE_LOCATION`. Approximate-only permission is treated as missing location, keeping the Start button's amber warning badge active and preventing tracking start until Precise Location is granted.
2. **Contextual Educational Rationale**: When only Approximate Location is granted, `PermissionRationaleSheet.kt` highlights `permission_rationale_precise_location_explanation`, explaining to athletes why GPS is essential for speed, distance, route, and elevation recording.
3. **Lazy Dynamic Location Device Instantiation**: Added `public synchronized void checkOrInitializeLocationDevices()` in `DeviceManager.java` and delegated through `BANALService.java`. When `ACCESS_FINE_LOCATION` is granted mid-session, GPS and Google Fused location devices are instantiated immediately and added to active devices without requiring a process restart.
4. **Lifecycle Re-evaluation Hooks**: Wired `checkOrInitializeLocationDevices()` to `permissionLauncher` in `ControlTrackingScreen.kt` as well as `onResume()` and `onRequestPermissionsResult` in `MainActivityWithNavigation.kt`.
5. **100% 9-Language Localization**: Full localization across EN, DE, ES, FR, IT, JA, NL, PL, and PT for `permission_rationale_precise_location_explanation`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRI-004` (1: Precise Location Gating) | `TST-PRI-003.1` | `ControlTrackingPermissionTest.kt` | **PASSED** (8/8) | `Verified` |
| `REQ-PRI-004` (2: Dynamic Device Instantiation) | `TST-PRI-003.2` | `DeviceManagerLocationInitTest.kt` | **PASSED** (3/3) | `Verified` |
| `REQ-PRI-004` (3: 9-Language Localization Parity) | `TST-PRI-003.3` | `PermissionRationaleSheetContractTest.kt`, `PermissionLocalizationTest.kt` | **PASSED** (6/6, 9/9 locales) | `Verified` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-PRI-003.4` | `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingPermissionTest" \
                            --tests "com.atrainingtracker.banalservice.devices.DeviceManagerLocationInitTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PermissionRationaleSheetContractTest" \
                            --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PermissionLocalizationTest"
```
**Output**:
```text
BUILD SUCCESSFUL in 1m 2s
32 actionable tasks: 2 executed, 30 up-to-date
17 tests, 0 failures, 100% successful
```

- `ControlTrackingPermissionTest`: Verified coarse-only permission does not satisfy location requirements, coarse-only triggers `RationaleStep.FOREGROUND` with explanation, and fine location permission satisfies the check.
- `DeviceManagerLocationInitTest`: Verified `checkOrInitializeLocationDevices()` creates `SpeedAndLocationDevice_GPS` upon fine location permission escalation and operates idempotently without duplicate instantiation.
- `PermissionRationaleSheetContractTest`: Verified contract presence and non-zero ID for all rationale strings including `permission_rationale_precise_location_explanation`.
- `PermissionLocalizationTest`: Verified complete 9-language presence and non-blank content for `permission_rationale_precise_location_explanation` across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 4. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite passed with 100% success rate.
2. **Chesterton's Fence Preservation**: Material 3 JIT rationale flow, battery optimization fallback, and background location cascade remain strictly preserved.
3. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-PRI-004`) and `docs/tests.md` (`TST-PRI-003`) updated to `Verified`.
4. **Governance Script Passed**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.1` passed cleanly.
5. **Subtask Completion**: Stage 5 subtask `ATT-2519` moved to `In Überprüfung` for independent Gate 5 audit.
6. **Parent Ticket Final Review**: Parent ticket `ATT-2357` will be transitioned to `Final Review (Human)` and assigned to `human` upon in-sprint integration.
