# Stage 3: Implementation Plan - ATT-1815: Relocate Edit Workout fields and Workout List card section toggles to Advanced Settings

**Ticket**: [ATT-1815](https://rainerblind.atlassian.net/browse/ATT-1815)  
**Sub-task**: [ATT-1861](https://rainerblind.atlassian.net/browse/ATT-1861) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-216` (*Settings/Aftermath: Relocation of Workout Card Sections and Edit Workout Fields Customization Toggles to Advanced Settings*)  
**Test Mapping**: `TST-UI-170` (*Settings/Aftermath: Relocation of Aftermath Customization Toggles to Advanced Settings Verification*)  
**Branch**: `feature/ATT-1815`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

The standard Display Settings bottom sheet (`DisplaySettingsDialog.kt`) currently hosts 14 granular toggles for customizing Aftermath post-workout screens:
1. 8 toggles for Workout List detailed card sections (`WorkoutCardSectionPreferences`).
2. 6 toggles for Edit Workout metadata form fields (`EditWorkoutFieldPreferences`).

Placing these 14 domain-specific toggles inside the basic display settings sheet causes severe visual bloat, obscuring core device and display preferences (e.g. Force Portrait, Keep Screen On, Screen Lock, Cockpit Theme, Display Brightness modes).

Conversely, `AdvancedTuningDialog.kt` has a dedicated Category 4 titled *"Aftermath & Profil-Analytik"* (`tuning_cat_aftermath`), which currently only hosts the Profile X-Axis Domain selector (Distance vs Time). Centralizing all Aftermath customization toggles under Category 4 in `AdvancedTuningDialog.kt` restores clarity to Display Settings while providing a cohesive, centralized home for advanced post-workout customization.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-216` (*Settings/Aftermath: Relocation of Workout Card Sections and Edit Workout Fields Customization Toggles to Advanced Settings*)
* **Test Mapping**: `TST-UI-170` (*Settings/Aftermath: Relocation of Aftermath Customization Toggles to Advanced Settings Verification*)
  * `[TST-UI-170.1]`: `DisplaySettingsDialog` Cleanup Contract Test (`DisplaySettingsCleanupTest.kt`)
  * `[TST-UI-170.2]`: `AdvancedTuningDialog` Category 4 Contract Test (`AdvancedTuningAftermathContractTest.kt`)
  * `[TST-UI-170.3]`: Aftermath Tuning State & Reset Integration Test (`AftermathTuningSettingsTest.kt`)
  * `[TST-UI-170.4]`: Localization Parity Audit (`WorkoutCardSettingsLocalizationTest.kt`, `EditWorkoutSettingsLocalizationTest.kt`, `AftermathTuningLocalizationTest.kt`)
  * `[TST-UI-170.5]`: Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **DataStore Key & Entity Immutability**: Persistence keys (`workout_card_show_*`, `edit_workout_show_*`), serialization routines, and default values remain 100% unchanged.
2. **Downstream Consumer Parity**: `WorkoutSummary.kt`, `WorkoutSummariesViewModel.kt`, `EditWorkoutScreen.kt`, and `EditWorkoutViewModel.kt` continue observing `MyPreferenceManager` flows without any modifications.
3. **9-Language Localization Parity**: All string resources for categories, section titles, and toggle labels remain defined across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. **Mandatory Programmatic Pre-Check Before Stage 4 Code Modifications**:
   - `python3 tools/jira_util.py check-gate ATT-1861` must exit code 0 (`GATE_PASSED`) before modifying production source files under `app/src/...`.
5. **Human Gate Invariance**: Terminal transition of parent ticket `ATT-1815` is strictly `Final Review (Human)` assigned to `rainer`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `DisplaySettingsDialog.kt` (`com.atrainingtracker.trainingtracker.ui.settings.display`)
- Remove unused imports: `MyPreferenceManager`, `WorkoutCardSectionPreferences`, `EditWorkoutFieldPreferences`.
- Remove state variables: `currentWorkoutCardPrefs`, `isWorkoutCardPrefsLoaded`, `currentEditWorkoutPrefs`, `isEditWorkoutPrefsLoaded`.
- Remove the two `LaunchedEffect` blocks collecting `preferenceManager.workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow`.
- In `onSave`: remove calls to `preferenceManager.setWorkoutCardPreferences` and `preferenceManager.setEditWorkoutFieldPreferences`.
- In the Composable layout: remove the two `Column` blocks rendering `settings_workout_card_title` (8 toggles) and `settings_edit_workout_title` (6 toggles) along with their dividers.
- Retain the `onNavigateToTuning` action button allowing direct navigation into `AdvancedTuningDialog`.

### Component 2: `AdvancedTuningDialog.kt` (`com.atrainingtracker.trainingtracker.ui.settings.tuning`)
- Import `MyPreferenceManager`, `WorkoutCardSectionPreferences`, `EditWorkoutFieldPreferences`.
- Remember `preferenceManager = remember(context) { MyPreferenceManager(context) }`.
- Maintain mutable state for both preference models:
  ```kotlin
  val persistedWorkoutCardPrefs by preferenceManager.workoutCardPreferencesFlow.collectAsState(initial = WorkoutCardSectionPreferences())
  val persistedEditWorkoutPrefs by preferenceManager.editWorkoutFieldPreferencesFlow.collectAsState(initial = EditWorkoutFieldPreferences())

  var workoutCardPrefs by remember { mutableStateOf(WorkoutCardSectionPreferences()) }
  var editWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }
  var isAftermathPrefsInitialized by remember { mutableStateOf(false) }

  LaunchedEffect(persistedWorkoutCardPrefs, persistedEditWorkoutPrefs) {
      if (!isAftermathPrefsInitialized) {
          workoutCardPrefs = persistedWorkoutCardPrefs
          editWorkoutPrefs = persistedEditWorkoutPrefs
          isAftermathPrefsInitialized = true
      }
  }
  ```
- In Category 4 (`tuning_cat_aftermath`), beneath the Profile X-Axis Domain chips:
  - Add Section A: **Trainingsliste (Detail-Karten)** (`R.string.settings_workout_card_title`) with 8 `TuningToggleItem` switches.
  - Add Section B: **Training bearbeiten** (`R.string.settings_edit_workout_title`) with 6 `TuningToggleItem` switches.
- Define private composable:
  ```kotlin
  @Composable
  private fun TuningToggleItem(
      title: String,
      isChecked: Boolean,
      onCheckedChange: (Boolean) -> Unit
  ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
      ) {
          Text(
              text = title,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f)
          )
          Switch(
              checked = isChecked,
              onCheckedChange = onCheckedChange,
              modifier = Modifier.scale(0.8f)
          )
      }
  }
  ```
- In `onSave`:
  ```kotlin
  preferenceManager.setWorkoutCardPreferences(workoutCardPrefs)
  preferenceManager.setEditWorkoutFieldPreferences(editWorkoutPrefs)
  ```
- In `resetToDefaults`:
  ```kotlin
  preferenceManager.setWorkoutCardPreferences(WorkoutCardSectionPreferences())
  preferenceManager.setEditWorkoutFieldPreferences(EditWorkoutFieldPreferences())
  workoutCardPrefs = WorkoutCardSectionPreferences()
  editWorkoutPrefs = EditWorkoutFieldPreferences()
  ```

### Component 3: Test Suite Enhancements
- `DisplaySettingsCleanupTest.kt`: Verify source contract that `DisplaySettingsDialog.kt` no longer contains the Aftermath toggles or preference dependencies.
- `AdvancedTuningAftermathContractTest.kt`: Verify source contract that `AdvancedTuningDialog.kt` contains both Aftermath sections, all 14 string keys, and handles save/reset.
- `AftermathTuningSettingsTest.kt`: Unit test verifying DataStore saving and default restoration for both preference models.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
- Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate ATT-1861`.

### Step 2: Clean up `DisplaySettingsDialog.kt`
- Remove Aftermath preference models, state, LaunchedEffects, save logic, and toggle layout blocks.

### Step 3: Enhance `AdvancedTuningDialog.kt`
- Add `MyPreferenceManager` integration, mutable state, Category 4 toggle layouts, save persistence, and factory reset restoration.

### Step 4: Create Contract and Unit Tests
- Create `DisplaySettingsCleanupTest.kt`.
- Create `AdvancedTuningAftermathContractTest.kt`.
- Create `AftermathTuningSettingsTest.kt`.

### Step 5: Execute Targeted Tests
- Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.*"`.
- Run localization tests: `./gradlew testDebugUnitTest --tests "*LocalizationTest"`.
