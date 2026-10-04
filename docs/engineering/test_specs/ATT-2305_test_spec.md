# Stage 2: Requirement & Test Specification - ATT-2305: Optimize Layout, Text Wrapping and Reordering Controls in Workout Cards & Details Settings

**Ticket**: [ATT-2305](https://rainerblind.atlassian.net/browse/ATT-2305)  
**Sub-task**: [ATT-2329](https://rainerblind.atlassian.net/browse/ATT-2329) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*)  
**Test Spec ID**: `TST-UI-223`  
**Branch**: `feature/ATT-2305`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-UI-264)

### 1.1 Problem Statement & Rationale
In `WorkoutMasksAndCardsSection.kt`, the section title column was constrained to ~112dp due to 64dp reorder buttons and two 64dp checkbox columns, causing mid-word line wraps on German compound words ("Runden-Übersich / t", "Telemetrie-Diagr / amme", "Zonenauswertun / g"). Furthermore, injecting the Lap Display Mode segmented button directly inside the reorderable row broke table alignment and row height rhythm.

### 1.2 Functional & Architectural Requirements
The system SHALL optimize layout, text wrapping, and reordering ergonomics in `WorkoutMasksAndCardsSection.kt` (ATT-2305):

1. **Compact Vertical Reorder Controls**:
   - The reorder controls for moving sections up and down SHALL be formatted as a compact vertical stack of width `32.dp`, containing two `IconButton`s of size `22.dp` with icons of size `18.dp`, reducing horizontal consumption from 64dp to 32dp.
2. **Standardized Checkbox Column Widths**:
   - The checkbox columns for "In Liste" and "In Details" in both the header and rows SHALL have width `50.dp` (preserving standard Material 3 48dp touch targets), reducing horizontal consumption from 64dp to 50dp each.
3. **Horizontal Title Space Expansion**:
   - The horizontal space available for section title `Text` (`weight(1f)`) SHALL increase by at least 50dp (from ~112dp to ~172dp in standard mobile dialogs), enabling German titles ("Runden-Übersicht", "Telemetrie-Diagramme", "Strava-Aktivitätsdaten") to display on a single line and preventing mid-word hyphenation.
4. **Column Header Vertical Alignment**:
   - The header row SHALL utilize a leading `Spacer(width = 32.dp)` and header label containers of width `50.dp` for `tuning_matrix_col_list` and `tuning_matrix_col_details`, centered directly over the checkboxes.
5. **Decoupled Lap Display Mode Sub-Control**:
   - When Laps are enabled in either list or details (`workoutCardPrefs.showLaps || workoutDetailPrefs.showLaps`), the `SingleChoiceSegmentedButtonRow` for `LapDisplayMode` SHALL render as a dedicated, visually structured subsection card below the matrix table rather than inside the reorderable item row.
6. **File Size Constraint**:
   - `WorkoutMasksAndCardsSection.kt` SHALL remain strictly under 400 lines of code (`REQ-UI-262`).
7. **Preservation of Functional Invariants**:
   - Preference bindings, reordering logic, DataStore persistence, and 9-language localization parity MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Title Legibility & No Mid-Word Breaks)**:
  * *Given* the 'Trainingsliste & Details' card in Advanced Tuning Settings,
  * *When* viewing section titles in German (e.g. "Runden-Übersicht", "Telemetrie-Diagramme"),
  * *Then* titles SHALL NOT break or wrap mid-word across multiple lines.
* **Criterion 2 (Compact Reordering Controls)**:
  * *Given* any row in the section table,
  * *When* inspecting the reorder buttons,
  * *Then* they SHALL be vertically arranged within a compact 32dp container with Up and Down icons and active bounds checks.
* **Criterion 3 (Harmonious Sub-Setting Flow)**:
  * *Given* the matrix table rendering with Laps enabled,
  * *When* inspecting the list rows and reordering items,
  * *Then* all 8 table rows SHALL maintain uniform height and rhythm, and `LapDisplayMode` SHALL render cleanly below the table.
* **Criterion 4 (Header Alignment)**:
  * *Given* the table headers,
  * *When* viewed,
  * *Then* 'In Liste' and 'In Details' titles SHALL align directly above their respective 50dp checkbox columns.

---

## 2. Test Specification (TST-UI-223)

### Test Case 1: Layout Metrics & Typography Contract Test (`TST-UI-223.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* **Checks**:
  - Reorder controls container width is `32.dp`.
  - Checkbox column widths in header and content rows are `50.dp`.
  - Header spacer width matches reorder controls width (`32.dp`).
  - Lap display mode segmented button is decoupled from reorderable items and positioned as a footer card.

### Test Case 2: Existing Contract Preservation Test (`TST-UI-223.2`)
* **Target Files**:
  - `WorkoutSectionReorderContractTest.kt` (reordering logic and icon contracts).
  - `AdvancedTuningModularityTest.kt` (file existence and $< 400$ lines constraint).
  - `AdvancedTuningVisualContractTest.kt` (composable existence).
  - `LapDisplayModeSettingsTest.kt` (SegmentedButton preference bindings).

### Test Case 3: 9-Language Localization Audit (`TST-UI-223.3`)
* **Checks**: Verify string parity across all 9 localized `strings.xml` files for tuning matrix strings (`tuning_matrix_feature`, `tuning_matrix_col_list`, `tuning_matrix_col_details`, `settings_lap_display_mode_title`, `settings_lap_display_mode_table`, `settings_lap_display_mode_visualizer`).

### Test Case 4: Full Clean-Room Regression (`TST-UI-223.4`)
* **Command**: `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-223.1` | UI Unit | `WorkoutMasksAndCardsSection` | `REQ-UI-264` | Specified |
| `TST-UI-223.2` | Contract | Reorder, Modularity, & Lap Contracts | `REQ-UI-264`, `REQ-UI-262` | Specified |
| `TST-UI-223.3` | Localization | `values*/strings.xml` | `REQ-UI-264`, `REQ-LOC-001` | Specified |
| `TST-UI-223.4` | Regression | Full Test Suite (`testDebugUnitTest`) | `REQ-PRO-001` | Specified |
