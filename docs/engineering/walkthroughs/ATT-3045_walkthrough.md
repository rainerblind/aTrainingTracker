# Stage 5 Walkthrough: ATT-3045 - App does not receive location updates when starting tracking immediately after granting permissions on fresh install

**Ticket**: [ATT-3045](https://atrainingtracker.atlassian.net/browse/ATT-3045)  
**Sub-task**: [ATT-3086](https://atrainingtracker.atlassian.net/browse/ATT-3086) (`[Test]`)  
**Parent Epic**: [ATT-2617](https://atrainingtracker.atlassian.net/browse/ATT-2617) (*Track Telemetry: Signal Quality, Sensor Redundancy & Anomaly Detection*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements of `REQ-TRK-017` and test specifications under `TST-TRK-009` have been fully constructed, verified, and audited:

1. **Root Cause Resolution**:
   - On a fresh installation, `DeviceManager` was constructed before the user had granted Android runtime permissions, leaving `mSpeedAndLocationDevice_GPS`, `mSpeedAndLocationDevice_GoogleFused`, and `mSpeedAndLocationDevice_Network` uninstantiated (`null`).
   - Starting tracking immediately after granting permissions bypassed location device initialization because `TrackerService` did not trigger `BANALService.checkOrInitializeLocationDevices()`, leading to zero-coordinate trackpoints (`GCData = 'T------A---'`).
   - We introduced a multi-layered defensive initialization architecture ensuring location devices are checked, instantiated, and update listeners attached upon:
     - `TrackerService.onCreate()`
     - `TrackerService.mBanalConnection.onServiceConnected()` (prior to evaluating `onSearchingFinished()`)
     - `TrainingApplication.startTracking()`
     - Permission escalation and provider re-enablement in `DeviceManager.checkOrInitializeLocationDevices()`

2. **Active Location Listener Re-Registration**:
   - Enhanced `SpeedAndLocationDevice_GPS` with `checkOrReRegisterLocationUpdates()` and `isLocationUpdatesRegistered()` tracking. If `requestLocationUpdates` failed at construction (e.g. provider disabled or permission timing), it is re-attempted seamlessly without requiring an app restart.
   - Enhanced `SpeedAndLocationDevice_GoogleFused` with `checkOrReRegisterLocationUpdates()`.
   - Added virtual `checkOrReRegisterLocationUpdates()` method to `SpeedAndLocationDevice` base class.

3. **Preservation of System Invariants**:
   - Location gating (`REQ-PRI-004`), Android 14+ FGS bitmask derivation (`REQ-TRK-015`), wake lock acquisition (`REQ-TRK-003`), 1Hz deterministic sampling (`REQ-TRK-007`), BLE/ANT+ peripheral discovery, and 100% full clean-room unit test pass rate have been strictly preserved.

---

## 2. Test Execution & Regression Results

### 2.1 Targeted Unit & Contract Tests
- **Test Suite**: `app/src/test/java/com/atrainingtracker/banalservice/devices/FreshInstallLocationInitTest.kt`
- **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.FreshInstallLocationInitTest"`
- **Outcome**: **BUILD SUCCESSFUL in 12s (5 of 5 tests passed)**:
  - `testDeviceManager_whenPermissionGrantedPostInit_instantiatesGpsDevice`: Verifies lazy instantiation of GPS device upon post-init permission grant.
  - `testSpeedAndLocationDeviceGPS_reregisterUpdates_whenInitiallyUnavailable`: Verifies update listener attachment when provider becomes available.
  - `testSpeedAndLocationDeviceGPS_checkOrReRegister_isIdempotentWhenAlreadyRegistered`: Verifies no redundant registration when updates are already active.
  - `testTrackerService_structuralWiring_callsCheckOrInitializeLocationDevices`: Verifies `BANALService.checkOrInitializeLocationDevices()` wiring in `TrackerService`.
  - `testTrainingApplication_structuralWiring_callsCheckOrInitializeLocationDevices`: Verifies `BANALService.checkOrInitializeLocationDevices()` wiring in `startTracking()`.

- **Existing Suites Verification**:
  - `DeviceManagerLocationInitTest`: **BUILD SUCCESSFUL in 18s** (all tests passed).
  - `SpeedAndLocationDeviceResilienceTest`: **BUILD SUCCESSFUL in 12s** (all tests passed).

### 2.2 Clean-Room Full Test Suite Regression
- **Command**: `./gradlew testDebugUnitTest`
- **Duration**: **2m 37s**
- **Outcome**: **100% pass rate**, **0 failures**, **0 errors**, **0 regressions across all 2308+ unit tests**.

---

## 3. Living Documentation & Governance Synchronization

- `docs/requirements.md`: `REQ-TRK-017` updated to `Verified`.
- `docs/tests.md`: `TST-TRK-009` updated to `Verified`.
- `tools/verify_requirement_governance.py --base-ref sprint/2026-41.7`: Verification passed cleanly (code 0).

---

## 4. Visual & UI Consistency Audit (Rule 23)

N/A - Non-UI backend/service ticket; does not introduce or modify any UI components. Affects backend sensor management and tracking services (`DeviceManager.java`, `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `SpeedAndLocationDevice.java`, `TrackerService.java`, `TrainingApplication.java`).
