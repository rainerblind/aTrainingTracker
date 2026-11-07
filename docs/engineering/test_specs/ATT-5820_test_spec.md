# Stage 2: Requirement & Test Specification - ATT-5820: Auto-Pause Slider Accordion

**Ticket**: [ATT-5820](https://rainerblind.atlassian.net/browse/ATT-5820)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-SET-080` (`REQ-SET-080.6`)  
**Test Spec ID**: `TST-SET-080` (`TST-SET-080.7`)  
**Branch**: `feature/ATT-5820`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-SET-080.6`)

### 1.1 Problem Statement & Rationale
In `AutoPauseTabsScreen.kt`, three calibration sliders (`speedThreshold`, `debounceSec`, `resumeDebounceSec`) render openly per sport tab, cluttering the UI. Following Design Guidelines Section 3 (*Settings & Accordion Structure*), technical sliders SHALL be encapsulated in an initially collapsed accordion with active values in its header subtitle.

### 1.2 Functional & Architectural Requirements
1. **Accordion Encapsulation (`REQ-SET-080.6a`)**: In `AutoPauseSportTabContent`, when Auto-Pause is enabled, the 3 sliders SHALL be hosted inside `TuningAccordionSection` titled `tuning_auto_pause_custom_thresholds_title`.
2. **Default Collapsed State (`REQ-SET-080.6b`)**: The accordion section SHALL default to collapsed (`isExpanded = false` via `rememberSaveable`) for each sport tab.
3. **Dynamic Active Values Subtitle (`REQ-SET-080.6c`)**: The header SHALL display a live summary subtitle (`TuningSubtitleFormatter.formatAutoPauseSubtitle`) showing current threshold and debounce settings (e.g., `"%1$s · Pause: %2$s · Fortsetzen: %3$s"`).
4. **9-Language Localization (`REQ-SET-080.6d`)**: All new accordion titles and format strings SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default Collapsed)**: Given an enabled sport tab, when the screen renders, then the calibration sliders are collapsed inside the accordion and the header subtitle shows active settings.
* **Criterion 2 (Expand / Collapse)**: Given a collapsed accordion, when tapped, then it expands smoothly revealing all 3 sliders.
* **Criterion 3 (Live Subtitle Update)**: Given any slider adjustment, when the value changes, then the header subtitle updates reactively.

### 1.4 System Invariants
* DataStore keys, default values, step intervals, and reset-to-defaults behavior are strictly preserved.

### 1.5. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-SET-080`, targeting `AutoPauseTabsScreen.kt`.
* `Historical Origin & Commit Trace`: `ATT-4836` (commit `69e9d6d3`), `ATT-4924`, and `ATT-5820`.
* `Root Reason for Existing Formulation`: Sliders were previously displayed openly; progressive disclosure requires bundling them into a default-collapsed accordion.
* `Preservation of Core Invariants`: Full DataStore persistence and theme contrast preserved.

---

## 2. Test Specification (`TST-SET-080.7`)

### Test Case 1: Accordion Contract Test (`TST-SET-080.7a`)
* **Scope**: Contract Test (`AutoPauseTabsScreenContractTest.kt`)
* **Assertions**: Asserts presence of `TuningAccordionSection`, default collapsed state (`isExpanded = false`), and encapsulation of the 3 sliders.

### Test Case 2: Subtitle Formatter Unit Test (`TST-SET-080.7b`)
* **Scope**: Unit Test (`TuningSubtitleFormatterTest.kt`)
* **Assertions**: Asserts metric (`km/h`) and imperial (`mph`) formatting of active values with debounce delays.

### Test Case 3: 9-Language Localization Parity (`TST-SET-080.7c`)
* **Scope**: Unit Test (`TranslationParityTest.kt`)
* **Assertions**: 100% parity across all 9 locales for `tuning_auto_pause_custom_thresholds_title` and `tuning_auto_pause_subtitle_format`.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-SET-080.7d`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.autopause.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-080.7a` | Contract | `AutoPauseTabsScreenContractTest` | `REQ-SET-080.6a`, `REQ-SET-080.6b` | Specified |
| `TST-SET-080.7b` | Unit | `TuningSubtitleFormatter.formatAutoPauseSubtitle` | `REQ-SET-080.6c` | Specified |
| `TST-SET-080.7c` | Localization | `TranslationParityTest` | `REQ-SET-080.6d` | Specified |
| `TST-SET-080.7d` | Regression | Tier 1 Unit Test Suite | `REQ-PRO-001` | Specified |
