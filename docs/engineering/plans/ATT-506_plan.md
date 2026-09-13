# Implementation Plan: Go to edit workout from workout pop-up (ATT-506)

## 1. Overview & Architecture
This plan establishes direct navigation to `EditWorkoutScreen` from the Workout Detail Popup (`TrackOnMapScreen`) in analytical views (Period Map and Favorite Tracks / Cluster Heatmap) and enforces a consistent app-wide action button layout where the edit action (`R.drawable.ic_table_edit`) is positioned on the **very right**.

Traceability:
- **Requirement**: `REQ-SET-070` (Direct Navigation to Workout Editor from Workout Detail Popup & Unified Action Button Layout)
- **Test Specification**: `TST-SET-059` (Workout Popup Direct Edit & Header Button Layout Verification)

## 2. Proposed Changes

### Component 1: Action Button Ordering in Favorite Tracks Header
#### [MODIFY] `WorkoutClusterHeatmapScreen.kt`
- In `ClusterSummaryHeader` action row:
  - Move `Icons.Default.EditLocationAlt` (Edit Fingerprint) to the left.
  - Move `R.drawable.ic_table_edit` (Rename / Identity) to the **very right**.
- In `WorkoutClusterHeatmapScreen`:
  - Add parameter `onEditWorkout: (Long) -> Unit = {}`.
  - Pass `onEditWorkout = onEditWorkout` to `TrackOnMapScreen` in `sheetContent`.

---

### Component 2: Workout Popup Header Edit Action
#### [MODIFY] `TrackOnMapScreen.kt`
- Add parameter `onEditWorkout: ((Long) -> Unit)? = null`.
- In `WorkoutHeader` call site:
  - Wire `onClicked = { onEditWorkout?.invoke(workoutData.id) }`.
  - In `actions`:
    - Call `headerActions()` first (ensuring contextual actions like `SwapHoriz` appear on the left).
    - If `onEditWorkout != null`, render an `IconButton` (`size = 32.dp`) with `R.drawable.ic_table_edit` on the **very right**, with `@string/edit_workout` content description and `MaterialTheme.colorScheme.primary` tint.
#### [MODIFY] `WorkoutHeader.kt`
- When `menuEnabled == false` and `onClicked != {}`: enable clickable on the `Surface` so clicking anywhere on the header triggers edit when enabled.

---

### Component 3: Host Fragment Navigation Integration
#### [MODIFY] `WorkoutClustersFragment.kt`
- In `selectedCluster != null`: pass `onEditWorkout = { id -> editedWorkoutId = id }` to `WorkoutClusterHeatmapScreen`.
- In `inspectedWorkout != null`: pass `onEditWorkout = { id -> editedWorkoutId = id }` to `TrackOnMapScreen`.

#### [MODIFY] `PeriodMapScreen.kt`
- Add parameter `onEditWorkout: (Long) -> Unit = {}`.
- Pass `onEditWorkout = onEditWorkout` to `TrackOnMapScreen` in `sheetContent`.

#### [MODIFY] `PeriodsFragment.kt`
- Add `var editedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }`.
- Conditionally render `EditWorkoutScreen(viewModel = editViewModel, onBack = { editedWorkoutId = null; viewModel.loadPeriods() })` when `editedWorkoutId != null`.
- Pass `onEditWorkout = { id -> editedWorkoutId = id }` to `PeriodMapScreen`.

#### [MODIFY] `WorkoutSummariesListFragment.kt` & `WorkoutSummariesTabbedFragment.kt`
- Pass `onEditWorkout = { id -> selectedWorkoutIdForEdit = id }` to `TrackOnMapScreen` for `selectedWorkoutForDetails` to ensure consistent edit capabilities across all detail map popups.

---

## 3. Verification Plan

### Automated Tests
- Execute `./gradlew testDebugUnitTest` across all modules:
  - Verify zero regressions across `WorkoutClustersViewModelTest`, `WorkoutNavigationEventsTest`, `EditWorkoutClusteringTest`, etc.

### Manual & Interactive Test Protocol (`TST-SET-059`)
1. **Favorite Tracks (Lieblingsstrecken) Header**:
   - Open a Favorite Track detail view (`WorkoutClusterHeatmapScreen`).
   - Verify action buttons: `EditLocationAlt` is on the left, `ic_table_edit` is on the very right.
2. **Workout Popup in Favorite Tracks**:
   - Tap a workout marker on the cluster heatmap to open the bottom sheet popup.
   - Verify header action buttons: `SwapHoriz` is on the left, `ic_table_edit` is on the very right.
   - Tap the edit icon: verify `EditWorkoutScreen` opens with the workout's current properties.
   - Press system back or top back button: verify return to the cluster heatmap without state loss.
3. **Workout Popup in Period Map**:
   - Open a Period Map from `PeriodsFragment`.
   - Tap a workout: verify the popup header renders `ic_table_edit` on the very right.
   - Tap the edit icon: verify `EditWorkoutScreen` opens.
   - Make a change (e.g. rename or toggle commute) and press back: verify return to `PeriodMapScreen` with the update reflected.
