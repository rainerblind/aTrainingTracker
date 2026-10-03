# Stage 1 Analysis: ATT-1813 - Reorder telemetry graphs: place Speed/Pace above Heart Rate graph

**Ticket**: [ATT-1813](https://rainerblind.atlassian.net/browse/ATT-1813)  
**Sub-task**: [ATT-1849](https://rainerblind.atlassian.net/browse/ATT-1849) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1813`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

In the Aftermath workout inspection view (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) and in the detailed workout list summary cards (`WorkoutSummary.kt`), continuous telemetry graphs are currently rendered in the order:
1. Heart Rate graph (`TelemetryMetricType.HEART_RATE`)
2. Speed / Pace graph (`TelemetryMetricType.SPEED` / `TelemetryMetricType.PACE`)
3. Cycling Power graph (`TelemetryMetricType.POWER`)

Directly following the continuous telemetry charts section are the zone distribution cards:
- Heart Rate 5-Zone Distribution Card (`HeartRateZoneDistributionCard`)
- Power 5-Zone Distribution Card (`PowerZoneDistributionCard`)

In almost all athletic sessions (running, walking, standard cycling without a power meter), athletes record Heart Rate and GPS Speed/Pace. In the current layout, the Speed/Pace graph is positioned directly in between the Heart Rate continuous curve and the Heart Rate Zone Distribution card. This creates an awkward, disconnected visual rhythm: the user views their continuous heart rate curve, then looks at speed/pace, and then jumps back to heart rate for the 5-zone distribution.

The goal of ATT-1813 is to reorder the continuous telemetry graphs to place Speed/Pace above Heart Rate. This clusters the Heart Rate continuous curve directly above the Heart Rate Zone Distribution card, establishing an intuitive, cohesive visual analysis flow for cardiovascular exertion.

---

## 2. Root Cause Analysis & Layout Investigation

### Current Implementation in `MapDetailLayout.kt` (lines 271–334):
```kotlin
if (showZoomControls) {
    // 1. HR Graph
    if (TelemetryMetricUtils.hasHeartRateData(path)) {
        ...
        TelemetryMetricGraph(
            metricType = TelemetryMetricType.HEART_RATE,
            ...
        )
    }

    // 2. Speed / Pace Graph
    if (TelemetryMetricUtils.hasSpeedData(path)) {
        ...
        TelemetryMetricGraph(
            metricType = if (isRunning) TelemetryMetricType.PACE else TelemetryMetricType.SPEED,
            ...
        )
    }

    // 3. Power Graph
    if (TelemetryMetricUtils.hasPowerData(path)) {
        ...
        TelemetryMetricGraph(
            metricType = TelemetryMetricType.POWER,
            ...
        )
    }
}
```

### Current Implementation in `WorkoutSummary.kt` (lines 278–346):
```kotlin
if (preferences.showTelemetryCharts && telemetryPoints.isNotEmpty()) {
    // 1. HR Graph
    if (TelemetryMetricUtils.hasHeartRateData(telemetryPoints)) { ... }

    // 2. Speed / Pace Graph
    if (TelemetryMetricUtils.hasSpeedData(telemetryPoints)) { ... }

    // 3. Power Graph
    if (TelemetryMetricUtils.hasPowerData(telemetryPoints)) { ... }
}
// Followed immediately by:
if (preferences.showZoneAnalysis && (hrZoneDistribution != null || powerZoneDistribution != null)) {
    hrZoneDistribution?.let { HeartRateZoneDistributionCard(...) }
    powerZoneDistribution?.let { PowerZoneDistributionCard(...) }
}
```

### Structural Findings:
1. `MapDetailLayout.kt` and `WorkoutSummary.kt` independently implement the rendering loop for continuous telemetry charts.
2. In both locations, Heart Rate is rendered first, followed by Speed/Pace, followed by Power.
3. Neither composable has an external ordering dependency or side effect that requires Heart Rate to precede Speed/Pace: both check `TelemetryMetricUtils` for data availability and configure `TelemetryMetricGraph` with the appropriate `metricType`.
4. Moving Speed/Pace above Heart Rate in both files will achieve the desired clustered visual flow:
   - Elevation Profile
   - Speed / Pace Graph (if present)
   - Heart Rate Graph (if present)
   - Power Graph (if present)
   - Heart Rate Zone Distribution Card (if present)
   - Power Zone Distribution Card (if present)
   - Lap Split Visualizer Card (if present)

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * In `MapDetailLayout.kt`, reorder the telemetry graph blocks under `showZoomControls` so that the Speed/Pace graph block precedes the Heart Rate graph block.
  * In `WorkoutSummary.kt`, reorder the telemetry graph blocks under `showTelemetryCharts` so that the Speed/Pace graph block precedes the Heart Rate graph block.
  * Maintain all existing conditional presence checks (`hasSpeedData`, `hasHeartRateData`, `hasPowerData`), divider lines, headings, and padding.
  * Update visual contract tests (`TelemetryMetricGraphTest.kt` / `MapDetailLayoutTest.kt`) to verify the new graph ordering.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not change the internal drawing, scaling, or interpolation logic of `TelemetryMetricGraph.kt`.
  * Do not alter the order of Zone Distribution cards or Lap Split cards in `analyticsContent`.
  * Do not modify database schemas, repository queries, or preference models.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-206` (*Aftermath: Continuous Telemetry Metric Graphs (Heart Rate, Speed/Pace, Power) with Section Headings & Synchronized Multi-Chart Scrubbing Architecture*), extending Epic `ATT-111`.
* **Historical Origin & Commit Trace**:
  - `REQ-UI-206` was introduced in commit `061a4b35` (ATT-1740) where `TelemetryMetricGraph.kt` was created and added to `MapDetailLayout.kt`.
  - In ATT-1714 (`REQ-UI-210`), telemetry charts were incorporated into `WorkoutSummary.kt` following the same ordering.
* **Root Reason for Existing Formulation**:
  The initial implementation in ATT-1740 rendered graphs in an arbitrary order (Heart Rate, Speed/Pace, Power) following the order of metrics in the telemetry model. At the time, the visual adjacency between the continuous Heart Rate curve and the Heart Rate Zone Distribution card was not evaluated from a user experience standpoint.
* **Preservation of Core Invariants**:
  - **Synchronized Multi-Chart Scrubbing**: All graphs continue sharing `selectedDistance` and updating synchronously.
  - **Adaptive Sport Selection**: Running sports continue rendering Pace (`min/km`), while other sports render Speed (`km/h`).
  - **Localization Parity**: All 5 graph heading string resources remain identical across all 9 locales.
  - **Clean Omission**: Graphs for metrics not present in a workout continue to be omitted with 0dp whitespace.

---

## 5. Architectural Strategy & High-Level Solution

1. In `MapDetailLayout.kt`:
   - Move the Speed/Pace section (heading and `TelemetryMetricGraph`) directly above the Heart Rate section.
   - Maintain the `Spacer(modifier = Modifier.height(8.dp))` spacing between graphs.
2. In `WorkoutSummary.kt`:
   - Move the Speed/Pace block (divider, heading, and `TelemetryMetricGraph`) directly above the Heart Rate block.
3. In `TelemetryMetricGraphTest.kt`:
   - Add/update unit test assertions verifying that in both `MapDetailLayout.kt` and `WorkoutSummary.kt`, the Speed/Pace graph appears before the Heart Rate graph.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Synchronized scrubbing cursor position across graphs and map track remains 100% functional.
  2. Running sport type correctly toggles between Speed and Pace labels.
  3. When telemetry data is missing, omitted graphs leave no extraneous dividers or whitespace.
  4. 100% pass rate across test suite (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**
  - Declarative Compose layout reordering with zero business logic or state mutations.
