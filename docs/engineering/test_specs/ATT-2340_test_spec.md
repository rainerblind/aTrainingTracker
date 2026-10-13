# Stage 2: Requirement & Test Specification - ATT-2340

**Ticket**: [ATT-2340](https://atrainingtracker.atlassian.net/browse/ATT-2340)  
**Sub-task**: [ATT-2531](https://atrainingtracker.atlassian.net/browse/ATT-2531) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2340`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Formal Requirement Specification

### **REQ-MAP-032: Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering**

The system SHALL guarantee that touching or dragging across any workout chart (ElevationProfile or TelemetryMetricGraph for Speed, Pace, Heart Rate, Power) displays a synchronized directional scrubber marker at the exact GPS coordinate along the route and auto-centers the map view, irrespective of whether the chart operates in the TIME or DISTANCE domain (ATT-2340):

1. **Direct PathPoint Propagation (`ATrainingTrackerMap.kt`)**:
   - `ATrainingTrackerMap` SHALL accept optional parameter `activeScrubPoint: PathPoint? = null`.
   - When provided, `ATrainingTrackerMap` SHALL forward `activeScrubPoint` down to both `ScrubMarkerLayer` and `ScrubberController`.
   - The legacy `selectedDistance: Double? = null` parameter SHALL be preserved with 100% backward compatibility.

2. **Robust Scrubber Marker Placement (`ScrubMarkerLayer.kt`)**:
   - `ScrubMarkerLayer` SHALL accept optional parameter `activeScrubPoint: PathPoint? = null`.
   - The target marker point SHALL resolve via:
     ```kotlin
     val point = activeScrubPoint ?: selectedDistance?.let { targetDist ->
         TelemetryMetricUtils.findNearestPoint(activePath, targetDist, isTimeDomain = false)
             ?: activePath.find { it.distance >= targetDist }
     }
     ```
   - Marker rendering SHALL be guarded: if `point == null` or `point.latLng` is `(0.0, 0.0)` (e.g. trackless workouts without GPS fixes), no marker SHALL be rendered on the map.
   - The directional orientation (`isWestbound`) SHALL be evaluated using the resolved point's position within `activePath`.

3. **Domain-Aware Camera Centering (`ScrubberController.kt`)**:
   - `ScrubberController` SHALL accept optional parameter `activeScrubPoint: PathPoint? = null`.
   - When `point != null && (point.latLng.latitude != 0.0 || point.latLng.longitude != 0.0)` and the coordinate lies outside the 20% safe margin of the current viewport, the camera SHALL smoothly animate to center on `point.latLng`.

4. **Detailed Layout Integration (`MapDetailLayout.kt`)**:
   - `MapDetailLayout` SHALL pass its resolved `activeScrubPoint` directly to `ATrainingTrackerMap`.
   - `TelemetryMetricGraph` SHALL support `onPointSelected: (PathPoint?) -> Unit = {}`, emitting the resolved nearest `PathPoint` during horizontal scrubbing and tap gestures.
   - `MapDetailLayout` SHALL update `activeScrubPoint` reactively from both `ElevationProfile` and `TelemetryMetricGraph`.

5. **100% Backward Compatibility**:
   - Callers providing only `selectedDistance: Double?` (such as RouteOnMapScreen or SegmentOnMapScreen) SHALL continue to function with zero behavioral degradation.

---

## 2. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Telemetry Graph Scrubbing Synchronization)**:
  * *Given* a workout with GPS track and speed telemetry displayed in `MapDetailLayout`,
  * *When* the athlete touches or scrubs across the Speed/Pace graph operating in the TIME domain,
  * *Then* the directional scrubber marker appears on the map at the exact GPS coordinate corresponding to the scrubbed point.

* **Criterion 2 (Elevation Profile Distance Domain Scrubbing)**:
  * *Given* `ElevationProfile` operating in the DISTANCE domain,
  * *When* the athlete touches or scrubs across the elevation curve,
  * *Then* the map marker tracks along the route with identical precision and camera auto-centering.

* **Criterion 3 (Trackless Workout Safety)**:
  * *Given* a trackless workout (indoor turbo trainer session without GPS fixes where coordinates are `(0.0, 0.0)`),
  * *When* the athlete scrubs across HR or Power graphs,
  * *Then* the system updates the telemetry badge without rendering spurious markers at `(0.0, 0.0)` in the ocean.

---

## 3. Test Specification

### **TST-MAP-034: Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering Verification**

1. **ScrubMarkerLayer Unit & Contract Tests (`ScrubMarkerLayerTest.kt`)**:
   - Verify that when `activeScrubPoint` is provided with valid GPS coordinates, the marker is placed at `activeScrubPoint.latLng`.
   - Verify that when `activeScrubPoint` is null and `selectedDistance` is provided, the marker resolves via fallback.
   - Verify that when `point.latLng` is `(0.0, 0.0)`, no marker is rendered.
   - Verify that `isWestbound` orientation correctly reflects travel direction along the route.

2. **ScrubberController Unit Tests (`ScrubberControllerTest.kt`)**:
   - Verify that `ScrubberController` animates camera to `point.latLng` when `activeScrubPoint` is provided.
   - Verify that coordinates at `(0.0, 0.0)` do not trigger camera animations.

3. **MapDetailLayout Scrub Synchronization Contract Tests**:
   - Verify that `MapDetailLayout` propagates `activeScrubPoint` into `ATrainingTrackerMap`.
   - Verify that `TelemetryMetricGraph` fires `onPointSelected` with the resolved `PathPoint`.

4. **Clean-Room Full Suite Regression**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.
