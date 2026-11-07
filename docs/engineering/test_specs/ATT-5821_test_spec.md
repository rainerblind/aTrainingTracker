# Stage 2: Requirement & Test Specification - ATT-5821: EditSportTypeDialog Progressive Disclosure

**Ticket**: [ATT-5821](https://rainerblind.atlassian.net/browse/ATT-5821)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-365` (`REQ-UI-365.4`)  
**Test Spec ID**: `TST-UI-328` (`TST-UI-328.6`)  
**Branch**: `feature/ATT-5821`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-365.4`)

### 1.1 Problem Statement & Rationale
In `EditSportTypeDialog.kt`, Min/Max speed fields (lacking an explanation that they govern automatic sport type detection) and niche TCX/GC export dropdowns are displayed openly among everyday settings. Following Design Guidelines Section 3 (*Settings & Accordion Structure*), everyday fields (Name, Base Sport, Equipment, Strava) SHALL be placed upfront, while speed thresholds (with detection hint) and TCX/GC mappings SHALL be encapsulated in an initially collapsed "Erweiterte Optionen" accordion.

### 1.2 Functional & Architectural Requirements
1. **Primary Field Prominence (`REQ-UI-365.4a`)**: Name, Base Sport Type, Linked Equipment, and Strava Sport Mapping SHALL be rendered consecutively on the primary dialog surface.
2. **Speed Auto-Detection Hint (`REQ-UI-365.4b`)**: Min/Max speed fields SHALL be preceded by an explanatory hint text (`sport_type_speed_detection_hint`) explaining their role in automatic sport detection.
3. **Advanced Options Accordion (`REQ-UI-365.4c`)**: Speed detection hint, Min/Max speeds, and TCX/GoldenCheetah dropdowns SHALL be grouped inside an accordion titled `sport_type_advanced_options` ("Erweiterte Optionen").
4. **Default Collapsed State (`REQ-UI-365.4d`)**: The accordion SHALL default to collapsed (`isAdvancedExpanded = false` via `rememberSaveable`).
5. **9-Language Parity (`REQ-UI-365.4e`)**: All new titles, descriptions, and hints SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Primary View)**: Given the edit sport type dialog, when opened, then only Name, Base Sport, Equipment, Strava, and the collapsed Advanced Options accordion header are visible.
* **Criterion 2 (Expand Advanced)**: Given the collapsed Advanced Options accordion, when tapped, then it expands to reveal the detection hint, Min/Max speed fields, and TCX/GoldenCheetah dropdowns.
* **Criterion 3 (Save Integrity)**: Given configured speed thresholds and export names inside the accordion, when saved, then values persist correctly to `SportTypeDatabaseManager`.

### 1.4 System Invariants
* SQLite schema, data models, speed unit conversions via `MyHelper`, and comma/dot input parsing are strictly preserved.

### 1.5. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-UI-365`, targeting `EditSportTypeDialog.kt`.
* `Historical Origin & Commit Trace`: `ATT-2751`, `ATT-3363` (Sprint 2026-42.3), and `ATT-5821` (Sprint 2026-45.12).
* `Root Reason for Existing Formulation`: Fields were historically stacked vertically without grouping or progressive disclosure.
* `Preservation of Core Invariants`: Decimal parsing, database persistence, and AMOLED contrast are 100% preserved.

---

## 2. Test Specification (`TST-UI-328.6`)

### Test Case 1: Dialog Layout & Encapsulation Contract (`TST-UI-328.6a`)
* **Scope**: Contract Test (`EditSportTypeDialogContractTest.kt`)
* **Assertions**: Asserts Name, Base Sport, Equipment, and Strava precede Advanced Options; asserts Min/Max speeds, hint, TCX, and GC are inside the accordion; asserts `isAdvancedExpanded = false`.

### Test Case 2: 9-Language Localization Parity (`TST-UI-328.6b`)
* **Scope**: Unit Test (`TranslationParityTest.kt`)
* **Assertions**: Asserts 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all new string keys.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-328.6c`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.sporttype.*" --tests "com.atrainingtracker.trainingtracker.ui.sporttypes.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-328.6a` | Contract | `EditSportTypeDialogContractTest` | `REQ-UI-365.4a`, `REQ-UI-365.4c`, `REQ-UI-365.4d` | Specified |
| `TST-UI-328.6b` | Localization | `TranslationParityTest` | `REQ-UI-365.4e` | Specified |
| `TST-UI-328.6c` | Regression | Tier 1 Unit Test Suite | `REQ-PRO-001`, `REQ-UI-365.4b` | Specified |
