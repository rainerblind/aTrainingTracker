# Stage 3: Implementation Plan - ATT-7320: Add RPE & Feel switch in PhysiologyModelsSection, include showRpeAndFeel in active card count, and migrate RPE_AND_FEEL into detail tabs

**Ticket**: [ATT-7320](https://issue-tracker/ATT-7320)  
**Parent Epic**: UI Settings / Tuning (Workout Aftermath & Expert Settings)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-WKO-036.1`–`REQ-WKO-036.5`  
**Test Mapping**: `TST-WKO-036.1`–`TST-WKO-036.5`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
In ATT-6879, RPE and Feel tracking was introduced into `EditWorkoutScreen`, `WorkoutSummaries` (DB v30), Garmin FIT export/import, and section models. However, three gaps remain:
1. Users expect an RPE & Feel master toggle in `PhysiologyModelsSection` alongside Morning Sleep & Wellness, synchronized with `workoutCardPrefs.showRpeAndFeel`.
2. `WorkoutMasksAndCardsSection.activeCardCount` and `AdvancedTuningAccordion` subtitle formatters omit `showRpeAndFeel` from their active card count sums.
3. Upgraded devices with persisted v1/v2 tab payloads omit `RPE_AND_FEEL` because `WorkoutDetailTabConfig.deserializeTabs()` lacks auto-migration for `RPE_AND_FEEL`.
4. `WorkoutSectionReorderContractTest.kt` fails because it asserts 12 sections with `DETAILS` at index 0 instead of 13 sections with `RPE_AND_FEEL` at index 0.

---

## 2. Traceability & Requirements Mapping
* **REQ-WKO-036.1** $\rightarrow$ `TST-WKO-036.1` (`PhysiologyModelsVisibilityContractTest`): RPE & Feel toggle in `PhysiologyModelsSection`.
* **REQ-WKO-036.2** $\rightarrow$ `TST-WKO-036.2` (`AdvancedTuningAccordionTest`): Include `showRpeAndFeel` in `activeCardCount` and subtitles.
* **REQ-WKO-036.3** $\rightarrow$ `TST-WKO-036.3` (`WorkoutDetailTabsTest`): Auto-migrate `RPE_AND_FEEL` to index 0 of `tab_overview` when absent.
* **REQ-WKO-036.4** $\rightarrow$ `TST-WKO-036.4` (`WorkoutSectionReorderContractTest`): 13 sections in `DEFAULT_ORDER` swap logic.
* **REQ-WKO-036.5** $\rightarrow$ `TST-WKO-036.5` (`AftermathTuningLocalizationTest`): 9-language localization parity for `tuning_rpe_feel_desc`.

---

## 3. System Invariants & Preserved Behavior
1. **Default OFF Invariant**: `showRpeAndFeel` defaults to `false` for fresh and unrated profiles.
2. **Settings Synchronization Invariant**: Toggling RPE & Feel in `PhysiologyModelsSection` updates `workoutCardPrefs.showRpeAndFeel`, keeping `WorkoutMasksAndCardsSection` synchronized.
3. **Non-Destructive Migration Invariant**: Tab deserialization only inserts `RPE_AND_FEEL` at index 0 of `tab_overview` if absent from all tabs.
4. **Modularity Invariant**: All files in `ui/settings/tuning/` strictly remain `< 400` lines.
5. **Human Gate Invariant**: Parent ticket terminal state stops at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### UI Consistency (Rule 23)
* **Reference screen / component**: `PhysiologyModelsSection.kt` (Model 7 Morning Sleep & Wellness toggle), `TuningToggleItem`.
* **Reused components**: `TuningToggleItem`, `HorizontalDivider`, `stringResource(R.string.settings_workout_card_rpe_feel)`.
* **Theme tokens**: `MaterialTheme.colorScheme.outlineVariant`, standard 12.dp spacing.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Strings (`REQ-WKO-036.5`, `TST-WKO-036.5`)
* Add `tuning_rpe_feel_desc` in `app/src/main/res/values*/strings_rpe_feel.xml` across all 9 canonical locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### Step 2: Settings UI & Switch Synchronization (`REQ-WKO-036.1`, `TST-WKO-036.1`)
* In `PhysiologyModelsSection.kt`: Add `showRpeAndFeel: Boolean = false`, `onShowRpeAndFeelChange: (Boolean) -> Unit = {}`, and render `TuningToggleItem` for RPE & Feel. Compact internal lines to ensure `< 400` lines.
* In `AdvancedTuningDialog.kt` & `AdvancedTuningScreen.kt`: Wire `showRpeAndFeel = workoutCardPrefs.showRpeAndFeel` and `onShowRpeAndFeelChange = { workoutCardPrefs = workoutCardPrefs.copy(showRpeAndFeel = it) }`.

### Step 3: Active Card Count & Subtitle Formatting (`REQ-WKO-036.2`, `TST-WKO-036.2`)
* In `WorkoutMasksAndCardsSection.kt`: Add `workoutCardPrefs.showRpeAndFeel` to `activeCardCount` list (total 13 items).
* In `AdvancedTuningAccordion.kt`: Add `cardPrefs.showRpeAndFeel` to `formatWorkoutMatrixSubtitle` and `formatWorkoutMasksSubtitle`.

### Step 4: Tab Deserialization Upgrade Migration (`REQ-WKO-036.3`, `TST-WKO-036.3`)
* In `WorkoutDetailTabConfig.kt` (`deserializeTabs`): Add `withRpe` step prepending `WorkoutDetailSectionType.RPE_AND_FEEL` to index 0 of `tab_overview` if absent from all tabs.

### Step 5: Test Verification & Fixes (`REQ-WKO-036.1`..`036.5`)
* Update `WorkoutSectionReorderContractTest.kt` to assert 13 sections with `RPE_AND_FEEL` at 0 and `DETAILS` at 1.
* Update `WorkoutDetailTabsTest.kt` expectations for wire migration and add `TST-UI-453_4c`.
* Run test suite under sprint lock (`python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest`).
