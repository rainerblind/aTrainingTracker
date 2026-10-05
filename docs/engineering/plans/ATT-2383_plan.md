# Stage 3: Implementation Plan - ATT-2383: Simplify Column Headers to List and Details in Workout Cards and Details Settings

**Ticket**: [ATT-2383](https://rainerblind.atlassian.net/browse/ATT-2383)  
**Sub-task**: [ATT-2481](https://rainerblind.atlassian.net/browse/ATT-2481) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-271`  
**Test Mapping**: `TST-UI-231`  
**Branch**: `feature/ATT-2383`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Description & Background

In the *Trainingsliste & Details* section of Advanced Settings ([WorkoutMasksAndCardsSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt)), an 8-feature matrix table allows athletes to independently configure section visibility for the Workout Summary List and Workout Details views.

The table header currently uses prepositional phrases for column titles:
* German (`values-de`): `"In Liste"` and `"In Details"`
* English (`values`): `"In List"` and `"In Details"`
* Spanish (`values-es`): `"En lista"` and `"En detalles"`
* French (`values-fr`): `"En liste"` and `"En détails"`
* Italian (`values-it`): `"In elenco"` and `"Nei dettagli"`
* Japanese (`values-ja`): `"一覧"` and `"詳細"`
* Dutch (`values-nl`): `"In lijst"` and `"In details"`
* Polish (`values-pl`): `"Na liście"` and `"W szczegółach"`
* Portuguese (`values-pt`): `"Na lista"` and `"Nos detalhes"`

With each checkbox column standardized to `50.dp` width (introduced in `ATT-2305` / `REQ-UI-264` to maximize horizontal space for feature names like *"Runden-Übersicht"* and *"Telemetrie-Diagramme"* without mid-word hyphenation), multi-word titles with prepositions (e.g., `"In Details"`, `"W szczegółach"`, `"Nei dettagli"`) collide horizontally and wrap across two lines (e.g. `"In\nDetails"`). This disrupts vertical alignment, misaligns header baselines, and degrades visual polish.

Removing redundant prepositions converts headers into concise singular nouns (*"List"* / *"Details"*, *"Liste"* / *"Details"*, *"Lista"* / *"Szczegóły"*) that fit cleanly within the 50.dp column bound without wrapping while retaining 100% semantic clarity.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-271` (*Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix*)
  * Refines Clause 4 of `REQ-UI-264` and Clause 3 of `REQ-UI-240`.
  * Enforces preposition-free singular noun titles across all 9 locales.
  * Mandates defensive single-line typography (`maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `textAlign = TextAlign.Center`) in `WorkoutMasksAndCardsSection.kt`.
* **Test Mapping**: `TST-UI-231` (*Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix Verification*)
  * Defensive UI contract tests in `WorkoutMasksAndCardsLayoutTest.kt`.
  * 100% 9-language translation parity in `TranslationParityTest.kt`.
  * Full clean-room test suite verification.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly without regression.
2. **DataStore Preference Integrity**: All DataStore preference keys (`workout_card_show_*`, `workout_detail_show_*`) and underlying serialization/deserialization logic remain strictly untouched.
3. **Touch Target Standard**: 50.dp column containers and 48x48dp minimum interactive touch targets are strictly preserved.
4. **9-Language Localization Parity**: All 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) maintain 100% key parity with zero missing or mismatching string resources.
5. **Code Metric Invariant**: `WorkoutMasksAndCardsSection.kt` remains strictly under 400 lines of code (`REQ-UI-262`).
6. **Subtask Self-Sufficiency**: Subtask [ATT-2481](https://rainerblind.atlassian.net/browse/ATT-2481) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2383](https://rainerblind.atlassian.net/browse/ATT-2383) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Localized String Resources (`strings.xml` across 9 Locales)
Update string resources `tuning_matrix_col_list` and `tuning_matrix_col_details`:
* `app/src/main/res/values/strings.xml`: `"List"` / `"Details"`
* `app/src/main/res/values-de/strings.xml`: `"Liste"` / `"Details"`
* `app/src/main/res/values-es/strings.xml`: `"Lista"` / `"Detalles"`
* `app/src/main/res/values-fr/strings.xml`: `"Liste"` / `"Détails"`
* `app/src/main/res/values-it/strings.xml`: `"Elenco"` / `"Dettagli"`
* `app/src/main/res/values-ja/strings.xml`: `"一覧"` / `"詳細"` (preserved, already optimal)
* `app/src/main/res/values-nl/strings.xml`: `"Lijst"` / `"Details"`
* `app/src/main/res/values-pl/strings.xml`: `"Lista"` / `"Szczegóły"`
* `app/src/main/res/values-pt/strings.xml`: `"Lista"` / `"Detalhes"`

### Component 2: Compose Layout & Typography (`WorkoutMasksAndCardsSection.kt`)
Update the two header `Text` composables inside their respective `Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center)`:
* Add `maxLines = 1`
* Add `overflow = TextOverflow.Ellipsis`
* Add `textAlign = TextAlign.Center`

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: [WorkoutMasksAndCardsSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt) in Advanced Settings (`AdvancedTuningDialog.kt`).
* **Reused components**: Material 3 `Text` composable, `MaterialTheme.typography.labelMedium`, `FontWeight.Bold`, `MaterialTheme.colorScheme.primary`.
* **Theme tokens**: Typography `MaterialTheme.typography.labelMedium`, color `MaterialTheme.colorScheme.primary`, column width `50.dp`, spacer `32.dp`.
* **New one-off styles & justification**: None. Reuses existing theme typography and layout tokens with standard defensive single-line truncation.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update String Resources across all 9 Locales
* **Files**:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`
* **Changes**: Replace prepositional phrases with concise singular nouns for `tuning_matrix_col_list` and `tuning_matrix_col_details`.

### Step 2: Defensive Header Typography in `WorkoutMasksAndCardsSection.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt`
* **Changes**: Add `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center` to both header `Text` composables.

### Step 3: Implement Unit Contract Test in `WorkoutMasksAndCardsLayoutTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Changes**: Add `@Test fun testHeaderTypographyAndDefensiveTruncationContract()` verifying single-line centering and ellipsis handling for matrix column headers.

### Step 4: Targeted Unit & Localization Parity Verification
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.WorkoutMasksAndCardsLayoutTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAftermathContractTest"
  ```
* **Success Criteria**: 100% test pass rate with zero failures.

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted contract and localization unit tests passing with 0 failures.
  2. Clean-room regression suite (`./gradlew testDebugUnitTest`) verifying 0 regressions.
  3. Automated Gate 4 and Gate 5 reviews before in-sprint merge.
* **Rollback**:
  - All modifications are isolated to branch `feature/ATT-2383`.
  - In case of failure, changes can be rolled back via git checkout without affecting `sprint/2026-41.1` or `develop`.
