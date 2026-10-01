# Stage 2 Requirement & Test Specification: ATT-1714

## 1. Ticket & Metadata
- **Parent Ticket**: [ATT-1714](https://atrainingtracker.atlassian.net/browse/ATT-1714) - `[Feature] [Aftermath/WorkoutSummary] Configurable Sections in Detailed Workout Cards (Charts, Zones, Map, Elevation, Extrema)`
- **Subtask**: [ATT-1801](https://atrainingtracker.atlassian.net/browse/ATT-1801) - `Stage 2: Requirement & Test Specification`
- **Target Branch**: `feature/ATT-1714`
- **Target Version**: `V4.9.38`
- **Author**: AI Agent 1 (Implementer)
- **Date**: 2026-10-01

---

## 2. Requirement Specification (`REQ-UI-210`)

### REQ-UI-210: Aftermath: Configurable Sections in Detailed Workout Cards (Charts, Zones, Map, Elevation, Extrema)
The system SHALL provide athlete-configurable section visibility for detailed workout journal cards in Aftermath ([WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt)), enabling athletes to tailor card vertical height and visual analytics density directly from display settings (ATT-1714):

1. **Configurable Preferences Model (`WorkoutCardSectionPreferences`)**:
   - The system SHALL define an immutable data structure `WorkoutCardSectionPreferences` containing 8 boolean toggles:
     - `showDescription: Boolean = true`
     - `showExtrema: Boolean = true`
     - `showLaps: Boolean = true`
     - `showStrava: Boolean = true`
     - `showMapPreview: Boolean = true`
     - `showElevationProfile: Boolean = true`
     - `showTelemetryCharts: Boolean = false` (default false for clean density)
     - `showZoneAnalysis: Boolean = false` (default false for clean density)
   - The system SHALL persist these preferences in `MyPreferenceManager` (DataStore) with atomic reading/updating and expose `workoutCardPreferencesFlow: Flow<WorkoutCardSectionPreferences>`.

2. **Decoupled & Conditional Section Rendering (`WorkoutSummary.kt`)**:
   - `WorkoutDescription` SHALL be rendered if and only if `preferences.showDescription` is true and description data is non-blank.
   - `WorkoutExtrema` SHALL be rendered if and only if `preferences.showExtrema` is true and extrema rows are non-empty.
   - `WorkoutLaps` SHALL be rendered if and only if `preferences.showLaps` is true and laps are non-empty.
   - `StravaActivitySection` SHALL be rendered if and only if `preferences.showStrava` is true and Strava activity JSON is non-blank.
   - The Media section SHALL decouple map preview and elevation profile:
     - If both `showMapPreview` and `showElevationProfile` are true: render combined map and elevation layout.
     - If only `showMapPreview` is true: render `PathPreviewMap` independently without empty elevation spacing.
     - If only `showElevationProfile` is true: render `ElevationProfile` independently without map rendering.
     - If both are false: omit the media section completely with zero vertical whitespace consumption.
   - When `preferences.showTelemetryCharts` is true:
     - The composable SHALL lazily load `PathPoint` track telemetry on `Dispatchers.IO` for the visible card.
     - When available, render `TelemetryMetricGraph` for Heart Rate, Speed/Pace, and Power where sensor data is present.
   - When `preferences.showZoneAnalysis` is true:
     - The composable SHALL lazily load `ZoneDistributionData` on `Dispatchers.IO` for the visible card.
     - When available, render `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` where zone data is present.

3. **Settings UI Integration (`DisplaySettingsDialog.kt`)**:
   - `DisplaySettingsDialog.kt` SHALL provide a structured configuration category titled *"Trainingsliste (Detail-Karten)"* / *"Workout List (Detailed Cards)"*.
   - The category SHALL present intuitive toggle switches for all 8 configurable sections with clear localized labels.
   - Saving settings SHALL persist all modified preferences atomically and update the workout list reactively.

4. **100% 9-Language Localization Parity**:
   - All section toggle titles and descriptions SHALL be defined across all 9 supported locales: English (`values`), German (`values-de`), Spanish (`values-es`), French (`values-fr`), Italian (`values-it`), Japanese (`values-ja`), Dutch (`values-nl`), Polish (`values-pl`), Portuguese (`values-pt`).

5. **Preservation of Core Invariants**:
   - `WorkoutHeader` and `WorkoutDetails` SHALL remain mandatory anchors on every card and are NOT removable.
   - All card interactions (`onMapClick`, `onEditWorkout`, `onExport`, `onSaveAsRoute`, `onDeleteRequest`, lap editing) SHALL remain 100% operational.
   - Compact mode (`WorkoutSummaryCompact.kt`) SHALL remain untouched.
   - Asynchronous lazy queries on `Dispatchers.IO` SHALL preserve 60/120fps scrolling in `LazyColumn`.

### Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. **Original Requirement ID & Target**: Net-new requirement only (`REQ-UI-210`), extending Epic `ATT-355` (*Good and consistent UI*), `REQ-UI-085` (`WorkoutSummary` foundation), `REQ-UI-206` (telemetry graphs), and `REQ-UI-207` (zone cards).
2. **Historical Origin & Commit Trace**: `WorkoutSummary.kt` previously rendered sections whenever data was present. User feedback requested granular toggles to customize card size and directly view telemetry curves and zone distributions in the list.
3. **Root Reason for Existing Formulation**: Sections were rendered unconditionally when data existed to showcase all features by default.
4. **Preservation of Core Invariants**: Mandatory header and details anchors, single-tap navigation, long-press context menu, and lazy loading performance are strictly preserved.

### Acceptance Criteria (Given-When-Then)
- **AC-1 (Section Visibility Toggles)**:
  - *Given* an athlete in `DisplaySettingsDialog`,
  - *When* disabling "Karten-Vorschau" (Map Preview) and saving,
  - *Then* workout cards in `WorkoutList` SHALL omit the map preview while retaining other enabled sections.
- **AC-2 (Independent Elevation Profile)**:
  - *Given* an athlete has disabled Map Preview but enabled Elevation Profile,
  - *When* viewing a workout with elevation data,
  - *Then* the elevation profile SHALL render cleanly without map placeholder space.
- **AC-3 (Telemetry Charts in Workout Card)**:
  - *Given* an athlete enables "Telemetrie-Diagramme" (Telemetry Charts) in settings,
  - *When* viewing a workout card with recorded heart rate, speed, or power,
  - *Then* the card SHALL render continuous metric curves with headings.
- **AC-4 (Zone Distribution in Workout Card)**:
  - *Given* an athlete enables "Zonenauswertung" (Zone Analysis) in settings,
  - *When* viewing a workout card with heart rate or power zones,
  - *Then* the card SHALL render the 5-zone distribution column charts.
- **AC-5 (Essential Anchors Invariant)**:
  - *Given* all 8 configurable sections are toggled off by the athlete,
  - *When* viewing workout cards in `WorkoutList`,
  - *Then* the card SHALL still render `WorkoutHeader` and `WorkoutDetails` cleanly.

---

## 3. Test Specification (`TST-UI-164`)

### TST-UI-164: Aftermath Detailed Workout Card Configurable Sections Verification

1. **Preference DataStore & Serialization Unit Tests (`WorkoutCardSectionPreferencesTest.kt`)**:
   - `testDefaultPreferencesMatchSpecification()`:
     - Verify default values: description, extrema, laps, strava, map, elevation are true; telemetry charts and zone analysis are false.
   - `testDataStoreReadWriteRoundTrip()`:
     - Verify that toggling individual preferences persists accurately to DataStore and emits updated state via `workoutCardPreferencesFlow`.
   - `testJsonSerializationRoundTrip()`:
     - Verify lossless JSON serialization and deserialization of `WorkoutCardSectionPreferences`.

2. **WorkoutSummary Visual Contract Unit Tests (`WorkoutSummarySectionsTest.kt`)**:
   - `testWorkoutSummaryRendersAllSectionsByDefault()`:
     - Verify that default preferences retain standard existing sections.
   - `testWorkoutSummarySuppressesDisabledSections()`:
     - Verify disabling description, extrema, laps, and strava omits the respective composables.
   - `testWorkoutSummaryDecoupledMediaRendering()`:
     - Verify map-only, elevation-only, both, and neither media rendering modes.
   - `testWorkoutSummaryPreservesHeaderAndDetailsAlways()`:
     - Verify `WorkoutHeader` and `WorkoutDetails` are always present even when all 8 toggles are false.

3. **9-Language Localization Parity Unit Tests (`WorkoutCardSettingsLocalizationTest.kt`)**:
   - Verify string tokens (`settings_workout_card_title`, `settings_workout_card_map`, `settings_workout_card_elevation`, `settings_workout_card_extrema`, `settings_workout_card_laps`, `settings_workout_card_strava`, `settings_workout_card_description`, `settings_workout_card_charts`, `settings_workout_card_zones`) exist across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with 0 regressions.

---

## 4. Traceability Matrix

| Requirement | Test Specification | Verification Method | Target File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-210` | `TST-UI-164` | Preference DataStore tests, Composable structural tests, 9-Language Localization Audit (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummarySectionsTest.kt`, `WorkoutCardSettingsLocalizationTest.kt`) | `WorkoutSummary.kt`, `MyPreferenceManager.kt`, `WorkoutSummariesViewModel.kt`, `DisplaySettingsDialog.kt`, `strings.xml` (all 9 locales) | Specified |
