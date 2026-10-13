# Stage 1: Analysis - ATT-3324: Use numeric distance values instead of formatted strings when prioritizing active live segments

**Ticket**: [ATT-3324](https://rainerblind.atlassian.net/browse/ATT-3324)  
**Sub-task**: [ATT-3356](https://rainerblind.atlassian.net/browse/ATT-3356) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*) / [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-LIV-005` (refining `REQ-LIV-003`)  
**Test Mapping**: `TST-LIV-001`  
**Branch**: `feature/ATT-3324`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Domain & Defect Forensics

### 1.1 Observable Defect & User Impact
When riding a workout route with Strava Live Segments enabled (such as during replay of 2018-04-27), multiple overlapping segments frequently share the same road corridor and trigger simultaneously. For example:
* **Segment A ("Dachtel 2 Deckenpfronn Climb")**: 2,326 m length.
* **Segment B ("Dachtel2Deckenpronn complete")**: 3,739 m length.

Both segments start at the identical road intersection in Dachtel and proceed up the same hill. During live tracking, the app tracks the shorter segment (Segment A) normally until its remaining distance drops from 1,005 m to 995 m. The instant the remaining distance drops below 1,000 meters, the active Live Segment card abruptly flips away from Segment A and displays the longer segment (Segment B, which still has ~2,400 m remaining). Later, when Segment A reaches exactly 500 m remaining, it abruptly flips back to Segment A.

This erratic flipping disorients the athlete during intense climbs, degrades glanceability, and compromises hands-free cockpit safety.

### 1.2 Forensic Root Cause Analysis (RCA)
Code investigation across `LiveSegmentsRepository.kt`, `TrackingViewModel.kt`, and `DistanceFormatter.java` revealed that active live segments are sorted using **lexicographical (alphabetical) String comparisons** on human-readable formatted display values rather than numeric numerical comparisons:

1. **Missing Raw Distance Properties in `LiveSegmentData` (`LiveSegmentsRepository.kt:50-59`)**:
   ```kotlin
   data class LiveSegmentData(
       val segmentStatus: LiveSegmentStatus,
       val timeOnSegment: String = "--:--",
       val distanceToStart: String = "--",
       val distanceOnSegment: String = "--",
       val distanceOnSegment_raw: Double = 0.0,
       val remainingDistance: String = "--",
       val segmentOffset: String = "--",
       val segmentOffset_raw: Double = 0.0
   )
   ```
   While `distanceOnSegment_raw` and `segmentOffset_raw` exist as numeric `Double` values, `remainingDistance` and `distanceToStart` exist **exclusively as formatted `String`s**.

2. **Unit Threshold Shift in `DistanceFormatter.java`**:
   At values $\ge 1,000\text{ m}$, `DistanceFormatter.format_with_units()` formats in kilometers (e.g. `"1.05 km"`, `"2.40 km"`). At values $< 1,000\text{ m}$, it switches units to meters (e.g. `"995 m"`).

3. **Lexicographical Tie-Breaker in `TrackingViewModel.kt:161-175`**:
   ```kotlin
   }.thenBy {
       when(it.liveData.segmentStatus) {
           LiveSegmentStatus.ON_SEGMENT,
           LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> it.liveData.remainingDistance
           LiveSegmentStatus.APPROACHING -> it.liveData.distanceToStart
           else -> it.liveData.segmentOffset
       }
   }
   ```
   Kotlin's `thenBy { String }` delegates to `String.compareTo()`, which compares characters by ASCII code:
   * Segment A (995 m remaining): formatted string is `"995 m"`.
   * Segment B (2,400 m remaining): formatted string is `"2.40 km"`.
   * Character `'9'` has ASCII value 57; character `'2'` has ASCII value 50.
   * Because `'2' < '9'`, Kotlin evaluates `"2.40 km" < "995 m"` as `true`.
   * In an ascending sort (`thenBy`), Segment B ("2.40 km") is sorted **before** Segment A ("995 m")!

4. **Secondary Flip at 500 m**:
   At 500 m remaining, Segment A crosses `SEGMENT_END_DISTANCE_THRESHOLD` (500 m) and transitions from `ON_SEGMENT` to `ON_SEGMENT_CLOSE_TO_FINISH`. In Priority 1, `ON_SEGMENT_CLOSE_TO_FINISH` has weight 4 while `ON_SEGMENT` has weight 3. Priority 1 overrides Priority 2, causing Segment A to flip back to first place.

5. **Approaching and Segment Offset Vulnerability**:
   The identical flaw impacts approaching segments (`thenBy { it.liveData.distanceToStart }`, e.g. `"950 m"` vs `"1.20 km"`) and fallback offset (`thenBy { it.liveData.segmentOffset }` instead of `segmentOffset_raw`).

---

## 2. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: `REQ-LIV-003` (*Remaining distance/time vs PR*) and `TrackingViewModel.kt:161-175`.
2. **Historical Origin & Commit Trace**: Introduced during early Strava Live Segments implementation when `LiveSegmentData` was designed primarily as a presentation model for Compose UI cards.
3. **Root Reason for Existing Formulation**: The original author designed `LiveSegmentData` to bundle formatted strings (`distanceToStart`, `remainingDistance`) directly for UI rendering so composables wouldn't need formatting utilities. When writing `activeLiveSegments.sortedWith`, the author accessed `it.liveData.remainingDistance` for convenience, inadvertently triggering string ASCII comparison.
4. **Preservation of Core Invariants**:
   * Formatted string presentation in UI cards (`SegmentHeader`, `LiveSegmentSheet`) remains 100% intact.
   * `distanceToStart_raw` and `remainingDistance_raw` default to `Double.MAX_VALUE`, ensuring uninitialized segments sort cleanly to the end.
   * Gate crossing, polyline orthogonal projection, PR delta math, and 100% clean-room test pass rate remain strictly preserved.

---

## 3. User Scope Grounding (`ATT-1250`)

### In-Scope:
* Add `remainingDistance_raw: Double = Double.MAX_VALUE` to `LiveSegmentData`.
* Add `distanceToStart_raw: Double = Double.MAX_VALUE` to `LiveSegmentData`.
* Populate `remainingDistance_raw` and `distanceToStart_raw` across all lifecycle states in `LiveSegmentsRepository.kt`.
* Refactor `TrackingViewModel.kt` `activeLiveSegments` comparator to sort strictly by `remainingDistance_raw`, `distanceToStart_raw`, and `segmentOffset_raw`.
* Add targeted unit tests in `LiveSegmentSortingTest.kt` asserting correct numeric sorting across the 1 km boundary for both on-segment and approaching states under metric and imperial unit regimes.

### Out-of-Scope:
* Modifying gate geometry, cross-track thresholds, or polyline projection algorithms (`PolylineProgressTracker`).
* Modifying `DistanceFormatter.java` formatting rules or unit labels.
* Modifying Live Segment cockpit bottom sheet layouts or styling tokens.

---

## 4. Architectural Solution Strategy

```text
┌────────────────────────────────────────────────────────┐
│ LiveSegmentsRepository.kt                             │
│ • State updates compute raw Double metrics in meters   │
│ • LiveSegmentData:                                     │
│   remainingDistance_raw: Double = Double.MAX_VALUE     │
│   distanceToStart_raw: Double = Double.MAX_VALUE       │
└───────────────────────────┬────────────────────────────┘
                            │ emits LiveSegment(liveData)
                            ▼
┌────────────────────────────────────────────────────────┐
│ TrackingViewModel.kt (activeLiveSegments)              │
│ • Priority 1: Status weight (4, 3, 2, 1, 0)            │
│ • Priority 2: Raw Numeric Tie-Breaker:                 │
│   - ON_SEGMENT / CLOSE: it.liveData.remainingDistance_raw │
│   - APPROACHING:        it.liveData.distanceToStart_raw    │
│   - ELSE:               it.liveData.segmentOffset_raw      │
│   (Strict Double.compareTo — zero string comparison!) │
└────────────────────────────────────────────────────────┘
```
