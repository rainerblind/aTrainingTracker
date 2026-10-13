# Stage 4: Implementation Report - ATT-3324: Use numeric distance values instead of formatted strings when prioritizing active live segments

**Ticket**: [ATT-3324](https://rainerblind.atlassian.net/browse/ATT-3324)  
**Sub-task**: [ATT-3359](https://rainerblind.atlassian.net/browse/ATT-3359) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*) / [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-LIV-005` (refining `REQ-LIV-003`)  
**Test Mapping**: `TST-LIV-001`  
**Branch**: `feature/ATT-3324`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Summary of Changes

1. **Raw Numeric Distance Representation in `LiveSegmentData` (`LiveSegmentsRepository.kt`)**:
   * Added `val distanceToStart_raw: Double = Double.MAX_VALUE` and `val remainingDistance_raw: Double = Double.MAX_VALUE` to `LiveSegmentData`.
   * Preserved default `Double.MAX_VALUE` so uninitialized or inactive segments naturally sort to the end of any candidate list.
   * Preserved all existing formatted string properties (`distanceToStart`, `remainingDistance`, `distanceOnSegment`, `segmentOffset`) for Jetpack Compose UI rendering without any UI breakage.

2. **State-Aware Metric Population (`LiveSegmentsRepository.kt`)**:
   * `LiveSegmentStatus.APPROACHING`:
     * Populated `distanceToStart_raw = distanceToStart`.
     * Populated `remainingDistance_raw = liveSegment.staticData.summary.distance_raw + distanceToStart`.
   * `LiveSegmentStatus.ON_SEGMENT`, `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`:
     * Populated `distanceToStart_raw = 0.0`.
     * Populated `remainingDistance_raw = remainingDistance` (orthogonal blended distance).
     * Populated `segmentOffset_raw = distanceToSegment` (cross-track error).
   * `LiveSegmentStatus.FINISHED`:
     * Initial finish: `distanceToStart_raw = 0.0`, `remainingDistance_raw = 0.0`, `segmentOffset_raw = distanceToEnd`.
     * Continuous finished updates: updated `segmentOffset_raw = distanceToEnd`.

3. **Strict Numeric Sorting Comparator in `TrackingViewModel.kt`**:
   * Refactored `activeLiveSegments` comparator tie-breaker in `TrackingViewModel.kt:161-175`:
     * Replaced string comparison (`it.liveData.remainingDistance`, `it.liveData.distanceToStart`, `it.liveData.segmentOffset`) with unrounded `Double` metrics:
       * `ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH` -> `it.liveData.remainingDistance_raw`
       * `APPROACHING` -> `it.liveData.distanceToStart_raw`
       * `else` -> `it.liveData.segmentOffset_raw`
   * Completely eliminated ASCII lexicographical string sorting inversions where `"2.40 km" < "995 m"` previously inverted candidate ranking at the 1,000 m boundary.

4. **Targeted Unit Tests (`LiveSegmentSortingTest.kt`)**:
   * Created comprehensive test suite verifying:
     * `testOnSegment_sortingAcrossOneKmBoundary_prioritizesShorterSegmentNumerically`: Asserts 995 m ranks before 2,400 m.
     * `testApproaching_sortingAcrossOneKmBoundary_prioritizesCloserStartNumerically`: Asserts 950 m ranks before 1,200 m.
     * `testStatusWeight_preservesPriorityOverRawDistance`: Asserts `ON_SEGMENT_CLOSE_TO_FINISH` (weight 4) outranks `ON_SEGMENT` (weight 3).
     * `testImperialUnits_sortsNumericallyWithoutStringArtifacts`: Asserts distance ranking is unaffected by imperial formatting (`0.75 mi` vs `875 yd`).
     * `testFinishedStatus_sortsBySegmentOffsetRaw`: Asserts finished segments tie-break by numeric offset.

---

## 2. Verification & Test Results

* Targeted Unit Tests (`LiveSegmentSortingTest.kt`):
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.LiveSegmentSortingTest"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed in 11s).

* Repository Integration Tests (`LiveSegmentsRepositoryTest.kt`):
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepositoryTest"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed in 8s).

* 9-Language Localization Audit (`TranslationParityTest.kt`):
  ```bash
  ./gradlew testDebugUnitTest --tests "*TranslationParityTest*"
  ```
  Result: **BUILD SUCCESSFUL** (100% tests passed in 7s).
