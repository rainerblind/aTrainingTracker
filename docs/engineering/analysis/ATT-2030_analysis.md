# Stage 1 Analysis: ATT-2030 - [Settings/Aftermath] Matrix Table Presentation for Configurable Sections: Workout Summary List vs. Workout Details

**Ticket**: [ATT-2030](https://rainerblind.atlassian.net/browse/ATT-2030)  
**Sub-task**: [ATT-2087](https://rainerblind.atlassian.net/browse/ATT-2087) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Branch**: `feature/ATT-2030`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In Advanced Settings (`AdvancedTuningDialog.kt`), the aftermath section customization options are currently rendered as a flat vertical list of 8 toggle switches under a misleading heading:
* German: *"Trainingsliste (Detail-Karten)"*
* English: *"Workout List (Detailed Cards)"*

This design exhibits several structural deficiencies:
1. **Misleading Conceptual Scope**: The heading implies that the 8 toggles solely govern the scrolling journal cards in the main workout list (`WorkoutSummary.kt`). In reality, athletes expect a clear distinction between the **Workout Summary List** (which needs to be lightweight, responsive, and quickly scannable) and the **Workout Details** screen (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`, which serves as the deep-dive analytical view).
2. **Coupled Configuration Dilemma**: Athletes cannot independently configure list cards to be compact and fast (omitting heavy elevation profiles, telemetry charts, and zone bars) while enjoying full graphs and analytics in the detail screen—or vice versa.
3. **Cluttered & Unintuitive UI**: A flat vertical list of toggles fails to visually communicate the dual-surface nature of workout aftermath visualization.

### Proposed Solution: 2-Column Matrix Table UI
Reorganize Section 5 of Advanced Settings into a clean, compact **2-Column Matrix Table layout**:

| Feature / Element | In Workout Summary (Liste) | In Workout Details (Detailansicht) |
| :--- | :---: | :---: |
| **Beschreibung** (Description) | [x] *(default ON)* | [x] *(default ON)* |
| **Extremwerte** (Extrema values) | [x] *(default ON)* | [x] *(default ON)* |
| **Runden** (Laps table / visualizer) | [x] *(default ON)* | [x] *(default ON)* |
| **Strava-Status** (Sync badge) | [x] *(default ON)* | [x] *(default ON)* |
| **Karte** (Map thumbnail / Full map) | [x] *(default ON)* | [x] *(default ON)* |
| **Höhenprofil** (Elevation Profile) | [ ] *(default OFF)* | [x] *(default ON)* |
| **Telemetrie-Diagramme** (Pace/HR/Power) | [ ] *(default OFF)* | [x] *(default ON)* |
| **Trainingszonen** (Zone analysis bars) | [ ] *(default OFF)* | [x] *(default ON)* |

---

## 2. Forensic Investigation & Gap Analysis

### 2.1 Preferences Storage Layer (`MyPreferenceManager.kt`)
* Currently, `WorkoutCardSectionPreferences` stores:
  ```kotlin
  data class WorkoutCardSectionPreferences(
      val showDescription: Boolean = true,
      val showExtrema: Boolean = true,
      val showLaps: Boolean = true,
      val showStrava: Boolean = true,
      val showMapPreview: Boolean = true,
      val showElevationProfile: Boolean = true, // To be aligned to false for list cards
      val showTelemetryCharts: Boolean = false,
      val showZoneAnalysis: Boolean = false,
      val lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY
  )
  ```
* There is no existing preference model for `WorkoutDetailPreferences`.
* The detail screen (`TrackOnMapScreen.kt`) currently hardcodes certain elements or lacks independent toggle controls for description, extrema, laps, strava, map, elevation, charts, and zones.

### 2.2 Settings UI Layer (`AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`)
* In `AdvancedTuningDialog.kt`:
  - `WorkoutMasksAndCardsSection` renders a single column of 8 `TuningToggleItem` composables.
  - Section state collects only `workoutCardPreferencesFlow`.
  - Factory reset only resets `workoutCardPrefs`.
* In `AdvancedTuningAccordion.kt`:
  - `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` formats only active card count (`%1$d/8 Cards` / `%1$d/8 Karten`).
  - It needs modernization to reflect both list and detail counts: e.g. `"%1$d/8 Liste, %2$d/8 Details"` / `"%1$d/8 List, %2$d/8 Details"`.

### 2.3 Workout Details Layer (`TrackOnMapScreen.kt` & `MapDetailLayout.kt`)
* In `TrackOnMapScreen.kt`:
  - `WorkoutHeader` is rendered in `header`.
  - `showMap` is conditioned only on boolean flag and `hasGpsTrack`.
  - `showElevationProfile` is hardcoded to `hasGpsTrack && ...`.
  - In `analyticsContent`, HR zone distribution, Power zone distribution, and Lap split visualizer are rendered without toggle gating.
  - `WorkoutDescription`, `WorkoutExtrema`, and `StravaActivitySection` are currently not available in `TrackOnMapScreen`. Integrating `WorkoutDetailPreferences` allows athletes to optionally view or hide description, extrema, laps, strava, map, elevation, telemetry graphs, and zones in the detail screen.

---

## 3. DataStore Schema Migration Strategy & Backward Compatibility

To avoid resetting existing users' custom visibility settings upon upgrading to `V4.9.38`:

### 3.1 Preferences DataStore Keys & Evolution
1. **List Cards (`WorkoutListCardPreferences`)**:
   - Reuses existing DataStore keys (`workout_card_show_description`, `workout_card_show_extrema`, `workout_card_show_laps`, `workout_card_show_strava`, `workout_card_show_map`, `workout_card_show_elevation`, `workout_card_show_charts`, `workout_card_show_zones`, `workout_card_lap_display_mode`).
   - Default fallback for `workout_card_show_elevation` when key is absent in DataStore is updated to `false` for list cards, preserving scrolling performance and fast scannability.
   - For backward compatibility and API stability:
     ```kotlin
     typealias WorkoutListCardPreferences = WorkoutCardSectionPreferences
     ```
2. **Detail View (`WorkoutDetailPreferences`)**:
   - New dedicated DataStore keys:
     ```kotlin
     val WORKOUT_DETAIL_SHOW_DESCRIPTION = booleanPreferencesKey("workout_detail_show_description")
     val WORKOUT_DETAIL_SHOW_EXTREMA = booleanPreferencesKey("workout_detail_show_extrema")
     val WORKOUT_DETAIL_SHOW_LAPS = booleanPreferencesKey("workout_detail_show_laps")
     val WORKOUT_DETAIL_SHOW_STRAVA = booleanPreferencesKey("workout_detail_show_strava")
     val WORKOUT_DETAIL_SHOW_MAP = booleanPreferencesKey("workout_detail_show_map")
     val WORKOUT_DETAIL_SHOW_ELEVATION = booleanPreferencesKey("workout_detail_show_elevation")
     val WORKOUT_DETAIL_SHOW_CHARTS = booleanPreferencesKey("workout_detail_show_charts")
     val WORKOUT_DETAIL_SHOW_ZONES = booleanPreferencesKey("workout_detail_show_zones")
     ```
   - **Migration Fallback Rule**:
     When reading `workoutDetailPreferencesFlow`, if any `workout_detail_show_*` key is null/unset:
     - For `showMap`, `showElevationProfile`, `showTelemetryCharts`, and `showZoneAnalysis`: fallback to `true` (standard analytical detail view default).
     - For `showDescription`, `showExtrema`, `showLaps`, and `showStrava`: if the corresponding `workout_card_show_*` preference is explicitly set, inherit that value as a seamless transition; otherwise default to `true`.
     - This guarantees that existing user customizations are respected without silent state resets.

---

## 4. Call-Site Audit & API Contract Stability

An exhaustive audit of existing callers in the codebase:

| Caller / Consumer | Current Usage | Action in ATT-2030 |
| :--- | :--- | :--- |
| `MyPreferenceManager.kt` | Exposes `workoutCardPreferencesFlow`, `setWorkoutCardPreferences`, `WorkoutCardSectionPreferences` | Retain all existing methods; alias `WorkoutListCardPreferences = WorkoutCardSectionPreferences`; add `workoutDetailPreferencesFlow` and `setWorkoutDetailPreferences`. |
| `WorkoutSummary.kt` | Accepts `preferences: WorkoutCardSectionPreferences` | Unchanged call signature; continues consuming list preferences. Zero regression to list scrolling. |
| `WorkoutSummariesViewModel.kt` | Exposes `workoutCardPreferences: StateFlow<WorkoutCardSectionPreferences>` | Retain `workoutCardPreferences`; add `workoutDetailPreferences: StateFlow<WorkoutDetailPreferences>`. |
| `WorkoutTabsScreen.kt` | Passes `workoutCardPreferences` to `WorkoutList` | Unchanged; continues passing list preferences. |
| `WorkoutList.kt` | Renders `WorkoutSummary` with `workoutCardPreferences` | Unchanged; continues passing list preferences. |
| `WorkoutSummariesTabbedScreen.kt` | Collects `workoutCardPreferences` and renders `TrackOnMapScreen` | Pass `detailPreferences` to `TrackOnMapScreen`. |
| `TrackOnMapScreen.kt` | Renders map, elevation, and analytics | Accept `detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()`, honoring toggles for map, elevation, charts, zones, laps, extrema, and description. |
| `AdvancedTuningDialog.kt` | Section 5 renders toggles and manages `workoutCardPrefs` | Collect both `workoutCardPrefs` and `workoutDetailPrefs`; render 2-column matrix table; save and reset both models atomically. |
| `AdvancedTuningAccordion.kt` | Formats Section 5 subtitle via `formatWorkoutMasksSubtitle` | Provide updated `formatWorkoutMatrixSubtitle(listPrefs, detailPrefs, context)` displaying dual count (`%1$d/8 Liste, %2$d/8 Details`). Deprecate single-model overload. |

---

## 5. Chesterton's Fence Archaeology (REQ-PRO-022)

### 5.1 Historical Evolution Trace
1. **`REQ-UI-210` (ATT-1714, Sprint 2026-40.6)**: Introduced `WorkoutCardSectionPreferences` to allow athletes to hide or show individual sections in workout list cards.
2. **`REQ-UI-216` (ATT-1815, Sprint 2026-40.7)**: Relocated `WorkoutCardSectionPreferences` from `DisplaySettingsDialog.kt` to Advanced Settings (`AdvancedTuningDialog.kt`) to reduce clutter.
3. **`REQ-UI-239` (ATT-2029, Sprint 2026-40.12)**: Removed redundant edit workout input field toggles from Advanced Settings Section 5, narrowing Section 5 to focus on aftermath visualization.

### 5.2 Chesterton's Fence Findings & Invariants
* **Why does the existing code only configure list cards?**  
  Historically, aftermath customization focused solely on the scrolling list cards (`WorkoutSummary.kt`). The detail view (`TrackOnMapScreen.kt`) was treated as an all-inclusive inspector. However, user feedback revealed that athletes want control over both views, and specifically want list cards to remain snappy (omitting heavy elevation/telemetry/zones by default) while keeping detail views analytical.
* **Core Invariants to Preserve**:
  1. Existing DataStore keys for `workout_card_show_*` must remain intact and functional so that existing user settings are preserved.
  2. Factory reset must restore sensible defaults for both models atomically.
  3. 9-language localization parity across all supported application locales.

---

## 6. User Scope Grounding (ATT-1250)

### In-Scope Objectives:
1. **New Preferences Model `WorkoutDetailPreferences`**:
   - Create `WorkoutDetailPreferences` with 8 boolean flags (`showDescription`, `showExtrema`, `showLaps`, `showStrava`, `showMap`, `showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`), all defaulting to `true`.
   - Add DataStore keys, flow, and updater in `MyPreferenceManager.kt`.
2. **Update `WorkoutCardSectionPreferences` (List Cards)**:
   - Update default `showElevationProfile` to `false` for list cards (preserving performance and scannability, per ticket specification).
3. **Compose Matrix Table UI in `AdvancedTuningDialog.kt`**:
   - Build a clean, responsive 2-column matrix table composable in Section 5 with table header ("In Liste", "In Details"), 8 feature rows, and alternating subtle row backgrounds for optimal readability.
   - Retain lap display mode selection (Table vs. Visualizer).
4. **Modernize Subtitle Formatter in `AdvancedTuningAccordion.kt`**:
   - Update formatter to display active counts for both surfaces (`"%1$d/8 Liste, %2$d/8 Details"` / `"%1$d/8 List, %2$d/8 Details"`).
5. **Hook `WorkoutDetailPreferences` into `TrackOnMapScreen.kt`**:
   - Honor detail preferences for map, elevation profile, telemetry graphs, and bottom-sheet analytics/metadata.
6. **100% 9-Language Localization**:
   - Provide string translations for matrix column headers, row labels, and updated subtitles across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

### Out-of-Scope:
* Re-architecting the MapDetailLayout coordinate math or scrubbing engines.
* Modifying database schemas or Room entities.

---

## 7. Localization Specifications & String Identifiers

To guarantee 100% complete parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT), the following resource keys are defined:

| String Resource ID | English (Default) | German (de) | Context / UI Location |
| :--- | :--- | :--- | :--- |
| `tuning_cat_workout_masks_cards` | Workout Cards & Details | Trainingsliste & Details | Section 5 Accordion Title |
| `tuning_matrix_col_list` | In List | In Liste | Column 1 Header |
| `tuning_matrix_col_details` | In Details | In Details | Column 2 Header |
| `tuning_matrix_feature` | Section / Feature | Bereich / Element | Column 0 Header |
| `tuning_summary_matrix_format` | %1$d/8 List, %2$d/8 Details | %1$d/8 Liste, %2$d/8 Details | Section 5 Accordion Subtitle |
| `settings_workout_card_description` | Description | Beschreibung | Row 1 Label (Existing) |
| `settings_workout_card_extrema` | Extrema Values | Extremwerte | Row 2 Label (Existing) |
| `settings_workout_card_laps` | Laps Overview | Runden | Row 3 Label (Existing) |
| `settings_workout_card_strava` | Strava Activity | Strava-Status | Row 4 Label (Existing) |
| `settings_workout_card_map` | Map | Karte | Row 5 Label (Existing) |
| `settings_workout_card_elevation` | Elevation Profile | Höhenprofil | Row 6 Label (Existing) |
| `settings_workout_card_charts` | Telemetry Charts | Telemetrie-Diagramme | Row 7 Label (Existing) |
| `settings_workout_card_zones` | Training Zones | Trainingszonen | Row 8 Label (Existing) |

---

## 8. Proposed Architecture & Solution Design

### 8.1 Data Models (`MyPreferenceManager.kt`)
```kotlin
typealias WorkoutListCardPreferences = WorkoutCardSectionPreferences

data class WorkoutDetailPreferences(
    val showDescription: Boolean = true,
    val showExtrema: Boolean = true,
    val showLaps: Boolean = true,
    val showStrava: Boolean = true,
    val showMap: Boolean = true,
    val showElevationProfile: Boolean = true,
    val showTelemetryCharts: Boolean = true,
    val showZoneAnalysis: Boolean = true
)
```

### 8.2 Matrix Table Composable Layout & Touch Target Geometry
* **Touch-Target Compliance**: Every interactive checkbox or switch in the table must strictly meet Material 3 accessibility guidelines (`Modifier.minimumInteractiveComponentSize()` / 48x48dp minimum touch bounds).
* **Column Weight & Clipping Prevention**:
  - Column 0 (Feature Label): Flexible width `Modifier.weight(1f)` with `TextOverflow.Ellipsis` or multi-line wrap support to gracefully handle longer localized terms (e.g. German *"Telemetrie-Diagramme"*, Portuguese *"Gráficos de telemetria"*).
  - Column 1 ("In Liste") & Column 2 ("In Details"): Fixed width (68.dp - 76.dp) centered containers hosting the 48dp touch-target checkboxes.
* **Non-Functional Performance Constraints**:
  - Main workout list scrolling is safeguarded at 60/120 FPS by ensuring heavy visual assets (`showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`) default to `false` in `WorkoutListCardPreferences`.

```text
┌─────────────────────────────────────────────────────────────┐
│ Category 5: Trainingsliste & Details (5/8 Liste, 8/8 Details)│
├─────────────────────────────────────────────────────────────┤
│ Bereich / Element          │  In Liste   │   In Details     │
├────────────────────────────┼─────────────┼──────────────────┤
│ Beschreibung               │     [x]     │       [x]        │
│ Extremwerte                │     [x]     │       [x]        │
│ Runden                     │     [x]     │       [x]        │
│   ↳ Runden-Darstellung: [Tabelle] [Visualizer]              │
│ Strava-Status              │     [x]     │       [x]        │
│ Karte                      │     [x]     │       [x]        │
│ Höhenprofil                │     [ ]     │       [x]        │
│ Telemetrie-Diagramme       │     [ ]     │       [x]        │
│ Trainingszonen             │     [ ]     │       [x]        │
└─────────────────────────────────────────────────────────────┘
```

---

## 9. Acceptance Criteria (Given-When-Then)

* **AC-1 (Matrix Table Presentation)**:
  - *Given* an athlete navigating to Settings -> Advanced / Expert Settings,
  - *When* expanding Section 5 (*Trainingsliste & Details*),
  - *Then* the configuration is rendered as a clean 2-column matrix table with "In Liste" and "In Details" columns and 8 feature rows.
* **AC-2 (Sensible Defaults)**:
  - *Given* a fresh installation or factory reset,
  - *When* inspecting the matrix table,
  - *Then* the List column enables Description, Extrema, Laps, Strava, and Map while leaving Elevation, Telemetry, and Zones OFF; and the Details column enables all 8 features by default.
* **AC-3 (Independent Customization)**:
  - *Given* an athlete toggling a feature in the List column,
  - *When* observing the Details column,
  - *Then* the Details column toggle remains unaffected, and both views render independently according to their respective preferences.
* **AC-4 (DataStore Persistence & Factory Reset)**:
  - *Given* changes made to either column,
  - *When* saving or resetting to factory defaults,
  - *Then* all preferences are atomically persisted to DataStore and restored reliably without UI stutter.
* **AC-5 (Localization & Quality Assurance)**:
  - *Given* any of the 9 supported application locales,
  - *When* viewing the matrix table and accordion subtitle,
  - *Then* all headers, rows, and subtitles are 100% localized without missing translations or raw keys.
