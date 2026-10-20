# Stage 3: Implementation Plan - ATT-4291: Support distance interval filtering (min/max range and preset intervals) in Routes and Favorite Routes

**Ticket**: [ATT-4291](https://rainerblind.atlassian.net/browse/ATT-4291)  
**Sub-task**: [ATT-4297](https://rainerblind.atlassian.net/browse/ATT-4297) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-417` (*Distance Interval Filtering (Min/Max Range and Preset Intervals) in Routes and Favorite Routes*, refining `REQ-UI-133` and `REQ-UI-135`)  
**Test Mapping**: `TST-UI-380` (`TST-UI-380.1` – `TST-UI-380.7`)  
**Branch**: `feature/ATT-4291`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

Currently, route filtering (`RouteFilterCriteria` / `RouteFilterBottomSheet`) and favorite route / workout cluster filtering (`ClusterFilterCriteria` / `ClusterFilterBottomSheet`) only support single-sided lower-bound distance thresholds (`minDistanceMeters`: `> 10 km`, `> 25 km`, `> 50 km`, `> 100 km`).

This creates significant usability friction for athletes with extensive route libraries:
* When an athlete wants to find a medium-length route or favorite track for an after-work session (e.g., between `25 km` and `50 km`), selecting `> 25 km` continues to include all 80 km, 100 km, and 150+ km long rides, forcing tedious manual scrolling and inspection.
* There is currently no way to specify an upper bound (`maxDistanceMeters`), nor to enter custom kilometer limits outside the four hardcoded chip values.
* In contrast, the workout history filtering architecture (`WorkoutFilterBottomSheet` / `WorkoutFilterCriteria`, introduced in `REQ-UI-157`) already provides a mature, highly intuitive **Distance Interval** interface featuring preset range chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) plus two-way synchronized custom `Min (km)` and `Max (km)` decimal input fields.
* Active filter chips on the Routes screen (`ActiveRouteFilterChipsRow`) and Favorite Routes screen (`ActiveClusterFilterChipsRow`) only display single-sided `> X km` labels and cannot reflect bounded intervals or upper limits.

This implementation plan establishes the architectural blueprint for Stage 4 construction, extending `RouteFilterCriteria` and `ClusterFilterCriteria` to support full distance interval filtering (`[minDistanceMeters, maxDistanceMeters]`), restructuring the bottom sheets to match `WorkoutFilterBottomSheet`, and harmonizing active filter chip formatting and dismissal across all screens while strictly preserving existing system invariants and backward compatibility.

---

## 2. Traceability & Requirements Mapping

Every requirement in `REQ-UI-417` and test case in `TST-UI-380` is mapped directly to atomic Stage 4 implementation steps:

| Requirement / Test Case | Description | Stage 4 Implementation Step | Verification Method |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-417.1`** | Domain model extensions: `maxDistanceMeters` field, `activeFilterCount` counting distance once if either/both bounds set, short-circuit `matches` evaluation, and DataStore JSON serialization/deserialization with safe fallback for legacy records. | **Step 1** (`RouteFilterCriteria.kt`)<br>**Step 2** (`ClusterFilterCriteria.kt`) | Unit tests `TST-UI-380.1`, `TST-UI-380.2`, `TST-UI-380.3`, `TST-UI-380.4` |
| **`REQ-UI-417.2`** | Filter Bottom Sheets: Separate Distance from Elevation Gain in `RouteFilterBottomSheet.kt`; implement preset chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) and synchronized custom `Min (km)` / `Max (km)` decimal text fields in both `RouteFilterBottomSheet.kt` and `ClusterFilterBottomSheet.kt`. | **Step 3** (`RouteFilterBottomSheet.kt`)<br>**Step 4** (`ClusterFilterBottomSheet.kt`) | Interactive UI verification & unit tests |
| **`REQ-UI-417.3`** | Active Filter Chips & Dismissal: Unified removable distance chip rendering bounded (`"25 - 50 km"`), lower-bound (`"> 25 km"`), or upper-bound (`"< 50 km"`) labels; updating dismissal callback to reset both `minDistanceMeters = null` and `maxDistanceMeters = null`. | **Step 5** (`ActiveRouteFilterChipsRow.kt`, `RouteTabbedScreen.kt`)<br>**Step 6** (`ActiveClusterFilterChipsRow.kt`, `WorkoutClustersTabsScreen.kt`) | Unit test `TST-UI-380.5` |
| **`REQ-UI-417.4`** | 100% 9-Language Localization Parity across all strings used (`filter_section_distance`, `filter_min_label`, `filter_max_label`, `filter_distance_interval_format`, `filter_min_distance_format`, `filter_max_distance_format`, `sort_elevation_gain`). | **Step 7** (`TranslationParityTest.kt`) | Localization test `TST-UI-380.6` |
| **`REQ-PRO-001`** | Full test suite clean-room regression pass without any broken tests. | **Step 8** (Clean-room test run) | Regression suite `TST-UI-380.7` (`./gradlew testDebugUnitTest`) |

---

## 3. System Invariants & Preserved Behavior

1. **Zero Regression on Existing Filter Dimensions**:
   - Routes: Free-text search query (name and description), route origin source (`Strava`, `Local GPX`, `Workout`), map selection / visibility flag (`isSelected`), and minimum elevation gain threshold (`minElevationGainMeters`) MUST remain 100% operational.
   - Favorite Tracks: Cluster name query, linked equipment name (`equipmentName`), minimum recordings / hit count threshold (`minHitCount`), and spatial starting location geofence (`startLocationLat`, `startLocationLng`, `startLocationRadiusM`) MUST remain 100% operational.
2. **Lossless DataStore JSON Persistence & Backward Compatibility**:
   - `RouteFilterCriteria` and `ClusterFilterCriteria` serialized in prior releases lack `maxDistanceMeters`. Deserialization via `fromJson()` MUST safely default `maxDistanceMeters` to `null` without throwing `JSONException`, failing silent fallbacks, or resetting unrelated criteria.
   - Symmetrically, serialization via `toJson()` MUST omit `maxDistanceMeters` when `null` to minimize payload footprint, and serialize standard floating-point numbers when present.
3. **Thread Safety & Architectural Layering (SWE.2)**:
   - Filter criteria evaluation is performed entirely in memory on domain DTOs (`RouteWithPath`, `WorkoutCluster`) inside reactive Kotlin `StateFlow` pipelines (`RoutesViewModel`, `WorkoutClustersViewModel`).
   - Zero SQLite database schema alterations. No database migrations, index alterations, or background service modifications are required.
4. **Code Lock Invariant (Stage 3)**:
   - No production code in `app/src/main/...` or test code in `app/src/test/...` is modified during Stage 3. All code modifications are strictly deferred to Stage 4 after Gate 3 sign-off.
5. **Sub-Task Self-Sufficiency & Direct Completion (Rule 3 / Rule 45)**:
   - Sub-task `ATT-4297` (`[Impl-Plan]`) transitions directly to `Erledigt` via transition `freigabe` upon passing Gate 3 independent audit, which synchronously advances parent ticket `ATT-4291` to `Implementation`.
6. **Parent Ticket Human Gate Invariance (Rule 3)**:
   - Moving parent ticket `ATT-4291` to `Erledigt` remains strictly reserved for the human user in Ceremony 2 (Sprint-End Joint Review).

---

## 4. Proposed Architectural Changes

```text
┌────────────────────────────────────────────────────────────────────────┐
│ DOMAIN LAYER (Immutable Filter DTOs)                                    │
│ • RouteFilterCriteria(minDistanceMeters, maxDistanceMeters, ...)       │
│ • ClusterFilterCriteria(minDistanceMeters, maxDistanceMeters, ...)     │
│   - activeFilterCount: (min != null || max != null) counts as 1 dim    │
│   - matches(): short-circuits min and max bounds                       │
│   - toJson() / fromJson(): lossless persistence with null-safety       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER: MODAL BOTTOM SHEETS (REQ-UI-417.2)                  │
│ • RouteFilterBottomSheet.kt                                            │
│   - Section: Distance (R.string.filter_section_distance)               │
│     * Preset chips: < 10 km, 10-25 km, 25-50 km, 50-100 km, > 100 km   │
│     * OutlinedTextFields: Min (km), Max (km) (KeyboardType.Decimal)    │
│   - Section: Elevation Gain (R.string.sort_elevation_gain)             │
│     * Retains: > 100 m, > 250 m, > 500 m, > 1000 m                     │
│ • ClusterFilterBottomSheet.kt                                          │
│   - Section 3: Refactored to identical Distance Interval Section       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER: ACTIVE FILTER CHIPS ROW (REQ-UI-417.3)             │
│ • ActiveRouteFilterChipsRow.kt & ActiveClusterFilterChipsRow.kt        │
│   - Render single removable chip for distance interval:                │
│     * [min, max]: stringResource(R.string.filter_distance_interval_format, minKm, maxKm)
│     * [min only]: stringResource(R.string.filter_min_distance_format, minKm)
│     * [max only]: stringResource(R.string.filter_max_distance_format, maxKm)
│   - Removal callback onRemoveDistanceRange clears min & max bounds     │
│ • RouteTabbedScreen.kt & WorkoutClustersTabsScreen.kt                   │
│   - Wire onRemoveDistanceRange to clear both bounds                    │
└────────────────────────────────────────────────────────────────────────┘
```

### Component 1: Domain Criteria Models (`RouteFilterCriteria.kt`, `ClusterFilterCriteria.kt`)
* **Layer**: UI / Domain Model (`com.atrainingtracker.trainingtracker.ui.routes`, `com.atrainingtracker.trainingtracker.ui.clusters`).
* **Modifications**:
  1. Add `val maxDistanceMeters: Double? = null` with default `null` to the primary constructor.
  2. Update `activeFilterCount`: replace `if (minDistanceMeters != null) count++` with `if (minDistanceMeters != null || maxDistanceMeters != null) count++`.
  3. In `matches(route: RouteWithPath)` and `matches(cluster: WorkoutCluster, linkedEquipment: Set<String>)`:
     ```kotlin
     if (minDistanceMeters != null && distance < minDistanceMeters) return false
     if (maxDistanceMeters != null && distance > maxDistanceMeters) return false
     ```
  4. In `toJson()`: serialize `maxDistanceMeters` if non-null via `maxDistanceMeters?.let { json.put("maxDistanceMeters", it) }`.
  5. In `fromJson()`: deserialize `maxDistanceMeters` via `if (json.has("maxDistanceMeters")) json.getDouble("maxDistanceMeters") else null`.

### Component 2: Bottom Sheet Filter Presentation (`RouteFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`)
* **Layer**: Jetpack Compose UI.
* **Modifications in `RouteFilterBottomSheet.kt`**:
  1. Manage local mutable state:
     ```kotlin
     var localMinDistanceMeters by remember(criteria.minDistanceMeters) { mutableStateOf(criteria.minDistanceMeters) }
     var localMaxDistanceMeters by remember(criteria.maxDistanceMeters) { mutableStateOf(criteria.maxDistanceMeters) }
     var minDistanceText by remember(criteria.minDistanceMeters) {
         mutableStateOf(criteria.minDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } } ?: "")
     }
     var maxDistanceText by remember(criteria.maxDistanceMeters) {
         mutableStateOf(criteria.maxDistanceMeters?.let { (it / 1000.0).let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } } ?: "")
     }
     ```
  2. Update `onClearAll` to clear `localMinDistanceMeters = null`, `localMaxDistanceMeters = null`, `minDistanceText = ""`, `maxDistanceText = ""`.
  3. Update `onApply` to forward `minDistanceMeters = localMinDistanceMeters, maxDistanceMeters = localMaxDistanceMeters`.
  4. Split Section 4:
     - **Distance Section** (`R.string.filter_section_distance`):
       - FlowRow with 5 preset chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`). Clicking a chip populates `localMinDistanceMeters`, `localMaxDistanceMeters`, `minDistanceText`, and `maxDistanceText`; clicking an active chip clears both.
       - Row with two equal-weight `OutlinedTextField`s for `Min (km)` and `Max (km)` using `KeyboardOptions(keyboardType = KeyboardType.Decimal)`. Editing text converts kilometers to meters (`str.toDoubleOrNull()?.times(1000.0)`), updates the numeric bounds, and automatically deselects mismatched preset chips.
     - **Elevation Gain Section** (`R.string.sort_elevation_gain`):
       - Dedicated header displaying `R.string.sort_elevation_gain`.
       - FlowRow containing the existing 4 elevation chips (`> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`).
