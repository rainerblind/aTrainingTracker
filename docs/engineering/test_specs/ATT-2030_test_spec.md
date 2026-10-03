# Stage 2: Requirement & Test Specification - ATT-2030: [Settings/Aftermath] Matrix Table Presentation for Configurable Sections: Workout Summary List vs. Workout Details

**Ticket**: [ATT-2030](https://rainerblind.atlassian.net/browse/ATT-2030)  
**Sub-task**: [ATT-2088](https://rainerblind.atlassian.net/browse/ATT-2088) (`[Test-Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections: Workout Summary List vs. Workout Details*)  
**Test Spec ID**: `TST-UI-199`  
**Branch**: `feature/ATT-2030`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification

### REQ-UI-240: Matrix Table Presentation for Configurable Aftermath Sections (Workout Summary List vs. Workout Details)

The system SHALL provide independent section configuration for the Workout Summary List (`WorkoutSummary.kt`) and the Workout Details inspection screen (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`), presented as a 2-Column Matrix Table in Advanced Settings (`AdvancedTuningDialog.kt`) (ATT-2030):

1. **Separated Preferences Architecture (`MyPreferenceManager.kt`)**:
   - The system SHALL maintain two decoupled preference data models:
     - `WorkoutListCardPreferences` (aliased to `WorkoutCardSectionPreferences` for complete backward compatibility):
       `val showDescription: Boolean = true`  
       `val showExtrema: Boolean = true`  
       `val showLaps: Boolean = true`  
       `val showStrava: Boolean = true`  
       `val showMapPreview: Boolean = true`  
       `val showElevationProfile: Boolean = false` *(default OFF to safeguard 60/120 FPS list scrolling)*  
       `val showTelemetryCharts: Boolean = false` *(default OFF)*  
       `val showZoneAnalysis: Boolean = false` *(default OFF)*  
       `val lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`
     - `WorkoutDetailPreferences`:
       `val showDescription: Boolean = true`  
       `val showExtrema: Boolean = true`  
       `val showLaps: Boolean = true`  
       `val showStrava: Boolean = true`  
       `val showMap: Boolean = true`  
       `val showElevationProfile: Boolean = true`  
       `val showTelemetryCharts: Boolean = true`  
       `val showZoneAnalysis: Boolean = true`
   - `MyPreferenceManager` SHALL expose both `workoutCardPreferencesFlow` (list cards) and `workoutDetailPreferencesFlow` (details), with corresponding atomic setters `setWorkoutCardPreferences` and `setWorkoutDetailPreferences`.

2. **Backward-Compatible DataStore Migration Strategy**:
   - Existing DataStore keys for `workout_card_show_*` SHALL be preserved without mutation.
   - For `WorkoutDetailPreferences`, new keys (`workout_detail_show_*`) SHALL be introduced. If unset in DataStore:
     - `showMap`, `showElevationProfile`, `showTelemetryCharts`, and `showZoneAnalysis` SHALL default to `true`.
     - `showDescription`, `showExtrema`, `showLaps`, and `showStrava` SHALL inherit from their respective `workout_card_show_*` values if explicitly configured, or default to `true`.

3. **2-Column Matrix Table UI (`AdvancedTuningDialog.kt`)**:
   - In Section 5 (*"Trainingsliste & Details"* / `tuning_cat_workout_masks_cards`), the system SHALL render a 2-Column Matrix Table:
     - Header Row: Feature Column Label (`tuning_matrix_feature`), Column 1 ("In Liste" / `tuning_matrix_col_list`), Column 2 ("In Details" / `tuning_matrix_col_details`).
     - 8 Feature Rows: Description, Extrema, Laps (with embedded lap display mode selector), Strava Status, Map, Elevation Profile, Telemetry Charts, and Training Zones.
   - Touch Target Accessibility: All checkboxes SHALL comply with Material 3 touch target standards (`minimumInteractiveComponentSize()` / 48x48dp minimum bounds).
   - Dynamic Layout: Feature column SHALL use flexible weight (`weight(1f)`) with wrap support for long translations; column containers SHALL be fixed (68–76dp) centered touch targets.

4. **Accordion Subtitle Formatting**:
   - `TuningSubtitleFormatter` SHALL format the Section 5 accordion subtitle via `R.string.tuning_summary_matrix_format` reflecting dual active counts (`"%1$d/8 Liste, %2$d/8 Details"` / `"%1$d/8 List, %2$d/8 Details"`).

5. **Detail Screen Reactivity (`TrackOnMapScreen.kt` & `MapDetailLayout.kt`)**:
   - `TrackOnMapScreen` SHALL collect and honor `WorkoutDetailPreferences`, gating map display, elevation profile, telemetry graphs, and bottom-sheet analytics/metadata accordingly.

6. **100% 9-Language Localization Parity**:
   - All string resource identifiers (`tuning_matrix_col_list`, `tuning_matrix_col_details`, `tuning_matrix_feature`, `tuning_summary_matrix_format`, `tuning_cat_workout_masks_cards`) SHALL be 100% defined across all 9 application locales.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-UI-240`), extending `REQ-UI-210` (Workout Card Sections) and `REQ-UI-216` (Advanced Settings Integration) under Epic `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*).
   - Target Release: `V4.9.38`.

2. **Historical Origin & Commit Trace**:
   - Commit `e35a1bb2` (`ATT-1714`): Initial `WorkoutCardSectionPreferences` data model.
   - Commit `2965ceb1` (`ATT-1815`): Moved Aftermath toggles to `AdvancedTuningDialog.kt`.
   - Commit `fbb4c696` (`ATT-2029`): Excised form toggles, preparing Section 5 for clean aftermath visualization focus.

3. **Root Reason for Existing Formulation**:
   - Previously, only list cards had toggles, leading users to believe the toggles applied to the entire aftermath experience or forcing heavy charts onto list cards to see them anywhere.

4. **Preservation of Core Invariants**:
   - Existing DataStore keys (`workout_card_show_*`) remain strictly functional.
   - List card rendering (`WorkoutSummary.kt`) continues to consume list card preferences without regressions.
   - 60/120 FPS list scrolling is preserved by defaulting heavy graphs to OFF for the list view.

---

## 3. Given-When-Then Acceptance Criteria

* **AC-1 (Matrix Table Presentation)**:
  - *Given* an athlete opening Advanced Settings (`AdvancedTuningDialog`),
  - *When* viewing Section 5 (*Trainingsliste & Details*),
  - *Then* the section SHALL display a 2-column matrix table with headers "In Liste" and "In Details" and 8 feature rows.
* **AC-2 (Sensible Defaults & Performance Preservation)**:
  - *Given* a fresh installation or factory reset,
  - *When* inspecting the matrix table,
  - *Then* the List column SHALL enable Description, Extrema, Laps, Strava, and Map while leaving Elevation, Telemetry, and Zones OFF; and the Details column SHALL enable all 8 features.
* **AC-3 (Independent Customization & Persistence)**:
  - *Given* an athlete modifying checkboxes in either column,
  - *When* saving settings and returning to the Workout Summary List and Workout Details screen,
  - *Then* each surface SHALL independently reflect its configured preferences without state bleeding.
* **AC-4 (Atomic Factory Reset)**:
  - *Given* customized list and detail preferences,
  - *When* tapping "Reset to Factory Defaults",
  - *Then* both models SHALL revert to their respective defaults atomically in DataStore and UI state.
* **AC-5 (Localization & Accessibility Compliance)**:
  - *Given* any of the 9 supported locales,
  - *When* inspecting Section 5,
  - *Then* all text SHALL be 100% localized, text SHALL not be clipped, and touch targets SHALL satisfy 48x48dp minimum bounds.

---

## 4. Test Cases & Verification Procedures (TST-UI-199)

### Test Case 1: `WorkoutDetailPreferencesTest.kt` (`TST-UI-199.1`)
* **Objective**: Verify default values, DataStore serialization, and fallback migration rules.
* **Assertions**:
  - `WorkoutDetailPreferences()` defaults all 8 boolean properties to `true`.
  - `WorkoutCardSectionPreferences()` defaults `showElevationProfile` to `false`.
  - Reading `workoutDetailPreferencesFlow` when keys are missing yields default `true`.
  - Writing and reading round-trips correctly through DataStore.

### Test Case 2: `AdvancedTuningMatrixContractTest.kt` (`TST-UI-199.2`)
* **Objective**: Verify structural composition and state wiring of the matrix table in `AdvancedTuningDialog.kt`.
* **Assertions**:
  - `WorkoutMasksAndCardsSection` renders matrix column headers (`tuning_matrix_col_list`, `tuning_matrix_col_details`).
  - Both `workoutCardPrefs` and `workoutDetailPrefs` are passed and updated via callbacks.
  - Lap display mode selector is rendered within the Laps row.
  - All interactive elements employ `Modifier.minimumInteractiveComponentSize()`.

### Test Case 3: `AdvancedTuningAccordionTest.kt` (`TST-UI-199.3`)
* **Objective**: Verify subtitle formatting with dual counts.
* **Assertions**:
  - `TuningSubtitleFormatter.formatWorkoutMatrixSubtitle(cardPrefs, detailPrefs, context)` returns formatted string matching `%1$d/8 Liste, %2$d/8 Details`.

### Test Case 4: `TrackOnMapScreenPreferencesTest.kt` (`TST-UI-199.4`)
* **Objective**: Verify `TrackOnMapScreen` / `MapDetailLayout` respects `WorkoutDetailPreferences`.
* **Assertions**:
  - Setting `showElevationProfile = false` suppresses elevation profile in detail layout.
  - Setting `showTelemetryCharts = false` suppresses telemetry speed/HR/power graphs.
  - Setting `showMap = false` suppresses map rendering.

### Test Case 5: `TranslationParityTest.kt` (`TST-UI-199.5`)
* **Objective**: 9-language localization audit.
* **Assertions**:
  - `tuning_matrix_col_list`, `tuning_matrix_col_details`, `tuning_matrix_feature`, `tuning_summary_matrix_format`, `tuning_cat_workout_masks_cards` are present across all 9 localized `strings.xml` files with matching format specifiers.

### Test Case 6: Clean-Room Regression (`TST-UI-199.6`)
* **Objective**: Full test suite regression.
* **Assertions**:
  - `./gradlew testDebugUnitTest` passes 100% with 0 failures and 0 regressions.

---

## 5. Requirement Traceability Matrix

| Requirement ID | Test Case ID | Test Category | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-240.1` | `TST-UI-199.1` | Unit Test | `MyPreferenceManager.kt` | Specified |
| `REQ-UI-240.2` | `TST-UI-199.1` | Unit Test | `MyPreferenceManager.kt` | Specified |
| `REQ-UI-240.3` | `TST-UI-199.2` | Contract Test | `AdvancedTuningDialog.kt` | Specified |
| `REQ-UI-240.4` | `TST-UI-199.3` | Unit Test | `AdvancedTuningAccordion.kt` | Specified |
| `REQ-UI-240.5` | `TST-UI-199.4` | Contract Test | `TrackOnMapScreen.kt` | Specified |
| `REQ-UI-240.6` | `TST-UI-199.5` | Unit Audit | `res/values*/strings.xml` | Specified |
| `REQ-PRO-001` | `TST-UI-199.6` | Regression | Whole Project (`testDebugUnitTest`) | Specified |
