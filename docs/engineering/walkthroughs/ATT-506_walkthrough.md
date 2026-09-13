# Walkthrough: Go to Edit Workout from Workout Pop-up (ATT-506)

* **Parent Ticket**: [ATT-506](https://rainerblind.atlassian.net/browse/ATT-506) (*[Feature] Go to edit workout from workout pop-up*)
* **Sub-Task**: [ATT-853](https://rainerblind.atlassian.net/browse/ATT-853) (*[Implementation] Go to edit workout from workout pop-up*)
* **Target Version**: `V4.9.36`
* **Requirement**: `REQ-SET-070` (*Direct Navigation to Workout Editor from Workout Detail Popup & Unified Action Button Layout*)
* **Test Specification**: `TST-SET-059` (*Workout Detail Popup Direct Edit Navigation and Layout Consistency*)
* **Branch**: `feature/ATT-506`

---

## 1. Overview & Architecture

When analyzing workouts via peek popups in analytical map screens (Period Map and Favorite Tracks / Cluster Heatmap), users frequently need to edit workout metadata without exiting to the main list.
This feature introduces direct editing directly from the peek bottom sheet header, provides a unified action button layout, and guarantees smooth return navigation.

---

## 2. Summary of Changes

### 2.1 Action Button Ordering in Lieblingsstrecken (`WorkoutClusterHeatmapScreen.kt`)
* In `WorkoutClusterSummaryHeader`, reordered the action buttons in the top-end action row:
  * `Icons.Default.EditLocationAlt` (`cluster_edit_fingerprint_content_desc`) is placed on the **left**.
  * `R.drawable.ic_table_edit` (`edit_workout_name`) is placed on the **very right**.
* Added `onEditWorkout: ((Long) -> Unit)? = null` parameter to `WorkoutClusterHeatmapScreen` and forwarded it to `TrackOnMapScreen` inside the `BottomSheetScaffold` peek content.

### 2.2 Workout Popup Header Action & Click Area (`TrackOnMapScreen.kt` & `WorkoutHeader.kt`)
* In [`TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt):
  * Added `onEditWorkout: ((Long) -> Unit)? = null` parameter.
  * In `WorkoutHeader`, rendered caller-provided `headerActions()` on the left (e.g. `SwapHoriz`), followed by an `IconButton` (`ic_table_edit`, `32.dp`, primary tint) on the **very right** whenever `onEditWorkout != null`.
  * Wired `onClicked = onEditWorkout?.let { edit -> { edit(workoutData.id) } }`.
* In [`WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt):
  * Made `onClicked: (() -> Unit)? = null` optional.
  * Supported clickability on `Surface` when `menuEnabled == false` if `onClicked != null`.
  * Increased top row trailing spacer to `72.dp` to prevent workout name text from colliding with multiple trailing action icons.

### 2.3 Host Fragment Wiring & Navigation Flow
* [`WorkoutClustersFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt):
  * Forwarded `onEditWorkout = { id -> editedWorkoutId = id }` to `WorkoutClusterHeatmapScreen` and to `TrackOnMapScreen` in `inspectedWorkout != null`.
  * On return from `EditWorkoutScreen`, cleared `editedWorkoutId` and refreshed peek selection via `viewModel.selectWorkoutForPeek(id)`.
* [`PeriodsFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsFragment.kt) & [`PeriodMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt):
  * Added `editedWorkoutId` state in `PeriodsFragment`.
  * Displayed `EditWorkoutScreen` when `editedWorkoutId != null`, reloading periods on return via `viewModel.loadPeriods()` and refreshing peek selection.
  * Forwarded `onEditWorkout` via `PeriodMapScreen` to `TrackOnMapScreen`.
* [`WorkoutSummariesListFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt) & [`WorkoutSummariesTabbedFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt):
  * Forwarded `onEditWorkout = { id -> selectedWorkoutIdForEdit = id }` to `TrackOnMapScreen` for consistent edit experience in detail views.

### 2.4 Automated Tests
* [`WorkoutHeaderDataTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderDataTest.kt):
  * `onEditWorkoutCallback_receivesCorrectWorkoutId`: Verifies action callback contract with workout ID.

---

## 3. Verification & Test Evidence

### 3.1 Automated Tests
Executed full debug unit test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL in 2m 51s` (32 actionable tasks, all unit tests passed).
Dedicated test suite:
```bash
./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataTest
```
**Result**: `BUILD SUCCESSFUL in 6s` (all tests passed).
