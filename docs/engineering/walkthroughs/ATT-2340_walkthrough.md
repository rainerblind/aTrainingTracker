# Stage 5: Verification & Walkthrough - ATT-2340

**Ticket**: [ATT-2340](https://atrainingtracker.atlassian.net/browse/ATT-2340)  
**Sub-task**: [ATT-2534](https://atrainingtracker.atlassian.net/browse/ATT-2534) (`[Test]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2340`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary

When scrubbing Speed, Pace, Heart Rate, or Power telemetry graphs in `MapDetailLayout`, the directional map position scrubber pin was previously omitted because `ScrubMarkerLayer` and `ScrubberController` were tightly bound to forward geodesic distance thresholds on `activePath` (`it.distance >= targetDist`), while `activeScrubPoint` calculated in `MapDetailLayout` was never forwarded to `ATrainingTrackerMap`.
This defect has been resolved by:
1. Extending `ScrubMarkerLayer` and `ScrubberController` with an `activeScrubPoint: PathPoint?` parameter and dual-domain fallback logic.
2. Extending `ATrainingTrackerMap` to receive and forward `activeScrubPoint`.
3. Updating `MapDetailLayout` to compute `activeScrubPoint` with time/distance domain awareness and pass it directly to `ATrainingTrackerMap`.
4. Adding an `onPointSelected: (PathPoint?) -> Unit` callback to `TelemetryMetricGraph` for immediate point reporting during drag and tap gestures.
5. Guarding against trackless (indoor) `(0.0, 0.0)` coordinates to prevent spurious map rendering.

All 1922 unit and regression tests passed cleanly with 0 failures in 8m 53s.

---

## 2. Requirement & Test Verification Status

| Requirement / Test Case | Description | Previous Status | New Status |
| :--- | :--- | :--- | :--- |
| **REQ-MAP-032** | Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering | `Specified` | `Verified` |
| **TST-MAP-034** | Domain-Agnostic Scrubber Marker Synchronization & Telemetry Graph Auto-Centering Verification | `Specified` | `Verified` |

---

## 3. Targeted Test Results

1. `ScrubMarkerLayerResolutionTest`:
   - `directActiveScrubPointTakesPrecedenceOverSelectedDistance`: PASSED.
   - `fallbackToSelectedDistanceWhenActiveScrubPointIsNull`: PASSED.
   - `timeDomainScrubPointWithZeroDistanceResolvesViaPointDirectly`: PASSED.
   - `tracklessCoordinatesAtZeroZeroAreRejected`: PASSED.
   - `validGpsCoordinatesAreAccepted`: PASSED.
   - `isWestboundCalculationCorrectlyDetectsDirection`: PASSED.
   - `nullScrubPointAndNullDistanceResolvesToNull`: PASSED.

2. `MapDetailLayoutScrubSynchronizationTest`:
   - `scrubMarkerLayerAcceptsActiveScrubPointParameter`: PASSED.
   - `scrubberControllerAcceptsActiveScrubPointParameter`: PASSED.
   - `aTrainingTrackerMapForwardsActiveScrubPoint`: PASSED.
   - `telemetryMetricGraphSupportsOnPointSelectedCallback`: PASSED.
   - `mapDetailLayoutWiresOnPointSelectedAndPassesActiveScrubPoint`: PASSED.

3. Full regression execution:
   - Command: `./gradlew testDebugUnitTest`
   - Total tests: 1922
   - Failures: 0
   - Execution time: 8m 53s
   - Status: BUILD SUCCESSFUL

---

## 4. Key Artifacts & Code Changes

- [MapLayers.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt): Added `activeScrubPoint: PathPoint? = null` to `ScrubMarkerLayer`, dual-domain resolution fallback, and `(0.0, 0.0)` trackless coordinate filtering.
- [MapBehaviors.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt): Added `activeScrubPoint: PathPoint? = null` to `ScrubberController` with coordinate safety check.
- [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt): Added `activeScrubPoint: PathPoint? = null` parameter and forwarded to `ScrubberController` and `ScrubMarkerLayer`.
- [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt): Added `onPointSelected: (PathPoint?) -> Unit = {}` callback and gesture integration.
- [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt): Pass `activeScrubPoint = activeScrubPoint` into `ATrainingTrackerMap`.
- [ScrubMarkerLayerResolutionTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ScrubMarkerLayerResolutionTest.kt): Unit test suite for resolution and direction logic.
- [MapDetailLayoutScrubSynchronizationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubSynchronizationTest.kt): Contract test suite for component integration.
