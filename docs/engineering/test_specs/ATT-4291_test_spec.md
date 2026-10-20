# Stage 2: Requirement & Test Specification - ATT-4291: Support distance interval filtering (min/max range and preset intervals) in Routes and Favorite Routes

**Ticket**: [ATT-4291](https://rainerblind.atlassian.net/browse/ATT-4291)  
**Sub-task**: [ATT-4296](https://rainerblind.atlassian.net/browse/ATT-4296) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-417` (*Distance Interval Filtering (Min/Max Range and Preset Intervals) in Routes and Favorite Routes*)  
**Test Spec ID**: `TST-UI-380`  
**Branch**: `feature/ATT-4291`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-417)

### 1.1 Problem Statement & Rationale
Currently, route filtering (`RouteFilterCriteria` / `RouteFilterBottomSheet`) and favorite route / workout cluster filtering (`ClusterFilterCriteria` / `ClusterFilterBottomSheet`) only support single-sided lower-bound distance thresholds (`minDistanceMeters`: `> 10 km`, `> 25 km`, `> 50 km`, `> 100 km`).

When an athlete with a large route library seeks a medium-distance route (e.g., a 25–50 km after-work ride), selecting `> 25 km` continues to include all 80 km, 100 km, and 150+ km long endurance routes. There is currently no mechanism to set an upper bound (`maxDistanceMeters`), nor to enter custom kilometer values. In contrast, the workout history filter sheet (`WorkoutFilterBottomSheet` / `WorkoutFilterCriteria`, introduced in `REQ-UI-157`) provides a mature, highly intuitive **Distance Interval** section featuring preset range chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) and two-way synchronized custom `Min (km)` and `Max (km)` decimal input fields. Furthermore, active filter chips on the Routes screen (`ActiveRouteFilterChipsRow`) and Favorite Routes screen (`ActiveClusterFilterChipsRow`) only display single-sided `> X km` labels and cannot reflect bounded intervals or upper limits.

This specification closes the feature disparity between routes, favorite tracks, and workouts by establishing bounded distance interval filtering (`[minDistanceMeters, maxDistanceMeters]`) across `RouteFilterCriteria` and `ClusterFilterCriteria`, harmonizing the respective modal bottom sheets with `WorkoutFilterBottomSheet`, and standardizing active filter chip formatting across all three screens.

### 1.2 Functional & Architectural Requirements

1. **Domain Model Extensions (`RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`)**:
   - `RouteFilterCriteria` SHALL declare `val maxDistanceMeters: Double? = null` with default `null`.
   - `ClusterFilterCriteria` SHALL declare `val maxDistanceMeters: Double? = null` with default `null`.
   - `activeFilterCount` in both models SHALL treat distance as a single active filter dimension if and only if `minDistanceMeters != null || maxDistanceMeters != null`. Setting both bounds SHALL NOT increment `activeFilterCount` by more than 1.
   - `RouteFilterCriteria.matches(route: RouteWithPath)` SHALL evaluate:
     - `if (minDistanceMeters != null && route.summary.distance < minDistanceMeters) return false`
     - `if (maxDistanceMeters != null && route.summary.distance > maxDistanceMeters) return false`
   - `ClusterFilterCriteria.matches(cluster: WorkoutCluster, linkedEquipment: Set<String>)` SHALL evaluate:
     - `if (minDistanceMeters != null && cluster.refDistance < minDistanceMeters) return false`
     - `if (maxDistanceMeters != null && cluster.refDistance > maxDistanceMeters) return false`
   - Serialization & Persistence:
     - `toJson()` in both models SHALL serialize `maxDistanceMeters` as a JSON number if non-null: `maxDistanceMeters?.let { json.put("maxDistanceMeters", it) }`.
     - `fromJson()` in both models SHALL deserialize `maxDistanceMeters` safely if present: `maxDistanceMeters = if (json.has("maxDistanceMeters")) json.getDouble("maxDistanceMeters") else null`.
     - Legacy JSON records lacking `maxDistanceMeters` SHALL resolve cleanly to `null` without throwing exceptions or corrupting existing filter state.

