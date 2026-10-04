# Stage 1: Problem Domain & Root Cause Analysis - ATT-2305: Optimize Layout, Text Wrapping and Reordering Controls in Workout Cards & Details Settings

**Ticket**: [ATT-2305](https://rainerblind.atlassian.net/browse/ATT-2305)  
**Sub-task**: [ATT-2328](https://rainerblind.atlassian.net/browse/ATT-2328) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*)  
**Test Spec ID**: `TST-UI-223`  
**Branch**: `feature/ATT-2305`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Forensic Analysis

### 1.1 Observable Defect & UI Audit
During the Sprint Review of ATT-2176, inspecting the "Trainingsliste & Details" card in Advanced Tuning Settings (`WorkoutMasksAndCardsSection.kt`) on a physical device revealed significant visual crampedness and ergonomic deficiencies:
1. **Severe Text Wrapping & Word Truncation in Section Titles**:
   - The section title column (`weight(1f)`) was severely squeezed horizontally because the reorder controls on the left consumed `64.dp` (two 32dp icon buttons side-by-side) and the two visibility checkbox columns on the right consumed `64.dp` each (total controls width: `192.dp`).
   - In a standard mobile dialog width of ~320–340dp, the title column was constricted to only ~120dp.
   - Long German compound titles broke mid-word across lines, producing awkward fragments:
     - `Runden-Übersich` / `t`
     - `Telemetrie-Diagr` / `amme`
     - `Zonenauswertun` / `g (HR & Power)`
2. **Disruptive In-Between Placement of Sub-Controls**:
   - When Laps was enabled (`feature.isLaps == true`), the `SingleChoiceSegmentedButtonRow` for `LapDisplayMode` was nested directly inside the reorderable row container of the Laps item.
   - Reordering the sections dragged this bulky segmented button across the list, violently disrupting the table row rhythm, alternating row backgrounds, and vertical visual scanning.
3. **Cramped & Misaligned Column Headers**:
   - Column headers `In Liste` and `In Details` in `Row` were rigid and too close to the edge, without optimal alignment over the respective checkbox touch centers.
4. **Sub-optimal Reorder Controls Ergonomics**:
   - Two horizontal arrow buttons consumed 64dp of horizontal real estate while offering poor vertical affordance.

### 1.2 Forensic Root Cause
In `WorkoutMasksAndCardsSection.kt`:
```kotlin
// Reorder buttons taking 64dp horizontally
Row(modifier = Modifier.width(64.dp)) {
    IconButton(..., modifier = Modifier.size(32.dp)) { ... }
    IconButton(..., modifier = Modifier.size(32.dp)) { ... }
}
// Text with weight(1f) starved of width
Text(text = stringResource(feature.titleRes), modifier = Modifier.weight(1f))
// Checkbox columns taking 64dp each
Box(modifier = Modifier.size(64.dp, 48.dp)) { Checkbox(...) }
Box(modifier = Modifier.size(64.dp, 48.dp)) { Checkbox(...) }
```
- Total fixed width = $64 + 64 + 64 + 16\text{ (padding)} = 208\text{dp}$.
- Available text width on 320dp dialog = $320 - 208 = 112\text{dp}$.
- Furthermore, the nested `if (feature.isLaps)` condition inside `features.forEachIndexed` broke layout modularity.

---

## 2. Chesterton's Fence Archaeology (`REQ-PRO-022`)

* **Target Requirement**: `REQ-UI-255` (*Workout Cards & Details Visibility & Reordering Preferences*) and `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog*).
* **Historical Origin**:
  - `REQ-UI-255` was created in ATT-2176 to introduce the 8-feature visibility matrix and section reordering.
  - `REQ-UI-262` was created in ATT-2033 to decompose `AdvancedTuningDialog.kt` into dedicated category files under `categories/` while enforcing a strict `< 400` lines-of-code ceiling per file.
* **Root Reason for Existing Formulation**:
  - The initial implementation prioritized functional correctness (two-way binding with `WorkoutCardSectionPreferences`, `WorkoutDetailPreferences`, and `WorkoutSectionType.DEFAULT_ORDER`) and modular file decomposition.
  - Reorder controls were placed horizontally as a quick mechanism without optimizing width distribution for long German compound titles.
* **Preservation of Core Invariants**:
  - All existing preference bindings (`workoutCardPrefs`, `workoutDetailPrefs`, `workoutSectionsOrder`) MUST remain 100% functional.
  - The `< 400` lines per file constraint in `ui.settings.tuning` MUST be strictly preserved.
  - Existing contract tests (`WorkoutSectionReorderContractTest`, `AdvancedTuningModularityTest`, `AdvancedTuningVisualContractTest`, `LapDisplayModeSettingsTest`) MUST continue to pass.

---

## 3. Scope Bounding & Proposed Solution

### 3.1 In-Scope (SWE.1 / SWE.2)
1. **Vertical Reorder Controls Layout**:
   - Refactor the reorder buttons from horizontal `Row(64.dp)` to a compact vertical `Column(32.dp)` with two 22dp `IconButton`s (18dp icons), freeing 32dp of horizontal space.
2. **Optimized Checkbox Column Widths**:
   - Adjust checkbox column widths from `64.dp` to `50.dp` (preserving standard Material 3 48dp touch targets), freeing an additional 28dp of horizontal space.
3. **50% Title Width Expansion**:
   - Total width gained for section titles = $32\text{dp} + 28\text{dp} = 60\text{dp}$ (increasing title width from ~112dp to ~172dp).
   - Long compound titles ("Runden-Übersicht", "Telemetrie-Diagramme", "Strava-Aktivitätsdaten") fit cleanly on a single line, and "Zonenauswertung (HR & Power)" wraps naturally between words without single-character breaks.
4. **Lap Display Mode Extraction**:
   - Move `SingleChoiceSegmentedButtonRow` out of the reorderable item row into a dedicated, cleanly styled subsection card immediately below the matrix table.
5. **Harmonized Column Header Alignment**:
   - Match column header widths to `32.dp` (spacer) and `50.dp` (List and Details headers), ensuring pixel-perfect vertical alignment over checkboxes.

### 3.2 Out-of-Scope
- Modifications to DataStore schemas or default order definitions (`WorkoutSectionType.DEFAULT_ORDER`).
- Reordering animation libraries or drag-and-drop third-party gestures.
- Changes to workout card rendering in `WorkoutHistoryScreen` or `WorkoutDetailLayout`.

---

## 4. Verification Strategy & Gate Criteria

1. **Gate 1 Exit Criteria**: Approved Stage 1 Analysis deliverable and subtask `ATT-2328` completion.
2. **Targeted Tests**:
   - `WorkoutSectionReorderContractTest`: Reordering logic, boundary conditions, and iconography contracts.
   - `AdvancedTuningModularityTest`: File line counts strictly $< 400$ lines.
   - `LapDisplayModeSettingsTest`: SegmentedButton presence and 9-language localization parity.
   - New `WorkoutMasksAndCardsLayoutTest`: Verifying compact reorder container width, 50dp checkbox column alignment, and decoupled lap display mode footer.
3. **Clean-Room Regression**: Full test suite pass rate: 100% (1,691 tests).