* **Modifications in `ClusterFilterBottomSheet.kt`**:
  1. Add identical local state variables and handlers for `localMaxDistanceMeters`, `minDistanceText`, and `maxDistanceText`.
  2. Restructure Section 3: replace the single-sided distance chips with the identical Distance Interval section (preset chips + synchronized `Min (km)` / `Max (km)` text fields).

### Component 3: Active Filter Chips & Host Screen Wiring (`ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`)
* **Layer**: Jetpack Compose UI.
* **Modifications**:
  1. In `ActiveRouteFilterChipsRow.kt`:
     - Add `onRemoveDistanceRange: () -> Unit = {}` and maintain `onRemoveMinDistance: () -> Unit = onRemoveDistanceRange` for full signature compatibility.
     - When `minDistanceMeters != null || maxDistanceMeters != null`, render a single `RemovableFilterChip` with:
       - Both set: `stringResource(R.string.filter_distance_interval_format, minKm, maxKm)`
       - Min only: `stringResource(R.string.filter_min_distance_format, minKm)`
       - Max only: `stringResource(R.string.filter_max_distance_format, maxKm)`
       - Formatting: whole numbers formatted as integer strings (e.g. `"25"`), non-integers formatted with decimal points (e.g. `"12.5"`).
       - `onRemove = onRemoveDistanceRange`.
  2. In `RouteTabbedScreen.kt`:
     - Wire `onRemoveDistanceRange = { onUpdateFilterCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) } }`.
  3. In `ActiveClusterFilterChipsRow.kt`:
     - Apply identical signature and unified interval chip rendering.
  4. In `WorkoutClustersTabsScreen.kt`:
     - Wire `onRemoveDistanceRange = { viewModel.updateFilterCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) } }`.

