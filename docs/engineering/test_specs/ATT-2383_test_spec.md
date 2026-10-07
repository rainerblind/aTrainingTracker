# Stage 2: Requirement & Test Specification - ATT-2383: Simplify Column Headers to List and Details in Workout Cards and Details Settings

**Ticket**: [ATT-2383](https://rainerblind.atlassian.net/browse/ATT-2383)  
**Sub-task**: [ATT-2480](https://rainerblind.atlassian.net/browse/ATT-2480) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-271` (*Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix*)  
**Test Spec ID**: `TST-UI-231`  
**Branch**: `feature/ATT-2383`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Requirement Specification (REQ-UI-271)

### 1.1 Problem Statement & Rationale
In the *Trainingsliste & Details* section of Advanced Settings ([WorkoutMasksAndCardsSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt)), a 2-column matrix table allows athletes to independently configure feature visibility for the Workout Summary List and Workout Details views.

In Sprint `2026-40.14` (`ATT-2305` / `REQ-UI-264`), the checkbox columns were narrowed to `50.dp` to give German feature titles horizontal space. However, the table column headers retained legacy prepositional phrasing (German: *"In Liste"* / *"In Details"*, English: *"In List"* / *"In Details"*, Polish: *"Na liście"* / *"W szczegółach"*, Italian: *"In elenco"* / *"Nei dettagli"*). At `50.dp` width, multi-word prepositional headers collide horizontally and cause awkward 2-line wrapping (e.g. `"In\nDetails"`), breaking vertical alignment and baseline consistency.

In a tabular column header context directly above checkboxes, prepositions (*"In"*, *"En"*, *"Na"*, *"W"*) are grammatically redundant. The concise noun alone conveys 100% of the semantic meaning while fitting comfortably within the 50.dp column bound without wrapping.

### 1.2 Functional & Architectural Requirements
1. **Preposition-Free Singular Noun Headers**:
   - The string resource `tuning_matrix_col_list` SHALL be defined as the concise singular noun for "List" across all 9 supported locales:
     - `values` (EN): `List`
     - `values-de` (DE): `Liste`
     - `values-es` (ES): `Lista`
     - `values-fr` (FR): `Liste`
     - `values-it` (IT): `Elenco`
     - `values-ja` (JA): `一覧`
     - `values-nl` (NL): `Lijst`
     - `values-pl` (PL): `Lista`
     - `values-pt` (PT): `Lista`
   - The string resource `tuning_matrix_col_details` SHALL be defined as the concise noun for "Details" across all 9 supported locales:
     - `values` (EN): `Details`
     - `values-de` (DE): `Details`
     - `values-es` (ES): `Detalles`
     - `values-fr` (FR): `Détails`
     - `values-it` (IT): `Dettagli`
     - `values-ja` (JA): `詳細`
     - `values-nl` (NL): `Details`
     - `values-pl` (PL): `Szczegóły`
     - `values-pt` (PT): `Detalhes`
2. **Defensive Layout & Truncation Hardening (`WorkoutMasksAndCardsSection.kt`)**:
   - The header `Text` composables for `tuning_matrix_col_list` and `tuning_matrix_col_details` inside their respective `Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center)` SHALL enforce:
     - `maxLines = 1`
     - `overflow = TextOverflow.Ellipsis`
     - `textAlign = TextAlign.Center`
   - This defensively prevents text from breaking into two lines or causing vertical container expansion even on extreme system font scaling.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines Clause 4 of `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*) and Clause 3 of `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections*).
2. *Historical Origin & Commit Trace*:
   - `02f3d414` (Sprint 2026-40.12, ATT-2030): Initial 2-column matrix table with `tuning_matrix_col_list` ("In List" / "In Liste") and `tuning_matrix_col_details` ("In Details").
   - `6817b037` (Sprint 2026-40.14, ATT-2305): Narrowed column containers to 50dp without updating string lengths.
3. *Root Reason for Existing Formulation*: Prepositions were originally chosen when columns were 64dp–76dp wide. At 50dp width, multi-word titles with prepositions collide horizontally. In a tabular context directly above checkboxes, prepositions are redundant and concise nouns convey complete semantic meaning.
4. *Preservation of Core Invariants*: 50dp column width, Material 3 48x48dp touch targets, DataStore preferences (`WorkoutCardPreferences`, `WorkoutDetailPreferences`), reordering logic, subtitle formatting, and 100% 9-language translation parity are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Localized Noun Headers)**:
  * *Given* the 'Trainingsliste & Details' section in Advanced Settings,
  * *When* viewed in any of the 9 supported locales (e.g. German, English, Polish, Italian),
  * *Then* the column headers for List and Details SHALL display concise singular nouns without prepositions (e.g. "Liste" / "Details", "List" / "Details", "Lista" / "Szczegóły", "Elenco" / "Dettagli").
* **Criterion 2 (Single-Line Centered Alignment)**:
  * *Given* the 50dp column width containers,
  * *When* rendering header text,
  * *Then* header titles SHALL render on a single centered line without line breaking or baseline distortion.
* **Criterion 3 (Translation Parity)**:
  * *Given* `TranslationParityTest`,
  * *When* executed,
  * *Then* all 9 localized `strings.xml` files SHALL maintain 100% parity with zero missing keys and zero format mismatches.

### 1.5 System Invariants
* DataStore persistence keys (`workout_card_show_*`, `workout_detail_show_*`) and defaults MUST NOT change.
* 48dp minimum interactive component touch targets MUST NOT change.
* 9-language localization parity MUST NOT be broken.
* Parent ticket Human Decision Gate remains strictly enforced.

---

## 2. Test Specification (TST-UI-231)

### Test Case 1: Header Defensive Styling & Attributes Contract (`TST-UI-231.1`)
* **Scope**: UI Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Preconditions**: `WorkoutMasksAndCardsSection.kt` source code exists.
* **Action**: Verify that the header `Text` composables for `tuning_matrix_col_list` and `tuning_matrix_col_details` specify `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center`.
* **Expected Result**: Assertions pass confirming defensive layout constraints.

### Test Case 2: 9-Language Localization Audit (`TST-UI-231.2`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Preconditions**: String resources in all 9 locales.
* **Action**: Run `TranslationParityTest`.
* **Expected Result**: 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries or format specifier mismatches.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-231.3`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-231.1` | UI Contract | `WorkoutMasksAndCardsSection.kt` | `REQ-UI-271` | Specified |
| `TST-UI-231.2` | Localization | `TranslationParityTest` | `REQ-UI-271`, `REQ-LOC-001` | Specified |
| `TST-UI-231.3` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
