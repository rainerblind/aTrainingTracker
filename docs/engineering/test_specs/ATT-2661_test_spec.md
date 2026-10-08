# Stage 2: Requirement & Test Specification - ATT-2661: TrainingApplication.startTracking FGS Background Crash

**Ticket**: [ATT-2661](https://atrainingtracker.atlassian.net/browse/ATT-2661)  
**Sub-task**: [ATT-2663](https://atrainingtracker.atlassian.net/browse/ATT-2663) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement ID**: `REQ-STB-014`  
**Test Spec ID**: `TST-STB-014`  
**Branch**: `feature/ATT-2661`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-STB-014)

### 1.1 Problem Statement & Rationale
In production release `4.9.38.1 (263)`, `TrainingApplication.startTracking()` crashed with `IllegalStateException: Not allowed to start service ...: app is in background` when processing the `REQUEST_START_TRACKING` broadcast while the process was in background state (`RCVR idle`). `TrainingApplication.java` invoked the legacy pre-Android 8.0 `startService(intent)` without using `ContextCompat.startForegroundService()` and without localized exception shielding.

### 1.2 Functional & Architectural Requirements
The system SHALL ensure resilient workout tracking service launch and crash immunity across all Android API versions when `startTracking()` is invoked (ATT-2661):

1. *Foreground Service Startup Delegation*:
   - In `TrainingApplication.startTracking()`, the system SHALL launch `TrackerService` using `ContextCompat.startForegroundService(this, intent)` rather than `startService(intent)`, signaling to the Android platform that the service will promote itself to the foreground via `startForeground()`.

2. *Defensive Launch Exception Shielding*:
   - The invocation of `ContextCompat.startForegroundService(this, intent)` SHALL be encapsulated in a `try ... catch (IllegalStateException | SecurityException)` block.
   - Any runtime restriction thrown by the Android framework (such as `IllegalStateException` due to background start restrictions or `ForegroundServiceStartNotAllowedException` on Android 12+ / API 31+, or `SecurityException` due to permission state) SHALL be caught and logged cleanly without terminating the application process.

3. *Failure State Recovery & Athlete Notification*:
   - If starting the service throws an `IllegalStateException` or `SecurityException`:
     - The system SHALL reset `cTrackingMode = TrackingMode.READY`.
     - The system SHALL notify registered tracking state observers via `notifyTrackingStateChanged()`.
     - The system SHALL post `TrackerService.showTrackingInterruptedNotification(this)` to alert the athlete that tracking could not start immediately and provide a high-priority notification to tap and resume once the app is explicitly brought to the foreground.

4. *System Invariants*:
   - `START_STICKY` service lifecycle (`REQ-STB-002`) MUST remain unaltered.
   - `TrackerService.StartType` contracts (`START_NORMAL`, `RESUME_BY_USER`) MUST NOT be changed.
   - Paired sensor discovery, accumulator reset, and Pebble watchapp coordination MUST NOT be degraded.
   - Zero unit test regressions across the full clean-room suite.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Foreground Service Launch Delegation)**:
  * *Given* an application in the foreground initiating workout tracking,
  * *When* `startTracking()` executes,
  * *Then* it SHALL invoke `ContextCompat.startForegroundService()` with the configured `TrackerService` intent and transition `cTrackingMode` to `TrackingMode.TRACKING`.

* **Criterion 2 (Background Launch Restriction Immunity)**:
  * *Given* an application process evaluated as in the background (`RCVR idle`) where Android rejects service startup with `IllegalStateException`,
  * *When* `startTracking()` executes,
  * *Then* it SHALL catch the exception without crashing, reset `cTrackingMode` to `READY`, notify observers, and post `TrackerService.showTrackingInterruptedNotification()`.

* **Criterion 3 (SecurityException Permission Shielding)**:
  * *Given* an environment where launching the foreground service throws `SecurityException`,
  * *When* `startTracking()` executes,
  * *Then* it SHALL catch the exception, log diagnostics, and prevent process termination.

---

## 2. Test Specification (TST-STB-014)

### Test Case 1: Normal Foreground Service Launch (`TST-STB-014.1`)
* **Scope**: Unit Test (`TrainingApplicationTrackingTest.kt`)
* **Preconditions**: Mock Context and service start dispatcher.
* **Action**: Invoke `startTracking()`.
* **Expected Result**: Verifies that `ContextCompat.startForegroundService` is called with target component `TrackerService` and tracking mode transitions to `TRACKING`.

### Test Case 2: Background Restriction Resilience (`TST-STB-014.2`)
* **Scope**: Unit Test (`TrainingApplicationTrackingTest.kt`)
* **Preconditions**: Simulate `ContextCompat.startForegroundService` throwing `IllegalStateException("Not allowed to start service: app is in background")`.
* **Action**: Invoke `startTracking()`.
* **Expected Result**: Exception is caught cleanly, process does not crash, `cTrackingMode` resets to `READY`, and `showTrackingInterruptedNotification` is invoked.

### Test Case 3: SecurityException Shielding (`TST-STB-014.3`)
* **Scope**: Unit Test (`TrainingApplicationTrackingTest.kt`)
* **Preconditions**: Simulate `ContextCompat.startForegroundService` throwing `SecurityException("Missing FGS permission")`.
* **Action**: Invoke `startTracking()`.
* **Expected Result**: Exception is caught cleanly, logged, and tracking mode resets to `READY`.

### Test Case 4: Full Clean-Room Regression Test Suite (`TST-STB-014.4`)
* **Scope**: Clean-room integration regression
* **Action**: Execute `./gradlew testDebugUnitTest`.
* **Expected Result**: 100% of unit tests pass with zero failures.

---

## 3. Traceability Matrix

| Requirement | Description | Test Case | Target Artifacts | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-014.1` | `startForegroundService` delegation | `TST-STB-014.1` | `TrainingApplication.java` | `Specified` |
| `REQ-STB-014.2` | `IllegalStateException` shielding | `TST-STB-014.2` | `TrainingApplication.java` | `Specified` |
| `REQ-STB-014.3` | `SecurityException` shielding | `TST-STB-014.3` | `TrainingApplication.java` | `Specified` |
| `REQ-STB-014.4` | Tracking interrupted notification fallback | `TST-STB-014.2` | `TrainingApplication.java`, `TrackerService.java` | `Specified` |
| `REQ-PRO-001` | Clean-room regression suite | `TST-STB-014.4` | Full test suite | `Specified` |
