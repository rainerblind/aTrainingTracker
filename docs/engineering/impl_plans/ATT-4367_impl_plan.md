# Stage 3: Implementation Plan - ATT-4367: Support elevation gain interval filtering (min/max range and preset intervals) in Routes, Favorite Routes, and Workouts

**Ticket**: [[ATT-4367]](https://rainerblind.atlassian.net/browse/ATT-4367)  
**Sub-task**: [[ATT-4419]](https://rainerblind.atlassian.net/browse/ATT-4419) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-425` (*Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) across Routes, Favorite Routes / Workout Clusters, and Workouts*)  
**Test Mapping**: `TST-UI-389` (*Elevation Gain Interval Filtering Verification Suite*)  
**Branch**: `feature/ATT-4367`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During Sprint 2026-43.1 Joint Review of ticket ATT-4291 (*Support distance interval filtering in Routes and Favorite Routes*), the user requested extending bounded interval filtering to **Elevation Gain (Höhenmeter)** across the application's three primary collection and navigation surfaces:
1. **Routes** (`RouteFilterCriteria`, `RouteFilterBottomSheet`, `ActiveRouteFilterChipsRow`, `RouteTabbedScreen`)
2. **Favorite Routes / Workout Clusters** (`ClusterFilterCriteria`, `ClusterFilterBottomSheet`, `ActiveClusterFilterChipsRow`, `WorkoutClustersTabsScreen`)
3. **Workouts** (`WorkoutFilterCriteria`, `WorkoutFilterBottomSheet`, `ActiveFilterChipsRow`, `WorkoutTabsScreen`)

### Architectural Deficits Identified:
* **Routes**: Only supported single-sided lower bounds (`minElevationGainMeters`) with 4 hardcoded chips (`> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`). It lacked `maxElevationGainMeters`, custom text entry, and interval active chip formatting.
* **Favorite Routes (Clusters)**: Completely lacked elevation gain filtering, despite having distance intervals and despite `WorkoutClusterStats` computing `avgAscentMeters`.
* **Workouts**: Completely lacked elevation gain filtering, despite having distance and duration intervals and despite `WorkoutData` recording authoritative `ascentMeters: Long`.
* **Active Filter Chip Strips**: Only Routes had a chip (`> X m`), clearing only the minimum bound; Clusters and Workouts had no elevation chips.

This plan details the step-by-step implementation to achieve complete triad parity across domain models, bottom sheets, active chip rows, and host screens.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-425` (*Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) across Routes, Favorite Routes / Workout Clusters, and Workouts*)
* **Test Mapping**: `TST-UI-389` (*Elevation Gain Interval Filtering Verification Suite across Routes, Favorite Routes, and Workouts*)
  * `TST-UI-389.1`: Route Elevation Gain Interval Matching & Active Count (`RouteFilterCriteriaTest.kt`)
  * `TST-UI-389.2`: Route Criteria JSON Roundtrip & Backward Compatibility (`RouteFilterCriteriaTest.kt`)
  * `TST-UI-389.3`: Cluster Elevation Gain Interval Matching & Active Count (`ClusterFilterCriteriaTest.kt`)
  * `TST-UI-389.4`: Cluster Criteria JSON Roundtrip & Backward Compatibility (`ClusterFilterCriteriaTest.kt`)
  * `TST-UI-389.5`: Workout Elevation Gain Interval Matching & Active Count (`WorkoutFilterCriteriaTest.kt`)
  * `TST-UI-389.6`: Workout Criteria JSON Roundtrip & Backward Compatibility (`WorkoutFilterCriteriaTest.kt`)
  * `TST-UI-389.7`: Active Filter Chip Strips Elevation Formatting & Dismissal Contract
  * `TST-UI-389.8`: 9-Language Localization & Format Specifier Parity (`TranslationParityTest.kt`)
  * `TST-UI-389.9`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**:
   - Routes: Search query, origin source, selection flag, and distance intervals remain 100% operational.
   - Clusters: Search query, equipment, distance intervals, hit counts, and start location geofencing remain 100% operational.
   - Workouts: Query, year/month/dates, sport sub-types, equipment, commute/trainer/race/GPS flags, distance intervals, duration intervals, start locations, and sources remain 100% operational.
2. **Database Schema Immutability**:
   - Zero SQLite database schema alterations across `Routes.db`, `RouteClusters.db`, and `TrainingTracker.db`. Filtering is evaluated purely in memory on domain DTOs within reactive ViewModel flows.
3. **DataStore Preference Backward Compatibility**:
   - Missing JSON properties during deserialization safely resolve to `null` without throwing `JSONException` or resetting existing filter state.
4. **Thread Safety & Dispatcher Affinity**:
   - ViewModel state flows remain on `viewModelScope` with `WhileSubscribed(5000)`. Predicates are evaluated synchronously in memory.
5. **Parent Human Gate Invariance (Rule 3)**:
   - Terminal completion of parent ticket ATT-4367 remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: 9-Language Localization String Resources (`res/values*/strings.xml`)
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  - Add `filter_max_elevation_format`: `&lt; %1$s m`
  - Add `filter_elevation_interval_format`: `%1$s - %2$s m`
  - Note: `filter_min_elevation_format` (`&gt; %1$s m`) and `sort_elevation_gain` ("Elevation Gain" / "Höhenmeter") are already defined in all 9 locales.

### Component 2: Route Filtering Surface (`ui/routes/`)
* **`RouteFilterCriteria.kt`**:
  - Add parameter `val maxElevationGainMeters: Double? = null`.
  - Update `activeFilterCount`: count elevation as 1 dimension if `minElevationGainMeters != null || maxElevationGainMeters != null`.
  - Update `matches(route: RouteWithPath)`:
    ```kotlin
    if (minElevationGainMeters != null && route.summary.elevationGain < minElevationGainMeters) return false
    if (maxElevationGainMeters != null && route.summary.elevationGain > maxElevationGainMeters) return false
    ```
  - Update `toJson()` and `fromJson()`: serialize/deserialize `maxElevationGainMeters` safely.
* **`RouteFilterBottomSheet.kt`**:
  - Add local states: `localMaxElevationGainMeters`, `minElevationText`, `maxElevationText`.
  - Elevation Gain Section:
    - 5 preset interval chips: `< 100 m`, `100 - 250 m`, `250 - 500 m`, `500 - 1000 m`, `> 1000 m`.
    - Two-way synchronized `OutlinedTextField` inputs for `Min (m)` and `Max (m)` (`KeyboardType.Number`).
    - Preset click updates text and numeric states; text editing updates numeric states and clears mismatched preset selections.
    - `onClearAll` resets both elevation bounds and text states to empty.
* **`ActiveRouteFilterChipsRow.kt` & `RouteTabbedScreen.kt`**:
  - Render a unified removable chip when `criteria.minElevationGainMeters != null || criteria.maxElevationGainMeters != null`.
  - Format: interval (`"$min - $max m"`), min only (`"> $min m"`), max only (`"< $max m"`).
  - Callback: `onRemoveElevationRange: () -> Unit` (with `onRemoveMinElevation = onRemoveElevationRange` for backward compatibility).
  - `RouteTabbedScreen.kt`: wire `onRemoveElevationRange` to reset both `minElevationGainMeters = null, maxElevationGainMeters = null`.

### Component 3: Favorite Routes / Workout Clusters Filtering Surface (`ui/clusters/`)
* **`ClusterFilterCriteria.kt`**:
  - Add parameters `val minElevationGainMeters: Double? = null, val maxElevationGainMeters: Double? = null`.
  - Update `activeFilterCount`: count elevation as 1 dimension if either/both bounds are set.
  - Update `matches(cluster: WorkoutCluster, linkedEquipment: Set<String> = emptySet(), elevationGainMeters: Double? = null)`:
    ```kotlin
    if (minElevationGainMeters != null || maxElevationGainMeters != null) {
        val elev = elevationGainMeters ?: 0.0
        if (minElevationGainMeters != null && elev < minElevationGainMeters) return false
        if (maxElevationGainMeters != null && elev > maxElevationGainMeters) return false
    }
    ```
  - Update `toJson()` and `fromJson()` to serialize and deserialize `minElevationGainMeters` and `maxElevationGainMeters`.
* **`ClusterFilterBottomSheet.kt`**:
  - Add local states: `localMinElevationGainMeters`, `localMaxElevationGainMeters`, `minElevationText`, `maxElevationText`.
  - Add **Elevation Gain Section** (`R.string.sort_elevation_gain`) with the identical 5 preset chips and synchronized `Min (m)` / `Max (m)` fields.
  - Include elevation bounds in `onClearAll` and `onApply`.
* **`ActiveClusterFilterChipsRow.kt` & `WorkoutClustersTabsScreen.kt`**:
  - Render unified removable elevation chip when `minElevationGainMeters != null || maxElevationGainMeters != null`.
  - Provide callback: `onRemoveElevationRange: () -> Unit = {}`.
  - In `WorkoutClustersTabsScreen.kt`:
    - Collect `val clusterStats by viewModel.clusterStats.collectAsState()`.
    - Pass `clusterStats[cluster.id]?.avgAscentMeters?.toDouble()` to `filterCriteria.matches(...)`.
    - Wire `onRemoveElevationRange = { viewModel.updateFilterCriteria { it.copy(minElevationGainMeters = null, maxElevationGainMeters = null) } }`.

### Component 4: Workouts Filtering Surface (`ui/aftermath/workoutlist/`)
* **`WorkoutFilterCriteria.kt`**:
  - Add parameters `val minElevationGainMeters: Double? = null, val maxElevationGainMeters: Double? = null`.
  - Update `activeFilterCount`: count elevation as 1 dimension if either/both bounds are set.
  - Update `matches(workout: WorkoutData)`:
    ```kotlin
    if (minElevationGainMeters != null && workout.ascentMeters < minElevationGainMeters) return false
    if (maxElevationGainMeters != null && workout.ascentMeters > maxElevationGainMeters) return false
    ```
  - Update `toJson()` and `fromJson()` to serialize and deserialize `minElevationGainMeters` and `maxElevationGainMeters`.
* **`WorkoutFilterBottomSheet.kt`**:
  - Add local states: `localMinElevationGainMeters`, `localMaxElevationGainMeters`, `minElevationText`, `maxElevationText`.
  - Add **Elevation Gain Section** (`R.string.sort_elevation_gain`) directly following Duration Interval (Section 7), with the identical 5 preset chips and synchronized `Min (m)` / `Max (m)` text fields.
  - Include elevation bounds in `onClearAll` and `onApply`.
* **`ActiveFilterChipsRow.kt` & `WorkoutTabsScreen.kt`**:
  - Render unified removable elevation chip when `minElevationGainMeters != null || maxElevationGainMeters != null`.
  - Provide callback: `onRemoveElevationRange: () -> Unit = {}`.
  - In `WorkoutTabsScreen.kt`:
    - Wire `onRemoveElevationRange = { onUpdateFilterCriteria { it.copy(minElevationGainMeters = null, maxElevationGainMeters = null) } }`.

### UI Consistency (Rule 23 — mandatory)
* **Reference screen / component**:
  - `WorkoutFilterBottomSheet.kt` Distance Interval (Section 6) and `RouteFilterBottomSheet.kt` Distance Interval (Section 4, from ATT-4291).
* **Reused components**:
  - `FilterBottomSheetScaffold` (`ui/common/filters/FilterBottomSheetScaffold.kt`)
  - `RemovableFilterChip` (`ui/common/filters/RemovableFilterChip.kt`)
  - `FilterChip` (Material 3)
  - `OutlinedTextField` with `KeyboardOptions(keyboardType = KeyboardType.Number)`
* **Theme tokens**:
  - Heading style: `MaterialTheme.typography.titleMedium` with `MaterialTheme.colorScheme.primary`
  - Spacing: 6.dp below headers, 8.dp horizontal and 4.dp vertical between chips, 12.dp between Min and Max text fields
  - Shape: Default Material 3 chip and text field rounded corners
* **New one-off styles & justification**: None. 100% strict reuse of the established distance interval design pattern.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization String Resources
* **Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  - Add `<string name="filter_max_elevation_format">&lt; %1$s m</string>`
  - Add `<string name="filter_elevation_interval_format">%1$s - %2$s m</string>`

### Step 2: Route Filtering Surface
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteria.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ActiveRouteFilterChipsRow.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedScreen.kt`
* **Changes**:
  - `RouteFilterCriteria`: add `maxElevationGainMeters: Double? = null`, update `activeFilterCount`, update `matches`, update `toJson` / `fromJson`.
  - `RouteFilterBottomSheet`: convert Section 5 to 5 preset chips + `Min (m)` / `Max (m)` inputs with two-way sync.
  - `ActiveRouteFilterChipsRow`: render interval-formatted chip, wire `onRemoveElevationRange`.
  - `RouteTabbedScreen`: wire `onRemoveElevationRange` to clear both bounds.

### Step 3: Favorite Routes / Workout Clusters Filtering Surface
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteria.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt`
* **Changes**:
  - `ClusterFilterCriteria`: add `minElevationGainMeters`, `maxElevationGainMeters`, update `activeFilterCount`, update `matches(cluster, linkedEquipment, elevationGainMeters)`, update `toJson` / `fromJson`.
  - `ClusterFilterBottomSheet`: add Section for Elevation Gain with 5 preset chips and `Min (m)` / `Max (m)` inputs with two-way sync.
  - `ActiveClusterFilterChipsRow`: render interval-formatted chip, wire `onRemoveElevationRange`.
  - `WorkoutClustersTabsScreen`: collect `clusterStats`, pass `avgAscentMeters` to `matches`, wire `onRemoveElevationRange`.

### Step 4: Workouts Filtering Surface
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteria.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt`
* **Changes**:
  - `WorkoutFilterCriteria`: add `minElevationGainMeters`, `maxElevationGainMeters`, update `activeFilterCount`, update `matches(workout)`, update `toJson` / `fromJson`.
  - `WorkoutFilterBottomSheet`: add Section for Elevation Gain following Duration, with 5 preset chips and `Min (m)` / `Max (m)` inputs with two-way sync.
  - `ActiveFilterChipsRow`: render interval-formatted chip, wire `onRemoveElevationRange`.
  - `WorkoutTabsScreen`: wire `onRemoveElevationRange` to clear both bounds.

### Step 5: Unit Tests Authoring & Updates
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaTest.kt`
* **Changes**:
  - Add test methods for `TST-UI-389.1` through `TST-UI-389.7` verifying interval matching, upper-bound only, lower-bound only, activeFilterCount derivation, JSON roundtrips, and legacy backward compatibility across all three models.

### Step 6: Targeted Verification Commands
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteFilterCriteriaTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteriaTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteriaTest"
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted test commands in Step 6 after Stage 4 construction.
  - Run full suite `./gradlew testDebugUnitTest` in Stage 5 clean-room regression.
  - Verify requirement governance script: `python3 tools/verify_requirement_governance.py`.
* **Rollback**:
  - The feature branch `feature/ATT-4367` is completely isolated from `develop`. In the event of unforeseen failure, the branch can be reset or abandoned without impacting release or main lines.
