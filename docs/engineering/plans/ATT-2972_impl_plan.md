# Stage 3 Implementation Plan: ATT-2972 - Workout finalization failure on stop

**Ticket**: [ATT-2972](https://atrainingtracker.atlassian.net/browse/ATT-2972)  
**Sub-task**: [ATT-3049](https://atrainingtracker.atlassian.net/browse/ATT-3049) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `bugfix/ATT-2972`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Overview & SWE.2 Detailed Design

```
+-------------------------------------------------------------+
|                      TrackerService                         |
+-------------------------------------------------------------+
| 1. onStartCommand()                                         |
|    - getForegroundServiceType()                             |
|      - LOCATION (always if location granted)                |
|      - CONNECTED_DEVICE (if BLUETOOTH_CONNECT granted)      |
|      - HEALTH (only if ACTIVITY_RECOGNITION granted)        |
|    - performStartForeground(ID, notif, derivedType)         |
|      ==> No SecurityException on Android 14+!               |
+-------------------------------------------------------------+
| 2. onDestroy()                                              |
|    - if (mWorkoutID > 0) endWorkout();                      |
|      ==> Guarantees finalization if workout row exists      |
+-------------------------------------------------------------+
| 3. endWorkout()                                             |
|    - try: createNewLap()                                    |
|    - try: activeDevicesDb.insert()                          |
|    - try: summariesDatabaseManager.saveAccumulated()        |
|    - try: summariesDb.update(FINISHED = 1)                  |
|    - finally:                                               |
|        - sendBroadcast(TRACKING_FINISHED_INTENT)            |
|        - LocalBroadcastManager.sendBroadcast(...)          |
+-------------------------------------------------------------+
```

---

## 2. Invariants & Governance Rules
1. **`REQ-CON-019` Preservation**: BLE sensors must maintain foreground connectivity via `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` when Bluetooth permissions are granted.
2. **`REQ-TRK-002` Lap Guard**: Zero-duration phantom laps remain suppressed in `createNewLap()`.
3. **`REQ-TRK-010` Reactive Data Layer**: Broadcasts to `LocalBroadcastManager` for `WorkoutRepository` and cluster engines must be dispatched.
4. **Android 14/15 Permission Contract**: Never pass `FOREGROUND_SERVICE_TYPE_HEALTH` to `startForeground()` unless `android.permission.ACTIVITY_RECOGNITION` (or equivalent health runtime permission) is granted.

---

## 3. Step-by-Step Implementation Sequence

### Step 1: Add Permission Helpers & Gated FGS Type Derivation (`TrackerService.java`)
- Implement `hasHealthPermission()` checking `ACTIVITY_RECOGNITION` and `FOREGROUND_SERVICE_HEALTH`.
- Implement `hasBluetoothPermission()` checking `BLUETOOTH_CONNECT` (or `BLUETOOTH`).
- Implement `getForegroundServiceType()` combining only permitted flags.
- Update `onStartCommand()` to call `performStartForeground(..., getForegroundServiceType())`.

### Step 2: Harden Workout Teardown & Finalization (`TrackerService.java`)
- Update `onDestroy()`: Ensure `endWorkout()` is invoked whenever `mWorkoutID > 0`.
- Harden `endWorkout()`: Wrap intermediate database helper operations in discrete `try/catch` blocks and place SQLite `FINISHED = 1` commit and intent broadcasting in a `try/finally` block.

### Step 3: Implement Automated Unit Tests
- Create `TrackerServiceFGSTypeTest.kt` verifying `getForegroundServiceType()` dynamically adapts to granted vs ungranted permissions.
- Create `TrackerServiceFinalizationTest.kt` verifying `FINISHED = 1` and completion intents are dispatched even if ancillary operations fail.

### Step 4: Verification & Regression Testing
- Execute targeted unit tests.
- Deploy debug APK to connected device (`Pixel 10`) and test physical Start -> Stop -> Edit Workout transition.
- Run complete test suite: `./gradlew testDebugUnitTest`.
