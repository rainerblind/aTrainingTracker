# Stage 1 Analysis: ATT-5190 - [Bug] Power TelemetryMetricGraph marginal histogram: render unfolded 10 W bins instead of giant black Sprint-Tail overflowBin

**Ticket**: [ATT-5190](https://rainerblind.atlassian.net/browse/ATT-5190)  
**Parent Epic**: Standalone  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5190`  
**Author**: AI Agent 1 (pro / Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
- In `TelemetryMetricGraph.kt` (Workout Details / Map & Graphs screen), when `ZoneCardDisplayMode.HISTOGRAM` is active for Cycling Power (`Leistung`), a massive solid black vertical block (`TTColor.Zone7`) spans from `tailCutoff` (e.g. 375 W) to `dataMax` (e.g. 574 W) on the right marginal strip.
- The marginal histogram shares a continuous 1:1 vertical Y-axis with the power time-series curve. Rendering a folded overflow bin creates an unsightly block obscuring high-wattage zone inspection.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Root Cause**:
  1. *Sprint-Tail Folding in `TelemetryHistogramCalculator.kt:133-182`*: For standalone X-axis column histograms (`TelemetryHistogramChart.kt`), all bins above `tailCutoff` fold into a single `overflowBin` spanning `[tailCutoff, dataMax]` with `durationSec = tailDuration` and `color = TTColor.Zone7`.
  2. *Continuous Y-Axis Projection Mismatch in `TelemetryMetricGraph.kt` (L482-L510 & L938-L976)*: Marginal rendering iterates over `histogram.bins`. The vertical rect height is calculated as `abs(valueToY(rangeMax) - valueToY(rangeMin))`. For `overflowBin`, this stretches across the entire 150–200 W tail.
  3. *Empty Bin Background Clutter*: Drawing container tracks (`alpha = 0.15f`) for empty 0-duration bins (`bin.durationSec == 0L`) in Zone 7 adds dark visual noise.
- **Independent Peer Review & Verification**:
  - Audited `TelemetryHistogramCalculator.kt`: Confirmed `sprintTail.bins.filterNot { it.isReturn }` contains the unfolded individual 10 W bins from `tailCutoff` to `dataMax`.
  - Audited `TelemetryMetricGraph.kt:482-510` (portrait) & `L938-L976` (landscape): Confirmed both iterate over `histogram.bins` instead of continuous linear bins.

---

## 3. Chesterton's Fence Archaeology & Side-Effect Impact Audit (Rule 68)
- **Original Requirement ID & Target**: Amends `REQ-UI-251` (*Marginal Strip in Portrait*) and `REQ-UI-431` (*Marginal Pane in Landscape*); complements `REQ-UI-419` (*Sprint Tail Folding*) and `REQ-UI-420` (*Coggan 7-Zone Parity*). Targets `ZoneDistributionModels.kt` and `TelemetryMetricGraph.kt`.
- **Historical Origin & Commit Trace**: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`), commit `752d580f` (`ATT-4293`, `REQ-UI-419`), and commit `dcb8f15f` (`ATT-2145`, `REQ-UI-431`).
- **Root Reason for Existing Formulation**: Tail folding was designed exclusively for X-axis column charts to save horizontal space. Its side effect on Y-axis docked marginal strips was overlooked.
- **Preservation of Core Invariants**: Standalone `TelemetryHistogramChart.kt` folding and interactive sprint-tail drill-down remain 100% untouched. Heart rate histograms remain unaffected.
- **Side-Effect & Cross-Caller Impact Analysis (Rule 68)**: Exposing `linearBins` on `TelemetryHistogramData` is purely additive. Only `TelemetryMetricGraph.kt` portrait and landscape marginal rendering switch to `linearBins`. Zero impact on database, sensors, or background services.

---

## 4. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Add `linearBins: List<TelemetryHistogramBin>` helper to `TelemetryHistogramData` in `ZoneDistributionModels.kt`.
  2. In `TelemetryMetricGraph.kt` (portrait and landscape), iterate over `histogram.linearBins` and calculate `maxBinPct` from `linearBins`.
  3. Skip background container tracks (`alpha = 0.15f`) for 0-duration bins (`bin.durationSec == 0L`) in `TelemetryMetricGraph.kt`.
  4. Extend unit and contract tests in `TelemetryMetricGraphMarginalBarsContractTest.kt` and `TelemetryMetricGraphLandscapeContractTest.kt`.
- **Out-of-Scope Non-Goals**:
  - Modifying `calculateTailCutoff` or folding thresholds in `TelemetryHistogramCalculator.kt`.
  - Modifying `TelemetryHistogramChart.kt` interaction or layout.

---

## 5. Architectural Strategy & High-Level Solution
- **`ZoneDistributionModels.kt`**: Add `val linearBins: List<TelemetryHistogramBin> get() = if (sprintTail == null) bins else bins.filterNot { it.isOverflow } + sprintTail.bins.filterNot { it.isReturn }`.
- **`TelemetryMetricGraph.kt`**:
  - In portrait (L482-L510) and landscape (L938-L976), iterate over `histogram.linearBins` and calculate `maxBinPct` across `histogram.linearBins`.
  - Guard background track and frequency bar drawing with `if (bin.durationSec > 0L)`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**: Standalone Zones tab histogram retains interactive folding; multi-chart horizontal alignment (50 dp start padding, 72%/28% landscape split) preserved byte-for-byte.
- **Risk Rating**: **LOW**. Additive property and localized Compose Canvas drawing logic in `TelemetryMetricGraph.kt`. Zero impact on database or background tracking.
