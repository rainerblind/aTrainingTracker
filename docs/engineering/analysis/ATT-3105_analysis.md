# Stage 1 Analysis: ATT-3105 - Normalize Climb popup elevation profile X-axis to climb distance instead of full route distance

**Ticket**: [ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)  
**Sub-task**: [ATT-3143](https://atrainingtracker.atlassian.net/browse/ATT-3143) (`[Analysis]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3105`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.7 Joint Review on Google Pixel 10 (as documented in evidence attachment `climb_elevation_profile_progress_bug.png`), when inspecting an individual climb in the Climb detail popup (`ClimbDetailSheet.kt` hosting `ClimbOnMapScreen.kt`), the elevation profile chart renders the climb using global parent route coordinates (e.g., 0 to 28.10 km) rather than local climb distance (e.g., 0 to 722 m).

Specifically:
- For a 722 m climb located at kilometer 27.38 of a 28.10 km route, the X-axis labels span 0, 5, 10, 15, 20, 28.10 km.
- The actual climb elevation profile is compressed into a narrow sliver at the far right edge of the chart (between 97% and 100% of the horizontal canvas width).
- Approximately 97% of the chart area is completely blank.
- The interactive scrubber is difficult to operate within this tiny ~3% horizontal region.

The intended behavior is for the climb elevation profile to span 100% of the chart width from base (0.0 m) to summit (722 m), providing clear grade inspection and full-width scrubbing fidelity.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic tracing across `ClimbDetector.kt`, `Climb.kt`, `ClimbOnMapScreen.kt`, and `MapDetailLayout.kt` revealed the exact mechanism:

### 2.1 Route Distance Retention in `Climb.pathPoints`
In `ClimbDetector.kt` (lines 100–127):
```kotlin
val climbPoints = points.subList(startIdx, peakIdx + 1)
...
Climb(
    name = name,
    routeId = routeId,
    startLat = points[startIdx].latLng.latitude,
    startLng = points[startIdx].latLng.longitude,
    endLat = points[peakIdx].latLng.latitude,
    endLng = points[peakIdx].latLng.longitude,
    distanceMeters = dist,
    elevationGainMeters = gain,
    avgGradePercent = avgGrade,
    maxGradePercent = maxGrade,
    category = category,
    pathPoints = climbPoints
)
```
When climbs are extracted from a parent route (`points`), each `PathPoint` retains its global route cumulative distance (`p.distance`), e.g., 27,380 m to 28,102 m.

### 2.2 Direct Pass-Through to `MapDetailLayout`
In `ClimbOnMapScreen.kt` (lines 74–80):
```kotlin
MapDetailLayout(
    bSportType = bSportType,
    zoomFocus = if (climbBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
    initialBounds = climbBounds,
    activeScrubPath = climb.pathPoints,
    ...
)
```
`ClimbOnMapScreen` passes `climb.pathPoints` directly as `activeScrubPath`.

### 2.3 `ElevationProfile` Domain Computation
In `MapDetailLayout.kt` (lines 138, 298, 324) and `ElevationProfile.kt`:
- `ProfileDomainMath.calculateTotalSpan` computes the chart domain based on `activeScrubPath.last().distance` (e.g. 28,102 m).
- `ElevationProfile` scales the horizontal X-axis from `0.0` to `maxDistance = pathPoints.last().distance`.
- For points with distance from 27,380 m to 28,102 m:
  $$\text{Canvas } X = \frac{p.\text{distance} - \text{startDist}}{\text{visibleSpan}} \times \text{width} = \frac{27380}{28102} \times \text{width} \approx 97.4\%$$
- As a result, the entire climb profile is squeezed between 97.4% and 100.0% of the canvas width, leaving 0% to 97.4% empty.

---

## 3. Chesterton's Fence Archaeology & Invariant Preservation

Before modifying the distance domain, we investigated existing usages of `Climb.pathPoints` to avoid unintended regressions:

1. **`ClimbHeader.kt` (line 208)**:
   ```kotlin
   val startDistMeters = climb.pathPoints.firstOrNull()?.distance ?: 0.0
   Text(text = stringResource(R.string.routes_climb_start_at, formatters.distance.format_with_units(startDistMeters) ...))
   ```
   *Preservation Invariant*: `Climb.pathPoints` stored in `Climb` and in SQLite (`Climbs.db`) contains the parent route offset so the climb header can display "Start at 27.4 km". The raw `Climb.pathPoints` must not be mutated in place or stripped of route context in `Climb` entity definitions.

2. **`ElevationProfile.kt` on Route Screens (`RouteOnMapScreen.kt`)**:
   When viewing a whole route, `ElevationProfile` receives the full route path points and the route's climbs list. It renders climb summit badges using `summitPt.distance in currentStartDist..(currentStartDist + visibleSpan)`. These distances must remain in global route space when drawn over the full route.

3. **`LiveClimbsRepository.kt`**:
   `LiveClimbsRepository` computes progress during active tracking via `path[closestIdx].distance - path.first().distance`, which explicitly subtracts `first().distance`. It functions identically whether `first().distance` is 0 or non-zero.

4. **Historical Precedent in `ClimbDetailSheet.kt` (`ClimbDetailElevationProfile`)**:
   The legacy standalone canvas implementation in `ClimbDetailElevationProfile` (lines 134–149) explicitly implemented:
   ```kotlin
   val startDist = pathPoints.first().distance
   val totalDist = (pathPoints.last().distance - startDist).coerceAtLeast(1.0)
   ...
   val x = (((p.distance - startDist) / totalDist) * width).toFloat()
   ```
   This confirms that local climb elevation views were historically designed to be normalized to local climb distance ($0 \dots \text{totalDist}$). The unified `MapDetailLayout` migration in ATT-3053 inadvertently omitted this distance normalization step when passing `climb.pathPoints` as `activeScrubPath`.

---

## 4. User Scope Grounding (ATT-1250)

In accordance with ATT-1250:
- Scope is strictly bounded to normalizing the climb path distances supplied to `MapDetailLayout` within `ClimbOnMapScreen.kt`.
- No database schema migrations or changes to `ClimbDetector.kt` or `ClimbsDatabaseManager.kt` are needed or permitted.
- `ClimbHeader` continues to receive the original `climb` reference and accurately displays the route-level "Start at X.X km" indicator.
- Map polyline rendering in `MapContentScope.climbs(listOf(climb))` continues using latitude/longitude unaffected.

---

## 5. Architectural Solution (SWE.2)

### 5.1 Extension / Property on `Climb`: `normalizedPathPoints`
In `Climb.kt`:
```kotlin
/**
 * Sequence of path points normalized to local climb distance starting at 0.0 meters.
 * LatLng, altitude, time, and telemetry fields are preserved intact.
 */
val normalizedPathPoints: List<PathPoint>
    get() {
        if (pathPoints.isEmpty()) return emptyList()
        val startDist = pathPoints.first().distance
        if (startDist == 0.0) return pathPoints
        return pathPoints.map { it.copy(distance = (it.distance - startDist).coerceAtLeast(0.0)) }
    }
```

### 5.2 Pass Normalized Path in `ClimbOnMapScreen.kt`
In `ClimbOnMapScreen.kt`:
```kotlin
val normalizedClimbPath = remember(climb) {
    climb.normalizedPathPoints
}

MapDetailLayout(
    ...
    activeScrubPath = normalizedClimbPath,
    ...
)
```

### 5.3 Interactive Scrubbing Parity
- **Distance Domain**: $0.0 \le \text{selectedDistance} \le \text{climb.distanceMeters}$.
- **Point Lookup**: `TelemetryMetricUtils.findNearestPoint(normalizedClimbPath, selectedDistance)` finds the exact `PathPoint`.
- **Map Marker Synchronization**: `activeScrubPoint.latLng` corresponds exactly to the physical position on the climb. The Google Map marker tracks smoothly across the climb from base to summit.

---

## 6. Verification & Test Strategy (SWE.3 / SWE.4)

1. **Unit Test (`ClimbNormalizationTest` / `ClimbDetailSheetContractTest`)**:
   - Verify `Climb.normalizedPathPoints` maps `[PathPoint(27380.0, ...), PathPoint(28102.0, ...)]` to `[PathPoint(0.0, ...), PathPoint(722.0, ...)]`.
   - Verify empty `pathPoints` returns empty list.
   - Verify already normalized `pathPoints` (starting at 0.0) returns the original list without redundant allocations.
   - Verify `ClimbOnMapScreen` passes normalized path points to `MapDetailLayout`.
2. **Regression Check**:
   - Verify `ClimbHeader` retains the original `climb.pathPoints.first().distance` for "Start at 27.4 km".
   - Verify full test suite passes cleanly: `./gradlew testDebugUnitTest`.
