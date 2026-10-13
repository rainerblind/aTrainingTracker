# Stage 1 Analysis: ATT-2383 - Simplify Column Headers to List and Details in Workout Cards and Details Settings

**Ticket**: [ATT-2383](https://rainerblind.atlassian.net/browse/ATT-2383)  
**Sub-task**: [ATT-2478](https://rainerblind.atlassian.net/browse/ATT-2478) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2383`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Statement & Motivation

In the *Trainingsliste & Details* section of Advanced Settings ([WorkoutMasksAndCardsSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt)), a 2-column matrix table allows athletes to independently configure feature visibility for the Workout Summary List and Workout Details views.

The table header currently uses prepositional phrases for column titles:
- German: `"In Liste"` and `"In Details"`
- English: `"In List"` and `"In Details"`
- Polish: `"Na liście"` and `"W szczegółach"`
- Italian: `"In elenco"` and `"Nei dettagli"`
- Spanish: `"En lista"` and `"En detalles"`
- French: `"En liste"` and `"En détails"`
- Portuguese: `"Na lista"` and `"Nos detalhes"`
- Dutch: `"In lijst"` and `"In details"`
- Japanese: `"一覧"` and `"詳細"`

With each checkbox column standardized to `50.dp` width (to maximize horizontal space for feature names like *"Runden-Übersicht"* and *"Telemetrie-Diagramme"* without mid-word hyphenation), multi-word titles with prepositions (e.g., *"In Details"*, *"W szczegółach"*, *"Nei dettagli"*) collide horizontally and force awkward two-line wrapping (e.g. `"In\nDetails"`). This disrupts vertical alignment, misaligns header baselines, and degrades visual polish.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Evolutionary Width Reduction**:
   - In `ATT-2030` (`REQ-UI-240`), the initial matrix table implementation utilized wide column containers (64.dp - 76.dp), which accommodated multi-word headers with prepositions.
   - In `ATT-2305` (`REQ-UI-264`), the checkbox columns were narrowed to `50.dp` (providing the minimum Material 3 48.dp touch target plus 2.dp padding) in order to grant the feature title column (`weight(1f)`) an additional ~50.dp of horizontal space.
2. **Omission of String Simplification**:
   - When the column widths were reduced to `50.dp`, the string resources `tuning_matrix_col_list` and `tuning_matrix_col_details` were not adapted.
   - At `50.dp` (~130px at standard density), bold `MaterialTheme.typography.labelMedium` text cannot fit strings with 10–13 characters (such as `"W szczegółach"` or `"Nei dettagli"`) on a single line, causing automatic text wrapping.
3. **Redundancy of Prepositions**:
   - In a tabular column header context directly above checkboxes, the preposition (*"In"*, *"En"*, *"Na"*, *"W"*) is grammatically redundant. The concise noun alone (*"List"* / *"Details"*, *"Liste"* / *"Details"*) conveys 100% of the semantic meaning while fitting comfortably within the 50.dp column bound without wrapping.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update `tuning_matrix_col_list` and `tuning_matrix_col_details` across all 9 supported locales:
    * `values` (EN): `"List"` / `"Details"`
    * `values-de` (DE): `"Liste"` / `"Details"`
    * `values-es` (ES): `"Lista"` / `"Detalles"`
    * `values-fr` (FR): `"Liste"` / `"Détails"`
    * `values-it` (IT): `"Elenco"` / `"Dettagli"`
    * `values-ja` (JA): `"一覧"` / `"詳細"` (already optimal)
    * `values-nl` (NL): `"Lijst"` / `"Details"`
    * `values-pl` (PL): `"Lista"` / `"Szczegóły"`
    * `values-pt` (PT): `"Lista"` / `"Detalhes"`
  * Add defensive styling (`maxLines = 1`, `overflow = TextOverflow.Ellipsis`, `textAlign = TextAlign.Center`) to the header `Text` composables in [WorkoutMasksAndCardsSection.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/WorkoutMasksAndCardsSection.kt).
  * Update requirement [REQ-UI-264](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) to reflect the simplified header labels.
  * Verify 100% 9-language translation parity with `TranslationParityTest`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to DataStore preferences (`WorkoutCardPreferences`, `WorkoutDetailPreferences`) or underlying persistence keys.
  * No modification to feature row ordering, drag-and-drop / reordering logic, or `LapDisplayMode` controls.
  * No modification to other sections in `AdvancedTuningDialog.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: [REQ-UI-264](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*) and [REQ-UI-240](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) (*Matrix Table Presentation for Configurable Aftermath Sections*).
* **Historical Origin & Commit Trace**:
  - `02f3d414` (Sprint 2026-40.12, ATT-2030): Initial 2-column matrix table with `tuning_matrix_col_list` ("In List" / "In Liste") and `tuning_matrix_col_details` ("In Details").
  - `6817b037` (Sprint 2026-40.14, ATT-2305): Optimized column width to 50.dp to give German feature titles horizontal space.
* **Root Reason for Existing Formulation**:
  - Prepositional phrases were originally chosen for colloquial clarity when columns were 64.dp - 76.dp wide.
* **Preservation of Core Invariants**:
  - The simplified nouns (*"Liste"* / *"Details"*, *"List"* / *"Details"*) maintain full semantic clarity for athletes toggling feature visibility in lists vs. details.
  - The 50.dp checkbox touch target (`minimumInteractiveComponentSize` / 48x48dp) remains completely intact.
  - 100% 9-language localization parity is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **Resource Simplification**:
   - Update string resources in `app/src/main/res/values*/strings.xml` for all 9 locales:
     | Locale | `tuning_matrix_col_list` | `tuning_matrix_col_details` |
     | :--- | :--- | :--- |
     | `values` (en) | `List` | `Details` |
     | `values-de` (de) | `Liste` | `Details` |
     | `values-es` (es) | `Lista` | `Detalles` |
     | `values-fr` (fr) | `Liste` | `Détails` |
     | `values-it` (it) | `Elenco` | `Dettagli` |
     | `values-ja` (ja) | `一覧` | `詳細` |
     | `values-nl` (nl) | `Lijst` | `Details` |
     | `values-pl` (pl) | `Lista` | `Szczegóły` |
     | `values-pt` (pt) | `Lista` | `Detalhes` |
2. **Defensive Layout Hardening**:
   - In `WorkoutMasksAndCardsSection.kt`, enhance the header `Text` composables with `textAlign = TextAlign.Center`, `maxLines = 1`, and `overflow = TextOverflow.Ellipsis`.
3. **Verification**:
   - Execute `TranslationParityTest` to confirm all 9 locales are synchronized with 0 missing keys.
   - Run Compose UI / Unit tests covering `WorkoutMasksAndCardsSection`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features, DataStore preferences, and unit tests.
  2. 100% 9-language localization parity across all supported application locales.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Pure string resource simplification and defensive composable layout refinement with zero data model, persistence, or concurrency implications.
