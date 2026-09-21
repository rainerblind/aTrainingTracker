# ASPICE Stage 1: Analysis & Problem Domain Formal Audit (ATT-1043)

## 1. Problem Domain & Motivation
The application currently uses a full-screen `Scaffold` (`EditWorkoutScreen.kt`) with a `TopAppBar` (`ArrowBack` icon + `Button(R.string.save)`) for editing workout details (name, route/cluster, sport, equipment, commute/trainer flags, Strava upload, description, goal, method).

This full-screen modal pattern causes several usability and architectural issues:
1. **Design System Inconsistency**: Diverges from the established `AppModalBottomSheet` and `AppDialogActions` design pattern (`REQ-UI-148`, `REQ-UI-149`, `REQ-UI-150`). While other configuration and edit workflows (e.g., `EditRouteDialog`, `EditDeviceDialog`, `EditEquipmentDialog`, `EditSportTypeDialog`) present as modern bottom sheets, workout editing remains a legacy full-screen screen.
2. **Fragment Viewport Collapse / Scrim Occlusion**: In callers (`PeriodsFragment`, `WorkoutSummariesListFragment`, `WorkoutSummariesTabbedFragment`, `WorkoutClustersFragment`), `selectedWorkoutIdForEdit` / `editedWorkoutId` was placed in an exclusive `if-else` block that unmounted the underlying list or map view. When converting to a modal bottom sheet, maintaining this exclusive structure would leave a blank/empty screen behind the translucent scrim.
3. **Action Bar Divergence**: The current `TopAppBar` places the "Speichern" button in the top-right corner and uses a top-left back arrow instead of the standardized bottom `AppDialogActions.SaveCancel` ("Abbrechen" on the left, "Speichern" on the right).

---

## 2. Root Cause Analysis
- `EditWorkoutScreen.kt` was constructed as an independent screen with its own `Scaffold` and `TopAppBar` before the app-wide consolidation to `AppModalBottomSheet`.
- Fragment callers treated workout editing as a separate screen state rather than an interactive modal dialog layered over the workout summaries list or map details.

---

## 3. Target Architecture & Scope Boundaries
### 3.1 Dialog Architecture (`EditWorkoutDialog.kt`)
- Backed by `AppModalBottomSheet` with drag handle, dismiss on scrim tap, close 'X' button in header, and edge-to-edge window insets (`navigationBarsPadding`, `imePadding`).
- **Header**:
  - Icon: `Icons.Default.Edit` (or painter `R.drawable.ic_table_edit`).
  - Title: `stringResource(R.string.edit_workout)` ("Training bearbeiten").
- **Body**:
  - Scrollable `Column` retaining all 8 input sections:
    1. Workout Name (`OutlinedTextField`)
    2. Route / Cluster Assignment (`OutlinedTextField` + tap opens `EditWorkoutClusterDialog` + clear button)
    3. Sport & Equipment Spinners (`DropdownSelector`)
    4. Commute & Trainer Checkboxes
    5. Strava Upload Checkbox (conditional on Strava integration)
    6. Description (`OutlinedTextField`, multi-line)
    7. Goal (`OutlinedTextField`)
    8. Method (`OutlinedTextField`)
- **Action Bar**:
  - `AppDialogActions.SaveCancel`:
    - `onCancel`: dismisses dialog without calling `saveChanges()`.
    - `onSave`: calls `viewModel.saveChanges()` and dismisses dialog.

### 3.2 Caller Layering Optimization
In all 4 caller sites:
- `WorkoutSummariesListFragment.kt`
- `WorkoutSummariesTabbedFragment.kt`
- `PeriodsFragment.kt`
- `WorkoutClustersFragment.kt`

The background content (`WorkoutList`, `TrackOnMapScreen`, `WorkoutTabsScreen`, `PeriodsList`, `WorkoutClustersScreen`) will remain composed, and `EditWorkoutDialog` will be rendered as an overlay whenever `selectedWorkoutIdForEdit != null` / `editedWorkoutId != null`. This provides a fluid slide-up bottom sheet over the real content.

---

## 4. Impacted Files & Components
- **Edit Workout UI**:
  - [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt) (refactored to `AppModalBottomSheet` with `AppDialogActions.SaveCancel`, or alias to `EditWorkoutDialog.kt`)
- **Callers**:
  - [WorkoutSummariesListFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt)
  - [WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)
  - [PeriodsFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsFragment.kt)
  - [WorkoutClustersFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersFragment.kt)
- **Tests**:
  - [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)

---

## 5. Invariant Checklist
- [x] **Data Persistence**: `saveChanges()` in `EditWorkoutViewModel` must be called only when "Speichern" is clicked.
- [x] **Reversion / Dismiss**: Dismissing via "Abbrechen", swipe down, scrim tap, or 'X' must leave the database unmutated.
- [x] **Cluster Dialog Sub-flow**: Tapping the Route/Cluster field must open `EditWorkoutClusterDialog` seamlessly and return selected values without dismissing the parent edit sheet.
- [x] **Dropdown Logic**: Sport and Equipment filtering and expansion rules (`REQ-UI-130`) must remain unaltered.
- [x] **Keyboard & Scroll Ergonomics**: TextFields inside the bottom sheet must respond to `imePadding()` when on-screen keyboard is visible.
