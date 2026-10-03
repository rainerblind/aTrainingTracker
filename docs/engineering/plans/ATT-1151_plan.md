# Stage 3 Implementation Plan: Multi-Region Period Map Viewport Detection (ATT-1151)

## 1. Executive Summary & Goal
When an athlete logs workouts across distant geographical locations during a single aggregation period (such as Europe and North America in a yearly summary), the legacy system computes a global bounding box spanning all coordinate extrema. This causes the initial map viewport in `PeriodMapScreen` and the summary thumbnail in `PeriodSummaryCard` to center squarely on Greenland and the empty North Atlantic Ocean (The "Greenland Anomaly").

This implementation introduces `SpatialRegionEngine` to partition period workouts into geographical activity regions, automatically focus camera viewports on the athlete's primary training region, provide an interactive, horizontally scrollable region switcher chip row when multiple regions are present ($k > 1$), and eliminate empty ocean viewports while maintaining 100% legacy parity for single-region training periods ($k = 1$).

---

## 2. Requirement Traceability Matrix (ASPICE SWE.3)
This plan implements and traces the following requirements from `docs/requirements.md` and `docs/tests.md`:

| Requirement ID | Description | Target Component(s) | Verification ID |
| :--- | :--- | :--- | :--- |
| **REQ-PER-013** | **Multi-Region Period Map Viewport Detection & Adaptive Bounding.**<br>Partition workouts into distinct geographical activity regions, prioritize dominant region, center camera on dominant region, support antimeridian wrapping, and provide interactive region switching. | `SpatialRegionEngine.kt`, `PeriodsViewModel.kt`, `InteractivePeriodMap.kt`, `PeriodMapScreen.kt`, `PeriodSummaryCard.kt` | `TST-PER-019` |
| **REQ-PER-012** *(Invariant)* | **Period Map Scalability, Memory Budget & Adaptive Layering Invariant.**<br>Vector polyline and marker budget constraints ($\le 30$ workouts) MUST be preserved. | `PeriodsViewModel.kt`, `PeriodMapScreen.kt` | `TST-PER-018` |

---

## 3. Architectural Design & Component Breakdown

### 3.1 Spatial Region Engine (`SpatialRegionEngine.kt`)
- **Package**: `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist`
- **Configuration Constants (`SpatialRegionConfig`)**:
  ```kotlin
  object SpatialRegionConfig {
      const val REGION_CLUSTER_THRESHOLD_METERS = 75_000.0  // 75 km
      const val SINGLE_REGION_ENVELOPE_METERS = 60_000.0    // 60 km
  }
  ```
- **Data Models**:
  ```kotlin
  data class SpatialRegion(
      val id: String,                  // e.g. "region_0"
      val label: String,               // e.g. "Region 1"
      val bounds: LatLngBounds,        // Bounding box tightly enclosing this region
      val center: LatLng,              // Centroid
      val workoutCount: Int,
      val totalDistanceMeters: Double,
      val mostRecentTimestampS: Long,
      val isPrimary: Boolean,
      val workoutIds: Set<Long>
  )
  ```
- **Core Methods**:
  1. `detectRegions(workouts: List<WorkoutData>): List<SpatialRegion>`:
     - Extracts valid coordinates (filtering null, empty, and `0.0, 0.0` sentinel coordinates).
     - Single-Region Fast Path ($O(1)$): Returns 1 region immediately if valid workout count $\le 1$ or if global bounding box diagonal $< 500\text{ km}$.
     - Connected Component Partitioning: Clusters workouts within geodesic Haversine distance $D_{\text{cluster}} = 600\text{ km}$.
     - Dominant Region Identification: Ranks regions deterministically by (a) Workout count (descending), (b) Total active distance (descending), (c) Most recent workout timestamp (descending). Top region has `isPrimary = true` (index 0).
  2. `buildNormalizedBounds(points: List<LatLng>): LatLngBounds`:
     - Computes bounding box with antimeridian ($\pm 180^\circ$) support. Detects whether longitudinal span in wrapped $[0^\circ, 360^\circ)$ space is narrower than standard space, constructing `LatLngBounds(southwest, northeast)` with `southwest.longitude > northeast.longitude` when crossing the antimeridian line.
     - Clamps latitudes to $[-85.0, 85.0]$ for Web Mercator compliance.

