# Stage 1 Analysis: ATT-5370 - Workout Details Graphs: Add wider right-side marginal histograms to all graphs (Elevation, Speed/Pace, W' Balance) in graph color

**Ticket**: [ATT-5370](https://rainerblind.atlassian.net/browse/ATT-5370)  
**Sub-task**: [ATT-5387](https://rainerblind.atlassian.net/browse/ATT-5387) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-45.5`  
**Branch**: `feature/ATT-5370`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
In Workout Details / Map inspection views (`MapDetailLayout`, `WorkoutSummary`, `TrackOnMapScreen`), only Heart Rate and Power render right-side marginal histograms (colored by physiological zones). Sibling stacked charts (`ElevationProfile`, `SPEED/PACE`, `W_PRIME_BAL`) leave their right margin unused (empty `25.dp` strip in portrait; blank `Spacer(weight(0.28f))` in landscape). Furthermore, the `25.dp` portrait strip is too narrow (~`20.dp` bar width) for clear distribution legibility. This ticket extends marginal histograms across all stacked graphs in their primary series color, widens the portrait margin to `52.dp`, and maintains strict horizontal plot alignment (Rule 64).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause & Architectural Gap**:
  * `TelemetryMetricGraph.kt`: Lines 448–518 gate marginal bars strictly on `zoneDistribution != null && !isLandscape`. In landscape (lines 811–824), non-zoned metrics fall back to `Spacer(modifier = Modifier.weight(0.28f))`. No continuous linear binning exists for non-zoned metrics (`Speed/Pace`, `W'bal`).
  * `ElevationProfile.kt`: Allocates `end = 25.dp` in portrait and `Spacer(weight(0.28f))` in landscape without calculating or rendering an altitude distribution histogram. Standalone callers (`SensorGridScreen`, `ClimbDetailSheet`, `RouteItem`) need to stay clean.
  * Portrait Strip Width: `endPaddingPx = 25.dp.toPx()` allocates only `stripWidthPx = 20.dp.toPx()`, which visually constricts histogram frequency bars.
* **Independent Peer Review & Verification**:
  * Audited `TelemetryMetricGraph.kt` (lines 397, 448, 811): Confirmed `endPaddingPx = 25.dp` and empty fallback spacer for non-zoned series.
  * Audited `ElevationProfile.kt` (lines 509, 669, 957): Confirmed `end = 25.dp` padding and `weight(0.28f)` spacer with zero histogram rendering.
  * Cross-Caller Audit (Rule 68): Audited all 5 non-stacked `ElevationProfile` callers (`SensorGridScreen`, `LiveClimbSheet`, `ClimbDetailSheet`, `RouteItem`, `SegmentItem`). Verified they must remain clean via `alignWithTelemetryCharts = false`.
  * Verified Rules 69 & 70 compliance: Non-zoned histograms must draw unconditional `alpha = 0.15f` background tracks and strictly clamp `[dataMin, dataMax]` bounds to prevent out-of-domain stacking at `y = 0`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Universal marginal histogram across all stacked graphs (`ElevationProfile`, `SPEED/PACE`, `HEART_RATE`, `POWER`, `W_PRIME_BAL`) in `MapDetailLayout`, `WorkoutSummary`, and `TrackOnMapScreen`.
  * Series-color rendering for non-zoned graphs (`colorScheme.primary` for Speed/Pace, `TTColor.WPrimeBalGreen` for W'bal, series color for Elevation) with `alpha = 0.15f` background track (Rule 69) and domain boundary clamping (Rule 70).
  * Preserve 5/7-zone coloring for Heart Rate and Power.
  * Widen right-side margin in portrait from `25.dp` to `52.dp` (yielding ~`44.dp` bar width) across all stacked charts, preserving 1:1 plot width alignment (Rule 64) and 72%/28% landscape split.
  * Parameterize `ElevationProfile` (`alignWithTelemetryCharts: Boolean = false`) so compact cockpit/bottom-sheet callers remain clean.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying physiological zone calculations or threshold storage (`TelemetryZoneMath`).
  * Changing zoom/pan gesture mechanics, coordinate offset formulas, or scrubbing badge logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-251` (Portrait Marginal Strip), `REQ-UI-431` (Landscape Marginal Pane), and `REQ-UI-433.4` (Rule 64 Multi-Chart Alignment). Targets `TelemetryMetricGraph.kt`, `ElevationProfile.kt`, `MapDetailLayout.kt`, `WorkoutSummary.kt`, and `TrackOnMapScreen.kt`.
* **Historical Origin & Commit Trace**: Commits `c8192a0e` (`ATT-2147`, `REQ-UI-251`), `dcb8f15f` (`ATT-2145`, `REQ-UI-431`), `56b230ec` (`ATT-4774`, `REQ-UI-433.4`), and `9ce1ef68` / `cbb8317a` (`ATT-5281` / `ATT-5311`, Rules 69 & 70).
* **Root Reason for Existing Formulation**: `25.dp` was selected in `ATT-2147` as a conservative right margin exclusively for HR/Power zone bars; non-zoned charts left this space blank because physiological zones did not apply.
* **Preservation of Core Invariants**: 1:1 horizontal plot width and scrubbing cursor alignment (`startPadding = 50.dp`, `endPadding = 52.dp` in portrait; 72%/28% split in landscape per Rule 64) is strictly preserved across all stacked charts.

---

## 5. Architectural Strategy & High-Level Solution
| Component | Architectural Strategy |
| :--- | :--- |
| `TelemetryMetricGraph.kt` | Update `endPaddingPx = if (isLandscape) 0.dp.toPx() else 52.dp.toPx()`. For non-zoned metrics (`SPEED/PACE`, `W_PRIME_BAL`), compute 20–24 uniform linear frequency bins across `[dataMin, dataMax]` and render series-colored bars with `alpha = 0.15f` background track (Rules 69 & 70) in portrait strip and landscape 28% pane. |
| `ElevationProfile.kt` | Add `alignWithTelemetryCharts: Boolean = false`. When true, apply `endPadding = 52.dp` in portrait and 72%/28% split in landscape, computing and rendering a series-colored altitude marginal histogram across `[minAlt, maxAlt]`. Standalone callers default to false. |
| Callers (`MapDetailLayout`, `WorkoutSummary`, `TrackOnMapScreen`) | Pass `alignWithTelemetryCharts = true` to `ElevationProfile` and ensure stacked charts share identical horizontal plot bounds. |

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Strict 1:1 horizontal plot width and scrubbing alignment across all stacked charts (Rule 64).
  2. Unconditional background track continuity (Rule 69) and domain boundary clamping without stacking at `y = 0` (Rule 70).
  3. Non-stacked callers (`SensorGridScreen`, `LiveClimbSheet`, etc.) unaffected.
* **Risk Rating**: **LOW** (UI layout styling and coordinate alignment; bounded blast radius verified via unit tests and previews).
