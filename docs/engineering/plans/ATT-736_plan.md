# Implementation Plan: Multi-Dimensional Route Filtering & Shared Filter Architecture (ATT-736)

## 1. Overview & Objectives
* **Parent Ticket**: [ATT-736](https://rainerblind.atlassian.net/browse/ATT-736) (`[Feature] Filter Routes`)
* **Sub-Task**: [ATT-751](https://rainerblind.atlassian.net/browse/ATT-751) (`[Impl-Plan] Filter Routes`)
* **Associated Requirements & Tests**:
  * Requirement: `REQ-UI-133` ([docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L257))
  * Test Specification: `TST-UI-086` ([docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L287))
  * FixVersion: `V4.9.36`
  * Branch: `feature/ATT-736`

### Core User Directives
1. **Look & Feel Parity**: The filtering of routes must match the look and feel of workout filtering (`ATT-742` / `REQ-UI-132`) as closely as possible:
   * Header filter action button with Material funnel icon (`Icons.Default.FilterAlt`) and `BadgedBox` active count badge.
   * Modal bottom sheet anchored flush to the bottom edge with a top title/close header and a dedicated bottom action bar ("Reset all" & "OK").
   * Horizontal scrolling chip strip for active criteria directly below the sport tabs with single-tap removal.
   * Auto-reset of filter criteria when returning to the main view (`drawer_start_tracking`) or switching drawer items.
2. **Code Sharing & Zero Duplication**:
   * Shared composables must be extracted for the bottom sheet layout shell, filter action button, and removable filter chip, eliminating duplicate layout code and preventing UI divergence between workouts and routes.

---

## 2. Shared Filter Architecture (Common Components)

We introduce a common package `com.atrainingtracker.trainingtracker.ui.common.filters` containing the reusable building blocks:

### A. Shared Bottom Sheet Shell
#### [NEW] [FilterBottomSheetScaffold.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterBottomSheetScaffold.kt)
* Reusable Material 3 `ModalBottomSheet` container wrapping the common geometry:
  * Flush bottom anchoring without gaps, including `navigationBarsPadding()`.
  * **Top Header Row**: Localized title text with `MaterialTheme.typography.titleLarge` and an `IconButton` with `Icons.Default.Close` for dismissal.
  * **Top Divider**: `HorizontalDivider()`.
  * **Scrollable Content Body**: `Column` with `rememberScrollState()`, `verticalScroll()`, and standardized padding (`horizontal = 20.dp`, `vertical = 12.dp`) accepting `@Composable ColumnScope.() -> Unit`.
  * **Bottom Divider**: `HorizontalDivider()`.
  * **Dedicated Bottom Action Row**: Full-width split actions:
    * "Reset all" (`OutlinedButton` with `R.string.filter_clear_all`) invoking `onClearAll()`.
    * "OK" / "Apply" (`Button` with `R.string.OK`) invoking `onApply()`.
* Utilized by both `WorkoutFilterBottomSheet` and `RouteFilterBottomSheet`.

### B. Shared Filter Action Button
#### [NEW] [FilterActionButton.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterActionButton.kt)
* Standardized action button rendering `IconButton(onClick = onClick)`:
  * Funnel icon: `Icons.Default.FilterAlt`.
  * When `isFilterActive && activeFilterCount > 0`, wrapped in `BadgedBox` with `Badge { Text(activeFilterCount.toString()) }`.
  * Configurable `tint: Color` (defaulting to `MaterialTheme.colorScheme.onPrimaryContainer`).
* Reused in `WorkoutListActions.kt` and `RouteTabbedScreen.kt`.

### C. Shared Removable Filter Chip
#### [NEW] [RemovableFilterChip.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/RemovableFilterChip.kt)
* Extracted from `ActiveFilterChipsRow.kt` into a shared composable:
  * Renders `InputChip` with `selected = true`, custom label, and trailing `Icons.Default.Close` icon.
  * Styled with `InputChipDefaults.inputChipColors` using `secondaryContainer`.
* Reused in `ActiveFilterChipsRow.kt` (workouts) and `ActiveRouteFilterChipsRow.kt` (routes).

---

## 3. Route Filtering Domain & Presentation

### A. Domain Model & Predicate
#### [NEW] [RouteFilterCriteria.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteria.kt)
* Immutable data class representing active filter criteria for routes:
  * `query: String = ""` (substring matching against `RouteSummary.name` and `RouteSummary.description`, case-insensitive).
  * `source: RouteSource? = null` (filter by origin: `STRAVA`, `LOCAL_GPX`, `WORKOUT`).
  * `isSelected: Boolean? = null` (filter by routes selected / marked visible on map).
  * `minDistanceMeters: Double? = null` (preset thresholds: 10 km, 25 km, 50 km, 100 km).
  * `minElevationGainMeters: Double? = null` (preset thresholds: 100 m, 250 m, 500 m, 1000 m).
* Methods & Properties:
  * `activeFilterCount: Int`: Computes count of active filter dimensions.
  * `isEmpty: Boolean` / `isNotEmpty: Boolean`: Fast emptiness check.
  * `matches(route: RouteWithPath): Boolean`: High-performance short-circuit predicate evaluation.
  * `toJson(): String` and `companion object { fun fromJson(json: String?): RouteFilterCriteria }`: Lightweight JSON persistence.

### B. Modal Route Filter Bottom Sheet
#### [NEW] [RouteFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt)
* Built on top of `FilterBottomSheetScaffold`:
  * Title: `stringResource(R.string.filter_routes_title)`.
  * Section 1: Text search `OutlinedTextField` with search leading icon and clear trailing icon.
  * Section 2: Route Source `FlowRow` of `FilterChip` items (`Strava`, `GPX`, `Workout`).
  * Section 3: Selection / Map Visibility `FilterChip` ("Selected only").
  * Section 4: Minimum Distance `FlowRow` chips (10 km, 25 km, 50 km, 100 km).
  * Section 5: Minimum Elevation Gain `FlowRow` chips (100 m, 250 m, 500 m, 1000 m).

### C. Active Route Filter Chips Row
#### [NEW] [ActiveRouteFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ActiveRouteFilterChipsRow.kt)
* Horizontal scrolling `LazyRow` displayed directly below sport tabs when `criteria.isNotEmpty`:
  * Query chip: `"query"`
  * Source chip: `Strava` / `GPX` / `Workout`
  * Selection chip: `@string/filter_route_selected`
  * Distance chip: `≥ 10 km`, etc.
  * Elevation chip: `≥ 250 m`, etc.
  * "Clear all" action button.

---

## 4. State Management, Lifecycle & Screen Integration

### A. Preferences Persistence Layer
#### [MODIFY] [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt)
* Define `ROUTE_FILTER_CRITERIA_JSON = stringPreferencesKey("route_filter_criteria_json")`.
* Expose `routeFilterCriteriaFlow: Flow<RouteFilterCriteria>`.
* Expose `suspend fun setRouteFilterCriteria(criteria: RouteFilterCriteria)`.
* Expose `fun clearRouteFilterCriteria()` executing asynchronously on `appScope`.

### B. ViewModel Pipeline
#### [MODIFY] [RoutesViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesViewModel.kt)
* Observe `preferenceManager.routeFilterCriteriaFlow` into `val filterCriteria: StateFlow<RouteFilterCriteria>`.
* Update `routes: StateFlow<List<RouteWithPath>>` pipeline:
  * Combine `routesRepository.allRoutes`, `_sortOrder`, `currentLocation`, and `filterCriteria`.
  * Apply `criteria.matches(route)` prior to sorting.
* Add mutation methods:
  * `fun setFilterCriteria(criteria: RouteFilterCriteria)`
  * `fun clearFilterCriteria()`
  * `fun updateFilterCriteria(transform: (RouteFilterCriteria) -> RouteFilterCriteria)`

### C. UI Integration & Action Row Harmonization
#### [MODIFY] [RouteTabbedScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedScreen.kt)
* Header action row: Append `FilterActionButton` at the end of the action row (`Select All` $\rightarrow$ `Import` $\rightarrow$ `Sync Indicator` $\rightarrow$ `Sort` $\rightarrow$ `Filter`).
* Position `ActiveRouteFilterChipsRow` directly below the `PrimaryScrollableTabRow`.
* Host `RouteFilterBottomSheet` when filter dialog state is open.

#### [MODIFY] [RoutesFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesFragment.kt)
* Pass `filterCriteria`, `onApplyFilterCriteria`, `onClearAllFilters`, and `onUpdateFilterCriteria` to `RouteTabbedScreen`.
* In `onDestroyView()`, invoke `viewModel.clearFilterCriteria()` if `activity?.isChangingConfigurations != true`.

#### [MODIFY] [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
* Clear route filter criteria when pressing back to `drawer_start_tracking`:
  ```kotlin
  if (mSelectedFragmentId == R.id.drawer_routes) {
      MyPreferenceManager(applicationContext).clearRouteFilterCriteria()
  }
  ```
* Clear route filter criteria when switching drawer items away from `drawer_routes`:
  ```kotlin
  if (mSelectedFragmentId == R.id.drawer_routes && itemId != R.id.drawer_routes) {
      MyPreferenceManager(applicationContext).clearRouteFilterCriteria()
  }
  ```

### D. Workout Filter Refactoring (Adopting Shared Components)
#### [MODIFY] [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)
* Refactor to use `FilterBottomSheetScaffold`, removing redundant header, divider, and bottom action bar code.
#### [MODIFY] [WorkoutListActions.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutListActions.kt)
* Refactor filter icon button to use `FilterActionButton`.
#### [MODIFY] [ActiveFilterChipsRow.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt)
* Refactor chips to use shared `RemovableFilterChip`.

---

## 5. 9-Language Localization Parity

Add route-specific filter strings to `strings_filters.xml` across all 9 supported locales:
* `filter_routes_title`: "Filter routes" / "Routen filtern"
* `filter_route_source`: "Source" / "Quelle"
* `filter_route_selected`: "Selected only" / "Nur ausgewählte"
* `filter_min_elevation_format`: "≥ %1$s m"

---

## 6. System Invariant Checklist
* [x] *Workout Filter Invariant*: Workout filtering (`REQ-UI-132` / `TST-UI-085`) remains 100% operational with identical appearance and behavior.
* [x] *Route Sorting Invariant*: All 4 route sorting orders (`DISTANCE_TO_USER`, `TOTAL_ELEVATION_GAIN`, `ROUTE_DISTANCE`, `NAME`) remain fully operational on filtered subsets.
* [x] *Sport Tab Hierarchy*: Route tabs (*All*, *Bike*, *Run*, *Other*) continue to partition routes by `BSportType` while strictly respecting active filters.
* [x] *Route Action Operations*: GPX import, Strava route synchronization, route deletion, and route editing remain unhindered.
* [x] *Localization Parity*: Strict format specifier and key parity across all 9 application locales.

---

## 7. Automated Verification Plan
* **Unit Tests**:
  * `RouteFilterCriteriaTest` (10 tests): Verify predicate matching across query, source, selection, distance, elevation gain, and active count calculation.
  * `RoutesViewModelFilterTest` (5 tests): Verify reactive flow composition, sorting interplay, live filter updates, and reset behavior.
* **Translation Parity**:
  * `TranslationParityTest`: Verify 100% key parity and placeholder safety across all 9 locales.
* **Full Regression Suite**:
  * `./gradlew testDebugUnitTest`: Verify zero regressions across the entire project test suite.
