# Stage 2: Requirement & Test Specification - ATT-2129: Telemetry Graphs and Zone Cards in Workout Summary Do Not Open Workout Details on Click

**Ticket**: [ATT-2129](https://rainerblind.atlassian.net/browse/ATT-2129)  
**Sub-task**: [ATT-2133](https://rainerblind.atlassian.net/browse/ATT-2133) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2129`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal System Requirement: REQ-UI-247

### Title
**Aftermath/Summary: Click-to-Open Navigation and Gesture Disambiguation for Telemetry Graphs and Zone Distribution Cards in WorkoutSummary.**

### Description
The system SHALL provide seamless click-to-open navigation (`onMapClick()`) across all telemetry graph sections and zone distribution cards in `WorkoutSummary.kt`, while ensuring gesture transparency and smooth vertical scrolling (ATT-2129):

1. **`TelemetryMetricGraph` Gesture Gating (`TelemetryMetricGraph.kt`)**:
   - `TelemetryMetricGraph` SHALL declare an optional boolean parameter `enableGestures: Boolean = true`.
   - When `enableGestures == true`, the underlying `Canvas` SHALL attach `.pointerInput(...)` for horizontal scrubbing and pan gestures.
   - When `enableGestures == false`, the `Canvas` SHALL omit `.pointerInput(...)`, rendering as a passive graphical canvas that propagates pointer touch events upwards to parent click modifiers without intercepting or consuming tap events.

2. **Telemetry Section Click-to-Open Navigation (`WorkoutSummary.kt`)**:
   - In `WorkoutSummary.kt`, each telemetry metric preview block (Speed/Pace, Heart Rate, Cycling Power) consisting of its header `Text` and `TelemetryMetricGraph` SHALL be wrapped in a container possessing `mapClickModifier` (`Modifier.clickable { if (workoutData.headerData.finished) onMapClick() }`).
   - Each `TelemetryMetricGraph` in `WorkoutSummary.kt` SHALL be invoked with `enableGestures = false`.
   - A tap anywhere on the telemetry title, graph canvas, or surrounding section bounds SHALL reliably invoke `onMapClick()`.

3. **Zone Distribution Card Click-to-Open Navigation (`WorkoutSummary.kt`)**:
   - In `WorkoutSummary.kt`, `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL receive `mapClickModifier` chained to their layout modifier (`.then(mapClickModifier)`).
   - Tapping on the zone card header, column chart, or card surface SHALL invoke `onMapClick()`.
   - Taps on the segmented button mode switcher (if present) SHALL be consumed by the inner button row without triggering navigation.

4. **Preservation of Core Invariants**:
   - Full interactive scrubbing and zooming in `MapDetailLayout.kt` (detailed workout view) SHALL remain 100% operational with default `enableGestures = true`.
   - Vertical scrolling of the workout summary list (`WorkoutSummariesTabbedScreen`) SHALL remain completely fluid without touch slop conflicts.
   - 9-language translation parity across all headings and labels SHALL remain 100% intact.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-247`), refining `REQ-UI-205` (*Slotted Analytics Architecture*) and `REQ-UI-206` (*Telemetry Metric Graphs*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (Commit `e7a6a273`, `ATT-1391`) introduced `TelemetryMetricGraph` and summary list previews.
3. *Root Reason for Existing Formulation*: In `ATT-1391`, telemetry graphs were added to `WorkoutSummary.kt` as visual previews. The author omitted `mapClickModifier` and did not gate `pointerInput` on the canvas.
4. *Preservation of Core Invariants*: Interactive scrubbing in `MapDetailLayout`, multi-chart cursor synchronization, 5-zone histogram toggling, and 9-language localization parity are 100% strictly preserved.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Telemetry Graph Tap Opens Details)**:
  - *Given* an athlete viewing a workout summary list card with telemetry graphs enabled,
  - *When* the athlete taps anywhere on the Speed/Pace, Heart Rate, or Power graph or its title,
  - *Then* `onMapClick()` SHALL be triggered and the detailed workout view SHALL open.
- **AC-2 (Zone Distribution Card Tap Opens Details)**:
  - *Given* an athlete viewing a workout summary list card with zone distribution cards enabled,
  - *When* the athlete taps on the Heart Rate or Power zone card,
  - *Then* `onMapClick()` SHALL be triggered and the detailed workout view SHALL open.
- **AC-3 (Interactive Scrubbing Preserved in Details)**:
  - *Given* an athlete in the detailed workout view (`MapDetailLayout`),
  - *When* dragging across the telemetry graphs,
  - *Then* interactive scrubbing and the floating telemetry badge SHALL function with zero degradation.
- **AC-4 (Fluid Vertical Scrolling in List)**:
  - *Given* the workout summary list with telemetry graphs visible,
  - *When* the athlete performs vertical fling or scroll gestures over the graphs,
  - *Then* the list SHALL scroll smoothly without gesture stalls or unintended navigation.

---

## 2. Formal Test Specification: TST-UI-206

### Test Cases

#### TST-UI-206.1: Structural Contract Test for `TelemetryMetricGraph` Parameter Gating
- **Test File**: `TelemetryMetricGraphGesturesContractTest.kt`
- **Method**: Verify that `TelemetryMetricGraph` declares `enableGestures: Boolean = true` as an optional parameter with default `true`.
- **Method**: Verify that the `Canvas` pointer input modifier is conditionally applied based on `enableGestures`.

#### TST-UI-206.2: Structural Contract Test for `WorkoutSummary` Click Wiring
- **Test File**: `WorkoutSummaryClickContractTest.kt`
- **Method**: Verify that `WorkoutSummary.kt` applies `mapClickModifier` to Speed/Pace, Heart Rate, and Power graph containers.
- **Method**: Verify that `WorkoutSummary.kt` passes `enableGestures = false` to all `TelemetryMetricGraph` invocations.
- **Method**: Verify that `WorkoutSummary.kt` passes `mapClickModifier` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.

#### TST-UI-206.3: Detailed View Regression & Invariant Test
- **Test File**: `MapDetailLayoutTest.kt`, `TelemetryMetricGraphGestureTest.kt`
- **Method**: Verify that `MapDetailLayout` continues to invoke `TelemetryMetricGraph` with default `enableGestures = true` and that gesture disambiguation passes 100%.

#### TST-UI-206.4: 9-Language Localization Audit
- **Test File**: `TranslationParityTest.kt`
- **Method**: Verify that all string resources used in `WorkoutSummary` graph headings (`graph_heading_speed`, `graph_heading_pace`, `graph_heading_heart_rate`, `graph_heading_power`, `aftermath_hr_zones_title`, `aftermath_power_zones_title`) are 100% translated across all 9 supported locales.

#### TST-UI-206.5: Clean-Room Full Suite Regression Execution
- **Method**: Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
