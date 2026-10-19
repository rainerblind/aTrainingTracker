# Stage 3: Implementation Plan - ATT-4043: Clean up WorkoutMasksAndCardsSection: remove redundant Details column, collapse Summary list by default, and split Zone Display Mode into Summary vs Details

**Ticket**: [ATT-4043](https://rainerblind.atlassian.net/browse/ATT-4043)  
**Sub-task**: [ATT-4223](https://rainerblind.atlassian.net/browse/ATT-4223) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-414` (*Workout Cards & Details Settings Streamlining: Redundant Details Column Removal, Authoritative Tab Section Activation, Collapsible Summary Reorder List, and Scoped Zone Mode Selectors*)  
**Test Mapping**: `TST-UI-377` (*Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite*)  
**Branch**: `feature/ATT-4043`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In the aftermath settings screen (*Advanced Settings -> Trainingsliste & Detail-Ansicht*), the configuration table previously used a 2-column matrix ("Liste" and "Details"). With the introduction of individually configurable workout detail tabs (`WorkoutDetailTabsConfigSheet`, `WorkoutDetailTabConfig` in ATT-3920), the "Details" column in the table became redundant and actively harmful: unchecking a box in the "Details" column silently suppressed sections from rendering even when explicitly assigned to a tab. Furthermore, the 9-row reorderable list is only relevant to summary cards in the workout list, and when expanded by default, pushes other settings offscreen. Finally, zone display mode selectors were grouped together at the bottom instead of residing within their respective domain blocks (Summary vs Details).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-414` (*Workout Cards & Details Settings Streamlining: Redundant Details Column Removal, Authoritative Tab Section Activation, Collapsible Summary Reorder List, and Scoped Zone Mode Selectors*)
  * `REQ-UI-414.1`: Elimination of Redundant Details Column & Authoritative Tab Section Activation
  * `REQ-UI-414.2`: Domain Partitioning: Trainingsliste (Summary) vs. Trainings-Details
  * `REQ-UI-414.3`: Collapsible Summary Cards Reorder List (collapsed by default with active count indicator)
  * `REQ-UI-414.4`: Domain-Scoped Zone Card Display Mode & Lap Display Mode Placement
  * `REQ-UI-414.5`: Modularity & Strictly < 400 Lines Enforcement
* **Test Mapping**: `TST-UI-377` (*Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite*)
  * `TST-UI-377.1`: Authoritative Tab Data Availability & Section Activation Test (`WorkoutDetailTabsTest.kt`)
  * `TST-UI-377.2`: Single-Column Checkbox & Layout Structure Contract Test (`WorkoutMasksAndCardsLayoutTest.kt`)
  * `TST-UI-377.3`: Collapsible Summary Reorder List Contract Test (`WorkoutMasksAndCardsLayoutTest.kt`)
  * `TST-UI-377.4`: Scoped Zone Display Mode & Detail Tabs Action Contract Test (`WorkoutMasksAndCardsLayoutTest.kt`)
  * `TST-UI-377.5`: Modularity & 400-Line Limit Test (`WorkoutMasksAndCardsLayoutTest.kt`)
  * `TST-UI-377.6`: 9-Language Localization Parity Audit (`TranslationParityTest.kt`)
  * `TST-UI-377.7`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Tab Customization Authority**: Tab section assignments in `WorkoutDetailTabConfig` are the sole authority for Workout Details section rendering. `WorkoutDetailSectionAvailability.isSectionActive` evaluates solely data presence (`hasDescription`, `hasExtrema`, `hasMap`, `hasElevation`, `hasCharts`, `hasZones`, `hasLaps`, `hasStrava`, `hasExportStatus`) and tab inclusion, ignoring legacy boolean flags in `detailPreferences`.
2. **Summary Card Reordering & DataStore Persistence**: Reordering cards via Up/Down buttons persists order in DataStore (`WorkoutCardSectionPreferences`), and toggling checkboxes modifies active card visibility in `WorkoutSummary.kt` without change in persistence semantics.
3. **Touch Targets & Accessibility**: Up/Down buttons, collapsible toggle button, and checkboxes adhere to Android accessibility guidelines with >= 48dp touch targets and clear descriptions.
4. **9-Language Parity**: All newly introduced string keys (`workout_card_reorder_toggle`, `settings_workout_details_title`) exist across all 9 localized `strings.xml` files with identical `%1$d` format specifiers.
5. **Modularity Invariant**: `WorkoutMasksAndCardsSection.kt` remains strictly under 400 lines (tested by `WorkoutMasksAndCardsLayoutTest.kt`).
6. **Subtask Self-Sufficiency**: Subtask transitions directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-4043` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutDetailSectionAvailability.isSectionActive` in `WorkoutDetailTabConfig.kt`
- Modify `WorkoutDetailSectionAvailability.isSectionActive(section: WorkoutDetailSectionType, detailPreferences: WorkoutDetailPreferences = WorkoutDetailPreferences()): Boolean`
- Remove checks against `detailPreferences.showDescription`, `detailPreferences.showExtrema`, `detailPreferences.showMap`, `detailPreferences.showElevationProfile`, `detailPreferences.showCharts`, `detailPreferences.showZones`, `detailPreferences.showLaps`, `detailPreferences.showStravaSegmentEfforts`, and `detailPreferences.showExportStatus`.
- Gating is determined strictly by data presence (`hasDescription`, `hasExtrema`, `hasMap`, `hasElevation`, `hasCharts`, `hasZones`, `hasLaps`, `hasStrava`, `hasExportStatus`).
- Keep the `detailPreferences` parameter with default value for backwards source compatibility.

### Component 2: `WorkoutMasksAndCardsSection.kt` Layout Restructuring
- Partition the composable into two clean visual sections:
  1. **Trainingsliste (Summary-Karten)**:
     - Section Title: `R.string.settings_workout_card_title` ("Workout List (Detailed Cards)" / "Trainingsliste (Detail-Karten)").
     - Collapsible Header Card / Toggle Button:
       - State: `var isSummaryListExpanded by remember { mutableStateOf(false) }` (collapsed by default).
       - Title & count: `stringResource(R.string.workout_card_reorder_toggle, activeCardCount)`.
       - Icon: `Icons.Default.KeyboardArrowDown` when collapsed, `Icons.Default.KeyboardArrowUp` when expanded.
     - When expanded:
       - Header row: 32dp spacer for reorder buttons, Element header (`R.string.tuning_matrix_feature`, weight 1f), single 50dp centered header (`R.string.tuning_matrix_col_list`). The legacy `tuning_matrix_col_details` header is eliminated.
       - 9-row reorder list: Each row renders Up/Down buttons (32dp), localized section title (weight 1f), and single centered Checkbox (50dp) for `feature.listChecked` (`onListCheckChanged`). Details checkbox is eliminated.
     - Lap Display Mode Selector: Rendered in Summary section when `workoutCardPrefs.showLaps` is enabled.
     - Zone Display Mode Selector (Summary): `ZoneDisplayModeSelector(selectedMode = workoutCardPrefs.zoneCardDisplayMode, onModeSelected = onSummaryZoneModeChanged, titleRes = R.string.tuning_zone_card_mode_title)`
  2. **Trainings-Details (Detail-Ansicht)**:
     - Section Title: `R.string.settings_workout_details_title` ("Workout Details (Full Screen)" / "Trainings-Details (Detail-Ansicht)").
     - Detail-Tabs Button: `OutlinedButton` launching `WorkoutDetailTabsConfigSheet` (`R.string.workout_detail_tabs_config_title`).
     - Zone Display Mode Selector (Details): `ZoneDisplayModeSelector(selectedMode = workoutDetailPrefs.zoneCardDisplayMode, onModeSelected = onDetailZoneModeChanged, titleRes = R.string.tuning_zone_card_mode_title)`

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `WorkoutMasksAndCardsSection.kt` and `WorkoutDetailTabsConfigSheet.kt` in aftermath settings.
* **Reused components**: Standard Compose `Card`, `OutlinedButton`, `IconButton`, `Icon`, `Text`, `Checkbox`, `HorizontalDivider`, and existing sub-composable `ZoneDisplayModeSelector`.
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(8.dp)` / `MaterialTheme.shapes.small` for toggle card.
  - Spacing: standard 8.dp, 12.dp, 16.dp paddings and spacers.
  - Colors: `MaterialTheme.colorScheme.primary` for icons/accents, `MaterialTheme.colorScheme.onSurface` for text, `MaterialTheme.colorScheme.surfaceVariant` for container cards.
* **New one-off styles & justification**: None. Strictly adheres to existing design guidelines and material theme tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Keys in 9 Languages
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`)
* Add `workout_card_reorder_toggle` and `settings_workout_details_title` with matching `%1$d` specifier.

### Step 2: Refactor `WorkoutDetailSectionAvailability.isSectionActive`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabConfig.kt`
* Remove legacy `detailPreferences.showX` gating in `isSectionActive`, evaluating solely data availability flags.

### Step 3: Refactor `WorkoutMasksAndCardsSection.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsSection.kt`
* Remove Details column from header and rows.
* Implement collapsible summary cards reorder list (collapsed by default, toggle card with active count and arrow icon).
* Split layout into Trainingsliste vs Trainings-Details blocks.
* Place Summary Zone Display Mode in Trainingsliste block; place Details Zone Display Mode in Trainings-Details block.
* Ensure total file length remains strictly < 400 lines.

### Step 4: Update Unit & Contract Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabsTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/WorkoutMasksAndCardsLayoutTest.kt`
* Assert `isSectionActive` ignores `detailPreferences.showX`.
* Assert `WorkoutMasksAndCardsSection.kt` structural contracts (single checkbox column, no details column, collapsible state, arrow icons, line count < 400).

### Step 5: Targeted Unit Test Verification
* Command:
  `./gradlew testDebugUnitTest --tests "*WorkoutDetailTabsTest*" --tests "*WorkoutMasksAndCardsLayoutTest*" --tests "*TranslationParityTest*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted unit tests to verify contract, layout, and tab availability logic.
  - Run translation parity test across all 9 locales.
  - Full clean-room test suite run (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**:
  - If any unexpected behavior occurs, changes are isolated on `feature/ATT-4043` and can be reverted cleanly via git checkout.