### UI Consistency (Rule 23 — Mandatory UI Audit)
* **Reference Screen / Component**: `WorkoutFilterBottomSheet.kt` and `ActiveFilterChipsRow.kt` (`com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist`, introduced in `REQ-UI-157` / Sprint 2026-42.2).
* **Reused Components**:
  - `FilterBottomSheetScaffold` (`com.atrainingtracker.trainingtracker.ui.common.filters.FilterBottomSheetScaffold`)
  - `RemovableFilterChip` (`com.atrainingtracker.trainingtracker.ui.common.filters.RemovableFilterChip`)
  - Material 3 `FilterChip` and `OutlinedTextField`
* **Theme Tokens**:
  - **Shapes**: Standard `MaterialTheme.shapes.small` (8.dp) for chips; Material 3 default rounded corners for `OutlinedTextField`.
  - **Spacing**: `6.dp` spacer between section header and chips; `8.dp` horizontal and `4.dp` vertical chip spacing in `FlowRow`; `12.dp` horizontal spacing between `Min` and `Max` text fields in `Row`; `16.dp` horizontal padding for screens and chip rows.
  - **Colors**: `MaterialTheme.colorScheme.primary` for section titles; `MaterialTheme.colorScheme.surface` / `surfaceContainer` for bottom sheet surfaces; `MaterialTheme.colorScheme.onSurface` for text.
  - **Typography**: `MaterialTheme.typography.titleMedium` for section headings; `MaterialTheme.typography.bodyMedium` for chip text and text fields.
