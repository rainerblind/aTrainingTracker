# Stage 3: Implementation Plan - ATT-5311: TelemetryMetricGraph Marginal Histogram Domain Boundary Clamping

**Ticket**: [ATT-5311](https://rainerblind.atlassian.net/browse/ATT-5311)  
**Sub-task**: [ATT-5322](https://rainerblind.atlassian.net/browse/ATT-5322) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-UI-443.2`, `REQ-UI-443.3`  
**Test Mapping**: `TST-UI-443.2`, `TST-UI-443.3`, `TST-UI-443.REG`  
**Branch**: `feature/ATT-5311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `TelemetryMetricGraph.kt`, marginal histogram drawing loops in portrait (L488) and landscape `AlignedMarginalZoneDistribution` (L944) iterate across all bins in `linearBins` unconditionally. When a workout exhibits power spikes (e.g. up to 800+ W), `linearBins` unrolls bins extending far beyond the graph's plotted Y-axis range `[dataMin, dataMax]` (e.g. 574 W). For out-of-range bins (`bin.rangeMin >= dataMax`), `valueToY(...)` clamps coordinates to the top boundary (`topPaddingPx`), and `.coerceAtLeast(1f)` forces a 1px height. Drawing `bin.color.copy(alpha = 0.15f)` repeatedly at `y = topPaddingPx` for dozens of out-of-range bins stacks into an opaque solid black horizontal bar along the top edge (Rule 70). Symmetrically, bins below `dataMin` stack at the bottom edge.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-443.2` (Portrait Domain Boundary Clamping)** $\leftrightarrow$ `TST-UI-443.2`: Out-of-range bin filtering & clamping in portrait marginal strip.
* **`REQ-UI-443.3` (Landscape Domain Boundary Clamping)** $\leftrightarrow$ `TST-UI-443.3`: Out-of-range bin filtering & clamping in `AlignedMarginalZoneDistribution`.
* **`REQ-PRO-001` (Regression Gate)** $\leftrightarrow$ `TST-UI-443.REG`: Targeted unit and contract tests in `ui.map.*`, `ui.aftermath.zones.*`, `build.*`, and `ThemeColorCentralizationAuditTest`.

---

## 3. System Invariants & Preserved Behavior
1. **In-Domain Track Continuity**: All `linearBins` within `[dataMin, dataMax]` retain unconditional `alpha = 0.15f` container tracks (Rule 69).
2. **Foreground Duration Gating**: Solid foreground frequency bars (`barWidthPx > 0f`) and text overlays remain strictly gated on `bin.durationSec > 0L`.
3. **Standalone Histogram Invariance**: Standalone `TelemetryHistogramChart.kt` sprint-tail folding and drill-down navigation remain 100% invariant (`REQ-UI-419`).
4. **Layout & Gesture Invariance**: 50dp start padding, 72%/28% landscape split, multi-chart scrubbing cursor, and touch gestures remain invariant.

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference components: `TelemetryZoneMath.calculatePowerZoneBands` interval clamping (`maxOf(dataMin, rawMin)` / `minOf(dataMax, rawMax)`).
  * Reused Canvas calls: `drawRect` (portrait), `drawRoundRect` with `CornerRadius(2.dp.toPx())` (landscape).
  * Theme tokens: `bin.color.copy(alpha = 0.15f)` (background track), `bin.color` / `bin.color.copy(alpha = 0.85f)` (foreground bars).
  * New one-off styles: None.
* **Domain Boundary Clamping Guard (Rule 70)**:
  Filter out-of-domain bins with `bin.rangeMax <= dataMin || bin.rangeMin >= dataMax` and clamp `clampedMin = maxOf(dataMin, bin.rangeMin.toDouble())`, `clampedMax = minOf(dataMax, bin.rangeMax.toDouble())`. If `clampedMin >= clampedMax`, skip rendering. Map `clampedMax` and `clampedMin` to coordinates via `valueToY(...)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Portrait Marginal Strip Domain Clamping (`TelemetryMetricGraph.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* In portrait marginal histogram loop (L488), filter out-of-domain bins and clamp `[rangeMin, rangeMax]` to `[dataMin, dataMax]` before calling `valueToY(...)`.

### Step 2: Landscape Marginal Distribution Domain Clamping (`TelemetryMetricGraph.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* In `AlignedMarginalZoneDistribution` loop (L944), mirror portrait domain filtering and clamping to `[dataMin, dataMax]` before calling `valueToY(...)`.

### Step 3: Contract Test Verification (`TelemetryMetricGraphMarginalBarsContractTest.kt` & `TelemetryMetricGraphLandscapeContractTest.kt`)
* Files: `TelemetryMetricGraphMarginalBarsContractTest.kt`, `TelemetryMetricGraphLandscapeContractTest.kt`
* Add contract test methods asserting that out-of-domain bins (`rangeMin >= dataMax` or `rangeMax <= dataMin`) are filtered and do not produce stacked 1px rects at boundary coordinates (`y = 0` / `topPaddingPx`).

### Step 4: Verification & Regression Tests (`TST-UI-443.REG`)
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests in Stage 4, followed by clean-room suite in Stage 5.
* **Rollback**: Clean git revert of `feature/ATT-5311`; no database migrations or persistent state alterations.
