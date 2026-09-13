# Implementation Plan: Filter Workouts (ATT-128)

## 1. Overview & Problem Context
Athletes tracking training sessions over extended periods accumulate hundreds or thousands of workouts. Navigating to specific historical sessions currently requires manual scrolling or sorting by broad metrics (date, duration, distance, elevation gain).
**ATT-128** introduces multi-dimensional workout filtering on `WorkoutTabsScreen` with a clean, extensible domain model (`WorkoutFilterCriteria`), top app bar action button with active badge indicator, a Material 3 modal filter bottom sheet (`WorkoutFilterBottomSheet`), removable active filter chips strip (`ActiveFilterChipsRow`), and session persistence across app restarts via `MyPreferenceManager` (DataStore).

---

## 2. Requirements & Verification Traceability
* **Primary Requirement**: `REQ-UI-132` (*Multi-Dimensional Workout Filtering, Modal Filter Sheet & Persistent Filter State*)
* **Verification Test Case**: `TST-UI-085` (*Multi-Dimensional Workout Filtering, Modal Sheet & Persistence Verification*)
* **Target Version (Lösungsversion)**: `V4.9.36`

---

## 3. Proposed Changes by Component

### 3.1. Domain & Data Layer (`com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist`)
* **[NEW] `WorkoutFilterCriteria.kt`**:
  - Immutable data class holding active filter values:
    - `query: String = ""`
    - `year: Int? = null`
    - `month: Int? = null`
    - `startDateS: Long? = null`
    - `endDateS: Long? = null`
    - `sportTypeId: Long? = null`
    - `equipmentId: Long? = null`
    - `isCommute: Boolean? = null`
    - `isTrainer: Boolean? = null`
    - `hasGpsTrack: Boolean? = null`
    - `minDistanceMeters: Double? = null`
    - `minDurationSec: Long? = null`
  - Computed property `val activeFilterCount: Int`
  - Computed property `val isEmpty: Boolean`
  - Method `fun matches(workout: WorkoutData): Boolean`: High-performance predicate evaluating all active criteria against workout fields.
  - JSON serialization: `fun toJson(): String` and `companion object { fun fromJson(json: String): WorkoutFilterCriteria }` for safe, lightweight preference storage.

* **[MODIFY] `WorkoutSummariesViewModel.kt`**:
  - Introduce `_filterCriteria = MutableStateFlow(WorkoutFilterCriteria())` and public `val filterCriteria: StateFlow<WorkoutFilterCriteria>`.
  - Wire DataStore loading on initialization so persisted filter criteria are restored asynchronously.
  - Expose helper functions:
    - `fun setFilterCriteria(criteria: WorkoutFilterCriteria)`
    - `fun clearFilterCriteria()`
    - `fun updateFilter(transform: (WorkoutFilterCriteria) -> WorkoutFilterCriteria)`
    - `fun removeQuery()`, `fun removeYear()`, `fun removeSport()`, `fun removeEquipment()`, etc.
  - Update `val workouts: StateFlow<List<WorkoutData>>`:
    Combine `workoutRepo.allWorkouts`, `_sortOrder`, and `_filterCriteria` into a single reactive pipeline applying filtering before sorting.

* **[MODIFY] `MyPreferenceManager.kt`**:
  - Add DataStore preference key `WORKOUT_FILTER_CRITERIA_JSON = stringPreferencesKey("workout_filter_criteria_json")`.
  - Expose `val workoutFilterCriteriaFlow: Flow<WorkoutFilterCriteria>`.
  - Expose `suspend fun setWorkoutFilterCriteria(criteria: WorkoutFilterCriteria)`.

---

### 3.2. Presentation & UI Layer (`com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist`)
* **[MODIFY] `WorkoutListActions.kt`**:
  - Add optional parameters to `WorkoutListActions`:
    - `onFilterClicked: (() -> Unit)? = null`
    - `isFilterActive: Boolean = false`
    - `activeFilterCount: Int = 0`
  - When `onFilterClicked != null`, render an `IconButton` displaying `Icons.Default.FilterList` with `BadgedBox` if `isFilterActive == true` displaying `activeFilterCount`.
  - Maintain backward compatibility for existing callers (`WorkoutClustersFragment`, `WorkoutSummariesListFragment`).

