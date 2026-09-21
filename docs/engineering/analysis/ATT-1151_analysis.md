# Stage 1 System Analysis: Multi-Region Period Map Viewport Detection (ATT-1151)

## 1. Executive Summary & Problem Formulation
### 1.1 The Greenland Anomaly (Root Cause Analysis)
In `aTrainingTracker`, training sessions are aggregated into hierarchical periods: **Day**, **Week**, **Month**, and **Year**.
When an athlete trains in geographically distant locations during the same period—such as Europe (e.g. Stuttgart, Germany: ~48.7° N, 9.2° E) and North America (e.g. California / Colorado, USA: ~37.7° N, -122.4° W)—the existing bounding box calculation naively computes a single rectangular bounding box covering all points:
$$\text{minLat} = \min(lat_i), \quad \text{maxLat} = \max(lat_i)$$
$$\text{minLng} = \min(lng_i), \quad \text{maxLng} = \max(lng_i)$$

When `minLng = -122.4°` and `maxLng = 9.2°`, the resulting bounding box spans nearly 132° of longitude across the North Atlantic Ocean. The map camera auto-fits this bounding box via `CameraUpdateFactory.newLatLngBounds(bounds, padding)`.
As a direct consequence:
1. The map viewport is centered squarely on the North Atlantic Ocean and **Greenland** / Iceland.
2. The user's actual workouts in Europe and the USA are shrunk into minuscule, unrecognizable dots at the extreme margins of the screen.
3. 99% of the rendered map viewport contains empty ocean or uninhabited polar ice sheets where zero training took place.

### 1.2 Architectural Scope
The anomaly occurs in two places:
1. **Interactive Period Map View** (`PeriodMapScreen.kt` & `InteractivePeriodMap.kt`): When entering the detailed map view for a multi-region period (e.g., Year 2024).
2. **Period List Card Thumbnail Map** (`PeriodSummaryCard.kt` -> `PeriodMultiWorkoutMap`): The thumbnail map embedded in each card on the periods tab.

---

## 2. Technical Analysis of Existing Implementation

### 2.1 Database & Persistence Layer (`PeriodSummaries.db`)
- `PeriodSummariesContract` stores single global extrema per period:
  `COLUMN_BOUND_MIN_LAT`, `COLUMN_BOUND_MIN_LNG`, `COLUMN_BOUND_MAX_LAT`, `COLUMN_BOUND_MAX_LNG`.
- These columns represent the strict geometric bounding box of all workouts in the period.
- Rolling up from days to weeks/months/years (`PeriodsRepository.aggregateChildrenToParent`):
  ```kotlin
  val spatialChildren = children.filter { it.minLat < 90.0 }
  val minLat = if (spatialChildren.isNotEmpty()) spatialChildren.minOf { it.minLat } else 90.0
  val maxLat = if (spatialChildren.isNotEmpty()) spatialChildren.maxOf { it.maxLat } else -90.0
  val minLng = if (spatialChildren.isNotEmpty()) spatialChildren.minOf { it.minLng } else 180.0
  val maxLng = if (spatialChildren.isNotEmpty()) spatialChildren.maxOf { it.maxLng } else -180.0
  ```
- Because a single bounding box cannot represent disjoint regions without enclosing everything in between, the database columns `minLat`/`maxLat`/`minLng`/`maxLng` correctly describe the global envelope, but are ill-suited for directly centering the viewport when multiple distant regions exist.

### 2.2 ViewModel & Processing Pipeline (`PeriodsViewModel.kt`)
- When the user taps a period card, `showPeriodMap(summary: PeriodSummary)` launches:
  ```kotlin
  val workouts = periodsRepo.getWorkoutsForRange(summary.startTimestampS, summary.endTimestampS)
  ```
  Every workout in the period is retrieved, complete with its coordinates (`minLat`, `maxLat`, `minLng`, `maxLng`, `startLatLng`, `mapPolyline`).
