# Stage 2: Requirement & Test Specification - ATT-4043: Clean up WorkoutMasksAndCardsSection: remove redundant Details column, collapse Summary list by default, and split Zone Display Mode into Summary vs Details

**Ticket**: [ATT-4043](https://rainerblind.atlassian.net/browse/ATT-4043)  
**Sub-task**: [ATT-4222](https://rainerblind.atlassian.net/browse/ATT-4222) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-414` (*Workout Cards & Details Settings Streamlining: Redundant Details Column Removal, Authoritative Tab Section Activation, Collapsible Summary Reorder List, and Scoped Zone Mode Selectors*)  
**Test Spec ID**: `TST-UI-377` (*Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-4043`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-414)

### 1.1 Problem Statement & Rationale
In Sprint 2026-42.8, *ATT-3920* introduced customizable Workout Detail tabs (`WorkoutDetailTabsConfigSheet`, `WorkoutDetailTabConfig`), allowing athletes to assign, order, and toggle sections per tab. However, *Advanced Settings -> Trainingsliste & Detail-Ansicht* (`WorkoutMasksAndCardsSection.kt`) still retained a legacy 2-column matrix table ("Liste" and "Details"). Unchecking a box in the "Details" column silently suppressed that section from rendering even when assigned to a Detail Tab. In addition, the 9-row reorderable list is only relevant to the Summary Cards in the workout list and occupies excessive vertical space when expanded by default. Finally, the *Zonenkarten-Anzeigemodus* selectors for Summary and Details were lumped together at the bottom rather than placed in their respective domain sections.

### 1.2 Functional & Architectural Requirements

1. **Elimination of Redundant Details Column & Authoritative Tab Assignment (`REQ-UI-414.1`)**:
   - `WorkoutMasksAndCardsSection.kt` SHALL remove the "Details" checkbox column and "Details" column header from the 9-row reorderable section table.
   - `WorkoutDetailSectionAvailability.isSectionActive(section, detailPreferences)` SHALL evaluate section activation strictly based on non-empty data presence (`hasDescription`, `hasExtrema`, `hasMap`, `hasElevation`, `hasCharts`, `hasZones`, `hasLaps`, `hasStrava`, `hasExportStatus`) and tab section inclusion in `WorkoutDetailTabConfig`, and SHALL NOT gate section activation on legacy boolean flags in `WorkoutDetailPreferences` (`showDescription`, `showExtrema`, `showMap`, etc.), ensuring tab assignment in `WorkoutDetailTabConfig` is the sole authoritative configuration source for the Workout Details screen.

2. **Domain Partitioning: Trainingsliste (Summary) vs. Trainings-Details (`REQ-UI-414.2`)**:
   - `WorkoutMasksAndCardsSection.kt` SHALL partition its layout into two distinct visual domain blocks:
     (a) **Trainingsliste (Summary-Karten)**: Dedicated to the list of workout summary cards.
     (b) **Trainings-Details (Detail-Ansicht)**: Dedicated to the full-screen Workout Details screen and its tabs.

3. **Collapsible Summary Cards Reorder List (`REQ-UI-414.3`)**:
   - The 9-row reorderable summary card section list in the Trainingsliste block SHALL be collapsible by default.
   - A toggle header/button SHALL present the title and active card count (`R.string.workout_card_reorder_toggle`, showing `%1$d active` / `%1$d aktiv`) alongside an expandable chevron icon (`KeyboardArrowDown` when collapsed, `KeyboardArrowUp` when expanded).
   - Tapping the toggle SHALL expand or collapse the 9-row table without disrupting the athlete's current configuration.
   - When expanded, the table header SHALL render a 32dp spacer, Element title (`R.string.tuning_matrix_feature`, weight 1f), and a single 50dp centered header (`R.string.tuning_matrix_col_list`), with each row containing 32dp Up/Down reorder icon buttons, the localized section title, and a centered 50dp Checkbox controlling `workoutCardPrefs` visibility.

4. **Domain-Scoped Zone Card Display Mode & Lap Display Mode Placement (`REQ-UI-414.4`)**:
   - The *Zonenkarten-Anzeigemodus* (`ZoneDisplayModeSelector`) SHALL be split and placed within its respective domain block:
     - The Summary Zone Display Mode selector SHALL reside within the Trainingsliste block (`workoutCardPrefs.zoneCardDisplayMode`).
     - The Details Zone Display Mode selector SHALL reside within the Trainings-Details block (`workoutDetailPrefs.zoneCardDisplayMode`), directly alongside the "Detail-Tabs konfigurieren" action button.
   - The *Runden-Anzeigemodus* (`LapDisplayMode`: Table vs Visualizer) SHALL reside within the Trainingsliste block when `workoutCardPrefs.showLaps` is enabled.

5. **Modularity & File Length Limit (`REQ-UI-414.5`)**:
   - `WorkoutMasksAndCardsSection.kt` SHALL remain strictly under 400 lines in compliance with `REQ-PRO-011`.

#### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement `REQ-UI-414`, refining `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections*), `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout*), `REQ-UI-271` (*Simplified Preposition-Free Column Headers*), and `REQ-UI-408` (*Individually Configurable Workout Details Tabs*). Targets `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabConfig.kt`, and `strings.xml`.
2. **Historical Origin & Commit Trace**: Commit `02f3d414` (Sprint 2026-40.12, initial 2-column matrix), commit `6817b037` (Sprint 2026-40.14, singular headers), and commit `c8939a3f` (Sprint 2026-42.8, ATT-3920 configurable detail tabs).
3. **Root Reason for Existing Formulation**: When the 2-column matrix was introduced, Workout Details had no tabs, so a single unified matrix was the only place to toggle detail sections. Now that tab configuration has dedicated per-tab section toggles, the Details column in the matrix is an obsolete secondary gating mechanism.
4. **Preservation of Core Invariants**:
   - Summary card section reordering (`DEFAULT_ORDER`), DataStore persistence (`WorkoutCardSectionPreferences`), and active card visibility in `WorkoutSummary.kt` must remain 100% intact.
   - Tab validation rules (Map pinned at index 0, minimum 1-tab fallback) in `WorkoutDetailTabConfig` must remain intact.
   - Minimum 48dp touch targets on checkboxes and buttons.
   - 100% 9-language translation parity.

### 1.4 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Authoritative Tab Section Activation)**:
  * *Given* an athlete assigning the `MAP` section to a tab in `WorkoutDetailTabsConfigSheet`,
  * *When* opening that tab on a workout with a GPS track,
  * *Then* the Map view SHALL render regardless of legacy `WorkoutDetailPreferences.showMap` boolean state.

* **Criterion 2 (Single-Column Checkbox Table & Collapsible by Default)**:
  * *Given* an athlete opening *Advanced Settings -> Trainingsliste & Detail-Ansicht*,
  * *When* the screen composes,
  * *Then* the 9-row summary cards list is collapsed by default showing the active count, the "Details" column is absent from the table, and two distinct sections for Summary Cards and Detail-Ansicht are displayed.
  * *When* the collapsible toggle is tapped,
  * *Then* the 9 reorderable rows expand with Up/Down buttons and single-column checkboxes for `WorkoutCardSectionPreferences`.

* **Criterion 3 (Domain-Scoped Zone Mode Selectors)**:
  * *Given* the Summary and Details configuration blocks,
  * *When* adjusting *Zonenkarten-Anzeigemodus*,
  * *Then* the Summary selector updates `workoutCardPrefs.zoneCardDisplayMode` and the Details selector updates `workoutDetailPrefs.zoneCardDisplayMode`.

---

## 2. Test Specification (TST-UI-377)

### Test Case 1: Authoritative Tab Data Availability & Section Activation Test (`TST-UI-377.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsTest.kt`
* **Preconditions**: `WorkoutDetailSectionAvailability` with `hasMap = true` and `hasElevation = true`. `WorkoutDetailPreferences` with `showMap = false` and `showElevationProfile = false`.
* **Action**: Evaluate `availability.isSectionActive(WorkoutDetailSectionType.MAP, detailPrefs)` and `availability.isSectionActive(WorkoutDetailSectionType.ELEVATION, detailPrefs)`.
* **Expected Result**: Both return `true`, confirming section activation is governed by data availability and tab configuration, rather than legacy boolean flags.

### Test Case 2: Single-Column Checkbox & Layout Structure Contract Test (`TST-UI-377.2`)
* **Scope**: Contract Test (AST / Structural inspection)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Preconditions**: Source file `WorkoutMasksAndCardsSection.kt`.
* **Action**: Inspect code structure and composable references.
* **Expected Result**:
  - The composable table header references `tuning_matrix_col_list` (50dp width) and does NOT contain `tuning_matrix_col_details`.
  - Feature rows contain a single checkbox for `feature.listChecked` and no second checkbox for details.

### Test Case 3: Collapsible Summary Reorder List Contract Test (`TST-UI-377.3`)
* **Scope**: Contract Test (AST / Structural inspection)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Action**: Verify collapsible toggle state and icons.
* **Expected Result**:
  - Source code maintains collapsible state (initialized to `false` for collapsed by default).
  - References `Icons.Default.KeyboardArrowDown` and `Icons.Default.KeyboardArrowUp` for expand/collapse visual feedback.
  - References `R.string.workout_card_reorder_toggle` format string.

### Test Case 4: Scoped Zone Display Mode & Detail Tabs Action Contract Test (`TST-UI-377.4`)
* **Scope**: Contract Test (AST / Structural inspection)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Action**: Verify zone display mode split and detail tabs action button placement.
* **Expected Result**:
  - `ZoneDisplayModeSelector` for Summary is placed in the Trainingsliste block.
  - `ZoneDisplayModeSelector` for Details is placed in the Trainings-Details block alongside `WorkoutDetailTabsConfigSheet` launch button.

### Test Case 5: Modularity & 400-Line Limit Test (`TST-UI-377.5`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Action**: Count lines of `WorkoutMasksAndCardsSection.kt`.
* **Expected Result**: Total line count is strictly < 400 lines.

### Test Case 6: 9-Language Localization Parity Audit (`TST-UI-377.6`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/TranslationParityTest.kt`
* **Action**: Inspect all 9 supported locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Expected Result**:
  - `workout_card_reorder_toggle` and `settings_workout_details_title` exist in all 9 files.
  - Exactly matching format specifier `%1$d` in all 9 files for `workout_card_reorder_toggle`.
  - Zero XML entity issues (no `&#10;`).

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-UI-377.7`)
* **Scope**: Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate with 0 regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Implementation Target |
| :--- | :--- | :--- |
| `REQ-UI-414.1` (Redundant Details Column & Authoritative Tab Assignment) | `TST-UI-377.1`<br>`TST-UI-377.2` | `WorkoutDetailTabsTest.kt`<br>`WorkoutMasksAndCardsLayoutTest.kt` |
| `REQ-UI-414.2` (Domain Partitioning: Summary vs Details) | `TST-UI-377.2`<br>`TST-UI-377.4` | `WorkoutMasksAndCardsLayoutTest.kt` |
| `REQ-UI-414.3` (Collapsible Summary Cards Reorder List) | `TST-UI-377.3` | `WorkoutMasksAndCardsLayoutTest.kt` |
| `REQ-UI-414.4` (Domain-Scoped Zone Mode & Lap Mode Placement) | `TST-UI-377.4` | `WorkoutMasksAndCardsLayoutTest.kt` |
| `REQ-UI-414.5` (Strictly < 400 Lines Modularity) | `TST-UI-377.5` | `WorkoutMasksAndCardsLayoutTest.kt` |
| `REQ-UI-414` (9-Language Parity) | `TST-UI-377.6` | `TranslationParityTest.kt` |
| `REQ-PRO-001` (Clean-Room Full Suite Regression) | `TST-UI-377.7` | `./gradlew testDebugUnitTest` |
