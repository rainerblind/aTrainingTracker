# Stage 1 Analysis: ATT-5504 - ElevationProfile: Restrict right-hand Altitude Histogram exclusively to Workout Details (hide in Live Segments, Climbs, Routes & Live Map)

**Ticket**: [ATT-5504](https://rainerblind.atlassian.net/browse/ATT-5504)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5504`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
Following the introduction of the right-hand marginal Altitude Histogram next to `ElevationProfile` in Workout Details (`ATT-5370`), `MapDetailLayout.kt` hardcoded `alignWithTelemetryCharts = true` unconditionally. Because `MapDetailLayout` is shared across non-workout inspection views, the marginal Altitude Histogram and its 52dp right margin reduction now leak into Live Segments, Segment Details, Climb Details, Route Details, Live Map sheets, and compact Workout List Cards. Athletes require clean full-width elevation profiles across non-workout views, restricting the marginal altitude histogram strictly to detailed workout analysis.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Hardcoded Parameter in Shared Layout**: In `MapDetailLayout.kt:389`, `alignWithTelemetryCharts` is passed as literal `true` to `ElevationProfile`. While `TrackOnMapScreen` and `WorkoutDetailTabbedContent` pass `workoutData != null`, all other callers pass `workoutData = null`.
* **Workout List Preview Leakage**: In `WorkoutSummary.kt:342`, the compact 100dp list card sets `alignWithTelemetryCharts = preferences.showTelemetryCharts && telemetryPoints.isNotEmpty()`, erroneously rendering a cramped 52dp altitude histogram inside feed items.
* **Independent Peer Review & Verification (Rules 69 & 70, ATT-5431)**:
  * Audited all 8 callers of `MapDetailLayout`: Verified only `TrackOnMapScreen` (`:412`) and `WorkoutDetailTabbedContent` (`:274, 316, 408`) provide `workoutData`. Callers `LiveSegmentSheet` (`:49`), `SegmentOnMapScreen` (`:102`), `ClimbOnMapScreen` (`:79`), `RouteOnMapScreen` (`:158`), `MapScreenWithTrack` (`:724`), and `WorkoutClusterHeatmapScreen` (`:387`) pass `workoutData = null`.
  * Audited `ElevationProfile.kt:498-508`: `altitudeHistogram` calculation and marginal layout are gated strictly on `alignWithTelemetryCharts`. When `false`, end padding reverts to standard `25.dp` in portrait and `0.dp` in landscape, with no `AlignedMarginalLinearDistribution`.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * In `MapDetailLayout.kt`, declare optional parameter `showElevationHistogram: Boolean = workoutData != null` and pass `alignWithTelemetryCharts = showElevationHistogram` to `ElevationProfile` (line 389).
  * In `WorkoutSummary.kt:342`, pass `alignWithTelemetryCharts = false` on the compact list card `ElevationProfile`.
  * Add unit tests verifying histogram suppression in non-workout screens and list cards, while preserving it in workout details.
* **Out-of-Scope Non-Goals**:
  * No changes to `TelemetryMetricGraph` or stacked telemetry zone distributions (`REQ-UI-446.2`/`.3`).
  * No modifications to coordinate systems, zoom/pan clamping, or climb shading algorithms.
* **Complexity Rating**: **1 SP (Trivial)** (Clean parameter gating on existing layout contracts).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-446` (*Universal Stacked Workout Details Marginal Histograms & Widened Strip Layout Parity*), targeting `MapDetailLayout.kt`, `WorkoutSummary.kt`, and `ElevationProfile.kt`.
* **Historical Origin & Commit Trace**: Commit `75485932fe` (`ATT-5370`, Sprint 2026-45.7), which widened the portrait margin to 52dp and added the series-colored linear altitude histogram.
* **Root Reason for Existing Formulation**: ATT-5370 aimed to align stacked telemetry graphs and elevation profile in Workout Details. In haste, `alignWithTelemetryCharts = true` was hardcoded in `MapDetailLayout` without considering non-workout consumers.
* **Preservation of Core Invariants**: Workout Details (`TrackOnMapScreen`, `WorkoutDetailTabbedContent`) retain 1:1 horizontal plot bounds and marginal altitude histograms (`Rule 64` / `REQ-UI-446.3`). Non-stacked callers retain clean full-width profiles (`REQ-UI-446.1`).

---

## 5. Architectural Strategy & High-Level Solution
* **Shared Map Container (`MapDetailLayout.kt`)**: Add parameter `showElevationHistogram: Boolean = workoutData != null`. Forward `alignWithTelemetryCharts = showElevationHistogram` to `ElevationProfile`.
* **Feed Summary Card (`WorkoutSummary.kt`)**: Update line 342 to `alignWithTelemetryCharts = false`.
* **Cross-Caller Impact**: All 6 non-workout `MapDetailLayout` callers instantly revert to clean profile rendering with zero caller signature refactoring required.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Horizontal plot alignment (`startPadding = 50.dp`, `endPadding = 52.dp`) strictly preserved in Workout Details (`Rule 64`).
  2. Non-workout callers and list cards retain uncluttered elevation profiles without right margin offsets.
* **Risk Rating**: **LOW** (Declarative layout parameter gating with zero database or state mutation risk).
