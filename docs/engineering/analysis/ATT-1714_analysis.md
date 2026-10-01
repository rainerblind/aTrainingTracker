# Stage 1 Analysis: ATT-1714 - Configurable Sections in Detailed Workout Cards

**Ticket**: [ATT-1714](https://atrainingtracker.atlassian.net/browse/ATT-1714)  
**Sub-task**: [ATT-1800](https://atrainingtracker.atlassian.net/browse/ATT-1800) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1714`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

In the Aftermath workout list ([WorkoutList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt)), each item rendered in detailed view ([WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)) displays a fixed, hardcoded sequence of sections:
1. **Header** ([WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt)): Title, date, sport, equipment, actions menu.
2. **Description** ([WorkoutDescription.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdescription/WorkoutDescription.kt)): Notes, goal, method.
3. **Details** ([WorkoutDetails.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetails.kt)): Primary metrics (distance, time, speed/pace).
4. **Extrema** ([WorkoutExtrema.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutextrema/WorkoutExtrema.kt)): Extrema table (max speed, avg/max HR, power, cadence, etc.).
5. **Laps** ([WorkoutLaps.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt)): Lap split rows with interactive editing.
6. **Strava** ([StravaActivitySection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt)): Strava sync status and segment efforts.
7. **Media Section**: 300dp vertical column coupling [PathPreviewMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt) (GPS route) and [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) (elevation profile).
8. **Export Status** ([ExportStatus.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatus.kt)): Export badges.

Athletes have diverse preferences regarding card density and visual analytics:
- **Minimalist Athletes**: Wish to eliminate large 300dp map previews or lengthy lap lists to maximize the number of visible workouts on screen without switching to the ultra-bare compact mode.
- **Data-Driven Athletes**: Want rich visual analytics directly on the journal card—specifically requesting the ability to inspect continuous **Telemetry Charts** (Heart Rate, Power, Speed/Pace time-series curves introduced in ATT-1740) and **Zone Analysis** (5-zone time distributions for HR and Power introduced in ATT-1739) right within the journal list without tapping into sub-screens.

Allowing athletes to toggle section visibility empowers them to tailor the workout journal precisely to their analytical priorities.

---

## 2. Root Cause & Architectural Gap Analysis

### 2.1 Current State Analysis
- **Coupled Visibility**: Section visibility currently depends solely on data presence (e.g. `workoutData.extremaData.dataRows.isNotEmpty()`, `isPlayServiceAvailable && workoutData.mapPolyline != ""`). Athletes have no preference switches to hide unwanted sections.
- **Rigid Media Section Coupling**: In `WorkoutSummary.kt`, `PathPreviewMap` and `ElevationProfile` are bundled in a fixed 300dp column (`WorkoutMediaSection`). If a user wants to view elevation without a map preview, or vice versa, the layout has no decoupled presentation mode.
- **Missing Telemetry & Zone Integration**: Reusable composables for continuous curves ([TelemetryMetricGraph.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt)) and zone distribution ([HeartRateZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt), [PowerZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt)) exist in the codebase, but are currently only rendered inside `TrackOnMapScreen`. They have not been integrated into `WorkoutSummary.kt`.
- **Data Confinement & Lazy Loading**: `WorkoutData` contains metadata, polylines, and extrema summaries, but intentionally omits raw track points (`PathPoint`) and zone distributions to keep the in-memory list lightweight. Loading full sample tables for every workout in the list eagerly would consume excessive memory and degrade scroll performance. Therefore, telemetry charts and zone distribution in `WorkoutSummary` must be loaded asynchronously and lazily on demand when visible.

### 2.2 Settings Configuration Gap
- [DisplaySettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt) manages cockpit orientation, screen wake locks, cockpit theme mode, and brightness modes. It currently lacks a configuration category for customizing workout journal cards.
- [MyPreferenceManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt) manages `IS_COMPACT_VIEW`, marker types, and filter criteria JSON, but does not yet store granular card section visibility preferences.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
1. **Section Preferences Model (`WorkoutCardSectionPreferences`)**:
   Define an immutable data class with 8 configurable section toggles:
   - `showDescription: Boolean = true`
   - `showExtrema: Boolean = true`
   - `showLaps: Boolean = true`
   - `showStrava: Boolean = true`
   - `showMapPreview: Boolean = true`
   - `showElevationProfile: Boolean = true`
   - `showTelemetryCharts: Boolean = false` (default false for clean density)
   - `showZoneAnalysis: Boolean = false` (default false for clean density)
2. **DataStore Persistence in `MyPreferenceManager`**:
   Store preferences reactively in `dataStore` with individual boolean keys or a JSON snapshot, exposing `workoutCardPreferencesFlow: Flow<WorkoutCardSectionPreferences>` and update methods.
3. **ViewModel Integration in `WorkoutSummariesViewModel`**:
   Expose `workoutCardPreferences: StateFlow<WorkoutCardSectionPreferences>` to the UI hierarchy.
4. **Decoupled & Conditional Rendering in `WorkoutSummary.kt`**:
   - Condition each optional section (`WorkoutDescription`, `WorkoutExtrema`, `WorkoutLaps`, `StravaActivitySection`) on its respective preference.
   - Decouple map and elevation profile: render map preview independently if `showMapPreview` is true; render elevation profile independently if `showElevationProfile` is true; render combined layout when both are true; omit cleanly when both are false.
   - When `showTelemetryCharts` is true, lazily load `PathPoint`s on `Dispatchers.IO` for the visible card and render `TelemetryMetricGraph` (HR, Speed/Pace, Power).
   - When `showZoneAnalysis` is true, lazily load `ZoneDistributionData` on `Dispatchers.IO` for the visible card and render `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
5. **Settings UI Integration in `DisplaySettingsDialog.kt`**:
   Add a structured group under *"Trainingsliste (Detail-Karten)"* / *"Workout List (Detailed Cards)"* with intuitive switches for all 8 configurable sections.
6. **Localization & Parity**:
   Define all section toggle labels and descriptions across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 100% translation parity.

### Out-of-Scope Non-Goals (Scope Bounding)
1. **Compact Mode (`WorkoutSummaryCompact.kt`) Customization**: Compact mode is designed as a minimalist single-line summary; its layout remains untouched.
2. **Section Reordering / Drag-and-Drop**: The visual vertical sequence of sections remains standardized and stable.
3. **Edit Workout Dialog Configurable Fields**: Dedicated exclusively to companion ticket [ATT-1713](https://atrainingtracker.atlassian.net/browse/ATT-1713).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-210`, extending Epic [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*), [ATT-850](https://atrainingtracker.atlassian.net/browse/ATT-850) (*Detailed Workout Card Architecture*), [ATT-1739](https://atrainingtracker.atlassian.net/browse/ATT-1739) (*Zone Column Distributions*), and [ATT-1740](https://atrainingtracker.atlassian.net/browse/ATT-1740) (*Continuous Telemetry Graphs*).
* **Historical Origin & Commit Trace**: `WorkoutSummary.kt` was created to provide an all-in-one comprehensive overview of completed sessions. Feedback from athletes across sprints requested granular control over card height and the inclusion of post-workout curves directly in the list.
* **Root Reason for Existing Formulation**: Sections were previously rendered whenever data was non-null because initial development prioritized feature completeness over user-customizable layout density.
* **Preservation of Core Invariants**:
  - `WorkoutHeader` and `WorkoutDetails` remain mandatory anchors on every card, ensuring workout identity, date, duration, and distance are always visible.
  - Card touch interactions (`onMapClick`, `onEditWorkout`, `onExport`, `onSaveAsRoute`, `onDeleteRequest`) remain 100% operational.
  - Asynchronous background loading on `Dispatchers.IO` ensures zero UI thread stutter or frame drops during list scrolling.
  - 100% 9-language localization parity maintained across all new string resources.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Component Architecture

```mermaid
graph TD
    subgraph Storage
        DS["MyPreferenceManager (DataStore)"]
    end

    subgraph Settings
        DSD["DisplaySettingsDialog.kt"] -->|Update Toggles| DS
    end

    subgraph UI Architecture
        WSVM["WorkoutSummariesViewModel"] -->|workoutCardPreferencesFlow| WTS["WorkoutTabsScreen.kt"]
        WTS --> WL["WorkoutList.kt"]
        WL --> WS["WorkoutSummary.kt"]
        
        WS -->|showDescription| WD["WorkoutDescription"]
        WS -->|showExtrema| WE["WorkoutExtrema"]
        WS -->|showLaps| WLaps["WorkoutLaps"]
        WS -->|showStrava| WS_Strava["StravaActivitySection"]
        WS -->|showMapPreview| WMap["PathPreviewMap"]
        WS -->|showElevationProfile| WEl["ElevationProfile"]
        WS -->|showTelemetryCharts (lazy)| WTMG["TelemetryMetricGraph (HR, Speed, Power)"]
        WS -->|showZoneAnalysis (lazy)| WZD["ZoneDistributionCards (HR & Power)"]
    end
```

### 5.2 Performance & Lazy Loading Invariant
To ensure flawless 60/120fps scrolling in `LazyColumn`:
- `showTelemetryCharts` and `showZoneAnalysis` trigger a `produceState` coroutine tied to `workoutData.id`.
- If the card scrolls off-screen, the coroutine is automatically cancelled by Compose lifecycle.
- Queries are strictly dispatched on `Dispatchers.IO` through `WorkoutRepository`.

---

## 6. Verification Plan & Test Strategy

1. **Preference DataStore & Serialization Unit Tests (`WorkoutCardSectionPreferencesTest.kt`)**:
   - Verify defaults: all legacy sections true, telemetry charts and zones false.
   - Verify toggling each individual section persists and round-trips accurately.
2. **WorkoutSummary Visual Contract Unit Tests (`WorkoutSummarySectionsTest.kt`)**:
   - Verify that disabling each section omits its corresponding composable.
   - Verify that enabling telemetry charts and zone analysis triggers rendering when data is available.
   - Verify decoupled map vs elevation profile rendering modes.
3. **9-Language Localization Audit (`WorkoutCardSettingsLocalizationTest.kt`)**:
   - Verify all 8 section toggle labels and descriptions exist across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
4. **Clean-Room Full Suite Regression Execution**:
   - `./gradlew testDebugUnitTest` across all modules verifying 0 test failures.
