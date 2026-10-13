# Stage 2: Requirement & Test Specification (ATT-2972)

**Ticket**: [ATT-2972](https://atrainingtracker.atlassian.net/browse/ATT-2972)  
**Sub-task**: [ATT-3048](https://atrainingtracker.atlassian.net/browse/ATT-3048) (`[Test-Spec]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `bugfix/ATT-2972`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Traceability Matrix

| Requirement ID | Requirement Description | Test Case ID | Test Type | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-TRK-015** | Permission-Gated Foreground Service Types in TrackerService | `TST-TRK-007` | Unit Test | Planned |
| **REQ-TRK-016** | Guaranteed Workout Finalization and Unconditional Completion Broadcast on Stop | `TST-TRK-008` | Unit Test | Planned |

---

## 2. Formal Requirement Specifications

### REQ-TRK-015: Permission-Gated Foreground Service Types in TrackerService
The system SHALL dynamically evaluate held permissions before promoting `TrackerService` to an Android 14+ (API 34+) Foreground Service (ATT-2972):
1. **Permission Gating for FGS Types**:
   - `FOREGROUND_SERVICE_TYPE_LOCATION` SHALL always be included if `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` is granted.
   - `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` SHALL only be included if `BLUETOOTH_CONNECT` (or `BLUETOOTH` on API < 31) is granted.
   - `FOREGROUND_SERVICE_TYPE_HEALTH` SHALL strictly be gated by verifying that the app holds `FOREGROUND_SERVICE_HEALTH` AND at least one health runtime permission (e.g. `ACTIVITY_RECOGNITION`). If health runtime permissions are not held, `FOREGROUND_SERVICE_TYPE_HEALTH` SHALL be omitted.
2. **Crash & Interruption Prevention**:
   - Omitting unpermitted FGS types prevents `ActivityManagerService` from throwing fatal `SecurityException`, ensuring `mTrackingInterrupted` is NOT falsely set to `true` during normal startup.
3. **Preservation of Invariants**:
   - Background BLE sensor polling via `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` remains enabled when Bluetooth permissions are present.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an Android 14+ device where location and bluetooth permissions are granted but `ACTIVITY_RECOGNITION` / health runtime permissions are absent,
- *When* `TrackerService.onStartCommand()` derives the foreground service type,
- *Then* the derived bitmask SHALL contain `LOCATION | CONNECTED_DEVICE` and SHALL NOT contain `HEALTH`.
- *Given* an Android 14+ device where health runtime permission IS granted,
- *When* `TrackerService.onStartCommand()` derives the foreground service type,
- *Then* the derived bitmask SHALL include `HEALTH`.

---

### REQ-TRK-016: Guaranteed Workout Finalization and Unconditional Completion Broadcast on Stop
The system SHALL guarantee the finalization and closure of an active workout session when tracking terminates (ATT-2972):
1. **Guaranteed Finalization Protocol**:
   - In `TrackerService.onDestroy()`: If a workout session was created (`mWorkoutID > 0`), the system SHALL invoke `endWorkout()` regardless of `mTrackingInterrupted` unless the session failed during initial synchronous table allocation before any workout row existed.
2. **Defensive Database Commit & Intent Broadcast**:
   - `endWorkout()` SHALL structure execution using `try/finally` blocks such that updating `WorkoutSummaries.FINISHED = 1` and dispatching `TRACKING_FINISHED_INTENT` with `WORKOUT_ID` are guaranteed to execute even if subsidiary operations (e.g. `createNewLap()`, peripheral persistence, or file exports) throw an unexpected exception.
3. **App Restart Cleanliness**:
   - Setting `FINISHED = 1` guarantees that `hasUnfinishedWorkout()` evaluates to `false` on subsequent app launches, preventing false crash warning dialogs.

**Acceptance Criteria (Given-When-Then)**:
- *Given* an active workout tracking session with `mWorkoutID > 0`,
- *When* the workout is stopped (via UI Stop, notification stop, or service teardown),
- *Then* `WorkoutSummaries.FINISHED = 1` SHALL be committed to SQLite and `TRACKING_FINISHED_INTENT` SHALL be broadcast containing `WORKOUT_ID`.
- *Given* a transient failure occurs during peripheral device persistence or live session export in `endWorkout()`,
- *When* the service tears down,
- *Then* `WorkoutSummaries.FINISHED = 1` SHALL still be committed and `TRACKING_FINISHED_INTENT` dispatched.
- *Given* a cleanly finished workout,
- *When* the athlete relaunches the application,
- *Then* `hasUnfinishedWorkout()` SHALL return `false` and `StartOrResumeDialog` SHALL NOT be shown.

---

## 3. Test Specifications

### TST-TRK-007: Permission-Gated Foreground Service Types Verification
- **Test Class**: `com.atrainingtracker.trainingtracker.tracker.TrackerServiceFGSTypeTest`
- **Methodology**:
  - Test 1: Given absence of `ACTIVITY_RECOGNITION`, verify `getForegroundServiceType()` omits `FOREGROUND_SERVICE_TYPE_HEALTH`.
  - Test 2: Given presence of `ACTIVITY_RECOGNITION` and `BLUETOOTH_CONNECT`, verify `getForegroundServiceType()` includes both `HEALTH` and `CONNECTED_DEVICE`.
  - Test 3: Verify no `SecurityException` occurs on FGS startup with derived types.

### TST-TRK-008: Guaranteed Workout Finalization and Completion Broadcast Test
- **Test Class**: `com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest`
- **Methodology**:
  - Test 1: Verify `endWorkout()` sets `FINISHED = 1` in `WorkoutSummaries.TABLE` and dispatches `TRACKING_FINISHED_INTENT`.
  - Test 2: Mock subsidiary helper failure in `endWorkout()`, assert `FINISHED = 1` and intent broadcast are still executed via `try/finally`.
  - Test 3: Verify `onDestroy()` executes `endWorkout()` when `mWorkoutID > 0`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: Net-new requirements (`REQ-TRK-015`, `REQ-TRK-016`).
2. **Historical Origin & Commit Trace**: Ticket `ATT-2972`, Sprint `2026-41.6`. Arises from regression introduced in `ATT-2773` commit `53370e78` where unconditional inclusion of `FOREGROUND_SERVICE_TYPE_HEALTH` caused `SecurityException`, triggering service interruption and omitting `endWorkout()`.
3. **Root Reason for Existing Formulation**: Previously, `TrackerService` simply combined all Android 14+ FGS types without checking health runtime permissions, assuming targetSDK 34+ would accept manifest declaration alone. Android 14+ / 15 OS runtime validates that health types mandate active runtime grants.
4. **Preservation of Core Invariants**: BLE connected device type is preserved when Bluetooth permissions are held; background location remains active; existing lap creation invariants (`REQ-TRK-002`) and reactive summary updates (`REQ-TRK-010`) remain intact.
