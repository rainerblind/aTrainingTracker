# Stage 3: Implementation Plan - ATT-1819: Fix unreadable x-Axis milestone collisions and text overlapping on HR and Pace graphs

**Ticket**: [ATT-1819](https://rainerblind.atlassian.net/browse/ATT-1819)  
**Sub-task**: [ATT-1887](https://rainerblind.atlassian.net/browse/ATT-1887) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-220` (*Aftermath Continuous Telemetry Graphs: Adaptive X-Axis Milestone Decimation, Collision Prevention, and Standardized Boundary Labeling*)  
**Test Mapping**: `TST-UI-174`  
**Branch**: `feature/ATT-1819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In the post-workout inspection screen (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`), secondary continuous graphs (Speed/Pace, Heart Rate, and Power) plot telemetry curves with distance milestones or elapsed time along the bottom X-axis. On medium-to-long activities (such as 17.82 km run *"Mit Jonas durch den Wald"*), every kilometer marker (`0 km` to `17 km`) is rendered unconditionally without clearance checks.

Because every milestone label repeats the unit suffix (`" km"` or `" mi"`), the width of each label (~35–45 dp) significantly exceeds the spacing between ticks (~17 dp), bleeding all labels into an unreadable solid text mash (`0 km1 km2 km...17 km`). In addition, secondary continuous graphs lack start and end boundary labels (unlike `ElevationProfile`, which cleanly anchors `17,82 km`), drawing `0 km` directly over the Y-axis tick mark.

This plan details the atomic implementation steps to refine the adaptive step progression in `ElevationProfileZoomMath.kt`, standardize milestone text formatting, anchor start/end boundary labels, and implement dynamic minimum pixel clearance and decimation in `TelemetryMetricGraph.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-220` (*Aftermath Continuous Telemetry Graphs: Adaptive X-Axis Milestone Decimation, Collision Prevention, and Standardized Boundary Labeling*)
* **Test Mapping**: `TST-UI-174`
  * `[TST-UI-174.1]`: Adaptive 2 km step progression in `ElevationProfileZoomMathTest.kt`
  * `[TST-UI-174.2]`: Milestone formatting without redundant units in `TelemetryMetricGraphTest.kt`
  * `[TST-UI-174.3]`: Boundary start and end labels with units in `TelemetryMetricGraphTest.kt`
  * `[TST-UI-174.4]`: Milestone decimation and collision prevention in `TelemetryMetricGraphTest.kt`
  * `[TST-UI-174.5]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Shared Padding Contract**: Horizontal paddings (50 dp start, 25 dp end) and bottom padding (24 dp) MUST NOT change, preserving pixel-perfect vertical alignment with `ElevationProfile`.
2. **Synchronized Multi-Chart Scrubbing**: Touch scrubbing coordinate transformation (`canvasXToDistance`) and vertical cursor line placement MUST remain 100% synchronized across Map, Elevation Profile, and Telemetry graphs.
3. **Pure Math Invertibility**: All coordinate mapping and clamping functions in `ElevationProfileZoomMath.kt` MUST remain pure, side-effect-free, and invertible.
4. **Subtask Self-Sufficiency**: Subtask `ATT-1887` transitions directly to `Erledigt` upon Gate 3 approval via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-1819` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationProfileZoomMath.kt` (Pure Mathematical Engine)
* Refine `calculateAdaptiveDistanceStep`:
  ```kotlin
  fun calculateAdaptiveDistanceStep(visibleDist: Double, unit: MyUnits): Float {
      return if (unit == MyUnits.METRIC) {
          when {
              visibleDist > 50_000 -> 10_000f
              visibleDist > 20_000 -> 5_000f
              visibleDist > 10_000 -> 2_000f  // 2km step for 10-20km workouts
              visibleDist > 5_000 -> 1_000f
              visibleDist > 1_500 -> 500f
              visibleDist > 500 -> 100f
              else -> 50f
          }
      } else {
          // Imperial already uses 2mi step: > 30 -> 5f, > 10 -> 2f, > 3 -> 1f
          ...
      }
  }
  ```

### Component 2: `TelemetryMetricGraph.kt` (Presentation & Rendering Engine)
1. **Instantiate Distance Formatter**:
   `val distanceFormatter = remember(unit) { DistanceFormatter() }`
2. **Boundary Start and End Labels**:
   - Compute `endLabel`:
     - Distance domain: `distanceFormatter.format_with_units(startDist + visibleSpan)` (e.g. `"17,82 km"`).
     - Time domain: `ElevationProfileZoomMath.formatTimeTick((startDist + visibleSpan).toLong())`.
   - Measure width: `val endLabelWidth = axisTextPaint.measureText(endLabel)`.
   - Draw `endLabel` at `startPaddingPx + chartWidthPx - endLabelWidth`.
   - When zoomed ($Z > 1.01$), compute `startLabel` (`distanceFormatter.format_with_units(startDist)` or `formatTimeTick(startDist.toLong())`) and draw at `startPaddingPx`.
3. **Standardized Milestone Formatting**:
   - In Distance domain:
     - Metric: if `visibleSpan < 1500`, `"${currentDist.toInt()}m"`; else if `currentDist % 1000.0 != 0.0`, `String.format(Locale.US, "%.1f", currentDist / 1000.0)`; else `"${(currentDist / 1000.0).toInt()}"`.
     - Imperial: if `miles % 1.0 != 0.0`, `String.format(Locale.US, "%.1f", miles)`; else `"${miles.toInt()}"`.
   - In Time domain: `ElevationProfileZoomMath.formatTimeTick(currentSec.toLong())`.
4. **Collision Avoidance & Decimation Algorithm**:
   - Calculate `tickX = startPaddingPx + ElevationProfileZoomMath.distanceToCanvasX(...)`.
   - Draw vertical grid line across all ticks:
     `drawLine(color = outlineVariant.copy(alpha = 0.3f), start = Offset(tickX, topPaddingPx), end = Offset(tickX, topPaddingPx + chartHeightPx))`
   - Check boundary margin clearance:
     `val startClearance = tickX > (startPaddingPx + 40.dp.toPx())`
     `val endClearance = (startPaddingPx + chartWidthPx - tickX) > (endLabelWidth + 16.dp.toPx())`
   - Check adjacent label clearance:
     `val labelWidth = axisTextPaint.measureText(label)`
     `val labelLeft = tickX - (labelWidth / 2f)`
     `if (startClearance && endClearance && labelLeft >= lastDrawnRightX + 12.dp.toPx())` -> draw text centered at `labelLeft` and update `lastDrawnRightX = labelLeft + labelWidth`.
     Else: suppress text (decimate).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `ElevationProfileZoomMath.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMath.kt`
* **Changes**: Add `visibleDist > 10_000 -> 2_000f` in `calculateAdaptiveDistanceStep`.

### Step 2: Add Mathematical Unit Tests in `ElevationProfileZoomMathTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileZoomMathTest.kt`
* **Changes**:
  - Add test asserting `calculateAdaptiveDistanceStep(17_820.0, MyUnits.METRIC)` returns `2_000f`.
  - Add test asserting `calculateAdaptiveDistanceStep(12_000.0, MyUnits.METRIC)` returns `2_000f`.
  - Verify existing tests at 8,000m (1,000f), 25,000m (5,000f) remain passing.

### Step 3: Implement Boundary Labels & Decimation in `TelemetryMetricGraph.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  - Add `DistanceFormatter` instantiation.
  - Draw start and end boundary labels with units.
  - Omit redundant unit suffix on intermediate milestone labels.
  - Add boundary margin clearance and adjacent label decimation tracking `lastDrawnRightX`.
  - Center-align milestone text labels.

### Step 4: Author Unit Tests in `TelemetryMetricGraphTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Changes**:
  - Add `testMilestoneLabelFormatting_omitsRedundantUnit`.
  - Add `testBoundaryLabels_rendersStartAndEnd`.
  - Add `testXAxisMilestoneDecimation_preventsLabelCollisions`.

### Step 5: Execute Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*ElevationProfileZoomMathTest*" --tests "*TelemetryMetricGraphTest*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`ElevationProfileZoomMathTest`, `TelemetryMetricGraphTest`) will validate the 2 km step, label formatting, boundary labels, and decimation logic. Full clean-room test suite (`./gradlew testDebugUnitTest`) will confirm zero regressions across all modules in Stage 5.
* **Rollback Plan**: All changes are committed to isolated branch `feature/ATT-1819`. Reverting any commit or checking out `sprint/2026-40.7` cleanly restores pre-modification behavior.