- In background processing (`Dispatchers.Default`), `PeriodsViewModel` decodes the polyline and builds `PeriodMapState`.

### 2.3 Map Controller Layer (`InteractivePeriodMap.kt` & `MapBehaviors.kt`)
- `InteractivePeriodMap.kt` currently initializes bounds as:
  ```kotlin
  val periodBounds = remember(summary.periodType, summary.startTimestampS) {
      if (summary.minLat < 90.0 && summary.minLat != 0.0) {
          LatLngBounds(LatLng(summary.minLat, summary.minLng), LatLng(summary.maxLat, summary.maxLng))
      } else null
  }
  ```
  Passing this directly to `ATrainingTrackerMap` with `zoomFocus = MapZoomFocus.EXPLICIT_BOUNDS`.
- `MapBoundsController` executes:
  ```kotlin
  cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(initialBounds, padding))
  ```
  Which centers on Greenland!

---

## 3. Algorithmic Options for Region & Cluster Detection

We evaluate three design approaches to resolve this problem:

| Criterion | Option A: Hardcoded Distance Threshold Disjoint Bounding | Option B: Geographic Spatial Clustering (DBSCAN / Spatial Grid) | Option C: Density-Based Primary Region Detection with Region Switcher (Recommended) |
| :--- | :--- | :--- | :--- |
| **Concept** | If bounding box width > 3000 km, discard points furthest from the centroid. | Run DBSCAN on workout centroids with $\epsilon = 500\text{ km}$; find all clusters of activity. | Spatial clustering ($\epsilon = 300\text{--}500\text{ km}$) identifies distinct activity regions. Default viewport focuses on the dominant/primary region (or latest activity region). In interactive view, provide seamless UI chips/selector to jump between detected training regions (e.g. "Europe (42)", "USA (18)"). |
| **Data Loss / Clipping** | Poor: Arbitrarily clips workouts without user control. | Medium: Clusters found, but does not specify how the camera behaves or how the user accesses other regions. | Excellent: Zero data loss. Camera immediately shows meaningful, detailed workouts; user can toggle between training regions with 1 tap. |
| **Visual Quality** | Poor: Might still show partial ocean or miss entire trips. | Good: Identifies clusters. | World-Class: Solves the "Greenland problem" entirely. Focuses tight zoom on where the user actually ran/biked, while letting them explore other continents cleanly. |
| **Offline Performance** | Fast ($O(N)$). | Fast ($O(N \log N)$ or $O(N^2)$ for small $N \le 1000$). | Fast ($O(N^2)$ on $N$ workouts in period, typically $N < 500$, runs in $< 5\text{ ms}$). Zero network required. |
| **Card Thumbnail Parity**| Displays clipped box. | Displays dominant cluster. | Displays dominant training cluster (where most workouts occurred) so period card thumbnails show real roads/trails instead of ocean. |

---

## 4. Proposed Algorithm: Spatial Region Clustering (`SpatialRegionEngine`)

### 4.1 Geodesic Region Partitioning Algorithm
1. **Input**: A list of workouts $W = \{w_1, w_2, \dots, w_n\}$ with spatial coordinates (start coordinates or bounding centers).
2. **Clustering Radius ($\epsilon$)**:
   - Training within a metropolitan area or regional holiday (e.g. Schwarzwald, Alps, Bavaria) spans up to ~150–250 km.
   - Long-distance displacement (transatlantic, transcontinental, Europe $\leftrightarrow$ USA, Europe $\leftrightarrow$ Asia) spans $> 1000\text{ km}$.
   - We define a cluster merging threshold $\mathbf{D_{\text{cluster}} = 500\text{ km}}$ (geodesic distance).
