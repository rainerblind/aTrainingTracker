# Stage 1 Analysis: ATT-5311 - TelemetryMetricGraph Marginal Histogram Domain Boundary Clamping

**Ticket**: [ATT-5311](https://rainerblind.atlassian.net/browse/ATT-5311)  
**Sub-task**: [ATT-5320](https://rainerblind.atlassian.net/browse/ATT-5320) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-5311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During on-device review of `ATT-5281` (*Leistung* chart in *Histogramm* mode), a solid black horizontal bar was observed along the top edge of the right marginal histogram in `TelemetryMetricGraph.kt`.
In `ATT-5281`, container tracks were rendered unconditionally across `linearBins` to eliminate white horizontal stripes in Zone 7. However, `linearBins` spans the full workout power range (up to 800+ W spikes), exceeding the graph's plotted Y-axis range `[dataMin, dataMax]` (e.g. 574 W). For all bins above `dataMax` (`bin.rangeMin >= dataMax`), `valueToY(...)` clamps coordinates to the top boundary, while `rectHeight = abs(yTop - yBottom).coerceAtLeast(1f)` forces a 1px height. Stacking dozens of 1px `alpha = 0.15f` rectangles at the top boundary compounds into an opaque black line.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  * In `TelemetryMetricGraph.kt` L488–500 (portrait) and L944–957 (landscape `AlignedMarginalZoneDistribution`), the drawing loops iterate over all `linearBins` without domain boundary filtering or clamping against `[dataMin, dataMax]`.
  * For out-of-range bins (`bin.rangeMin >= dataMax`), `valueToY(...)` clamps to `topPaddingPx`. Zero-height difference is forced to `1f` by `.coerceAtLeast(1f)`.
  * Repeated execution of `drawRect` / `drawRoundRect` with `bin.color.copy(alpha = 0.15f)` stacks $N$ translucent layers at the identical Y coordinate: $(1 - 0.15)^N \approx 0$ for $N \ge 30$, producing a solid black artifact at `y = topPaddingPx`.
  * Symmetrically, any bins below `dataMin` (`bin.rangeMax <= dataMin`) clamp and stack at `topPaddingPx + chartHeightPx`.
* **Independent Peer Review & Verification**:
  * Confirmed in `TelemetryMetricGraph.kt#L488-L510` & `#L944-L975`: both loops iterate unconditionally over all `linearBins` without filtering or clamping `[rangeMin, rangeMax]` to `[dataMin, dataMax]`.
  * Confirmed in `TelemetryZoneMath.kt#L86-L98` & `#L123-L135`: 5-zone / 7-zone modes already explicitly clamp intervals to `[dataMin, dataMax]` and discard bands where `clampedMin >= clampedMax`.
  * Confirmed in `docs/design_guidelines.md` §2.7 (Rule 70): explicitly codifies the Domain-Boundary Clamping Guard, requiring out-of-domain bin filtering (`bin.rangeMax > dataMin && bin.rangeMin < dataMax`) and interval clamping before `valueToY(...)` mapping.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Filter out-of-domain bins (`bin.rangeMax <= dataMin || bin.rangeMin >= dataMax`) and clamp `[rangeMin, rangeMax]` to `[dataMin, dataMax]` in `TelemetryMetricGraph.kt` portrait marginal strip (L488).
  * Apply identical filtering and clamping in landscape `AlignedMarginalZoneDistribution` (L944).
  * Retain unconditional background track continuity for all in-domain bins (`alpha = 0.15f`).
  * Add unit and contract tests in `TelemetryMetricGraphMarginalBarsContractTest.kt` and `TelemetryMetricGraphLandscapeContractTest.kt` verifying out-of-domain bins do not stack at boundaries.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to `ZoneDistributionModels.kt` or `linearBins` resolution logic.
  * No modifications to standalone `TelemetryHistogramChart.kt` (sprint-tail folding remains invariant per `REQ-UI-419`).
  * No alterations to 5-zone / 7-zone band calculation or non-histogram display modes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-443.2` (Portrait) & `REQ-UI-443.3` (Landscape), amending `REQ-UI-251` & `REQ-UI-431`. Targets `TelemetryMetricGraph.kt`.
* **Historical Origin & Commit Trace**: Commit `9d44d0f5` (Sprint 2026-45.2, `ATT-5190`) and commit `9ce1ef68` (Sprint 2026-45.3, `ATT-5281`).
* **Root Reason for Existing Formulation**: `ATT-5281` removed `if (bin.durationSec > 0L)` to guarantee background track continuity between sparse sprint spikes, but overlooked that `linearBins` spans beyond the graph's visible Y-axis range `[dataMin, dataMax]`.
* **Preservation of Core Invariants**: In-domain track continuity (`alpha = 0.15f`), foreground bar duration gating (`bin.durationSec > 0L`), standalone histogram folding, and multi-chart scrubbing alignment are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Domain Boundary Filtering & Clamping in Portrait (`TelemetryMetricGraph.kt#L488`)**:
   Skip bins with `bin.rangeMax <= dataMin || bin.rangeMin >= dataMax`. Clamp `clampedMin = maxOf(dataMin, bin.rangeMin.toDouble())` and `clampedMax = minOf(dataMax, bin.rangeMax.toDouble())`. If `clampedMin >= clampedMax`, skip. Map `clampedMax` and `clampedMin` to Y coordinates.
2. **Domain Boundary Filtering & Clamping in Landscape (`TelemetryMetricGraph.kt#L944`)**:
   Mirror the portrait filtering and clamping in `AlignedMarginalZoneDistribution`.
3. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
   - Zero side-effects on shared repositories or companion StateFlows.
   - Bins within `[dataMin, dataMax]` retain exact pixel mapping and visual continuity.
   - Contract test suite updated to assert domain boundary clamping and zero out-of-domain stacking.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero out-of-range histogram bins rendered at boundary coordinates (`y = topPaddingPx` or `y = topPaddingPx + chartHeightPx`).
  2. Continuous background container tracks for all in-domain `linearBins` spanning `[dataMin, dataMax]`.
  3. Standalone `TelemetryHistogramChart.kt` sprint-tail folding and interactive drill-down remain invariant.
* **Risk Rating**: **LOW**. Localized boundary check and clamping in two drawing loops in `TelemetryMetricGraph.kt`.
