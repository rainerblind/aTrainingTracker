# Stage 2 Requirement & Test Specification: ATT-2967 - Display current athlete position marker on Live Segment elevation profile graph

**Ticket**: [ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)  
**Sub-task**: [ATT-3035](https://atrainingtracker.atlassian.net/browse/ATT-3035) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2967`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Formal Requirement Specification

### REQ-UI-331: Live Segment Elevation Profile Dynamic Position Marker & Scrub Distance Synchronization

* **Requirement ID**: `REQ-UI-331`
* **Parent Epic**: `ATT-2582` (Segments: Live Tracking, Exploration & Route Integration)
* **Functional Scope**:
  1. **Scrub Distance Propagation (`LIveSegmentSheet.kt`)**:
     - `LiveSegmentSheet` must evaluate an `externalScrubDistance: Double?` based on `liveSegment.liveData.segmentStatus`:
       - `LiveSegmentStatus.ON_SEGMENT` or `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: pass `liveSegment.liveData.distanceOnSegment_raw`.
       - `LiveSegmentStatus.FINISHED`: pass `liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)`.
       - `LiveSegmentStatus.APPROACHING` or `LiveSegmentStatus.FAR_FAR_AWAY`: pass `null`.
     - `LiveSegmentSheet` must pass this evaluated `externalScrubDistance` directly to `MapDetailLayout`.
  2. **Clearance & Marker Synchronization (`MapDetailLayout.kt`)**:
     - `MapDetailLayout`'s `LaunchedEffect(externalScrubDistance)` must synchronize `selectedDistance = externalScrubDistance` unconditionally so that transition to `null` properly clears any active scrubber/position pin from the elevation profile canvas.
  3. **Elevation Profile Marker Rendering**:
     - When `selectedDistance` is non-null, `MapDetailLayout` renders the elevation position marker at the indicated distance metric along the segment's path.
  4. **Invariants & Preservations**:
     - Preserve clamped height without full-screen expansion (`REQ-UI-270`, `TST-UI-229`).
     - Preserve header styling and 0.5dp divider (`REQ-UI-196`).
     - Suppress map and zoom controls (`REQ-UI-197`).
     - Zero regressions across existing suite.

---

## 2. Formal Test Specification

### TST-UI-291: Live Segment Elevation Profile Position Marker & Scrub Distance Verification

* **Test ID**: `TST-UI-291`
* **Traceable Requirement**: `REQ-UI-331`
* **Target Test Classes**:
  - `com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetContractTest`
  - `com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetLayoutTest`

### Test Verification Cases
1. **Scrub Distance Calculation Contract Tests (`LiveSegmentSheetContractTest.kt`)**:
   - Verify `LIveSegmentSheet.kt` evaluates `externalScrubDistance` using `when (liveSegment.liveData.segmentStatus)`:
     - Maps `ON_SEGMENT` and `ON_SEGMENT_CLOSE_TO_FINISH` to `distanceOnSegment_raw`.
     - Maps `FINISHED` to `summary.distance` or `distanceOnSegment_raw`.
     - Maps `APPROACHING` and `FAR_FAR_AWAY` to `null`.
   - Verify `LIveSegmentSheet.kt` passes `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`.
2. **Scrub Distance Synchronization in `MapDetailLayout.kt`**:
   - Verify `MapDetailLayout.kt` synchronizes `selectedDistance = externalScrubDistance` upon changes without gating on `!= null`.
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Given-When-Then Acceptance Criteria

* **Criterion 1 (Active Live Segment Position Marker)**:
  * *Given* an active live segment in status `ON_SEGMENT` with `distanceOnSegment_raw = 152.0`
  * *When* `LiveSegmentSheet` is composed
  * *Then* `externalScrubDistance` is evaluated as `152.0` and passed to `MapDetailLayout`, causing `selectedDistance` to pin the position marker at 152m.

* **Criterion 2 (Approaching State Elevation Cleanliness)**:
  * *Given* an approaching live segment in status `APPROACHING`
  * *When* `LiveSegmentSheet` is composed
  * *Then* `externalScrubDistance` is `null`, ensuring no location marker pin is rendered over the ahead-elevation profile.

* **Criterion 3 (Finished Segment Marker Snap)**:
  * *Given* a completed live segment in status `FINISHED` with total segment distance `1250`
  * *When* `LiveSegmentSheet` is composed
  * *Then* `externalScrubDistance` evaluates to 1250m, snapping the position marker cleanly to the finish line of the elevation curve.
