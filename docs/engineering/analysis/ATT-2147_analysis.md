# Stage 1 Analysis: ATT-2147 - Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode

**Ticket**: [ATT-2147](https://rainerblind.atlassian.net/browse/ATT-2147)  
**Sub-task**: [ATT-2164](https://rainerblind.atlassian.net/browse/ATT-2164) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2147`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

In portrait orientation on mobile devices, horizontal width is constrained (~360–412 dp). The detailed workout view and summary cards present telemetry graphs (Heart Rate, Cycling Power) with continuous curves over time/distance, while time-in-zone distributions are separated into standalone cards further down the screen.

Athletes inspecting a telemetry curve lack an immediate, integrated visual connection between the instantaneous curve and the aggregate duration spent in each intensity zone. While landscape mode allows wide distribution tables with text and minutes, portrait orientation requires a **minimalist, ultra-compact marginal strip (ca. 24–32 dp)** directly adjacent to the graph's right edge:
1. **Zero Text Clutter**: Pure visual bar/histogram representation without numeric labels, text, percentages, or minutes.
2. **Vertical Threshold Alignment**: The horizontal bars or histogram buckets must align vertically with the exact Y-axis threshold boundaries of the curve.
3. **Synchronous Mode Switching**: When the athlete toggles between 5-Zones mode and fine-grained Histogram mode in the corresponding zone card, the marginal strip must switch representation synchronously.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Architectural Investigation of Current State
1. **`TelemetryMetricGraph.kt`**:
   - Computes `startPadding = 50.dp` and `endPadding = 25.dp`.
   - In `drawCanvas`, it renders horizontal zone background bands (`zoneBands`) and secondary right-hand text labels (`Z1`–`Z5`) in the 25.dp right margin when thresholds are defined.
   - However, it currently has no knowledge of `ZoneDistributionData` or duration percentages, rendering only static threshold bands without distribution weights.
2. **`HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`**:
   - Possess internal state `var displayMode by rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }`.
   - Because `displayMode` is unhoisted and private to the card, external components like `TelemetryMetricGraph` cannot react to user mode changes.
3. **`TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `WorkoutSummary.kt`**:
   - Possession of `hrZoneDistribution` and `powerZoneDistribution` is already established.
   - However, `TelemetryMetricGraph` invocations in `MapDetailLayout` and `WorkoutSummary` do not pass the distribution data or display mode.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement an ultra-slim marginal distribution strip (width ~24–32 dp) on the right side of `TelemetryMetricGraph` for Heart Rate and Power metrics.
  * Render pure visual horizontal bars in 5-zone mode (aligned with zone threshold boundaries).
  * Render fine-grained frequency bars in histogram mode (aligned with histogram bucket boundaries).
  * Hoist `displayMode` in zone distribution cards so switching between 5-zones and histogram updates the marginal strip synchronously.
  * Preserve pixel-perfect alignment of the primary graph and scrubbing marker across all charts.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Textual annotations or percentage labels inside the portrait marginal strip (must remain zero-text to prevent clutter).
  * Altering landscape mode table layouts or desktop analytics.
  * Altering Speed/Pace charts (speed/pace metrics do not use 5-zone power/HR models).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-251`), refining `REQ-UI-206` (*Telemetry Metric Graphs*) and `REQ-UI-230` (*Horizontal Background Training Zone Bands*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: Commit `e7a6a273` (`ATT-1391`) introduced `TelemetryMetricGraph`. Commit `53b708cf` (`ATT-1839`) added zone bands with right-side Z1–Z5 text labels.
* **Root Reason for Existing Formulation**: `endPadding = 25.dp` was reserved for textual zone badges ("Z1".."Z5"). Replacing or augmenting this with weighted marginal bars delivers immediate visual density validation without sacrificing layout symmetry.
* **Preservation of Core Invariants**: Horizontal graph axes and multi-chart scrubbing alignment with `ElevationProfile` (`startPadding = 50.dp`, total end allocation) must remain strictly invariant.

---

## 5. Architectural Strategy & High-Level Solution

1. **State Hoisting in Zone Cards (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)**:
   - Hoist `displayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES` and `onDisplayModeChange: (ZoneCardDisplayMode) -> Unit` parameters with default fallback to internal state if unhoisted.
2. **Marginal Strip Renderer in `TelemetryMetricGraph.kt`**:
   - Accept optional `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`.
   - In `Canvas` draw phase, when `zoneDistribution != null`:
     - **5-Zones Mode**: In the right marginal region, draw horizontal bars for Zones 1–5 spanning between `valueToY(band.maxVal)` and `valueToY(band.minVal)`. The bar width scales proportionally to the zone's time percentage (`percentage / 100f`).
     - **Histogram Mode**: Draw thin horizontal bars for each `TelemetryHistogramBin` between `valueToY(bin.rangeMax)` and `valueToY(bin.rangeMin)` with width proportional to bin frequency.
3. **Integration in `MapDetailLayout.kt` and `WorkoutSummary.kt`**:
   - Pass distribution data and hoisted `displayMode` to `TelemetryMetricGraph` and zone cards.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Synchronized scrubbing with `ElevationProfile` and track marker remains unaffected.
  2. Gesture handling and vertical scroll transparency remain 100% operational.
  3. 9-language translation parity maintained.
  4. 100% clean-room unit test suite pass rate.
* **Risk Rating**: **LOW**
  - Pure visual presentation enhancement utilizing pre-calculated distribution data with zero SQLite schema or threading changes.
