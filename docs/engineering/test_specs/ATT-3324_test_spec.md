# Stage 2: Requirement & Test Specification - ATT-3324: Use numeric distance values instead of formatted strings when prioritizing active live segments

**Ticket**: [ATT-3324](https://rainerblind.atlassian.net/browse/ATT-3324)  
**Sub-task**: [ATT-3357](https://rainerblind.atlassian.net/browse/ATT-3357) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*) / [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-LIV-005` (refining `REQ-LIV-003`)  
**Test Spec ID**: `TST-LIV-001`  
**Branch**: `feature/ATT-3324`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (REQ-LIV-005)

### 1.1 Problem Statement & Rationale
When riding a workout route with Strava Live Segments enabled (e.g. replay of 2018-04-27), multiple overlapping segments frequently share the same road corridor and trigger concurrently (e.g. "Dachtel 2 Deckenpfronn Climb", 2,326 m vs "Dachtel2Deckenpronn complete", 3,739 m). During live tracking, the app tracks the shorter segment until its remaining distance drops below 1,000 meters. The instant remaining distance reaches 995 m, the active Live Segment card flips abruptly to the longer segment (~2,400 m remaining), before flipping back at 500 m.

This erratic behavior is caused by sorting candidate segments using lexicographical (alphabetical) String comparisons on human-readable formatted display values (`DistanceFormatter.format_with_units()`) where `"2.40 km" < "995 m"` evaluates to `true` (ASCII `'2'` = 50 < `'9'` = 57). The system must prioritize active and approaching live segments strictly by raw numeric distance values in meters (`Double`).

### 1.2 Functional & Architectural Requirements
The system SHALL prioritize and sort active and approaching Strava Live Segments during live tracking (`TrackingViewModel.kt`) strictly by raw numeric distance values in meters (`Double`), eliminating unit-boundary sorting inversions and erratic active segment flipping caused by lexicographical string comparisons (ATT-3324, refining `REQ-LIV-003`):

1. **Raw Distance Representation in `LiveSegmentData` (`LiveSegmentsRepository.kt`)**:
   * The system SHALL define `val remainingDistance_raw: Double = Double.MAX_VALUE` in `LiveSegmentData`.
   * The system SHALL define `val distanceToStart_raw: Double = Double.MAX_VALUE` in `LiveSegmentData`.
   * Default initialization SHALL be `Double.MAX_VALUE` so that uninitialized or inactive segment states naturally sort to the end.

2. **State-Aware Raw Distance Metric Population (`LiveSegmentsRepository.kt`)**:
   * For `LiveSegmentStatus.APPROACHING`: `distanceToStart_raw` SHALL be populated with the athlete's along-track geodesic distance to the virtual start gate (`distanceToStart`), and `remainingDistance_raw` SHALL be populated with `liveSegment.staticData.summary.distance_raw + distanceToStart`.
   * For `LiveSegmentStatus.ON_SEGMENT` and `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: `distanceToStart_raw` SHALL evaluate to `0.0`, and `remainingDistance_raw` SHALL be populated with the remaining along-track distance to the virtual finish gate (`remainingDistance`).
   * For `LiveSegmentStatus.FINISHED`: `distanceToStart_raw` SHALL evaluate to `0.0`, `remainingDistance_raw` SHALL evaluate to `0.0`, and `segmentOffset_raw` SHALL be populated with the athlete's distance beyond the finish gate (`distanceToEnd`).
   * For `LiveSegmentStatus.FAR_FAR_AWAY`: `distanceToStart_raw` and `remainingDistance_raw` SHALL default to `Double.MAX_VALUE`.

3. **Strict Numeric Sorting Comparator in `TrackingViewModel.kt`**:
   * In `activeLiveSegments`, secondary tie-breaking SHALL evaluate strictly against raw numeric `Double` metrics:
     * For `LiveSegmentStatus.ON_SEGMENT` and `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`: tie-break ascending by `it.liveData.remainingDistance_raw`.
     * For `LiveSegmentStatus.APPROACHING`: tie-break ascending by `it.liveData.distanceToStart_raw`.
     * For any other status: tie-break ascending by `it.liveData.segmentOffset_raw`.
   * Formatted strings (`remainingDistance`, `distanceToStart`, `segmentOffset`) MUST NOT be used in the sorting comparator.

4. **System Invariants**:
   * Segment progress calculation, gate crossing detection, orthogonal polyline projection (`REQ-GEO-001`), PR pacing comparison, and visual UI formatting via `DistanceFormatter` MUST NOT be altered.
   * `LiveSegmentData` formatted string properties (`remainingDistance`, `distanceToStart`, `segmentOffset`, `distanceOnSegment`) SHALL remain fully intact for Compose UI rendering.
   * 100% full-suite unit test pass rate.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (On-Segment Sorting Across 1 km Boundary)**:
  * *Given* two active live segments on the same trajectory (Segment A with 995 m remaining formatted as `"995 m"`, and Segment B with 2,400 m remaining formatted as `"2.40 km"`),
  * *When* `activeLiveSegments` comparator evaluates priority,
  * *Then* Segment A SHALL be prioritized ahead of Segment B (995.0 < 2400.0), with zero UI card flipping.
* **Criterion 2 (Approaching Segments Sorting Across 1 km Boundary)**:
  * *Given* two approaching live segments (Segment A at 950 m to start gate formatted as `"950 m"`, and Segment B at 1,200 m to start gate formatted as `"1.20 km"`),
  * *When* `activeLiveSegments` comparator evaluates priority,
  * *Then* Segment A SHALL be prioritized ahead of Segment B (950.0 < 1200.0).
* **Criterion 3 (Raw Numeric Representation & Precision)**:
  * *Given* any `LiveSegmentData` emitted across `APPROACHING`, `ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, and `FINISHED`,
  * *When* inspecting `remainingDistance_raw` and `distanceToStart_raw`,
  * *Then* both properties contain exact unrounded double-precision meter distances.
* **Criterion 4 (Status Weight Priority Invariant)**:
  * *Given* Segment A in `ON_SEGMENT_CLOSE_TO_FINISH` and Segment B in `ON_SEGMENT`,
  * *When* `activeLiveSegments` evaluates priority,
  * *Then* Segment A SHALL outrank Segment B based on primary status weight (4 > 3).

### 1.4 System Invariants
* Thread-safety of `LiveSegmentsRepository` flow emissions (`StateFlow<List<LiveSegment>>`) MUST NOT be compromised.
* Existing formatted string contracts in `LiveSegmentData` MUST NOT be broken.
* No changes to database persistence or Strava API models.
* Zero regressions across the full unit test suite (`./gradlew testDebugUnitTest`).

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-LIV-003` (*Remaining distance/time vs PR*) and `TrackingViewModel.kt:161-175`.
* **Historical Origin & Commit Trace**: Introduced during early Strava Live Segments implementation when `LiveSegmentData` was designed primarily as a presentation model for Compose UI cards.
* **Root Reason for Existing Formulation**: In early development, `LiveSegmentData` was structured to bundle formatted strings (`distanceToStart`, `remainingDistance`) directly for UI rendering so composables wouldn't need formatting utilities. When writing `activeLiveSegments.sortedWith`, the author accessed `it.liveData.remainingDistance` for convenience, inadvertently triggering string ASCII comparison.
* **Preservation of Core Invariants**: Formatted string presentation in UI cards (`SegmentHeader`, `LiveSegmentSheet`) remains 100% intact. `distanceToStart_raw` and `remainingDistance_raw` default to `Double.MAX_VALUE`, ensuring uninitialized segments sort cleanly to the end. Gate crossing, polyline orthogonal projection, PR delta math, and 100% clean-room test pass rate remain strictly preserved.

---

## 2. Test Specification (TST-LIV-001)

### Test Case 1: `activeLiveSegments_onSegment_sortsNumericallyAcrossOneKmBoundary` (`TST-LIV-001.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/LiveSegmentSortingTest.kt`
* **Preconditions**: Two segments in `LiveSegmentStatus.ON_SEGMENT`. Segment A has `remainingDistance_raw = 995.0` (`remainingDistance = "995 m"`). Segment B has `remainingDistance_raw = 2400.0` (`remainingDistance = "2.40 km"`).
* **Action**: Evaluate sorting via comparator or `TrackingViewModel.activeLiveSegments`.
* **Expected Result**: Segment A is ordered before Segment B (`[Segment A, Segment B]`).

### Test Case 2: `activeLiveSegments_approaching_sortsNumericallyAcrossOneKmBoundary` (`TST-LIV-001.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/LiveSegmentSortingTest.kt`
* **Preconditions**: Two segments in `LiveSegmentStatus.APPROACHING`. Segment A has `distanceToStart_raw = 950.0` (`distanceToStart = "950 m"`). Segment B has `distanceToStart_raw = 1200.0` (`distanceToStart = "1.20 km"`).
* **Action**: Evaluate sorting via comparator.
* **Expected Result**: Segment A is ordered before Segment B (`[Segment A, Segment B]`).

### Test Case 3: `activeLiveSegments_statusWeight_preservesHierarchyOverRawDistance` (`TST-LIV-001.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/LiveSegmentSortingTest.kt`
* **Preconditions**: Segment A has status `ON_SEGMENT_CLOSE_TO_FINISH` with `remainingDistance_raw = 450.0`. Segment B has status `ON_SEGMENT` with `remainingDistance_raw = 100.0`.
* **Action**: Evaluate sorting via comparator.
* **Expected Result**: Segment A is ordered before Segment B due to higher status weight (4 > 3).

### Test Case 4: `activeLiveSegments_imperialUnits_sortsNumericallyWithoutStringArtifacts` (`TST-LIV-001.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/LiveSegmentSortingTest.kt`
* **Preconditions**: Segments with formatted strings under imperial settings (e.g. `"0.62 mi"` vs `"900 yd"`).
* **Action**: Evaluate sorting strictly by raw Double meters.
* **Expected Result**: Sort order remains strictly determined by meter distances regardless of localized/imperial string representations.

### Test Case 5: 9-Language Localization & Specifier Audit (`TST-LIV-001.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches (0 new UI strings introduced).

### Test Case 6: Clean-Room Regression Suite (`TST-LIV-001.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (0 failures, 0 regressions).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-LIV-001.1` | Unit | `TrackingViewModel.activeLiveSegments` | `REQ-LIV-005` Clause 1, 2, 3 | Specified |
| `TST-LIV-001.2` | Unit | `TrackingViewModel.activeLiveSegments` | `REQ-LIV-005` Clause 1, 2, 3 | Specified |
| `TST-LIV-001.3` | Unit | `TrackingViewModel.activeLiveSegments` | `REQ-LIV-005` Clause 3 | Specified |
| `TST-LIV-001.4` | Unit | `TrackingViewModel.activeLiveSegments` | `REQ-LIV-005` Clause 3 | Specified |
| `TST-LIV-001.5` | Localization | `TranslationParityTest` | `REQ-LIV-005`, `REQ-UI-106` | Specified |
| `TST-LIV-001.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
