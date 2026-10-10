# Stage 3 Implementation Plan: ATT-2967 - Display current athlete position marker on Live Segment elevation profile graph

**Ticket**: [ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)  
**Sub-task**: [ATT-3036](https://atrainingtracker.atlassian.net/browse/ATT-3036) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2967`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Strategy & SWE.2 Detailed Design

### 1.1 Context & Goal
Provide real-time athlete position marker visualization on the Live Segment bottom sheet elevation profile graph (`LIveSegmentSheet.kt`), allowing athletes to anticipate upcoming ramps, crests, and gradient changes while actively racing/riding a Strava or local segment.

### 1.2 Step-by-Step Implementation Sequence

#### Step 1: Update `LIveSegmentSheet.kt` to Evaluate & Forward `externalScrubDistance`
- In `LiveSegmentSheet.kt`:
  ```kotlin
  val externalScrubDistance = when (liveSegment.liveData.segmentStatus) {
      LiveSegmentStatus.ON_SEGMENT,
      LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> liveSegment.liveData.distanceOnSegment_raw
      LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
      LiveSegmentStatus.APPROACHING,
      LiveSegmentStatus.FAR_FAR_AWAY -> null
  }
  ```
- Pass `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`.

#### Step 2: Harmonize Scrub Distance Synchronization in `MapDetailLayout.kt`
- In `MapDetailLayout.kt`, change lines 125-129:
  ```kotlin
  LaunchedEffect(externalScrubDistance) {
      selectedDistance = externalScrubDistance
  }
  ```
  This ensures that when `externalScrubDistance` transitions to `null` (e.g., returning to `APPROACHING` or reset), `selectedDistance` is cleanly reset to `null`.

#### Step 3: Enhance Contract & Unit Tests
- In `LiveSegmentSheetContractTest.kt`:
  - Add tests asserting that `LiveSegmentSheet` evaluates `externalScrubDistance` based on `liveSegment.liveData.segmentStatus` and supplies it to `MapDetailLayout`.
  - Add tests asserting that `MapDetailLayout` synchronizes `selectedDistance = externalScrubDistance`.
- In `LiveSegmentSheetLayoutTest.kt`:
  - Add layout and integration contract tests verifying `externalScrubDistance` evaluation logic for `ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, `FINISHED`, and `APPROACHING`.

#### Step 4: Verification & Clean-Room Regression
- Execute targeted unit test suites.
- Execute full `./gradlew testDebugUnitTest` suite (2,284 tests).

---

## 2. Invariants & Preservations

1. Clamped wrap-content height without viewport expansion (`REQ-UI-270`, `TST-UI-229`).
2. Suppressed map (`showMap = false`) and zoom controls (`showZoomControls = false`) (`REQ-UI-197`).
3. Unified surface background (`MaterialTheme.colorScheme.surface`) (`REQ-UI-196`).
4. Approaching segment clean elevation profile without premature position marker pin.
5. 100% full clean-room unit test pass rate across the full application test suite.
