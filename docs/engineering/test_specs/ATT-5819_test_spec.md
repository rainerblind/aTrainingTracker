# Stage 2: Requirement & Test Specification - ATT-5819: Friendly Advisory Notice

**Ticket**: [ATT-5819](https://rainerblind.atlassian.net/browse/ATT-5819)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-222` (`REQ-UI-222.6`)  
**Test Spec ID**: `TST-UI-176` (`TST-UI-176.5`)  
**Branch**: `feature/ATT-5819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-222.6`)

### 1.1 Problem Statement & Rationale
In Expert Settings, `TuningWarningNotice` currently displays an alarming red danger banner (`errorContainer`, `Icons.Default.Warning`) and copy focusing narrowly on corrupted sensor heuristics. Because Expert Settings now manages beneficial layout configurations (Workout Cards, Cockpit Typography) and training models, the copy across all 9 locales SHALL be rewritten to be friendly, factual, and reassuring (explaining layout, model, and sensor customizations, with factory reset reassurance), and the container SHALL be modernized to an informative advisory notice per `docs/design_guidelines.md` Section 5.4 (*Semantic Alert & Warning Containers*).

### 1.2 Functional & Architectural Requirements
1. **Friendly & Factual Copy (`REQ-UI-222.6a`)**: `advanced_tuning_warning_desc` SHALL constructively inform athletes that layout, model, and sensor parameters are customized here, and that default settings can be restored at any time via the reset action.
2. **Advisory Container Modernization (`REQ-UI-222.6b`)**: `TuningWarningNotice` in `TuningFormControls.kt` SHALL use an informative container (`surfaceVariant` or `secondaryContainer` styling with `Icons.Default.Tune` or `Icons.Default.Info`), eliminating alarming error-red styling (`errorContainer` / `Icons.Default.Warning`).
3. **Permanent Banner Visibility (`REQ-UI-222.6c`)**: `TuningWarningNotice` SHALL remain permanently visible at the top of the Expert Settings destination (`AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`).
4. **9-Language Localization Parity (`REQ-UI-222.6d`)**: 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid XML escaping and zero inline emojis.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Advisory Notice Presentation)**: Given an athlete opening Expert Settings, when the screen renders, then `TuningWarningNotice` displays an informative container and icon without error-red danger styling.
* **Criterion 2 (Constructive Copy)**: Given `TuningWarningNotice`, when read in any supported language, then it constructively explains layout, model, and sensor tuning, reassuring that factory defaults can be restored at any time.
* **Criterion 3 (Reset Functionality Preservation)**: Given the reset-to-defaults button, when tapped, then all tuning parameters restore to defaults without regression.

### 1.4 System Invariants
* Zero DataStore preference key or range regressions; factory reset button functionality preserved; 100% 9-language translation parity maintained.

### 1.5. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-222`, targeting `TuningFormControls.kt` and `strings.xml`.
* `Historical Origin & Commit Trace`: `ATT-1821` (Sprint 2026-40.8), `ATT-1957` (Sprint 2026-40.9), and `ATT-5819` (Sprint 2026-45.12).
* `Root Reason for Existing Formulation`: Defensive error-red warning styling and alarming copy were introduced when Expert Settings only exposed experimental GPS and dimming heuristics.
* `Preservation of Core Invariants`: Advisory notice presence, factory reset reassurance, DataStore integrity, and AMOLED compliance strictly preserved.

---

## 2. Test Specification (`TST-UI-176.5`)

### Test Case 1: Advisory Banner Visual & Contract Test (`TST-UI-176.5a`)
* **Scope**: Contract Test (`AdvancedTuningVisualContractTest.kt`)
* **Assertions**: Asserts `TuningWarningNotice` renders informative icon (`Icons.Default.Tune` or `Icons.Default.Info`), eliminates `errorContainer` and `Icons.Default.Warning`, and binds `advanced_tuning_warning_desc`.

### Test Case 2: 9-Language Localization Parity (`TST-UI-176.5b`)
* **Scope**: Unit Test (`TranslationParityTest.kt`)
* **Assertions**: Asserts 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for `advanced_tuning_warning_title` and `advanced_tuning_warning_desc`.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-176.5c`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-176.5a` | Contract | `AdvancedTuningVisualContractTest` | `REQ-UI-222.6b`, `REQ-UI-222.6c` | Specified |
| `TST-UI-176.5b` | Localization | `TranslationParityTest` | `REQ-UI-222.6d` | Specified |
| `TST-UI-176.5c` | Regression | Tier 1 Unit Test Suite | `REQ-PRO-001`, `REQ-UI-222.6a` | Specified |
