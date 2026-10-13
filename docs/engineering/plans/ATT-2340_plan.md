# Stage 3: Implementation Plan - ATT-2340

**Ticket**: [ATT-2340](https://atrainingtracker.atlassian.net/browse/ATT-2340)  
**Sub-task**: [ATT-2532](https://atrainingtracker.atlassian.net/browse/ATT-2532) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2340`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Architectural Strategy & SWE.2 Design

The root problem stems from an impedance mismatch between time-domain telemetry graphs (which emit timestamps or distance proxies) and map layers expecting geodesic distance thresholds (`it.distance >= targetDist`).
Rather than relying on lossy secondary index searches, our SWE.2 design introduces **direct, domain-agnostic `PathPoint` propagation**:
1. When scrubbing any chart (ElevationProfile or TelemetryMetricGraph for Speed, HR, Power), the chart's gesture engine already resolves the exact nearest `PathPoint` via `TelemetryMetricUtils.findNearestPoint`.
2. By exposing an `onPointSelected: (PathPoint?) -> Unit` callback on `TelemetryMetricGraph` (matching the existing interface on `ElevationProfile`), `MapDetailLayout` captures the exact active point without loss of coordinate precision.
3. `ATrainingTrackerMap`, `ScrubMarkerLayer`, and `ScrubberController` are extended with `activeScrubPoint: PathPoint? = null`. When present, they directly read `point.latLng`, eliminating distance threshold lookups entirely while falling back cleanly for legacy distance-only callers.
4. Spurious rendering for trackless (indoor) workouts is eliminated by verifying `point.latLng.latitude != 0.0 || point.latLng.longitude != 0.0`.

---

## 2. Atomic Implementation Steps

### Step 1: Extend `ScrubMarkerLayer` in `MapLayers.kt`
- Add parameter `activeScrubPoint: PathPoint? = null`.
- Resolve `point`:
  ```kotlin
  val point = activeScrubPoint ?: selectedDistance?.let { targetDist ->
      TelemetryMetricUtils.findNearestPoint(activePath, targetDist, isTimeDomain = false)
          ?: activePath.find { it.distance >= targetDist }
  }
  ```
- Guard against null or `(0.0, 0.0)` coordinate:
  ```kotlin
  if (point != null && (point.latLng.latitude != 0.0 || point.latLng.longitude != 0.0)) {
      val index = activePath.indexOf(point).let { idx ->
          if (idx != -1) idx else activePath.indexOfFirst { it.distance >= point.distance }
      }
      val isWestbound = ...
      Marker(...)
  }
  ```

### Step 2: Extend `ScrubberController` in `MapBehaviors.kt`
- Add parameter `activeScrubPoint: PathPoint? = null`.
- Update `LaunchedEffect(selectedDistance, activeScrubPoint, activePath)`:
  ```kotlin
  val scrubPoint = activeScrubPoint ?: selectedDistance?.let { targetDist ->
      TelemetryMetricUtils.findNearestPoint(activePath, targetDist, isTimeDomain = false)
          ?: activePath.find { it.distance >= targetDist }
  }
  if (scrubPoint != null && (scrubPoint.latLng.latitude != 0.0 || scrubPoint.latLng.longitude != 0.0)) {
      // Safe bounds check and cameraPositionState.animate(CameraUpdateFactory.newLatLng(point.latLng), 300)
  }
  ```

### Step 3: Extend `ATrainingTrackerMap.kt`
- Add parameter `activeScrubPoint: PathPoint? = null` with default `null`.
- Pass `activeScrubPoint = activeScrubPoint` into `ScrubberController` and `ScrubMarkerLayer`.

### Step 4: Add `onPointSelected` to `TelemetryMetricGraph.kt`
- Add parameter `onPointSelected: (PathPoint?) -> Unit = {}`.
- In drag and tap handling where `nearest: PathPoint?` is resolved, invoke `currentOnPointSelectedState(nearest)`.
- When gesture completes or is canceled, invoke `currentOnPointSelectedState(null)`.

### Step 5: Wire `MapDetailLayout.kt`
- Introduce `var touchedScrubPoint by remember { mutableStateOf<PathPoint?>(null) }`.
- When `selectedDistance == null`, reset `touchedScrubPoint = null`.
- Compute `val effectiveScrubPoint = touchedScrubPoint ?: activeScrubPoint`.
- Pass `activeScrubPoint = effectiveScrubPoint` into `ATrainingTrackerMap`.
- Pass `onPointSelected = { touchedScrubPoint = it }` to `ElevationProfile` and each `TelemetryMetricGraph` (Speed, Heart Rate, Power).

### Step 6: Author Targeted Unit Tests
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ScrubMarkerLayerResolutionTest.kt`:
  - Test resolution with `activeScrubPoint` provided directly.
  - Test resolution with fallback to `selectedDistance`.
  - Test zero coordinate rejection `(0.0, 0.0)`.
  - Test westbound bearing computation.
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubSynchronizationTest.kt`:
  - Verify AST / contract integrity of `onPointSelected` propagation from `TelemetryMetricGraph` into `ATrainingTrackerMap`.

---

## 3. Invariants & Risk Mitigations

1. **Zero Degradation for Distance Scrubbing**:
   - `selectedDistance` remains supported and functional; when `activeScrubPoint` is omitted, the layer uses `findNearestPoint` fallback.
2. **Trackless Safety**:
   - Indoor workouts without GPS never render spurious pins in the ocean because `(0.0, 0.0)` coordinates are explicitly rejected.
3. **No Breaking Parameter Changes**:
   - All newly added parameters have default values (`null` or empty lambda).

---

## 4. Review Gates Checklist

- [x] SWE.2 architecture addresses root cause without breaking changes.
- [x] Atomic implementation steps defined in strict order.
- [x] Invariants and regression risks analyzed.
- [x] Targeted unit test suite specified.
