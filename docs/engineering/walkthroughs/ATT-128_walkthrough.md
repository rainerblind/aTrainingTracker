# Implementation Walkthrough: Multi-Dimensional Workout Filtering (ATT-128)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-128](https://rainerblind.atlassian.net/browse/ATT-128) (`[Feature] Filter Workouts (& Segments & Routes)`)
* **Implementation Sub-Task**: [ATT-740](https://rainerblind.atlassian.net/browse/ATT-740) (`[Implementation] Filter Workouts (& Segments & Routes)`)
* **Associated Requirements & Tests**:
  * Requirement: `REQ-UI-132` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L355))
  * Test Specification: `TST-UI-085` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L320))
  * FixVersion: `V4.9.36`

Previously, the workout list offered sorting and compact/expanded view toggles, but users with large workout histories had no ability to search or filter workouts by criteria such as text query, year, sport type, gear, attributes (commute, trainer, GPS), or distance/duration thresholds.

This implementation provides:
1. **Clean, Extensible Filtering Data Model (`WorkoutFilterCriteria.kt`)**: Immutable model supporting text query, year/date range, sport sub-type, equipment/gear, commute, indoor trainer, GPS presence, and distance/duration ranges. Includes high-performance matching logic and robust JSON serialization.
2. **Session Persistence (`MyPreferenceManager.kt`)**: Filter criteria persist seamlessly across app restarts via Jetpack DataStore Preferences.
3. **Reactive In-Memory Filter & Sort Engine (`WorkoutSummariesViewModel.kt`)**: Combines repository workouts, active sort order, and active filter criteria into a reactive `StateFlow<List<WorkoutData>>`, ensuring zero lag and full preservation of existing sorting modes without database alterations.
4. **Intuitive Material 3 UI (`WorkoutFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, `WorkoutListActions.kt`, `WorkoutTabsScreen.kt`)**:
   * Filter action icon with active filter badge in the top app bar header.
   * Comprehensive Modal Bottom Sheet with structured filter sections and instant preview count.
   * Removable active filter chips row above the workout list with individual dismissal and "Clear all".
5. **Complete 9-Language Localization**: All 15 filter UI strings localized across `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/`.

---

## 2. Changes Implemented

### A. Data Model & Persistence
* **[WorkoutFilterCriteria.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteria.kt)**:
  * Implemented `WorkoutFilterCriteria` data class holding:
    * `query: String?` (case-insensitive search in title and description)
    * `year: Int?` (calendar year filter)
    * `dateRangeStartMs: Long?`, `dateRangeEndMs: Long?` (custom date range)
    * `sportTypeId: Long?` (sport sub-type ID)
    * `equipmentId: Long?` (equipment ID)
    * `commuteOnly: Boolean` (commute workout flag)
    * `trainerOnly: Boolean` (indoor trainer flag)
    * `hasGpsOnly: Boolean` (GPS track presence flag)
    * `minDistanceMeters: Double?`, `maxDistanceMeters: Double?` (distance thresholds)
    * `minDurationSec: Long?`, `maxDurationSec: Long?` (active duration thresholds)
  * Implemented `matches(workout: WorkoutData): Boolean` with short-circuit evaluation.
  * Implemented `activeFilterCount: Int` and `isEmpty: Boolean`.
  * Implemented `toJson(): String` and `fromJson(json: String?): WorkoutFilterCriteria` via `org.json.JSONObject`.
* **[MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)**:
  * Added `WORKOUT_FILTER_CRITERIA_JSON` preference key.
  * Added `workoutFilterCriteriaFlow: Flow<WorkoutFilterCriteria>` mapping saved JSON to `WorkoutFilterCriteria`.
  * Added `suspend fun setWorkoutFilterCriteria(criteria: WorkoutFilterCriteria)` writing JSON to DataStore.

### B. ViewModel & Reactive State
* **[WorkoutSummariesViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesViewModel.kt)**:
  * Injected `_filterCriteria: MutableStateFlow<WorkoutFilterCriteria>` initialized from `prefManager.workoutFilterCriteriaFlow`.
  * Exposed `val filterCriteria: StateFlow<WorkoutFilterCriteria> = _filterCriteria.asStateFlow()`.
  * Exposed `val allWorkouts: StateFlow<List<WorkoutData>> = workoutRepo.allWorkouts.stateIn(...)`.
  * Updated `val workouts: StateFlow<List<WorkoutData>>` combining `workoutRepo.allWorkouts`, `_sortOrder`, and `_filterCriteria`. Filters workouts before sorting according to selected `WorkoutSortOrder`.
  * Added `setFilterCriteria(criteria: WorkoutFilterCriteria)`, `clearFilterCriteria()`, and `updateFilterCriteria(transform: (WorkoutFilterCriteria) -> WorkoutFilterCriteria)` with persistence to DataStore.

### C. User Interface Components
* **[WorkoutListActions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutListActions.kt)**:
  * Added `onFilterClicked: (() -> Unit)?`, `isFilterActive: Boolean`, and `activeFilterCount: Int`.
  * Rendered `IconButton` with `Icons.Default.FilterList` and Material 3 `Badge` displaying the count of active filter dimensions.
* **[ActiveFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt)**:
  * Created horizontal scrollable row (`LazyRow`) with `InputChip` / `AssistChip` items for each active dimension.
  * Included trailing `✕` dismissal icons invoking granular removal in `WorkoutFilterCriteria`.
  * Included leading "Clear all" chip to reset all filters in a single tap.
* **[WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)**:
  * Material 3 `ModalBottomSheet` with structured layout:
    * Search query `OutlinedTextField` with clear button.
    * Year chips extracted dynamically from available workouts.
    * Sport sub-type chips extracted from workouts.
    * Gear / Equipment chips.
    * Attribute toggle chips (`Commute`, `Trainer`, `GPS`).
    * Distance range chips (`< 10 km`, `10 - 30 km`, `30 - 60 km`, `> 60 km`).
    * Duration range chips (`< 30 min`, `30 - 60 min`, `1 - 2 h`, `> 2 h`).
    * Bottom actions: "Reset" (clears draft) and "Apply" (commits criteria).
* **[WorkoutTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt)**:
  * Added filter button to `WorkoutListActions` in `CollapsibleHeader`.
  * Displayed `ActiveFilterChipsRow` below header when `!filterCriteria.isEmpty`.
  * Adjusted collapsible header height dynamically when active filter chips are visible.
  * Hosted `WorkoutFilterBottomSheet` on filter button click.
* **[WorkoutSummariesTabbedFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedFragment.kt)**:
  * Collected `viewModel.filterCriteria` and `viewModel.allWorkouts` and forwarded to `WorkoutTabsScreen`.

### D. Localization
* Added 15 strings across all 9 supported locales:
  * `app/src/main/res/values/strings.xml` (EN)
  * `app/src/main/res/values-de/strings.xml` (DE)
  * `app/src/main/res/values-es/strings.xml` (ES)
  * `app/src/main/res/values-fr/strings.xml` (FR)
  * `app/src/main/res/values-it/strings.xml` (IT)
  * `app/src/main/res/values-ja/strings.xml` (JA)
  * `app/src/main/res/values-nl/strings.xml` (NL)
  * `app/src/main/res/values-pl/strings.xml` (PL)
  * `app/src/main/res/values-pt/strings.xml` (PT)

### E. Automated Unit Testing
* **[WorkoutFilterCriteriaTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaTest.kt)**:
  * 10 tests verifying text query search (title and description), year filtering, sport sub-type filtering, gear filtering, commute/trainer/GPS attribute toggles, distance thresholds, duration thresholds, multi-criteria conjunction (AND logic), active filter counting, and JSON roundtrip serialization.
* **[WorkoutSummariesViewModelFilterTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesViewModelFilterTest.kt)**:
  * 5 tests verifying reactive flow combining with repository data, search query filtering, year filtering, clear filter restoration, and interplay with all `WorkoutSortOrder` modes.
* **[TranslationParityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt)**:
  * Verified 100% key parity and format string safety across all 9 locales.

---

## 3. Verification & Evidence
* **Automated Unit Tests**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*Filter*"`: **15/15 tests PASSED (100%)**.
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`: **PASSED (100% parity across all 9 locales)**.
