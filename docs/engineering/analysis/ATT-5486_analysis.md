# Stage 1 Analysis: ATT-5486 - ElevationProfile: Use Fixed TTColor.Zone1 Green for Marginal Altitude Histogram

**Ticket**: [ATT-5486](https://rainerblind.atlassian.net/browse/ATT-5486)  
**Sub-task**: [ATT-5515](https://rainerblind.atlassian.net/browse/ATT-5515) (`[Analysis]`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5486`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In `ElevationProfile.kt` (line 502), the marginal altitude histogram (`altitudeHistogram`) currently derives its bar color dynamically from the very first elevation segment of the track:
`val altColor = cachedData.segments.firstOrNull()?.color ?: colorScheme.primary`
Because each segment in `cachedData.segments` is colored by its instantaneous slope gradient (`getElevationGradeColor(grade)` / `getClimbGradeColor(grade)`), the entire altitude distribution changes color depending on the slope of the first few meters of the activity: flat starts render Green (`TTColor.Zone1`), moderate inclines render Orange (`TTColor.Zone3`), and steep inclines or initial GPS elevation spikes render Red (`TTColor.Zone4`). Altitude reflects elevation above sea level, not slope; thus, coupling histogram color to the initial driveway grade causes non-deterministic, confusing color shifts. The user expects a consistent base elevation color (`TTColor.Zone1` Green).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `ATT-5370` introduced marginal histograms across all stacked Workout Details charts. While non-zoned telemetry graphs have a fixed series color (e.g. speed cyan, W'bal green), `ElevationProfile` has multi-colored slope segments. The fallback `cachedData.segments.firstOrNull()?.color` sampled segment 0, mistaking its local slope color for an overall "profile series color".
* **Independent Peer Review & Verification (ATT-3534 / Rules 69 & 70)**:
  - Audited `ElevationProfile.kt` (L498-514): `cachedData.segments.firstOrNull()?.color` is the sole source of `altColor` passed to `TelemetryHistogramCalculator.computeLinearMarginalHistogram`.
  - Confirmed `TTColor.Zone1` is imported and defined as the canonical base green (`#4CAF50`), matching `GradeLegend` flat/descent (< 2%).
  - Replacing the line with `val altColor = TTColor.Zone1` makes the histogram color deterministic, clean, and decoupled from initial slope.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Replace dynamic first-segment color lookup in `ElevationProfile.kt` with fixed `TTColor.Zone1`.
  2. Remove unused `colorScheme` dependency from the `altitudeHistogram` `remember` block.
  3. Add targeted unit/contract test verifying `altitudeHistogram` retains `TTColor.Zone1` regardless of initial segment grade.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to telemetry metric graphs (`Speed`, `HR`, `Power`, `W'bal`).
  2. No changes to `TelemetryHistogramCalculator.computeLinearMarginalHistogram` binning math or Rule 69/70 boundary clamping.
  3. No changes to zoom, panning, or climb category badges in `ElevationProfile`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-446.2` (*Series-Color Rendering for Non-Zoned Graphs*, `ATT-5370`).
* **Historical Origin & Commit Trace**: Commit `75485932` (`ATT-5370`, Sprint 2026-45.5) introduced universal marginal histograms and set `altColor = cachedData.segments.firstOrNull()?.color`.
* **Root Reason for Existing Formulation**: `ATT-5370` attempted to match each graph's marginal histogram to its primary stroke color. Because `ElevationProfile` lacks a single stroke color, segment 0 was sampled as an improvised proxy.
* **Preservation of Core Invariants**: Retains 1:1 plot alignment (Rule 64, 52dp portrait / 72% landscape), unconditional background tracks (Rule 69), domain-boundary clamping (Rule 70), and clean-room test stability.

---

## 5. Architectural Strategy & High-Level Solution
* **Component Changes**:
  - `ElevationProfile.kt`: Set `val altColor = TTColor.Zone1` inside `remember(pathPoints, cachedData.minAlt, cachedData.maxAlt, alignWithTelemetryCharts)`.
  - Unit Test: Add verification in `ElevationProfileGradeColorTest.kt` or `TelemetryMetricGraphLandscapeAlignmentTest.kt` asserting `TTColor.Zone1` invariance under steep start gradients.
* **Side-Effect & Cross-Caller Audit (Rule 68)**:
  - Downstream callers: `MapDetailLayout`, `TrackOnMapScreen`, `WorkoutSummary`. All benefit from uniform green altitude strip.
  - Zero DB, API, or state schema changes. 100% UI-only fix.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in `alignWithTelemetryCharts` layout; zero changes to `getElevationGradeColor` thresholds.
* **Risk Rating**: **LOW** (1-line constant color assignment in UI composable).
