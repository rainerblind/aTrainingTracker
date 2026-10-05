# Stage 3: Implementation Plan - ATT-2385: Prevent Overlapping Distance Tick Labels on Elevation Profile X-Axis in Segment and Route Cards

**Ticket**: [ATT-2385](https://rainerblind.atlassian.net/browse/ATT-2385)  
**Sub-task**: [ATT-2486](https://rainerblind.atlassian.net/browse/ATT-2486) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-272`  
**Test Mapping**: `TST-UI-232`  
**Branch**: `feature/ATT-2385`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Description & Background

In segment and route card previews (such as the segment card *Pfefferburg_Schönaich*, 1.47 km), the distance tick labels on the X-axis of the elevation profile collide horizontally and overlap into an unreadable solid text block:
`200m300m400m500m600m700m800m900m1000m1100m  1,47 km` (documented in `docs/attachments/elevation_profile_overlap.png`).

The root causes are twofold:
1. `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` selects a 100 m step for any track whose visible span is between 501 m and 1,500 m. A 1.47 km track generates 14 ticks, producing an inter-tick spacing of only ~44–54 px across typical card preview widths (~240–300 dp), while labels like `"1000m"` require ~45–50 px.
2. The rendering loop in [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) performs zero adjacent label clearance tracking (`lastDrawnRightX`), allowing subsequent ticks to paint directly on top of previous labels.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-272` (*Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention*)
  * Refines Clause 5 of `REQ-UI-192` and `REQ-UI-201`.
  * Mandates calibrated step intervals for short and mid-range tracks (e.g. 250 m step for 800 m–2,000 m).
  * Enforces dynamic label collision avoidance via `shouldRenderTickLabel` with minimum clearance $\ge 24\text{dp}$.
  * Mandates synchronized suppression of vertical tick notch lines when labels are skipped.
* **Test Mapping**: `TST-UI-232` (*Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention Verification*)
  * `TST-UI-232.1`: Unit test step calibration across distance boundaries in `ElevationProfileZoomMathTest.kt`.
  * `TST-UI-232.2`: Collision evaluation clearance tests in `ElevationProfileZoomMathTest.kt`.
  * `TST-UI-232.3`: Architectural contract test in `ElevationProfileContractTest.kt` verifying `lastDrawnRightX` tracking in both distance and time rendering loops.
  * `TST-UI-232.4`: Full clean-room test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Scrubbing Invariant**: Distance-to-canvas coordinate translation (`distanceToCanvasX`, `canvasXToDistance`, `onDistanceSelected`) remains strictly unchanged.
3. **Elevation Bounds Invariant**: Vertical altitude bounds and grade color mapping (`calculateElevationBounds`, `calculateElevationProfileHeight`) are completely preserved.
4. **Contextual Zoom Invariant**: Zoom control suppression in preview cards (`REQ-UI-197`) remains strictly intact.
5. **Subtask Self-Sufficiency**: Subtask [ATT-2486](https://rainerblind.atlassian.net/browse/ATT-2486) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2385](https://rainerblind.atlassian.net/browse/ATT-2385) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationProfileZoomMath.kt`
* Add 250 m intermediate step for metric distances between 800 m and 2,000 m, and 0.25 mi step for imperial distances between 0.5 mi and 1.0 mi.
* Implement `shouldRenderTickLabel(labelLeft, labelRight, lastDrawnRightX, startClearanceThreshold, endClearanceThreshold, minSpacing): Boolean` providing reusable clearance validation.

### Component 2: `ElevationProfile.kt`
* In distance and time domain X-axis rendering loops:
  * Initialize `lastDrawnRightX` to track the right edge of `startLabel` (if drawn) or `0f`.
  * Compute `labelLeft = x - (labelWidth / 2f)` and `labelRight = x + (labelWidth / 2f)`.
  * Guard drawing with `ElevationProfileZoomMath.shouldRenderTickLabel(...)`.
  * Synchronize tick mark drawing: draw vertical tick notch `drawLine` only when the label satisfies clearance.
  * Update `lastDrawnRightX = labelRight` upon successful render.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) and [TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt).
* **Reused components**: Canvas `nativeCanvas.drawText`, `drawLine`, `highlightPaint`, `textPaint`, `24.dp` clearance threshold.
* **Theme tokens**: `MaterialTheme.colorScheme.onSurfaceVariant`, `1.dp.toPx()`, `24.dp`.
* **New one-off styles & justification**: None. Reuses existing typography, paints, and spacing tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Calibrate Step Intervals & Add Clearance Helper in `ElevationProfileZoomMath.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMath.kt`
* **Changes**:
  * In `calculateAdaptiveDistanceStep`:
    * Metric: add `visibleDist > 800 -> 250f` (before `300 -> 100f`).
    * Imperial: add `visibleMiles > 0.5 -> 0.25f` (before `0.2 -> 0.1f`).
  * Add `shouldRenderTickLabel(...)` helper.

### Step 2: Implement Dynamic Clearance Guard in `ElevationProfile.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Changes**:
  * In `drawIntoCanvas`:
    * Set `val minSpacingPx = 24.dp.toPx()` and `val endBoundaryThreshold = width - endLabelWidth - minSpacingPx`.
    * Update `isTimeDomain` loop with `lastDrawnRightX` tracking and `shouldRenderTickLabel`.
    * Update distance domain loop with `lastDrawnRightX` tracking and `shouldRenderTickLabel`.
    * Synchronize `drawLine` tick notch with label rendering.

### Step 3: Implement Unit & Contract Tests
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileContractTest.kt`
* **Changes**:
  * Add unit tests in `ElevationProfileZoomMathTest.kt` verifying 1,470 m returns 250 m and testing clearance logic.
  * Add architectural contract tests verifying `ElevationProfile.kt` maintains `lastDrawnRightX` tracking and `shouldRenderTickLabel`.

### Step 4: Targeted Unit Verification
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileZoomMathTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileContractTest"
  ```
* **Success Criteria**: 100% test pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted unit and contract tests passing with 0 failures.
  2. Full clean-room regression suite (`./gradlew testDebugUnitTest`) verifying 0 regressions.
  3. Gate 4 and Gate 5 automated audits before in-sprint merge.
* **Rollback**:
  - Branch isolation on `feature/ATT-2385` allows full revert via git checkout without affecting `sprint/2026-41.1`.
