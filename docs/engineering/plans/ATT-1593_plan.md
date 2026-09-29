# Stage 3: Implementation Plan - ATT-1593: Selection of Lieblingsorte & Lieblingsstrecken in Filter Dialogs

**Ticket**: [ATT-1593](https://atrainingtracker.atlassian.net/browse/ATT-1593)  
**Sub-task**: [ATT-1603](https://atrainingtracker.atlassian.net/browse/ATT-1603) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*)  
**Test Mapping**: `TST-UI-141` (`TST-UI-141.1`, `TST-UI-141.2`, `TST-UI-141.3`, `TST-UI-141.4`, `TST-UI-141.5`, `TST-UI-141.6`)  
**Branch**: `feature/ATT-1593`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

Athletes frequently need to filter workouts and route clusters by spatial context (*Lieblingsorte* / start locations) and route family (*Lieblingsstrecken* / clusters). While the underlying distance matching predicates exist for 1-tap card drill-downs (`ATT-1401`, `ATT-1402`), neither `WorkoutFilterBottomSheet` nor `ClusterFilterBottomSheet` allows users to select favorite start locations. Furthermore, `WorkoutFilterCriteria` lacks route cluster filtering dimensions (`clusterId`, `clusterName`), and `ActiveClusterFilterChipsRow` lacks a removable chip for active location filters.

This plan details the atomic implementation steps to integrate interactive chips into both bottom sheets, extend domain criteria and DataStore persistence, add active chip visualizations with 1-tap dismissal, and hoist repository flows through the view models.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*)
* **Test Mapping**: `TST-UI-141`
  - `TST-UI-141.1`: Domain criteria & JSON persistence unit tests (`WorkoutFilterCriteriaClusterTest.kt`).
  - `TST-UI-141.2`: Workout filter sheet composable tests (`WorkoutFilterBottomSheetLocationClusterTest.kt`).
  - `TST-UI-141.3`: Cluster filter sheet composable tests (`ClusterFilterBottomSheetLocationTest.kt`).
  - `TST-UI-141.4`: Active filter chips row tests (`ActiveFilterChipsRowClusterTest.kt`, `ActiveClusterFilterChipsRowLocationTest.kt`).
  - `TST-UI-141.5`: 9-language localization audit (`TranslationParityTest.java`).
  - `TST-UI-141.6`: Full clean-room test suite regression (`testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing filtering dimensions (text search, year, month, date ranges, sport sub-types, equipment, commute/trainer/GPS flags, distance/duration intervals) must continue to function identically.
2. **Backward Compatibility**: `WorkoutFilterCriteria` and `ClusterFilterCriteria` deserialization must handle legacy JSON strings lacking `clusterId` or `startLocation*` gracefully by defaulting to `null`.
3. **Thread Safety & Dispatcher Affinity**: Repository operations (`KnownLocationsRepository`, `WorkoutClusterRepository`) remain confined to their background single-thread dispatchers (`KnownLocationsDB-Thread`, `WorkoutClusterDB-Thread`).
4. **Single-Selection Toggle Ergonomics**: Tapping an unselected location/cluster chip selects it; tapping an already selected chip deselects it; tapping "Alle zurücksetzen" clears all selections.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Parent ticket `ATT-1593` terminal transition remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Domain & Persistence Layer (`WorkoutFilterCriteria.kt`)
* Add properties `val clusterId: Long? = null` and `val clusterName: String? = null`.
* In `activeFilterCount`, increment by 1 if `clusterId != null`.
* In `matches(workout: WorkoutData)`:
  ```kotlin
  if (clusterId != null && workout.clusterId != clusterId) {
      return false
  }
  ```
* In `toJson()` and `fromJson()`: add serialization and deserialization for `clusterId` and `clusterName`.

### Component 2: Workout Filter Bottom Sheet (`WorkoutFilterBottomSheet.kt`)
* Add parameters:
  - `knownLocations: List<KnownLocationItem> = emptyList()`
  - `availableClusters: List<WorkoutCluster> = emptyList()`
* Introduce local state:
  - `localStartLocationLat`, `localStartLocationLng`, `localStartLocationName`, `localStartLocationRadiusM` initialized from `criteria`.
  - `localClusterId`, `localClusterName` initialized from `criteria`.
* Add UI section for "Lieblingsorte" (`known_locations_title`):
  - Renders `FlowRow` of `FilterChip`s for each `KnownLocationItem`.
  - Tapping toggles selection.
* Add UI section for "Lieblingsstrecken" (`my_locations`):
  - Renders `FlowRow` of `FilterChip`s for each `WorkoutCluster`.
  - Tapping toggles selection.
* In `onClearAll`, reset location and cluster local states.
* In `onApply`, include `startLocation*` and `cluster*` in `criteria.copy(...)`.

### Component 3: Cluster Filter Bottom Sheet (`ClusterFilterBottomSheet.kt`)
* Add parameter `knownLocations: List<KnownLocationItem> = emptyList()`.
* Introduce local state for `localStartLocationLat`, `localStartLocationLng`, `localStartLocationName`, `localStartLocationRadiusM`.
* Add UI section for "Lieblingsorte" (`known_locations_title`):
  - Renders `FlowRow` of `FilterChip`s for each `KnownLocationItem`.
  - Tapping toggles selection.
* In `onClearAll`, reset location local state.
* In `onApply`, include `startLocation*` in `criteria.copy(...)`.

### Component 4: Active Filter Chips Rows
* In `ActiveFilterChipsRow.kt` (workouts):
  - Render `"🗺️ ${criteria.clusterName ?: stringResource(R.string.my_locations)}"` chip when `clusterId != null || !clusterName.isNullOrBlank()`.
  - Add parameter `onRemoveCluster: () -> Unit = {}`.
* In `ActiveClusterFilterChipsRow.kt` (clusters):
  - Render `"📍 ${criteria.startLocationName ?: stringResource(R.string.filter_start_location)}"` chip when `(startLocationLat != null && startLocationLng != null) || !startLocationName.isNullOrBlank()`.
  - Add parameter `onRemoveStartLocation: () -> Unit = {}`.

### Component 5: ViewModel & Screen Integration
* `WorkoutSummariesViewModel.kt`:
  - Expose `val knownLocations: StateFlow<List<KnownLocationItem>> = knownLocationsRepo.locationsFlow`.
  - Expose `val availableClusters: StateFlow<List<WorkoutCluster>> = clusterRepo.allClusters`.
* `WorkoutSummariesTabbedScreen.kt` & `WorkoutTabsScreen.kt`:
  - Collect `knownLocations` and `availableClusters` from ViewModel and pass to `WorkoutFilterBottomSheet`.
  - Pass `onRemoveCluster = { onUpdateFilterCriteria { it.copy(clusterId = null, clusterName = null) } }` to `ActiveFilterChipsRow`.
* `WorkoutClustersViewModel.kt`:
  - Expose `val knownLocations: StateFlow<List<KnownLocationItem>> = knownLocationsRepo.locationsFlow`.
* `WorkoutClustersTabsScreen.kt`:
  - Collect `knownLocations` from ViewModel and pass to `ClusterFilterBottomSheet`.
  - Pass `onRemoveStartLocation = { viewModel.updateFilterCriteria { it.copy(startLocationName = null, startLocationLat = null, startLocationLng = null, startLocationRadiusM = null) } }` to `ActiveClusterFilterChipsRow`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Extend `WorkoutFilterCriteria.kt`
* Add `clusterId` and `clusterName` to data class.
* Update `activeFilterCount`, `matches`, `toJson`, and `fromJson`.
* Add unit test: `WorkoutFilterCriteriaClusterTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteriaClusterTest"`

### Step 2: Extend `ActiveFilterChipsRow.kt` and `ActiveClusterFilterChipsRow.kt`
* Add cluster chip rendering and `onRemoveCluster` callback in `ActiveFilterChipsRow.kt`.
* Add start location chip rendering and `onRemoveStartLocation` callback in `ActiveClusterFilterChipsRow.kt`.
* Add unit tests: `ActiveFilterChipsRowClusterTest.kt` and `ActiveClusterFilterChipsRowLocationTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "*Active*FilterChipsRow*Test"`

### Step 3: Extend `WorkoutFilterBottomSheet.kt` and `ClusterFilterBottomSheet.kt`
* Add `knownLocations` and `availableClusters` sections and chips in `WorkoutFilterBottomSheet.kt`.
* Add `knownLocations` section and chips in `ClusterFilterBottomSheet.kt`.
* Add unit tests: `WorkoutFilterBottomSheetLocationClusterTest.kt` and `ClusterFilterBottomSheetLocationTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "*FilterBottomSheet*Test"`

### Step 4: Wire ViewModels and Tabbed Screens
* Update `WorkoutSummariesViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutTabsScreen.kt`.
* Update `WorkoutClustersViewModel.kt`, `WorkoutClustersTabsScreen.kt`.

### Step 5: Execute Full Regression Test Suite
* Run targeted tests and verify 100% pass rate.
* Command: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests in Steps 1–4.
  - Full clean-room test suite run in Step 5 (`./gradlew testDebugUnitTest`).
  - Automated Gate 4 and Gate 5 review audits.
* **Rollback Strategy**:
  - Work is strictly isolated on branch `feature/ATT-1593`.
  - In case of failure or regression, the branch can be reset or abandoned with zero side effects on `sprint/2026-40.4`.
