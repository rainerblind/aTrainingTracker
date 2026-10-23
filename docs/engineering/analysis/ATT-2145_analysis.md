# Stage 1 Analysis: ATT-2145 - Compact Aligned Marginal Zone Distribution Bars in Landscape Mode

**Ticket**: [ATT-2145](https://rainerblind.atlassian.net/browse/ATT-2145)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*Landscape Mode*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-2145`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
During workout aftermath analysis, athletes inspect telemetry graphs (HR, Power, Cadence, Pace) to evaluate effort distribution:
1. *Portrait Space Constraints*: In portrait mode, `REQ-UI-251` (`ATT-2147`) renders an ultra-slim marginal zone strip inside graph end padding (`endPaddingPx = 25.dp`). Limited width prevents rendering zone labels, percentages, or time durations.
2. *Underutilized Landscape Canvas*: In landscape mode (~640–900dp width), ample horizontal space allows docking a dedicated marginal distribution pane directly to the right of the telemetry graph.
3. *Lack of Vertical Alignment*: Conventional summary tables or pie charts disconnect time-in-zone statistics from graph Y-axis thresholds. Athletes need horizontal distribution bars that align 1:1 on the vertical Y-axis with the metric's zone thresholds (Zone 5 at top, Zone 1 at bottom).

---

## 2. Root Cause Analysis & Independent Peer Review (ATT-3534)
* **Live Code Audit**:
  * *Portrait Layout*: `TelemetryMetricGraph.kt:431-495` renders inline marginal bars directly inside the Canvas end padding (`25.dp`).
  * *Viewport Allocation*: `MapDetailLayout.kt` and `TelemetryMetricGraph.kt` currently allocate 100% width to the chart canvas regardless of screen orientation.
  * *Threshold Mapping*: In `TelemetryMetricGraph.kt`, Y coordinates are computed via `normalize(metricValue, minValue, maxValue)`. Marginal zone bands map linearly to these Y coordinate intervals.
  * *Peer Review Finding*: Placing the marginal zone pane in a horizontal `Row` (70–75% graph, 25–30% marginal bars) in landscape ensures clear text legibility (`Z5 45% 19:40`) while keeping bar vertical bounds strictly tied to the graph canvas Y-axis coordinates.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Orientation-aware layout branching in telemetry graph components for landscape viewports.
  * Side-by-side dual-pane layout in landscape: Telemetry chart on left (70–75% width), compact aligned marginal zone pane on right (25–30% width).
  * Exact vertical alignment: 5 horizontal zone bars (or fine histogram bins) aligned 1:1 on the Y-axis with graph zone thresholds.
  * Granular metrics display: Horizontal bars grow left-to-right displaying zone label, percentage, and time spent (e.g., `Z5 45% 19:40 min`).
  * Synchronous mode switching: Switching between 5-Zone bars (`FIVE_ZONES`/`COLUMNS`) and fine-grained frequency histogram (`HISTOGRAM`).
  * Scrubber line and tooltip synchronization across graph and marginal pane.
* **Out-of-Scope Non-Goals**:
  * Live tracking screen sensor grid layout (handled under ATT-52).
  * Backend database schema or telemetry calculation algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-251` (*Marginal Zone Bars & Histogram in Portrait*), complements `REQ-UI-206` (*Telemetry Graph Layout*); introduces `REQ-UI-424` (*Landscape Compact Aligned Marginal Zone Distribution*).
* **Historical Origin & Commit Trace**: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`, `TST-UI-210`, inline marginal zone strip within `endPaddingPx = 25.dp`).
* **Root Reason for Existing Formulation**: Portrait viewports (~360dp width) lack horizontal space for text labels and time metrics without severe graph compression.
* **Preservation of Core Invariants**: 100% portrait layout fidelity, multi-chart scrubbing alignment (`startPadding = 50.dp`), zero callback suppression (Rule 34), and 100% clean-room test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
1. *Orientation Branching*: In landscape mode, wrap `TelemetryMetricGraph` and `AlignedMarginalZoneDistribution` inside a parent `Row`.
2. *Proportional Sizing*: Left pane (Telemetry Graph) takes `weight(0.72f)`, right pane (Marginal Zone Distribution) takes `weight(0.28f)`.
3. *Vertical Y-Axis Alignment*: Pass identical top/bottom graph plot padding and Y-normalization bounds into the marginal pane canvas so horizontal zone bars match the graph's zone thresholds precisely.
4. *Zone Metrics Content*: Render filled horizontal bars with text overlay (`ZoneLabel`, `Percentage%`, `Duration mm:ss`).
5. *Mode Synchronization*: Bind `ZoneDistributionMode` state synchronously across graph and marginal distribution components.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in portrait mode; graph scrub synchronization preserved; `startPadding` alignment intact; 100% test pass rate.
* **Risk Rating**: **LOW–MEDIUM** (Scoped to Aftermath telemetry graph presentation; mitigated by isolated landscape layout branching).
