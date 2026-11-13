# Stage 1 Analysis: ATT-7536 - Standardize Expert Settings Form Controls & Design Guidelines

**Ticket**: [ATT-7536](https://rainerblind.atlassian.net/browse/ATT-7536)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7536` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
In `RadarSettingsSection.kt`, settings groups are wrapped inside 6 nested `ElevatedCard`s within an already-carded accordion container (`ExpertCategoryCard`), causing visual clutter, redundant shadows, and nested inset margins. Furthermore, `RadarSettingsSection.kt` instantiates 8 raw unscaled `Switch` composables and 4 unstandardized `SingleChoiceSegmentedButtonRow` blocks. `TuningFormControls.kt` lacks standardized wrappers for segmented rows (`TuningSegmentedButtonRow`) and dropdown pickers (`TuningDropdownRow`), and `docs/design_guidelines.md` does not yet codify Expert Settings form control standards or §2.4 side-by-side action button rules.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Missing Standard Form Controls)**: `TuningFormControls.kt` provides `TuningToggleItem`, `SubordinateTuningToggleItem`, and `TuningSliderItem`, but lacks `TuningSegmentedButtonRow` and `TuningDropdownRow`.
* **RCA 2 (Nested Card Anti-Pattern & Raw Switches)**: Without standardized segmented controls, `RadarSettingsSection.kt` grouped controls using nested `ElevatedCard`s and raw `Row + Switch` blocks.
* **Independent Verification on Sprint Branch**:
  * Confirmed 6 nested `ElevatedCard`s in `RadarSettingsSection.kt` (lines 115, 235, 690, 852, 916, 964).
  * Confirmed 8 raw `Switch(` calls in `RadarSettingsSection.kt` (lines 445, 475, 505, 535, 779, 809, 839, 952).
  * Confirmed `PhysiologyModelsSection.kt` demonstrates the canonical pattern: `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` without nested `ElevatedCard`s.

---

## 3. User Scope Grounding & Ceremony 1 Q1 Boundary (ATT-1250)
* **In-Scope Goals for ATT-7536**:
  1. Add `TuningSegmentedButtonRow` and `TuningDropdownRow` to `TuningFormControls.kt` (`icon = {}`, `labelSmall`, `maxLines = 1`, `softWrap = false`).
  2. Refactor `RadarSettingsSection.kt`: eliminate 6 nested `ElevatedCard`s, replace with `HorizontalDivider` + section subheaders, convert 8 raw switches to `TuningToggleItem` / `SubordinateTuningToggleItem`, replace custom Alert Volume slider with `TuningSliderItem`, and adopt `TuningSegmentedButtonRow`.
  3. Update `docs/design_guidelines.md` with §1.4/§3.3 Expert Settings form controls and §2.4 German button rules.
  4. Extend unit tests in `TuningFormControlsTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding / Ceremony 1 Q1 Boundary)**:
  * Migration of `NavigationSection.kt` and `AftermathAnalysisSection.kt` is deferred to `ATT-7539`.
  * Automated architecture contract test `TuningDesignGuidelinesContractTest.kt` is deferred to `ATT-7539`.
  * Zero changes to DataStore preference keys, RadarStateRepository, or protocol backends.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog*) and `REQ-SET-077` (*Standardized Route Clustering Form Controls via TuningFormControls*).
* **Historical Origin & Commit Trace**: `ATT-2033` (`096fd565`, Sprint 2026-39.2) introduced `TuningFormControls.kt` with `TuningSliderItem` and `TuningToggleItem`. `ATT-4432`/`ATT-4513` added `SubordinateTuningToggleItem`.
* **Root Reason for Existing Formulation**: Form controls were added incrementally as new settings types appeared; segmented controls and dropdowns were left unstandardized.
* **Preservation of Core Invariants**: DataStore keys, preference flows, defaults, and 9-language localization parity are 100% preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-UI-262`, `REQ-SET-077`.
* **Affected Architecture Elements**: `TuningFormControls.kt`, `RadarSettingsSection.kt`, `docs/design_guidelines.md`.
* **Verification Impact & Test Surface**: `TuningFormControlsTest.kt`, `processDebugResources`, `compileDebugUnitTestJavaWithJavac`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Pure UI restructuring improving visual hierarchy and consistency.

---

## 6. Living Documentation Plan
* Update `docs/requirements/req_ui_design_system.md` (`REQ-UI-262`) to document `TuningSegmentedButtonRow` and `TuningDropdownRow` additions.
