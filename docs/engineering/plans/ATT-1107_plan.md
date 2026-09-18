# Implementation Plan: Period Map Scalability & Adaptive Layering (ATT-1107)

* **Parent Issue**: [ATT-1107](https://rainerblind.atlassian.net/browse/ATT-1107) (*[Bug] ANR / Crash when showing a period (year) with many workouts*)
* **Sub-Task**: [ATT-1148](https://rainerblind.atlassian.net/browse/ATT-1148) (*Stage 3: Implementation Plan*)
* **Target Version**: `V4.9.37`
* **Requirement**: `REQ-PER-012` (*Period Map Scalability, Memory Budget & Adaptive Layering Invariant*)
* **Test Specification**: `TST-PER-018`
* **Branch**: `bugfix/ATT-1107`

---

## 1. Problem Statement & Technical Root Cause

When opening the spatial map view for long-term periods containing hundreds of workouts (such as yearly views or periods with many running activities), the app experiences catastrophic heap exhaustion (`OutOfMemoryError`), resulting in UI freezes (ANRs) and process termination.

### Empirical Evidence from Device (`66020DLCR002FL`)
1. **Crash at 22:40:25.636**: `java.lang.OutOfMemoryError: Failed to allocate a 4112 byte allocation with 206288 free bytes and 201KB until OOM, target footprint 268435456` during `HeatmapTileProvider.convolve()`.
2. **Crash at 23:23:41.567**: `java.lang.OutOfMemoryError: Failed to allocate a 39656 byte allocation with 608080 free bytes and 593KB until OOM, target footprint 268435456` during `GoogleMap.addPolyline()` via `PolylineKt.PolylineImpl`.

### Mechanism of Failure
- **Vector Polyline Explosion**: Every member workout in `PeriodsViewModel` is converted to a `MapTrack` and passed to `InteractivePeriodMap`, which iterates through all member tracks and calls `GoogleMap.addPolyline()` via Compose. For 300 workouts, this allocates hundreds of native C++ polylines and JNI arrays on the UI thread.
- **Marker Explosion**: 4 technical markers per workout (Start, End, Apex, Altitude) result in ~1,200 markers with dynamic canvas-generated bitmap descriptors.
- **Redundant Memory Duplication**: GPS points are decoded into `PathPoint` objects (with full altitude/distance streams) and additionally into `LatLng` lists for `heatmapPathMap`.
- **Default Heap Limit**: Standard Android app heap limit of 256MB is breached.

---

## 2. Target Architecture & Design Solution

### 2.1 Adaptive Layering Threshold (`MAX_PERIOD_VECTOR_TRACKS = 30`)
- **Large Periods (> 30 Workouts)**:
  - Vector polylines (`memberTracks`) and member markers (`memberMarkers`) are suppressed from the map.
  - The aggregate spatial picture is rendered via the high-performance raster `Heatmap` tile overlay + `anchorTracks` (the 4–5 spatial extrema and longest workout) + period `extremaMarkers`.
  - When the athlete taps or peeks an individual workout, that single session is rendered as an interactive vector line via `peekedTracks`.
- **Small Periods (<= 30 Workouts, e.g. Day, Week, or filtered Sport)**:
  - All member vector lines and technical markers are displayed with full fidelity.
- **Dynamic Sport Filter Re-activation**:
  - If a user selects a sport filter in `PeriodMapScreen` and the active workout count drops to $\le 30$, detailed vector tracks and markers for that sport are activated on-the-fly.

### 2.2 ViewModel Memory & Object Budgeting
- In `PeriodsViewModel.showPeriodMap()`:
  - When `workouts.size > MAX_PERIOD_VECTOR_TRACKS`, bypass instantiating hundreds of `MapTrack` and `PeriodPeakMarker` objects.
  - Decode polylines once for `heatmapPathMap`. For $\le 30$ workouts, derive `MapTrack` paths directly from the decoded `heatmapPathMap` points without redundant parsing of altitude/distance streams.

### 2.3 Application Process Heap Configuration
- Declare `android:largeHeap="true"` in `AndroidManifest.xml` under `<application>` to provide the necessary headroom (512MB on modern devices) for GIS map tile caching and background convolutions.

---

## 3. Concrete Implementation Steps

1. **`AndroidManifest.xml`**:
   - Add `android:largeHeap="true"` to `<application>`.
2. **`PeriodData.kt`**:
   - Define `const val MAX_PERIOD_VECTOR_TRACKS = 30`.
3. **`PeriodsViewModel.kt`**:
   - Optimize `showPeriodMap()`:
     - Decode `heatmapPathMap` once.
     - If `workouts.size <= MAX_PERIOD_VECTOR_TRACKS`, construct lightweight `tracks` and `markers`.
     - If `workouts.size > MAX_PERIOD_VECTOR_TRACKS`, set `tracks = emptyList()` and `markers = emptyList()`.
4. **`PeriodMapScreen.kt`**:
   - Update `filteredContent` calculation:
     - Check `val isAdaptive = workouts.size > MAX_PERIOD_VECTOR_TRACKS`.
     - When `isAdaptive == true`: `memberTracks = emptyList()`, `memberMarkers = emptyList()`.
     - When `isAdaptive == false`: generate `memberTracks` from `mapState.workoutIdToHeatmapPathMap` (or `mapState.tracks`) and filter `mapState.memberMarkers`.
5. **Unit Tests (`PeriodMapAdaptiveLayeringTest.kt`)**:
   - Verify threshold behavior:
     - `workouts.size > 30` -> empty memberTracks and memberMarkers, full heatmap.
     - `workouts.size <= 30` -> populated memberTracks and memberMarkers.
     - Sport filter switching from > 30 to <= 30 -> dynamically enables memberTracks for filtered sport.
     - Manifest contains `largeHeap="true"`.

---

## 4. Verification & Validation Plan
- Execute `./gradlew testDebugUnitTest --tests "*Period*"`
- Execute full unit test suite `./gradlew testDebugUnitTest`
- Inspect working tree and commits on `bugfix/ATT-1107`.
