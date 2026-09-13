# Implementation Plan - ATT-635: Interrupted Workout Resumption & Unfinished Workout Recovery

## Problem Statement
During physical device testing on Android 16 (Pixel 10), when `TrackerService` was killed by the OS and stopped due to Android 14+ background launch restrictions, the app posted the "Aufzeichnung unterbrochen" (Tracking Interrupted) notification. However, when the user tapped the notification:
1. The app returned to `MainActivityWithNavigation`, but remained in the idle ready state (`Start tracking`).
2. Tapping "Start tracking" would start a new workout, resetting accumulators and abandoning the active session.
3. Tracking was never resumed because:
   - `showTrackingInterruptedNotification()` lacked an action/extra indicating that resumption was requested.
   - `MainActivityWithNavigation` lacked logic to resume tracking upon receiving the notification Intent.
   - Unfinished workout detection (`prevTrackingFinishedProperly()` / `FINISHED == 0`) had been lost during the Compose migration.
   - `MainActivityWithNavigation.chooseResume()` attempted to modify a removed legacy TextView (`R.id.tvStart`).

## Proposed Architecture & Changes

### 1. [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- In `showTrackingInterruptedNotification()`:
  - Add `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` to `resumeIntent`.
  - Add `SELECTED_FRAGMENT = START_OR_TRACKING` to ensure navigation targets the tracking screen.
  - Configure `PendingIntent` flags appropriately.

### 2. [MODIFY] [MainActivityWithNavigation.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.java)
- Define `public static final String EXTRA_RESUME_INTERRUPTED_WORKOUT = "com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT";`
- In `handleIntent(Intent intent)`:
  - Check `intent.getBooleanExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT, false)`.
  - If `true`:
    - Remove extra from intent (`intent.removeExtra(EXTRA_RESUME_INTERRUPTED_WORKOUT)`).
    - Cancel interrupted notification: `NotificationManagerCompat.from(this).cancel(TrackerService.TRACKING_INTERRUPTED_NOTIFICATION_ID);`.
    - Ensure tracking drawer item is selected (`mSelectedFragmentId = R.id.drawer_start_tracking`).
    - Call `TrainingApplication.setResumeFromCrash(true);`.
    - Trigger immediate tracking resumption:
      `sendBroadcast(new Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(getPackageName()));`.
- In `onResume()`:
  - If `!TrainingApplication.isTracking()` and `WorkoutSummariesDatabaseManager.getInstance(this).hasUnfinishedWorkout()`:
    - Check if `StartOrResumeDialog` is already showing. If not, invoke `showStartOrResumeDialog()`.
- Update `chooseResume()`:
  - Cancel interrupted notification if active.
  - Call `TrainingApplication.setResumeFromCrash(true);`.
  - Trigger tracking resumption: `sendBroadcast(new Intent(TrainingApplication.REQUEST_START_TRACKING).setPackage(getPackageName()));`.
- Update `chooseStart()`:
  - Cancel interrupted notification if active.
  - Mark unfinished workout as finished in DB via `WorkoutSummariesDatabaseManager.getInstance(this).discardOrFinishUnfinishedWorkout()`.
  - Call `TrainingApplication.setResumeFromCrash(false);`.

### 3. [MODIFY] [TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)
- In `startTracking()`:
  - Consume and reset `cResumeFromCrash`:
    ```java
    if (cResumeFromCrash) {
        intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.RESUME_BY_USER.name());
        cResumeFromCrash = false;
    } else {
        intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name());
    }
    ```

### 4. [MODIFY] [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- Add `public boolean hasUnfinishedWorkout()`:
  - Query most recent workout in `WorkoutSummaries.TABLE` (`ORDER BY _id DESC LIMIT 1`).
  - Return `true` if `FINISHED == 0`, `false` otherwise.
- Add `public void discardOrFinishUnfinishedWorkout()`:
  - Update `WorkoutSummaries.TABLE` setting `FINISHED = 1` where `FINISHED = 0`.

### 5. [NEW] [WorkoutResumptionTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/tracker/WorkoutResumptionTest.kt)
- Create unit tests verifying:
  1. `showTrackingInterruptedNotification` configures Intent with `EXTRA_RESUME_INTERRUPTED_WORKOUT`.
  2. `hasUnfinishedWorkout()` accurately identifies unfinished vs finished workouts in SQLite.
  3. `discardOrFinishUnfinishedWorkout()` updates unfinished records to finished.
  4. `TrainingApplication.startTracking()` correctly passes `RESUME_BY_USER` when `cResumeFromCrash` is true and resets the flag.

---

## Traceability
| Requirement ID | Summary | Affected Components | Test Specification | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-STB-003** | **Interrupted Workout Resumption & Unfinished Workout Recovery.** Resuming interrupted workouts from notification in foreground and detecting unfinished workouts on app launch. | `TrackerService.java`, `MainActivityWithNavigation.java`, `TrainingApplication.java`, `WorkoutSummariesDatabaseManager.java` | **TST-STB-003** | In Bearbeitung |

---

## Verification Plan

### Automated Tests
1. Run dedicated resumption unit tests:
   ```bash
   ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.tracker.WorkoutResumptionTest
   ```
2. Run full regression test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### On-Device Manual Verification (Pixel 10, Android 16)
1. Install debug APK on Pixel 10:
   `adb -s 66020DLCR002FL install -r app/build/outputs/apk/debug/app-debug.apk`
2. Launch app and start a workout. Record for ~10 seconds.
3. Simulate process kill via adb:
   `adb -s 66020DLCR002FL shell "run-as com.atrainingtracker.debug kill -9 \$(pidof com.atrainingtracker.debug)"`
4. Confirm "Aufzeichnung unterbrochen" notification appears.
5. Tap notification -> confirm app opens in foreground and automatically resumes active workout (time, distance, metrics intact).
6. Kill app again, clear notification, launch app from app launcher -> confirm `StartOrResumeDialog` appears prompting to Resume or Start New.

---

## System Invariants
- **Service START_STICKY Integrity**: `TrackerService` retains `START_STICKY`.
- **Foreground Location Compliance**: Tracking resumption occurs in the foreground with user context, fully compliant with Android 14+ while-in-use location rules.
- **Data Preservation**: Zero data loss for interrupted workout sessions.
- **Normal Workflow Unaffected**: Starting a fresh workout when no interrupted workout exists continues to start cleanly.
