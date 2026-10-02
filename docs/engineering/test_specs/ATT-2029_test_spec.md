# Stage 2: Requirement & Test Specification - ATT-2029: [Settings/Aftermath] Remove Edit Workout Dialog Field Toggles from Advanced Settings

**Ticket**: [ATT-2029](https://rainerblind.atlassian.net/browse/ATT-2029)  
**Sub-task**: [ATT-2083](https://rainerblind.atlassian.net/browse/ATT-2083) (`[Test-Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-239` (*Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles from Advanced Settings*)  
**Test Spec ID**: `TST-UI-198`  
**Branch**: `feature/ATT-2029`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification

### REQ-UI-239: Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles from Advanced Settings

The system SHALL present all available workout metadata fields unconditionally inside the Edit Workout dialog (`EditWorkoutScreen.kt`) and remove the redundant field toggles from Advanced Settings (`AdvancedTuningDialog.kt`) (ATT-2029):

1. **Clean Advanced Settings (`AdvancedTuningDialog.kt`)**:
   - The system SHALL remove Sub-block 2 (*"Training bearbeiten"* / `settings_edit_workout_title`) and its 7 toggle switches (`settings_edit_workout_description`, `settings_edit_workout_cluster`, `settings_edit_workout_commute_trainer`, `settings_edit_workout_race`, `settings_edit_workout_strava`, `settings_edit_workout_goal`, `settings_edit_workout_method`) from `AdvancedTuningDialog.kt`.
   - Section 5 of Advanced Settings SHALL focus exclusively on Workout List Card customizations (`WorkoutCardSectionPreferences`).

2. **De-cluttered State & Factory Reset Lifecycle**:
   - `AdvancedTuningDialog.kt` SHALL remove local mutable state `editWorkoutPrefs`, DataStore persistence invocation `preferenceManager.setEditWorkoutFieldPreferences()`, and factory reset logic for `EditWorkoutFieldPreferences`.

3. **Unconditional Form Presentation (`EditWorkoutScreen.kt`)**:
   - In `EditWorkoutScreen.kt`, all standard workout metadata fields SHALL be rendered unconditionally:
     - Core Anchors: Workout Name, Sport Type dropdown, Equipment dropdown.
     - Metadata Attributes: Route / Cluster selector, Commute checkbox, Trainer checkbox, Race checkbox, Description multiline field, Goal field, and Method field.
   - The Strava upload checkbox SHALL remain conditionally rendered solely based on whether the Strava community connection is active (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`).
   - `EditWorkoutViewModel` SHALL be decoupled from `EditWorkoutFieldPreferences`.

4. **Active Cards Subtitle Formatting & 100% 9-Language Parity**:
   - `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` SHALL accept only `cardPrefs: WorkoutCardSectionPreferences, context: Context` and format the subtitle based on the count of active cards.
   - String resource `tuning_summary_masks_cards_format` SHALL be standardized to `"%1$d/8 Cards"` / `"%1$d/8 Karten"` across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

5. **Invariants**:
   - Saving workouts from `EditWorkoutScreen` MUST preserve all entered data without corruption.
   - The 8 Workout List Card section customization toggles (`WorkoutCardSectionPreferences`) in Advanced Settings MUST remain 100% operational.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement (`REQ-UI-239`), superseding `REQ-UI-211` (*Configurable Fields in Edit Workout Dialog*) and refining `REQ-UI-216` (*Relocation of Aftermath Customization Toggles to Advanced Settings*), targeting `EditWorkoutScreen.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningAccordion.kt`.
   - Target Release: `V4.9.38`.
   - Parent Epic: `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*).

2. **Historical Origin & Commit Trace**:
   - Commit `4d8961ca` (`ATT-1713`): Initial introduction of `EditWorkoutFieldPreferences` and toggle switches in `DisplaySettingsDialog.kt`.
   - Commit `2965ceb1` (`ATT-1815`): Relocation of Aftermath customization toggles to `AdvancedTuningDialog.kt` Category 4.
   - Commit `30b42d79` (`ATT-2005`): Added `showRace` toggle.

3. **Root Reason for Existing Formulation**:
   - The author originally assumed athletes wanted to customize and shrink the Edit Workout form to avoid vertical scrolling.
   - However, athlete feedback demonstrated that hiding fields in an editing dialog creates confusion when users seek to add notes, set goals, or assign clusters to workouts. A data editing modal should naturally expose all editable properties.

4. **Preservation of Core Invariants**:
   - Core anchor fields (Workout Name, Sport Type dropdown, Equipment dropdown) continue to be permanently rendered.
   - All optional fields (Cluster, Commute, Trainer, Race, Description, Goal, Method) become permanently rendered alongside the core fields.
   - Strava upload remains conditionally rendered based on active Strava community connection (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`).
   - Workout List Cards customization toggles (`WorkoutCardSectionPreferences`) in Advanced Settings remain 100% functional and intact.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Clean Advanced Settings)**:
  - *Given* an athlete in Settings -> Expert / Advanced Settings,
  - *When* scrolling to Section 5 ("Workout-Karten & Eingabemasken" / `tuning_cat_workout_masks_cards`),
  - *Then* the section SHALL only render Workout List Card customization switches and SHALL NOT display the "Training bearbeiten" header or any edit dialog field toggles.

* **Criterion 2 (Unconditional Edit Dialog Form Presentation)**:
  - *Given* an athlete tapping the Edit icon on any workout in the summaries list or map detail view,
  - *When* the Edit Workout bottom sheet opens,
  - *Then* all standard attributes (Name, Route Cluster, Sport, Equipment, Commute, Trainer, Race, Description, Goal, Method) SHALL be visible and editable unconditionally.

* **Criterion 3 (Accurate Subtitle Formatting)**:
  - *Given* an athlete configuring workout card toggles in Advanced Settings,
  - *When* 8 of 8 card sections are enabled,
  - *Then* the Section 5 accordion header subtitle SHALL display "8/8 Cards" (or localized equivalent in the active locale).

* **Criterion 4 (Reset to Factory Defaults Safety)**:
  - *Given* modified settings in Advanced Settings,
  - *When* the athlete taps "Reset to Factory Defaults",
  - *Then* all preferences reset without references to or errors from `EditWorkoutFieldPreferences`.

---

## 4. Test Case Specification

### TST-UI-198.1: Structural Contract Verification (`AdvancedTuningAftermathContractTest.kt`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAftermathContractTest.kt`
* **Test Objectives**:
  - Assert that `AdvancedTuningDialog.kt` contains `WorkoutCardSectionPreferences` and all 8 card section toggles.
  - Assert that `AdvancedTuningDialog.kt` does NOT reference `EditWorkoutFieldPreferences`.
  - Assert that `AdvancedTuningDialog.kt` does NOT reference `settings_edit_workout_title` or any of the 7 edit field toggles.
  - Assert that `AdvancedTuningDialog.kt` does NOT invoke `setEditWorkoutFieldPreferences`.

### TST-UI-198.2: Unconditional Form Layout Contract Test (`EditWorkoutFieldsLayoutTest.kt`)
* **Scope**: Contract Test / Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutFieldsLayoutTest.kt`
* **Test Objectives**:
  - Assert that all metadata fields in `EditWorkoutScreen.kt` are permanently visible without conditional toggle dependencies.

### TST-UI-198.3: Accordion Subtitle Formatting Test (`AdvancedTuningAccordionTest.kt`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt`
* **Test Objectives**:
  - Verify that `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` formats strings correctly with single argument (`%1$d/8 Cards`).

### TST-UI-198.4: 9-Language Localization Audit (`TranslationParityTest.kt`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Test Objectives**:
  - Verify that `tuning_summary_masks_cards_format` is defined across all 9 localized `strings.xml` files with matching format specifiers (`%1$d/8`).

### TST-UI-198.5: Clean-Room Full Suite Regression Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Pass Criteria**: 100% pass rate across all modules with 0 regressions.

---

## 5. Traceability Matrix

| Test Case | Scope | Method Under Test / Target | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-198.1` | Contract | `AdvancedTuningDialog.kt` clean-up contract | `REQ-UI-239` | Specified |
| `TST-UI-198.2` | Contract | `EditWorkoutScreen.kt` unconditional layout | `REQ-UI-239` | Specified |
| `TST-UI-198.3` | Unit | `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` | `REQ-UI-239` | Specified |
| `TST-UI-198.4` | Localization | `tuning_summary_masks_cards_format` 9-locale parity | `REQ-UI-239`, `REQ-UI-106` | Specified |
| `TST-UI-198.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