2. **Modal Filter Bottom Sheets (`RouteFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`)**:
   - `RouteFilterBottomSheet.kt`:
     - Distance and elevation gain SHALL be separated into distinct sections.
     - **Distance Section** (`R.string.filter_section_distance`):
       - Preset chips: `< 10 km` (`min = null, max = 10000.0`), `10 - 25 km` (`min = 10000.0, max = 25000.0`), `25 - 50 km` (`min = 25000.0, max = 50000.0`), `50 - 100 km` (`min = 50000.0, max = 100000.0`), `> 100 km` (`min = 100000.0, max = null`).
       - Custom input fields: two equal-weight `OutlinedTextField`s for `Min (km)` (`R.string.filter_min_label`) and `Max (km)` (`R.string.filter_max_label`) with `KeyboardType.Decimal`.
       - Two-way synchronization: selecting a preset chip updates both text fields and numeric meter bounds; manual text editing updates numeric meter bounds (multiplying entered km by 1000.0) and deselects mismatched preset chips; clearing a field or chip resets the corresponding bound to `null`.
     - **Elevation Gain Section** (`R.string.sort_elevation_gain`):
       - Dedicated header displaying `R.string.sort_elevation_gain` ("Elevation Gain" / "Höhenmeter").
       - Retains existing chips: `> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`.
   - `ClusterFilterBottomSheet.kt`:
     - Replaces single-sided distance chips in Section 3 with the identical **Distance Section** (`R.string.filter_section_distance`) providing preset chips and synchronized `Min (km)` and `Max (km)` text fields matching `RouteFilterBottomSheet` and `WorkoutFilterBottomSheet`.
     - Sections 4 (Recordings / Hit Count) and 5 (Start Location) remain intact.

