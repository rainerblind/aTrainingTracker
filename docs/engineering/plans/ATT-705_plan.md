# Implementation Plan: Bulk Workout Deletion Progress Feedback & Notification (ATT-705)

## Overview
During testing of bulk workout deletion ([ATT-296](https://rainerblind.atlassian.net/browse/ATT-296)), it was observed that after confirming retention days in `DeleteOldWorkoutsDialog`, the dialog dismisses immediately while lengthy background tasks (iterative multi-table SQLite cascade deletions, analytical period cache resynchronization, and cluster integrity verification) execute. Without any observable visual feedback or progress notification, the user has no indication whether the operation is actively progressing or if the application has hung.

This plan addresses [ATT-705](https://rainerblind.atlassian.net/browse/ATT-705) by implementing a dual progress notification architecture ([`REQ-UI-128`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L252), [`TST-UI-081`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L282)):
1. **In-App Compose Modal Dialog (`WorkoutDeletionProgressDialog`)**: Blocks UI interactions during active writes, displaying session-by-session progress (Phase 1: deletion with linear progress indicator and numeric counter $X / Y$) and cache resynchronization (Phase 2: circular spinner with wait status).
2. **Foreground / System Notification (`NotificationCompat.Builder`)**: Posts an ongoing progress notification on background channel `NOTIFICATION_CHANNEL__EXPORT` with category `CATEGORY_PROGRESS` and `setOngoing(true)` so users can monitor progress even if the app is backgrounded.
3. **State Machine & Helper Refactoring**: Extends `DeletionProgress` with granular state definitions (`Deleting`, `Resyncing`, `Idle`) and wires progress updates from `WorkoutDeletionHelper` through `WorkoutRepository` and `WorkoutSummariesViewModel` to the Compose UI.

---

## User Review Required
> [!NOTE]
> All user-facing strings utilized by the progress dialog (`@string/deleteOldWorkouts`, `@string/deleting_workout`, `@string/deleting_please_wait`, `@string/please_wait`) are already fully localized across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). No new string resource additions or translations are required.

---

## Proposed Changes

### Component 1: Data Model & Repository State
#### [MODIFY] [DeletionProgress.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/DeletionProgress.kt)
* Expand sealed hierarchy:
  * Retain `Idle` and `InProgress(workoutName, workoutId)` for backwards compatibility.
  * Add `data class Deleting(val current: Int, val total: Int, val workoutName: String, val workoutId: Long) : DeletionProgress()`.
  * Add `object Resyncing : DeletionProgress()`.

#### [MODIFY] [WorkoutDeletionHelper.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java)
* Introduce `public interface DeletionProgressCallback { void onProgress(int current, int total, long workoutId); }`.
* Add overloaded `deleteOldWorkouts(int daysToKeep, DeletionProgressCallback progressCallback)` iterating through `oldWorkoutIds` with 1-based index and total count.
* Maintain existing `deleteOldWorkouts(int daysToKeep, Function1<Long, Unit> progressCallback)` delegating to the callback for full backwards compatibility with existing tests.

#### [NEW] [WorkoutDeletionNotificationManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutDeletionNotificationManager.kt)
* Dedicated notification manager responsible for:
  * `showProgressNotification(current: Int, total: Int, workoutName: String)`: ongoing progress notification on `NOTIFICATION_CHANNEL__EXPORT`.
  * `showResyncNotification()`: ongoing indeterminate notification during period/cluster resynchronization.
  * `cancelNotification()`: cleans up notification upon completion.
  * Defensive runtime permission check for `POST_NOTIFICATIONS` on Android 13+.

#### [MODIFY] [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)
* In `deleteOldWorkouts(daysToKeep: Int)`:
  * Connect `WorkoutDeletionNotificationManager`.
  * Pass `DeletionProgressCallback` to `deletionHelper.deleteOldWorkouts`.
  * Post `DeletionProgress.Deleting(current, total, workoutName, workoutId)` and update notification on each session.
  * Upon deletion completion, post `DeletionProgress.Resyncing` and update notification during periods/clusters resynchronization.
  * In `finally` block, guarantee posting `DeletionProgress.Idle` and cancelling notification.

---

### Component 2: Presentation & Jetpack Compose UI
#### [NEW] [WorkoutDeletionProgressDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutDeletionProgressDialog.kt)
* Modal Compose dialog with non-dismissible properties (`dismissOnBackPress = false`, `dismissOnClickOutside = false`).
* Renders:
  * Title: `@string/deleteOldWorkouts`.
  * When `Deleting`: `@string/deleting_workout` with workout title, `LinearProgressIndicator`, and counter `$current / $total`.
  * When `Resyncing`: `CircularProgressIndicator` with `@string/please_wait`.

#### [MODIFY] [WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)
* Accept `deletionProgress: DeletionProgress = DeletionProgress.Idle`.
* Host `WorkoutDeletionProgressDialog(progress = deletionProgress)` inside the screen container.

#### [MODIFY] [WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)
* Observe `viewModel.deletionProgress.observeAsState(DeletionProgress.Idle)`.
* Forward `deletionProgress` into `WorkoutTabsScreen`.

---

### Component 3: Automated Unit Testing
#### [NEW] [WorkoutDeletionProgressTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutDeletionProgressTest.kt)
* Unit tests verifying:
  1. `WorkoutDeletionHelper` callback invocations with accurate `(current, total, workoutId)` sequence.
  2. `WorkoutRepository` sequential progress emissions (`Idle` -> `Deleting(1, 2)` -> `Deleting(2, 2)` -> `Resyncing` -> `Idle`).
  3. Failure safety: verify `_deletionProgress` resets to `Idle` in `finally` even if deletion helper or resync fails.
  4. `WorkoutDeletionNotificationManager` invocation and cancellation logic.

---

## Verification Plan

### Automated Tests
1. Run target unit tests:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutDeletionProgressTest"
   ```
2. Run full clean-room regression test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### Manual Verification
1. Open the Workout List screen.
2. Tap the delete old workouts action button (`ic_baseline_delete_sweep_24`).
3. Set retention days to trigger deletion of older workouts.
4. Verify that the modal progress dialog appears immediately, displays the session name being purged, increments the progress bar and counter, transitions to the resync spinner, and dismisses automatically when complete.
5. Verify that during deletion, the Android notification shade displays the ongoing deletion notification and dismisses upon completion.
