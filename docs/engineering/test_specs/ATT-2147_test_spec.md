# Stage 2: Requirement & Test Specification - ATT-2147: Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode

**Ticket**: [ATT-2147](https://rainerblind.atlassian.net/browse/ATT-2147)  
**Sub-task**: [ATT-2165](https://rainerblind.atlassian.net/browse/ATT-2165) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2147`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal Requirement Specification (`REQ-UI-251`)

### REQ-UI-251: Aftermath/UI: Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode
The system SHALL render an ultra-slim marginal distribution strip (width ~24–32 dp) adjacent to the right margin of `TelemetryMetricGraph.kt` in portrait orientation for Heart Rate and Power metrics, vertically aligned with the curve's Y-axis and synchronized with zone card mode toggles (ATT-2147):
1. **Marginal Strip Presentation (`TelemetryMetricGraph.kt`)**:
   - `TelemetryMetricGraph` SHALL accept optional parameters `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`.
   - When `zoneDistribution != null`, a compact marginal strip of width ~28 dp SHALL be rendered within the right boundary region of the graph canvas.
   - The strip SHALL be purely visual, omitting text labels, percentages, and duration units to eliminate visual clutter.
2. **5-Zones Mode Alignment**:
   - When `zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES`, the marginal strip SHALL render up to 5 horizontal bars corresponding to Zones 1..5.
   - The top and bottom Y coordinates of each zone bar SHALL align with the threshold boundary lines on the telemetry graph's Y axis (`valueToY(band.maxVal)` and `valueToY(band.minVal)`).
   - Each bar SHALL grow horizontally from left to right with a width proportional to its share of total active duration (`(percentage / 100f) * stripWidthPx`), styled in its respective zone color.
3. **Histogram Mode Alignment**:
   - When `zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM`, the strip SHALL render a vertical sequence of fine-grained frequency bars corresponding to `zoneDistribution.histogram.bins`.
   - Each bin bar SHALL span vertically between `valueToY(bin.rangeMax)` and `valueToY(bin.rangeMin)` and grow horizontally proportional to its frequency share, styled in the bin's zone color.
4. **Synchronous Mode Switching**:
   - In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, `displayMode: ZoneCardDisplayMode` and `onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null` SHALL be hoisted parameters.
   - Switching modes in the zone card SHALL immediately update the hoisted state, synchronously toggling the marginal strip on the graph between 5-zones and histogram representation.
5. **Preserved Invariants**:
   - Primary graph curve, multi-chart scrubbing alignment, horizontal axes with `ElevationProfile` (`startPadding = 50.dp`), and 9-language localization parity SHALL remain 100% intact.

---

### Chesterton's Fence Archaeology & Requirement Traceability
1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-251`), refining `REQ-UI-206` (*Telemetry Metric Graphs*) and `REQ-UI-230` (*Horizontal Background Training Zone Bands*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. **Historical Origin & Commit Trace**: Commit `e7a6a273` (`ATT-1391`) introduced `TelemetryMetricGraph`. Commit `53b708cf` (`ATT-1839`) added zone bands with right-side Z1–Z5 text labels.
3. **Root Reason for Existing Formulation**: `endPadding = 25.dp` was reserved for textual zone badges ("Z1".."Z5"). Replacing or augmenting this with weighted marginal bars delivers immediate visual density validation without sacrificing layout symmetry.
4. **Preservation of Core Invariants**: Horizontal graph axes and multi-chart scrubbing alignment with `ElevationProfile` (`startPadding = 50.dp`, total end allocation) must remain strictly invariant.

---

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Minimalist Bar Layout in Portrait)**:
  * *Given* a telemetry graph for HR or Power in portrait mode with zone distribution available,
  * *When* rendered,
  * *Then* a minimalist right-side marginal strip (ca. 24–32 dp) is displayed without text clutter.
* **AC-2 (Vertical Threshold Alignment)**:
  * *Given* the Y-axis scaling of the graph,
  * *When* rendering zone bars or histogram buckets,
  * *Then* the vertical extents align with the exact threshold boundaries of the curve.
* **AC-3 (Synchronous Mode Switching)**:
  * *Given* the mode toggle in the zone card,
  * *When* toggling between 5-Zones and Histogram,
  * *Then* the marginal strip updates synchronously.

---

## 2. Test Specification (`TST-UI-210`)

### Test Cases
1. **TST-UI-210.1 (TelemetryMetricGraph Marginal Parameters & Rendering)**:
   - Structural and architectural contract tests in `TelemetryMetricGraphMarginalBarsContractTest.kt`:
     - Verify `TelemetryMetricGraph` accepts `zoneDistribution` and `zoneDisplayMode`.
     - Verify drawing phase computes marginal strip rects aligning with `valueToY`.
2. **TST-UI-210.2 (Zone Cards State Hoisting)**:
   - Verify `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` accept `displayMode` and `onDisplayModeChange`.
3. **TST-UI-210.3 (Synchronous Mode Switching Contract)**:
   - Verify `TrackOnMapScreen` / `MapDetailLayout` hoist `displayMode` states and wire them to both `TelemetryMetricGraph` and the respective zone cards.
4. **TST-UI-210.4 (Clean-Room Full Suite Regression Execution)**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Requirement | Test Identifier | Target Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-251` (AC-1) | `TST-UI-210.1` | `TelemetryMetricGraph.kt` | Specified |
| `REQ-UI-251` (AC-2) | `TST-UI-210.1` | `TelemetryMetricGraph.kt` | Specified |
| `REQ-UI-251` (AC-3) | `TST-UI-210.2`, `TST-UI-210.3` | `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `MapDetailLayout.kt` | Specified |