* **[NEW] `ActiveFilterChipsRow.kt`**:
  - Horizontally scrolling chip bar (`LazyRow`) displayed directly above the workout list when `filterCriteria.isNotEmpty`.
  - Renders removable chips for each active dimension with trailing `✕` icon:
    - Text search query (`"..."`)
    - Year (`"2025"`)
    - Sport name (`"Road Bike"`)
    - Equipment name (`"Canyon"`)
    - Commute / Trainer / GPS tags
    - Min Distance / Duration
    - "Clear all" action chip when multiple criteria are active.
  - Tapping `✕` invokes the corresponding remove handler reactively.

* **[NEW] `WorkoutFilterBottomSheet.kt`**:
  - Modern Material 3 `ModalBottomSheet` displaying:
    1. Header row with title (`@string/filter_workouts_title`), "Clear All" / "Reset" action, and "Done" dismiss button.
    2. Search query `OutlinedTextField` with leading search icon and trailing clear icon.
    3. Year selection row (extracting distinct years from workout history as selectable chips).
    4. Sport selection dropdown / chips (extracting available sports from workout history).
    5. Equipment selection dropdown / chips (extracting available equipment).
    6. Workout attributes chips (Commute, Trainer/Indoor, Has GPS Track).
    7. Threshold inputs for minimum distance (km) and minimum duration (min).

* **[MODIFY] `WorkoutTabsScreen.kt`**:
  - Connect `filterCriteria` state from ViewModel.
  - Integrate `WorkoutFilterBottomSheet` driven by `showFilterSheet` boolean state.
  - Pass filter action callbacks, `isFilterActive`, and `activeFilterCount` to `WorkoutListActions`.
  - Render `ActiveFilterChipsRow` above the pager content so filters are immediately visible and dismissible across all tabs.

---

### 3.3. Localization (`app/src/main/res/values*/strings.xml`)
* Add new localized strings across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT):
  - `filter_workouts_title` ("Filter Workouts")
  - `filter_action` ("Filter")
  - `filter_clear_all` ("Clear all")
  - `filter_search_hint` ("Search title, description, notes...")
  - `filter_section_time` ("Time & Period")
  - `filter_section_sport` ("Sport Type")
  - `filter_section_equipment` ("Equipment")
  - `filter_section_attributes` ("Attributes")
  - `filter_section_thresholds` ("Distance & Duration")
  - `filter_commute` ("Commute")
  - `filter_trainer` ("Indoor / Trainer")
  - `filter_has_gps` ("Has GPS Track")
  - `filter_min_distance_format` ("> %1$s km")
  - `filter_min_duration_format` ("> %1$s min")
  - `filter_all_sports` ("All sports")
  - `filter_all_equipment` ("All equipment")
  - `filter_no_matching_workouts` ("No workouts match the active filters")

---

## 4. System Invariants & Non-Regression Protections
1. **Sorting Invariant**: `WorkoutSortOrder` (*Date*, *Duration*, *Distance*, *Elevation Gain*) MUST continue to sort filtered results without regression.
2. **View Mode Invariant**: Toggling between Compact view and Detailed view MUST remain fully operational.
3. **Tab Hierarchy Invariant**: Top-level tabs (*All*, *Bike*, *Run*, *Other*) MUST continue to partition workouts by `BSportType` while strictly respecting all active sub-filters.
4. **Deletion Workflows**: Individual workout deletion and bulk deletion of old workouts MUST NOT be altered or broken.
5. **Database Integrity**: Zero SQLite schema alterations; in-memory filtering operates on decoded `WorkoutData` models.
6. **Caller Compatibility**: Default parameters on `WorkoutListActions` prevent compilation or runtime breakage for existing callers.

---

## 5. Automated Verification Strategy
* **Unit Tests (`WorkoutFilterCriteriaTest.kt`)**:
  - Test predicate matching across all dimensions: text query (title/notes/description case-insensitive match), date range, year/month, specific sport ID, specific equipment ID, commute flag, trainer flag, GPS track flag, and distance/duration thresholds.
  - Test active filter count calculation and JSON serialization/deserialization.
* **ViewModel Tests (`WorkoutSummariesViewModelFilterTest.kt`)**:
  - Test combining `allWorkouts`, `sortOrder`, and `_filterCriteria`.
  - Test updating and clearing filter criteria.
  - Test interaction between tab filtering (`BSportType`) and `WorkoutFilterCriteria`.
* **Clean-Room Regression**:
  - Execute `./gradlew testDebugUnitTest` to guarantee 100% test pass with zero regressions.
