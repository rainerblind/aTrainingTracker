# Stage 2: Requirement & Test Specification - ATT-7320: Add RPE & Feel switch in PhysiologyModelsSection, include showRpeAndFeel in active card count, and migrate RPE_AND_FEEL into detail tabs

**Ticket**: [ATT-7320](https://issue-tracker/ATT-7320)  
**Parent Epic**: UI Settings / Tuning (Workout Aftermath & Expert Settings)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.4` | **Branch**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-WKO-036.1`–`REQ-WKO-036.5` | **Test Spec ID**: `TST-WKO-036.1`–`TST-WKO-036.5`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-WKO-036.1..036.5)

### 1.1 Functional & Architectural Requirements
* **REQ-WKO-036.1** (*Synchronized PhysiologyModelsSection Switch*): `PhysiologyModelsSection` SHALL provide a top-level `TuningToggleItem` for RPE & Feel bound to `showRpeAndFeel` in `WorkoutCardSectionPreferences`, maintaining bidirectional synchronization with `WorkoutMasksAndCardsSection`.
* **REQ-WKO-036.2** (*Active Card Count & Subtitle Invariant*): `WorkoutMasksAndCardsSection.activeCardCount` and `AdvancedTuningAccordion` (`formatWorkoutMatrixSubtitle`, `formatWorkoutMasksSubtitle`) SHALL include `workoutCardPrefs.showRpeAndFeel` in active card count calculations (`count { it }` across all 13 summary sections).
* **REQ-WKO-036.3** (*WorkoutDetailTabConfig Upgrade Migration*): `WorkoutDetailTabConfig.deserializeTabs()` SHALL auto-migrate `WorkoutDetailSectionType.RPE_AND_FEEL` to index 0 of `tab_overview` (or `preset == OVERVIEW`) if `RPE_AND_FEEL` is absent from all tabs across both v1 and v2 wire payloads.
* **REQ-WKO-036.4** (*Reorder Contract Test Parity*): `WorkoutSectionReorderContractTest` SHALL assert 13 sections in `DEFAULT_ORDER` with `RPE_AND_FEEL` at index 0 and verify swap logic with `DETAILS` at index 1.
* **REQ-WKO-036.5** (*9-Language Localization Parity*): Summary string `tuning_rpe_feel_desc` SHALL be defined across all 9 canonical locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) in `strings_rpe_feel.xml`.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Switch Synchronization)**: *Given* `AdvancedTuningScreen` or `AdvancedTuningDialog`, *When* toggling RPE & Feel in `PhysiologyModelsSection`, *Then* `workoutCardPrefs.showRpeAndFeel` updates, the checkbox in `WorkoutMasksAndCardsSection` reflects the change immediately, and saving persists the state.
* **Criterion 2 (Active Count Reflection)**: *Given* `WorkoutMasksAndCardsSection` and accordion header, *When* `showRpeAndFeel` is enabled, *Then* `activeCardCount` and `formatWorkoutMasksSubtitle` increment by 1.
* **Criterion 3 (Tab Deserialization Migration)**: *Given* an existing persisted wire string lacking `RPE_AND_FEEL`, *When* deserialized by `WorkoutDetailTabConfig.deserializeTabs()`, *Then* `RPE_AND_FEEL` is prepended to `tab_overview` at index 0 without modifying custom tab structures.
* **Criterion 4 (Contract Test Green)**: *Given* `WorkoutSectionReorderContractTest`, *When* executed, *Then* all tests pass verifying 13 sections and reordering logic.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-WKO-036` (*Section Ordering & Progressive Disclosure*), refining `REQ-UI-255` (*Customizable Section Reordering*) and `REQ-UI-408` (*Multi-Tab Aftermath Architecture*).
2. *Historical Origin & Commit Trace*: `ATT-6879` introduced `RPE_AND_FEEL` to `DEFAULT_ORDER` and `DEFAULT_TABS`, but omitted settings switch parity in `PhysiologyModelsSection`, omitted `showRpeAndFeel` from card count sums, and lacked auto-migration in `deserializeTabs`.
3. *Root Reason for Existing Formulation*: Focus was on `EditWorkoutScreen` and card rendering; the toggle was left solely in the section reorder matrix.
4. *Preservation of Core Invariants*: Default `OFF` (`showRpeAndFeel = false`) preserved; existing custom tab structures preserved; pure JVM contract test integrity maintained.

---

## 2. Test Specification (TST-WKO-036.1..036.5)

### Test Case 1: `testPhysiologyModelsSection_rpeFeelSwitchContract` (`TST-WKO-036.1`)
* **Scope**: Contract Test (`PhysiologyModelsVisibilityContractTest.kt`)
* **Action**: Verify `PhysiologyModelsSection` composable declares `showRpeAndFeel` and `onShowRpeAndFeelChange` parameters and binds them to UI.

### Test Case 2: `testWorkoutMasksAndCards_activeCardCountIncludesRpeFeel` (`TST-WKO-036.2`)
* **Scope**: Unit & Subtitle Formatter Test (`AdvancedTuningAccordionTest.kt`)
* **Action**: Assert `formatWorkoutMasksSubtitle` and `formatWorkoutMatrixSubtitle` count 13 items when all enabled and increment count when only `showRpeAndFeel` is true.

### Test Case 3: `testWorkoutDetailTabConfig_autoMigratesRpeFeelToOverviewIndex0` (`TST-WKO-036.3`)
* **Scope**: Unit Test (`WorkoutDetailTabsTest.kt`)
* **Action**: Pass legacy v1 and v2 wire payloads omitting `RPE_AND_FEEL`; assert deserialized `tab_overview` contains `RPE_AND_FEEL` at index 0.

### Test Case 4: `testReorderingLogic_moveUpAndDown` (`TST-WKO-036.4`)
* **Scope**: Unit Test (`WorkoutSectionReorderContractTest.kt`)
* **Action**: Verify swap logic on `DEFAULT_ORDER` (13 items): moving index 1 (`DETAILS`) to index 0 results in `DETAILS` at index 0 and `RPE_AND_FEEL` at index 1 with total size 13.

### Test Case 5: `testRpeFeelStrings_9LanguageParity` (`TST-WKO-036.5`)
* **Scope**: Localization Test (`AftermathTuningLocalizationTest.kt` / `WorkoutDetailTabsTest.kt`)
* **Action**: Assert `tuning_rpe_feel_desc` exists and is non-empty across all 9 canonical locales.

---

## 3. Traceability Matrix

| Requirement ID | Test Case ID | Test Class / Method | Verification Target | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-WKO-036.1` | `TST-WKO-036.1` | `PhysiologyModelsVisibilityContractTest` | `PhysiologyModelsSection.kt` | Specified |
| `REQ-WKO-036.2` | `TST-WKO-036.2` | `AdvancedTuningAccordionTest` | `AdvancedTuningAccordion.kt`, `WorkoutMasksAndCardsSection.kt` | Specified |
| `REQ-WKO-036.3` | `TST-WKO-036.3` | `WorkoutDetailTabsTest` | `WorkoutDetailTabConfig.kt` (`deserializeTabs`) | Specified |
| `REQ-WKO-036.4` | `TST-WKO-036.4` | `WorkoutSectionReorderContractTest` | `WorkoutSectionType.DEFAULT_ORDER` (13 sections) | Specified |
| `REQ-WKO-036.5` | `TST-WKO-036.5` | `AftermathTuningLocalizationTest` | `strings_rpe_feel.xml` (9 locales) | Specified |
