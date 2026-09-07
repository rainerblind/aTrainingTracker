# Implementation Plan - ATT-296: Reactivate Deleting Old Workouts

## 1. Overview & Problem Statement
The bulk workout deletion capability allows athletes to reclaim device storage by deleting sessions older than a configurable retention threshold ($D \ge 0$ days). While the backend logic in `WorkoutDeletionHelper` and `WorkoutRepository` remained partially present, the feature was deactivated during the Jetpack Compose modernization of the Aftermath screen (`WorkoutSummariesTabbedFragment`, `WorkoutTabsScreen`, `WorkoutListActions`).
Furthermore, forensic analysis revealed a critical data leak in `WorkoutDeletionHelper.deleteWorkout` where `fileBaseName` was queried *after* deleting the workout summary from SQLite, causing high-frequency sample tables (`workout_samples_<fileBaseName>`) to be permanently orphaned.
Per user guidance, the default retention threshold is increased from 30 to **365 days**.

---

## 2. Traceability Matrix

| Requirement ID | Component / Layer | Implementation File(s) | Verification Test ID |
| :--- | :--- | :--- | :--- |
| **REQ-DAT-010** | Database & Deletion Engine | `WorkoutDeletionHelper.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt` | **TST-DAT-004** |
| **REQ-UI-127** | Jetpack Compose UI & ViewModel | `DeleteOldWorkoutsDialog.kt`, `WorkoutListActions.kt`, `WorkoutTabsScreen.kt`, `WorkoutSummariesTabbedFragment.kt`, `strings.xml` | **TST-UI-080** |

---

## 3. Impact Analysis & System Invariants

### 3.1. Blast Radius & Call-Site Audit
* `WorkoutDeletionHelper.deleteWorkout(long workoutId)`:
  * Callers: `WorkoutRepository.deleteWorkout`, `WorkoutDeletionHelper.deleteOldWorkouts`.
  * Fix sequencing: Query `fileBaseName` *before* deleting summary row so that `WorkoutSamplesDatabaseManager.deleteWorkout(fileBaseName)` drops the SQLite sample table properly.
* `WorkoutSummariesDatabaseManager.getOldWorkouts(int days)`:
  * Harden SQL query with parameterized binding: `TIME_START <= datetime('now', '-' || ? || ' days')`.
* `WorkoutListActions.kt`:
  * Callers: `WorkoutTabsScreen.kt`, `WorkoutSummariesListFragment.kt`, `WorkoutClustersFragment.kt`.
  * Make `onDeleteOldWorkoutsClicked: (() -> Unit)? = null` optional with default `null` so other screens (`WorkoutClustersFragment`) are not affected.
* `strings.xml`:
  * Update `defaultDaysToKeep` from `"30"` to `"365"`.

### 3.2. Preserved Invariants
* **Non-Destructive for Recent Workouts**: All sessions recorded within the retention threshold ($> \text{now} - D\text{ days}$) MUST NOT be modified or deleted.
* **Surgical Cascade Integrity**: For each deleted session, `PeriodsRepository.onWorkoutDeleted` and `WorkoutClusterEngine.onWorkoutDeleted` MUST be invoked.
* **Header Height Invariant**: `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` (32dp) and `COMPACT_HEADER_CONTENT_HEIGHT` (80dp) MUST be preserved.
* **Localization Parity**: 100% translation coverage across all 9 supported locales.

---

## 4. Proposed Changes

### Component 1: Resource Configuration
#### [MODIFY] [strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml)
* Update `defaultDaysToKeep` string resource value from `30` to `365` (`translatable="false"`).

---

### Component 2: Core Deletion Engine & Database Layer
#### [MODIFY] [WorkoutDeletionHelper.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutDeletionHelper.java)
* Re-order deletion operations in `deleteWorkout(long workoutId)`:
  ```java
  String fileBaseName = mSummariesManager.getBaseFileName(workoutId);
  mSummariesManager.deleteWorkout(workoutId);
  mLapsManager.deleteWorkout(workoutId);
  if (fileBaseName != null) {
      mSamplesManager.deleteWorkout(fileBaseName);
      mExportStatusRepo.deleteWorkout(fileBaseName);
  }
  ```
* Ensure `deleteOldWorkouts(int daysToKeep, Function1<Long, Unit> progressCallback)` safely iterates over matching old workout IDs.

#### [MODIFY] [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
* Refine `getOldWorkouts(int days)` to use parameterized query `WorkoutSummaries.TIME_START + " <= datetime('now', '-' || ? || ' days')"` with `selectionArgs = new String[]{String.valueOf(days)}` and `getColumnIndexOrThrow(WorkoutSummaries.C_ID)`.

---

### Component 3: Compose UI & Presentation Layer
#### [NEW] [DeleteOldWorkoutsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/DeleteOldWorkoutsDialog.kt)
* Create Compose dialog with:
  * Title: `stringResource(R.string.deleteOldWorkouts)`
  * Prompt: `stringResource(R.string.deleteWorkoutsThatAreOlderThanDays)`
  * `OutlinedTextField` pre-filled with `stringResource(R.string.defaultDaysToKeep)` ("365")
  * Validation enforcing valid non-negative integer ($D \ge 0$)
  * Dismiss button: "Cancel" (`stringResource(R.string.Cancel)`)
  * Confirm button: "Delete" (`stringResource(R.string.delete)`), enabled only if input is valid

#### [MODIFY] [WorkoutListActions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutListActions.kt)
* Add parameter `onDeleteOldWorkoutsClicked: (() -> Unit)? = null`.
* If provided, render an `IconButton` with `painterResource(R.drawable.ic_baseline_delete_sweep_24)`, localized content description `stringResource(R.string.deleteOldWorkouts)`, and color `tint`.

#### [MODIFY] [WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)
* Add parameter `onDeleteOldWorkouts: (Int) -> Unit`.
* Hoist state `var showDeleteOldWorkoutsDialog by remember { mutableStateOf(false) }`.
* Connect `WorkoutListActions(onDeleteOldWorkoutsClicked = { showDeleteOldWorkoutsDialog = true })`.
* Render `DeleteOldWorkoutsDialog` when state is `true`.

#### [MODIFY] [WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)
* Pass `onDeleteOldWorkouts = { daysToKeep -> viewModel.executeDeleteOldWorkouts(daysToKeep) }` to `WorkoutTabsScreen`.

---

## 5. Verification Plan

### 5.1. Automated Unit & Integration Tests
1. **`WorkoutDeletionHelperTest.kt`** (`TST-DAT-004`):
   * Test bulk deletion with threshold 365 days: sessions older than 365 days are deleted, newer sessions are preserved.
   * Test that `workout_samples_<fileBaseName>` tables are dropped and not orphaned.
   * Test that progress callback is invoked for each deleted workout ID.
2. **`DeleteOldWorkoutsDialogTest.kt`** (`TST-UI-080`):
   * Test default value evaluates to 365.
   * Test input validation (empty string, letters, negative numbers disable confirm button).
   * Test valid submission triggers confirm callback with integer value.
3. **Clean-Room Regression**:
   * Run full test suite: `./gradlew testDebugUnitTest`.

### 5.2. Manual Verification
* Deploy to test device/emulator:
  * Open "Einheiten" / Workouts screen.
  * Verify delete sweep icon button appears in the header title row.
  * Tap icon: verify dialog displays headline, prompt, and default "365".
  * Verify canceling does nothing.
  * Verify entering valid number and confirming dispatches bulk deletion.
