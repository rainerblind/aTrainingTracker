# Stage 1 Analysis: ATT-2972 - Workout finalization failure on stop: Workout marked unfinished and Edit Workout view does not open

**Ticket**: [ATT-2972](https://atrainingtracker.atlassian.net/browse/ATT-2972)  
**Sub-task**: [ATT-3047](https://atrainingtracker.atlassian.net/browse/ATT-3047) (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `bugfix/ATT-2972`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Problem Summary

When an athlete stops a workout tracking session on Android 14+ (API 34+), the workout session fails to finalize:
1. **Database Left in Unfinished State**: The recorded session remains in `WorkoutSummaries.TABLE` with `FINISHED = 0` (or `NULL`).
2. **Missing Terminal Broadcast & Navigation**: The terminal broadcast `TRACKING_FINISHED_INTENT` is never dispatched, preventing `MainActivityWithNavigation` from auto-navigating to the `EditWorkoutScreen`.
3. **Spurious Crash Recovery Prompt**: Upon subsequently restarting the app, `MainActivityWithNavigation.checkUnfinishedWorkout()` detects an unfinished row via `hasUnfinishedWorkout()` and presents the `StartOrResumeDialog` warning that *"the previous workout was not finished properly"*.

This issue was confirmed in production/beta testing on a Google Pixel 10 (API 35/37) on 2026-10-10 during Workout 5061 (`2026-10-10_085640`).

---

## 2. Forensic Investigation & Regression Bisection Evidence

Using the newly established `regression-bisect` methodology, binary interval bisection across historical sprint versions bounded the defect between working sprint `Sprint 2026-41.1` (`d70612e8`) and broken sprint `Sprint 2026-41.4` (`813e94ca`).

The exact culprit commit was pinpointed:
* **Culprit Commit**: `53370e78ba98ffa8e3b6719b923afa24e9ec51f0`
* **Jira Ticket**: [ATT-2773](https://atrainingtracker.atlassian.net/browse/ATT-2773) (*Bluetooth LE devices are not discovered during sensor scanning in ControlTrackingScreen*)
* **Author**: Rainer Blind, Thu Oct 8 22:12:53 2026
* **Commit Message**: `fix(ble): direct TRANSPORT_LE connection, aggressive ScanSettings, and Android 14+ FGS types (ATT-2773)`

### 2.1 The Failure Mechanism
In commit `53370e78`, `TrackerService.onStartCommand()` was modified to include additional Android 14+ Foreground Service types:
```java
// TrackerService.java lines 518-522
int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    foregroundServiceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH | ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
}
performStartForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification, foregroundServiceType);
```

On Android 14+ (API 34+ / targetSDK 37), Android enforces strict permission prerequisites for each FGS type:
- `FOREGROUND_SERVICE_TYPE_HEALTH` requires `android.permission.FOREGROUND_SERVICE_HEALTH` **and** at least one runtime health permission:
  `[ACTIVITY_RECOGNITION, HIGH_SAMPLING_RATE_SENSORS, READ_HEART_RATE, ...]`
- Because `ACTIVITY_RECOGNITION` and health runtime permissions were not granted by the athlete, Android's `ActiveServices.validateForegroundServiceType()` threw a fatal `java.lang.SecurityException`:
  ```text
  java.lang.SecurityException: Starting FGS with type health callerApp=ProcessRecord{...} targetSDK=37 requires permissions: all of the permissions [android.permission.FOREGROUND_SERVICE_HEALTH] and any of the permissions [android.permission.ACTIVITY_RECOGNITION, ...]
  ```

### 2.2 Cascading Teardown and Omission of `endWorkout()`
In `TrackerService.java:523-533`:
```java
} catch (SecurityException | IllegalStateException e) {
    Log.e(TAG, "Failed to start foreground service: " + e.getMessage(), e);
    mTrackingInterrupted = true;
    if (mTrackerHandle != null) {
        mTrackerHandle.cancel(true);
        mTrackerHandle = null;
    }
    showTrackingInterruptedNotification();
    performStopSelf();
    return Service.START_STICKY;
}
```
When `performStopSelf()` triggers `TrackerService.onDestroy()`:
```java
// TrackerService.java lines 637-639
if (!mTrackingInterrupted) {
    endWorkout();
}
```
Because `mTrackingInterrupted` was marked `true`, **`endWorkout()` was completely skipped**!
Consequently:
- `createNewLap()` was not called.
- `summaryValues.put(WorkoutSummaries.FINISHED, 1)` was never written to SQLite.
- `TRACKING_FINISHED_INTENT` was never broadcast.
- The UI received no navigation intent and remained stuck or silently returned to the main screen.

---

## 3. Chesterton's Fence Archaeology

### 3.1 Origin & Purpose of FGS Types in ATT-2773
* **Commit**: `53370e78` (ATT-2773)
* **Rationale**: On Android 14+, foreground services accessing Bluetooth LE connected peripherals (heart rate monitors, power meters, speed/cadence sensors) require `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` and/or `FOREGROUND_SERVICE_TYPE_HEALTH` so the OS does not throttle or kill background BLE GATT operations.
* **Why did it break?**: The author combined `FOREGROUND_SERVICE_TYPE_LOCATION`, `CONNECTED_DEVICE`, and `HEALTH` unconditionally without checking whether the requisite runtime permissions were actually granted.

### 3.2 Invariants to Preserve
1. **BLE GATT Continuity**: Bluetooth LE peripherals must remain actively polled in the background during tracking via `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` (which only requires `BLUETOOTH_CONNECT`, already granted).
2. **Crash Resilience**: An uncaught exception during FGS promotion must not leave a recorded workout permanently corrupt in SQLite.
3. **Guaranteed Finalization**: Stopping a workout (via stop button, notification action, or system shutdown) must reliably set `FINISHED = 1` and dispatch `TRACKING_FINISHED_INTENT` unless the session was genuinely a zero-sample uninitialized abortion.

---

## 4. Scope Bounding

### In Scope
1. **Permission-Gated FGS Types**:
   - Only declare `FOREGROUND_SERVICE_TYPE_HEALTH` if the app actually holds `FOREGROUND_SERVICE_HEALTH` and `ACTIVITY_RECOGNITION` (or related runtime permission).
   - If not held, default to `FOREGROUND_SERVICE_TYPE_LOCATION | FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`.
2. **Defensive Finalization in `onDestroy()` / `endWorkout()`**:
   - Differentiate fatal startup initialization failure (where no workout table exists) from regular session termination.
   - If `mWorkoutID > 0`, ensure `endWorkout()` reliably marks `FINISHED = 1` and emits `TRACKING_FINISHED_INTENT` using structured `try/finally` blocks.
3. **Automated Unit Testing**:
   - Add unit test verifying that `TrackerService` does not include `FOREGROUND_SERVICE_TYPE_HEALTH` when health permissions are absent.
   - Add unit test asserting `WorkoutSummaries.FINISHED == 1` and intent dispatch upon workout completion.

### Out of Scope
- Modifying BLE scanning algorithms (already verified in ATT-2773).
- UI redesign of `EditWorkoutScreen`.

---

## 5. Proposed Architecture & Solution Design

```
[TrackerService.onStartCommand]
       │
       ├── Determine permitted FGS types:
       │     base = LOCATION
       │     if (hasBluetoothPermission) base |= CONNECTED_DEVICE
       │     if (hasHealthPermission)    base |= HEALTH
       │
       └── performStartForeground(ID, notification, base)
             │ (No SecurityException thrown!)
             ▼
[Athlete Taps Stop]
       │
       ▼
[TrackerService.onDestroy]
       │
       └── Guaranteed execution of endWorkout() via try/finally
             ├── saveLap()
             ├── update(WorkoutSummaries.FINISHED = 1)  <-- GUARANTEED
             └── sendBroadcast(TRACKING_FINISHED_INTENT) <-- GUARANTEED
                   │
                   ▼
       [MainActivityWithNavigation] opens EditWorkoutScreen!
```
