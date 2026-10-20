# Stage 1 Analysis: ATT-4367 - Support elevation gain interval filtering (min/max range and preset intervals) in Routes, Favorite Routes, and Workouts

**Ticket**: [[ATT-4367]](https://rainerblind.atlassian.net/browse/ATT-4367)  
**Sub-task**: [[ATT-4417]](https://rainerblind.atlassian.net/browse/ATT-4417) (`[Analysis]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Branch**: `feature/ATT-4367`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-43.1 Joint Review of ticket ATT-4291 (*Support distance interval filtering in Routes and Favorite Routes*), the user requested extending bounded interval filtering to **Elevation Gain (Höhenmeter)** across the application's three core activity and route surfaces:
1. **Routes** (`RouteFilterCriteria`, `RouteFilterBottomSheet`, `ActiveRouteFilterChipsRow`, `RouteTabbedScreen`)
2. **Favorite Routes / Workout Clusters** (`ClusterFilterCriteria`, `ClusterFilterBottomSheet`, `ActiveClusterFilterChipsRow`, `WorkoutClustersTabsScreen`)
3. **Workouts** (`WorkoutFilterCriteria`, `WorkoutFilterBottomSheet`, `ActiveFilterChipsRow`, `WorkoutTabsScreen`)

### Current State & Usability Gaps:
* **Routes (`RouteFilterCriteria` / `RouteFilterBottomSheet`)**:
  * Currently supports only a single-sided lower-bound threshold: `val minElevationGainMeters: Double? = null`.
  * The bottom sheet offers only 4 hardcoded single-sided chips: `> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`.
  * There is no upper-bound parameter (`maxElevationGainMeters`) and no custom numeric input fields (`Min (m)` / `Max (m)`).
  * An athlete looking for a flat or rolling ride (e.g., `< 250 m` or `100 - 250 m` for recovery) cannot filter out steep mountain passes, as selecting any threshold only increases the minimum ascent.
* **Favorite Routes / Workout Clusters (`ClusterFilterCriteria` / `ClusterFilterBottomSheet`)**:
  * Currently completely lacks elevation gain filtering, despite having distance interval filtering (`minDistanceMeters` / `maxDistanceMeters`, introduced in ATT-4291) and despite the underlying engine computing `avgAscentMeters` in `WorkoutClusterStats`.
* **Workouts (`WorkoutFilterCriteria` / `WorkoutFilterBottomSheet`)**:
  * While workout filtering has mature distance and duration intervals (`minDistanceMeters` / `maxDistanceMeters` and `minDurationSec` / `maxDurationSec` from `REQ-UI-157`), it has **no elevation gain filter criteria at all**, despite `WorkoutData` recording authoritative `ascentMeters: Long`.
* **Active Filter Chip Strips**:
  * `ActiveRouteFilterChipsRow` only renders single-sided `> X m` chips and clears only `minElevationGainMeters`.
  * `ActiveClusterFilterChipsRow` and `ActiveFilterChipsRow` do not support elevation chips at all.

This improvement eliminates this major architectural and UX inconsistency across all three screens by extending domain criteria, modal filter sheets, and active chip strips with bounded elevation gain interval filtering (`[minElevationGainMeters, maxElevationGainMeters]`), canonical preset chips, and two-way synchronized custom `Min (m)` / `Max (m)` input fields.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Architectural Investigation Across the Three Filter Surfaces

#### Surface 1: Routes (`ui/routes/`)
* **`RouteFilterCriteria.kt`**:
  * Holds `val minElevationGainMeters: Double? = null`, but lacks `val maxElevationGainMeters: Double? = null`.
  * `activeFilterCount` increments by 1 if `minElevationGainMeters != null`.
  * `matches(route: RouteWithPath)` only evaluates:
    ```kotlin
    if (minElevationGainMeters != null && route.summary.elevationGain < minElevationGainMeters) {
        return false
    }
    ```
  * `toJson()` and `fromJson()` only serialize/deserialize `minElevationGainMeters`.
* **`RouteFilterBottomSheet.kt`**:
  * Section 5 ("Elevation Gain Thresholds") renders only 4 single-sided chips:
    ```kotlin
    listOf(100.0, 250.0, 500.0, 1000.0).forEach { m ->
        FilterChip(
            selected = (localMinElevationGainMeters == m),
            onClick = { localMinElevationGainMeters = if (localMinElevationGainMeters == m) null else m },
            label = { Text(stringResource(R.string.filter_min_elevation_format, m.toInt().toString())) }
        )
    }
    ```
  * Lacks upper bound state (`localMaxElevationGainMeters`) and custom text inputs.
* **`ActiveRouteFilterChipsRow.kt` & `RouteTabbedScreen.kt`**:
  * Renders `RemovableFilterChip` only if `minElevationGainMeters != null` with format `> X m`.
  * Callback `onRemoveMinElevation` clears only `minElevationGainMeters = null`.

#### Surface 2: Favorite Routes / Workout Clusters (`ui/clusters/`)
* **`ClusterFilterCriteria.kt`**:
  * Does not contain `minElevationGainMeters` or `maxElevationGainMeters`.
  * `activeFilterCount` does not count elevation.
  * `matches(cluster, linkedEquipment)` does not evaluate cluster ascent.
  * `toJson()` / `fromJson()` do not handle elevation.
* **Data Availability in Cluster Architecture**:
  * In `WorkoutClusterStats.kt`:
    ```kotlin
    val totalAscentMeters: Long = 0L
    val avgAscentMeters: Long
        get() = if (workoutCount > 0) totalAscentMeters / workoutCount else 0L
    ```
  * `WorkoutClustersViewModel` exposes `clusterStats: StateFlow<Map<Long, WorkoutClusterStats>> = repository.clusterStats`.
  * Each cluster represents a recurring route/fingerprint; `avgAscentMeters` accurately provides the characteristic elevation gain of that track.
* **`ClusterFilterBottomSheet.kt`**:
  * Has sections for Search Query, Equipment, Distance Interval, Recordings, and Start Location, but no Elevation Gain section.
* **`ActiveClusterFilterChipsRow.kt` & `WorkoutClustersTabsScreen.kt`**:
  * Lacks elevation chip rendering and dismissal wiring.

#### Surface 3: Workouts (`ui/aftermath/workoutlist/`)
* **`WorkoutFilterCriteria.kt`**:
  * Has 13 dimensions (Query, Time, Dates, Sport, Equipment, Commute, Trainer, Race, GPS, Distance Interval, Duration Interval, Start Location, Cluster, Source), but completely lacks elevation gain bounds.
  * In `WorkoutData.kt`, each recorded session already holds `val ascentMeters: Long`.
* **`WorkoutFilterBottomSheet.kt`**:
  * Has Distance Interval (Section 6) and Duration Interval (Section 7), but no Elevation Gain section.
* **`ActiveFilterChipsRow.kt` & `WorkoutTabsScreen.kt`**:
  * Lacks elevation chip rendering and dismissal wiring.

### 2.2 Canonical Preset Interval Architecture (Harmonizing with ATT-4291)

In ATT-4291, distance interval filtering established the canonical 5-bucket pattern across all three filter surfaces:
* `< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`
* Two-way synchronized `OutlinedTextField`s for `Min` and `Max`.

For Elevation Gain, the application's existing thresholds (previously in `RouteFilterBottomSheet.kt`) are `100 m`, `250 m`, `500 m`, and `1000 m`. Notice the exact 10x ratio between distance (in km) and elevation gain (in meters):
* `10 km` $\leftrightarrow$ `100 m`
* `25 km` $\leftrightarrow$ `250 m`
* `50 km` $\leftrightarrow$ `500 m`
* `100 km` $\leftrightarrow$ `1000 m`

Structuring Elevation Gain with the exact same 5-bucket interval partition:
1. `< 100 m` (`min = null, max = 100.0`): Flat terrain / recovery sessions
2. `100 - 250 m` (`min = 100.0, max = 250.0`): Gently undulating terrain
3. `250 - 500 m` (`min = 250.0, max = 500.0`): Rolling hills / moderate climbs
4. `500 - 1000 m` (`min = 500.0, max = 1000.0`): Significant hills / mountain stages
5. `> 1000 m` (`min = 1000.0, max = null`): Alpine / high-altitude epic climbs

This design provides three critical advantages:
* **100% backward continuity**: Preserves all 4 thresholds previously familiar to athletes (`100 m`, `250 m`, `500 m`, `1000 m`).
* **Visual rhythm parity**: 5 chips neatly fill the horizontal FlowRow width, perfectly matching the 5 chips of the Distance Interval section directly preceding or adjacent to it.
* **Custom flexibility**: Two synchronized `Min (m)` and `Max (m)` integer text fields allow entering arbitrary numbers (e.g. `200 m`, `350 m`, `750 m`).

### 2.3 Resource Availability & 9-Language Localization Audit

* Section Header: `R.string.sort_elevation_gain` ("Elevation Gain" / "Höhenmeter") is already defined across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* Lower Bound Format: `R.string.filter_min_elevation_format` (`&gt; %1$s m`) already exists in all 9 locales.
* Net-new strings required across all 9 locales:
  * `filter_max_elevation_format`: `&lt; %1$s m`
  * `filter_elevation_interval_format`: `%1$s - %2$s m`
* Because "m" (meters) is the universal SI unit standard across all 9 languages, the text content for these formats is identical across all locales.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Domain Model Parity**:
     * Extend `RouteFilterCriteria`, `ClusterFilterCriteria`, and `WorkoutFilterCriteria` with `minElevationGainMeters: Double? = null` and `maxElevationGainMeters: Double? = null`.
     * Update `activeFilterCount` across all three models to count elevation gain once when either/both bounds are present.
     * Update `matches(...)` across all three models to enforce `[minElevationGainMeters, maxElevationGainMeters]`.
       * Routes: test `route.summary.elevationGain`.
       * Clusters: test `elevationGainMeters` (resolved from `clusterStats[cluster.id]?.avgAscentMeters?.toDouble() ?: 0.0`).
       * Workouts: test `workout.ascentMeters.toDouble()`.
     * Update `toJson()` and `fromJson()` across all three models with safe backward-compatible serialization.
  2. **Modal Bottom Sheets**:
     * Update `RouteFilterBottomSheet`: convert Section 5 from single-sided chips to bounded elevation intervals (`< 100 m`, `100 - 250 m`, `250 - 500 m`, `500 - 1000 m`, `> 1000 m`) plus two-way synchronized `Min (m)` and `Max (m)` integer text fields.
     * Update `ClusterFilterBottomSheet`: add dedicated **Elevation Gain** section (`R.string.sort_elevation_gain`) with the identical 5 preset chips and `Min (m)` / `Max (m)` fields.
     * Update `WorkoutFilterBottomSheet`: add dedicated **Elevation Gain** section (`R.string.sort_elevation_gain`) following Distance and Duration intervals, with the identical 5 preset chips and `Min (m)` / `Max (m)` fields.
  3. **Active Filter Chip Strips & Host Screens**:
     * Update `ActiveRouteFilterChipsRow`, `ActiveClusterFilterChipsRow`, and `ActiveFilterChipsRow` to display unified removable elevation interval chips (`"$min - $max m"`, `"> $min m"`, `"< $max m"`).
     * Provide `onRemoveElevationRange: () -> Unit` callback that clears both elevation boundaries simultaneously upon dismissal.
     * Wire dismissal in host screens (`RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutTabsScreen.kt`).
     * In `WorkoutClustersTabsScreen.kt`, collect `clusterStats` from `viewModel.clusterStats` and pass `clusterStats[cluster.id]?.avgAscentMeters?.toDouble()` to `filterCriteria.matches(...)`.
  4. **Localization Parity**:
     * Add `filter_max_elevation_format` and `filter_elevation_interval_format` across all 9 application locales.
  5. **Unit Test Coverage**:
     * Comprehensive unit test suites in `RouteFilterCriteriaTest`, `ClusterFilterCriteriaTest`, and `WorkoutFilterCriteriaTest` covering preset intervals, custom bounds, active filter count derivation, and JSON serialization roundtrips.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. *Segment Filtering (`SegmentFilterCriteria` / `SegmentFilterBottomSheet`)*: Segments are localized climbs where single-sided elevation thresholds (`minElevationGainMeters`) already match climb categorization needs; bounded interval filtering was not requested.
  2. *SQLite Database Schema Alterations (`Routes.db`, `RouteClusters.db`, `TrainingTracker.db`)*: Filtering is strictly performed in-memory on domain DTOs within reactive ViewModel flows; no schema migrations or database columns are affected.
  3. *Modifying Sorting Orders or Clustering Engines*: Clustering algorithms and sort criteria (`RouteSortOrder`, `ClusterSortOrder`) remain unchanged.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### 4.1 Archaeology for `REQ-UI-133` & `REQ-UI-417` (Route Filtering)
* **Original Requirement ID & Target**: `REQ-UI-133` (*Multi-Dimensional Route Filtering*) and `REQ-UI-417` (*Support distance interval filtering in Routes and Favorite Routes*). Targets `RouteFilterCriteria.kt`, `RouteFilterBottomSheet.kt`, `ActiveRouteFilterChipsRow.kt`, and `RouteTabbedScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.1 (`ATT-736`, commit `3c5132da`), refined in Sprint 2026-43.1 (`ATT-4291`, commit `2818f65a`).
* **Root Reason for Existing Formulation**: Initial route filtering implemented basic thresholding (`> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`) under a combined "Distance & Duration" section to prune very flat routes. ATT-4291 separated distance and elevation into distinct sections and modernized distance to bounded intervals, leaving elevation gain as single-sided thresholds.
* **Preservation of Core Invariants**:
  * Existing route filter dimensions (query, origin source, selection, distance interval) remain 100% operational.
  * Backward compatibility for DataStore JSON is preserved: legacy JSON missing `maxElevationGainMeters` deserializes safely with `maxElevationGainMeters = null`.
  * Lower-bound filtering continues to function identically when only `minElevationGainMeters` is set.

### 4.2 Archaeology for `REQ-UI-135` & `REQ-UI-417` (Favorite Routes / Clusters)
* **Original Requirement ID & Target**: `REQ-UI-135` (*Multi-Dimensional Favorite Tracks Filtering*) and `REQ-UI-417`. Targets `ClusterFilterCriteria.kt`, `ClusterFilterBottomSheet.kt`, `ActiveClusterFilterChipsRow.kt`, and `WorkoutClustersTabsScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.1 (`ATT-737`, commit `472be081`), updated in Sprint 2026-43.1 (`ATT-4291`, commit `2818f65a`).
* **Root Reason for Existing Formulation**: Clusters originally only filtered by name, equipment, distance, hit count, and start geofence. Elevation gain was not included in initial criteria because early clustering engines focused on 2D track geometry.
* **Preservation of Core Invariants**:
  * All existing cluster dimensions (query, equipment, distance interval, recordings, start location) remain 100% intact.
  * Cluster matching with missing elevation stats gracefully defaults to `0.0` without throwing or rejecting valid clusters unless an elevation filter is explicitly configured.

### 4.3 Archaeology for `REQ-UI-132` & `REQ-UI-157` (Workout Filtering)
* **Original Requirement ID & Target**: `REQ-UI-132` (*Multi-Dimensional Workout Filtering*) and `REQ-UI-157` (*Workout Distance & Duration Interval Filtering*). Targets `WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, and `WorkoutTabsScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.1 (`ATT-735`, commit `316d946f`), enhanced in Sprint 2026-42.2 (`ATT-761`, commit `0408b022`).
* **Root Reason for Existing Formulation**: `REQ-UI-157` introduced bounded intervals for Distance (km) and Duration (min) with preset chips and custom fields, but omitted elevation gain because flat workout imports (e.g. indoor trainer sessions) had 0 m ascent and were thought to be handled sufficiently by the sport sub-type filter.
* **Preservation of Core Invariants**:
  * All 13 existing workout criteria dimensions remain 100% functional.
  * Legacy workout filter JSON lacking elevation bounds deserializes cleanly with `minElevationGainMeters = null, maxElevationGainMeters = null`.
  * Indoor trainer workouts (with 0 m ascent) naturally match flat intervals (`< 100 m`) or pass unaffected when elevation filtering is inactive.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Immutable Domain Models
In `RouteFilterCriteria`, `ClusterFilterCriteria`, and `WorkoutFilterCriteria`:
1. Add `val minElevationGainMeters: Double? = null, val maxElevationGainMeters: Double? = null`.
2. Update `activeFilterCount`:
   ```kotlin
   if (minElevationGainMeters != null || maxElevationGainMeters != null) count++
   ```
3. Update `matches(...)`:
   * Route:
     ```kotlin
     if (minElevationGainMeters != null && route.summary.elevationGain < minElevationGainMeters) return false
     if (maxElevationGainMeters != null && route.summary.elevationGain > maxElevationGainMeters) return false
     ```
   * Cluster:
     ```kotlin
     if (minElevationGainMeters != null || maxElevationGainMeters != null) {
         val elev = elevationGainMeters ?: 0.0
         if (minElevationGainMeters != null && elev < minElevationGainMeters) return false
         if (maxElevationGainMeters != null && elev > maxElevationGainMeters) return false
     }
     ```
   * Workout:
     ```kotlin
     if (minElevationGainMeters != null && workout.ascentMeters < minElevationGainMeters) return false
     if (maxElevationGainMeters != null && workout.ascentMeters > maxElevationGainMeters) return false
     ```
4. `toJson()` & `fromJson()`:
   * Serialize `minElevationGainMeters` and `maxElevationGainMeters` when non-null.
   * Gracefully parse existing JSON payloads lacking these keys as `null`.

### 5.2 Modal Bottom Sheet Presentation
In `RouteFilterBottomSheet`, `ClusterFilterBottomSheet`, and `WorkoutFilterBottomSheet`:
1. Maintain local states:
   * `localMinElevationGainMeters`, `localMaxElevationGainMeters`
   * `minElevationText`, `maxElevationText`
2. Render Elevation Gain section:
   * Header: `stringResource(R.string.sort_elevation_gain)`
   * 5 Preset chips:
     * `< 100 m` (`null, 100.0`)
     * `100 - 250 m` (`100.0, 250.0`)
     * `250 - 500 m` (`250.0, 500.0`)
     * `500 - 1000 m` (`500.0, 1000.0`)
     * `> 1000 m` (`1000.0, null`)
   * Row with two equal-width `OutlinedTextField`s for `Min (m)` and `Max (m)` using `KeyboardOptions(keyboardType = KeyboardType.Number)`.
   * Two-way synchronization: selecting a preset populates text fields; manual text editing updates numeric bounds and deselects mismatched chips.

### 5.3 Active Filter Chip Formatting & Dismissal
In `ActiveRouteFilterChipsRow`, `ActiveClusterFilterChipsRow`, and `ActiveFilterChipsRow`:
1. Render a single removable elevation chip when `minElevationGainMeters != null || maxElevationGainMeters != null`:
   * Both bounds: `stringResource(R.string.filter_elevation_interval_format, minM.toInt(), maxM.toInt())` $\rightarrow$ e.g. `"250 - 500 m"`
   * Min only: `stringResource(R.string.filter_min_elevation_format, minM.toInt())` $\rightarrow$ e.g. `"> 500 m"`
   * Max only: `stringResource(R.string.filter_max_elevation_format, maxM.toInt())` $\rightarrow$ e.g. `"< 250 m"`
2. Dismissal via `onRemoveElevationRange: () -> Unit` reactively clears both `minElevationGainMeters = null` and `maxElevationGainMeters = null`.
3. In host screens (`RouteTabbedScreen`, `WorkoutClustersTabsScreen`, `WorkoutTabsScreen`), wire the callback to update filter criteria.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression on all existing filter dimensions (query, sport, equipment, commute, trainer, race, GPS, distance intervals, duration intervals, recording counts, geofences).
  2. DataStore preference backward compatibility: missing JSON attributes resolve to `null` without throwing or losing user settings.
  3. Visual and functional consistency: strict 1:1 parity with the established distance interval UI pattern across all three filter surfaces.
  4. 100% 9-language localization parity across all supported locales.
  5. Parent ticket Human Decision Gate remains strictly enforced.

* **Risk Rating**: **LOW**
  * **Rationale**: The change is purely additive, affecting only immutable domain filter DTOs, in-memory predicate matching, and Compose UI presentation components. No SQLite database schemas, foreign keys, background services, or sensor dispatchers are touched. All mathematical comparisons are pure numeric checks on already calculated domain fields (`elevationGain`, `avgAscentMeters`, `ascentMeters`).
