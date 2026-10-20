# Stage 2: Requirement & Test Specification - ATT-4367: Support elevation gain interval filtering (min/max range and preset intervals) in Routes, Favorite Routes, and Workouts

**Ticket**: [ATT-4367](https://rainerblind.atlassian.net/browse/ATT-4367)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-425` (*Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) across Routes, Favorite Routes / Workout Clusters, and Workouts*)  
**Test Spec ID**: `TST-UI-389`  
**Branch**: `feature/ATT-4367`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-UI-425)

### 1.1 Problem Statement & Rationale

During the Sprint 2026-43.1 Joint Review of ticket ATT-4291 (*Support distance interval filtering in Routes and Favorite Routes*), the user requested extending bounded interval filtering to **Elevation Gain (Höhenmeter)** across the application's three core route and activity surfaces:
1. **Routes** (`RouteFilterCriteria`, `RouteFilterBottomSheet`, `ActiveRouteFilterChipsRow`, `RouteTabbedScreen`)
2. **Favorite Routes / Workout Clusters** (`ClusterFilterCriteria`, `ClusterFilterBottomSheet`, `ActiveClusterFilterChipsRow`, `WorkoutClustersTabsScreen`)
3. **Workouts** (`WorkoutFilterCriteria`, `WorkoutFilterBottomSheet`, `ActiveFilterChipsRow`, `WorkoutTabsScreen`)

Currently, significant feature disparities and UX inconsistencies exist between these screens:
* **Routes (`RouteFilterCriteria` / `RouteFilterBottomSheet`)**:
  * Currently supports only a single-sided lower-bound threshold: `val minElevationGainMeters: Double? = null`.
  * The bottom sheet offers only 4 hardcoded single-sided chips: `> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`.
  * There is no upper-bound parameter (`maxElevationGainMeters`) and no custom numeric input fields (`Min (m)` / `Max (m)`). An athlete looking for a flat or rolling ride (e.g., `< 250 m` or `100 - 250 m` for recovery) cannot filter out steep mountain routes.
* **Favorite Routes / Workout Clusters (`ClusterFilterCriteria` / `ClusterFilterBottomSheet`)**:
  * Currently completely lacks elevation gain filtering, despite having distance interval filtering (`minDistanceMeters` / `maxDistanceMeters`, introduced in ATT-4291) and despite the underlying engine computing `avgAscentMeters` in `WorkoutClusterStats`.
* **Workouts (`WorkoutFilterCriteria` / `WorkoutFilterBottomSheet`)**:
  * While workout filtering has mature distance and duration intervals (`minDistanceMeters` / `maxDistanceMeters` and `minDurationSec` / `maxDurationSec` from `REQ-UI-157`), it has **no elevation gain filter criteria at all**, despite `WorkoutData` recording authoritative `ascentMeters: Long`.
* **Active Filter Chip Strips**:
  * `ActiveRouteFilterChipsRow` only renders single-sided `> X m` chips and clears only `minElevationGainMeters`.
  * `ActiveClusterFilterChipsRow` and `ActiveFilterChipsRow` do not support elevation chips at all.

This specification establishes complete architectural and UX harmonization across all three screens by extending domain criteria, modal filter sheets, and active chip strips with bounded elevation gain interval filtering (`[minElevationGainMeters, maxElevationGainMeters]`), canonical preset chips, and two-way synchronized custom `Min (m)` / `Max (m)` input fields.

### 1.2 Functional & Architectural Requirements

