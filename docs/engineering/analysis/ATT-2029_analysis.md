# Stage 1 Analysis: ATT-2029 - [Settings/Aftermath] Remove Edit Workout Dialog Field Toggles from Advanced Settings

**Ticket**: [ATT-2029](https://rainerblind.atlassian.net/browse/ATT-2029)  
**Sub-task**: [ATT-2082](https://rainerblind.atlassian.net/browse/ATT-2082) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Branch**: `feature/ATT-2029`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In previous sprint increments ([ATT-1713], [ATT-1815], [ATT-2005]), a set of toggle switches (`showDescription`, `showCluster`, `showCommuteTrainer`, `showRace`, `showStravaUpload`, `showGoal`, `showMethod`) was introduced under a *"Training bearbeiten"* / *"Edit Workout"* sub-block in Advanced Settings (`AdvancedTuningDialog.kt`). These toggles permitted athletes to hide metadata input fields inside the Edit Workout dialog (`EditWorkoutScreen.kt`).

Real-world user evaluation revealed that hiding input fields in the edit dialog was a fundamental product misunderstanding:
1. **Misalignment with User Mental Model**: The Edit Workout dialog is an intentional, focused data-entry and inspection surface. When an athlete explicitly taps the "Edit" action, they expect all available attributes (Description, Route Cluster, Commute, Trainer, Race, Strava sync, Goal, and Method) to be directly accessible and editable without having to navigate into expert settings to re-enable hidden fields.
2. **Cognitive Overhead & Clutter in Advanced Settings**: Hosting 7 form-field toggles alongside the 8 workout list card toggles bloated Advanced Settings Section 5 ("Workout-Karten & Eingabemasken"), confusing athletes with redundant micro-configurations.
3. **Redundant Architectural Complexity**: Maintaining `EditWorkoutFieldPreferences` across DataStore, `MyPreferenceManager`, `EditWorkoutViewModel`, and `AdvancedTuningDialog` created unnecessary state synchronizations and edge cases during factory resets.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic examination of the codebase reveals how edit field toggles permeate the settings and UI layers:

### 2.1 Settings UI Layer (`AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`)
* In `AdvancedTuningDialog.kt` (lines 787–925):
  - `WorkoutMasksAndCardsSection` renders two distinct sub-blocks: Sub-block 1 (*"Trainingsliste (Detail-Karten)"* / `settings_workout_card_title`) with 8 card toggles, and Sub-block 2 (*"Training bearbeiten"* / `settings_edit_workout_title`) with 7 field toggles.
  - `AdvancedTuningDialog` manages local mutable state `var editWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }`, collects `preferenceManager.editWorkoutFieldPreferencesFlow`, persists updates via `preferenceManager.setEditWorkoutFieldPreferences`, and restores defaults in the factory reset handler.
* In `AdvancedTuningAccordion.kt` (lines 134–160):
  - `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` counts active card sections and active edit fields, formatting the subtitle via `R.string.tuning_summary_masks_cards_format` (`"%1$d/8 Cards, %2$d/6 Fields"` or `"%1$d/8 Karten, %2$d/6 Felder"`).

### 2.2 Form Presentation Layer (`EditWorkoutScreen.kt` & `EditWorkoutViewModel.kt`)
* In `EditWorkoutScreen.kt` (lines 90–301):
  - The composable collects `val fieldPrefs by viewModel.fieldPreferences.collectAsState()`.
  - Seven form sections are guarded by `if (fieldPrefs.showX) { ... }`:
    - Line 121: `if (fieldPrefs.showCluster)` (Route / Cluster Assignment)
    - Line 209: `if (fieldPrefs.showCommuteTrainer)` (Commute and Trainer checkboxes)
    - Line 226: `if (fieldPrefs.showRace)` (Race / Wettkampf checkbox)
    - Line 237: `if (fieldPrefs.showStravaUpload && TrainingApplication.uploadToCommunity(FileFormat.STRAVA))` (Strava upload)
    - Line 270: `if (fieldPrefs.showDescription)` (Description multiline text field)
    - Line 281: `if (fieldPrefs.showGoal)` (Goal text field)
    - Line 292: `if (fieldPrefs.showMethod)` (Method text field)
* In `EditWorkoutViewModel.kt` (lines 59–64):
  - Exposes `fieldPreferences: StateFlow<EditWorkoutFieldPreferences>` reading from DataStore.

### 2.3 Preferences Storage Layer (`MyPreferenceManager.kt`)
* Lines 61–69: Data class `EditWorkoutFieldPreferences`.
* Lines 137–158: `editWorkoutFieldPreferencesFlow` and `setEditWorkoutFieldPreferences`.
* DataStore keys `EDIT_WORKOUT_SHOW_*`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Remove Sub-block 2 (*"Training bearbeiten"* / `settings_edit_workout_title`) and its 7 toggle items from `WorkoutMasksAndCardsSection` in `AdvancedTuningDialog.kt`.
  2. Remove `editWorkoutPrefs` state, DataStore save, and factory reset references from `AdvancedTuningDialog.kt`.
  3. Update `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` and localized strings (`tuning_summary_masks_cards_format`) to report active workout cards count cleanly (e.g. `"%1$d/8 Cards"` / `"%1$d/8 Karten"`) across all 9 languages.
  4. Ensure `EditWorkoutScreen.kt` displays all standard metadata fields unconditionally (retaining conditional Strava rendering solely based on whether the Strava community connection is active).
  5. Decouple `EditWorkoutViewModel` from `EditWorkoutFieldPreferences`.
  6. Update structural contract tests (`AdvancedTuningAftermathContractTest.kt`, `EditWorkoutFieldsLayoutTest.kt`, `AdvancedTuningAccordionTest.kt`) to enforce the clean removal and unconditional form rendering.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying the 8 Workout List Card customization toggles (`WorkoutCardSectionPreferences`).
  2. Altering `EditWorkoutScreen` data saving, cluster creation, or database persistence logic.
  3. Modifying Matrix Table presentation for Workout Details vs Summary List (reserved strictly for subsequent ticket `ATT-2030`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-211` (*Aftermath: Configurable Fields in Edit Workout Dialog*) and `REQ-UI-216` (*Settings/Aftermath: Relocation of Workout Card Sections and Edit Workout Fields Customization Toggles to Advanced Settings*).
   - Target Release: `V4.9.38`.
   - Parent Epic: `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*).

2. **Historical Origin & Commit Trace**:
   - Commit `4d8961ca` (`ATT-1713`): Initial introduction of `EditWorkoutFieldPreferences` and toggle switches in `DisplaySettingsDialog.kt`.
   - Commit `2965ceb1` (`ATT-1815`): Relocation of Aftermath customization toggles from `DisplaySettingsDialog.kt` to `AdvancedTuningDialog.kt` Category 4.
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

## 5. Architectural Strategy & High-Level Solution

### Component 1: `AdvancedTuningDialog.kt` & `AdvancedTuningAccordion.kt`
* Remove Sub-block 2 (`settings_edit_workout_title` and 7 `TuningToggleItem`s) from `WorkoutMasksAndCardsSection`.
* Rename / simplify section composable to `WorkoutCardsSection` accepting only `workoutCardPrefs: WorkoutCardSectionPreferences`.
* Remove `editWorkoutPrefs` from `AdvancedTuningDialog` state, LaunchedEffect, save handler, and factory reset.
* In `AdvancedTuningAccordion.kt`, update `formatWorkoutMasksSubtitle` to take only `cardPrefs: WorkoutCardSectionPreferences, context: Context` and format `tuning_summary_masks_cards_format` with `activeCards`.
* Update `tuning_summary_masks_cards_format` in all 9 language resource files (`strings.xml`) to `"%1$d/8 Cards"` / `"%1$d/8 Karten"`.

### Component 2: `EditWorkoutScreen.kt` & `EditWorkoutViewModel.kt`
* In `EditWorkoutScreen.kt`, remove `fieldPrefs` collection and remove `if (fieldPrefs.showX)` guards around Route/Cluster, Commute/Trainer, Race, Description, Goal, and Method.
* In `EditWorkoutViewModel.kt`, deprecate or remove `fieldPreferences`.

### Component 3: Test Modernization
* Update `AdvancedTuningAftermathContractTest.kt`: assert that `EditWorkoutFieldPreferences` and `settings_edit_workout_*` are NOT present in `AdvancedTuningDialog.kt`.
* Update `EditWorkoutFieldsLayoutTest.kt`: assert that all fields are unconditionally rendered.
* Update `AdvancedTuningAccordionTest.kt`: verify updated single-argument subtitle formatter.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. No data loss when saving workouts in `EditWorkoutScreen`.
  2. Workout card section toggles in Advanced Settings remain 100% operational.
  3. 9-language translation parity strictly preserved across all `strings.xml`.
* **Risk Rating**: **MINIMAL**
  - Removing UI toggles and simplifying form layout removes conditional complexity without modifying any data schemas or business logic.
