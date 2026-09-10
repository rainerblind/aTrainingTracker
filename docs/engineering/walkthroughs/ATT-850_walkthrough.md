# Walkthrough: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action (ATT-850)

* **Parent Ticket**: [ATT-850](https://rainerblind.atlassian.net/browse/ATT-850) (*[Verbesserung] When clicking on a workout, always navigate to the workout on map screen; when clicking on the edit workout, we always go to the edit workout dialog*)
* **Sub-Task**: [ATT-858](https://rainerblind.atlassian.net/browse/ATT-858) (*[Implementation] When clicking on a workout, always navigate to the workout on map screen; when clicking on the edit workout, we always go to the edit workout dialog*)
* **Target Version**: `V4.9.36`
* **Requirement**: [`REQ-SET-071`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L113) (*Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action*)
* **Test Specification**: [`TST-SET-060`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L229) (*Workout Card Click-to-Map Navigation & Dedicated Header Edit Action Verification*)
* **Branch**: `feature/ATT-850`

---

## 1. Overview & Architecture

Previously in workout list views, tapping anywhere on a workout summary card (Header surface, Description, Details metrics, Extrema) launched `EditWorkoutScreen`, while only tapping the mini-map preview launched `TrackOnMapScreen`. This violated user expectations during activity inspection and caused unintended transitions into edit mode.

Under ATT-850:
1. **Inspection-First Default**: Tapping anywhere on a workout card (Header title/surface, Description, Details, Extrema, MediaSection) routes directly to the detailed map screen ([`TrackOnMapScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) via `onMapClick()`).
2. **Dedicated Edit Action**: [`WorkoutHeader`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) encapsulates an edit action button (`R.drawable.ic_table_edit`, `32.dp`, primary tint) positioned on the **very right** of the action row. Clicking it opens [`EditWorkoutScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt).
3. **Preserved Exceptions**:
   - The **Cluster button** in `WorkoutHeader` navigates directly to the linked cluster heatmap screen in `WorkoutClustersFragment` via `onClusterClick(clusterId)`.
   - The **Export status badge** (`ExportStatus`) continues to open its internal `ExportDetailsDialog`.
   - The **Export menu (3-dots)** continues to open the file export format menu.
4. **Compact Card Parity**: [`WorkoutSummaryCompact`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt) routes card tap to map view and provides an Edit option in its long-press context menu alongside Delete.

---

## 2. Summary of Changes

### 2.1 Action Row & Edit Action Encapsulation ([`WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt))
* Added parameter `onEditWorkout: (() -> Unit)? = null`.
* In top-end action row, placed `actions()` on the left, `more_vert` export button in the middle, and `IconButton` with `R.drawable.ic_table_edit` on the **very right** whenever `onEditWorkout != null`.
* Enforces unified action ordering across `WorkoutHeader`, `TrackOnMapScreen`, and `ClusterSummaryHeader`.

### 2.2 Click-to-Map Navigation & Header Edit Wiring ([`WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt))
* Re-wired `WorkoutHeader`:
  * `onClicked = onMapClick` (tapping header navigates to map view).
  * `onEditWorkout = onEditWorkout` (forwarded to dedicated edit button).
* Replaced `editWorkoutModifier` with `mapClickModifier = Modifier.clickable { if (workoutData.headerData.finished) onMapClick() }`.
* Applied `mapClickModifier` across `WorkoutDescription`, `WorkoutDetails`, and `WorkoutExtrema`.
* Preserved `WorkoutMediaSection`, `ExportStatus`, and `onClusterClick`.

### 2.3 Cleaner Header Delegation in Map Popup ([`TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt))
* Forwarded `onEditWorkout = onEditWorkout?.let { edit -> { edit(workoutData.id) } }` directly to `WorkoutHeader`.
* Removed manual inline edit button from `actions` block, delegating layout placement to `WorkoutHeader`.

### 2.4 Compact Summary Parity ([`WorkoutSummaryCompact.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt) & [`WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt))
* Renamed parameter to `onMapClick: () -> Unit` and added `onEditWorkout: (() -> Unit)? = null`.
* Wired card tap to `onMapClick`.
* Added "Edit Workout" menu item with `ic_table_edit` to the long-press context menu alongside "Delete".
* Forwarded callbacks from `WorkoutList`.

### 2.5 Automated Unit Tests ([`WorkoutHeaderDataTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderDataTest.kt))
* Added unit test `workoutBodyClick_routesToMapNavigation_andRemainsIsolatedFromEdit` validating:
  * Body click triggers `onMapClick` without triggering edit or cluster navigation.
  * Edit button click triggers `onEditWorkout` without triggering map or cluster navigation.
  * Cluster button click triggers `onClusterClick` without triggering map or edit navigation.

---

## 3. Verification & Test Evidence

### 3.1 Clean-Room Test Suite
Executed full clean-room unit test suite:
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL in 1m 46s` (32 actionable tasks, 0 failures, 0 regressions).

### 3.2 Targeted Action & Navigation Test
Executed `WorkoutHeaderDataTest`:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataTest"
```
**Result**: `BUILD SUCCESSFUL in 13s` (all 3 tests passed).