1. **Domain Model Extensions (`RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`, `WorkoutFilterCriteria.kt`)**:
   - `RouteFilterCriteria` SHALL declare `val maxElevationGainMeters: Double? = null` alongside existing `val minElevationGainMeters: Double? = null`.
   - `ClusterFilterCriteria` SHALL declare `val minElevationGainMeters: Double? = null` and `val maxElevationGainMeters: Double? = null`.
   - `WorkoutFilterCriteria` SHALL declare `val minElevationGainMeters: Double? = null` and `val maxElevationGainMeters: Double? = null`.
   - `activeFilterCount` in all three models SHALL treat elevation gain as a single active filter dimension if and only if `minElevationGainMeters != null || maxElevationGainMeters != null`. Setting both bounds SHALL NOT increment `activeFilterCount` by more than 1.
   - `RouteFilterCriteria.matches(route: RouteWithPath)` SHALL evaluate:
     - `if (minElevationGainMeters != null && route.summary.elevationGain < minElevationGainMeters) return false`
     - `if (maxElevationGainMeters != null && route.summary.elevationGain > maxElevationGainMeters) return false`
   - `ClusterFilterCriteria.matches(cluster: WorkoutCluster, linkedEquipment: Set<String> = emptySet(), elevationGainMeters: Double? = null)` SHALL evaluate:
     - `if (minElevationGainMeters != null || maxElevationGainMeters != null)`:
       - `val elev = elevationGainMeters ?: 0.0`
       - `if (minElevationGainMeters != null && elev < minElevationGainMeters) return false`
       - `if (maxElevationGainMeters != null && elev > maxElevationGainMeters) return false`
   - `WorkoutFilterCriteria.matches(workout: WorkoutData)` SHALL evaluate:
     - `if (minElevationGainMeters != null && workout.ascentMeters < minElevationGainMeters) return false`
     - `if (maxElevationGainMeters != null && workout.ascentMeters > maxElevationGainMeters) return false`
   - Serialization & DataStore Persistence:
     - `toJson()` across all three models SHALL serialize `minElevationGainMeters` and `maxElevationGainMeters` as JSON numbers when non-null.
     - `fromJson()` across all three models SHALL deserialize `minElevationGainMeters` and `maxElevationGainMeters` safely if present. Legacy JSON records lacking these fields SHALL resolve cleanly to `null` without throwing exceptions or corrupting existing filter state.

