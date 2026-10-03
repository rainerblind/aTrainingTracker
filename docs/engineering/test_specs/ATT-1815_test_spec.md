# Stage 2: Requirement & Test Specification - ATT-1815: Relocate Edit Workout fields and Workout List card section toggles to Advanced Settings

**Ticket**: [ATT-1815](https://rainerblind.atlassian.net/browse/ATT-1815)  
**Sub-task**: [ATT-1860](https://rainerblind.atlassian.net/browse/ATT-1860) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-216` (*Settings/Aftermath: Relocation of Workout Card Sections and Edit Workout Fields Customization Toggles to Advanced Settings*)  
**Test Mapping**: `TST-UI-170` (*Settings/Aftermath: Relocation of Aftermath Customization Toggles to Advanced Settings Verification*)  
**Branch**: `feature/ATT-1815`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-216)

### 1.1 Problem Statement & Rationale
When requirements `REQ-UI-210` (ATT-1714) and `REQ-UI-211` (ATT-1713) were introduced, the toggles for customizing Workout List detailed card sections (`WorkoutCardSectionPreferences`) and Edit Workout metadata fields (`EditWorkoutFieldPreferences`) were placed directly into the standard Display Settings bottom sheet (`DisplaySettingsDialog.kt`). This resulted in 14 granular toggles bloating the standard display preferences screen, creating visual clutter and distracting athletes seeking basic display options (e.g. orientation lock, screen timeout, cockpit theme, brightness modes).

Conversely, `AdvancedTuningDialog.kt` contains Category 4 (*"Aftermath & Profil-Analytik"* / `tuning_cat_aftermath`), which currently only hosts the Profile X-Axis Domain selector. Relocating these granular Aftermath customization toggles to Category 4 in `AdvancedTuningDialog.kt` creates a cohesive and logical organization: standard display settings remain clean and focused on viewport/device options, while all advanced post-workout customization is centralized in Advanced Settings.

### 1.2 Functional & Architectural Requirements
1. **De-cluttering Standard Display Settings (`DisplaySettingsDialog.kt`)**:
   - `DisplaySettingsDialog.kt` SHALL remove the Workout List card sections toggle group (`settings_workout_card_title`) and all 8 associated switches (`settings_workout_card_*`).
   - `DisplaySettingsDialog.kt` SHALL remove the Edit Workout fields toggle group (`settings_edit_workout_title`) and all 6 associated switches (`settings_edit_workout_*`).
   - `DisplaySettingsDialog.kt` SHALL remove local state variables, LaunchedEffects, and persistence calls for `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences`.
   - `DisplaySettingsDialog.kt` SHALL retain the "Advanced Settings" navigation button (`onNavigateToTuning`), allowing athletes to seamlessly navigate directly to Advanced Tuning.

2. **Consolidation into Advanced Tuning Category 4 (`AdvancedTuningDialog.kt`)**:
   - `AdvancedTuningDialog.kt` SHALL host both Aftermath customization sections inside Category 4 (*"Aftermath & Profil-Analytik"*), positioned beneath the Profile X-Axis Domain selector:
     - Section A: **Trainingsliste (Detail-Karten)** / **Workout List (Detailed Cards)** (`R.string.settings_workout_card_title`) with 8 toggles:
       1. Description (`R.string.settings_workout_card_description`) -> `showDescription`
       2. Extrema (`R.string.settings_workout_card_extrema`) -> `showExtrema`
       3. Laps (`R.string.settings_workout_card_laps`) -> `showLaps`
       4. Strava (`R.string.settings_workout_card_strava`) -> `showStrava`
       5. Map Preview (`R.string.settings_workout_card_map`) -> `showMapPreview`
       6. Elevation Profile (`R.string.settings_workout_card_elevation`) -> `showElevationProfile`
       7. Telemetry Charts (`R.string.settings_workout_card_charts`) -> `showTelemetryCharts`
       8. Zone Distribution (`R.string.settings_workout_card_zones`) -> `showZoneAnalysis`
     - Section B: **Training bearbeiten** / **Edit Workout** (`R.string.settings_edit_workout_title`) with 6 toggles:
       1. Description (`R.string.settings_edit_workout_description`) -> `showDescription`
       2. Route / Cluster (`R.string.settings_edit_workout_cluster`) -> `showCluster`
       3. Commute & Trainer (`R.string.settings_edit_workout_commute_trainer`) -> `showCommuteTrainer`
       4. Strava Upload (`R.string.settings_edit_workout_strava`) -> `showStravaUpload`
       5. Goal (`R.string.settings_edit_workout_goal`) -> `showGoal`
       6. Method (`R.string.settings_edit_workout_method`) -> `showMethod`
   - Toggles SHALL use consistent Material 3 styling via a reusable private composable `TuningToggleItem` matching existing dialog visual conventions.

3. **DataStore Lifecycle & State Management**:
   - `AdvancedTuningDialog.kt` SHALL observe `MyPreferenceManager.workoutCardPreferencesFlow` and `MyPreferenceManager.editWorkoutFieldPreferencesFlow`.
   - On `onSave`: `AdvancedTuningDialog.kt` SHALL persist modified preferences via `MyPreferenceManager.setWorkoutCardPreferences(currentWorkoutCardPrefs)` and `MyPreferenceManager.setEditWorkoutFieldPreferences(currentEditWorkoutPrefs)`.
   - On `resetToDefaults`: `AdvancedTuningDialog.kt` SHALL restore `WorkoutCardSectionPreferences()` and `EditWorkoutFieldPreferences()` default instances in DataStore and local state.

4. **100% 9-Language Localization Parity**:
   - All string resources referenced by the relocated sections SHALL maintain 100% complete definitions across all 9 supported locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Display Settings De-cluttering)**:
  * *Given* an athlete opening the standard Display Settings bottom sheet (`DisplaySettingsDialog`),
  * *When* inspecting the sheet content,
  * *Then* the sheet SHALL display core display toggles (Force Portrait, Keep Screen On, Screen Lock), Cockpit Theme, Display Brightness modes/slider, and the "Advanced Settings" button, and SHALL NOT display any Workout Card or Edit Workout section toggles.
