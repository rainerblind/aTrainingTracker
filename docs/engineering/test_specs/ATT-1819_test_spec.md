# Stage 2: Requirement & Test Specification - ATT-1819: Fix unreadable x-Axis milestone collisions and text overlapping on HR and Pace graphs

**Ticket**: [ATT-1819](https://rainerblind.atlassian.net/browse/ATT-1819)  
**Sub-task**: [ATT-1886](https://rainerblind.atlassian.net/browse/ATT-1886) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-220` (*Aftermath Continuous Telemetry Graphs: Adaptive X-Axis Milestone Decimation, Collision Prevention, and Standardized Boundary Labeling*)  
**Test Spec ID**: `TST-UI-174`  
**Branch**: `feature/ATT-1819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-220)

### 1.1 Problem Statement & Rationale
In post-workout detailed inspection (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`), secondary continuous graphs (Speed/Pace, Heart Rate, and Power) render X-axis milestone markers along the bottom axis. On moderate-to-long workouts (e.g. 17.82 km run *"Mit Jonas durch den Wald"*), every kilometer marker (`0 km` to `17 km`) is drawn unconditionally without clearance checks.

Because each label repeats `" km"` or `" mi"`, milestone widths (~35–45 dp) exceed the distance between ticks (~17 dp), causing all labels to collide into an unreadable solid text mash (`0 km1 km2 km...17 km`). Additionally, secondary graphs lack start and end boundary labels (unlike `ElevationProfile`, which anchors `17,82 km`), drawing `0 km` directly over the left Y-axis origin.

To guarantee professional visual aesthetics, clarity, and readability, the system must refine the adaptive step progression, standardize milestone formatting without redundant units, anchor start/end boundary labels, and enforce dynamic collision avoidance / decimation.

### 1.2 Functional & Architectural Requirements

1. **Adaptive Distance Step Refinement (`ElevationProfileZoomMath.kt`)**:
   - `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` SHALL introduce an intermediate step for metric workouts between 10 km and 20 km:
     `visibleDist > 10_000 -> 2_000f`.
   - Workouts with visible distance in `(10_000, 20_000]` meters SHALL produce 2 km tick intervals, generating ~5–9 milestone ticks rather than 10–19 ticks.
   - All existing step thresholds (`> 50_000 -> 10_000f`, `> 20_000 -> 5_000f`, `> 5_000 -> 1_000f`, `> 1_500 -> 500f`, `> 500 -> 100f`, `else -> 50f`) and Imperial step thresholds SHALL remain strictly preserved.

2. **Standardized Milestone Label Formatting (`TelemetryMetricGraph.kt`)**:
   - In `TelemetryMetricGraph.kt`, intermediate milestone labels SHALL NOT repeat `" km"` or `" mi"` on every tick:
     - Metric: When `visibleSpan < 1500`, format as `"${currentDist.toInt()}m"`; when decimal (`currentDist % 1000.0 != 0.0`), format as `"%.1f"`; else format as `"${(currentDist / 1000.0).toInt()}"` (e.g. `"2"`, `"4"`, `"6"`).
     - Imperial: When decimal, format as `"%.1f"`; else format as `"${miles.toInt()}"` (e.g. `"2"`, `"4"`, `"6"`).
   - This matches the clean milestone notation of `ElevationProfile.kt`, reducing label width by ~65% and preventing text bloat.

3. **Start and End Boundary Labels Parity (`TelemetryMetricGraph.kt`)**:
   - `TelemetryMetricGraph` SHALL instantiate a remembered `DistanceFormatter`:
     `val distanceFormatter = remember(unit) { DistanceFormatter() }`
   - An explicit `endLabel` with units SHALL be rendered at the right edge of the chart:
     - Distance domain: `distanceFormatter.format_with_units(startDist + visibleSpan)` (e.g. `"17,82 km"`).
     - Time domain: `ElevationProfileZoomMath.formatTimeTick((startDist + visibleSpan).toLong())`.
     - Positioned at `startPaddingPx + chartWidthPx - endLabelWidth`.
   - When zoomed ($Z > 1.01$), an explicit `startLabel` with units SHALL be rendered at `startPaddingPx`:
     - Distance domain: `distanceFormatter.format_with_units(startDist)`.
     - Time domain: `ElevationProfileZoomMath.formatTimeTick(startDist.toLong())`.

4. **Dynamic Minimum Pixel Clearance & Decimation Algorithm (`TelemetryMetricGraph.kt`)**:
   - **Start & End Boundary Margin Clearance**: Intermediate milestone labels SHALL NOT be rendered within 40 dp of the start padding (`tickX <= startPaddingPx + 40.dp.toPx()`) or within `endLabelWidth + 16.dp.toPx()` of the right chart edge (`startPaddingPx + chartWidthPx - tickX <= endLabelWidth + 16.dp.toPx()`).
   - **Adjacent Label Spacing Clearance**: The renderer SHALL track the rightmost extent of the previously rendered milestone label (`lastDrawnRightX`). An intermediate label SHALL only be rendered if its left edge maintains at least 12 dp clearance:
     `val labelLeft = tickX - (labelWidth / 2f)`
     `if (labelLeft >= lastDrawnRightX + 12.dp.toPx())` -> draw text and update `lastDrawnRightX = labelLeft + labelWidth`.
   - Overlapping milestone text SHALL be cleanly decimated/skipped.
   - Vertical grid lines across the chart height SHALL continue to be rendered at all valid tick positions to preserve structural visual cadence.

5. **Center-Aligned Label Positioning (`TelemetryMetricGraph.kt`)**:
   - Milestone labels SHALL be precisely centered under their corresponding tick marks:
     `nativeCanvas.drawText(label, tickX - (labelWidth / 2f), size.height - 4.dp.toPx(), axisTextPaint)`.
   - Arbitrary fixed offsets (such as `tickX - 20f`) SHALL be eliminated.

6. **Time Domain Parity (`ProfileXAxisDomain.TIME`)**:
   - The same boundary labeling, margin clearance, and adjacent decimation checks SHALL apply identically when `isTimeDomain == true`.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Adaptive 2 km Step)**:
  * *Given* a workout with total distance 17.82 km ($17,820\text{ m}$),
  * *When* `calculateAdaptiveDistanceStep` is evaluated in Metric mode,
  * *Then* it SHALL return `2_000f` (2,000 meters).
