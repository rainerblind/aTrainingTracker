# Stage 5 Walkthrough: ATT-2147 - Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode

**Ticket**: [ATT-2147](https://rainerblind.atlassian.net/browse/ATT-2147)  
**Sub-task**: [ATT-2168](https://rainerblind.atlassian.net/browse/ATT-2168) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2147`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Executive Summary & Verification Goals

The goal of ATT-2147 was to introduce an ultra-slim marginal distribution strip adjacent to the right margin of `TelemetryMetricGraph.kt` in portrait orientation for Heart Rate and Power metrics, vertically aligned with the curve's Y-axis and synchronized with zone card mode toggles (5-zones vs. fine-grained histogram).

### Delivered Architecture & Implementation
1. **Marginal Strip in `TelemetryMetricGraph.kt`**:
   - Accepts optional parameters `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`.
   - Renders a clean ~20 dp marginal strip right of the curve within the right margin region.
   - 5-Zones mode: Renders horizontal bars for Zones 1..5 aligned with the exact threshold boundaries via `valueToY(band.maxVal)` / `valueToY(band.minVal)` with bar width proportional to duration percentage (`entry.percentage / 100f * stripWidthPx`).
   - Histogram mode: Renders fine-grained frequency bars for each histogram bin aligned with `bin.rangeMax` / `bin.rangeMin` via `valueToY` with bar width proportional to relative bin frequency.
   - Pure visual representation: Suppresses text labels in favor of density and zero clutter.
2. **State Hoisting in Cards (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)**:
   - Hoisted `displayMode` and `onDisplayModeChange` with seamless fallback to internal state.
3. **Screen Layout Integration (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutSummary.kt`)**:
   - Hoisted display modes and wired them bidirectionally between `TelemetryMetricGraph` and the zone distribution cards.

---

## 2. Test Execution & Verification Matrix

| Test Suite / Target | Description | Result |
| :--- | :--- | :--- |
| **`TelemetryMetricGraphMarginalBarsContractTest`** | Verified parameter contract, state hoisting in cards, wiring in MapDetailLayout/TrackOnMapScreen, and bar scaling math | **PASS (100%)** |
| **`MapDetailLayoutCollapsingHeaderContractTest`** | Verified nested-scroll collapsing connection and minimum map height bounds | **PASS (100%)** |
| **`MapDetailLayoutMetadataSlotContractTest`** | Verified upper metadata slotting above the map and outside lower column | **PASS (100%)** |
| **`TrackOnMapScreenMetadataRoutingContractTest`** | Verified Strava section upper slot routing and analytics slot cleanup | **PASS (100%)** |
| **Full Clean-Room Regression Suite** | `./gradlew testDebugUnitTest` across all unit tests | **PASS (100%)** |

---

## 3. Invariants & Backward Compatibility Verification

- **Multi-Chart Scrubbing**: `startPaddingPx = 50.dp` remains identical, ensuring perfect horizontal alignment with `ElevationProfile` and other telemetry graphs.
- **Fallback Invariant**: When `zoneDistribution == null`, `TelemetryMetricGraph` displays standard Z1–Z5 right-axis labels with zero UI distortion.
- **Gesture Invariant**: Drag and pan gestures on the canvas remain fully responsive and undisturbed.
