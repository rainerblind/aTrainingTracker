# Stage 2: Requirement & Test Specification - ATT-1813: Reorder telemetry graphs: place Speed/Pace above Heart Rate graph

**Ticket**: [ATT-1813](https://rainerblind.atlassian.net/browse/ATT-1813)  
**Sub-task**: [ATT-1850](https://rainerblind.atlassian.net/browse/ATT-1850) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-214` (*Aftermath: Telemetry Metric Graph Ordering (Speed/Pace Preceding Heart Rate) for Clustered Cardiovascular Exertion Flow*)  
**Test Spec ID**: `TST-UI-168`  
**Branch**: `feature/ATT-1813`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-214)

### 1.1 Problem Statement & Rationale
In Aftermath workout inspection (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) and in workout summary cards (`WorkoutSummary.kt`), continuous telemetry graphs have previously been rendered in the order: Heart Rate -> Speed/Pace -> Power. Because typical athletic sessions record Heart Rate and Speed/Pace but no Power, the Speed/Pace graph sat directly between the continuous Heart Rate curve and the Heart Rate Zone Distribution card. Reordering the graphs so that Speed/Pace precedes Heart Rate clusters the continuous heart rate curve directly adjacent to the 5-zone distribution card, establishing an intuitive, uninterrupted visual flow for cardiovascular exertion analysis.

### 1.2 Functional & Architectural Requirements
1. **Layout Ordering in `MapDetailLayout.kt`**:
   - Under `if (showZoomControls)`, continuous telemetry graphs SHALL be arranged strictly in the order:
     1. Speed / Pace Graph (`TelemetryMetricType.SPEED` or `TelemetryMetricType.PACE` depending on `bSportType == BSportType.RUN`) if `TelemetryMetricUtils.hasSpeedData(path)`
     2. Heart Rate Graph (`TelemetryMetricType.HEART_RATE`) if `TelemetryMetricUtils.hasHeartRateData(path)`
     3. Cycling Power Graph (`TelemetryMetricType.POWER`) if `TelemetryMetricUtils.hasPowerData(path)`
   - When scrollable content is rendered, these graphs SHALL precede `analyticsContent` (which contains `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, and `LapSplitVisualizerCard`).

2. **Layout Ordering in `WorkoutSummary.kt`**:
   - Under `if (preferences.showTelemetryCharts && telemetryPoints.isNotEmpty())`, continuous telemetry graphs SHALL be arranged strictly in the order:
     1. Speed / Pace Graph if `TelemetryMetricUtils.hasSpeedData(telemetryPoints)`
     2. Heart Rate Graph if `TelemetryMetricUtils.hasHeartRateData(telemetryPoints)`
     3. Cycling Power Graph if `TelemetryMetricUtils.hasPowerData(telemetryPoints)`
   - This section SHALL precede the lazy-loaded Zone Distribution cards section (`HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`).

3. **Conditional Rendering & Visual Hygiene**:
   - Each graph SHALL only render if corresponding metric samples exist.
   - If a metric is absent, it SHALL be omitted cleanly with zero extra whitespace or dangling divider lines.
   - Spacing (`Spacer(modifier = Modifier.height(8.dp))` in `MapDetailLayout.kt`, and `HorizontalDivider` in `WorkoutSummary.kt`) SHALL be preserved cleanly.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Inspection Screen Graph Sequence)**:
  * *Given* an athlete viewing a completed workout with Heart Rate and GPS track data in `TrackOnMapScreen`,
  * *When* the lower section is inspected,
  * *Then* the Speed/Pace graph SHALL appear above the Heart Rate graph, and the Heart Rate graph SHALL appear directly above the Heart Rate Zone Distribution card.
* **Criterion 2 (Workout Summary Card Graph Sequence)**:
  * *Given* an athlete viewing a workout card in `WorkoutSummary` with `showTelemetryCharts` and `showZoneAnalysis` enabled,
  * *When* the card renders on screen,
  * *Then* the Speed/Pace graph SHALL be rendered above the Heart Rate graph.
* **Criterion 3 (Absence Omission)**:
  * *Given* a workout recorded without Heart Rate (e.g. GPS speed only),
  * *When* rendered in `MapDetailLayout` or `WorkoutSummary`,
  * *Then* the Speed/Pace graph SHALL render, while the Heart Rate and Power graphs are omitted with zero extra padding.

### 1.4 System Invariants
1. **Synchronized Scrubbing**: `selectedDistance` remains shared across all displayed graphs and the map marker.
2. **Sport Type Selection**: Running sports display Pace (`min/km`), while other sports display Speed (`km/h`).
3. **Localization Parity**: Graph heading string resources (`graph_heading_speed`, `graph_heading_pace`, `graph_heading_heart_rate`, `graph_heading_power`) remain unchanged and present across all 9 locales.
4. **Zero Regression**: Full test suite (`./gradlew testDebugUnitTest`) maintains 100% pass rate.

---

## 2. Test Specification (TST-UI-168)

### Test Case 1: `testMapDetailLayout_telemetryGraphOrdering_speedPrecedesHeartRate` (`[TST-UI-168.1]`)
* **Scope**: Visual & Structural Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Preconditions**: `MapDetailLayout.kt` exists.
* **Action**: Parse `MapDetailLayout.kt` AST/content and find indices of Speed/Pace graph block and Heart Rate graph block under `showZoomControls`.
* **Expected Result**: Index of `hasSpeedData` / `graph_heading_speed` precedes index of `hasHeartRateData` / `graph_heading_heart_rate`.

### Test Case 2: `testWorkoutSummary_telemetryGraphOrdering_speedPrecedesHeartRate` (`[TST-UI-168.2]`)
* **Scope**: Visual & Structural Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphTest.kt`
* **Preconditions**: `WorkoutSummary.kt` exists.
* **Action**: Parse `WorkoutSummary.kt` AST/content and find indices of Speed/Pace graph block and Heart Rate graph block under `showTelemetryCharts`.
* **Expected Result**: Index of `hasSpeedData` precedes index of `hasHeartRateData`.

### Test Case 3: Localization & Formatting Parity (`[TST-UI-168.3]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricLocalizationTest.kt`
* **Preconditions**: 9 locale `strings.xml` resource files exist.
* **Action**: Run `TelemetryMetricLocalizationTest`.
* **Expected Result**: 100% parity across all 9 locales for all graph heading strings.

### Test Case 4: Clean-Room Regression Suite (`[TST-UI-168.4]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-168.1]` | Contract | `MapDetailLayoutTest.testMapDetailLayout_telemetryGraphOrdering_speedPrecedesHeartRate` | `REQ-UI-214` | Specified |
| `[TST-UI-168.2]` | Contract | `TelemetryMetricGraphTest.testWorkoutSummary_telemetryGraphOrdering_speedPrecedesHeartRate` | `REQ-UI-214` | Specified |
| `[TST-UI-168.3]` | Localization | `TelemetryMetricLocalizationTest` | `REQ-UI-214`, `REQ-UI-106` | Specified |
| `[TST-UI-168.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