* **Criterion 2 (Milestone Formatting without Redundant Units)**:
  * *Given* a continuous telemetry graph (Heart Rate or Pace) displaying 2 km milestone ticks,
  * *When* intermediate labels are rendered,
  * *Then* labels SHALL render as `"2"`, `"4"`, `"6"`, etc., and SHALL NOT contain trailing `" km"` or `" mi"`.
* **Criterion 3 (Boundary End Label with Units)**:
  * *Given* a 17.82 km workout displayed in `TelemetryMetricGraph`,
  * *When* inspecting the right side of the X-axis,
  * *Then* an explicit end label (`"17,82 km"` in Metric or `"11.07 mi"` in Imperial) SHALL render anchored at the right chart boundary.
* **Criterion 4 (Collision Prevention & Decimation)**:
  * *Given* a continuous telemetry graph rendered on a narrow screen or with dense ticks,
  * *When* two adjacent milestone labels would collide (`spacing < 12.dp`),
  * *Then* the colliding label SHALL be decimated (skipped), ensuring zero overlapping text across the entire X-axis.
* **Criterion 5 (Vertical Alignment with ElevationProfile)**:
  * *Given* detailed inspection in `MapDetailLayout`,
  * *When* viewing Elevation Profile, Speed/Pace, and Heart Rate stacked vertically,
  * *Then* the X-axis milestone ticks and boundary labels across all three charts SHALL align vertically with zero visual collision.

