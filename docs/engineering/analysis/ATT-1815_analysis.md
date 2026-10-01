# Stage 1 Analysis: ATT-1815 - Relocate Edit Workout fields and Workout List card section toggles to Advanced Settings

**Ticket**: [ATT-1815](https://rainerblind.atlassian.net/browse/ATT-1815)  
**Sub-task**: [ATT-1859](https://rainerblind.atlassian.net/browse/ATT-1859) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1815`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During Sprint 2026-40.6, requirements `REQ-UI-210` (ATT-1714) and `REQ-UI-211` (ATT-1713) introduced comprehensive athlete customization for Aftermath screens:
1. `WorkoutCardSectionPreferences`: 8 boolean toggles controlling visibility of workout list card sections (Description, Extrema, Laps, Strava Activity, Map Preview, Elevation Profile, Telemetry Charts, and Zone Analysis).
2. `EditWorkoutFieldPreferences`: 6 boolean toggles controlling visibility of optional metadata fields in the Edit Workout dialog (Description, Route/Cluster, Commute & Trainer, Strava Upload, Training Goal, and Training Method).

These toggles were initially integrated directly into the standard Display Settings bottom sheet (`DisplaySettingsDialog.kt`). While functional, placing 14 granular toggles inside `DisplaySettingsDialog.kt` significantly bloated the standard display preferences screen:
- Athletes navigating to Display Settings primarily seek core viewport and device display options (e.g. Force Portrait, Keep Screen On, Screen Lock behavior, Cockpit Always-Dark Theme, and Display Brightness modes).
- The presence of 14 domain-specific Aftermath toggles creates visual clutter and dilutes the focus of the standard Display Settings sheet.
- Conversely, `AdvancedTuningDialog.kt` (Advanced Settings / Tuning) already features a dedicated Category 4 (*"Aftermath & Profil-Analytik"* / `tuning_cat_aftermath`), which currently only hosts the Profile X-Axis Domain selector (Distance vs Time). Category 4 is the natural, cohesive architectural home for these advanced Aftermath display customizations.

The objective of ATT-1815 is to relocate both Aftermath toggle sections from `DisplaySettingsDialog.kt` into `AdvancedTuningDialog.kt` under Category 4, restoring simplicity to Display Settings while grouping all advanced Aftermath customizations together.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Inspection Findings:
1. **Current Bloat in `DisplaySettingsDialog.kt`**:
   - Lines 60–86 maintain state and collection for both preference models:
     ```kotlin
     var currentWorkoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
     var currentEditWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }
     // LaunchedEffects collecting workoutCardPreferencesFlow and editWorkoutFieldPreferencesFlow
     ```
   - Lines 143–239 render two large vertical `Column` sections containing 14 `DisplayOptionToggle` switches for `settings_workout_card_title` and `settings_edit_workout_title`.
   - Lines 98–101 asynchronously persist these preferences on save.
   - This accounts for ~120 lines of domain-specific Aftermath UI in a dialog intended for system display options.

2. **Structure of `AdvancedTuningDialog.kt`**:
   - `AdvancedTuningDialog.kt` is organized into clean, categorized sections using `TuningCategoryHeader`:
     - Category 1: AMOLED Battery Saver (`tuning_cat_battery_saver`)
     - Category 2: GPS & Location Filtering (`tuning_cat_gps`)
     - Category 3: Elevation & Gradient Dynamics (`tuning_cat_elevation`)
     - Category 4: Aftermath & Profil-Analytik (`tuning_cat_aftermath`)
     - Category 5: Cockpit-Typografie (`tuning_cat_cockpit_typography`)
   - Category 4 currently contains only the X-axis domain selection (`profileXAxisDomain: DISTANCE | TIME`).
   - Inserting the Workout List card sections (`WorkoutCardSectionPreferences`) and Edit Workout fields (`EditWorkoutFieldPreferences`) directly beneath the X-axis domain in Category 4 consolidates all Aftermath post-workout analysis customization in one place.
   - `AdvancedTuningDialog.kt` also includes a prominent "Reset to Factory Defaults" (`R.string.reset_to_defaults`) button, which will cleanly reset `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences` to their default instances alongside the tuning config.

3. **DataStore Architecture (`MyPreferenceManager.kt`)**:
   - `MyPreferenceManager` encapsulates preferences via:
     - `workoutCardPreferencesFlow: Flow<WorkoutCardSectionPreferences>`
     - `suspend fun setWorkoutCardPreferences(prefs: WorkoutCardSectionPreferences)`
     - `editWorkoutFieldPreferencesFlow: Flow<EditWorkoutFieldPreferences>`
     - `suspend fun setEditWorkoutFieldPreferences(prefs: EditWorkoutFieldPreferences)`
   - The underlying keys (`workout_card_show_*`, `edit_workout_show_*`), serialization, defaults, and downstream consumers (`WorkoutSummary.kt`, `WorkoutSummariesViewModel.kt`, `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`) are completely decoupled from the settings UI.
   - Changing the hosting dialog requires zero modifications to DataStore keys or consumer screens.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remove the 8 `WorkoutCardSectionPreferences` toggles and 6 `EditWorkoutFieldPreferences` toggles from `DisplaySettingsDialog.kt`.
  * Remove unused `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences` state management, LaunchedEffects, and persistence from `DisplaySettingsDialog.kt`.
  * Integrate both toggle sections into `AdvancedTuningDialog.kt` under Category 4 (*Aftermath & Profil-Analytik*).
  * In `AdvancedTuningDialog.kt`, observe and collect `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` via `MyPreferenceManager`.
  * Persist modified preferences in `AdvancedTuningDialog.kt`'s `onSave` action.
  * Reset both preference models to default instances in `AdvancedTuningDialog.kt`'s `resetToDefaults` action.
  * Preserve 100% existing string resources and 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do not change any DataStore preference keys, field names, or default values.
  * Do not alter the rendering or business logic of `WorkoutSummary.kt` or `EditWorkoutScreen.kt`.
  * Do not alter other categories or parameters in `AdvancedTuningDialog.kt` or `DisplaySettingsDialog.kt`.
  * Do not modify dialog fragment routing in `ATrainingTrackerApp.kt` or `MainActivityWithNavigation.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-UI-210` (*Aftermath: Configurable Sections in Detailed Workout Cards*) & `REQ-UI-211` (*Aftermath: Configurable Fields in Edit Workout Dialog*), extending Epic `ATT-355` (*Good and consistent UI*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-210` introduced in Sprint 2026-40.6 (commit `e35a1bb2`).
  - `REQ-UI-211` introduced in Sprint 2026-40.6 (commit `4d8961ca`).
* **Root Reason for Existing Formulation**:
  - When `REQ-UI-210` and `REQ-UI-211` were implemented, `DisplaySettingsDialog.kt` was the quickest integration point to expose the toggles to athletes without creating new dialogs or adding tabs.
  - However, user testing revealed that mixing granular Aftermath list card and form customization into standard Display Settings bloated the sheet and hindered navigation.
* **Preservation of Core Invariants**:
  - `REQ-UI-216` refines the UI hosting location specified in Section 3 of `REQ-UI-210` and Section 4 of `REQ-UI-211`, redirecting the hosting container from `DisplaySettingsDialog.kt` to `AdvancedTuningDialog.kt`.
  - All functional capabilities, DataStore keys, default values, reactive behavior, and 9-language strings are 100% preserved.
  - A navigation button from `DisplaySettingsDialog` to `AdvancedTuningDialog` already exists (`onNavigateToTuning`), ensuring full discoverability.

---

## 5. Architectural Strategy & High-Level Solution

1. **Clean up `DisplaySettingsDialog.kt`**:
   - Remove `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences` state variables, LaunchedEffects, and persistence logic.
   - Remove the two `Column` blocks rendering the toggles and their dividers.
   - Retain core display toggles (Force Portrait, Keep Screen On, Screen Lock), Cockpit Theme Mode, Display Brightness modes/slider, and the "Advanced Settings" navigation button.

2. **Enhance `AdvancedTuningDialog.kt`**:
   - Remember `preferenceManager = remember(context) { MyPreferenceManager(context) }`.
   - Maintain mutable state `workoutCardPrefs` and `editWorkoutPrefs`.
   - Collect flows from `preferenceManager` and initialize mutable state on launch.
   - Under Category 4 (`tuning_cat_aftermath`), below the Profile X-Axis Domain chip selector:
     - Render Workout List Card Sections subheader (`settings_workout_card_title`) with 8 toggles.
     - Render Edit Workout Fields subheader (`settings_edit_workout_title`) with 6 toggles.
   - Implement `TuningToggleItem` composable matching Material 3 styling (compact switch aligned right).
   - In `onSave`: persist `workoutCardPrefs` and `editWorkoutPrefs` via `preferenceManager.setWorkoutCardPreferences` and `preferenceManager.setEditWorkoutFieldPreferences`.
   - In `resetToDefaults`: reset `workoutCardPrefs` and `editWorkoutPrefs` to defaults in DataStore and local state.

3. **Verification & Tests**:
   - Update `DisplaySettingsTest.kt` if any assertions touched relocated fields.
   - Add unit test suite `AftermathTuningSettingsTest.kt` verifying loading, saving, and reset-to-defaults for both preference models in the tuning context.
   - Verify localization tests (`WorkoutCardSettingsLocalizationTest.kt`, `EditWorkoutSettingsLocalizationTest.kt`, `AftermathTuningLocalizationTest.kt`) pass across all 9 languages.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. DataStore persistence keys (`workout_card_show_*`, `edit_workout_show_*`) remain strictly unchanged.
  2. Reactive updates in `WorkoutSummary` and `EditWorkoutScreen` continue to observe changes seamlessly via DataStore flows.
  3. 9-language localization parity maintained across all strings.
  4. 100% clean-room unit test pass rate across `./gradlew testDebugUnitTest`.
  5. Parent ticket decision gate (`Final Review (Human)`) remains strictly enforced.
* **Risk Rating**: **LOW**
  - Pure UI relocation of existing, mature, and tested preference toggles without changes to the underlying DataStore schema or domain consumers.
