# Stage 1 Analysis: ATT-7320 - Add RPE & Feel switch in PhysiologyModelsSection, include showRpeAndFeel in active card count, and migrate RPE_AND_FEEL into detail tabs

**Ticket**: [ATT-7320](https://issue-tracker/ATT-7320)  
**Parent Epic**: UI Settings / Tuning (Workout Aftermath & Expert Settings)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Branch**: `sprint/2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Following the introduction of RPE and Feel tracking in ATT-6879, three usability and architectural gaps were identified:
1. **Discoverability & Synchronization**: Users expect a dedicated top-level switch for RPE & Feel in Expert Settings (`PhysiologyModelsSection` alongside Morning Sleep & Wellness) synchronized with `showRpeAndFeel`, rather than having to hunt for a checkbox buried within the collapsed reorderable matrix of `WorkoutMasksAndCardsSection`.
2. **Active Card Count Omission**: In `WorkoutMasksAndCardsSection.kt` (lines 140-144) and `AdvancedTuningAccordion.kt` (lines 164 & 170, `formatWorkoutMatrixSubtitle` / `formatWorkoutMasksSubtitle`), `workoutCardPrefs.showRpeAndFeel` is omitted from `count { it }`, causing the active card count badge to remain stale when RPE & Feel is toggled.
3. **Upgrade Tab Migration**: In `WorkoutDetailTabConfig.kt` (`deserializeTabs`), legacy v1 and v2 payloads auto-migrate `PHOTOS` and `GRADE_DISTRIBUTION` if missing, but lack auto-migration for `RPE_AND_FEEL`. Upgraded installations with existing persisted tab configs miss `RPE_AND_FEEL` from `tab_overview` unless manually reset.
4. **Contract Test Desynchronization**: `WorkoutSectionReorderContractTest.kt` expects 12 sections with `DETAILS` at index 0 and `DESCRIPTION` at index 1, failing because ATT-6879 introduced `RPE_AND_FEEL` at index 0 (13 total sections).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause 1 (Missing Settings Switch)**: `PhysiologyModelsSection.kt` only exposes toggles for Coggan, PMC, Aerobic Efficiency, Critical Power, Cardio-Power Dynamics, DFA a1, and Morning Sleep & Wellness. It lacks a toggle for RPE & Feel bound to `workoutCardPrefs.showRpeAndFeel`.
* **Root Cause 2 (Hardcoded Count Lists)**: `WorkoutMasksAndCardsSection.kt:140-144` defines `activeCardCount` using a hardcoded list of 12 booleans omitting `workoutCardPrefs.showRpeAndFeel`. Similarly, `AdvancedTuningAccordion.kt:164,170` formats subtitles counting only 8 legacy fields.
* **Root Cause 3 (Omitted Tab Migration Branch)**: `WorkoutDetailTabConfig.kt:448-494` executes `withPhotos` and `withGrade` migrations, but has no `withRpe` step to prepend `WorkoutDetailSectionType.RPE_AND_FEEL` to `tab_overview` when absent from all tabs.
* **Independent Peer Review & Verification**:
  * Confirmed that `showRpeAndFeel` already exists in `WorkoutCardSectionPreferences` (`MyPreferenceManager.kt`) and is bound in `WorkoutMasksAndCardsSection.kt:338`.
  * Verified that `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` hold `workoutCardPrefs` state and can seamlessly wire `showRpeAndFeel` and `onShowRpeAndFeelChange` into `PhysiologyModelsSection`.
  * Verified that inserting `RPE_AND_FEEL` at index 0 of `tab_overview` matches `DEFAULT_TABS[0]` structure.
  * Verified that updating `WorkoutSectionReorderContractTest.kt` to reflect 13 sections and `RPE_AND_FEEL` at index 0 resolves the contract test failure.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  1. Add `showRpeAndFeel` toggle with localized title (`settings_workout_card_rpe_feel`) and summary description (`tuning_rpe_feel_desc`, 9 languages) to `PhysiologyModelsSection.kt`.
  2. Wire `showRpeAndFeel` and `onShowRpeAndFeelChange` in `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt` to keep it synchronized with `workoutCardPrefs`.
  3. Include `workoutCardPrefs.showRpeAndFeel` in `activeCardCount` (`WorkoutMasksAndCardsSection.kt`) and `TuningSubtitleFormatter` (`AdvancedTuningAccordion.kt`).
  4. Implement `withRpe` migration in `WorkoutDetailTabConfig.deserializeTabs()` inserting `RPE_AND_FEEL` at index 0 of `tab_overview` if absent from all tabs.
  5. Update `WorkoutSectionReorderContractTest.kt` for 13 sections and `RPE_AND_FEEL` at index 0.
  6. Add unit test coverage for tab auto-migration and subtitle active counts.
* **Out-of-Scope Non-Goals**:
  - No changes to database schema or `WorkoutSummariesDatabaseManager` (already at DB_VERSION 30).
  - No modifications to FIT file reader/writer or Garmin FIT SDK integration.
  - No alteration of `WorkoutRpeFeelSection` aftermath UI or Borg CR10 scales.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-508` (*Per-Workout RPE & Subjective Feel Tracking*), `REQ-UI-255` (*Customizable Section Reordering*), and `REQ-UI-408` (*Multi-Tab Aftermath Architecture*).
* **Historical Origin & Commit Trace**: `ATT-6879` introduced `RPE_AND_FEEL` as section 0 in `WorkoutSectionType.DEFAULT_ORDER` and `DEFAULT_TABS`, but omitted settings switch parity and migration for existing persisted tabs.
* **Root Reason for Existing Formulation**: The initial implementation focused on `EditWorkoutScreen` and card rendering, leaving the settings switch solely in the section reorder matrix.
* **Preservation of Core Invariants**:
  - Default `OFF` (`showRpeAndFeel = false`) invariant is strictly preserved.
  - Synchronization invariant: Toggling the switch in `PhysiologyModelsSection` immediately reflects in `WorkoutMasksAndCardsSection` and vice versa.
  - Non-destructive tab migration: Only prepends `RPE_AND_FEEL` to `tab_overview` when `RPE_AND_FEEL` is missing from all tabs, never overriding custom placement.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-UI-508`, `REQ-UI-255`, `REQ-UI-408`.
* **Affected Architecture Elements**:
  - `PhysiologyModelsSection.kt`
  - `WorkoutMasksAndCardsSection.kt`
  - `AdvancedTuningAccordion.kt`
  - `AdvancedTuningDialog.kt`
  - `AdvancedTuningScreen.kt`
  - `WorkoutDetailTabConfig.kt`
  - `strings_rpe_feel.xml` (all 9 language locales)
* **Verification Impact & Test Surface**:
  - `WorkoutSectionReorderContractTest.kt`
  - `WorkoutDetailTabsTest.kt` (verify auto-migration of `RPE_AND_FEEL`)
  - `AdvancedTuningAccordionTest.kt` / `WorkoutMasksAndCardsLayoutTest.kt`
* **Risk Assessment & Invariants**: **LOW**. No database migrations, purely UI state binding and tab configuration deserialization normalization.
