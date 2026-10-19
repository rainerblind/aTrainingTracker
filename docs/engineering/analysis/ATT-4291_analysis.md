# Stage 1 Analysis: ATT-4291 - Support distance interval filtering (min/max range and preset intervals) in Routes and Favorite Routes

**Ticket**: [[ATT-4291]](https://rainerblind.atlassian.net/browse/ATT-4291)  
**Sub-task**: [[ATT-4295]](https://rainerblind.atlassian.net/browse/ATT-4295) (`[Analysis]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4291`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

Currently, route filtering (`RouteFilterCriteria` / `RouteFilterBottomSheet`) and favorite route / cluster filtering (`ClusterFilterCriteria` / `ClusterFilterBottomSheet`) only support single-sided lower-bound distance thresholds (`minDistanceMeters`: `> 10 km`, `> 25 km`, `> 50 km`, `> 100 km`).

This creates significant usability friction for athletes with extensive route libraries:
* When an athlete wants to find a medium-length route or favorite track for an after-work session (e.g., between `25 km` and `50 km`), selecting `> 25 km` continues to include all 80 km, 100 km, and 150+ km long rides, forcing tedious manual scrolling and inspection.
* There is currently no way to specify an upper bound (`maxDistanceMeters`), nor to enter custom kilometer limits outside the four hardcoded chip values.
* In contrast, the workout history filtering architecture (`WorkoutFilterBottomSheet` / `WorkoutFilterCriteria`, introduced in `REQ-UI-157`) already provides a mature, highly intuitive **Distance Interval** interface featuring preset range chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) plus two-way synchronized custom `Min (km)` and `Max (km)` decimal input fields.
* Active filter chips on the Routes screen (`ActiveRouteFilterChipsRow`) and Favorite Routes screen (`ActiveClusterFilterChipsRow`) only display single-sided `> X km` labels and cannot reflect bounded intervals or upper limits.

This improvement resolves this feature disparity by extending `RouteFilterCriteria` and `ClusterFilterCriteria` to support full distance interval filtering (`[minDistanceMeters, maxDistanceMeters]`), updating the respective bottom sheets to match `WorkoutFilterBottomSheet`, and harmonizing active filter chip formatting across all three screens.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Current Architectural Limitations

#### `RouteFilterCriteria.kt` & `RouteFilterBottomSheet.kt`
* `RouteFilterCriteria` holds `val minDistanceMeters: Double? = null`, but completely lacks `maxDistanceMeters`.
* In `RouteFilterCriteria.activeFilterCount`, distance is counted as an active dimension solely if `minDistanceMeters != null`.
* In `RouteFilterCriteria.matches(route: RouteWithPath)`, the predicate only checks:
  ```kotlin
  if (minDistanceMeters != null && route.summary.distance < minDistanceMeters) {
      return false
  }
  ```
* In `RouteFilterCriteria.toJson()` and `fromJson()`, only `minDistanceMeters` is serialized/deserialized to/from DataStore preferences.
* In `RouteFilterBottomSheet.kt`, Section 4 combines distance and elevation gain under a single generic header (`R.string.filter_section_thresholds` / "Distance & Duration"):
  ```kotlin
  listOf(10.0, 25.0, 50.0, 100.0).forEach { km ->
      val meters = km * 1000.0
      FilterChip(
          selected = (localMinDistanceMeters == meters),
          onClick = { localMinDistanceMeters = if (localMinDistanceMeters == meters) null else meters },
          label = { Text(stringResource(R.string.filter_min_distance_format, km.toInt().toString())) }
      )
  }
  ```
  This offers neither upper bounds nor custom numeric entry.

#### `ClusterFilterCriteria.kt` & `ClusterFilterBottomSheet.kt`
* `ClusterFilterCriteria` similarly defines `val minDistanceMeters: Double? = null` with no `maxDistanceMeters`.
* In `ClusterFilterCriteria.matches(cluster: WorkoutCluster, linkedEquipment: Set<String>)`, only `refDistance < minDistanceMeters` is checked.
* In `ClusterFilterBottomSheet.kt`, Section 3 ("Minimum Reference Distance Thresholds") renders identical single-sided `> 10 km`, `> 25 km`, `> 50 km`, `> 100 km` chips without interval or custom input options.

#### Active Filter Chip Strips (`ActiveRouteFilterChipsRow.kt` & `ActiveClusterFilterChipsRow.kt`)
* In both chip strips, distance is displayed via:
  ```kotlin
  if (criteria.minDistanceMeters != null) {
      val km = (criteria.minDistanceMeters / 1000.0).toInt()
      RemovableFilterChip(
          label = stringResource(R.string.filter_min_distance_format, km.toString()),
          onRemove = onRemoveMinDistance
      )
  }
  ```
* The chip removal callback `onRemoveMinDistance` only clears `minDistanceMeters`.
* There is no support for interval formatting (`"$min - $max km"`) or upper-bound formatting (`"< $max km"`).

### 2.2 Reference Architecture (`WorkoutFilterBottomSheet.kt` & `ActiveFilterChipsRow.kt`)

`WorkoutFilterCriteria` and `WorkoutFilterBottomSheet` (`REQ-UI-157`) define the established standard for interval filtering in the application:
1. **Model**:
   * `val minDistanceMeters: Double? = null, val maxDistanceMeters: Double? = null`
   * `activeFilterCount` increments by 1 if `minDistanceMeters != null || maxDistanceMeters != null`.
   * `matches`:
     ```kotlin
     if (minDistanceMeters != null && workout.totalDistance < minDistanceMeters) return false
     if (maxDistanceMeters != null && workout.totalDistance > maxDistanceMeters) return false
     ```
2. **Preset Interval Chips**:
   * `< 10 km` (`min = null, max = 10000.0`)
   * `10 - 25 km` (`min = 10000.0, max = 25000.0`)
   * `25 - 50 km` (`min = 25000.0, max = 50000.0`)
   * `50 - 100 km` (`min = 50000.0, max = 100000.0`)
   * `> 100 km` (`min = 100000.0, max = null`)
3. **Synchronized Custom Text Fields**:
   * Two decimal `OutlinedTextField`s for `Min (km)` and `Max (km)`.
   * Selecting a preset chip populates both text fields; manually editing a text field updates `localMinDistanceMeters` or `localMaxDistanceMeters` (multiplying entered km by 1000.0) and deselects mismatched preset chips.
4. **Active Filter Chip Formatting**:
   * Both bounds set: `R.string.filter_distance_interval_format` ("%1$s - %2$s km") $\rightarrow$ e.g., `"25 - 50 km"`
   * Min only: `R.string.filter_min_distance_format` (">&gt; %1$s km") $\rightarrow$ e.g., `"> 25 km"`
   * Max only: `R.string.filter_max_distance_format` ("&lt; %1$s km") $\rightarrow$ e.g., `"< 50 km"`
   * Dismissing the chip resets both `minDistanceMeters = null` and `maxDistanceMeters = null`.

### 2.3 Resource Availability & Localization Parity
All required localized strings already exist across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
* `R.string.filter_section_distance` ("Distance" / "Distanz")
* `R.string.filter_min_label` ("Min")
* `R.string.filter_max_label` ("Max")
* `R.string.filter_distance_interval_format` ("%1$s - %2$s km")
* `R.string.filter_min_distance_format` (">&gt; %1$s km")
* `R.string.filter_max_distance_format` ("&lt; %1$s km")
* `R.string.sort_elevation_gain` ("Elevation Gain" / "Höhenmeter")

No net-new string additions are required.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Extend `RouteFilterCriteria` with `val maxDistanceMeters: Double? = null`, update `activeFilterCount` to count distance once when either/both bounds are present, update `matches(route)` to enforce both lower and upper distance boundaries, and include `maxDistanceMeters` in `toJson()` / `fromJson()`.
  2. Extend `ClusterFilterCriteria` with `val maxDistanceMeters: Double? = null`, update `activeFilterCount`, update `matches(cluster, linkedEquipment)` to enforce `refDistance` bounds, and include `maxDistanceMeters` in `toJson()` / `fromJson()`.
  3. Refactor `RouteFilterBottomSheet`: separate distance from elevation into a dedicated **Distance** section (`R.string.filter_section_distance`) with preset interval chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) and custom `Min (km)` / `Max (km)` text fields; retain elevation gain thresholds in their own dedicated **Elevation Gain** section (`R.string.sort_elevation_gain`).
  4. Refactor `ClusterFilterBottomSheet`: replace single-sided distance chips with the dedicated **Distance** interval section matching `RouteFilterBottomSheet` and `WorkoutFilterBottomSheet`.
  5. Refactor `ActiveRouteFilterChipsRow` and `ActiveClusterFilterChipsRow`: render a unified removable distance chip with interval formatting (`"$min - $max km"`, `"> $min km"`, `"< $max km"`), and invoke a removal callback (`onRemoveDistanceRange`) that clears both distance boundaries.
  6. Update host screens (`RouteTabbedScreen.kt` and `WorkoutClustersTabsScreen.kt`) to wire `onRemoveDistanceRange` to clear both `minDistanceMeters = null` and `maxDistanceMeters = null`.
  7. Add comprehensive unit tests in `RouteFilterCriteriaTest` and `ClusterFilterCriteriaTest` covering preset interval matching, custom min/max bounds, active filter count derivation, and JSON serialization roundtrips.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying segment filtering (`SegmentFilterCriteria` / `SegmentFilterBottomSheet`): Segments are short point-to-point climbs where distance interval filtering was not requested and single-sided thresholds remain appropriate.
  2. Modifying workout filtering (`WorkoutFilterCriteria` / `WorkoutFilterBottomSheet`): This serves as the reference implementation and is already complete.
  3. Modifying SQLite database schemas (`Routes.db`, `WorkoutClusters.db`): Filtering is performed entirely in memory on domain DTOs within reactive ViewModel flows; no schema migrations or database column alterations are needed.
  4. Modifying sorting algorithms (`RouteSortOrder`, `ClusterSortOrder`) or sport-tab partitioning.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### 4.1 Requirement Evolution & Archaeology for `REQ-UI-133` (Route Filtering)