3. **Cluster Formulation**:
   - For each workout $w_i$, calculate distance to existing region centroids using Haversine / `Location.distanceBetween`.
   - If distance $\le D_{\text{cluster}}$, assign to region and update region bounds ($minLat, maxLat, minLng, maxLng$).
   - If distance $> D_{\text{cluster}}$ for all existing regions, create a new region.
   - Merge overlapping or proximate regions if bounding boxes expand within $D_{\text{cluster}}$.
4. **Region Ranking & Dominant Region**:
   - Rank regions by **workout count** (primary metric) and **total distance / recency** (tie-breaker).
   - Region 1 (Primary): The region with the most workouts (e.g., home region in Germany with 120 workouts).
   - Region 2 (Secondary): Travel / vacation region (e.g., California trip with 15 workouts).
5. **Camera Viewport Selection**:
   - If number of regions $k = 1$ (normal case, 95% of periods): Single bounding box enclosing all workouts is used (identical to current behavior).
   - If number of regions $k > 1$ (multi-continent or distant travel):
     - **Initial Camera Viewport**: Centers on **Region 1 (Dominant Region)**, tightly fitted to its workouts with standard padding.
     - **Result**: No Greenland, no empty oceans! The map opens immediately with detailed, high-resolution workout tracks.

### 4.2 Interactive Region Switcher UI
In `PeriodMapScreen`:
- When $k > 1$, render a sleek horizontal chip row / pill bar at the top (under the header, or floating above the map):
  - `[ Stuttgart (120) ]`  `[ California (15) ]`  `[ All Regions ]`
- Tapping a chip animates the camera (`cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(regionBounds, padding))`).
- Selecting "All Regions" allows the user to explicitly zoom out to the world view if they intentionally want to see the global overview.

### 4.3 Summary Card Thumbnail Parity
In `PeriodSummaryCard.kt`:
- For period cards (Week, Month, Year), if $k > 1$, `PeriodMultiWorkoutMap` fits the bounds of the **Dominant Region**.
- The card displays meaningful tracks instead of an empty ocean tile.

---

## 5. Architectural & System Invariants
1. **Zero Database Migrations Required**:
   - Clustering can be executed on-the-fly in `PeriodsViewModel` using the existing workout dataset fetched for the period (`getWorkoutsForRange`), or cached in memory.
   - No schema changes to `PeriodSummaries.db` are strictly necessary, preventing database migration overhead and preserving compatibility with existing backups.
2. **Offline & Zero Network Invariant**:
   - Region naming can use local reverse-geocoding (if available) or clean coordinate/cardinal identifiers (e.g., "Region 1 (48.8°N, 9.2°E)", "Region 2 (37.7°N, -122.4°W)", or "Primary Region (%d)").
3. **Pure Kotlin / Android Standard Library**:
   - Geodesic distance uses standard spherical trigonometric calculations (Haversine) already validated in `WorkoutClusterEngine.distanceBetween`.
4. **Performance Safety**:
   - Calculating regions for $N \le 500$ workouts takes $< 5\text{ ms}$ on mobile CPUs and executes on `Dispatchers.Default`.

---

## 6. Verification & Test Strategy (ASPICE SWE.1)
1. **Unit Tests (`PeriodSpatialClusterTest.kt`)**:
   - Test case: Single region (10 workouts in Munich) $\rightarrow$ Returns 1 region matching global bounds.
   - Test case: Transatlantic split (50 workouts in Germany, 10 workouts in California) $\rightarrow$ Returns 2 distinct regions; Germany is identified as dominant region; California is secondary.
   - Test case: Viewport bounds for Germany region do NOT include Greenland or USA coordinates.
   - Test case: Transcontinental split (Europe + East Asia) $\rightarrow$ Correctly partitions into 2 regions.
   - Test case: Edge case with empty workouts or single workout.
2. **UI Component Tests**:
   - Verify `PeriodMapScreen` displays region selector chips when $k > 1$.
   - Verify tapping a chip triggers camera repositioning to that region's bounds.
   - Verify `PeriodSummaryCard` thumbnail displays dominant region bounds.
