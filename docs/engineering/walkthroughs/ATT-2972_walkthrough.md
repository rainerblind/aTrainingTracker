# Stage 5 Walkthrough: ATT-2972 - Workout finalization failure on stop: Workout marked unfinished and Edit Workout view does not open

**Ticket**: [ATT-2972](https://atrainingtracker.atlassian.net/browse/ATT-2972)  
**Sub-task**: [ATT-3051](https://atrainingtracker.atlassian.net/browse/ATT-3051) (`[Test]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `bugfix/ATT-2972`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements under `REQ-TRK-015` and `REQ-TRK-016` along with test specifications `TST-TRK-007` and `TST-TRK-008` have been implemented and verified.

### 1.1 Regression Root Cause & Archaeology
Using the newly established `regression-bisect` skill and `tools/sprint_bisect.py`, the interval was bounded between `Sprint 2026-41.1` (`d70612e8`, WORKING) and `Sprint 2026-41.4` (`813e94ca`, BROKEN). Interactive git interval bisection isolated the exact breaking commit:
- **Culprit Commit**: `53370e78ba98ffa8e3b6719b923afa24e9ec51f0` (*fix(ble): direct TRANSPORT_LE connection, aggressive ScanSettings, and Android 14+ FGS types (ATT-2773)*).
- **Mechanism**: On Android 14+ (API 34+), commit `53370e78` unconditionally included `FOREGROUND_SERVICE_TYPE_HEALTH` in `startForeground()`. Under Android 14+ security policies, declaring `FOREGROUND_SERVICE_HEALTH` in the manifest without holding runtime permissions (e.g. `ACTIVITY_RECOGNITION`) causes `ActivityManagerService` to throw a fatal `SecurityException`.
- **Failure Cascade**: The exception was caught by `TrackerService.onStartCommand()`, which flagged `mTrackingInterrupted = true`. Subsequently, when the user stopped tracking, `TrackerService.onDestroy()` evaluated `if (!mTrackingInterrupted) endWorkout();`. Because `mTrackingInterrupted` was falsely `true`, `endWorkout()` was skipped entirely! The workout was never marked `FINISHED = 1`, `TRACKING_FINISHED_INTENT` was never sent, Edit Workout view did not open, and subsequent app launches displayed false crash/unfinished workout dialogs.

### 1.2 Implemented Fix
1. **Dynamic Permission Gating (`REQ-TRK-015`)**:
   - `determineForegroundServiceType()` dynamically derives foreground service flags on Android 14+ based on held runtime permissions.
   - `FOREGROUND_SERVICE_TYPE_HEALTH` is only requested if health runtime permissions (`ACTIVITY_RECOGNITION`) are granted.
   - `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` is gated by Bluetooth permissions (`BLUETOOTH_CONNECT` on API 31+).
   - `FOREGROUND_SERVICE_TYPE_LOCATION` is included when location permissions are granted.
   - This eliminates the `SecurityException`, so `mTrackingInterrupted` is NOT falsely set during normal startup.
2. **Guaranteed Workout Finalization & Transactional Integrity (`REQ-TRK-016`)**:
   - `endWorkout()` wraps SQLite summary updates in an explicit database transaction (`beginTransaction()`, `setTransactionSuccessful()`, `endTransaction()`).
   - Unconditional dispatch of `TRACKING_FINISHED_INTENT` with `WORKOUT_ID` is enforced in a `finally` block.
   - `TrackerService.onDestroy()` invokes `endWorkout()` when `mWorkoutID > 0`, ensuring complete session closure.

### 1.3 Physical On-Device Verification
- **Device**: Google Pixel 10 (`66020DLCR002FL`, Android 16 preview / targetSDK 37).
- **Procedure**:
  1. Built debug APK and deployed to device.
  2. Launched workout tracking session.
  3. Stopped workout tracking session.
  4. Verified workout immediately completed, `WorkoutSummaries.FINISHED` committed as `1`, `EditWorkout` view opened seamlessly, and subsequent app restarts did not show unclosed workout warnings.
- **Human Verification**: Confirmed and signed off by user: *"Yes. This works now. Thanks a lot."*

---

## 2. Automated Test Execution & Regression Results

### 2.1 Targeted Unit Test Suites
1. **`TrackerServiceFGSTypeTest.kt` (`TST-TRK-007`)**:
   - `testApi34_withoutHealthPermission_omitsHealthType`: Verified `FOREGROUND_SERVICE_TYPE_HEALTH` is omitted when `ACTIVITY_RECOGNITION` is missing.
   - `testApi34_withHealthPermission_includesHealthType`: Verified `FOREGROUND_SERVICE_TYPE_HEALTH` is included when permission is held.
   - `testApi33_returnsZeroForegroundServiceType`: Verified legacy API levels return `0`.
   - **Result**: 3/3 tests passed.
2. **`TrackerServiceFinalizationTest.kt` (`TST-TRK-008`)**:
   - `testEndWorkout_marksFinishedAndDispatchesBroadcast`: Verified `FINISHED = 1` written to SQLite and `TRACKING_FINISHED_INTENT` broadcast dispatched.
   - `testEndWorkout_resilientToAuxiliaryExceptions`: Verified that even if auxiliary operations throw, transaction and broadcast execute via `finally`.
   - `testOnDestroy_callsEndWorkout_whenWorkoutIdValid`: Verified normal teardown triggers finalization cleanly.
   - **Result**: 3/3 tests passed.

### 2.2 Clean-Room Full Test Suite Regression
- Command: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL**
- Total Tests: **2,290 tests passed**, **0 failed**, **0 regressions**.

---

## 3. Architecture & Code Changes

1. **`TrackerService.java`**:
   - Added `getBuildVersionSdkInt()` helper for testable SDK version abstraction.
   - Added `hasBluetoothPermission()` and `hasHealthPermission()` runtime permission checks.
   - Added `determineForegroundServiceType()` dynamically gating Android 14+ FGS bitmask.
   - Hardened `endWorkout()` with transactional atomicity (`beginTransaction() ... setTransactionSuccessful() ... endTransaction()`).
   - Ensured `TRACKING_FINISHED_INTENT` dispatches unconditionally via `finally`.
   - Comprehensive JavaDoc documentation on all new methods.
2. **Unit Tests**:
   - `TrackerServiceFGSTypeTest.kt`: Unit tests for FGS type permission gating.
   - `TrackerServiceFinalizationTest.kt`: Unit tests for workout finalization atomicity and intent broadcast.
3. **Living Documentation & Governance**:
   - `docs/requirements.md`: `REQ-TRK-015` and `REQ-TRK-016` updated to `Verified`.
   - `docs/tests.md`: `TST-TRK-007` and `TST-TRK-008` added and marked `Verified`.

---

## 4. Invariants & Preservations Check

- [x] **BLE Connected Device Type**: Preserved when Bluetooth permissions are granted.
- [x] **Location Tracking**: Preserved via `FOREGROUND_SERVICE_TYPE_LOCATION`.
- [x] **Lap Creation & Summary Invariants (`REQ-TRK-002`, `REQ-TRK-010`)**: Intact.
- [x] **Crash Recovery (`REQ-STB-002`)**: Unfinished workout detection preserved for genuine crashes.
- [x] **Clean-Room Regression**: 100% full test suite pass rate (2,290 passed, 0 failed).

---

## 5. Recommendation

**RECOMMEND PASS**: Advance `ATT-3051` to `Erledigt` via Gate 5 audit, merge `bugfix/ATT-2972` into `sprint/2026-41.6`, set FixVersion `V4.9.39` on `ATT-2972`, and advance `ATT-2972` to `Final Review (Human)`.