### 3.2 View Model & State Management (`PeriodsViewModel.kt`)
- **PeriodMapState Updates**:
  ```kotlin
  data class PeriodMapState(
      val tracks: List<MapTrack> = emptyList(),
      val workoutIdToHeatmapPathMap: Map<Long, List<LatLng>> = emptyMap(),
      val memberMarkers: List<PeriodPeakMarker> = emptyList(),
      val regions: List<SpatialRegion> = emptyList(),
      val selectedRegionId: String? = null,
      val isLoading: Boolean = false,
  )
  ```
- **Execution & Persistence Contract**:
  - `showPeriodMap(summary)`:
    - Runs `SpatialRegionEngine.detectRegions(workouts)` strictly on `Dispatchers.Default` alongside polyline decoding.
    - Sets initial `selectedRegionId` to `primaryRegion.id` or restores persisted selection from `SavedStateHandle`.
  - Add `selectRegion(regionId: String?)`:
    - Updates `_mapState` and saves `selectedRegionId` to `SavedStateHandle`.

### 3.3 Interactive Period Map View (`PeriodMapScreen.kt` & `InteractivePeriodMap.kt`)
- **Initial Bounds & Camera Fitting**:
  - `InteractivePeriodMap.kt`:
    - Uses `selectedRegion?.bounds ?: primaryRegion?.bounds ?: periodBounds` as initial bounds.
    - For multi-region periods, initial camera focuses tightly on the dominant region instead of ocean tiles.
- **Horizontal Region Switcher Chip Row (`PeriodMapScreen.kt`)**:
  - When `mapState.regions.size > 1`, renders horizontal scrollable chip bar (`Modifier.horizontalScroll(rememberScrollState())`) at the top of the map area.
  - Chips rendered:
    - `[ Primary: Region 1 (100) ]` (selected state if `selectedRegionId == region.id`)
    - `[ Region 2 (10) ]`
    - `[ All Regions (110) ]` (selected state if `selectedRegionId == null`)
  - Tapping a region chip invokes `viewModel.selectRegion(region.id)` and animates the camera to `region.bounds`.
  - Tapping "All Regions" invokes `viewModel.selectRegion(null)` and animates camera to global aggregate bounds.
  - When `mapState.regions.size <= 1`, chip row is hidden (`Visibility.GONE`), maintaining 100% visual fidelity for normal periods.

### 3.4 Period Summary Card Thumbnail (`PeriodSummaryCard.kt`)
- When displaying thumbnail map in `PeriodMultiWorkoutMap`:
  - If multiple regions are detected, focus camera on dominant region's bounds rather than global extrema.
  - Eliminates empty ocean thumbnail cards across period lists (Week, Month, Year).

### 3.5 9-Language Localization Parity (`strings.xml`)
Add the following keys across all 9 localized `strings.xml` files:
- `workout_periods__region_format`: `"Region %1$d (%2$d)"` / `"Region %1$d (%2$d)"`
- `workout_periods__primary_region_format`: `"Primary: Region %1$d (%2$d)"` / `"Hauptregion: Region %1$d (%2$d)"`
- `workout_periods__all_regions_format`: `"All Regions (%1$d)"` / `"Alle Regionen (%1$d)"`

---

## 4. Verification & Testing Strategy

### 4.1 Unit Testing (`SpatialRegionEngineTest.kt`)
- `testSingleRegionWithinTerritory_returnsSingleRegionWithExactBounds()`
- `testMultiContinentWorkouts_partitionsIntoEuropeAndUSA()`
- `testDominantRegionRanking_selectsHigherWorkoutCountAsPrimary()`
- `testDominantRegionRanking_breaksTiesByDistanceAndRecency()`
- `testAntimeridianCrossing_buildsWrappedBoundsWithoutWorldSpan()`
- `testFastPathBoundary_450kmReturnsSingleRegion_700kmReturnsTwoRegions()`
- `testNonGpsAndSentinelCoordinates_safelyBypassed()`

### 4.2 Integration & UI Testing
- `PeriodsViewModelTest`: Verifies `regions` populated on `showPeriodMap` and `selectedRegionId` retained.
- `PeriodMapScreenTest`: Verifies region chip row visibility when $k > 1$ vs $k = 1$.
- `PeriodMapAdaptiveLayeringTest`: Verifies `REQ-PER-012` invariant ($\le 30$ vector tracks) remains intact.

### 4.3 Clean-Room Regression
- Execute `./gradlew testDebugUnitTest` across all modules with 0 failures and 0 regressions.
