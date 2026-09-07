# Walkthrough: Reactivate Deletion of Old Workouts (ATT-296)

## 1. Overview
This change reactivates and modernizes the dormant bulk workout deletion feature ([ATT-296](https://rainerblind.atlassian.net/browse/ATT-296)):
1. **User-Configurable Retention Threshold**: Restored user ability to bulk-delete workouts older than $D$ days from the Workout List screen via the top-bar actions menu.
2. **Safe 365-Day Default**: Updated default retention period from 30 days to 365 days (`defaultDaysToKeep = 365`) to prevent unintended data loss for infrequent purgers.
3. **Critical Forensic Defect Resolution**: Fixed a silent SQLite data leak in `WorkoutDeletionHelper.deleteWorkout` where `fileBaseName` was previously resolved *after* deleting the summary row, which caused `getBaseFileName(workoutId)` to return `null` and permanently orphaned `workout_samples_<fileBaseName>` tables.
4. **Jetpack Compose UI & Dialog**: Implemented `DeleteOldWorkoutsDialog.kt` with numeric validation ($D \ge 0$), non-negative integer parsing, accessible labels, and integration into `WorkoutTabsScreen` and `WorkoutSummariesTabbedFragment`.

---

## 2. Key Changes

### Component 1: SQLite Cascade & Data Layer ([`REQ-DAT-010`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L245))
* **[WorkoutDeletionHelper.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java)**:
  * Reordered deletion steps in `deleteWorkout(long workoutId)`:
    1. Retrieve `fileBaseName = getBaseFileName(workoutId)` **before** deleting summary records.
    2. Drop sample tables via `mSamplesManager.deleteWorkout(fileBaseName)`.
    3. Remove export status via `mSummariesManager.deleteWorkoutExportStatus(workoutId)`.
    4. Remove summary row via `mSummariesManager.deleteWorkout(workoutId)`.
  * Added `@VisibleForTesting` constructor with dependency injection for `WorkoutSummariesDatabaseManager` and `WorkoutSamplesDatabaseManager`.
* **[WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)**:
  * Parameterized `getOldWorkouts(int days)` query using `TIME_START <= datetime('now', '-' || ? || ' days')` and `getColumnIndexOrThrow` for defensive index resolution.

### Component 2: Resource Configuration & Localization
* **[app/src/main/res/values/strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml#L59)**:
  * Updated `defaultDaysToKeep` integer string from `"30"` to `"365"`.
* Utilized existing, fully translated string resources across 9 locales:
  * `R.string.deleteOldWorkouts`
  * `R.string.deleteWorkoutsThatAreOlderThanDays`
  * `R.string.workout_periods__days` ("Days" / "Tage" / etc.)
  * `R.string.delete`
  * `R.string.Cancel`

### Component 3: Jetpack Compose Presentation Layer ([`REQ-UI-127`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L249))
* **[DeleteOldWorkoutsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/DeleteOldWorkoutsDialog.kt)**:
  * Created Compose dialog initializing input to `defaultDaysToKeep` (365).
  * Validates $D \ge 0$ with numeric keyboard filtering.
  * Disables Confirm ("Delete") button when input is invalid or blank.
* **[WorkoutListActions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutListActions.kt)**:
  * Added optional action callback `onDeleteOldWorkoutsClicked: (() -> Unit)? = null` rendering vector icon `ic_baseline_delete_sweep_24`.
* **[WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)**:
  * Hoisted dialog state `showDeleteOldWorkoutsDialog`.
  * Connected action item click to show dialog.
  * Connected dialog confirmation to `onDeleteOldWorkouts(days)`.
* **[WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)**:
  * Wired `onDeleteOldWorkouts` callback to `viewModel.executeDeleteOldWorkouts(days)`.

### Component 4: Automated Unit Tests ([`TST-DAT-004`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L279))
* **[WorkoutDeletionHelperTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelperTest.kt)**:
  * Added unit test verifying strict deletion order (fetching `fileBaseName` prior to summary deletion).
  * Tested multi-table cascade purging across summaries, sample tables, and export status.
  * Tested error resilience and null `fileBaseName` handling.

---

## 3. Verification Results

### Automated Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest"
```
