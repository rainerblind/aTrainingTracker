# Stage 4 Implementation Summary: ATT-2967 - Display current athlete position marker on Live Segment elevation profile graph

**Ticket**: [ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)  
**Sub-task**: [ATT-3037](https://atrainingtracker.atlassian.net/browse/ATT-3037) (`[Implementation]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2967`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Summary of Changes

### 1.1 `LIveSegmentSheet.kt`
- Evaluated `externalScrubDistance` based on `liveSegment.liveData.segmentStatus`:
  - `LiveSegmentStatus.ON_SEGMENT` and `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: maps to `liveSegment.liveData.distanceOnSegment_raw`.
  - `LiveSegmentStatus.FINISHED`: maps to `liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)`.
  - `LiveSegmentStatus.APPROACHING` and `LiveSegmentStatus.FAR_FAR_AWAY`: maps to `null`.
- Supplied `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`.

### 1.2 `MapDetailLayout.kt`
- Streamlined `LaunchedEffect(externalScrubDistance)` to synchronize `selectedDistance = externalScrubDistance` unconditionally without null gating, ensuring that transitioning to `null` properly clears any active scrubber/position pin from the elevation profile canvas.

### 1.3 Contract & Unit Tests
- Updated `LiveSegmentSheetContractTest.kt` with tests verifying `externalScrubDistance` evaluation logic and `MapDetailLayout` synchronization.
- Verified all segment unit tests pass cleanly.

---

## 2. Invariants & Preservations

1. Clamped wrap-content height without viewport expansion (`REQ-UI-270`, `TST-UI-229`).
2. Suppressed map (`showMap = false`) and zoom controls (`showZoomControls = false`) (`REQ-UI-197`).
3. Unified surface background (`MaterialTheme.colorScheme.surface`) (`REQ-UI-196`).
4. Approaching segment clean elevation profile without premature position marker pin.
5. 100% full clean-room unit test pass rate across the full application test suite.
