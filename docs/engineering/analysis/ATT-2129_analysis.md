# Stage 1 Analysis: ATT-2129 - Telemetry Graphs and Zone Cards in Workout Summary Do Not Open Workout Details on Click

**Ticket**: [ATT-2129](https://rainerblind.atlassian.net/browse/ATT-2129)  
**Sub-task**: [ATT-2132](https://rainerblind.atlassian.net/browse/ATT-2132) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2129`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

In the Workout Summary list item (`WorkoutSummary.kt`), athletes can enable inline previews for Telemetry Graphs (`showTelemetryCharts`) and Zone Analysis cards (`showZoneAnalysis`) via Tuning Preferences. 

While all other sections in `WorkoutSummary.kt` (Header, Description, Main Details, Extrema, Map Preview, and Elevation Profile) navigate to the Detailed Workout view (`MapDetailLayout` / `TrackOnMapScreen`) upon tap via `mapClickModifier`, tapping on:
1. Speed / Pace telemetry graphs,
2. Heart Rate telemetry graphs,
3. Cycling Power telemetry graphs, or
4. 5-Zone Distribution cards (Heart Rate & Power)

does nothing. The user expects tapping any section of the workout card preview to open the full detailed inspection view.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `WorkoutSummary.kt` and `TelemetryMetricGraph.kt` revealed two distinct root causes:

1. **Omission of `mapClickModifier` in `WorkoutSummary.kt`**:
   - In `WorkoutSummary.kt`, `val mapClickModifier = Modifier.clickable { if (workoutData.headerData.finished) onMapClick() }` is attached to `WorkoutDescription`, `WorkoutDetails`, `WorkoutExtrema`, `PathPreviewMap`, and `ElevationProfile`.
   - In lines 278–347 (Telemetry Metric Graphs) and lines 350–373 (Zone Distribution Cards), `mapClickModifier` was never applied to the section headings, graph modifiers, or zone cards.

2. **Pointer Event Interception by `TelemetryMetricGraph.kt`**:
   - In `TelemetryMetricGraph.kt` (lines 430–540), `Canvas` unconditionally attaches `.pointerInput(totalSpan, isTimeDomain, isPanMode) { awaitEachGesture { ... } }` to support scrubbing and panning.
   - In the summary list view, scrubbing is disabled (`onDistanceSelected = {}`), but the underlying pointer gesture detector intercepts down/up touch events, blocking parent click listeners from detecting taps and potentially interfering with list scrolling disambiguation.
   - In contrast, `ElevationProfile.kt` conditionally attaches `pointerInput` only when `showZoomControls == true`. In preview mode (`showZoomControls == false`), `ElevationProfile` omits `pointerInput`, allowing transparent click pass-through to parent `.clickable { onMapClick() }`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Enable click-to-open (`onMapClick()`) navigation across all telemetry graph sections (heading, canvas, and padding) in `WorkoutSummary.kt`.
  * Enable click-to-open (`onMapClick()`) navigation on `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` in `WorkoutSummary.kt`.
  * Parameterize `TelemetryMetricGraph` with `enableGestures: Boolean = true` (or read-only preview mode), omitting `.pointerInput` when `enableGestures == false` to ensure 100% gesture transparency and uncompromised vertical scrolling performance.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to scrubbing or zoom behavior in detailed view (`MapDetailLayout`).
  * No alteration of mode-switching segmented buttons inside `HeartRateZoneDistributionCard` / `PowerZoneDistributionCard`.
  * No changes to database persistence or data models.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-247`), extending and refining `REQ-UI-205` (*Slotted Analytics Architecture*) and `REQ-UI-206` (*Telemetry Metric Graphs*) under Epic `ATT-111`.
* **Historical Origin & Commit Trace**: Commit `e7a6a273` (`ATT-1391`) introduced `TelemetryMetricGraph` and inline previews in `WorkoutSummary.kt`.
* **Root Reason for Existing Formulation**: When telemetry graphs were first embedded into `WorkoutSummary.kt`, they were designed as visual previews. The author did not wire `mapClickModifier` to the newly added graph blocks or account for `pointerInput` gesture consumption on the canvas.
* **Preservation of Core Invariants**: Detailed view scrubbing, multi-chart synchronization, 5-zone histogram toggle interactions, and smooth 60fps vertical list scrolling are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **`TelemetryMetricGraph.kt` Gestures Gating**:
   - Add parameter `enableGestures: Boolean = true` to `TelemetryMetricGraph`.
   - When `enableGestures == true`, attach `.pointerInput(...)` as before.
   - When `enableGestures == false`, omit `.pointerInput(...)`, allowing the canvas to be a passive render target that propagates pointer events upwards.
2. **`WorkoutSummary.kt` Telemetry Graph Sections**:
   - Wrap each telemetry metric block (Heading + Graph) in a `Column(modifier = mapClickModifier.fillMaxWidth())`.
   - Pass `enableGestures = false` to `TelemetryMetricGraph`.
3. **`WorkoutSummary.kt` Zone Distribution Cards**:
   - Apply `mapClickModifier` to the modifier of `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`:
     `modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).then(mapClickModifier)`.
4. **Verification Strategy**:
   - Write structural and UI contract unit tests (`WorkoutSummaryClickContractTest.kt` and `TelemetryMetricGraphGesturesContractTest.kt`).
   - Run clean-room regression suite.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `MapDetailLayout` scrubbing and zooming retain 100% identical interactivity (`enableGestures = true` default).
  2. Vertical list scrolling in workout summary remains fluid without gesture stuttering.
  3. Parent ticket Human Decision Gate remains strictly guarded.
* **Risk Rating**: **LOW** (Targeted UI modifier wiring and optional gesture gating on canvas; zero database or background service impacts).
