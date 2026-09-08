# Implementation Plan: Filter Favorite Tracks / Lieblingsstrecken (ATT-737)

* **Parent Ticket**: [ATT-737](https://rainerblind.atlassian.net/browse/ATT-737) ([Feature] Filter Lieblingsstrecken)
* **Sub-Task**: [ATT-764](https://rainerblind.atlassian.net/browse/ATT-764) ([Impl-Plan] Filter Lieblingsstrecken)
* **Target Lösungsversion (Fix Version/s)**: `V4.9.36`
* **Requirement**: `REQ-UI-135` (*Multi-Dimensional Favorite Tracks Filtering, Shared Modal Filter Sheet & Persistent Filter State*)
* **Test Specification**: `TST-UI-088` (*Multi-Dimensional Favorite Tracks Filtering, Shared Modal Sheet & Persistence Verification*)
* **Branch**: `feature/ATT-737`

---

## 1. Executive Summary & Design Overview

This plan specifies the implementation of multi-dimensional filtering for Favorite Tracks / Workout Clusters in `WorkoutClustersTabsScreen.kt` / `WorkoutClustersFragment.kt`. It strictly adopts the unified design system established in ATT-736 (Routes) and ATT-735 (Segments), reusing shared UI infrastructure (`FilterBottomSheetScaffold`, `FilterActionButton`, `RemovableFilterChip`), persisting criteria in `DataStore` via `MyPreferenceManager`, integrating reactive state filtering into `WorkoutClustersViewModel`, and resetting active criteria upon navigating away from Favorite Tracks.

---

## 2. Component Architecture & Code Sharing

### 2.1 Reused Shared Components (`com.atrainingtracker.trainingtracker.ui.common.filters`)
* **`FilterBottomSheetScaffold.kt`**: Outer modal bottom sheet shell providing standardized header row (title + dismiss button), dividers, scrollable content slot, and sticky bottom split action bar ("Clear all" / "OK") anchored flush to the bottom edge.
* **`FilterActionButton.kt`**: Material funnel icon (`Icons.Default.FilterAlt`) with badge counter indicating the number of active filter dimensions.
* **`RemovableFilterChip.kt`**: Removable chip with trailing `✕` icon for single-tap dimension removal directly below tabs.

### 2.2 New Cluster-Specific Files (to be CREATED)
1. **`ClusterFilterCriteria.kt`** (`com.atrainingtracker.trainingtracker.ui.clusters`):
   * `@Immutable data class ClusterFilterCriteria(...)`:
     * `query: String = ""` (free-text match on cluster `name`)
     * `equipmentName: String? = null` (gear name linked via cluster's sport)
     * `minDistanceMeters: Double? = null` (10km, 25km, 50km, 100km thresholds)
     * `minHitCount: Int? = null` (≥ 3, 5, 10, 25 recording thresholds)
   * `activeFilterCount: Int`: Count of non-empty / non-null dimensions.
   * `isEmpty: Boolean`, `isNotEmpty: Boolean`.
   * `matches(cluster: WorkoutCluster, linkedEquipment: Set<String>): Boolean`: High-performance predicate evaluation with sequential short-circuiting.
   * `toJson(): String` and `fromJson(jsonStr: String?): ClusterFilterCriteria` for lightweight JSON persistence.

2. **`ClusterFilterBottomSheet.kt`** (`com.atrainingtracker.trainingtracker.ui.clusters`):
   * Modal bottom sheet composable wrapping `FilterBottomSheetScaffold`.
   * Displays 4 sections:
     1. Text Search Input: `OutlinedTextField` with leading search icon and trailing clear button.
     2. Equipment Selection: `FlowRow` of `FilterChip`s for all distinct equipment names found across clusters.
     3. Distance Thresholds: `FlowRow` of `FilterChip`s for 10km, 25km, 50km, 100km.
     4. Recordings Count Thresholds: `FlowRow` of `FilterChip`s for ≥ 3, ≥ 5, ≥ 10, ≥ 25 recordings.
   * "Clear all" resets local state and invokes `onClearAll()`; "OK" / "Apply" compiles `ClusterFilterCriteria`, emits via `onApplyCriteria()`, and dismisses sheet.

3. **`ActiveClusterFilterChipsRow.kt`** (`com.atrainingtracker.trainingtracker.ui.clusters`):
   * Horizontal `LazyRow` displaying `RemovableFilterChip` for each active filter dimension.
   * Renders "Clear all" `TextButton` when `criteria.activeFilterCount >= 2`.
   * Positioned directly below the primary sport tabs with 40.dp height.

4. **`ClusterFilterCriteriaTest.kt`** (`com.atrainingtracker.trainingtracker.ui.clusters`):
   * Automated unit tests verifying predicate evaluation across all dimensions, equipment matching, thresholds, multi-dimension AND conjunction, active count, and JSON serialization roundtrip.

---

## 3. Modifications to Existing Files

### 3.1 `MyPreferenceManager.kt`
* Add key: `val CLUSTER_FILTER_CRITERIA_JSON = stringPreferencesKey("cluster_filter_criteria_json")`.
* Add flow: `val clusterFilterCriteriaFlow: Flow<ClusterFilterCriteria> = dataStore.data.map { preferences -> ClusterFilterCriteria.fromJson(preferences[CLUSTER_FILTER_CRITERIA_JSON]) }`.
* Add setter: `suspend fun setClusterFilterCriteria(criteria: ClusterFilterCriteria)`.
* Add clearer: `fun clearClusterFilterCriteria()` (fire-and-forget via `appScope`).

### 3.2 `MainActivityWithNavigation.kt`
* In `handleIntent()` back gesture / finish block:
  ```kotlin
  if (mSelectedFragmentId == R.id.drawer_my_locations) {
      MyPreferenceManager(applicationContext).clearClusterFilterCriteria()
  }
  ```
* In `navigateToDrawerItem()` drawer item transition block:
  ```kotlin
  if (itemId == R.id.drawer_start_tracking || (mSelectedFragmentId == R.id.drawer_my_locations && itemId != R.id.drawer_my_locations)) {
      MyPreferenceManager(applicationContext).clearClusterFilterCriteria()
  }
  ```

### 3.3 `WorkoutClustersViewModel.kt`
* Expose `filterCriteria: StateFlow<ClusterFilterCriteria>` sourced from `preferenceManager.clusterFilterCriteriaFlow`.
* Expose helper functions:
  * `setFilterCriteria(criteria: ClusterFilterCriteria)`
  * `clearFilterCriteria()`
  * `updateFilterCriteria(transform: (ClusterFilterCriteria) -> ClusterFilterCriteria)`
  * `getAvailableEquipment(): StateFlow<List<String>>` (computes all distinct equipment linked across clusters in memory).
* Expose `filteredClusters: StateFlow<List<WorkoutCluster>>` or provide cluster filtering helper for `WorkoutClustersTabsScreen`.

### 3.4 `WorkoutClustersTabsScreen.kt`
* Integrate `FilterActionButton` in the top header row next to the tuning settings icon.
* Integrate `showFilterBottomSheet` state to display `ClusterFilterBottomSheet`.
* Integrate `ActiveClusterFilterChipsRow` below `PrimaryScrollableTabRow` when `filterCriteria.isNotEmpty`.
* Account for chips row height (40.dp) in `headerHeightDp` and `connection.appBarOffset`.
* Apply `criteria.matches(cluster, linkedEquipment)` in `HorizontalPager` before displaying items.
* Display `filter_no_matching_clusters` empty state message when filtered results are empty.

### 3.5 String Resources (All 9 Locales)
Add 5 new keys across `values/strings.xml`, `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, `values-it/strings.xml`, `values-ja/strings.xml`, `values-nl/strings.xml`, `values-pl/strings.xml`, `values-pt/strings.xml`:
* `filter_clusters_title`: Filter Favorite Tracks
* `filter_search_clusters_hint`: Search track name…
* `filter_section_recordings`: Recordings
* `filter_min_recordings_chip_format`: ≥ %1$d recordings
* `filter_no_matching_clusters`: No favorite tracks match the active filters

---

## 4. System Invariants & Regression Prevention

* **Clustering Engine Untouched**: `WorkoutClusterEngine`, `WorkoutClusterRepository`, and `WorkoutClusterDatabaseManager` remain completely unmodified.
* **Tuning Parameters Untouched**: Master slider and detail tolerances operate independently.
* **Map & Detail Untouched**: Heatmap display, peak markers, track generation, and member workout inspection remain intact.
* **Shared Component Integrity**: `FilterBottomSheetScaffold`, `FilterActionButton`, and `RemovableFilterChip` remain untouched.
* **Navigation Drawer Invariant**: Clear on navigate matches route and workout filtering standards.

---

## 5. Verification Plan

1. **Unit Tests**:
   * Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteriaTest"`.
2. **Translation Parity Test**:
   * Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`.
3. **Full Build & Test Suite**:
   * Execute `./gradlew testDebugUnitTest`.
4. **Manual / Interactive Verification**:
   * Verify funnel icon, badge count, sheet opening, section selection, active chips removal, and navigation reset.
