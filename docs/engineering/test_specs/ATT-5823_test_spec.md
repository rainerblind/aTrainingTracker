# Stage 2: Requirement & Test Specification - ATT-5823: KnownLocationsScreen Filter UI & Default Starts Threshold

**Ticket**: [ATT-5823](https://rainerblind.atlassian.net/browse/ATT-5823)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-466` (*KnownLocationsScreen Multi-Dimensional Filter UI & Default Threshold*)  
**Test Spec ID**: `TST-UI-466`  
**Branch**: `feature/ATT-5823`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-UI-466)

### 1.1 Problem Statement & Rationale
In "Lieblingsorte", all known start locations are currently shown unconditionally, cluttering the view with incidental 1–4 start recordings (e.g. vacation rides), while `KnownLocationsListContent` ignores filtering. The system SHALL default to $\ge 5$ starts (`minStarts = 5`) on entry, provide standard Material 3 filter controls (`FilterActionButton` with badge, `ActiveKnownLocationsFilterChipsRow`, and `KnownLocationsFilterBottomSheet` based on `FilterBottomSheetScaffold`), and render `uiState.filteredLocations` in the main list.

### 1.2 Functional & Architectural Requirements
1. **Default Starts Threshold (`REQ-UI-466.1`)**: `KnownLocationsViewModel` SHALL initialize with `minStarts = 5` (`KnownLocationsFilterCriteria(query = "", minStarts = 5)`). Filtering SHALL evaluate against authoritative starts (`startsByLocationId[item.id] ?: item.hitCount`).
2. **Header Filter Action Button (`REQ-UI-466.2`)**: `KnownLocationsScreen` top app bar SHALL host `FilterActionButton` beside the sort button, displaying a `BadgedBox` with `activeFilterCount` when filters are active.
3. **Active Filter Chips Row (`REQ-UI-466.3`)**: When filters are active, `ActiveKnownLocationsFilterChipsRow` SHALL display removable chips (`"≥ 5 Starts"`, keyword query) with 1-tap `×` dismissal. Clearing starts sets `minStarts = null` (revealing all locations).
4. **Modal Filter Bottom Sheet (`REQ-UI-466.4`)**: Tapping the filter button SHALL open `KnownLocationsFilterBottomSheet` (`FilterBottomSheetScaffold`) with text search, starts presets ($\ge 3$, $\ge 5$, $\ge 10$, $\ge 25$), and "Reset all" / "OK" actions.
5. **Filtered List Binding & Fallback (`REQ-UI-466.5`)**: `KnownLocationsListContent` SHALL render `uiState.filteredLocations` with empty-state feedback (`filter_no_matching_locations`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default $\ge 5$ Starts)**: *Given* the athlete opens Lieblingsorte, *When* rendered, *Then* only locations with $\ge 5$ starts are listed and `"≥ 5 Starts"` chip is visible.
* **Criterion 2 (Filter Reset)**: *Given* active filters, *When* tapping `×` on starts chip or "Reset all", *Then* all locations (including 1–4 starts) are listed.
* **Criterion 3 (Modal Sheet Controls)**: *Given* tapping the filter action button, *When* bottom sheet opens, *Then* search query input and presets for $\ge 3, 5, 10, 25$ starts are selectable.

### 1.4 System Invariants
Database schemas, stored location items, click listeners, edit/delete actions, and unit conversions MUST NOT be broken.

---

## 2. Test Specification (TST-UI-466)

### Test Case 1: Default Starts Threshold & Query Unit Tests (`TST-UI-466.1`)
* **Scope**: Unit Test | **Target File**: `app/src/test/.../ui/knownlocations/KnownLocationsViewModelTest.kt`
* **Preconditions**: Locations with varying starts (e.g. 1, 3, 5, 12 starts).
* **Action**: Verify default state has `minStarts = 5`, $< 5$ starts excluded; set `minStarts = null`, all included; apply name query.
* **Expected Result**: Filtering matches criteria accurately via `startsByLocationId ?: hitCount`.

### Test Case 2: Filter Action Button & Badge Contract Tests (`TST-UI-466.2`)
* **Scope**: Compose Contract Test | **Target File**: `app/src/test/.../ui/knownlocations/KnownLocationsScreenTest.kt`
* **Preconditions**: `KnownLocationsScreen` rendered with active/empty criteria.
* **Action**: Assert presence of `known_locations_filter_button` and badge count visibility.
* **Expected Result**: Badge displays active count when `isFilterActive == true`.

### Test Case 3: Active Filter Chips Row & Dismissal Contract Tests (`TST-UI-466.3`)
* **Scope**: Compose Contract Test | **Target File**: `app/src/test/.../ui/knownlocations/KnownLocationsScreenTest.kt`
* **Preconditions**: `ActiveKnownLocationsFilterChipsRow` with `minStarts = 5`.
* **Action**: Assert chip text formatting and verify click on remove triggers `onRemoveMinStarts`.
* **Expected Result**: Starts chip dismisses, updating list to unfiltered view.

### Test Case 4: 9-Language Localization Audit (`TST-UI-466.4`)
* **Scope**: Unit Test | **Target File**: `app/src/test/.../localization/TranslationParityTest.kt`
* **Action**: Verify `filter_known_locations_title`, `filter_min_starts_label`, `filter_min_starts_chip_format`, and `filter_no_matching_locations` exist in all 9 locales.
* **Expected Result**: 100% parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Test Case 5: Targeted Regression Execution (`TST-UI-466.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass across all known locations, translation parity, theme, and build tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-466.1` | Unit | `KnownLocationsViewModelTest.kt` | `REQ-UI-466.1` | Specified |
| `TST-UI-466.2` | Contract | `KnownLocationsScreenTest.kt` | `REQ-UI-466.2` | Specified |
| `TST-UI-466.3` | Contract | `KnownLocationsScreenTest.kt` | `REQ-UI-466.3`, `REQ-UI-466.5` | Specified |
| `TST-UI-466.4` | Parity | `TranslationParityTest.kt` | `REQ-LOC-001`, `REQ-UI-466.4` | Specified |
| `TST-UI-466.5` | Regression | Targeted Test Suite | `REQ-PRO-001` | Specified |
