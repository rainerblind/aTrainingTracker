# Stage 3 Implementation Plan: ATT-2170 - Place Lap Splits Between Extrema and Strava/Map in Detailed Workout View to Match Summary Order

**Ticket**: [ATT-2170](https://rainerblind.atlassian.net/browse/ATT-2170)  
**Sub-task**: [ATT-2173](https://rainerblind.atlassian.net/browse/ATT-2173) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architecture & Technical Strategy (SWE.2)

To fulfill `REQ-UI-252` and establish structural order parity between `WorkoutSummary.kt` and `TrackOnMapScreen.kt`, `LapSplitVisualizerCard` must be relocated from `analyticsContent` into `metadataContent`.

### Section Order Parity:
1. `WorkoutHeader`
2. `WorkoutDescription`
3. `WorkoutExtrema`
4. **`LapSplitVisualizerCard`** (guarded by `activeDetailPrefs.showLaps && splitChartData != null`)
5. **`StravaActivitySection`** (guarded by `activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()`)
6. **Map Viewport** (`MapDetailLayout` `BoxWithConstraints`)
7. **Telemetry Graphs** (`TelemetryMetricGraph` - Speed, HR, Power)
8. **Zone Distribution Cards** (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`)

---

## 2. Step-by-Step Atomic Implementation Steps

### Step 1: Relocate `LapSplitVisualizerCard` to `metadataContent` in `TrackOnMapScreen.kt`
- In `TrackOnMapScreen.kt`'s default `metadataContent` block:
  - Add `LapSplitVisualizerCard` invocation immediately after `WorkoutExtrema` and before `StravaActivitySection`.
  - Retain exact interactive bindings: `selectedLapNr` state and `onLapClick` toggle lambda.
- In `TrackOnMapScreen.kt`'s default `analyticsContent` block:
  - Delete `LapSplitVisualizerCard` block completely, leaving only `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.

### Step 2: Implement Architectural Contract Test
- Create `TrackOnMapScreenLapRoutingContractTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/`:
  - Verify `TrackOnMapScreen.kt` renders `LapSplitVisualizerCard` inside `metadataContent`.
  - Verify `LapSplitVisualizerCard` is located after `WorkoutExtrema` and before `StravaActivitySection`.
  - Verify `analyticsContent` does not contain `LapSplitVisualizerCard`.
  - Verify preference guard `activeDetailPrefs.showLaps`.
- Update `TrackOnMapScreenMetadataRoutingContractTest.kt` if necessary to assert the canonical order.

### Step 3: Clean-Room Regression Suite Execution
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Invariants & Guardrails

- **Interactive Map Lap Highlighting**: Clicking a lap continues to highlight the corresponding path segment on the map below via `LapSegmentUtils.sliceLapSegment`.
- **Collapsing Integration**: Because `metadataContent` is wired to `CollapsingAppBarNestedScrollConnection`, the lap split card smoothly collapses out of the viewport on scroll gestures, preventing map squashing.
- **Caller Override Safety**: When `metadataContent != null`, custom callers continue to override default metadata without regressions.
- **9-Language Translation Parity**: No string resource modifications required.
