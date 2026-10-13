# Stage 1 Analysis: ATT-2385 - Prevent Overlapping Distance Tick Labels on Elevation Profile X-Axis in Segment and Route Cards

**Ticket**: [ATT-2385](https://rainerblind.atlassian.net/browse/ATT-2385)  
**Sub-task**: [ATT-2484](https://rainerblind.atlassian.net/browse/ATT-2484) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2385`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Statement & Motivation

In the Segment and Route list cards (e.g. segment card *Pfefferburg_Schönaich*, distance 1.47 km), the distance tick labels on the X-axis of the elevation profile collide horizontally and overlap into an unreadable solid text block:
`200m300m400m500m600m700m800m900m1000m1100m  1,47 km` (documented in `docs/attachments/elevation_profile_overlap.png`).

This defect degrades readability, breaks visual aesthetics on segment and route cards, and creates an unprofessional impression in card previews.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic investigation of [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) and [ElevationProfileZoomMath.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMath.kt) reveals two collaborating failure mechanisms:

1. **Overly Granular Step Selection for Short-to-Mid Distances**:
   * In `ElevationProfileZoomMath.calculateAdaptiveDistanceStep(visibleDist, unit)`:
     ```kotlin
     when {
         visibleDist > 50_000 -> 10_000f
         visibleDist > 20_000 -> 5_000f
         visibleDist > 10_000 -> 2_000f
         visibleDist > 5_000 -> 1_000f
         visibleDist > 1_500 -> 500f
         visibleDist > 500 -> 100f
         else -> 50f
     }
     ```
   * For any segment or route whose visible span is between 501 m and 1,500 m (such as 1,470 m / 1.47 km), `calculateAdaptiveDistanceStep` selects a static `100f` (100 m) step.
   * A 1.47 km segment generates up to 14 intermediate ticks (100m, 200m, ..., 1400m). Across a typical card preview canvas width of 240–300 dp (~650–800 px), the horizontal distance between adjacent ticks is only ~44–54 px.
   * Labels like `"1000m"` or `"1100m"` require ~45–50 px of text width. With center-to-center distance equal to or smaller than text width, characters directly collide.

2. **Total Absence of Adjacent Label Clearance Tracking in ElevationProfile Render Loop**:
   * In `ElevationProfile.kt` (lines 627–645):
     ```kotlin
     while (currentD < currentStartDist + visibleSpan) {
         val x = ElevationProfileZoomMath.distanceToCanvasX(currentD, currentStartDist, visibleSpan, width)
         if (x > 60f && (width - x) > (endLabelWidth + 50f)) {
             canvas.nativeCanvas.drawLine(x, height, x, height - 10f, textPaint)
             val label = ...
             val lWidth = textPaint.measureText(label)
             canvas.nativeCanvas.drawText(label, x - (lWidth / 2), height + 45f, textPaint)
         }
         currentD += adaptiveDistStep
     }
     ```
   * The render loop only checks whether `x > 60f` and `(width - x) > (endLabelWidth + 50f)`.
   * It **never tracks `lastDrawnRightX`** and performs **zero clearance checks between consecutive intermediate labels**.
   * Furthermore, `width - x > endLabelWidth + 50f` checks center distance rather than `labelRight = x + (lWidth / 2)`, leading to abrupt cutoffs or right-edge collisions with `endLabel`.
   * The exact same absence of `lastDrawnRightX` tracking exists in the elapsed time domain (`isTimeDomain`, lines 611–620).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. *Adaptive Step Optimization*: Refine `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` to provide balanced, readable step increments for short and mid-range tracks (e.g. 500 m step for > 1,500 m or > 2,000 m; 250 m step for 800 m–2,000 m; 100 m step for 300 m–800 m; 50 m step for 150 m–300 m; 25 m step for <= 150 m).
  2. *Adaptive Step Width Guard*: Allow `calculateAdaptiveDistanceStep` to consider available canvas width (or target pixel spacing) where applicable, avoiding excessively dense ticks on narrow card previews.
  3. *Dynamic Collision Prevention & Clearance Invariant*: Update both distance and time tick rendering loops in `ElevationProfile.kt` to track `lastDrawnRightX` and enforce a guaranteed minimum clearance (`minSpacingPx = 24.dp.toPx()`) between adjacent labels, with `startLabel`, and with `endLabel`.
  4. *Synchronized Tick Mark Rendering*: Ensure tick marks (`drawLine`) and tick labels (`drawText`) are rendered together only when label spacing clearance is met, eliminating cluttered orphan tick lines.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to altitude (Y-axis) bounds or grade coloring math (`calculateElevationBounds`, `calculateElevationProfileHeight`).
  * No modification to scrub coordinate translation (`onDistanceSelected`, `canvasXToDistance`).
  * No change to telemetry card layout, route grouping, or list card adapters.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Refines Clause 5 of `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*) and `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture*).
* **Historical Origin & Commit Trace**:
  - `ATT-527` (Sprint `2026-40.4`, commit `69286d4e`): Introduced `ElevationProfileZoomMath.calculateAdaptiveDistanceStep`.
  - `ATT-1391` (Sprint `2026-40.5`): Introduced `calculateAdaptiveTimeStep` and time-domain ticks.
  - `ATT-1819` / `REQ-UI-220` (Sprint `2026-40.7`): Introduced `TelemetryMetricUtils.shouldRenderMilestoneLabel` with `lastDrawnRightX` tracking for continuous telemetry graphs in `TelemetryMetricGraph.kt`.
* **Root Reason for Existing Formulation**:
  - `ElevationProfile.kt` was written before `TelemetryMetricUtils.shouldRenderMilestoneLabel` was introduced in `TelemetryMetricGraph.kt`. The static `x > 60f` check was a rudimentary heuristic that was never upgraded to dynamic clearance tracking.
* **Preservation of Core Invariants**:
  - Existing zooming math, pan clamping, scrubbing accuracy, unit conversion (Metric / Imperial), and clean-room test suite pass rates are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Step Interval Calibration (`ElevationProfileZoomMath.kt`)**:
   * Add 250 m step for distances between 800 m and 2,000 m (or provide a canvas-width-aware overload `calculateAdaptiveDistanceStep(visibleDist, unit, canvasWidthPx)`):
     - Metric:
       - `visibleDist > 50_000` -> 10,000 m
       - `visibleDist > 20_000` -> 5,000 m
       - `visibleDist > 10_000` -> 2,000 m
       - `visibleDist > 5_000` -> 1,000 m
       - `visibleDist > 2_000` -> 500 m
       - `visibleDist > 800` -> 250 m (addresses 1.47 km segments: yields 5 ticks instead of 14)
       - `visibleDist > 300` -> 100 m
       - `visibleDist > 150` -> 50 m
       - else -> 25 m
     - Imperial:
       - `visibleMiles > 30` -> 5 mi
       - `visibleMiles > 10` -> 2 mi
       - `visibleMiles > 3` -> 1 mi
       - `visibleMiles > 1` -> 0.5 mi
       - `visibleMiles > 0.5` -> 0.25 mi
       - `visibleMiles > 0.2` -> 0.1 mi
       - else -> 0.05 mi
2. **Dynamic Label Collision Avoidance (`ElevationProfile.kt`)**:
   * In both `isTimeDomain` and distance domain rendering branches:
     * Initialize `lastDrawnRightX`: if zoomed in (`currentZoomScale > 1.01f`), `lastDrawnRightX = startLabelWidth`; otherwise `0f`.
     * For each intermediate candidate tick at `x`:
       - `val labelLeft = x - (labelWidth / 2f)`
       - `val labelRight = x + (labelWidth / 2f)`
       - Check:
         `labelLeft >= (if (lastDrawnRightX > 0f) lastDrawnRightX + minSpacingPx else minSpacingPx)`
         `&& labelRight <= (width - endLabelWidth - minSpacingPx)`
       - If clearance is satisfied:
         - Draw tick mark notch (`drawLine`).
         - Draw text label (`drawText`).
         - Update `lastDrawnRightX = labelRight`.
       - If clearance is NOT satisfied:
         - Skip this tick, ensuring zero overlapping text.
3. **Guaranteed Spacing**:
   * Standardize `minSpacingPx = 24.dp.toPx()` (consistent with Material 3 typography bounds and `TelemetryMetricUtils`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit test suites and `ElevationProfileZoomMathTest`.
  2. Single-line label readability guaranteed under any canvas width (portrait, landscape, split-screen, tablet).
  3. Parent ticket Human Decision Gate strictly preserved.
* **Risk Rating**: **LOW**
  - Self-contained UI rendering and mathematical step calculation with zero database, persistence, or background thread implications.
