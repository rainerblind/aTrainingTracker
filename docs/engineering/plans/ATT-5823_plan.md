# Stage 3: Implementation Plan - ATT-5823: KnownLocationsScreen Filter UI & Default Starts Threshold

**Ticket**: [ATT-5823](https://rainerblind.atlassian.net/browse/ATT-5823)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-UI-466` (`REQ-UI-466.1`–`REQ-UI-466.5`, extending `REQ-UI-165`, `REQ-UI-180`, `REQ-UI-186`, `REQ-DAT-011`)  
**Test Mapping**: `TST-UI-466` (`TST-UI-466.1`–`TST-UI-466.5`, slaved to `TST-UI-132.4`, `TST-DAT-011.3`)  
**Branch**: `feature/ATT-5823`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
In **Lieblingsorte** (`KnownLocationsScreen.kt` / `KnownLocationsViewModel.kt`), all locations are displayed unconditionally without filtering (`uiState.locations`), cluttering the view with incidental 1–4 start recordings (e.g. vacation rides). The screen lacks a filter action button, an active filter chip strip, and a filter bottom sheet. The system must default to $\ge 5$ starts (`minStarts = 5`) on screen entry using authoritative starts (`startsByLocationId[item.id] ?: item.hitCount`), provide standard Material 3 filter controls, and render `filteredLocations`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-466.1` (Default Starts Filter Contract)**: `KnownLocationsViewModel` initializes with `minStarts = 5` and filters via `startsByLocationId[item.id] ?: item.hitCount`. -> `TST-UI-466.1`
* **`REQ-UI-466.2` (Header Filter Action Button & Badge)**: Add `FilterActionButton` in `KnownLocationsScreen` top bar displaying `activeFilterCount` badge. -> `TST-UI-466.2`
* **`REQ-UI-466.3` (Active Filter Chips Row & Dismissal)**: Render `ActiveKnownLocationsFilterChipsRow` below header with 1-tap `×` dismissal (resets `minStarts = null`). -> `TST-UI-466.3`
* **`REQ-UI-466.4` (Modal Filter Bottom Sheet & Localization)**: Implement `KnownLocationsFilterBottomSheet` (`FilterBottomSheetScaffold`) with keyword search and presets ($\ge 3, 5, 10, 25$) + 9-language strings. -> `TST-UI-466.4`
* **`REQ-UI-466.5` (Filtered List Content & Fallback)**: `KnownLocationsListContent` renders `uiState.filteredLocations` with empty-state fallback. -> `TST-UI-466.3`, `TST-UI-466.5`

---

## 3. System Invariants & Preserved Behavior
1. **Data & Schema Integrity**: Stored SQLite locations are never mutated or permanently hidden; clearing filters displays all locations (including 1–4 starts).
2. **Authoritative Starts Metric**: Effective starts formula `startsByLocationId[item.id] ?: item.hitCount` (`REQ-DAT-011`) preserved across sorting and filtering.
3. **Interactive Actions**: Single-tap edit (`onEdit`), long-press delete context menu (`REQ-UI-061`), starts/routes drill-downs, and map thumbnails remain 100% operational.
4. **Code Lock**: Strict adherence to Rule 4; zero production code modified until Gate 3 passes.

---

## 4. Proposed Architectural Changes & UI Consistency
* **UI Consistency**: Reuses canonical filter components: `FilterActionButton`, `RemovableFilterChip`, and `FilterBottomSheetScaffold` matching `WorkoutClustersTabsScreen` and `RouteTabbedScreen`. Standard M3 color tokens (`primary`, `primaryContainer`, `surface`, `onSurfaceVariant`). Zero one-off styles.
* **Component Structure**:
  * `KnownLocationsFilterCriteria.kt`: Immutable data class `(query: String = "", minStarts: Int? = 5)` with `activeFilterCount`, `isEmpty`, `isNotEmpty`.
  * `KnownLocationsViewModel.kt`: Integrates `KnownLocationsFilterCriteria`, updates `applyFilter` to combine search text and `minStarts`, preserves `searchQuery` backward compatibility.
  * `ActiveKnownLocationsFilterChipsRow.kt`: Horizontal scrolling strip rendering `RemovableFilterChip` for query and `"≥ %d starts"`, plus "Clear all".
  * `KnownLocationsFilterBottomSheet.kt`: M3 modal sheet with search input, $\ge 3, 5, 10, 25$ starts `FilterChip` presets, and Reset/OK buttons.
  * `KnownLocationsScreen.kt`: Header filter action, active chips strip insertion, bottom sheet launcher, and `filteredLocations` binding.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Define `KnownLocationsFilterCriteria.kt` & Update `KnownLocationsViewModel.kt` (`REQ-UI-466.1`)
* Create `ui/knownlocations/KnownLocationsFilterCriteria.kt`.
* In `KnownLocationsViewModel.kt`: Add `filterCriteria` to `KnownLocationsUiState`, update `applyFilter` to evaluate `effectiveStarts >= minStarts`, expose `setFilterCriteria`, `setMinStarts`, `clearFilters`.

### Step 2: Add 9-Language Localization Strings (`REQ-UI-466.4`, `REQ-LOC-001`)
* Add `filter_known_locations_title`, `filter_search_known_locations_hint`, `filter_section_starts`, `filter_min_starts_chip_format`, and `filter_no_matching_locations` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Step 3: Implement `ActiveKnownLocationsFilterChipsRow.kt` & `KnownLocationsFilterBottomSheet.kt` (`REQ-UI-466.3`, `REQ-UI-466.4`)
* Implement active chips row using `RemovableFilterChip`.
* Implement filter bottom sheet using `FilterBottomSheetScaffold` with search field and starts presets.

### Step 4: Integrate Filter UI into `KnownLocationsScreen.kt` & Bind `filteredLocations` (`REQ-UI-466.2`, `REQ-UI-466.5`)
* Add `FilterActionButton` in top bar next to sort button; host active chips strip below top bar; wire bottom sheet.
* Update `KnownLocationsListContent` to render `filteredLocations` and handle empty-filtered fallback.

### Step 5: Unit & Contract Tests in `KnownLocationsViewModelTest.kt` & `KnownLocationsScreenTest.kt` (`TST-UI-466.1`–`TST-UI-466.3`)
* Add unit tests for default `minStarts = 5`, threshold filtering, clearing, and search combination.
* Add Compose contract test verifying filter button tag `known_locations_filter_button`, badge, and chip removal.

### Step 6: Verification & Governance Audit (`TST-UI-466.4`, `TST-UI-466.5`)
* Run test suites: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Execute `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass across unit, contract, localization, and theme audit tests.
* **Rollback**: `git checkout -- app/src/` cleanly reverts implementation changes.