3. **Active Filter Chip Strips (`ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, Host Screens)**:
   - When `minDistanceMeters != null || maxDistanceMeters != null`, both chip rows SHALL render a single removable chip:
     - Both bounds set: `R.string.filter_distance_interval_format` ("%1$s - %2$s km") $\rightarrow$ e.g., `"25 - 50 km"`.
     - Min only: `R.string.filter_min_distance_format` (">&gt; %1$s km") $\rightarrow$ e.g., `"> 25 km"`.
     - Max only: `R.string.filter_max_distance_format` ("&lt; %1$s km") $\rightarrow$ e.g., `"< 50 km"`.
     - Non-integer km values (e.g. 12.5) SHALL format as `12.5`, while whole numbers format as integers (e.g. `25` rather than `25.0`).
   - Chip removal callback SHALL be updated to `onRemoveDistanceRange: () -> Unit`. Tapping the `✕` remove icon SHALL clear both `minDistanceMeters = null` and `maxDistanceMeters = null` reactively.
   - In host screens (`RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`), `onRemoveDistanceRange` SHALL be wired to update the ViewModel filter state by setting both distance bounds to `null`.

4. **100% 9-Language Localization Parity**:
   - String resources SHALL be reused across all 9 application locales without adding duplicate keys or missing translations:
     - `R.string.filter_section_distance`
     - `R.string.filter_min_label`
     - `R.string.filter_max_label`
     - `R.string.filter_distance_interval_format`
     - `R.string.filter_min_distance_format`
     - `R.string.filter_max_distance_format`
     - `R.string.sort_elevation_gain`

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Preset Distance Interval Selection on Routes**
  * *Given* an athlete browsing the Routes list with `RouteFilterBottomSheet` open,
  * *When* the athlete taps the `25 - 50 km` preset chip and confirms with "OK",
  * *Then* `minDistanceMeters` is set to `25000.0` and `maxDistanceMeters` is set to `50000.0`,
  * *And* only routes with `25000.0 <= distance <= 50000.0` are shown,
  * *And* `ActiveRouteFilterChipsRow` renders a single chip displaying `"25 - 50 km"`,
  * *And* the header filter badge increments the active dimension count by 1.

* **Criterion 2: Custom Distance Bounds on Favorite Tracks**
  * *Given* an athlete on the Favorite Tracks screen with `ClusterFilterBottomSheet` open,
  * *When* the athlete enters `Min = 15` and `Max = 35` into the custom distance fields and taps "OK",
  * *Then* only clusters with `15000.0 <= refDistance <= 35000.0` are displayed,
  * *And* `ActiveClusterFilterChipsRow` displays `"15 - 35 km"`.

* **Criterion 3: Upper-Bound Only Selection**
  * *Given* the athlete selects the `< 10 km` preset chip (or enters only `Max = 10` with empty Min),
  * *When* applied,
  * *Then* `minDistanceMeters` is `null` and `maxDistanceMeters` is `10000.0`,
  * *And* routes / clusters with `distance <= 10000.0` are displayed,
  * *And* the active chip renders `"< 10 km"`.

* **Criterion 4: Lower-Bound Only Selection**
  * *Given* the athlete selects the `> 100 km` preset chip (or enters only `Min = 100` with empty Max),
  * *When* applied,
  * *Then* `minDistanceMeters` is `100000.0` and `maxDistanceMeters` is `null`,
  * *And* routes / clusters with `distance >= 100000.0` are displayed,
  * *And* the active chip renders `"> 100 km"`.

* **Criterion 5: Distance Filter Chip Removal**
  * *Given* an active distance interval filter applied on Routes or Favorite Tracks,
  * *When* the athlete taps the `✕` removal icon on the active distance chip,
  * *Then* both `minDistanceMeters` and `maxDistanceMeters` are cleared (`null`),
  * *And* the distance chip disappears,
  * *And* the list reactively displays all routes / clusters matching remaining active criteria.

* **Criterion 6: Lossless DataStore JSON Persistence Roundtrip**
  * *Given* a `RouteFilterCriteria` or `ClusterFilterCriteria` with `minDistanceMeters = 25000.0` and `maxDistanceMeters = 50000.0`,
  * *When* serialized to JSON and deserialized via `fromJson()`,
  * *Then* both bounds are accurately restored,
  * *And* given a legacy JSON string without `maxDistanceMeters`, `fromJson()` returns `maxDistanceMeters = null` and preserves `minDistanceMeters`.

### 1.4 System Invariants

1. **Non-Regression on Existing Filter Dimensions**:
   - Routes: search query, route source (Strava, Local GPX, Workout), map selection flag (`isSelected`), and minimum elevation gain MUST remain 100% operational.
   - Favorite Tracks: cluster name query, linked equipment, recordings / hit count threshold, and start location spatial geofence MUST remain 100% operational.
2. **Database Schema Immutability**:
   - Zero SQLite database schema alterations. Filtering is evaluated purely in memory on domain DTOs (`RouteWithPath`, `WorkoutCluster`) within reactive ViewModel flows.
3. **DataStore Backward Compatibility**:
   - Missing JSON properties during deserialization MUST safely default to `null` without throwing `JSONException` or resetting unrelated criteria.
4. **Visual & Design Consistency (Triad Baseline)**:
   - 1:1 parity with `WorkoutFilterBottomSheet` (`REQ-UI-157`) and `ActiveFilterChipsRow`. Chip layout, text field styling (`KeyboardType.Decimal`), and spacing MUST conform to `docs/design_guidelines.md` Sec 5.1 & 5.2.
5. **Parent Ticket Human Decision Gate (Rule 3)**:
   - Moving parent ticket ATT-4291 to `Erledigt` is strictly reserved for the human user in Ceremony 2.

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines `REQ-UI-133` (*Multi-Dimensional Route Filtering, Shared Modal Filter Sheet & Persistent Filter State*) and `REQ-UI-135` (*Multi-Dimensional Favorite Tracks (Lieblingsstrecken) Filtering, Shared Modal Filter Sheet & Persistent Filter State*). Targets `RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`, `RouteFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `RouteTabbedScreen.kt`, and `WorkoutClustersTabsScreen.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-42.1` (`ATT-736`, commit `3c5132da` for routes; `ATT-737`, commit `472be081` for favorite tracks).
* **Root Reason for Existing Formulation**: When route and cluster filtering were originally introduced, the requirements focused on establishing initial multi-dimensional filtering across source, selection, and lower-bound thresholds (`> 10 km`, `> 25 km`, `> 50 km`, `> 100 km`) to prune very short test tracks. Bounded interval filtering and custom numeric entry were deferred. Subsequently, `REQ-UI-157` introduced distance interval filtering for workouts, creating an architectural and visual inconsistency across the app's three primary collection screens.
* **Preservation of Core Invariants**:
  1. All other criteria dimensions (search query, route origin source, map selection, minimum elevation gain, linked equipment, recording hit counts, and spatial start location geofencing) remain 100% functional and backward compatible.
  2. Legacy persisted preferences in `DataStore` lacking `maxDistanceMeters` parse cleanly with `maxDistanceMeters = null`, producing identical filtering results to previous versions.
  3. Single-sided lower-bound distance thresholds (e.g. `> 100 km`) continue to function identically when only `minDistanceMeters` is set.
  4. Predicate evaluations in `matches(...)` remain non-blocking, deterministic, and short-circuiting.

---

## 2. Test Specification (TST-UI-380)

### Test Case 1: Route Distance Interval & Filter Count (`TST-UI-380.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`
* **Preconditions**: Instantiated routes with distances: 5 km, 10 km, 25 km, 45 km, 50 km, 80 km.
* **Action**:
  1. Test bounded interval `[25 km, 50 km]` (`min = 25000.0, max = 50000.0`): assert 25 km, 45 km, 50 km match; 5 km, 10 km, 80 km fail.
  2. Test upper bound only `< 10 km` (`min = null, max = 10000.0`): assert 5 km, 10 km match; 25 km, 45 km, 50 km, 80 km fail.
  3. Test lower bound only `> 50 km` (`min = 50000.0, max = null`): assert 50 km, 80 km match; 5 km, 10 km, 25 km, 45 km fail.
  4. Test `activeFilterCount`: assert `minOnly` gives 1, `maxOnly` gives 1, and `bothBounds` gives 1 (strictly 1 dimension for distance).
* **Expected Result**: All assertions pass; distance boundaries and count logic operate with 100% precision.

### Test Case 2: Route Criteria JSON Roundtrip & Backward Compatibility (`TST-UI-380.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`
* **Preconditions**:
  1. `RouteFilterCriteria` with `minDistanceMeters = 25000.0` and `maxDistanceMeters = 50000.0`.
  2. Legacy JSON string: `{"query":"Alps","minDistanceMeters":10000.0}` (lacking `maxDistanceMeters`).
* **Action**:
  1. Call `toJson()` on the two-bound instance, deserialize with `RouteFilterCriteria.fromJson()`, and compare properties.
  2. Call `RouteFilterCriteria.fromJson(legacyJson)`.
* **Expected Result**:
  1. Deserialized object matches original with both `minDistanceMeters = 25000.0` and `maxDistanceMeters = 50000.0`.
  2. Legacy JSON deserializes safely with `maxDistanceMeters = null` and `minDistanceMeters = 10000.0`.

### Test Case 3: Favorite Tracks (Cluster) Distance Interval & Filter Count (`TST-UI-380.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
* **Preconditions**: Instantiated clusters with `refDistance`: 8 km, 12.5 km, 25 km, 35 km, 50 km, 75 km.
* **Action**:
  1. Test bounded interval `[10 km, 35 km]` (`min = 10000.0, max = 35000.0`): assert 12.5 km, 25 km, 35 km match; 8 km, 50 km, 75 km fail.
  2. Test upper bound only `< 10 km` (`max = 10000.0`): assert 8 km matches; 12.5 km, 25 km fail.
  3. Test lower bound only `> 50 km` (`min = 50000.0`): assert 50 km, 75 km match; 35 km fails.
  4. Test `activeFilterCount`: assert `minDistanceMeters != null || maxDistanceMeters != null` adds strictly 1 to count.
* **Expected Result**: All assertions pass.

### Test Case 4: Cluster Criteria JSON Roundtrip & Backward Compatibility (`TST-UI-380.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
* **Preconditions**:
  1. `ClusterFilterCriteria` with `minDistanceMeters = 15000.0` and `maxDistanceMeters = 40000.0`.
  2. Legacy JSON string without `maxDistanceMeters`.
* **Action**: Execute JSON roundtrip and legacy parsing.
* **Expected Result**: Full field preservation and safe null resolution for legacy payloads.

### Test Case 5: Active Distance Filter Chip Formatting & Dismissal Contract (`TST-UI-380.5`)
* **Scope**: Unit / Contract Test
* **Target Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
* **Preconditions**: Formatted string generation logic for distance filter chips.
* **Action**:
  1. Both bounds: verify formatting produces `"$min - $max km"` (e.g. `"25 - 50 km"`).
  2. Min only: verify formatting produces `"> $min km"` (e.g. `"> 25 km"`).
  3. Max only: verify formatting produces `"< $max km"` (e.g. `"< 50 km"`).
  4. Non-integer values: verify `12500.0` formats as `"12.5"`, whereas `25000.0` formats as `"25"`.
  5. Removal callback: verify `criteria.copy(minDistanceMeters = null, maxDistanceMeters = null)` resets both bounds.
* **Expected Result**: Exact string format matches and clean state reset.

### Test Case 6: 9-Language Localization & Format Specifier Audit (`TST-UI-380.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Locales Audited**: `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), `values-pt/` (PT)
* **Resource Keys Verified**:
  - `filter_section_distance`:
    - EN: `Distance`
    - DE: `Distanz`
    - ES: `Distancia`
    - FR: `Distance`
    - IT: `Distanza`
    - JA: `距離`
    - NL: `Afstand`
    - PL: `Dystans`
    - PT: `Distância`
  - `filter_min_label`:
    - EN: `Min`
    - DE: `Min.`
    - ES: `Mín.`
    - FR: `Min`
    - IT: `Min.`
    - JA: `最小`
    - NL: `Min.`
    - PL: `Min.`
    - PT: `Mín.`
  - `filter_max_label`:
    - EN: `Max`
    - DE: `Max.`
    - ES: `Máx.`
    - FR: `Max`
    - IT: `Max.`
    - JA: `最大`
    - NL: `Max.`
    - PL: `Maks.`
    - PT: `Máx.`
  - `filter_distance_interval_format`: `%1$s - %2$s km` (Identical across all 9 locales)
  - `filter_min_distance_format`: `> %1$s km` (Identical across all 9 locales)
  - `filter_max_distance_format`: `< %1$s km` (Identical across all 9 locales)
  - `sort_elevation_gain`:
    - EN: `Elevation Gain`
    - DE: `Höhenmeter`
    - ES: `Desnivel`
    - FR: `Dénivelé`
    - IT: `Dislivello`
    - JA: `獲得標高`
    - NL: `Hoogtemeters`
    - PL: `Przewyższenie`
    - PT: `Desnível`
* **Expected Result**: 100% parity across all 9 locales, zero missing entries, zero format specifier mismatches.

### Test Case 7: Clean-Room Regression Suite (`TST-UI-380.7`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.
* **Expected Result**: `BUILD SUCCESSFUL` with 0 failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-380.1` | Unit | `RouteFilterCriteria.matches` / `activeFilterCount` | `REQ-UI-417.1` | Specified |
| `TST-UI-380.2` | Unit | `RouteFilterCriteria.toJson` / `fromJson` | `REQ-UI-417.1` | Specified |
| `TST-UI-380.3` | Unit | `ClusterFilterCriteria.matches` / `activeFilterCount` | `REQ-UI-417.1` | Specified |
| `TST-UI-380.4` | Unit | `ClusterFilterCriteria.toJson` / `fromJson` | `REQ-UI-417.1` | Specified |
| `TST-UI-380.5` | Unit / Contract | Active Filter Chips formatting & dismissal | `REQ-UI-417.3` | Specified |
| `TST-UI-380.6` | Localization | `TranslationParityTest` (9 locales) | `REQ-UI-417.4`, `REQ-UI-106` | Specified |
| `TST-UI-380.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
