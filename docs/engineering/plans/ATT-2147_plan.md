# Stage 3 Implementation Plan: ATT-2147 - Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode

**Ticket**: [ATT-2147](https://rainerblind.atlassian.net/browse/ATT-2147)  
**Sub-task**: [ATT-2166](https://rainerblind.atlassian.net/browse/ATT-2166) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2147`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architectural Decomposition (SWE.2)

### Target Components & Boundaries
1. **UI Presentation Component (`TelemetryMetricGraph.kt`)**:
   - Extend signature with optional `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`.
   - In `Canvas` draw phase, when `zoneDistribution != null`, render a dedicated ~24–28 dp marginal strip right of the chart canvas.
   - For `FIVE_ZONES` mode: Render 5 horizontal bars aligned with `band.minVal` / `band.maxVal` via `valueToY`.
   - For `HISTOGRAM` mode: Render fine-grained frequency bars for each `TelemetryHistogramBin` aligned with `bin.rangeMin` / `bin.rangeMax` via `valueToY`.
   - Pure visual representation (zero text, zero labels, zero duration units).
2. **State Hoisting in Cards (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)**:
   - Hoist `displayMode: ZoneCardDisplayMode? = null` and `onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null`.
   - If not supplied by caller, default cleanly to internal `rememberSaveable` state.
3. **Screen Layout Integration (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `WorkoutSummary.kt`)**:
   - In `MapDetailLayout.kt`, accept `hrZoneDistribution` and `powerZoneDistribution`, hoist `hrZoneDisplayMode` and `powerZoneDisplayMode`, and wire them to `TelemetryMetricGraph`.
   - In `TrackOnMapScreen.kt`, wire hoisted modes to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
   - In `WorkoutSummary.kt`, wire hoisted modes to `TelemetryMetricGraph` and cards.

---

## 2. Atomic Step Sequencing

### Step 1: State Hoisting in Zone Cards
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt`
* **Changes**:
  - Add `displayMode: ZoneCardDisplayMode? = null` and `onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null` parameters.
  - Wire segmented button selection and callbacks.
* **Verification**: Targeted compilation and card test.

### Step 2: Minimalist Marginal Strip Rendering in `TelemetryMetricGraph.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  - Add `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES` parameters.
  - In Canvas draw block:
    - If `zoneDistribution != null`, render marginal strip within right margin region.
    - 5-Zones mode: iterate `zoneDistribution.entries`, map Y coordinates via `valueToY`, render horizontal bars scaled by `percentage / 100f`.
    - Histogram mode: iterate `zoneDistribution.histogram.bins`, map Y coordinates, render horizontal bars scaled by relative frequency.
* **Verification**: Unit and contract tests.

### Step 3: Screen & Layout Wiring
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* **Changes**:
  - Pass distribution data and hoisted display modes between graphs and cards.
* **Verification**: UI rendering and interaction.

### Step 4: Contract Tests & Regression
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphMarginalBarsContractTest.kt`
* **Commands**:
  - `./gradlew testDebugUnitTest --tests "*TelemetryMetricGraphMarginalBarsContractTest*"`
  - `./gradlew testDebugUnitTest`

---

## 3. Invariant Protection & Rollback Safety

1. **Scrubbing Invariant**: Start padding (50.dp) and horizontal scale of the main curve remain unchanged, ensuring synchronized scrubbing across ElevationProfile and telemetry graphs.
2. **Gesture Invariant**: Drag and pan gestures on the canvas remain completely unaffected.
3. **Fallback Invariant**: If `zoneDistribution == null`, `TelemetryMetricGraph` retains its classic right-axis Z1–Z5 text presentation without any layout shifts.
4. **Clean-Room Regression**: Zero test regressions across existing unit and contract tests.
