# Stage 3: Implementation Plan - ATT-3324: Use numeric distance values instead of formatted strings when prioritizing active live segments

**Ticket**: [ATT-3324](https://rainerblind.atlassian.net/browse/ATT-3324)  
**Sub-task**: [ATT-3358](https://rainerblind.atlassian.net/browse/ATT-3358) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*) / [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-LIV-005` (refining `REQ-LIV-003`)  
**Test Mapping**: `TST-LIV-001`  
**Branch**: `feature/ATT-3324`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background
When riding a workout route with Strava Live Segments enabled (such as during replay of 2018-04-27), multiple overlapping segments frequently share the same road corridor and trigger simultaneously. When candidate segments are evaluated in `TrackingViewModel.kt:activeLiveSegments`, secondary tie-breaking previously evaluated formatted display strings (`remainingDistance`, `distanceToStart`, `segmentOffset`).

Because `DistanceFormatter.java` shifts units from meters (`"995 m"`) to kilometers (`"2.40 km"`) at the 1,000 m boundary, lexicographical string comparison evaluated `"2.40 km" < "995 m"` as `true` (ASCII `'2'` = 50 < `'9'` = 57). Consequently, active segments abruptly flipped away to longer segments the moment remaining distance dropped below 1 km, before flipping back at 500 m (`ON_SEGMENT_CLOSE_TO_FINISH`).

To permanently resolve this defect, the system will add unrounded numeric double-precision meter properties (`remainingDistance_raw`, `distanceToStart_raw`) to `LiveSegmentData` and refactor `TrackingViewModel.kt` to sort strictly by `Double` properties.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-LIV-005` (*Active and Approaching Live Segments Numeric Distance Prioritization*, refining `REQ-LIV-003`)
* **Test Mapping**: `TST-LIV-001` (*Active and Approaching Live Segments Numeric Distance Sorting Verification*)
  * `TST-LIV-001.1`: On-segment sorting across 1 km boundary (`LiveSegmentSortingTest.kt`)
  * `TST-LIV-001.2`: Approaching segment sorting across 1 km boundary (`LiveSegmentSortingTest.kt`)
  * `TST-LIV-001.3`: Status weight priority preservation (`LiveSegmentSortingTest.kt`)
  * `TST-LIV-001.4`: Imperial units formatting independence (`LiveSegmentSortingTest.kt`)
  * `TST-LIV-001.5`: 9-language localization audit (`TranslationParityTest.kt`)
  * `TST-LIV-001.6`: Full clean-room regression suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing live segment progress tracking, virtual gate crossing math, and PR pacing remain 100% operational.
2. **UI Card Presentation Invariance**: Formatted string properties (`remainingDistance`, `distanceToStart`, `distanceOnSegment`, `segmentOffset`) remain unchanged for Jetpack Compose UI rendering.
3. **Default Value Safety**: `remainingDistance_raw` and `distanceToStart_raw` default to `Double.MAX_VALUE`, ensuring inactive or uninitialized segments sort cleanly to the end of any candidate list.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `LiveSegmentData` Model & State Updates (`LiveSegmentsRepository.kt`)
* **Layer**: Domain / Repository (`com.atrainingtracker.trainingtracker.segments`)
* **Changes**:
  * In `LiveSegmentData`, add:
    * `val remainingDistance_raw: Double = Double.MAX_VALUE`
    * `val distanceToStart_raw: Double = Double.MAX_VALUE`
  * In `LiveSegmentsRepository.kt`:
    * Under `LiveSegmentStatus.APPROACHING`:
      * `distanceToStart_raw = distanceToStart`
      * `remainingDistance_raw = liveSegment.staticData.summary.distance_raw + distanceToStart`
    * Under `LiveSegmentStatus.ON_SEGMENT`, `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`:
      * `distanceToStart_raw = 0.0`
      * `remainingDistance_raw = remainingDistance`
      * `segmentOffset_raw = distanceToSegment`
    * Under `LiveSegmentStatus.FINISHED`:
      * Initial finish: `distanceToStart_raw = 0.0`, `remainingDistance_raw = 0.0`, `segmentOffset_raw = distanceToEnd`
      * Later updates: `segmentOffset_raw = distanceToEnd`
    * Under `LiveSegmentStatus.FAR_FAR_AWAY`:
      * Default `remainingDistance_raw = Double.MAX_VALUE`, `distanceToStart_raw = Double.MAX_VALUE`

### Component 2: Reactive Candidate Prioritization (`TrackingViewModel.kt`)
* **Layer**: UI Architecture / ViewModel (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)
* **Changes**:
  * In `activeLiveSegments`, refactor `thenBy` tie-breaker to evaluate strictly against `Double` properties:
    * `LiveSegmentStatus.ON_SEGMENT`, `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH` -> `it.liveData.remainingDistance_raw`
    * `LiveSegmentStatus.APPROACHING` -> `it.liveData.distanceToStart_raw`
    * `else` -> `it.liveData.segmentOffset_raw`

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. (Visual cards and styling are untouched; only the internal ViewModel candidate sorting comparator and repository data model are updated).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Raw Distance Properties to `LiveSegmentData` & Update `LiveSegmentsRepository.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt`
* **Changes**:
  * Add `remainingDistance_raw` and `distanceToStart_raw` properties with default `Double.MAX_VALUE` to `LiveSegmentData`.
  * Populate `distanceToStart_raw`, `remainingDistance_raw`, and `segmentOffset_raw` across `APPROACHING`, `ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, and `FINISHED` state branches.

### Step 2: Refactor Tie-Breaker Comparator in `TrackingViewModel.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* **Changes**:
  * Replace string property accesses in `activeLiveSegments` comparator with `it.liveData.remainingDistance_raw`, `it.liveData.distanceToStart_raw`, and `it.liveData.segmentOffset_raw`.

### Step 3: Author Unit Tests in `LiveSegmentSortingTest.kt`
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/LiveSegmentSortingTest.kt`
* **Changes**:
  * Implement tests validating:
    * Boundary crossing with 995 m vs 2,400 m in `ON_SEGMENT`.
    * Approaching boundary crossing with 950 m vs 1,200 m in `APPROACHING`.
    * Priority 1 status weight preservation (`ON_SEGMENT_CLOSE_TO_FINISH` > `ON_SEGMENT`).
    * Comparator stability across imperial and metric formatting.
* **Targeted Test Execution**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.LiveSegmentSortingTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit test run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.LiveSegmentSortingTest"`.
  2. Full clean-room test suite run: `./gradlew testDebugUnitTest` ensuring 100% pass rate with zero regressions.
* **Rollback Plan**:
  * Changes are isolated on `feature/ATT-3324`. If unexpected regressions occur, branch can be cleanly reset to `sprint/2026-42.1`.
