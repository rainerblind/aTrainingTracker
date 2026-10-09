# Stage 1 Problem Domain & Root Cause Analysis: ATT-2967 - Display current athlete position marker on Live Segment elevation profile graph

**Ticket**: [ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)  
**Sub-task**: [ATT-3034](https://atrainingtracker.atlassian.net/browse/ATT-3034) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2967`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Domain & Background

During active workout recording with an active Strava or local segment match, `SensorGridScreen` presents the real-time Live Segment bottom sheet (`LIveSegmentSheet.kt`). While this bottom sheet displays numerical telemetry (elapsed time, distance completed, remaining distance, PR delta) in `SegmentLiveDetails` and renders the segment's colored elevation profile at the bottom via `MapDetailLayout`, the elevation profile chart lacks any visual indicator or pin showing the athlete's current position along the segment profile.

Athletes riding or running a live segment cannot see where they currently are relative to upcoming terrain features (e.g. steep ramps, crests, falsches Flachstück, or upcoming descents).

---

## 2. Requirement Archaeology & Chesterton's Fence

1. **Original Historical Context**:
   - `LIveSegmentSheet.kt` was integrated with `MapDetailLayout` in Sprint 2026-40.15 (`REQ-UI-196`, `ATT-1644`) to provide a consistent header and elevation view.
   - `MapDetailLayout` supports an `externalScrubDistance: Double?` parameter (added in Sprint 2026-40.16 / `REQ-UI-302` for `RouteOnMapScreen` segment synchronization).
   - In `MapDetailLayout.kt`:
     ```kotlin
     var selectedDistance by remember { mutableStateOf<Double?>(null) }

     LaunchedEffect(externalScrubDistance) {
         if (externalScrubDistance != null) {
             selectedDistance = externalScrubDistance
         }
     }
     ```
   - In `LIveSegmentSheet.kt`, `MapDetailLayout` is currently invoked without providing `externalScrubDistance`, leaving `selectedDistance` unassigned during live tracking.

2. **Why It Was Formulated That Way**:
   - `LIveSegmentSheet` was built before `externalScrubDistance` existed on `MapDetailLayout`. When `externalScrubDistance` was subsequently introduced for route segment breakdown scrubbing (`RouteOnMapScreen`), `LIveSegmentSheet` was not retrofitted to forward the athlete's active segment progress.

3. **Invariants to Preserve**:
   - Clamped wrap-content height and suppressed map / zoom controls in bottom sheet mode (`REQ-UI-270`, `TST-UI-229`).
   - Unified surface background and header styling (`REQ-UI-196`).
   - Clean profile display without misleading pin when approaching (`APPROACHING` / `FAR_FAR_AWAY`).
   - Elevation profile canvas rendering and dynamic gradient color mapping.
   - 100% clean-room test pass rate across the full test suite.

---

## 3. Root Cause Analysis

In `LIveSegmentSheet.kt`:
```kotlin
MapDetailLayout(
    modifier = modifier.fillMaxWidth().wrapContentHeight(),
    bSportType = liveSegment.staticData.summary.bSportType,
    zoomFocus = MapZoomFocus.FIT_PRIMARY,
    activeScrubPath = liveSegment.staticData.path,
    useStatusBarsPadding = false,
    showMap = false,
    showZoomControls = false,
    // externalScrubDistance is omitted!
    header = { ... }
)
```
Because `externalScrubDistance` is not passed:
- `MapDetailLayout` defaults `externalScrubDistance = null`.
- `selectedDistance` remains `null`.
- Neither `ElevationProfileChart` nor `ElevationView` renders the location pin / scrubber dot on the profile canvas.
- In addition, in `MapDetailLayout.kt`, `LaunchedEffect(externalScrubDistance)` currently guards with `if (externalScrubDistance != null)`. If a caller passes `null` to clear a previous scrub distance, `selectedDistance` would stay stale. Updating this to `selectedDistance = externalScrubDistance` ensures clean clearance when transitioning from active segment back to approaching or null.

---

## 4. Proposed Solution & Architecture

1. **Calculate `externalScrubDistance` in `LIveSegmentSheet.kt`**:
   ```kotlin
   val externalScrubDistance = when (liveSegment.liveData.segmentStatus) {
       LiveSegmentStatus.ON_SEGMENT,
       LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> liveSegment.liveData.distanceOnSegment_raw
       LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
       LiveSegmentStatus.APPROACHING,
       LiveSegmentStatus.FAR_FAR_AWAY -> null
   }
   ```
2. **Forward to `MapDetailLayout`**:
   Pass `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`.
3. **Synchronize Clearance in `MapDetailLayout.kt`**:
   Ensure `LaunchedEffect(externalScrubDistance)` updates `selectedDistance = externalScrubDistance` so that when `externalScrubDistance` becomes `null`, `selectedDistance` is cleared cleanly.
4. **Verification & Contract Tests**:
   - Update `LiveSegmentSheetContractTest.kt` / `LiveSegmentSheetLayoutTest.kt` to verify that `LiveSegmentSheet` evaluates and forwards `externalScrubDistance` across all `LiveSegmentStatus` states.

---

## 5. Scope & Boundary

- **In Scope**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt`
- **Out of Scope**:
  - Pacing calculation logic or Strava segment matching math (`LiveSegmentsRepository.kt`).
  - Modifications to `ClimbProfileCanvas` or live climb sheets (`LiveClimbSheet.kt`).
