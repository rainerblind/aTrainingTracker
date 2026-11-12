# Stage 5: Walkthrough & Verification - ATT-7320: Add RPE & Feel switch in PhysiologyModelsSection, include showRpeAndFeel in active card count, and migrate RPE_AND_FEEL into detail tabs

**Ticket**: [ATT-7320](https://issue-tracker/ATT-7320)  
**Sub-task**: ATT-7348 (`[Test]`)  
**Parent Epic**: UI Settings / Tuning (Workout Aftermath & Expert Settings)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-WKO-036.1`–`REQ-WKO-036.5`  
**Test Mapping**: `TST-WKO-036.1`–`TST-WKO-036.5`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
This ticket resolves three follow-up defects identified after the introduction of RPE and Feel in ATT-6879:
1. **Settings Parity**: Exposed a top-level `TuningToggleItem` for RPE & Feel in `PhysiologyModelsSection.kt` alongside Morning Sleep & Wellness, fully synchronized with `workoutCardPrefs.showRpeAndFeel` and `WorkoutMasksAndCardsSection.kt`.
2. **Active Card Count & Subtitles**: Included `workoutCardPrefs.showRpeAndFeel` in `activeCardCount` (`WorkoutMasksAndCardsSection.kt`) and `TuningSubtitleFormatter` (`AdvancedTuningAccordion.kt`), properly reflecting the count when toggled.
3. **Tab Deserialization Migration**: Implemented `withRpe` auto-migration in `WorkoutDetailTabConfig.deserializeTabs()`, prepending `WorkoutDetailSectionType.RPE_AND_FEEL` to index 0 of `tab_overview` when absent from all tabs across both v1 and v2 wire strings.
4. **Contract Test & Localization**: Updated `WorkoutSectionReorderContractTest.kt` to verify 13 sections with `RPE_AND_FEEL` at index 0 and added localized string `tuning_rpe_feel_desc` across all 9 canonical languages.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-WKO-036.1` | `TST-WKO-036.1` | Unit Contract Test (`PhysiologyModelsVisibilityContractTest`) | **PASSED** | `Verified` |
| `REQ-WKO-036.2` | `TST-WKO-036.2` | Unit Test (`AdvancedTuningAccordionTest`) | **PASSED** | `Verified` |
| `REQ-WKO-036.3` | `TST-WKO-036.3` | Unit Test (`WorkoutDetailTabsTest`) | **PASSED** | `Verified` |
| `REQ-WKO-036.4` | `TST-WKO-036.4` | Reorder Contract Test (`WorkoutSectionReorderContractTest`) | **PASSED** | `Verified` |
| `REQ-WKO-036.5` | `TST-WKO-036.5` | 9-Language Localization Audit (`strings_rpe_feel.xml`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Suite
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.WorkoutSectionReorderContractTest"
BUILD SUCCESSFUL in 14s
33 actionable tasks: 13 executed, 15 from cache, 5 up-to-date

./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest"
BUILD SUCCESSFUL in 14s
33 actionable tasks: 13 executed, 15 from cache, 5 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* Pure Compose UI state wiring and tab deserialization; zero low-level hardware or sensor changes.
* Visual layout verified in `@Preview` composables (`PhysiologyModelsSectionDefaultPreview` and `PhysiologyModelsSectionAllEnabledPreview`) across Light and Dark themes.
* Verified that `PhysiologyModelsSection.kt` (377 lines), `WorkoutMasksAndCardsSection.kt` (396 lines), and `AdvancedTuningAccordion.kt` (360 lines) strictly comply with the `< 400` line threshold (`AdvancedTuningModularityTest`).

---

## 5. Invariants & Rollback Safety
1. **Default OFF**: Fresh installs and unrated workouts default `showRpeAndFeel = false`.
2. **Non-Destructive Migration**: Custom user tab orders in DataStore are preserved; `RPE_AND_FEEL` is only inserted into `tab_overview` if completely missing from all tabs.
3. **Synchronized State**: Mutating `showRpeAndFeel` in either `PhysiologyModelsSection` or `WorkoutMasksAndCardsSection` updates the shared `workoutCardPrefs` state synchronously.
