# Stage 3: Implementation Plan - ATT-2030: [Settings/Aftermath] Matrix Table Presentation for Configurable Sections: Workout Summary List vs. Workout Details

**Ticket**: [ATT-2030](https://rainerblind.atlassian.net/browse/ATT-2030)  
**Sub-task**: [ATT-2089](https://rainerblind.atlassian.net/browse/ATT-2089) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections: Workout Summary List vs. Workout Details*)  
**Test Mapping**: `TST-UI-199` (*Matrix Table Presentation for Configurable Aftermath Sections Verification*)  
**Branch**: `feature/ATT-2030`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In Advanced Settings (`AdvancedTuningDialog.kt`), the aftermath section customization options are currently rendered as a flat vertical list of 8 toggle switches under a misleading heading (*"Trainingsliste (Detail-Karten)"* / *"Workout List (Detailed Cards)"*).

This setup creates confusion:
1. The wording implies it only governs scrolling list cards (`WorkoutSummary.kt`), but users expect independent control over what is displayed in the **Workout Summary List** versus the **Workout Details** screen (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`).
2. Users cannot keep the list view fast, lightweight, and compact (avoiding heavy elevation profiles, telemetry charts, and zone bars) while enjoying full charts in the full-screen details view.
3. The flat list fails to communicate the dual-surface nature of workout aftermath visualization.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-240` (*Matrix Table Presentation for Configurable Aftermath Sections: Workout Summary List vs. Workout Details*)
* **Test Mapping**: `TST-UI-199`
  * `TST-UI-199.1`: Preferences Model & DataStore Fallback Tests (`WorkoutDetailPreferencesTest.kt`)
  * `TST-UI-199.2`: Matrix Table UI Structural Contract Tests (`AdvancedTuningMatrixContractTest.kt`)
  * `TST-UI-199.3`: Accordion Subtitle Formatting Tests (`AdvancedTuningAccordionTest.kt`)
  * `TST-UI-199.4`: Detail Screen Preferences Tests (`TrackOnMapScreenPreferencesTest.kt`)
  * `TST-UI-199.5`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-199.6`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **DataStore Key Stability & Migration**: Existing `workout_card_show_*` DataStore keys remain strictly unchanged and operational. Existing users will experience zero loss of their custom list preferences.
2. **Scrolling Performance (UI Thread Invariant)**: In `WorkoutListCardPreferences`, heavy visual components (`showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`) default to `false` to guarantee 60/120 FPS scrolling performance in LazyColumn list views.
3. **Touch-Target Accessibility (Material 3)**: All checkboxes and interactive controls in the 2-column matrix table must satisfy `Modifier.minimumInteractiveComponentSize()` (min 48x48dp touch bounds).
4. **Localization Parity**: 100% complete string resources across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with matching format specifiers.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2030` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Architectural Decomposition & Component Changes

```text
┌────────────────────────────────────────────────────────┐
│ UI Layer: AdvancedTuningDialog.kt                      │
│ - WorkoutAftermathMatrixSection (2-Column Matrix Table)│
│ - Category 5 Accordion Subtitle Formatter              │
└──────────────────────────┬─────────────────────────────┘
                           │ StateFlow / Callbacks
┌──────────────────────────▼─────────────────────────────┐
│ Preferences Layer: MyPreferenceManager.kt               │
│ - WorkoutListCardPreferences (aliased from WorkoutCard) │
│ - WorkoutDetailPreferences (New model + fallback rules)│
│ - DataStore: workout_card_show_*, workout_detail_show_*│
└──────────────────────────┬─────────────────────────────┘
                           │ Flow emissions
            ┌──────────────┴──────────────┐
            ▼                             ▼
┌───────────────────────────┐ ┌───────────────────────────┐
│ WorkoutSummary.kt (List)  │ │ TrackOnMapScreen.kt       │
│ - Consumes ListCardPrefs  │ │ - Consumes DetailPrefs    │
│ - Preserves 60/120 FPS    │ │ - Map, Elevation, Charts, │
│                           │ │   Zones, Extrema, Desc    │
└───────────────────────────┘ └───────────────────────────┘
```

### Component 1: `MyPreferenceManager.kt` (Preferences & DataStore Layer)
* Define `WorkoutDetailPreferences`:
  ```kotlin
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
* Provide `typealias WorkoutListCardPreferences = WorkoutCardSectionPreferences`.
* Update default in `WorkoutCardSectionPreferences`:
  - `val showElevationProfile: Boolean = false` (default OFF for list cards, preserving scrolling performance).
* Add DataStore keys:
  `WORKOUT_DETAIL_SHOW_DESCRIPTION`, `WORKOUT_DETAIL_SHOW_EXTREMA`, `WORKOUT_DETAIL_SHOW_LAPS`, `WORKOUT_DETAIL_SHOW_STRAVA`, `WORKOUT_DETAIL_SHOW_MAP`, `WORKOUT_DETAIL_SHOW_ELEVATION`, `WORKOUT_DETAIL_SHOW_CHARTS`, `WORKOUT_DETAIL_SHOW_ZONES`.
* Implement `workoutDetailPreferencesFlow` with fallback migration logic:
  - If detail keys are unset: `showMap`, `showElevationProfile`, `showTelemetryCharts`, and `showZoneAnalysis` default to `true`.
  - `showDescription`, `showExtrema`, `showLaps`, and `showStrava` inherit from `preferences[WORKOUT_CARD_SHOW_*]` if present, else default to `true`.
* Implement `suspend fun setWorkoutDetailPreferences(prefs: WorkoutDetailPreferences)`.

### Component 2: `AdvancedTuningDialog.kt` (UI Layer)
* Collect both `persistedWorkoutCardPrefs` and `persistedWorkoutDetailPrefs` from `MyPreferenceManager`.
* Maintain local mutable dialog state:
  `var workoutCardPrefs by remember { mutableStateOf<WorkoutCardSectionPreferences?>(null) }`
  `var workoutDetailPrefs by remember { mutableStateOf<WorkoutDetailPreferences?>(null) }`
* In `onSave`: persist both models via `setWorkoutCardPreferences` and `setWorkoutDetailPreferences`.
* In Factory Reset: reset both models in DataStore and local state to default constructor instances.
* Replace flat toggle list in Section 5 with `WorkoutAftermathMatrixSection`:
  - Header row: Column 0 (`tuning_matrix_feature`, `weight(1f)`), Column 1 ("In Liste" / `tuning_matrix_col_list`), Column 2 ("In Details" / `tuning_matrix_col_details`).
  - 8 feature rows: Description, Extrema, Laps (with embedded lap display mode selector), Strava, Map, Elevation, Telemetry, Zones.
  - Alternating subtle container backgrounds for row readability.
  - Material 3 48x48dp minimum touch bounds on all checkboxes.

### Component 3: `AdvancedTuningAccordion.kt` (Formatter Layer)
* Implement `TuningSubtitleFormatter.formatWorkoutMatrixSubtitle`:
  - Signature: `fun formatWorkoutMatrixSubtitle(cardPrefs: WorkoutCardSectionPreferences, detailPrefs: WorkoutDetailPreferences, context: Context): String`
  - Calculates active count for list cards (out of 8) and active count for details (out of 8).
  - Formats via `context.getString(R.string.tuning_summary_matrix_format, listCount, detailCount)` (`"%1$d/8 Liste, %2$d/8 Details"` / `"%1$d/8 List, %2$d/8 Details"`).

### Component 4: `TrackOnMapScreen.kt` & `MapDetailLayout.kt` (Detail Inspection Layer)
* In `TrackOnMapScreen.kt`:
  - Accept `detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()`.
  - Condition `showMap` on `detailPreferences.showMap && hasGpsTrack`.
  - Condition `showElevationProfile` on `detailPreferences.showElevationProfile && ...`.
  - Condition telemetry graphs (Speed/Pace, HR, Power) in `MapDetailLayout` on `detailPreferences.showTelemetryCharts`.
  - In `analyticsContent`:
    - Gate `hrZoneDistribution` and `powerZoneDistribution` on `detailPreferences.showZoneAnalysis`.
    - Gate `splitChartData` (Laps) on `detailPreferences.showLaps`.
    - Render `WorkoutDescription` when `detailPreferences.showDescription`.
    - Render `WorkoutExtrema` when `detailPreferences.showExtrema`.
    - Render `StravaActivitySection` when `detailPreferences.showStrava`.

### Component 5: 9-Language Localization Resources (`res/values*/strings.xml`)
* Define across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`:
  - `tuning_cat_workout_masks_cards`: "Workout Cards & Details" / "Trainingsliste & Details"
  - `tuning_matrix_col_list`: "In List" / "In Liste"
  - `tuning_matrix_col_details`: "In Details" / "In Details"
  - `tuning_matrix_feature`: "Feature" / "Bereich"
  - `tuning_summary_matrix_format`: "%1$d/8 List, %2$d/8 Details" / "%1$d/8 Liste, %2$d/8 Details"

---

## 5. Step-by-Step Implementation Sequence

### Step 1: Preferences Layer Construction & Unit Tests
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/MyPreferenceManager.kt`
* **Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/preferences/WorkoutDetailPreferencesTest.kt`
* **Actions**:
  - Define `WorkoutDetailPreferences`.
  - Update `WorkoutCardSectionPreferences` default `showElevationProfile = false`.
  - Implement DataStore keys, `workoutDetailPreferencesFlow`, and `setWorkoutDetailPreferences`.
  - Author and run `WorkoutDetailPreferencesTest.kt`.
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.preferences.WorkoutDetailPreferencesTest"
  ```

### Step 2: 9-Language Localization Synchronization
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 directories)
* **Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Actions**:
  - Add localized strings for matrix headers, feature column, and matrix subtitle format.
  - Verify 100% parity and format specifier matching.
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ```

### Step 3: Accordion Subtitle Formatter & Unit Tests
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* **Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt`
* **Actions**:
  - Add `formatWorkoutMatrixSubtitle(cardPrefs, detailPrefs, context)`.
  - Update unit tests to verify dual active counts.
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest"
  ```

### Step 4: 2-Column Matrix Table UI in `AdvancedTuningDialog.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAftermathContractTest.kt`, `AdvancedTuningVisualContractTest.kt`
* **Actions**:
  - Build `WorkoutAftermathMatrixSection` with 2-column matrix table, 48dp touch targets, alternating rows, and embedded lap selector.
  - Wire dual state in `AdvancedTuningDialog`, persistence on save, and atomic factory reset.
  - Update contract tests.
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ```

### Step 5: Detail View Reactivity in `TrackOnMapScreen.kt` & `MapDetailLayout.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`, `MapDetailLayout.kt`, `WorkoutSummariesTabbedScreen.kt`
* **Test**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenPreferencesTest.kt`
* **Actions**:
  - Consume `WorkoutDetailPreferences` in `TrackOnMapScreen`.
  - Gate map, elevation, charts, zones, laps, extrema, and description according to preferences.
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"
  ```

### Step 6: Clean-Room Full Suite Regression
* **Actions**: Execute full regression across the entire project.
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
