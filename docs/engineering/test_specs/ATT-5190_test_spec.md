# Stage 2: Requirement & Test Specification - ATT-5190: Power TelemetryMetricGraph marginal histogram: render unfolded 10 W bins instead of giant black Sprint-Tail overflowBin

**Ticket**: [ATT-5190](https://rainerblind.atlassian.net/browse/ATT-5190)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: Standalone  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-443` (*Power TelemetryMetricGraph Marginal Histogram Unfolded Linear Bins Projection Parity in Portrait and Landscape Modes*)  
**Test Spec ID**: `TST-UI-443`  
**Branch**: `feature/ATT-5190`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-443)

### 1.1 Problem Statement & Rationale
In `TelemetryMetricGraph.kt`, when `ZoneCardDisplayMode.HISTOGRAM` is active for Cycling Power, a massive solid black block (`TTColor.Zone7`) renders in the right marginal histogram across `[tailCutoff, dataMax]`. Standalone column histograms on the *Zones* tab (`TelemetryHistogramChart.kt`) fold rare sprint spikes into an `overflowBin`, but marginal histograms share a continuous 1:1 vertical Y-axis with the time-series curve. The single overflow bin is stretched across 150–200 W, obscuring high-wattage zone inspection.

### 1.2 Functional & Architectural Requirements
1. **Unfolded Linear Bins Domain Helper (`REQ-UI-443.1`)**: In `ZoneDistributionModels.kt`, `TelemetryHistogramData` SHALL expose `val linearBins: List<TelemetryHistogramBin>` returning `bins` when `sprintTail == null`, and `bins.filterNot { it.isOverflow } + sprintTail.bins.filterNot { it.isReturn }` when `sprintTail != null`.
2. **Portrait Marginal Strip Unfolded Projection (`REQ-UI-443.2`)**: In portrait mode (`TelemetryMetricGraph.kt:482-510`), when `zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM`, the 25dp marginal strip SHALL iterate over `histogram.linearBins`, scale `maxBinPct` across `linearBins`, and draw container background tracks (`alpha = 0.15f`) only when `bin.durationSec > 0L`.
3. **Landscape Aligned Marginal Pane Unfolded Projection (`REQ-UI-443.3`)**: In landscape mode (`TelemetryMetricGraph.kt:938-976`, `AlignedMarginalZoneDistribution`), the pane SHALL iterate over `histogram.linearBins`, scale `maxBinPct` across `linearBins`, and draw container tracks only when `bin.durationSec > 0L`. Text overlays (`${bin.percentage.toInt()}%`) SHALL be rendered only when `rectHeight >= 12.dp.toPx() && bin.durationSec > 0L`.
4. **Preservation of Standalone Sprint-Tail Folding (`REQ-UI-443.4`)**: Standalone `TelemetryHistogramChart.kt`, `TelemetryHistogramCalculator.calculatePowerHistogram` folding logic, interactive overflow navigation, multi-chart scrubbing alignment, and 50dp start padding SHALL remain 100% invariant.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-251` (*Marginal Strip in Portrait*) and `REQ-UI-431` (*Marginal Pane in Landscape*); complements `REQ-UI-419` (*Sprint Tail Folding*) and `REQ-UI-420` (*Coggan 7-Zone Parity*). Targets `ZoneDistributionModels.kt` and `TelemetryMetricGraph.kt`.
* **Historical Origin & Commit Trace**: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`), commit `752d580f` (`ATT-4293`, `REQ-UI-419`), and commit `dcb8f15f` (`ATT-2145`, `REQ-UI-431`).
* **Root Reason for Existing Formulation**: Sprint-tail folding was designed for standalone equal-column horizontal X-axis charts to conserve width. Its side effect on continuous vertical 1:1 Y-axis marginal strips was overlooked.
* **Preservation of Core Invariants**: Standalone `TelemetryHistogramChart.kt` folding and header toggle navigation remain 100% intact. Multi-chart horizontal alignment (50dp start padding, 72%/28% landscape split) is preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Unfolded Marginal Bins)**: *Given* a workout with sprint-tail folding active and `ZoneCardDisplayMode.HISTOGRAM`, *When* viewing `TelemetryMetricGraph` (portrait and landscape), *Then* the marginal histogram renders individual 10 W bins at their true Y-axis watt positions up to `dataMax` without a giant solid black overflow block.
* **Criterion 2 (Clean Empty Tail Space)**: *Given* high-wattage power bins with `durationSec == 0L`, *When* rendered, *Then* no background container track (`alpha = 0.15f`) is drawn.
* **Criterion 3 (Standalone Invariant)**: *Given* the same workout on the *Zones* tab (`TelemetryHistogramChart`), *When* inspected, *Then* the interactive sprint-tail overflow bar and drill-down navigation operate unchanged.

---

## 2. Test Specification (TST-UI-443)

* **TST-UI-443.1 (TelemetryHistogramData.linearBins Unit Tests)**: In `TelemetryHistogramCalculatorTest.kt`, verify `linearBins` returns `bins` when `sprintTail == null`, and returns unfolded 10 W bins without `isOverflow` or `isReturn` spanning `[dataMin, dataMax]` when `sprintTail != null`.
* **TST-UI-443.2 (Portrait Marginal Strip Contract Test)**: In `TelemetryMetricGraphMarginalBarsContractTest.kt`, assert `TelemetryMetricGraph.kt` portrait histogram logic iterates over `linearBins` and conditions container track on `bin.durationSec > 0L`.
* **TST-UI-443.3 (Landscape Aligned Marginal Distribution Contract Test)**: In `TelemetryMetricGraphLandscapeContractTest.kt`, assert `AlignedMarginalZoneDistribution` iterates over `linearBins` and conditions container track on `bin.durationSec > 0L`.
* **TST-UI-443.4 (Targeted Regression Suite)**: Run targeted package tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-443.1]` | Unit | `TelemetryHistogramCalculatorTest.testLinearBins_unfoldsSprintTailSequenceCorrectly` | `REQ-UI-443.1` | Specified |
| `[TST-UI-443.2]` | Contract | `TelemetryMetricGraphMarginalBarsContractTest.testPortraitMarginalHistogram_usesLinearBinsAndGuardsEmptyTracks` | `REQ-UI-443.2` | Specified |
| `[TST-UI-443.3]` | Contract | `TelemetryMetricGraphLandscapeContractTest.testLandscapeMarginalHistogram_usesLinearBinsAndGuardsEmptyTracks` | `REQ-UI-443.3` | Specified |
| `[TST-UI-443.4]` | Regression | Full targeted aftermath & map unit suite | `REQ-UI-443.4`, `REQ-PRO-001` | Specified |
