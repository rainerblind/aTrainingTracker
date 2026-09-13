# Analysis: Go to edit workout from workout pop-up (ATT-506)

## 1. Problem Statement & Motivation
When inspecting a workout via a bottom popup (peek sheet) in analytical or spatial views (such as the Period Map or Cluster Heatmap), users frequently identify details they wish to adjust immediately (e.g., correcting the sport type, adjusting notes, updating equipment, renaming, or reassigning cluster). Currently, these popups display read-only headers without an editing trigger, forcing users to leave the map, navigate through list hierarchies, and manually find the workout again.

The user requested:
> *"Sometimes I feel the urgent need to change a detail of a workout when it is shown by a popup. We already have a button to start editing within the header for the periods / clusters. I would like to have a similar look and feel."*

## 2. Component & Architecture Analysis

### 2.1 The Workout Popup (`TrackOnMapScreen`)
- Embedded as the bottom sheet content in:
  - `PeriodMapScreen.kt` (workout peek on Period Map)
  - `WorkoutClusterHeatmapScreen.kt` (workout peek on Cluster Heatmap)
  - `WorkoutClustersFragment.kt` (inspected workout preview)
  - `WorkoutSummariesListFragment.kt` and `WorkoutSummariesTabbedFragment.kt` (selected workout details)
- Houses `WorkoutHeader` inside `MapDetailLayout`:
  ```kotlin
  WorkoutHeader(
      modifier = Modifier.fillMaxWidth(),
      data = workoutData.headerData,
      menuEnabled = false,
      onClicked = { },
      actions = headerActions
  )
  ```
- Currently, `onClicked` is a no-op `{ }`, and `actions` only receives caller-specific additions (e.g., `SwapHoriz` in `WorkoutClusterHeatmapScreen`).

### 2.2 Visual Look & Feel & Action Button Ordering
- The standard editing icon across the app headers is `R.drawable.ic_table_edit`.
- **Consistent Action Button Ordering (Edit on the Very Right)**:
  - **Lieblingsstrecken Header (`ClusterSummaryHeader`)**:
    Currently, the header renders `onRename` (`ic_table_edit`) on the left and `onEditFingerprint` (`EditLocationAlt`) on the right. To establish a coherent app-wide layout convention, the order must be reversed:
    1. Left: `EditLocationAlt` (Edit locations / fingerprint)
    2. Very Right: `ic_table_edit` (Edit name / identity)
  - **Workout Popup Header (`WorkoutHeader`)**:
    The edit button (`ic_table_edit`, 32 dp `IconButton` tinted with `MaterialTheme.colorScheme.primary`) is placed on the **very right**. Any contextual actions (such as `SwapHoriz` for reassigning cluster in the cluster heatmap) are placed to the left of the edit button.
  - Tapping the header surface itself (`onClicked`) also triggers `onEditWorkout`.

### 2.3 Host Fragment State & Navigation Flow
1. **`WorkoutClustersFragment`**:
   - Already manages `editedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }` and routes to `EditWorkoutScreen(viewModel = editViewModel, onBack = { editedWorkoutId = null })`.
   - `WorkoutClusterHeatmapScreen` simply needs an `onEditWorkout: (Long) -> Unit` callback that sets `editedWorkoutId = id`.
2. **`PeriodsFragment`**:
   - Does not yet host `EditWorkoutScreen`.
   - Adding `editedWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }` in `PeriodsFragment`:
     - When `editedWorkoutId != null`: displays `EditWorkoutScreen(viewModel = editViewModel, onBack = { editedWorkoutId = null; viewModel.loadPeriods() })`.
     - When `editedWorkoutId == null`: displays `PeriodMapScreen` (or `PeriodsTabsScreen`).
   - `PeriodMapScreen` accepts `onEditWorkout: (Long) -> Unit` and passes it to `TrackOnMapScreen`.
3. **Data Refresh Invariant**:
   - On returning from `EditWorkoutScreen`, the underlying repository flows automatically publish the updated `WorkoutData`.
   - For `PeriodsViewModel` and `WorkoutClustersViewModel`, re-selecting or refreshing the peeked workout ensures the latest edited attributes are displayed seamlessly.

## 3. Requirements & Traceability
- **New Requirement `REQ-SET-070`**: Direct Navigation to Workout Editor from Workout Detail Popup.
- **New Test Specification `TST-SET-059`**: Edit Workout from Popup Verification.

## 4. Risk & Invariant Assessment
- **Zero Layout Regressions**: The 32 dp `IconButton` fits seamlessly in `WorkoutHeader`'s top-end action row alongside existing actions (e.g. cluster transfer button in cluster view).
- **Back Navigation**: Proper `BackHandler` integration ensures pressing back in `EditWorkoutScreen` returns cleanly to the open popup rather than exiting the fragment.
