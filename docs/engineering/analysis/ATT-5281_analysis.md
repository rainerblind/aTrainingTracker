# Stage 1 Analysis: ATT-5281 - Unconditional Histogram Background Track Continuity

**Ticket**: [ATT-5281](https://rainerblind.atlassian.net/browse/ATT-5281)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Branch**: `feature/ATT-5281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.2 review of `ATT-5190` (*Leistung* chart in *Histogramm* mode), white horizontal stripes were observed across the gray/black Zone 7 background track of the right-hand marginal histogram in `TelemetryMetricGraph.kt`.
In high-wattage sprint ranges (e.g. 375 W .. 574 W), power samples land in non-contiguous 10 W bins (e.g. 450 W, 490 W, 570 W) while intermediate bins have `durationSec == 0L`. Because the `alpha = 0.15f` background track was gated behind `if (bin.durationSec > 0L)`, uncolored white surface gaps appear between active bins, clashing with the continuous Zone 7 background band on the main chart to its left.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  * In `TelemetryMetricGraph.kt#L495` (portrait) and `#L953` (landscape `AlignedMarginalZoneDistribution`), the `alpha = 0.15f` background track is gated behind `if (bin.durationSec > 0L)`.
  * For sparse high-power sprint intervals, empty intermediate bins skip background rendering, breaking visual continuity.
* **Independent Peer Review & Verification**:
  * Confirmed in `TelemetryMetricGraph.kt#L494-L501` (portrait) & `#L952-L960` (landscape): both sites gate `drawRect` / `drawRoundRect` background tracks behind `if (bin.durationSec > 0L)`.
  * Confirmed in `docs/design_guidelines.md` §2.7 (Rule 69): Explicitly codifies that marginal histograms MUST draw the `alpha = 0.15f` background container track unconditionally for every `linearBin` across `[dataMin, dataMax]`, gating only the solid foreground bar on `if (bin.durationSec > 0L && barWidth > 0f)`.
  * Confirmed in `TelemetryMetricGraphLandscapeContractTest.kt#L262-L266`: Contains legacy assertion assuming empty bin track suppression; must be updated for unconditional continuity.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Draw the `bin.color.copy(alpha = 0.15f)` background track unconditionally for all `linearBins` in `TelemetryMetricGraph.kt` portrait mode (L495).
  * Draw the `bin.color.copy(alpha = 0.15f)` background track unconditionally for all `linearBins` in landscape `AlignedMarginalZoneDistribution` (L953).
  * Retain conditional gating on `bin.durationSec > 0L` for solid foreground frequency bars and text overlays.
  * Update `TelemetryMetricGraphLandscapeContractTest.kt` to enforce unconditional background track continuity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to `ZoneDistributionModels.kt` or `linearBins` resolution logic.
  * No alterations to standalone `TelemetryHistogramChart.kt` (Sprint-Tail folding remains invariant per `REQ-UI-419`).
  * No changes to 5-zones mode (`FIVE_ZONES`, `COLUMNS`, `STACKED_BAR`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-443.2` (Portrait) & `REQ-UI-443.3` (Landscape), amending `REQ-UI-251` & `REQ-UI-431`. Targets `TelemetryMetricGraph.kt`.
* **Historical Origin & Commit Trace**: Commit `9d44d0f5` (Sprint 2026-45.2, `ATT-5190`).
* **Root Reason for Existing Formulation**: `ATT-5190` added `if (bin.durationSec > 0L)` to suppress background tracks on empty bins to avoid visual clutter. However, this produced disruptive horizontal white stripes across non-contiguous Zone 7 power bursts.
* **Preservation of Core Invariants**: 1:1 Y-axis watt mapping, multi-chart plot alignment, and standalone histogram folding on the Zones tab are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Portrait Track Continuity (`TelemetryMetricGraph.kt#L494-L501`)**: Remove `if (bin.durationSec > 0L)` wrapping `drawRect(color = bin.color.copy(alpha = 0.15f), ...)`.
2. **Landscape Track Continuity (`TelemetryMetricGraph.kt#L952-L960`)**: Remove `if (bin.durationSec > 0L)` wrapping `drawRoundRect(color = bin.color.copy(alpha = 0.15f), ...)`.
3. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
   - Foreground bar (`barWidthPx > 0f`) and text percentage overlay remain conditional on `durationSec > 0L`.
   - Update contract assertion in `TelemetryMetricGraphLandscapeContractTest.kt` to verify unconditional track rendering.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Continuous background container track across all `linearBins` spanning `[dataMin, dataMax]`.
  2. Foreground frequency bars and percentage text rendered exclusively for active bins (`durationSec > 0L`).
  3. Zero regressions in multi-chart scrubbing or standalone histogram folding.
* **Risk Rating**: **LOW**. Minor drawing logic adjustment confined to two lines in `TelemetryMetricGraph.kt`.