* **New One-Off Styles & Justification**: None. 100% compliant with existing Material 3 design tokens and `docs/design_guidelines.md` (Sections 1.4, 5.1, 5.2, 5.3, 5.4).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Extend `RouteFilterCriteria.kt` Domain Model
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteria.kt`
* **Changes**:
  - Add `val maxDistanceMeters: Double? = null` with default `null` to constructor.
  - Update `activeFilterCount` to evaluate `if (minDistanceMeters != null || maxDistanceMeters != null) count++`.
  - Add upper bound predicate in `matches(route: RouteWithPath)`: `if (maxDistanceMeters != null && route.summary.distance > maxDistanceMeters) return false`.
  - In `toJson()`: add `maxDistanceMeters?.let { json.put("maxDistanceMeters", it) }`.
  - In `fromJson()`: add `maxDistanceMeters = if (json.has("maxDistanceMeters")) json.getDouble("maxDistanceMeters") else null`.
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteFilterCriteriaTest"`

### Step 2: Extend `ClusterFilterCriteria.kt` Domain Model
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteria.kt`
* **Changes**:
  - Add `val maxDistanceMeters: Double? = null` with default `null` to constructor.
  - Update `activeFilterCount` to evaluate `if (minDistanceMeters != null || maxDistanceMeters != null) count++`.
  - Add upper bound predicate in `matches(cluster: WorkoutCluster, linkedEquipment: Set<String>)`: `if (maxDistanceMeters != null && cluster.refDistance > maxDistanceMeters) return false`.
  - In `toJson()`: add `maxDistanceMeters?.let { json.put("maxDistanceMeters", it) }`.
  - In `fromJson()`: add `maxDistanceMeters = if (json.has("maxDistanceMeters")) json.getDouble("maxDistanceMeters") else null`.
* **Targeted Verification**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteriaTest"`