* **Original Requirement ID & Target**: `REQ-UI-133` (*Multi-Dimensional Route Filtering, Shared Modal Filter Sheet & Persistent Filter State*). Targets `RouteFilterCriteria.kt`, `RouteFilterBottomSheet.kt`, `ActiveRouteFilterChipsRow.kt`, and `RouteTabbedScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.1 (`ATT-736`, commit `3c5132da`).
* **Root Reason for Existing Formulation**: When route filtering was originally designed, the initial requirement prioritized basic filtering across source, selection, and lower-bound thresholds (`> 10 km`, `> 25 km`, `> 50 km`, `> 100 km`) to filter out very short test routes. Upper-bound filtering and custom numeric entry were deferred. Later, `REQ-UI-157` introduced distance interval filtering for workouts, creating a visual and functional disparity between routes and workouts.
* **Preservation of Core Invariants**:
  * Existing criteria dimensions (search query, route source, map selection, minimum elevation gain) remain 100% intact.
  * Backward compatibility for persisted preferences in `DataStore` is fully preserved: legacy JSON lacking `maxDistanceMeters` parses cleanly with `maxDistanceMeters = null`, producing identical filtering behavior to previous releases.
  * Single-sided minimum distance filtering (e.g. `> 100 km`) continues to function identically when only `minDistanceMeters` is specified.
  * Predicate evaluation remains non-blocking, deterministic, and short-circuiting.

### 4.2 Requirement Evolution & Archaeology for `REQ-UI-135` (Favorite Tracks / Cluster Filtering)

* **Original Requirement ID & Target**: `REQ-UI-135` (*Multi-Dimensional Favorite Tracks (Lieblingsstrecken) Filtering, Shared Modal Filter Sheet & Persistent Filter State*). Targets `ClusterFilterCriteria.kt`, `ClusterFilterBottomSheet.kt`, `ActiveClusterFilterChipsRow.kt`, and `WorkoutClustersTabsScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.1 (`ATT-737`, commit `472be081`). *(Note: The table row in `docs/requirements.md` was accidentally overwritten by commit `a9ce5b33` during `ATT-761`; this ticket re-specifies and updates the requirement with distance intervals).*
* **Root Reason for Existing Formulation**: Initial cluster filtering mirrored `REQ-UI-133`, providing only lower-bound reference distance thresholds (`refDistance >= minDistanceMeters`).
* **Preservation of Core Invariants**:
  * Existing cluster criteria dimensions (cluster name query, linked equipment, hit count/recordings threshold, spatial start location geofence) remain 100% intact.
  * Backward compatibility for persisted JSON preferences is guaranteed: legacy JSON without `maxDistanceMeters` parses with `maxDistanceMeters = null`.
  * Cluster matching invariants with `refDistance` and spatial fingerprints remain unchanged.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Immutable Domain Model Updates

