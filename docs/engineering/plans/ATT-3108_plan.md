# Stage 3: Implementation Plan - ATT-3108: LiveSegmentSheet crashes with NumberFormatException attempting to parse formatted distance string on segment finish

**Ticket**: [ATT-3108](https://atrainingtracker.atlassian.net/browse/ATT-3108)  
**Sub-task**: [ATT-3155](https://atrainingtracker.atlassian.net/browse/ATT-3155) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*)  
**Test Mapping**: `TST-UI-300` (*LiveSegmentSheet Finish State Type-Safe Metric Resolution & NumberFormatException Elimination Verification*)  
**Branch**: `feature/ATT-3108`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

When completing a live segment during active tracking, `LIveSegmentSheet.kt` crashed with `NumberFormatException: For input string: "766 m"` because `liveSegment.staticData.summary.distance.toDouble()` was invoked. In `SegmentSummary`, `distance` is a localized formatted string (e.g. `"766 m"` or `"1.2 km"`), whereas `distance_raw` is the canonical numeric `Double` in meters.

This plan specifies the atomic steps to eliminate string parsing by utilizing `summary.distance_raw`, update the contract test in `LiveSegmentSheetContractTest.kt`, add unit tests in `LiveSegmentSheetLayoutTest.kt`, and verify complete system regression integrity.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*)
* **Test Mapping**: `TST-UI-300` (*LiveSegmentSheet Finish State Type-Safe Metric Resolution & NumberFormatException Elimination Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Type Safety & Zero String Parsing**: No formatted display string with units (`"766 m"`, `"1.2 km"`) shall ever be parsed into a numeric primitive via `toDouble()`.
2. **Scrubber Pin Placement**: When a live segment completes (`LiveSegmentStatus.FINISHED`), `externalScrubDistance` shall evaluate to `summary.distance_raw.coerceAtLeast(liveData.distanceOnSegment_raw)`, keeping the scrubber pin aligned with the completed segment end.
3. **Bottom Sheet Layout Invariants**: Wrap-content height, suppressed map (`showMap = false`), suppressed zoom controls (`showZoomControls = false`), and unified surface styling remain intact.
4. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
5. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3108` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Step 1: `LIveSegmentSheet.kt` - Direct `distance_raw` Reference
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt`:
Update line 43:
```kotlin
val externalScrubDistance = when (liveSegment.liveData.segmentStatus) {
    LiveSegmentStatus.ON_SEGMENT,
    LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH -> liveSegment.liveData.distanceOnSegment_raw
    LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
    LiveSegmentStatus.APPROACHING,
    LiveSegmentStatus.FAR_FAR_AWAY -> null
}
```

### Step 2: `LiveSegmentSheetContractTest.kt` - Update Contract Assertion
In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetContractTest.kt`:
Update lines 128–130:
```kotlin
assertTrue(
    "LiveSegmentSheet must map FINISHED to total distance or distanceOnSegment_raw using distance_raw",
    content.contains("LiveSegmentStatus.FINISHED -> liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)")
)
```

### Step 3: `LiveSegmentSheetLayoutTest.kt` - Unit Tests for Segment Finish Distance Evaluation
In `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt`:
Add unit tests verifying:
1. `LiveSegmentStatus.FINISHED` with `summary.distance = "766 m"` and `summary.distance_raw = 766.0` does not crash with `NumberFormatException`.
2. Segment finish evaluation with localized decimal commas (`summary.distance = "1,2 km"`, `summary.distance_raw = 1200.0`) evaluates safely to `1200.0`.

---

## 5. Verification Strategy

1. Run targeted unit test suite:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheet*"
   ```
2. Run full clean-room unit test suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. Verify zero regressions across all 2350+ tests.
