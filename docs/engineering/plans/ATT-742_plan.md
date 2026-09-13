# Implementation Plan: Workout Filter UI & Lifecycle Improvements (ATT-742)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-742](https://rainerblind.atlassian.net/browse/ATT-742) (`[Verbesserung] Improve filtering of workouts`)
* **Sub-Task**: [ATT-746](https://rainerblind.atlassian.net/browse/ATT-746) (`[Impl-Plan] Improve filtering of workouts`)
* **Associated Requirements & Tests**:
  * Requirement: `REQ-UI-132` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L256))
  * Test Specification: `TST-UI-085` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L286))
  * FixVersion: `V4.9.36`

This plan addresses the four specific user feedback points identified during real-device testing of the initial filter implementation (ATT-128):
1. **Icon Affordance**: Replace the horizontal slider list icon (`Icons.Default.FilterList`) with the standard Material funnel icon (`Icons.Default.FilterAlt`).
2. **Bottom Sheet Anchoring**: Remove `Modifier.fillMaxHeight(0.9f)` on `ModalBottomSheet` to eliminate the bottom detachment gap artifact when dragging/expanding.
3. **Header & Action Button Layout**: De-clutter the top row by introducing a dedicated top header (Title + Close dismiss button) and a separate, full-width bottom action row ("Reset all" and "Apply").
4. **Lifecycle & Main View Reset**: Automatically clear active filter criteria when the user navigates away from the workouts section to the main view (Start Tracking), while preserving filters during inner-section interactions (tab switching, details, edit workout).

---

## 2. Proposed Changes

### A. Presentation Layer (Header Funnel Icon)
#### [MODIFY] [WorkoutListActions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutListActions.kt)
* Import `androidx.compose.material.icons.filled.FilterAlt`.
* In `IconButton(onClick = onFilterClicked)`, render `Icons.Default.FilterAlt` instead of `Icons.Default.FilterList`.
* Retain `BadgedBox` with `Badge(content = { Text(activeFilterCount.toString()) })` when `isFilterActive && activeFilterCount > 0`.

### B. Presentation Layer (Bottom Sheet Layout & Geometry)
#### [MODIFY] [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)
* **Sheet Container**: Remove `modifier = Modifier.fillMaxHeight(0.9f)` on `ModalBottomSheet`. Ensure the sheet surface anchors flush to the bottom edge with proper navigation bar window insets.
* **Top Header Row**:
  * Title: `stringResource(R.string.filter_workouts_title)` with `MaterialTheme.typography.titleLarge`.
  * Dismiss Action: `IconButton(onClick = onDismissRequest)` with `Icons.Default.Close`.
* **Scrollable Content Body**:
  * Accommodates search field, year chips, sport chips, gear chips, attribute toggles, and distance/duration threshold chips within a vertically scrollable container.
* **Dedicated Bottom Action Row**:
  * Distinct horizontal `Row` with full width and `navigationBarsPadding()`:
    * `"Reset all"` (`OutlinedButton` with `R.string.filter_clear_all`): Resets local draft state and invokes `onClearAll()`.
    * `"Apply"` (`Button` with `R.string.filter_apply`): Commits local criteria via `onApplyCriteria()` and dismisses sheet.

### C. State Management & Lifecycle Layer
#### [MODIFY] [WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)
* In `onDestroyView()`, invoke `viewModel.clearFilterCriteria()`.
* When the user presses back to return to `drawer_start_tracking` or switches to another drawer item, `onDestroyView()` resets active filter criteria in memory and DataStore, guaranteeing that subsequent visits start with the full, unfiltered list.
* Inner-section interactions (switching tabs All/Bike/Run/Other, opening track on map details, editing a workout) keep `WorkoutSummariesTabbedFragment` alive, preserving active filters seamlessly.

---

## 3. System Invariant Checklist
* [x] *Sorting Invariant*: `WorkoutSortOrder` (*Date*, *Duration*, *Distance*, *Elevation Gain*) remains fully operational on filtered subsets.
* [x] *View Mode Invariant*: Compact view vs. Detailed view toggling remains fully intact.
* [x] *Sport Tab Hierarchy Invariant*: Top-level tabs (*All*, *Bike*, *Run*, *Other*) continue to partition workouts by `BSportType` while respecting active sub-filters.
* [x] *Database & Deletion Invariant*: Individual workout deletion, bulk deletion of old workouts, and SQLite database schema remain untouched and uncorrupted.
* [x] *Caller Compatibility*: Default parameter values on `WorkoutListActions` preserve binary and source compatibility for all callers.
* [x] *Localization Parity*: All 15 existing localized strings across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) are reused without new translation debt.

---

## 4. Automated Verification Plan
* **Unit Tests**:
  * `WorkoutFilterCriteriaTest` (10 tests): Verify predicate evaluation, active count, JSON roundtrip.
  * `WorkoutSummariesViewModelFilterTest` (5 tests): Verify reactive flow composition, sort order interplay, live query filtering, and filter reset restoration.
* **Localization Parity**:
  * `TranslationParityTest`: Verify 100% key parity across all 9 locales.
* **Full Regression Suite**:
  * `./gradlew testDebugUnitTest`: Verify zero regressions across the entire project.