* **Criterion 2 (Advanced Settings Category 4 Customization)**:
  * *Given* an athlete navigating to Advanced Settings (`AdvancedTuningDialog`),
  * *When* scrolling to Category 4 (*"Aftermath & Profil-Analytik"*),
  * *Then* the athlete SHALL see the Profile X-Axis Domain selector, followed by the Workout List Card Sections switches (8 toggles) and Edit Workout Field switches (6 toggles).
* **Criterion 3 (Preferences Persistence & Reactive Downstream Behavior)**:
  * *Given* an athlete in `AdvancedTuningDialog`,
  * *When* toggling off Map Preview in Workout Cards and toggling off Goal in Edit Workout and tapping Save,
  * *Then* `MyPreferenceManager` SHALL persist the changes, workout cards in `WorkoutSummary` SHALL omit the map preview, and `EditWorkoutScreen` SHALL omit the Goal field.
* **Criterion 4 (Factory Reset Behavior)**:
  * *Given* custom Aftermath toggles saved in `AdvancedTuningDialog`,
  * *When* tapping "Reset to Factory Defaults",
  * *Then* both `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences` SHALL revert to their default states (all core sections enabled; charts/zones default false).

### 1.4 System Invariants
1. DataStore keys (`workout_card_show_*`, `edit_workout_show_*`) remain strictly unchanged.
2. Reactive consumers (`WorkoutSummary.kt`, `EditWorkoutScreen.kt`) continue functioning with zero modifications.
3. 9-language localization parity maintained across all strings.
4. Clean-room test suite maintains 100% pass rate.

---

## 2. Test Specification (TST-UI-170)

### Test Case 1: `DisplaySettingsDialog` Cleanup Contract Test (`[TST-UI-170.1]`)
* **Scope**: UI Layout & Architecture Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsCleanupTest.kt`
* **Preconditions**: `DisplaySettingsDialog.kt` exists.
* **Action**:
  - Verify that `DisplaySettingsDialog.kt` does NOT reference `settings_workout_card_title` or `settings_edit_workout_title`.
  - Verify that `DisplaySettingsDialog.kt` does NOT import or instantiate `WorkoutCardSectionPreferences` or `EditWorkoutFieldPreferences`.
  - Verify that `DisplaySettingsDialog.kt` retains core display options, theme mode, and brightness modes.
* **Expected Result**: Assertions pass.

### Test Case 2: `AdvancedTuningDialog` Category 4 Contract Test (`[TST-UI-170.2]`)
* **Scope**: UI Layout & Architecture Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAftermathContractTest.kt`
* **Preconditions**: `AdvancedTuningDialog.kt` exists.
* **Action**:
  - Verify that `AdvancedTuningDialog.kt` imports and references `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences`.
  - Verify that `AdvancedTuningDialog.kt` references `settings_workout_card_title` and all 8 card section string keys.
  - Verify that `AdvancedTuningDialog.kt` references `settings_edit_workout_title` and all 6 edit workout field string keys.
  - Verify that `onSave` persists both preference models and `resetToDefaults` restores both default instances.
* **Expected Result**: Assertions pass.

### Test Case 3: Aftermath Tuning State & Reset Integration Test (`[TST-UI-170.3]`)
* **Scope**: DataStore & ViewModel Integration Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AftermathTuningSettingsTest.kt`
* **Preconditions**: `MyPreferenceManager` and `TuningPreferencesDataStore` exist.
* **Action**:
  - Test saving custom `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences`.
  - Test resetting to defaults restores `WorkoutCardSectionPreferences()` and `EditWorkoutFieldPreferences()`.
* **Expected Result**: DataStore emits matching state flows accurately.

### Test Case 4: Localization Parity Audit (`[TST-UI-170.4]`)
* **Scope**: Localization Unit Test
* **Target Files**:
  - `WorkoutCardSettingsLocalizationTest.kt`
  - `EditWorkoutSettingsLocalizationTest.kt`
  - `AftermathTuningLocalizationTest.kt`
* **Action**: Run existing localization tests verifying all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% non-null, non-blank strings across all 9 locales.

### Test Case 5: Full Clean-Room Regression Suite (`[TST-UI-170.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 failures and 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Verification Procedure | Test Class / Method | Expected Status |
| :--- | :--- | :--- | :--- |
| **REQ-UI-216.1** (Display Settings Cleanup) | Contract test for removal of Aftermath toggles from Display Settings | `DisplaySettingsCleanupTest.kt` | Pass |
| **REQ-UI-216.2** (Advanced Tuning Category 4 Integration) | Contract test for inclusion of Aftermath toggles in Advanced Tuning | `AdvancedTuningAftermathContractTest.kt` | Pass |
| **REQ-UI-216.3** (DataStore Persistence & Reset) | Unit test verifying DataStore saving and default restoration | `AftermathTuningSettingsTest.kt` | Pass |
| **REQ-UI-216.4** (9-Language Parity) | Localization audit across 9 supported languages | `WorkoutCardSettingsLocalizationTest.kt`, `EditWorkoutSettingsLocalizationTest.kt` | Pass |
| **Zero Regression Invariant** | Full clean-room test execution | `./gradlew testDebugUnitTest` | Pass (100%) |
