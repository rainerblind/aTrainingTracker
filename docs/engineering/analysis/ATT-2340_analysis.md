# Stage 1: Problem Domain & Root Cause Analysis - ATT-2340

**Ticket**: [ATT-2340](https://atrainingtracker.atlassian.net/browse/ATT-2340)  
**Sub-task**: [ATT-2530](https://atrainingtracker.atlassian.net/browse/ATT-2530) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2340`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Symptoms

In the workout aftermath detail view (`TrackOnMapScreen` / `MapDetailLayout`), athletes can interactively scrub across the elevation profile and telemetry charts (Speed/Pace, Heart Rate, Power).
While scrubbing updates the telemetry badge (`ScrubbingTelemetryBadge`) and chart cursors:
1. When scrubbing charts operating in the time domain (which is the default for telemetry charts), the directional scrubber marker does not appear or track along the GPS route on the map.
2. In `ScrubMarkerLayer` (`MapLayers.kt`) and `ScrubberController` (`MapBehaviors.kt`), marker lookup is performed via:
   ```kotlin
   val index = activePath.indexOfFirst { it.distance >= targetDist }
   ```
   If `targetDist` represents time in seconds (e.g. 3600s) on a 3km run (3000m) or if distance values are unpopulated or non-linear, `indexOfFirst` returns `-1`. The marker is discarded and no map tracking occurs.
3. Furthermore, `MapDetailLayout.kt` computes `activeScrubPoint` from `selectedDistance`, but does not pass `activeScrubPoint` down into `ATrainingTrackerMap`, `ScrubMarkerLayer`, or `ScrubberController`.

---

## 2. Root Cause Analysis (RCA)

1. **Primitive Parameter Decoupling**:
   - `ATrainingTrackerMap` accepts only `selectedDistance: Double?` and `activeScrubPath: List<PathPoint>?`.
   - `ScrubMarkerLayer` and `ScrubberController` are forced to re-derive the marker location solely from `selectedDistance` using `it.distance >= targetDist`.
2. **Domain Mismatch & Distance Ambiguity**:
   - Telemetry graphs default to `ProfileXAxisDomain.TIME`. When the user scrubs, the selected coordinate is in seconds.
   - Even when `TelemetryMetricGraph` resolves `nearest?.distance`, if distance is omitted, unpopulated, or zero-offset in certain stream formats, `nearest.distance` does not correlate to `it.distance >= targetDist`.
   - When `activeScrubPoint` is already known or can be passed directly, re-evaluating `it.distance >= targetDist` is brittle and lossy.
3. **Zero Coordinates in Trackless Workouts**:
   - For trackless workouts without GPS fixes (e.g. indoor spinning), `point.latLng` is `(0.0, 0.0)`. Neither `ScrubMarkerLayer` nor `ScrubberController` checked whether `latLng` contains a valid coordinate (`!= (0.0, 0.0)`), which could cause spurious marker rendering at (0°N, 0°E) in the Gulf of Guinea.

---

## 3. Chesterton's Fence & Archaeology

1. **Why `selectedDistance: Double?` was introduced**:
   - Introduced in ATT-1391 / ATT-1740 when `ElevationProfile` was the sole graph on the map screen, operating strictly in the distance domain (meters).
   - Subsequent enhancements (ATT-2006, ATT-2031) introduced time-domain telemetry charts and `TelemetryMetricUtils.findNearestPoint`, but `ScrubMarkerLayer` and `ScrubberController` remained legacy distance-based lookups.
2. **Preservation Invariant**:
   - Backward compatibility for existing callers of `ATrainingTrackerMap`, `ScrubMarkerLayer`, and `ScrubberController` that only provide `selectedDistance: Double?` MUST be preserved via optional default parameter `activeScrubPoint: PathPoint? = null`.
   - Existing elevation profile scrubbing in distance domain MUST continue to work seamlessly.

---

## 4. Proposed Solution & Architecture

1. **Extend `ScrubMarkerLayer` (`MapLayers.kt`)**:
   - Add parameter `activeScrubPoint: PathPoint? = null`.
   - Resolve marker point:
     ```kotlin
     val point = activeScrubPoint ?: selectedDistance?.let { targetDist ->
         TelemetryMetricUtils.findNearestPoint(activePath, targetDist, isTimeDomain = false)
             ?: activePath.find { it.distance >= targetDist }
     }
     ```
   - Only render the `Marker` if `point != null && (point.latLng.latitude != 0.0 || point.latLng.longitude != 0.0)`.
   - Calculate `isWestbound` using the resolved point's index in `activePath`.
2. **Extend `ScrubberController` (`MapBehaviors.kt`)**:
   - Add parameter `activeScrubPoint: PathPoint? = null`.
   - Auto-center camera around `point.latLng` when point is valid (`!= (0.0, 0.0)`).
3. **Extend `ATrainingTrackerMap.kt`**:
   - Add parameter `activeScrubPoint: PathPoint? = null` with default `null`.
   - Forward `activeScrubPoint` to `ScrubberController` and `ScrubMarkerLayer`.
4. **Wire `MapDetailLayout.kt`**:
   - Pass `activeScrubPoint = activeScrubPoint` into `ATrainingTrackerMap`.
   - Support `onPointSelected` callback on `TelemetryMetricGraph` and `ElevationProfile` so that touching any graph instantly updates `activeScrubPoint`.

---

## 5. Affected Files & Boundary Matrix

| File | Nature of Change |
| :--- | :--- |
| `app/src/main/java/.../ui/map/MapLayers.kt` | Add `activeScrubPoint: PathPoint? = null` to `ScrubMarkerLayer`, guard against (0,0) coordinates, resolve point robustly. |
| `app/src/main/java/.../ui/map/MapBehaviors.kt` | Add `activeScrubPoint: PathPoint? = null` to `ScrubberController`, auto-center on valid coordinate. |
| `app/src/main/java/.../ui/map/ATrainingTrackerMap.kt` | Add `activeScrubPoint: PathPoint? = null` parameter and forward to layers/behaviors. |
| `app/src/main/java/.../ui/map/MapDetailLayout.kt` | Forward `activeScrubPoint` to `ATrainingTrackerMap`. |
| `app/src/main/java/.../ui/map/TelemetryMetricGraph.kt` | Add `onPointSelected: (PathPoint?) -> Unit = {}` callback and emit resolved nearest point on drag/tap. |
| `app/src/test/.../ui/map/ScrubMarkerLayerTest.kt` | Unit & contract tests verifying map marker visibility and position across TIME and DISTANCE domains. |

---

## 6. Review Gates Checklist

- [x] Problem statement and forensic symptoms documented.
- [x] Root cause analysis verified against source code.
- [x] Chesterton's Fence archaeology completed.
- [x] Invariants and backward compatibility contracts established.
- [x] Architectural solution and file modifications mapped.
