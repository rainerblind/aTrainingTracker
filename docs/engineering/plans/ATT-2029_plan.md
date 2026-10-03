# Stage 3: Implementation Plan - ATT-2029: [Settings/Aftermath] Remove Edit Workout Dialog Field Toggles from Advanced Settings

**Ticket**: [ATT-2029](https://rainerblind.atlassian.net/browse/ATT-2029)  
**Sub-task**: [ATT-2084](https://rainerblind.atlassian.net/browse/ATT-2084) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-239` (*Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles from Advanced Settings*)  
**Test Mapping**: `TST-UI-198` (*Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles Verification*)  
**Branch**: `feature/ATT-2029`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In `ATT-1713`, `ATT-1815`, and `ATT-2005`, seven toggle switches (`showDescription`, `showCluster`, `showCommuteTrainer`, `showRace`, `showStravaUpload`, `showGoal`, `showMethod`) were introduced under a *"Training bearbeiten"* (Edit Workout Fields) section in Advanced Settings (`AdvancedTuningDialog.kt`), allowing users to hide metadata fields inside the Edit Workout dialog (`EditWorkoutScreen.kt`).

However, hiding form fields in an intentional edit modal violates user mental models:
1. When an athlete deliberately taps "Edit Workout", they expect all available metadata attributes (Name, Route/Cluster, Sport, Equipment, Commute, Trainer, Race, Description, Goal, Method, and conditional Strava upload) to be directly accessible and editable without having to navigate to Expert Settings to un-hide individual inputs.
2. The presence of these 7 switches in Section 5 ("Workout-Karten & Eingabemasken") creates unnecessary visual bloat and state management overhead in `AdvancedTuningDialog.kt`.
3. The accordion subtitle in `AdvancedTuningAccordion.kt` currently formats both card counts and field counts (`%1$d/8 Cards, %2$d/6 Fields`), which must be simplified to format only active card sections (`%1$d/8 Cards`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-239` (*Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles from Advanced Settings*)
* **Test Mapping**: `TST-UI-198` (*Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles Verification*)
  * `TST-UI-198.1`: Structural Contract Verification (`AdvancedTuningAftermathContractTest.kt`)
  * `TST-UI-198.2`: Unconditional Form Layout Contract Test (`EditWorkoutFieldsLayoutTest.kt`)
  * `TST-UI-198.3`: Accordion Subtitle Formatting Test (`AdvancedTuningAccordionTest.kt`)
  * `TST-UI-198.4`: 9-Language Localization Audit (`TranslationParityTest.kt`)
  * `TST-UI-198.5`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All existing unit tests and UI contracts must continue to pass cleanly.
2. **Core Anchor Invariance**: Core metadata fields (Workout Name, Sport Type dropdown, Equipment dropdown) continue to be rendered as mandatory anchors in `EditWorkoutScreen.kt`.
3. **Strava Upload Conditional Logic**: The Strava upload checkbox remains conditionally rendered based exclusively on whether the Strava community connection is active (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`).
4. **Workout List Cards Invariance**: The 8 Workout List Card section customization toggles (`WorkoutCardSectionPreferences`) in Advanced Settings Section 5 remain 100% operational.
5. **Data Preservation Invariant**: Saving workouts from `EditWorkoutScreen.kt` must preserve all entered and untouched data without corruption or loss.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2029` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AdvancedTuningDialog.kt` (UI Layer)
* Remove Sub-block 2 (*"Training bearbeiten"* / `settings_edit_workout_title`) and its 7 toggle switches.
* Remove `HorizontalDivider` separating card toggles and field toggles.
* Update `WorkoutMasksAndCardsSection` parameters:
  - Keep `workoutCardPrefs: WorkoutCardSectionPreferences` and `onWorkoutCardPrefsChange: (WorkoutCardSectionPreferences) -> Unit`.
  - Delete `editWorkoutPrefs: EditWorkoutFieldPreferences` and `onEditWorkoutPrefsChange: (EditWorkoutFieldPreferences) -> Unit`.
* In `AdvancedTuningDialog`:
  - Remove `var editWorkoutPrefs by remember { mutableStateOf(EditWorkoutFieldPreferences()) }`.
  - Remove persistence save call `preferenceManager.setEditWorkoutFieldPreferences(editWorkoutPrefs)`.
  - Remove factory reset call `preferenceManager.setEditWorkoutFieldPreferences(EditWorkoutFieldPreferences())`.
  - Remove import of `EditWorkoutFieldPreferences`.

### Component 2: `AdvancedTuningAccordion.kt` (UI / Presentation Layer)
* Update `TuningSubtitleFormatter.formatWorkoutMasksSubtitle`:
  - New signature: `fun formatWorkoutMasksSubtitle(cardPrefs: WorkoutCardSectionPreferences, context: Context): String`.
  - Calculate `activeCards` count (0..8) and format via `context.getString(R.string.tuning_summary_masks_cards_format, activeCards)`.
  - Retain a `@Deprecated` backward-compatible overload `formatWorkoutMasksSubtitle(cardPrefs, editPrefs, context)` delegating to `formatWorkoutMasksSubtitle(cardPrefs, context)`.
* In `AdvancedTuningDialog.kt`, update Section 5 accordion header subtitle provider to call `TuningSubtitleFormatter.formatWorkoutMasksSubtitle(workoutCardPrefs, context)`.

### Component 3: `strings.xml` (Localization Layer across 9 Locales)
* Update `tuning_summary_masks_cards_format` across all 9 localized resource files to a single positional format argument (`%1$d`):
  - `values/`: `"%1$d/8 Cards"`
  - `values-de/`: `"%1$d/8 Karten"`
  - `values-es/`: `"%1$d/8 tarjetas"`
  - `values-fr/`: `"%1$d/8 cartes"`
  - `values-it/`: `"%1$d/8 schede"`
  - `values-ja/`: `"%1$d/8 カード"`
  - `values-nl/`: `"%1$d/8 kaarten"`
  - `values-pl/`: `"%1$d/8 kart"`
  - `values-pt/`: `"%1$d/8 cartões"`

### Component 4: `EditWorkoutScreen.kt` & `EditWorkoutViewModel.kt` (UI & ViewModel Layer)
* In `EditWorkoutScreen.kt`:
  - Remove `val fieldPrefs by viewModel.fieldPreferences.collectAsState()`.
  - Remove conditional guards:
    - Route / Cluster selector: Render unconditionally.
    - Commute and Trainer checkboxes: Render unconditionally.
    - Race checkbox: Render unconditionally.
    - Strava upload checkbox: Guarded solely by `if (TrainingApplication.uploadToCommunity(FileFormat.STRAVA))`.
    - Description multiline text field: Render unconditionally.
    - Goal text field: Render unconditionally.
    - Method text field: Render unconditionally.
* In `EditWorkoutViewModel.kt`:
  - Remove or mark `@Deprecated` `fieldPreferences: StateFlow<EditWorkoutFieldPreferences>`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Resource Localization Harmonization (9 Locales)
* Files: `app/src/main/res/values*/strings.xml`
* Changes: Update `tuning_summary_masks_cards_format` in all 9 directories to `%1$d/8 Cards` (and translated equivalents).

### Step 2: Advanced Tuning Accordion Subtitle Modernization
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* Changes: Add single-argument `formatWorkoutMasksSubtitle(cardPrefs, context)` and preserve `@Deprecated` overload.

### Step 3: Advanced Tuning Dialog Clean-Up
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* Changes: Excise Sub-block 2, remove `editWorkoutPrefs` state, save call, and reset call. Wire Section 5 to use single-argument subtitle formatter.

### Step 4: Unconditional Form Presentation in EditWorkoutScreen
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`
* Changes: Remove `fieldPrefs` collection and conditional guards around Route/Cluster, Commute/Trainer, Race, Description, Goal, Method. Strava upload depends solely on community connection.

### Step 5: Test Modernization & Verification
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAftermathContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutFieldsLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordionTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit and contract tests in Stage 4.
  - 100% full-suite clean-room regression test (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**:
  - Feature branch `feature/ATT-2029` is isolated from `sprint/2026-40.12`. Reverting commit or resetting branch cleanly restores the previous state without affecting other sprint tickets.