### Step 3: Restructure `RouteFilterBottomSheet.kt` UI
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt`
* **Changes**:
  - Add `localMaxDistanceMeters`, `minDistanceText`, and `maxDistanceText` states with decimal text initialization.
  - In `onClearAll`: reset `localMinDistanceMeters = null`, `localMaxDistanceMeters = null`, `minDistanceText = ""`, `maxDistanceText = ""`.
  - In `onApply`: populate both `minDistanceMeters` and `maxDistanceMeters`.
  - Separate Section 4 into:
    - **Distance Section** (`R.string.filter_section_distance`): 5 preset interval chips (`< 10 km`, `10 - 25 km`, `25 - 50 km`, `50 - 100 km`, `> 100 km`) and synchronized `Min (km)` / `Max (km)` decimal `OutlinedTextField`s.
    - **Elevation Gain Section** (`R.string.sort_elevation_gain`): retaining the 4 elevation gain chips (`> 100 m`, `> 250 m`, `> 500 m`, `> 1000 m`).

### Step 4: Restructure `ClusterFilterBottomSheet.kt` UI
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt`
* **Changes**:
  - Add `localMaxDistanceMeters`, `minDistanceText`, and `maxDistanceText` states.
  - In `onClearAll` and `onApply`: update handlers for both distance bounds.
  - In Section 3: replace single-sided distance chips with the identical Distance Interval section (preset chips + synchronized `Min (km)` / `Max (km)` decimal text fields).

### Step 5: Update `ActiveRouteFilterChipsRow.kt` and `RouteTabbedScreen.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ActiveRouteFilterChipsRow.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedScreen.kt`
* **Changes**:
  - In `ActiveRouteFilterChipsRow`: add `onRemoveDistanceRange: () -> Unit = {}`, alias `onRemoveMinDistance = onRemoveDistanceRange`.
  - Render unified distance chip when `minDistanceMeters != null || maxDistanceMeters != null` with interval formatting (`"$min - $max km"`, `"> $min km"`, `"< $max km"`).
  - In `RouteTabbedScreen`: pass `onRemoveDistanceRange = { onUpdateFilterCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) } }`.

### Step 6: Update `ActiveClusterFilterChipsRow.kt` and `WorkoutClustersTabsScreen.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt`
* **Changes**:
  - In `ActiveClusterFilterChipsRow`: add `onRemoveDistanceRange: () -> Unit = {}`, alias `onRemoveMinDistance = onRemoveDistanceRange`.
  - Render unified distance chip with interval formatting.
  - In `WorkoutClustersTabsScreen`: pass `onRemoveDistanceRange = { viewModel.updateFilterCriteria { it.copy(minDistanceMeters = null, maxDistanceMeters = null) } }`.

### Step 7: Author Comprehensive Unit Tests (`TST-UI-380.1` – `TST-UI-380.5`)
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterCriteriaTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterCriteriaTest.kt`
* **Changes**:
  - `TST-UI-380.1`: Test route distance interval matching (`[25 km, 50 km]`, `< 10 km`, `> 50 km`) and verify `activeFilterCount` increments by strictly 1.
  - `TST-UI-380.2`: Test route JSON serialization roundtrip with both bounds and test backward compatibility when parsing legacy JSON without `maxDistanceMeters`.
  - `TST-UI-380.3`: Test cluster distance interval matching (`[10 km, 35 km]`, `< 10 km`, `> 50 km`) and verify `activeFilterCount`.
  - `TST-UI-380.4`: Test cluster JSON roundtrip and legacy parsing.
  - `TST-UI-380.5`: Test active chip string formatting logic (`"$min - $max km"`, `"> $min km"`, `"< $max km"`, decimal formatting) and verify two-bound dismissal state reset.
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteFilterCriteriaTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteriaTest"
  ```

### Step 8: Localization Parity Verification (`TST-UI-380.6`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ```

### Step 9: Clean-Room Regression Verification (`TST-UI-380.7`)
* **Verification Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

### Verification Plan
1. **Unit Testing**:
   Run targeted tests for domain models and UI criteria:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteFilterCriteriaTest"
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteriaTest"
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesViewModelFilterTest"
   ```
2. **Localization Parity**:
   Verify all strings exist across all 9 supported locales:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
   ```
3. **Clean-Room Regression Suite**:
   Execute full test suite in Stage 5:
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. **Build & Assembly Verification**:
   Verify debug APK compilation:
   ```bash
   ./gradlew assembleDebug
   ```

### Rollback Plan
* The changes are isolated on git branch `feature/ATT-4291` branched from `sprint/2026-43.1`.
* In the event of an unresolvable failure or rejection during Stage 5 verification, the branch can be cleanly reset via `git reset --hard` or discarded via `git checkout sprint/2026-43.1 && git branch -D feature/ATT-4291` without leaving partial artifacts or affecting the sprint integration baseline.
* DataStore persistence uses safe JSON defaults (`maxDistanceMeters = null` when missing), guaranteeing zero persistence migration corruption if reverted.