In both `RouteFilterCriteria.kt` and `ClusterFilterCriteria.kt`:
```kotlin
// Add maxDistanceMeters parameter with default null
val maxDistanceMeters: Double? = null,

// Update activeFilterCount: distance interval counts as 1 dimension if either or both bounds are set
val activeFilterCount: Int
    get() {
        var count = 0
        // ... other dimensions ...
        if (minDistanceMeters != null || maxDistanceMeters != null) count++
        return count
    }

// Update matches predicate:
if (minDistanceMeters != null && distance < minDistanceMeters) {
    return false
}
if (maxDistanceMeters != null && distance > maxDistanceMeters) {
    return false
}

// Update toJson:
maxDistanceMeters?.let { json.put("maxDistanceMeters", it) }

// Update fromJson:
maxDistanceMeters = if (json.has("maxDistanceMeters")) json.getDouble("maxDistanceMeters") else null
```

### 5.2 UI Bottom Sheet Restructuring

In `RouteFilterBottomSheet.kt`:
1. Introduce local state for `localMaxDistanceMeters`, `minDistanceText`, and `maxDistanceText` mirroring `WorkoutFilterBottomSheet.kt`.
2. Format initial text representations cleanly (e.g. `25` rather than `25.0` for whole numbers).
3. Split the existing combined Section 4 into:
   * **Distance Section** (`R.string.filter_section_distance`):
     * Preset chips: `< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`.
     * `Row` containing two equal-weight `OutlinedTextField`s for `Min (km)` and `Max (km)` with `KeyboardType.Decimal`.
     * Preset selection updates both numeric bounds and text states; text field edits update numeric bounds and deselect mismatched chips.
   * **Elevation Gain Section** (`R.string.sort_elevation_gain`):
     * Retains the existing `> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m` chips under a distinct title.

