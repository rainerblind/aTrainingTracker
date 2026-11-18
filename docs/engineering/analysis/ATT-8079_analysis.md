# Stage 1 Analysis: ATT-8079 - Persist Unchecked Detail Sections, Decouple Summary Toggles & Reserve Header Width

**Ticket**: [ATT-8079](https://atrainingtracker.atlassian.net/browse/ATT-8079) (`2 SP`, Major) | **Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_02_Spät` (`3652c31c`)  
**Branch**: `feature/ATT-8079` | **Author**: `--as agent1` | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
Athletes customizing Workout Details and Summary cards hit three persistence/UI regressions:
1. Unchecking `RPE_AND_FEEL` / `GRADE_DISTRIBUTION` or deleting `tab_hrv` in `WorkoutDetailTabsConfigSheet` immediately reverts on the next Flow emission.
2. Disabling `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, or `showCriticalPowerInWorkoutSummary` hides those physiological rows in full Workout Details too.
3. `WorkoutMasksAndCardsSection` & `AdvancedTuningAccordion` still read ignored `WorkoutDetailPreferences` booleans (`"Details: 8"`, `showZoneAnalysis`), while `WorkoutHeader.actionSpacerWidth` omits custom `actions()` width.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (`WorkoutDetailTabConfig.kt:290-291,429-553`)**: `SCHEMA_PREFIX = "v2|"`. Only `PHOTOS` (`L467-489`) checks `if (isV1)`; `GRADE_DISTRIBUTION` (`L493-510`), `RPE_AND_FEEL` (`L514-526`), and `HRV` (`L530-550`) run unconditionally whenever absent, overriding user edits on every `MyPreferenceManager.workoutDetailTabsOrderFlow` (`L329-331`) read.
* **RCA 2 (`WorkoutDetailTabbedContent.kt:873-885`)**: `RenderWorkoutDetailCardSections` forwards `tuningConfig.showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, and `showCriticalPowerInWorkoutSummary` into `WorkoutDetails(...)` (unlike `showCardioPowerInWorkoutSummary = false` + `CardioPowerDynamicsCard` at `L883,919`). `WorkoutSummary.kt:293-304` independently consumes those flags for compact list cards.
* **RCA 3 (`WorkoutMasksAndCardsSection.kt:348,370`, `AdvancedTuningAccordion.kt:179-185`)**: Accordion subtitle counts 8 stale `WorkoutDetailPreferences` booleans (`L183`) and `WorkoutMasksAndCardsSection.kt:370` gates Details `ZoneDisplayModeSelector` on `workoutDetailPrefs.showZoneAnalysis` instead of `detailTabs`.
* **Independent Peer Review & Verification (Live Codebase Audit)**:
  * **Header Blind Spot (`ATT-8097` / `504a3d61`)**: `ATT-8097` already moved `Tune` from `headerActions` to `onOpenTabsConfig` (`WorkoutHeader.kt:157-184`, `WorkoutDetailTabbedContent.kt:659`) and added `onOpenTabsConfig` (`36.dp + 17/4.dp`) to `actionSpacerWidth` (`WorkoutHeader.kt:485-500`). However, `actionSpacerWidth` still ignores caller `actions()` (`L233`) and uses static dp sums instead of measuring `actionButtonsContent()` (`onSizeChanged`) with synchronous fallback.
  * **Test & Line-Cap Constraints**: `WorkoutDetailTabsTest.kt` has 1 `"v2|"` prefix check (`L148`) + 4 `"v2|"` legacy migration tests (`L213,532,547,719`) that must remain green (`isLegacyPreV3 = !raw.startsWith("v3|")`). `WorkoutMasksAndCardsSection.kt` is **397 lines** (strict `< 400` LoC cap per `REQ-UI-414.5`). `AdvancedTuningAccordionTest.kt:302-350` & `AdvancedTuningAftermathContractTest.kt:78` assert `formatWorkoutMatrixSubtitle` signature/output.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope**: (1) Bump `SCHEMA_PREFIX` to `"v3|"` and gate `GRADE_DISTRIBUTION`, `RPE_AND_FEEL`, `HRV` migrations on `!raw.startsWith("v3|")`; (2) pass `true` for the 3 summary-toggle params in `WorkoutDetailTabbedContent.kt:877-881` while preserving `enable*` model gates and `WorkoutSummary.kt` summary gating; (3) measure `actionButtonsContent` width (`maxOf` with `actionSpacerWidth`) in `WorkoutHeader.kt`; (4) gate Details `ZoneDisplayModeSelector` on `detailTabs` (`ZONES` or `GRADE_DISTRIBUTION`) and update `formatWorkoutMatrixSubtitle` (keeping overload compatibility) to reflect `detailTabs` active section count.
* **Out-of-Scope**: `WorkoutSummary.kt` card rendering, `WorkoutDetailTabConfig.DEFAULT_TABS`, `WorkoutPhotoPreferencesComponents.kt` (`DetailPhotoSubSettings` is already unconditional), `WorkoutDetailPreferences` DataStore schema removal, `WorkoutRepository.kt`. Complexity verified at **2 SP**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-408.2` (`WorkoutDetailTabConfig.kt`), `REQ-UI-414.1/.4/.5` (`WorkoutMasksAndCardsSection.kt`), `REQ-UI-403` (`WorkoutDetailTabbedContent.kt`), `REQ-UI-2786.2` (`WorkoutHeader.kt`).
* **Historical Origin & Commit Trace**: `c8939a3f` (`ATT-3920`, `v1|`), `c738f195` (`ATT-5654`), `ATT-5655` (`v2|` + `isV1` for `PHOTOS`), `c30eae3e` (`ATT-7320`), `c26eb77c` (`ATT-7756`), `504a3d61` (`ATT-8097`).
* **Root Reason for Existing Formulation**: Unconditional checks ensured upgrading users received new sections; omitting a `v3|` bump broke subsequent user removals. Summary flags were forwarded to `WorkoutDetails` before `CardioPowerDynamicsCard` established decoupling.
* **Preservation of Core Invariants**: `v1|`/`v2|` legacy upgrades still receive `PHOTOS`/`GRADE_DISTRIBUTION`/`RPE_AND_FEEL`/`HRV`; `WorkoutSummary` still hides metrics when summary flags are `false`; `WorkoutMasksAndCardsSection.kt < 400` LoC; `WorkoutHeader` Frame-0 test bounds (`ATT-8097`) preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-UI-408`, `REQ-UI-414`, `REQ-UI-403`, `REQ-UI-2786`; zero `SYS.2`/`SYS.3` impact (UI/settings only).
* **Affected Architecture Elements**: `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutHeader.kt`, `WorkoutMasksAndCardsSection.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`.
* **Verification Impact & Test Surface**: `WorkoutDetailTabsTest`, `WorkoutDetailHeaderActionsComposeTest`, `AdvancedTuningAccordionTest`, `AdvancedTuningAftermathContractTest`, `WorkoutMasksAndCardsLayoutTest`, plus new behavioral tests for `"v3|"` persistence, decoupled `WorkoutDetails` in `WorkoutDetailTabbedContent`, `actions()` spacer reservation, and tab-backed subtitle/zone gating.
* **Risk Assessment & Invariants**: Preserve legacy `v1|`/`v2|` migration, `< 400` LoC in `WorkoutMasksAndCardsSection.kt`, and `WorkoutRepository.kt` untouched. **Risk Rating: LOW** (self-contained UI/DataStore deserialization & settings wiring with clear behavioral tests).
