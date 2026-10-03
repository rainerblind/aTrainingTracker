# Stage 2 Requirement & Test Specification: ATT-2170 - Place Lap Splits Between Extrema and Strava/Map in Detailed Workout View to Match Summary Order

**Ticket**: [ATT-2170](https://rainerblind.atlassian.net/browse/ATT-2170)  
**Sub-task**: [ATT-2172](https://rainerblind.atlassian.net/browse/ATT-2172) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal Requirements

### REQ-UI-252: Aftermath/Details: Lap Split Visualizer Upper Metadata Slotting & Structural Parity in Detailed Workout View
The system SHALL ensure that the lap split summary (`LapSplitVisualizerCard`) in the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) maintains 1:1 structural order parity with the workout list summary (`WorkoutSummary.kt`) (ATT-2170):
1. *Upper Metadata Slotting (`metadataContent`)*:
   - `TrackOnMapScreen.kt` SHALL render `LapSplitVisualizerCard` within the `metadataContent` slot above the map viewport container (`BoxWithConstraints`).
   - `LapSplitVisualizerCard` SHALL be placed immediately following `WorkoutExtrema` and prior to `StravaActivitySection`.
2. *Preference & Data Guards*:
   - `LapSplitVisualizerCard` SHALL only be rendered when `activeDetailPrefs.showLaps == true` AND `splitChartData != null`.
3. *Analytics Slot Cleanup*:
   - `LapSplitVisualizerCard` SHALL NOT be rendered inside `analyticsContent`, eliminating duplicate rendering below telemetry graphs.
4. *Canonical Section Order Parity*:
   - In both `WorkoutSummary.kt` and `TrackOnMapScreen.kt`, the canonical section order SHALL be:
     1. Header (`WorkoutHeader`)
     2. Description & Notes (`WorkoutDescription`)
     3. Main Details
     4. Extrema (`WorkoutExtrema`)
     5. Laps (`LapSplitVisualizerCard`)
     6. Strava (`StravaActivitySection`)
     7. Map (`PathPreviewMap` / `MapDetailLayout` viewport)
     8. Telemetry Graphs (`TelemetryMetricGraph` - Speed, HR, Power)
     9. Zone Distribution Cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`)
5. *Preserved Invariants*:
   - Interactive lap tapping SHALL continue to update `selectedLapNr` and highlight the corresponding segment on the map below via `LapSegmentUtils.sliceLapSegment`.
   - Collapsing behavior via `CollapsingAppBarNestedScrollConnection` (`REQ-UI-250`) SHALL smoothly slide the lap card off-screen during upward scrolling.
   - Custom `metadataContent` callers (when `metadataContent != null`) retain complete control over slot rendering.
   - 9-language translation parity MUST NOT be broken.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-252`), refining `REQ-UI-245` (*Upper Metadata Slot Architecture*), `REQ-UI-248` (*Strava Activity Upper Slotting*), and `REQ-UI-250` (*Collapsing Upper Metadata Architecture*) under Epic `ATT-111`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2030`) decoupled detail screen preferences. In `ATT-2112`, description and extrema were moved above the map. In `ATT-2137`, Strava was moved above the map. Laps remained at the bottom of `analyticsContent`.
3. *Root Reason for Existing Formulation*: `LapSplitVisualizerCard` was placed in `analyticsContent` because early aftermath iterations had only one dynamic slot below the map. Now that `metadataContent` exists and is collapsible, placing laps between Extrema and Strava restores complete canonical parity with `WorkoutSummary.kt`.
4. *Preservation of Core Invariants*: Backward compatibility for callers with default `metadataContent = null`, preference gating, interactive lap highlighting on the map, and 9-language translation parity are 100% strictly preserved.

### Acceptance Criteria (Given-When-Then)
* **AC-1 (Upper Metadata Slotting)**:
  * *Given* a workout with recorded laps and `showLaps` enabled in detail preferences,
  * *When* viewing the detailed workout screen (`MapDetailLayout`),
  * *Then* `LapSplitVisualizerCard` SHALL render in `metadataContent` above the map, positioned immediately after `WorkoutExtrema` and before `StravaActivitySection`.
* **AC-2 (Analytics Slot Cleanup)**:
  * *Given* the detailed workout screen,
  * *When* scrolling through `analyticsContent` below the map and telemetry graphs,
  * *Then* `LapSplitVisualizerCard` SHALL NOT be rendered in `analyticsContent`.
* **AC-3 (Interactive Map Lap Highlighting)**:
  * *Given* a visible `LapSplitVisualizerCard` in `metadataContent` above the map,
  * *When* the athlete taps a lap bar,
  * *Then* `selectedLapNr` updates and the corresponding lap segment is highlighted on the map.

---

## 2. Test Specification

### TST-UI-211: Aftermath/Details: Lap Split Visualizer Upper Metadata Slotting & Structural Parity Verification
* **Requirement**: `REQ-UI-252`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenLapRoutingContractTest.kt`
* **Test Cases**:
  1. *Routing Contract Test*:
     - Assert `TrackOnMapScreen.kt` contains `LapSplitVisualizerCard` inside `metadataContent`.
     - Assert `LapSplitVisualizerCard` in `metadataContent` appears after `WorkoutExtrema` and before `StravaActivitySection`.
     - Assert `LapSplitVisualizerCard` does NOT appear inside `analyticsContent`.
  2. *Preference Guard Verification*:
     - Assert `LapSplitVisualizerCard` is guarded by `activeDetailPrefs.showLaps && splitChartData != null`.
  3. *Full Clean-Room Regression*:
     - `./gradlew testDebugUnitTest` passes with zero failures.