2. **Modal Filter Bottom Sheets (`RouteFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `WorkoutFilterBottomSheet.kt`)**:
   - In all three bottom sheets, the **Elevation Gain Section** SHALL render under header `R.string.sort_elevation_gain` ("Elevation Gain" / "Höhenmeter").
   - **Canonical 5 Preset Chips**:
     - `< 100 m` (`min = null, max = 100.0`): Flat terrain / recovery rides
     - `100 - 250 m` (`min = 100.0, max = 250.0`): Gently undulating terrain
     - `250 - 500 m` (`min = 250.0, max = 500.0`): Rolling hills / moderate climbs
     - `500 - 1000 m` (`min = 500.0, max = 1000.0`): Significant hills / mountain stages
     - `> 1000 m` (`min = 1000.0, max = null`): High-altitude epic climbs
   - **Custom Integer Inputs**:
     - Two equal-weight `OutlinedTextField`s for `Min (m)` and `Max (m)` using `label = { Text("${stringResource(R.string.filter_min_label)} (m)") }` and `${stringResource(R.string.filter_max_label)} (m)` with `KeyboardOptions(keyboardType = KeyboardType.Number)`.
   - **Two-Way Synchronization**:
     - Selecting a preset chip populates both text fields with integer meter values (or empty for unbounded) and sets local numeric meter bounds.
     - Manual text editing updates numeric meter bounds (parsing integer meters) and deselects mismatched preset chips.
     - Clearing a field or deselecting an active preset resets the corresponding bound to `null`.
   - Reset All (`onClearAll`): resets `localMinElevationGainMeters = null`, `localMaxElevationGainMeters = null`, and both text fields to `""`.

3. **Active Filter Chip Strips (`ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveFilterChipsRow.kt`, Host Screens)**:
   - When `minElevationGainMeters != null || maxElevationGainMeters != null`, all three chip rows SHALL render a single removable chip:
     - Both bounds set: `R.string.filter_elevation_interval_format` ("%1$s - %2$s m") $\rightarrow$ e.g., `"250 - 500 m"`.
     - Min only: `R.string.filter_min_elevation_format` (">&gt; %1$s m") $\rightarrow$ e.g., `"> 500 m"`.
     - Max only: `R.string.filter_max_elevation_format` ("&lt; %1$s m") $\rightarrow$ e.g., `"< 100 m"`.
     - Whole meter numbers SHALL format cleanly without decimal places (e.g. `250` rather than `250.0`).
   - Chip removal callback:
     - `ActiveRouteFilterChipsRow`: provide `onRemoveElevationRange: () -> Unit` (defaulting `onRemoveMinElevation = onRemoveElevationRange` for backward compatibility). Tapping `✕` SHALL clear both `minElevationGainMeters = null` and `maxElevationGainMeters = null` reactively.
     - `ActiveClusterFilterChipsRow`: provide `onRemoveElevationRange: () -> Unit`. Tapping `✕` SHALL clear both elevation bounds.
     - `ActiveFilterChipsRow`: provide `onRemoveElevationRange: () -> Unit`. Tapping `✕` SHALL clear both elevation bounds.
   - Host screen wiring:
     - In `RouteTabbedScreen.kt`, wire `onRemoveElevationRange` to reset both elevation bounds in `RoutesViewModel`.
     - In `WorkoutClustersTabsScreen.kt`, wire `onRemoveElevationRange` to reset both elevation bounds in `WorkoutClustersViewModel`, and in the cluster filtering pipeline, pass `clusterStats[cluster.id]?.avgAscentMeters?.toDouble()` to `filterCriteria.matches(...)`.
     - In `WorkoutTabsScreen.kt`, wire `onRemoveElevationRange` to reset both elevation bounds in `WorkoutSummariesViewModel`.

4. **100% 9-Language Localization Parity**:
   - Existing string resources SHALL be reused:
     - `R.string.sort_elevation_gain`
     - `R.string.filter_min_label`
     - `R.string.filter_max_label`
     - `R.string.filter_min_elevation_format` (`&gt; %1$s m`)
   - Net-new string resources SHALL be defined across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with 100% format specifier parity:
     - `R.string.filter_max_elevation_format`: `&lt; %1$s m`
     - `R.string.filter_elevation_interval_format`: `%1$s - %2$s m`

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Preset Elevation Gain Interval Selection on Routes**
  * *Given* an athlete browsing the Routes list with `RouteFilterBottomSheet` open,
  * *When* the athlete taps the `250 - 500 m` preset chip and confirms with "OK",
  * *Then* `minElevationGainMeters` is set to `250.0` and `maxElevationGainMeters` is set to `500.0`,
  * *And* only routes with `250.0 <= elevationGain <= 500.0` are shown,
  * *And* `ActiveRouteFilterChipsRow` renders a single chip displaying `"250 - 500 m"`,
  * *And* the header filter badge increments the active dimension count by 1.

* **Criterion 2: Custom Elevation Gain Bounds on Favorite Tracks**
  * *Given* an athlete on the Favorite Tracks screen with `ClusterFilterBottomSheet` open,
  * *When* the athlete enters `Min = 200` and `Max = 400` into the custom elevation fields and taps "OK",
  * *Then* only clusters whose average ascent satisfies `200.0 <= avgAscentMeters <= 400.0` are displayed,
  * *And* `ActiveClusterFilterChipsRow` displays `"200 - 400 m"`.

* **Criterion 3: Elevation Gain Interval Selection on Workouts**
  * *Given* an athlete on the Workouts history screen with `WorkoutFilterBottomSheet` open,
  * *When* the athlete taps the `100 - 250 m` preset chip (or enters `Min = 100` and `Max = 250`) and confirms with "OK",
  * *Then* only workouts with `100L <= ascentMeters <= 250L` are displayed,
  * *And* `ActiveFilterChipsRow` renders `"100 - 250 m"`.

* **Criterion 4: Upper-Bound Only Selection**
  * *Given* the athlete selects the `< 100 m` preset chip (or enters only `Max = 100` with empty Min) on any filter sheet,
  * *When* applied,
  * *Then* `minElevationGainMeters` is `null` and `maxElevationGainMeters` is `100.0`,
  * *And* entities with ascent $\le 100\text{ m}$ are displayed,
  * *And* the active chip renders `"< 100 m"`.

* **Criterion 5: Lower-Bound Only Selection**
  * *Given* the athlete selects the `> 1000 m` preset chip (or enters only `Min = 1000` with empty Max) on any filter sheet,
  * *When* applied,
  * *Then* `minElevationGainMeters` is `1000.0` and `maxElevationGainMeters` is `null`,
  * *And* entities with ascent $\ge 1000\text{ m}$ are displayed,
  * *And* the active chip renders `"> 1000 m"`.

* **Criterion 6: Elevation Filter Chip Removal**
  * *Given* an active elevation interval filter applied on Routes, Favorite Tracks, or Workouts,
  * *When* the athlete taps the `✕` removal icon on the active elevation chip,
  * *Then* both `minElevationGainMeters` and `maxElevationGainMeters` are cleared (`null`),
  * *And* the elevation chip disappears,
  * *And* the list reactively displays all entities matching remaining active criteria.

* **Criterion 7: Lossless DataStore JSON Persistence Roundtrip & Legacy Defense**
  * *Given* `RouteFilterCriteria`, `ClusterFilterCriteria`, or `WorkoutFilterCriteria` with `minElevationGainMeters = 250.0` and `maxElevationGainMeters = 500.0`,
  * *When* serialized to JSON and deserialized via `fromJson()`,
  * *Then* both bounds are accurately restored,
  * *And* given a legacy JSON string without `maxElevationGainMeters` (or without elevation fields), `fromJson()` returns null bounds safely without throwing exceptions.

### 1.4 System Invariants

1. **Non-Regression on Existing Filter Dimensions**:
   - Routes: search query, route origin source (Strava, Local GPX, Workout), map selection flag (`isSelected`), and distance interval MUST remain 100% operational.
   - Favorite Tracks: cluster name query, linked equipment, distance interval, recordings / hit count threshold, and start location spatial geofence MUST remain 100% operational.
   - Workouts: query, temporal filters (year, month, dates), sport sub-types, equipment, flags (commute, trainer, race, GPS), distance intervals, duration intervals, start locations, cluster filters, and workout sources MUST remain 100% operational.
2. **Database Schema Immutability**:
   - Zero SQLite database schema alterations across `Routes.db`, `RouteClusters.db`, and `TrainingTracker.db`. Filtering is evaluated purely in memory on domain DTOs within reactive ViewModel flows.
3. **DataStore Backward Compatibility**:
   - Missing JSON properties during deserialization MUST safely default to `null` without throwing `JSONException` or resetting unrelated criteria.
4. **Visual & Design Consistency (Triad Baseline)**:
   - 1:1 parity with distance intervals (`REQ-UI-417` / `REQ-UI-157`). Preset chip layout (FlowRow), text field styling (`KeyboardType.Number`), and spacing MUST conform to `docs/design_guidelines.md` Sec 5.1 & 5.2.
5. **Parent Ticket Human Decision Gate (Rule 3)**:
   - Moving parent ticket ATT-4367 to `Erledigt` is strictly reserved for the human user in Ceremony 2.

### ### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Refines `REQ-UI-133` (*Multi-Dimensional Route Filtering, Shared Modal Filter Sheet & Persistent Filter State*), `REQ-UI-135` (*Multi-Dimensional Favorite Tracks (Lieblingsstrecken) Filtering, Shared Modal Filter Sheet & Persistent Filter State*), and `REQ-UI-132` / `REQ-UI-157` (*Multi-Dimensional Workout Filtering and Distance/Duration Intervals*), amending `REQ-UI-417` (*Distance Interval Filtering in Routes and Favorite Routes*).
   - Targets `RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`, `WorkoutFilterCriteria.kt`, `RouteFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `WorkoutFilterBottomSheet.kt`, `ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveFilterChipsRow.kt`, `RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutTabsScreen.kt`.
2. **Historical Origin & Commit Trace**:
   - Introduced in Sprint `2026-42.1` (`ATT-736`, commit `3c5132da` for routes; `ATT-737`, commit `472be081` for favorite tracks; `ATT-735`, commit `316d946f` for workouts).
   - Distance intervals were added to workouts in `ATT-761` (`REQ-UI-157`) and harmonized into Routes and Favorite Routes in Sprint `2026-43.1` (`ATT-4291`, commit `2818f65a`, `REQ-UI-417`).
   - Requested for Elevation Gain during Sprint 2026-43.1 Joint Review.
3. **Root Reason for Existing Formulation**:
   - When route filtering was originally authored, single-sided lower bounds (`minElevationGainMeters`) were added to prune flat test tracks, while upper bounds and custom numerical inputs were deferred.
   - Favorite Tracks (clusters) focused initially on 2D geographic clustering and deferred elevation filtering.
   - Workouts focused initially on duration and distance, omitting elevation gain under the assumption that sport sub-types and GPS flags sufficed.
   - These historic omissions produced an inconsistent filtering experience where distance had full bounded interval controls, but elevation gain was either single-sided or completely absent.
4. **Preservation of Core Invariants**:
   - All other filter criteria dimensions across Routes, Clusters, and Workouts remain 100% functional and backward compatible.
   - Legacy persisted preferences in `DataStore` lacking `maxElevationGainMeters` parse cleanly with `maxElevationGainMeters = null`.
   - Single-sided lower-bound thresholds continue to function identically when only `minElevationGainMeters` is set.
   - Predicate evaluations in `matches(...)` remain non-blocking, deterministic, and short-circuiting.
   - Zero SQLite migrations; 100% 9-language localization parity.

---

## 2. Test Specification (TST-UI-389)

### Test Case 1: Route Elevation Gain Interval Matching & Filter Count (`TST-UI-389.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`
* **Preconditions**: Instantiated routes with elevation gains: 50 m, 100 m, 250 m, 400 m, 500 m, 1200 m.
* **Action**:
  - Test bounded interval `[250 m, 500 m]` (`min = 250.0, max = 500.0`): assert 250 m, 400 m, 500 m match; 50 m, 100 m, 1200 m fail.
  - Test upper bound only `< 100 m` (`min = null, max = 100.0`): assert 50 m, 100 m match; 250 m, 400 m, 500 m, 1200 m fail.
  - Test lower bound only `> 500 m` (`min = 500.0, max = null`): assert 500 m, 1200 m match; 50 m, 100 m, 250 m, 400 m fail.
* **Expected Result**: Matching routes pass, non-matching routes fail, and `activeFilterCount` increments by strictly 1 whether min, max, or both bounds are set.

### Test Case 2: Route Criteria JSON Roundtrip & Backward Compatibility (`TST-UI-389.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`
* **Action**:
  - Serialize criteria with `minElevationGainMeters = 250.0, maxElevationGainMeters = 500.0` to JSON and deserialize via `fromJson()`.
  - Deserialize legacy JSON lacking `maxElevationGainMeters` (`{"query":"Alps","minElevationGainMeters":250.0}`).
* **Expected Result**: Roundtrip restores both bounds; legacy JSON safely resolves `maxElevationGainMeters = null` and preserves `minElevationGainMeters = 250.0`.

### Test Case 3: Cluster Elevation Gain Interval Matching & Filter Count (`TST-UI-389.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
* **Preconditions**: Instantiated clusters with resolved elevation gains: 50 m, 150 m, 250 m, 350 m, 500 m, 800 m.
* **Action**:
  - Test bounded interval `[100 m, 250 m]` (`min = 100.0, max = 250.0`): assert 150 m, 250 m match; 50 m, 350 m, 500 m, 800 m fail.
  - Test upper bound only `< 100 m`: assert 50 m matches; 150 m, 250 m fail.
  - Test lower bound only `> 500 m`: assert 500 m, 800 m match; 350 m fails.
  - Test `activeFilterCount`: assert setting min, max, or both contributes strictly 1 to active count.
* **Expected Result**: Elevation filtering works accurately on clusters and active count is 1.

### Test Case 4: Cluster Criteria JSON Roundtrip & Backward Compatibility (`TST-UI-389.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
* **Action**:
  - Serialize criteria with `minElevationGainMeters = 100.0, maxElevationGainMeters = 250.0` to JSON and deserialize.
  - Deserialize legacy JSON without elevation keys.
* **Expected Result**: Roundtrip preserves both bounds; legacy JSON parses with both bounds `null`.

### Test Case 5: Workout Elevation Gain Interval Matching & Filter Count (`TST-UI-389.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaTest.kt`
* **Preconditions**: Instantiated workouts with `ascentMeters`: 40L, 100L, 200L, 250L, 600L, 1200L.
* **Action**:
  - Test bounded interval `[100 m, 250 m]` (`min = 100.0, max = 250.0`): assert 100L, 200L, 250L match; 40L, 600L, 1200L fail.
  - Test upper bound only `< 100 m`: assert 40L, 100L match; 200L, 250L, 600L fail.
  - Test lower bound only `> 500 m`: assert 600L, 1200L match; 250L fails.
  - Test `activeFilterCount`: assert setting min, max, or both contributes strictly 1 to count.
* **Expected Result**: Workout ascent is correctly filtered and active filter count is accurate.

### Test Case 6: Workout Criteria JSON Roundtrip & Backward Compatibility (`TST-UI-389.6`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaTest.kt`
* **Action**:
  - Serialize criteria with `minElevationGainMeters = 250.0, maxElevationGainMeters = 500.0` to JSON and deserialize.
  - Deserialize legacy JSON lacking elevation keys.
* **Expected Result**: Roundtrip preserves both bounds; legacy JSON resolves both bounds to `null`.

### Test Case 7: Active Filter Chip Strips Elevation Formatting & Dismissal Contract (`TST-UI-389.7`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt` / `ActiveFilterChipsRowTest.kt`
* **Action**:
  - Verify formatting helper produces:
    - `"$min - $max m"` (e.g. `"250 - 500 m"`) when both bounds are non-null.
    - `"> $min m"` (e.g. `"> 500 m"`) when only min bound is non-null.
    - `"< $max m"` (e.g. `"< 100 m"`) when only max bound is non-null.
  - Verify dismissal contract: `criteria.copy(minElevationGainMeters = null, maxElevationGainMeters = null)` clears both bounds.
* **Expected Result**: Format strings match localized pattern and dismissal resets both bounds.

### Test Case 8: 9-Language Localization & Format Specifier Parity (`TST-UI-389.8`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**:
  - Verify that string tokens `filter_max_elevation_format` and `filter_elevation_interval_format` exist and are non-blank across all 9 canonical application locales:
    - EN (`values`), DE (`values-de`), ES (`values-es`), FR (`values-fr`), IT (`values-it`), JA (`values-ja`), NL (`values-nl`), PL (`values-pl`), PT (`values-pt`).
  - Verify exact format specifiers (`%1$s` for max format, `%1$s` and `%2$s` for interval format).
* **Expected Result**: 100% parity across all 9 locales with zero missing keys and zero specifier mismatches.

### Test Case 9: Clean-Room Full Suite Regression Execution (`TST-UI-389.9`)
* **Scope**: Clean-room Full Suite Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Execute the full unit test suite across all modules, asserting 100% pass rate with zero regressions.
* **Expected Result**: 100% tests pass.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-389.1` | Unit | `RouteFilterCriteria.matches` / `activeFilterCount` | `REQ-UI-425.1` | Specified |
| `TST-UI-389.2` | Unit | `RouteFilterCriteria.toJson` / `fromJson` | `REQ-UI-425.1` | Specified |
| `TST-UI-389.3` | Unit | `ClusterFilterCriteria.matches` / `activeFilterCount` | `REQ-UI-425.1` | Specified |
| `TST-UI-389.4` | Unit | `ClusterFilterCriteria.toJson` / `fromJson` | `REQ-UI-425.1` | Specified |
| `TST-UI-389.5` | Unit | `WorkoutFilterCriteria.matches` / `activeFilterCount` | `REQ-UI-425.1` | Specified |
| `TST-UI-389.6` | Unit | `WorkoutFilterCriteria.toJson` / `fromJson` | `REQ-UI-425.1` | Specified |
| `TST-UI-389.7` | Contract | Active chip formatting & `onRemoveElevationRange` | `REQ-UI-425.3` | Specified |
| `TST-UI-389.8` | Localization | `TranslationParityTest` (all 9 locales) | `REQ-UI-425.4`, `REQ-UI-106` | Specified |
| `TST-UI-389.9` | Regression | Full project test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
