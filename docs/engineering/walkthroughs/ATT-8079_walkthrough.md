# Stage 5: Walkthrough & Verification - ATT-8079: Persist Unchecked Detail Sections, Decouple Summary Toggles & Reserve Header Width

**Ticket**: [ATT-8079](https://atrainingtracker.atlassian.net/browse/ATT-8079) (`2 SP`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_02_Spät` (ID 460)  
**Requirement Mapping**: `REQ-UI-2787` (`REQ-UI-2787.1`–`.4`) | **Test Mapping**: `TST-UI-2787` (`TST-UI-2787.1`–`.4`)  
**Branch**: `feature/ATT-8079` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Resolved all three Workout Details configuration and layout defects cleanly on `feature/ATT-8079`:
* **`v3|` Tab Persistence (`REQ-UI-2787.1`, `WorkoutDetailTabConfig.kt`)**: Bumped `SCHEMA_PREFIX` to `"v3|"` (`SCHEMA_PREFIX_V2 = "v2|"`) and gated `GRADE_DISTRIBUTION`, `RPE_AND_FEEL`, and `HRV` auto-migrations strictly on `!raw.startsWith("v3|")` (keeping `if (isV1)` for `PHOTOS`), so user removals in `WorkoutDetailTabsConfigSheet` persist across `serializeTabs` $\rightarrow$ `deserializeTabs` while `"v1|"`/`"v2|"` upgrades pass untouched.
* **Summary Toggle Decoupling (`REQ-UI-2787.2`, `WorkoutDetailTabbedContent.kt`)**: Passed `true` for `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, and `showCriticalPowerInWorkoutSummary` in `RenderWorkoutDetailCardSections` (`WorkoutDetailSectionType.DETAILS`, matching `CardioPowerDynamicsCard`), keeping full Workout Details independent while `WorkoutSummary.kt` continues gating compact list cards.
* **Header Custom `actions()` Width Reservation (`REQ-UI-2787.3`, `WorkoutHeader.kt`)**: Added `Modifier.onSizeChanged` width measurement on the Top-End `actionButtonsContent()` `Row` combined via `maxOf` with Frame-0 `actionSpacerWidth` (`ATT-8097`), preventing 2-line portrait title overlap even with custom `actions()`.
* **Tab-Driven Settings Subtitle & Zone Gating (`REQ-UI-2787.4`, `AdvancedTuning*`, `WorkoutMasksAndCardsSection.kt`, `values*/strings_workout_details.xml`)**: Added 9-locale `tuning_summary_matrix_tabs_format`, added `detailTabs` overload on `TuningSubtitleFormatter.formatWorkoutMatrixSubtitle` (retaining legacy 3-arg signature), wired `persistedDetailTabs` in `AdvancedTuningScreen` & `AdvancedTuningDialog`, and gated `ZoneDisplayModeSelector` on `WorkoutDetailTabConfig.hasAnyZoneOrGradeSection(resolvedDetailTabs)` while reducing `WorkoutMasksAndCardsSection.kt` to **394 LoC** (`< 400`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-2787.1` | `TST-UI-2787.1` | `WorkoutDetailTabsTest` (`v3\|` persistence + `v1`/`v2` migrations) | **PASSED** (20/20) | `Verified` |
| `REQ-UI-2787.2` | `TST-UI-2787.2` | `WorkoutDetailSummaryDecouplingTest` (`createComposeRule()` Details vs Summary) | **PASSED** (2/2) | `Verified` |
| `REQ-UI-2787.3` | `TST-UI-2787.3` | `WorkoutDetailHeaderActionsComposeTest` (`360dp` custom `actions()` + `Rule 8.5` PNG) | **PASSED** (6/6) | `Verified` |
| `REQ-UI-2787.4` | `TST-UI-2787.4` | `WorkoutMasksAndCardsBehaviorTest` (1/1) + `LayoutTest` (10/10) + `AccordionTest` (17/17) + `TranslationParityTest` (10/10) | **PASSED** (38/38) | `Verified` |

---

## 3. Automated Test & Visual Verification Evidence (`Rule 7.5` Evidence Reuse)
* **Code Integrity**: Zero source or test edits after Gate 4 PASS; Stage 4 JUnit XML results reused directly per `Rule 7.5`.
* **Executed Tier 1 Scope** (`python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest ...`, `BUILD SUCCESSFUL in 5m 57s` — **94/94 PASSED**, 0 failed, 0 skipped):
  * `WorkoutDetailTabsTest` (20/20), `WorkoutDetailSummaryDecouplingTest` (2/2), `WorkoutDetailTabbedContentTest` (7/7), `WorkoutDetailHeaderActionsComposeTest` (6/6), `WorkoutMasksAndCardsBehaviorTest` (1/1), `WorkoutMasksAndCardsLayoutTest` (10/10), `AdvancedTuningAccordionTest` (17/17), `AdvancedTuningAftermathContractTest` (2/2), `TranslationParityTest` (10/10), `UiReflectionOnlyTestAuditContractTest` (4/4), `SourceTextReadingAuditContractTest` (4/4), `ArchitectureConformanceContractTest` (11/11).
  * *Scope Note*: Targeted Tier 1 + Robolectric `createComposeRule()` + contract/9-locale subset only (physical phone untouched per `Rule 7.6`); full `:app:testDebugUnitTest` executes at Tier 2 Sprint Integration Gate (`Rule 7.5`).
* **`Rule 8.5` Visual Verification (`read_file`)**: Inspected `docs/engineering/walkthroughs/img/ATT-8079_header_actions_preview.png` (Light & Dark themes across `411dp`, `360dp` diagnostic badge, and `360dp` custom `actions` `EXT-1`) — clean typography, zero overlap, accurate theme mapping.
* **Living Docs & Governance**: `REQ-UI-2787` and `TST-UI-2787` promoted to `[Verified]`; `verify_requirement_governance.py --sync-indexes` and `--check-verified-evidence` passed cleanly for `ATT-8079`.
