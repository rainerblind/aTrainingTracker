# Stage 3: Implementation Plan - ATT-5190: Power TelemetryMetricGraph Marginal Histogram Unfolded Linear Bins Projection

**Ticket**: [[ATT-5190]](https://rainerblind.atlassian.net/browse/ATT-5190)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: Standalone  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-443` (`REQ-UI-443.1`, `REQ-UI-443.2`, `REQ-UI-443.3`, `REQ-UI-443.4`)  
**Test Mapping**: `TST-UI-443` (`TST-UI-443.1`, `TST-UI-443.2`, `TST-UI-443.3`, `TST-UI-443.4`)  
**Branch**: `feature/ATT-5190`  
**Author**: AI Agent 1 (pro / Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
In `TelemetryMetricGraph.kt` (portrait marginal strip & landscape `AlignedMarginalZoneDistribution`), when `ZoneCardDisplayMode.HISTOGRAM` is active for Cycling Power, a massive solid black block (`TTColor.Zone7`) spans from `tailCutoff` to `dataMax`. Standalone column histograms on the *Zones* tab (`TelemetryHistogramChart.kt`) fold high-power spikes into an `overflowBin`, but the marginal histogram shares a continuous 1:1 vertical Y-axis with the time-series curve. The single overflow bin is stretched across 150–200 W, obscuring high-wattage zone inspection.

---

## 2. Traceability & Requirements Mapping
* **REQ-UI-443.1** (*Unfolded Linear Bins Helper*) $\rightarrow$ `TST-UI-443.1` in `TelemetryHistogramCalculatorTest.kt`
* **REQ-UI-443.2** (*Portrait Marginal Strip Unfolded Projection*) $\rightarrow$ `TST-UI-443.2` in `TelemetryMetricGraphMarginalBarsContractTest.kt`
* **REQ-UI-443.3** (*Landscape Marginal Pane Unfolded Projection*) $\rightarrow$ `TST-UI-443.3` in `TelemetryMetricGraphLandscapeContractTest.kt`
* **REQ-UI-443.4** (*Preservation of Standalone Folding & Non-Regression*) $\rightarrow$ `TST-UI-443.4` (Targeted suite)

---

## 3. System Invariants & Preserved Behavior
1. **Standalone Sprint-Tail Invariant**: Standalone `TelemetryHistogramChart.kt` folding, clickable overflow bar, and drill-down navigation remain 100% untouched.
2. **Heart Rate Parity**: Heart rate marginal histograms and calculations remain unaffected.
3. **Multi-Chart Alignment**: Start padding (50 dp) and landscape dual-pane ratio (72% chart / 28% marginal pane) remain byte-for-byte preserved.
4. **Coding Standards Compliance**:
   - *Section 5.4*: Tests use programmatic reflection and behavioral mathematical modeling; no UI source-text grep.
   - *Section 1.10*: No Compose `MutableState` snapshot writes during composition; Canvas drawing reads pure immutable inputs.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Domain Model Layer (`ZoneDistributionModels.kt`)**:
  Add immutable computed property `linearBins: List<TelemetryHistogramBin>` to `TelemetryHistogramData`:
  `get() = if (sprintTail == null) bins else bins.filterNot { it.isOverflow } + sprintTail.bins.filterNot { it.isReturn }`.
* **Presentation Layer (`TelemetryMetricGraph.kt`)**:
  - *Portrait Strip (L482-L510)*: Iterate over `histogram.linearBins` instead of `histogram.bins`. Compute `maxBinPct` across `linearBins`. Guard container track and frequency bar with `if (bin.durationSec > 0L)`.
  - *Landscape Pane (L938-L976)*: Iterate over `histogram.linearBins`. Compute `maxBinPct` across `linearBins`. Guard container track and bar with `if (bin.durationSec > 0L)`. Text overlay retains existing `rectHeight >= 12.dp.toPx() && bin.durationSec > 0L`.
* **Test Layer**:
  - `TelemetryHistogramCalculatorTest.kt`: Unit tests verifying `linearBins` sequence, continuity, and absence of `overflowBin`/`returnBin`.
  - `TelemetryMetricGraphMarginalBarsContractTest.kt` & `TelemetryMetricGraphLandscapeContractTest.kt`: Behavioral math and reflection contract tests verifying 1:1 Y-projection and empty bin suppression.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Domain Extension (`ZoneDistributionModels.kt`)**:
   Add `val linearBins: List<TelemetryHistogramBin>` property.
2. **Step 2: Portrait & Landscape Canvas Updates (`TelemetryMetricGraph.kt`)**:
   Switch marginal histogram loops to `histogram.linearBins` and gate tracks on `bin.durationSec > 0L`.
3. **Step 3: Unit & Contract Tests**:
   Implement `TST-UI-443.1`, `TST-UI-443.2`, and `TST-UI-443.3` in test suites without UI source text grep.
4. **Step 4: Regression & Governance Check**:
   Execute targeted unit tests and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`com.atrainingtracker.trainingtracker.ui.map.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.zones.*`, `ThemeColorCentralizationAuditTest`).
* **Rollback**: Isolated branch `feature/ATT-5190`; can be reverted cleanly prior to squash merge.
