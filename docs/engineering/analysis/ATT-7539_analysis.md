# Stage 1 Analysis: ATT-7539 - Standardize Climb Formula & Category Segmented Rows with Design Guidelines Contract Test

**Ticket**: [ATT-7539](https://rainerblind.atlassian.net/browse/ATT-7539)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7539` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
In `NavigationSection.kt:136-168`, climb scoring formula selection (`ClimbScoringFormula.FIETS` vs. `STRAVA_GARMIN`, `REQ-CLM-010.3` / `ATT-7162`) uses two adjacent `FilterChip`s in a `Row` and a `titleSmall` heading. This directly violates `docs/design_guidelines.md §1.1` (Binary Mutually Exclusive Mode Selections) and §1.4 (Segmented Choice 2–4 Options). Adjacent chips look unanchored and imply independent filter tags rather than a unified switch, and lack `icon = {}`. Furthermore, `AftermathAnalysisSection.kt:106-158` contains legacy binary `FilterChip` rows for elevation and telemetry X-axis domains (`DISTANCE` vs. `TIME`), and `CockpitTypographySection.kt:138-157` uses a `FilterChip` row for 3-option font weights. No automated contract test currently prevents regressions against `docs/design_guidelines.md §1` across `ui/settings/tuning/categories/`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Missing Reusable Control in ATT-7162)**: `TuningSegmentedButtonRow` was created in `ATT-7536` (`TuningFormControls.kt`). When `ATT-7162` was implemented, the developer lacked a standard segmented control and copied the `FilterChip` anti-pattern from `AftermathAnalysisSection.kt`.
* **RCA 2 (Missing Architectural Style-Guide Guard)**: There is no automated contract test enforcing `design_guidelines.md §1` across tuning category files.
* **Independent Verification on Sprint Branch**:
  * Confirmed `NavigationSection.kt:155, 161` uses `FilterChip` for binary FIETS vs. Strava/Garmin formula selection.
  * Confirmed `AftermathAnalysisSection.kt:110, 116, 145, 151` uses `FilterChip` for binary `DISTANCE` vs. `TIME` domain selections.
  * Confirmed `CockpitTypographySection.kt:143` uses `FilterChip` for 3-option `CockpitFontWeight` selection.
  * Confirmed `SupporterDebugSection.kt:122` uses `FilterChip` in `FlowRow` for 7 developer tier overrides (`SupporterTierOverride.values()`).
  * Confirmed zero occurrences of raw `Switch(` or nested `ElevatedCard(` in `categories/`.

---

## 3. User Scope Grounding & Complexity Split Guardrail (ATT-1250 / REQ-PRO-029)
* **In-Scope Goals**:
  1. Replace `FilterChip` row in `NavigationSection.kt` with `TuningSegmentedButtonRow` (`FIETS` vs. `STRAVA_GARMIN`).
  2. Replace two legacy binary `FilterChip` rows in `AftermathAnalysisSection.kt` with `TuningSegmentedButtonRow` (Elevation & Telemetry X-axis: `DISTANCE` vs. `TIME`).
  3. Standardize `CockpitTypographySection.kt` to `TuningSegmentedButtonRow` (`CockpitFontWeight`).
  4. Create `TuningDesignGuidelinesContractTest.kt` in `app/src/test/.../ui/settings/tuning/` scanning `categories/` to assert zero raw `FilterChip(` (outside allowlisted `SupporterDebugSection.kt`), zero raw unscaled `Switch(`, and zero nested `ElevatedCard(`.
  5. Synchronize living docs (`REQ-UI-262`, `REQ-CLM-010`, `TST-UI-221`, `TST-CLM-010`).
* **Out-of-Scope Non-Goals**: No changes to DataStore keys, preference defaults, calculation engines, or non-category tuning files.
* **Complexity Rating**: 1 SP (Trivial / Standard UI refactor & contract test). Splitting not required.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog*) and `REQ-CLM-010` (*Configurable FIETS Index & Strava/Garmin Climb Categorization*).
* **Historical Origin & Commit Trace**: `ATT-2033` (`096fd565`) extracted tuning categories. `ATT-7162` (`3a5d840d`) added climb formula preference. `ATT-7536` (`7ba8a643`) created `TuningSegmentedButtonRow` and codified `docs/design_guidelines.md §1.4`.
* **Root Reason for Existing Formulation**: `TuningSegmentedButtonRow` did not exist when `ATT-7162` was authored.
* **Preservation of Core Invariants**: DataStore keys, reactive flows, defaults, composable signatures, and 9-language localization parity are 100% preserved.

---

## 5. Side-Effect & Cross-Caller Impact Analysis (Rule 68)
* `NavigationSection`, `AftermathAnalysisSection`, and `CockpitTypographySection` composable signatures and parameters are fully preserved; existing callers (`AdvancedTuningDialog.kt`) and contract tests (`MetricFormatterContractTest.kt`, `AdvancedTuningVisualContractTest.kt`) remain unaffected.
* `TuningDesignGuidelinesContractTest` runs as a JVM test in `testDebugUnitTest` without emulator or network dependency.

---

## 6. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-UI-262.5` and `REQ-CLM-010.3`.
* **Affected Architecture Elements**: `NavigationSection.kt`, `AftermathAnalysisSection.kt`, `CockpitTypographySection.kt`, `TuningDesignGuidelinesContractTest.kt`.
* **Verification Impact & Test Surface**: `TuningDesignGuidelinesContractTest.kt`, `MetricFormatterContractTest.kt`, `TranslationParityTest.kt`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Eliminates UI anti-patterns and establishes automated design system regression guards.

---

## 7. Living Documentation Plan
* Update `docs/requirements/req_ui_design_system.md` (`REQ-UI-262.5`), `docs/tests/tst_ui_design_system.md` (`TST-UI-221`), `docs/requirements/req_maps_navigation.md` (`REQ-CLM-010.3`), and `docs/tests/tst_maps_navigation.md` (`TST-CLM-010`).
