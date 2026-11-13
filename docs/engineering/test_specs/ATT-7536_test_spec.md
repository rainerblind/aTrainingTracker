# Stage 2: Requirement & Test Specification - ATT-7536: Standardize Expert Settings Form Controls

**Ticket**: [ATT-7536](https://rainerblind.atlassian.net/browse/ATT-7536) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7536`  
**Requirement Mapping**: `REQ-UI-262` (`REQ-UI-262.2`, `REQ-UI-262.5`) | **Test Spec ID**: `TST-UI-221` (`TST-UI-221.5`)  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-UI-262`)

### 1.1 Attributes & Problem Statement
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- `RadarSettingsSection.kt` wraps settings groups inside 6 nested `ElevatedCard`s within the accordion body, causing visual clutter and nested margin overhead. It also instantiates 8 raw unscaled `Switch(` calls and unstandardized `SingleChoiceSegmentedButtonRow` blocks. `TuningFormControls.kt` lacks standardized wrappers for segmented rows (`TuningSegmentedButtonRow`) and dropdown pickers (`TuningDropdownRow`), and `docs/design_guidelines.md` omits Expert Settings form control rules and §2.4 German side-by-side button length rules.

### 1.2 Functional & Architectural Requirements
1. `TuningFormControls.kt` SHALL provide `TuningSegmentedButtonRow` with `icon = {}`, `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)`, `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`, and `MaterialTheme.typography.labelSmall`.
2. `TuningFormControls.kt` SHALL provide `TuningDropdownRow` for dropdown selectors ($\ge 5$ options).
3. `RadarSettingsSection.kt` SHALL eliminate all 6 nested `ElevatedCard` containers, separating logical groups with `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` and section subheaders matching `PhysiologyModelsSection.kt`.
4. `RadarSettingsSection.kt` SHALL replace all 8 raw `Switch(` blocks with `TuningToggleItem` (0.8f scale) or `SubordinateTuningToggleItem` (0.65f scale).
5. `RadarSettingsSection.kt` SHALL replace the custom Alert Volume slider with `TuningSliderItem` and replace segmented button blocks with `TuningSegmentedButtonRow`.
6. `docs/design_guidelines.md` SHALL codify §1.4/§3.3 Expert Settings form controls and §2.4 side-by-side action button rules for German translations.
7. *Ceremony 1 Q1 Scope Boundary*: `NavigationSection.kt`, `AftermathAnalysisSection.kt`, and `TuningDesignGuidelinesContractTest.kt` are deferred to `ATT-7539`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* an athlete opening *Experten-Einstellungen -> Fahrrad-Radar & Simulator*, *When* viewing settings controls, *Then* all switches use `TuningToggleItem` (0.8f) or `SubordinateTuningToggleItem` (0.65f), Alert Volume uses `TuningSliderItem`, segmented rows use `TuningSegmentedButtonRow`, and zero nested `ElevatedCard` containers are rendered.
- *Given* `docs/design_guidelines.md`, *When* reviewed, *Then* §1.4/§3.3 codifies `TuningFormControls.kt` rules and §2.4 codifies German side-by-side button rules.
- *Given* `TuningFormControlsTest.kt`, *When* executed, *Then* contract tests for `TuningSegmentedButtonRow`, `TuningDropdownRow`, and `RadarSettingsSection.kt` card/switch hygiene pass 100%.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Amends `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog*, `ATT-2033`). Targets `TuningFormControls.kt`, `RadarSettingsSection.kt`, `docs/design_guidelines.md`.
- *Historical Origin & Commit Trace*: Sprint 2026-39.2 (`ATT-2033`, commit `096fd565`) extracted category files and created `TuningFormControls.kt`. Sprint 2026-46.5 (`ATT-7536`) added `TuningSegmentedButtonRow` and `TuningDropdownRow`, purged nested `ElevatedCard`s and raw `Switch(` blocks from `RadarSettingsSection.kt`, and codified design guidelines.
- *Root Reason for Existing Formulation*: Form controls were initially extracted for sliders and toggles; segmented rows and dropdowns were written ad-hoc in new categories like `RadarSettingsSection.kt`, resulting in nested card anti-patterns and unstandardized button padding.
- *Preservation of Core Invariants*: DataStore preference keys, reactive flow bindings, default values, stateless composable architecture, and 9-language localization parity are 100% preserved.

---

## 2. Test Specification (`TST-UI-221`)

### Test Cases
- `[TST-UI-221.5]` (Unit - `TuningFormControlsTest.kt`): Verify `TuningSegmentedButtonRow` and `TuningDropdownRow` export signatures, and `RadarSettingsSection.kt` eliminates nested `ElevatedCard`s and raw `Switch(` calls.
- `[TST-UI-221.6]` (Regression - Resource Compilation): `./gradlew processDebugResources` passing 100% without XML/AAPT2 errors.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-221.5]` | Unit | `TuningFormControls.kt`, `RadarSettingsSection.kt` | `REQ-UI-262.2`, `REQ-UI-262.5` | Specified |
| `[TST-UI-221.6]` | Regression | `./gradlew processDebugResources` (XML integrity) | `REQ-PRO-001` | Specified |