In `ClusterFilterBottomSheet.kt`:
1. Apply the identical **Distance Section** restructuring to Section 3.
2. Sections 4 (Recordings) and 5 (Start Location) remain intact.

### 5.3 Active Filter Chip Formatting & Dismissal

In `ActiveRouteFilterChipsRow.kt` and `ActiveClusterFilterChipsRow.kt`:
1. Replace single-sided chip logic with unified interval logic:
   ```kotlin
   if (criteria.minDistanceMeters != null || criteria.maxDistanceMeters != null) {
       item("distance") {
           val minKm = criteria.minDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } }
           val maxKm = criteria.maxDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } }
           val label = when {
               minKm != null && maxKm != null -> stringResource(R.string.filter_distance_interval_format, minKm, maxKm)
               minKm != null -> stringResource(R.string.filter_min_distance_format, minKm)
               else -> stringResource(R.string.filter_max_distance_format, maxKm!!)
           }
           RemovableFilterChip(
               label = label,
               onRemove = onRemoveDistanceRange
           )
       }
   }
   ```
2. Update the callback signature from `onRemoveMinDistance: () -> Unit` to `onRemoveDistanceRange: () -> Unit`.
3. In `RouteTabbedScreen.kt` and `WorkoutClustersTabsScreen.kt`, provide the callback:
   ```kotlin
   onRemoveDistanceRange = { onUpdateFilterCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) } }
   ```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing filter dimensions (query, source, selection, elevation gain, equipment, hit count, spatial starting location).
  2. Lossless backward compatibility for persisted `DataStore` preferences: missing JSON fields resolve gracefully to `null`.
  3. Visual and functional consistency baseline (`docs/design_guidelines.md` Sec 5.1): 1:1 parity with `WorkoutFilterBottomSheet` and `ActiveFilterChipsRow`.
  4. 9-language localization parity across all strings used.
  5. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * **Rationale**: The change is strictly confined to domain filter criteria DTOs and Compose presentation components. No SQLite schema migrations, foreign keys, or background services are modified. All mathematical comparisons are pure floating-point operations evaluated in memory.
