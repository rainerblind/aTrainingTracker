# Walkthrough - ATT-622: TrackerService Foreground Service Resilience & START_STICKY Preservation

## Incident Summary
- **Incident Key**: Issue `2ef48d4055425dd6f58cf1e7c3361eab`
- **Exception**: `java.lang.SecurityException: Starting FGS with type location callerApp=ProcessRecord{...} targetSDK=35 requires permissions: all of the permissions all of the permissions: [] and any of the permissions: [android.permission.ACCESS_FINE_LOCATION, android.permission.ACCESS_COARSE_LOCATION] and any of the permissions: [android.permission.ACCESS_BACKGROUND_LOCATION]`
- **Location**: `TrackerService.onStartCommand` during system-initiated background service recreation on Android 14+ (API 34+) devices.
- **Requirement**: `REQ-STB-002`
- **Test Specification**: `TST-STB-002`

---

## Key Changes

### 1. `TrackerService.java`
- **START_STICKY Retained**: `Service.START_STICKY` is strictly preserved as the return value of `onStartCommand` to maintain workout tracking resilience when Android restarts the service after process termination.
- **Background Recreation Precondition Guard**: When `onStartCommand` receives `intent == null` and `!hasBackgroundLocationPermission()`:
  - Detects that Android OS recreated the service in the background while the app only has foreground location permissions.
  - Skips location FGS promotion, sets `mTrackingInterrupted = true`, shows a user notification, calls `performStopSelf()`, and returns `START_STICKY`.
- **Exception Shielding**: Wrapped all `startForeground` calls in `try ... catch (SecurityException | IllegalStateException e)`.
  - Gracefully catches platform background launch restrictions, cancels tracking handles, notifies the user, and calls `performStopSelf()`.
- **User Notification Banner**: Implemented `showTrackingInterruptedNotification()`:
  - Posts high-priority notification to `NOTIFICATION_CHANNEL__TRACKING_2` using `R.drawable.logo` and pending intent to `MainActivityWithNavigation`.
  - Informs the user: *"Tracking Interrupted – Workout tracking was paused by Android. Tap to resume."*
- **Workout Data Integrity**:
  - Guarded `onDestroy()`: If `mTrackingInterrupted == true`, `endWorkout()` is NOT called. The workout is left active/unfinalized in the database so the user can resume tracking upon opening the app.
  - Null checks added for `mBanalService` inside `endWorkout()` to prevent secondary `NullPointerException`s if the service terminates before BANALService binds.
- **Testability Architecture**: Protected methods `performStopSelf()`, `performStartForeground()`, `hasBackgroundLocationPermission()`, `hasLocationPermission()`, and `notifyTrackingStarted()` allow deterministic JVM unit testing.

### 2. Localization (`strings.xml` across all 9 locales)
Added localized notification strings for:
- `tracking_interrupted_notification_title`
- `tracking_interrupted_notification_text`
Across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, and `values-pt`.

### 3. `TrainingApplication.java`
- Promoted `NOTIFICATION_CHANNEL__TRACKING_2` from `protected` to `public static final String` so `TrackerService` can reference it cleanly.

### 4. `TrackerServiceResilienceTest.kt`
Created comprehensive unit test suite:
- `testRecreationWithoutBackgroundLocationStopsSelfAndReturnsSticky`: Verifies `null` intent restart without background location stops service, posts notification, and returns `START_STICKY`.
- `testSecurityExceptionDuringStartForegroundCaughtGracefully`: Verifies `SecurityException` during FGS promotion is caught, logs error, notifies user, stops service, and returns `START_STICKY`.
- `testIllegalStateExceptionDuringStartForegroundCaughtGracefully`: Verifies `IllegalStateException` during FGS promotion is handled cleanly.
- `testOnDestroySkipsEndWorkoutWhenTrackingInterrupted`: Verifies unfinalized workout data is preserved when tracking is interrupted.
- `testOnDestroyCallsEndWorkoutWhenTrackingNotInterrupted`: Verifies normal stops properly finalize and export workouts.

---

## Verification Results

### Automated Unit Tests
Executed:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResilienceTest"
```
**Result**: BUILD SUCCESSFUL (5 passed, 0 failed).

Executed full test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: BUILD SUCCESSFUL (32 tasks, 0 failed, zero regressions across entire repository).

### Static Audit
- Verified all 9 locale string files for format string safety and positional specifiers.
- Verified zero instances of invalid `%` specifiers.

---

## Invariant Safety
- **Invariant 1 (START_STICKY Preservation)**: `START_STICKY` is unconditionally returned from `onStartCommand`.
- **Invariant 2 (Workout Data Preservation)**: Interrupted tracking never prematurely marks workouts as finished or triggers partial exports.
- **Invariant 3 (Foreground Tracking Intact)**: Normal workout starts with active UI or background location permissions continue to promote to location FGS without modification.
