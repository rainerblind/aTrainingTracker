# Implementation Plan: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action (ATT-850)

## 1. Overview & Architecture
This plan establishes consistent inspection-first navigation for workout summary cards in workout history views ([`WorkoutSummariesTabbedFragment`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt) and [`WorkoutSummariesListFragment`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt)).

Currently, clicking almost anywhere on a detailed workout card (header, description, details, extrema) unexpectedly triggers `onEditWorkout()`, while only the mini-map preview routes to the full map screen ([`TrackOnMapScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt)).

Under this plan:
1. **Clicking a workout navigates to the map screen**: Clicking anywhere on the workout item/body (Header title/surface, Description, Details metrics, Extrema, MediaSection) navigates directly to [`TrackOnMapScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt).
2. **Dedicated Edit Action**: [`WorkoutHeader`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) encapsulates a dedicated edit button (`R.drawable.ic_table_edit`, `32.dp`, primary tint) positioned on the **very right** of the action row. Clicking it opens [`EditWorkoutScreen`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt).
3. **Preserved Exceptions**:
   - **Cluster Button**: Tapping the cluster button (`onClusterClick`) continues to navigate directly to the linked cluster heatmap screen in `WorkoutClustersFragment`.
   - **Export Status**: Tapping the export status badge (`ExportStatus`) continues to open its internal `ExportDetailsDialog`.
   - **Export Dropdown (3-dots)**: Tapping the 3-dots icon button continues to open the file export format menu.
4. **Compact Card Parity**: [`WorkoutSummaryCompact`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt) routes card tap to map view and provides an Edit option in its long-press context menu alongside Delete.

### Traceability
- **Requirement**: [`REQ-SET-071`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L113) (Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action)
- **Test Specification**: [`TST-SET-060`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L229) (Workout Card Click-to-Map Navigation & Dedicated Header Edit Action Verification)

---

## 2. Proposed Changes

### Component 1: `WorkoutHeader.kt` Action Row & Edit Action Encapsulation
#### [MODIFY] [`WorkoutHeader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)
- Add parameter: `onEditWorkout: (() -> Unit)? = null`.
- In the top-end action row:
  - Place `actions()` (caller-provided actions, e.g. `SwapHoriz`) on the left.
  - When `onEditWorkout != null`, render `IconButton(onClick = onEditWorkout, modifier = Modifier.size(32.dp))` with `R.drawable.ic_table_edit`, tinted with `MaterialTheme.colorScheme.primary` and content description `@string/edit_workout`.
  - Place `more_vert` export button (when `menuEnabled == true`) on the **very right**.
- This enforces the revised action layout (`actions` $\rightarrow$ `ic_table_edit` $\rightarrow$ `more_vert`), keeping the edit button clearly separated from the screen edge and out of the scrollbar overlay.

---

### Component 2: `WorkoutSummary.kt` Navigation Wiring
#### [MODIFY] [`WorkoutSummary.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)
- Re-wire `WorkoutHeader`:
  - `onClicked = onMapClick` (tapping header navigates to map screen).
  - `onEditWorkout = onEditWorkout` (passes editor trigger to the dedicated edit button).
- Replace `editWorkoutModifier` with `mapClickModifier = Modifier.clickable { if (workoutData.headerData.finished) onMapClick() }`.
- Apply `mapClickModifier` to:
  - `WorkoutDescription`
  - `WorkoutDetails`
  - `WorkoutExtrema`
- Preserve:
  - `WorkoutMediaSection(onMapClick = onMapClick)`
  - `ExportStatus` (internal click target preserved)
  - `onClusterClick` in `WorkoutHeader` (cluster navigation preserved)
  - `onExport`, `onSaveAsRoute`, `onDeleteRequest`

---

### Component 3: `TrackOnMapScreen.kt` Cleanup
#### [MODIFY] [`TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt)
- In `WorkoutHeader` call site:
  - Pass `onEditWorkout = onEditWorkout?.let { edit -> { edit(workoutData.id) } }` directly to `WorkoutHeader`.
  - Pass `actions = { headerActions() }`.
  - Remove redundant inline `ic_table_edit` button from `actions` block since `WorkoutHeader` now encapsulates it natively on the very right.

---

### Component 4: `WorkoutSummaryCompact.kt` & `WorkoutList.kt`
#### [MODIFY] [`WorkoutSummaryCompact.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt)
- Update parameters:
  - `onMapClick: () -> Unit`
  - `onEditWorkout: (() -> Unit)? = null`
  - `onDeleteRequest: () -> Unit`
- Wire `MappableListItem(onClick = onMapClick, ...)`
- In long-press `DropdownMenu`:
  - Add Edit option when `onEditWorkout != null`:
    ```kotlin
    DropdownMenuItem(
        text = { Text(stringResource(R.string.edit_workout)) },
        onClick = { showContextMenu = false; onEditWorkout() },
        leadingIcon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_table_edit),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
    ```
#### [MODIFY] [`WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt)
- In `items` loop for `WorkoutSummaryCompact`:
  - Pass `onMapClick = { onMapClick(workoutData) }`
  - Pass `onEditWorkout = { onEditWorkout(workoutData.id) }`
  - Pass `onDeleteRequest = { onDeleteRequest(workoutData.id) }`

---

### Component 5: Map View Edit Navigation Precedence
#### [MODIFY] [`WorkoutSummariesTabbedFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt) & [`WorkoutSummariesListFragment.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt)
- Invert condition evaluation order in `Scaffold` body:
  ```kotlin
  if (selectedWorkoutIdForEdit != null) {
      EditWorkoutScreen(...)
  } else if (selectedWorkoutForDetails != null) {
      TrackOnMapScreen(...)
  } else {
      // List
  }
  ```
- Ensures that tapping the edit action button within `TrackOnMapScreen` immediately launches `EditWorkoutScreen`.
- Dismissing `EditWorkoutScreen` returns directly to `TrackOnMapScreen`.
- Pressing Back from `TrackOnMapScreen` returns cleanly to the workout list.

---

## 3. Verification Plan

### Automated Tests
- Run clean-room test suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Verify zero regressions across all modules.

### Manual & Interactive Test Protocol (`TST-SET-060`)
1. **Detailed Workout Card Click-to-Map Navigation**:
   - Open Workout History (`WorkoutSummariesTabbedFragment`).
   - Tap the Workout Header title/surface: verify immediate transition to `TrackOnMapScreen`.
   - Press Back: verify return to Workout History at identical scroll position.
   - Tap Description section: verify transition to `TrackOnMapScreen`.
   - Tap Details section (distance/time/speed): verify transition to `TrackOnMapScreen`.
   - Tap Extrema section: verify transition to `TrackOnMapScreen`.
   - Tap Map Preview: verify transition to `TrackOnMapScreen`.
2. **Dedicated Header Edit Action**:
   - In Workout History, tap the `ic_table_edit` button on the very right of the workout header:
     verify `EditWorkoutScreen` opens directly with the workout's current properties.
   - Make a change or press back: verify return to Workout History with updated details reflected.
3. **Preserved Exceptions**:
   - Clustered Workout: Tap the cluster pill button: verify immediate navigation to `WorkoutClustersFragment` displaying the cluster heatmap.
   - Export Status: Tap the export status badge: verify `ExportDetailsDialog` opens.
   - 3-Dots Menu: Tap `more_vert`: verify export format dropdown menu opens without triggering map navigation or edit mode.
4. **Compact Card Parity**:
   - Switch to compact list view.
   - Tap any compact card: verify transition to `TrackOnMapScreen`.
   - Long-press a compact card: verify context menu displays both "Edit Workout" and "Delete".
   - Tap "Edit Workout": verify `EditWorkoutScreen` opens.
