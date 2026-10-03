# Stage 1 Analysis: ATT-2170 - Place Lap Splits Between Extrema and Strava/Map in Detailed Workout View to Match Summary Order

**Ticket**: [ATT-2170](https://rainerblind.atlassian.net/browse/ATT-2170)  
**Sub-task**: [ATT-2171](https://rainerblind.atlassian.net/browse/ATT-2171) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2170`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & User Mandate

During the Sprint Review of `Sprint 2026-40.13`, the user identified a structural layout inconsistency between the workout list card summary (`WorkoutSummary.kt`) and the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`):
> *"Beim Testen ist mir aber aufgefallen, dass die Runden-Übersicht in der Detail Ansicht ganz unten platziert wird. In der Summary sind sie jedoch zwischen den Extremwerten und der Karte. Kannst du die Reihenfolge in der Detail-Ansicht so anpassen, dass sie der Reihenfolge in der Summary entspricht. Bitte dieses Ticket dann gleich im Rahmen des aktuellen Sprints umsetzen."*

### Canonical Section Order in `WorkoutSummary.kt`:
1. Header
2. Description & Notes (`WorkoutDescription`)
3. Main Details
4. Extrema (`WorkoutExtrema`)
5. **Laps (`LapSplitVisualizerCard`)**
6. **Strava Achievements (`StravaActivitySection`)**
7. Map Preview (`PathPreviewMap`)
8. Telemetry Graphs (`TelemetryMetricGraph` - Speed, HR, Power)
9. Zone Distribution Cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`)

### Current Inconsistency in `TrackOnMapScreen.kt`:
In `TrackOnMapScreen.kt`, `metadataContent` (above the map) contains:
- `WorkoutDescription`
- `WorkoutExtrema`
- `StravaActivitySection`
While `LapSplitVisualizerCard` is slotted inside `analyticsContent` (below the map and below all telemetry graphs and zone cards). This breaks cognitive consistency across views.

---

## 2. Root Cause Analysis

1. **Historical Slot Allocation**:
   Earlier aftermath sprints introduced `analyticsContent` as a single bucket below the map for all non-map items (laps, zone cards).
   When `ATT-2112` and `ATT-2137` relocated Description, Extrema, and Strava above the map into `metadataContent`, `LapSplitVisualizerCard` remained untouched at the bottom of `analyticsContent`.
2. **Missing Upper Slot Migration**:
   `LapSplitVisualizerCard` was not migrated to `metadataContent`. Placing it between `WorkoutExtrema` and `StravaActivitySection` in `metadataContent` achieves complete 1:1 order parity with `WorkoutSummary.kt`.
3. **Collapsible Architecture Benefit**:
   Because `ATT-2150` established the `CollapsingAppBarNestedScrollConnection` for `metadataContent`, placing `LapSplitVisualizerCard` above the map does not squash the map. When the athlete scrolls, the upper section smoothly collapses, preserving the full screen for map and analytics.

---

## 3. Scope & Target Architecture

1. **`TrackOnMapScreen.kt`**:
   - In `metadataContent`:
     - Render `LapSplitVisualizerCard` when `activeDetailPrefs.showLaps == true && splitChartData != null`, placed immediately after `WorkoutExtrema` and prior to `StravaActivitySection`.
   - In `analyticsContent`:
     - Remove `LapSplitVisualizerCard` completely, ensuring no duplicate rendering.
2. **Interactive Lap Highlighting**:
   - `selectedLapNr` and `onLapClick` remain bound to `LapSegmentUtils.sliceLapSegment`, guaranteeing that tapping a lap in the upper card still highlights the corresponding path segment on the map below.

---

## 4. Requirement Archaeology & Chesterton's Fence

1. **Origin**: `ATT-1888` (Lap split visualizer), `ATT-2030` (Decoupled detail preferences), `ATT-2112` (Upper metadata slot), `ATT-2137` (Upper Strava slot).
2. **Fence Assessment**: `LapSplitVisualizerCard` was placed in `analyticsContent` purely because an upper metadata slot did not exist at the time of its initial implementation. Moving it above the map aligns with the canonical layout of `WorkoutSummary.kt` without violating any invariants.
3. **Invariants Preserved**:
   - `activeDetailPrefs.showLaps` preference gating is strictly maintained.
   - Interactive lap selection / map highlight synchronization is 100% preserved.
   - Custom `metadataContent` callers (when `metadataContent != null`) remain untouched.
   - 9-language translation parity maintained.

---

## 5. Acceptance Criteria (Given-When-Then)

* **AC-1 (Canonical Order in Detailed Workout View)**:
  * **Given** an athlete opening a workout with recorded laps and `showLaps` enabled in detail preferences,
  * **When** viewing the detailed workout screen (`MapDetailLayout`),
  * **Then** `LapSplitVisualizerCard` SHALL render in `metadataContent` above the map, positioned immediately after `WorkoutExtrema` and before `StravaActivitySection`.
* **AC-2 (Analytics Slot Cleanup)**:
  * **Given** the detailed workout screen,
  * **When** scrolling through `analyticsContent` below the map and telemetry graphs,
  * **Then** `LapSplitVisualizerCard` SHALL NOT be rendered in `analyticsContent`.
* **AC-3 (Interactive Map Lap Highlighting)**:
  * **Given** a visible `LapSplitVisualizerCard` above the map,
  * **When** the athlete taps a lap bar,
  * **Then** `selectedLapNr` updates and the corresponding lap segment is highlighted on the map.