### 1.4 System Invariants
1. **Shared Padding Contract**: Horizontal paddings (50 dp start, 25 dp end) and bottom padding (24 dp) MUST NOT be altered.
2. **Synchronized Scrubbing**: Touch scrubbing coordinate transformation (`canvasXToDistance`) and vertical cursor line placement MUST remain 100% synchronized.
3. **Invertibility of Viewport Math**: All coordinate transformation functions in `ElevationProfileZoomMath` MUST remain strictly invertible.
4. **Clean-Room Regression**: Full test suite pass with 0 regressions.

---

## 2. Test Specification (TST-UI-174)

### Test Case 1: `testCalculateAdaptiveDistanceStep_includesTwoKilometerStep` (`[TST-UI-174.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`
* **Preconditions**: Pure mathematical helper `ElevationProfileZoomMath`.
* **Action**:
  - Evaluate `calculateAdaptiveDistanceStep(17_820.0, MyUnits.METRIC)`.
  - Evaluate `calculateAdaptiveDistanceStep(12_000.0, MyUnits.METRIC)`.
  - Evaluate `calculateAdaptiveDistanceStep(8_000.0, MyUnits.METRIC)`.
  - Evaluate `calculateAdaptiveDistanceStep(25_000.0, MyUnits.METRIC)`.
* **Expected Result**:
  - 17,820m -> `2_000f`.
  - 12,000m -> `2_000f`.
  - 8,000m -> `1_000f` (preserved).
  - 25,000m -> `5_000f` (preserved).

### Test Case 2: `testMilestoneLabelFormatting_omitsRedundantUnit` (`[TST-UI-174.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Preconditions**: `TelemetryMetricUtils` or formatting helper.
* **Action**:
  - Format milestone distance 2000m, 4000m, 10000m for `visibleSpan = 17820m` in Metric.
  - Format milestone distance 3218.68m (2 miles), 6437.37m (4 miles) for Imperial.
* **Expected Result**:
  - Metric returns `"2"`, `"4"`, `"10"`, NOT `"2 km"`, `"4 km"`, `"10 km"`.
  - Imperial returns `"2"`, `"4"`, NOT `"2.0 mi"`, `"4.0 mi"`.

### Test Case 3: `testBoundaryLabels_rendersStartAndEnd` (`[TST-UI-174.3]`)
* **Scope**: Unit Test / Visual Contract
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Preconditions**: Workout with distance 17.82 km (17820 m).
* **Action**:
  - Verify boundary label calculation: end label produces localized distance with unit (e.g. `17,82 km` or `17.82 km`).
  - Verify zoomed start label produces localized distance with unit (`0,00 km` or `5,00 km`).
* **Expected Result**:
  - End label is non-empty, contains unit suffix (`km` or `mi`), and anchors to right edge.

### Test Case 4: `testXAxisMilestoneDecimation_preventsLabelCollisions` (`[TST-UI-174.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Preconditions**: Simulate narrow chart width (300 dp) and dense milestone list (e.g. ticks every 1000 m on 17.82 km track).
* **Action**:
  - Execute decimation logic across candidate ticks.
* **Expected Result**:
  - Returned or rendered labels maintain minimum spacing clearance ($\ge 12\text{ dp}$ between adjacent labels).
  - No labels are drawn within start or end boundary margins.
  - Zero label collisions occur.

### Test Case 5: Clean-Room Full Suite Regression (`[TST-UI-174.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all modules with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-174.1]` | Unit | `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` | `REQ-UI-220` (item 1) | Specified |
| `[TST-UI-174.2]` | Unit | `TelemetryMetricGraph` milestone formatting | `REQ-UI-220` (item 2) | Specified |
| `[TST-UI-174.3]` | Unit | `TelemetryMetricGraph` boundary labels | `REQ-UI-220` (item 3) | Specified |
| `[TST-UI-174.4]` | Unit | `TelemetryMetricGraph` decimation & collision check | `REQ-UI-220` (items 4, 5) | Specified |
| `[TST-UI-174.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
