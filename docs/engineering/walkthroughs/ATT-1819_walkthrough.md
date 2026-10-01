# Stage 5: Walkthrough & Verification - ATT-1819: Fix unreadable x-Axis milestone collisions and text overlapping on HR and Pace graphs

**Ticket**: [ATT-1819](https://rainerblind.atlassian.net/browse/ATT-1819)  
**Sub-task**: [ATT-1889](https://rainerblind.atlassian.net/browse/ATT-1889) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-220` (*Aftermath/Graphs: Adaptive X-Axis Milestone Decimation, Collision Prevention, and Standardized Boundary Labeling*)  
**Test Mapping**: `TST-UI-174`  
**Branch**: `feature/ATT-1819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1819 resolves unreadable X-axis milestone collisions, overlapping labels, and missing boundary indicators on continuous telemetry graphs (`TelemetryMetricGraph.kt` and `ElevationProfileZoomMath.kt`):

1. **Adaptive 2 km Intermediate Step Progression**:
   - In `ElevationProfileZoomMath.calculateAdaptiveDistanceStep`, when `unit == MyUnits.METRIC`, workouts with visible distances between 10 km and 20 km previously skipped straight from 5 km to 1 km ticks. For a 17.82 km run (*'Mit Jonas durch den Wald'*), this generated 17 milestone markers crowded across a ~300 dp canvas (~17.6 dp per tick).
   - Added intermediate step: `visibleDist > 10_000 -> 2_000f`. Workouts in `(10_000, 20_000]` meters now generate clean 2 km tick intervals (e.g. 2, 4, 6, 8, 10, 12, 14, 16), reducing tick count to ~8 milestones.
2. **Standardized Milestone Label Formatting Without Redundant Units**:
   - In `TelemetryMetricGraph.kt`, intermediate milestone labels previously repeated `" km"` or `" mi"` on every single tick (e.g. `"1 km"`, `"2 km"`), expanding label widths to 35–45 dp and guaranteeing overlaps.
   - Introduced `TelemetryMetricUtils.formatMilestoneLabel(...)`: intermediate milestones now format compactly without units (`"2"`, `"4"`, `"10"`; or with decimal `"2.5"` when fractional). Sub-kilometer spans (<1500m) render as meters (e.g. `"500m"`).
3. **Start and End Boundary Labels Parity**:
   - `TelemetryMetricGraph` instantiates `DistanceFormatter` and renders an explicit `endLabel` with units anchored at the right chart boundary (`startPaddingPx + chartWidthPx - endLabelWidth`): e.g. `"17,82 km"` or `"11.07 mi"` (or `ElevationProfileZoomMath.formatTimeTick` in Time domain).
   - When zoomed ($Z > 1.01$), an explicit `startLabel` with units is rendered at `startPaddingPx`.
4. **Dynamic Minimum Pixel Clearance & Decimation Algorithm**:
   - Implemented `TelemetryMetricUtils.shouldRenderMilestoneLabel(...)`:
     - Start boundary clearance: Suppresses labels within 40 dp of `startPaddingPx`.
     - End boundary clearance: Suppresses labels within `endLabelWidth + 16 dp` of the right chart edge.
     - Dynamic adjacent clearance: Tracks `lastDrawnRightX` across sequential ticks and enforces a minimum clearance gap of $\ge 12\text{ dp}$ (`labelLeft >= lastDrawnRightX + 12.dp`).
     - Colliding milestone labels are cleanly decimated (skipped) while underlying vertical grid lines continue to render.
5. **Center-Aligned Label Positioning**:
   - Milestone labels are now centered horizontally under their tick marks (`tickX - (labelWidth / 2f)`), completely replacing legacy off-center `tickX - 20f` offsets.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-220` (item 1) | `[TST-UI-174.1]` | Unit Test (`ElevationProfileZoomMathTest.testCalculateAdaptiveDistanceStep_intermediate2kmStepFor10to20km`) | **PASSED** | `Verified` |
| `REQ-UI-220` (item 2) | `[TST-UI-174.2]` | Unit Test (`TelemetryMetricGraphTest.testMilestoneLabelFormatting_omitsRedundantUnit`) | **PASSED** | `Verified` |
| `REQ-UI-220` (item 3) | `[TST-UI-174.3]` | Unit Test (`TelemetryMetricGraphTest.testBoundaryLabels_rendersStartAndEnd`) | **PASSED** | `Verified` |
| `REQ-UI-220` (item 4, 5) | `[TST-UI-174.4]` | Unit Test (`TelemetryMetricGraphTest.testXAxisMilestoneDecimation_preventsLabelCollisions`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-174.5]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit Tests
```text
./gradlew testDebugUnitTest --tests "*ElevationProfileZoomMathTest*" --tests "*TelemetryMetricGraphTest*"
BUILD SUCCESSFUL in 8s
32 actionable tasks: 5 executed, 27 up-to-date
```
- `ElevationProfileZoomMathTest.testCalculateAdaptiveDistanceStep_intermediate2kmStepFor10to20km`: PASSED
- `ElevationProfileZoomMathTest.testCalculateAdaptiveDistanceStep`: PASSED
- `TelemetryMetricGraphTest.testMilestoneLabelFormatting_omitsRedundantUnit`: PASSED
- `TelemetryMetricGraphTest.testBoundaryLabels_rendersStartAndEnd`: PASSED
- `TelemetryMetricGraphTest.testXAxisMilestoneDecimation_preventsLabelCollisions`: PASSED
- `TelemetryMetricGraphTest.testFormatValue_paceFormattingMetricAndImperial`: PASSED
- `TelemetryMetricGraphTest.testFormatPaceMinutes_standardAndEdgeCases`: PASSED
- `TelemetryMetricGraphTest.testExtractMetricValue_paceStoppedThreshold`: PASSED
- `TelemetryMetricGraphTest.testTelemetryMetricGraph_paceYAxisLabelsAreMmSs`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
- Full test suite across all modules: 100% pass rate, 0 failures, 0 regressions.

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Long Workout (17.82 km 'Mit Jonas durch den Wald')**:
  1. Open the workout in detailed inspection (`TrackOnMapScreen` / `MapDetailLayout`).
  2. Inspect the X-axis of the Heart Rate, Speed, and Pace graphs.
  3. Verify that intermediate milestone labels appear at clean intervals (`2`, `4`, `6`, `8`, `10`, `12`, `14`, `16`) without the text `" km"`.
  4. Verify that the boundary label on the right explicitly displays `"17,82 km"`.
  5. Verify that no text labels collide, touch, or overlap each other.
* **Zoom Interaction**:
  1. Pinch-to-zoom into a segment of the graph.
  2. Verify that `startLabel` appears at the left chart edge indicating the zoomed start distance with units.
  3. Verify that intermediate milestones dynamically adapt their step and continue to respect the 12 dp clearance gap without collisions.
* **Short Workout (< 2 km)**:
  1. Open a short workout (e.g. 1.2 km).
  2. Verify milestone ticks render in meters (e.g. `"200m"`, `"400m"`) or clean fractional numbers.

---

## 5. Invariant & Governance Verification

1. **Shared Padding Geometry**: 50.dp start padding and 25.dp end padding remain uniform across `ElevationProfile` and `TelemetryMetricGraph`.
2. **Synchronized Scrubbing**: Scrubbing coordinate mapping (`canvasXToDistance`, `selectedDistance`) and vertical indicator lines are perfectly preserved.
3. **9-Language Parity**: Distance units and boundary labels utilize standard `DistanceFormatter`, preserving localization across all 9 supported locales.
4. **Clean-Room Regression**: Full test suite passes 100% cleanly without regressions.
