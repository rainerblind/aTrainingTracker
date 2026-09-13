# Implementation Plan - ATT-622: Resilient TrackerService Foreground Service Lifecycle & Crash Protection

## Problem Statement
When `TrackerService` is killed by the operating system (e.g. under low-memory pressure or battery optimization), Android attempts to recreate the service in the background and delivers a `null` Intent (`intent == null`) to `onStartCommand` because `onStartCommand` returns `Service.START_STICKY`.

On Android 14+ (API 34+ / targetSDK >= 34), promoting a service to a location foreground service (`FOREGROUND_SERVICE_TYPE_LOCATION`) from the background is strictly forbidden unless the app holds `ACCESS_BACKGROUND_LOCATION` ("Allow all the time") or is in an eligible foreground exemption state. When users have only granted foreground location ("While using the app"), calling `startForeground(..., notification, fgsType)` throws an unhandled `SecurityException`, crashing the process and causing repeat crash loops.

## User Mandate
> **Do not switch to `START_NOT_STICKY`.**
`Service.START_STICKY` must be retained to preserve workout tracking resilience so that active workouts can be automatically revived by the OS when background location permissions are present.

## Proposed Architecture & Changes

### 1. [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- **Retain `Service.START_STICKY`**: Keep `return Service.START_STICKY;` at the conclusion of `onStartCommand(...)`.
- **Precondition Guard on Null Intent (`intent == null`)**:
  - When `intent == null` (indicating system restart in background):
    - Check `hasBackgroundLocationPermission()`.
    - If `hasBackgroundLocationPermission()` is **true**: Proceed normally to call `startForeground` and resume tracking in the background.
    - If `hasBackgroundLocationPermission()` is **false**: Android 14+ will reject the location FGS promotion. Instead of calling `startForeground` and crashing, safely preserve the workout session, post an informative user notification (*"Training paused – tap to resume"*), invoke `stopSelf()`, and exit cleanly.
- **Robust Exception Shielding Around `startForeground(...)`**:
  - Encapsulate `startForeground(...)` invocations within a `try ... catch (SecurityException | IllegalStateException e)` block.
  - If a `SecurityException` or `ForegroundServiceStartNotAllowedException` is thrown by the OS runtime:
    - Log diagnostic details with `Log.e(TAG, ...)`.
    - Post the user notification to inform the user.
    - Call `stopSelf()` so the service does not remain in an illegal unpromoted state.
    - Guarantee zero unhandled exceptions escaping `onStartCommand`.
- **User Notification Helper**:
  - Implement `showTrackingInterruptedNotification()` to notify the user via `NotificationManagerCompat` with a PendingIntent opening `MainActivityWithNavigation`.

### 2. [NEW] [TrackerServiceResilienceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResilienceTest.kt)
- Create unit test verifying:
  1. `START_STICKY` contract is preserved.
  2. Background restart with missing background location permission stops safely without crashing.
  3. `SecurityException` or `ForegroundServiceStartNotAllowedException` during `startForeground` is caught and handled safely.

---

## Traceability
| Requirement ID | Summary | Affected Components | Test Specification | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-STB-002** | **Resilient TrackerService Foreground Service Lifecycle & Crash Protection.** Retain `START_STICKY`, guard `null` intent background restarts when lacking `ACCESS_BACKGROUND_LOCATION`, and shield `startForeground` against `SecurityException`. | `TrackerService.java` | **TST-STB-002** | Current Plan |

---

## Verification Plan

### Automated Tests
1. Run dedicated resilience tests:
   ```bash
   ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.tracker.TrackerServiceResilienceTest
   ```
2. Run full regression test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### System Invariants
- **Tracking Resilience**: `START_STICKY` is retained. Users with background location permissions experience automated session restarts.
- **Active Tracking**: User-initiated workouts in the foreground maintain location foreground service notifications.
- **Process Safety**: Zero crashes on background service recreation.
