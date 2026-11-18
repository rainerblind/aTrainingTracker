# Stage 3: Implementation Plan - ATT-8079: Persist Unchecked Detail Sections, Decouple Summary Toggles & Reserve Header Width

**Ticket**: [ATT-8079](https://atrainingtracker.atlassian.net/browse/ATT-8079) (`2 SP`) | **Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_02_Spät` (`3652c31c`)  
**Requirement Mapping**: `REQ-UI-2787` (`REQ-UI-2787.1`–`.4`) | **Test Mapping**: `TST-UI-2787` (`TST-UI-2787.1`–`.4`)  
**Branch**: `feature/ATT-8079` | **Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Traceability, Layering & System Invariants
* **Architecture Layering (SWE.2)**: Pure Compose UI + DataStore preference serialization in `ui/aftermath/`, `ui/components/workoutheader/`, and `ui/settings/tuning/`. Zero ViewModel, Repository (`WorkoutRepository.kt <= 5,117` LoC untouched), SQLite, or `SYS.2`/`SYS.3` changes (`docs/architecture.md` unaffected).
* **UI Consistency (`docs/design_guidelines.md`)**: No new controls or layouts; `WorkoutMasksAndCardsSection` retains `ZoneDisplayModeSelector` (`SingleChoiceSegmentedButtonRow`, `icon = {}`) gated by `detailTabs` and remains `< 400` LoC (`REQ-UI-414.5`); `WorkoutHeader` preserves `ATT-8097` `[Tune] | [⇄][Edit][Share]` grouping (`32.dp`, `primary` tint) and prevents title/action collision via `onSizeChanged`; `strings_workout_details.xml` uses indexed `%1$d..%5$d` across all 9 locales.
* **Preserved Invariants**: Legacy `"v1|"`/`"v2|"` auto-migrations (`WorkoutDetailTabsTest` `TST-UI-371.2b`, `TST-UI-453.4b`, `TST-UI-453.4c`, `TST-UI-527.2`); `WorkoutSummary.kt` card toggling on `show*InWorkoutSummary`; `WorkoutHeader` Frame-0 bounds (`ATT-8097`); 3-arg `formatWorkoutMatrixSubtitle(cardPrefs, detailPrefs, context)` contract (`AdvancedTuningAccordionTest`, `AdvancedTuningAftermathContractTest`); `0` `src/main` reads in tests.

---

## 2. Atomic Step Sequence (Stage 4 Construction — Post-Gate 3 `PASS` Only per `Rule 4.1`)

### Step 1: `v3|` Schema Bump & Pre-`v3` Migration Gating (`WorkoutDetailTabConfig.kt` — `REQ-UI-2787.1`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabConfig.kt`
* **Changes**:
  1. Bump `SCHEMA_PREFIX = "v3|"`, add `SCHEMA_PREFIX_V2 = "v2|"`, and derive `isV1` + `isLegacyPreV3 = !raw.startsWith(SCHEMA_PREFIX)`.
  2. Strip `SCHEMA_PREFIX`, `SCHEMA_PREFIX_V2`, and `SCHEMA_PREFIX_V1` cleanly when parsing payload.
  3. Keep `if (isV1)` for `PHOTOS` (`ATT-5655`) and wrap `GRADE_DISTRIBUTION`, `RPE_AND_FEEL`, and `HRV` auto-migrations in `if (isLegacyPreV3) { ... }` so `"v1|"` / `"v2|"` upgrades add them once while `"v3|"` payloads preserve removals.
  4. Add concise helper `WorkoutDetailTabConfig.hasAnyZoneOrGradeSection(tabs)` / `countDistinctSections(tabs)` here to avoid inflating `WorkoutMasksAndCardsSection.kt`.

### Step 2: Decouple Full Workout Details from Summary Toggles (`WorkoutDetailTabbedContent.kt` — `REQ-UI-2787.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* **Changes**: In `RenderWorkoutDetailCardSections` (`WorkoutDetailSectionType.DETAILS`, `L874-885`), pass `showCogganPowerInWorkoutSummary = true`, `showAerobicEfficiencyInWorkoutSummary = true`, and `showCriticalPowerInWorkoutSummary = true` into `WorkoutDetails(...)`, keeping `enable*` model gates active and leaving `WorkoutSummary.kt` card gating untouched.

### Step 3: Dynamic `actions()` Width Reservation (`WorkoutHeader.kt` — `REQ-UI-2787.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`
* **Changes**: Measure the portrait Top-End `actionButtonsContent()` `Row` (`L702`) via `Modifier.onSizeChanged { measuredActionsWidthPx = it.width }`, convert via `LocalDensity.current`, and reserve `maxOf(actionSpacerWidth, measuredActionsDp)` in the title `Row` (`L498-500`) so Frame-0 `ATT-8097` layout passes synchronously while any custom `actions()` content or font scale is dynamically reserved.

### Step 4: Tab-Driven Subtitle, Zone Gating & 9-Locale Format (`AdvancedTuning*`, `WorkoutMasksAndCardsSection.kt` — `REQ-UI-2787.4`)
* **Files**: `app/src/main/res/values*/strings_workout_details.xml` (9 locales), `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `WorkoutMasksAndCardsSection.kt`
* **Changes**:
  1. Add `tuning_summary_matrix_tabs_format` (`"%1$d/%2$d List, %3$d Tabs (%4$d/%5$d)"` + 8 localized variants) to `values*/strings_workout_details.xml`.
  2. Keep `formatWorkoutMatrixSubtitle(cardPrefs, detailPrefs, context)` untouched; add overload `formatWorkoutMatrixSubtitle(cardPrefs, detailTabs: List<WorkoutDetailTabConfig>, context)` counting active list cards (`14` entries) and configured `detailTabs` + distinct active detail sections.
  3. Collect `workoutDetailTabsOrderFlow` in `AdvancedTuningScreen` & `AdvancedTuningDialog` and pass `detailTabs` to `formatWorkoutMatrixSubtitle`.
  4. In `WorkoutMasksAndCardsSection.kt` (`L370`), gate `ZoneDisplayModeSelector` on `WorkoutDetailTabConfig.hasAnyZoneOrGradeSection(detailTabs)` (instead of `workoutDetailPrefs.showZoneAnalysis`), keeping `WorkoutMasksAndCardsSection.kt` strictly **`< 400` LoC** (`<= 397`).

### Step 5: Behavioral Tests, `Rule 8.5` PNG & Tier 1 Verification (`TST-UI-2787.1`–`.4`)
* **Test Files** (**0** `src/main/` file reads; `createComposeRule()` behavioral assertions):
  * `WorkoutDetailTabsTest.kt`: update `L148` `"v2|"` -> `"v3|"`; add `"v3|"` persistence tests for removing `RPE_AND_FEEL`, `GRADE_DISTRIBUTION`, `PHOTOS`, `tab_hrv`; keep all 4 `"v1|"`/`"v2|"` migration tests passing.
  * Create `WorkoutDetailSummaryDecouplingTest.kt`: `createComposeRule()` verifying `WorkoutDetailTabbedContent` displays Coggan/AE/W'bal with `show*InWorkoutSummary = false` while `WorkoutSummary` hides them.
  * `WorkoutDetailHeaderActionsComposeTest.kt`: add `360dp` custom `actions()` + 2-line title non-overlap test and update `Rule 8.5` Light/Dark PNG (`ATT-8079_header_actions_preview.png`).
  * `WorkoutMasksAndCardsLayoutTest.kt` & `AdvancedTuningAccordionTest.kt`: add `createComposeRule()` test for `detailTabs` zone-selector gating, unit test for `formatWorkoutMatrixSubtitle(cardPrefs, detailTabs, context)` + 3-arg legacy check, and file-length assertion (`< 400` LoC).
* **Tier 1 Verification Command** (under `python3 tools/parallel_sprint_lock.py --timeout 1800`):
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailSummaryDecouplingTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutDetailHeaderActionsComposeTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.WorkoutMasksAndCardsLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAftermathContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"` + `verify_requirement_governance.py`.
* **Rollback**: Isolated to `feature/ATT-8079`; `sprint/2026-11-18_02_Spät` untouched until Stage 6 `PASS`.
