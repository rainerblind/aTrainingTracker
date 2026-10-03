# Stage 3: Implementation Plan - ATT-1523: [Bug] [UI/UX] Enforce global delete-only long-press context menu and fix Lieblingsorte context menu

**Ticket**: [ATT-1523](https://atrainingtracker.atlassian.net/browse/ATT-1523)  
**Sub-task**: [ATT-1527](https://atrainingtracker.atlassian.net/browse/ATT-1527) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`  
**Test Mapping**: `TST-UI-070`  
**Branch**: `feature/ATT-1523`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In the aTrainingTracker application, all deletable list items (such as Workouts, Routes, Equipment, Sport Types, and Clusters) follow a universal UI design contract: long-pressing an item card displays a context menu anchored at the top-left (`Alignment.TopStart`) of the card containing strictly and exclusively a "Delete" action (or documented, explicit item lifecycle actions such as "Mark as finished" for unfinished workouts or "Duplicate as local" for Strava routes).

However, during user testing of the "Lieblingsorte" (Favorite Locations) screen (`KnownLocationsScreen.kt`), two significant deviations from this standard were discovered:
1. `KnownLocationCard` featured a redundant 3-dots overflow icon button (`Icons.Default.MoreVert`, test tag `location_overflow_button_${item.id}`) in its header row, which cluttered the card layout.
2. The context menu was anchored at `Alignment.TopEnd` (Top-Right) with `padding(end = 12.dp, top = 8.dp)` and contained three separate action items: "Show on Map", "Edit", and "Delete".

This violated `REQ-UI-061` and created visual and behavioral fragmentation. Furthermore, because single-tapping any `KnownLocationCard` already opens `EditKnownLocationDialog` (`onClick = onEdit`), and the central navigation map (`NavRoutes.MAP`) already displays favorite locations directly on the map, having "Edit" and "Show on Map" in the context menu is completely redundant.

The goal of this ticket is to:
1. Cleanly excise the redundant `MoreVert` button from `KnownLocationCard`.
2. Re-anchor the long-press context menu to `Alignment.TopStart` with `padding(start = 12.dp, top = 8.dp)`.
3. Eliminate "Show on Map" and "Edit" menu items so the context menu strictly presents "Delete".
4. Introduce a static/AST structural audit test (`GlobalDeleteContextMenuAuditTest.kt`) ensuring that all list item cards across the application adhere to the `REQ-UI-061` delete-only and Top-Left anchor invariant.

---

## 2. Traceability & Requirements Mapping

* **`REQ-UI-061`**: *Universal Long-Press Context Menu Standards for Deletable List Items*. Mandates `Alignment.TopStart` anchoring, standard offsets (`start = 12.dp, top = 8.dp`), strictly delete-only contents (or documented lifecycle actions), and explicitly prohibits secondary actions ("Edit", "Show on Map").
* **`REQ-UI-165`**: *Known Start Locations Management*. Standardizes `KnownLocationsScreen.kt` management list interactions, ensuring single-tap launches edit bottom sheet and long-press invokes delete-only menu.
* **`REQ-UI-166`**: *Lieblingsorte UI/UX Harmonization*. Harmonizes German and English naming and list styling.
* **`REQ-UI-180`**: *Display Favorite Locations on Central Navigation Map*. Streamlines favorite locations into a clean list view without redundant secondary menu triggers.
* **`TST-UI-070`**: *Universal Long-Press Context Menu Verification & Lieblingsorte Compliance*. Covers static structural AST audit, unit/layout test, 9-language localization parity, and clean-room full test suite regression.

---

## 3. System Invariants & Preserved Behavior

1. **Single-Tap Edit Invariance**: Tapping anywhere on `KnownLocationCard` (`onClick = onEdit`) MUST continue to immediately open `EditKnownLocationDialog`.
2. **Database & Repository Thread Safety**: Zero modification to SQLite schemas, `KnownLocationsRepository`, `KnownLocationsDatabaseManager`, or `KnownLocationsDB-Thread` background serialization.
3. **Map Navigation Independence**: Navigation to the central map (`NavRoutes.MAP`) and interactive bottom peek sheets on map markers remain 100% operational.
4. **9-Language Localization Parity**: Translations for `@string/delete` and `@string/really_delete_format` remain identical and valid across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. **Human Gate & Subtask Lifecycle**:
   - Subtasks in `In Überprüfung` transition directly to `Erledigt` via transition `freigabe` once automated gate audits pass.
   - Parent ticket `ATT-1523` completion remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `KnownLocationCard` in `KnownLocationsScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* **Changes**:
  1. Remove the header `IconButton` containing `Icons.Default.MoreVert` (lines 333–344). Allow the location title `Text` to occupy available horizontal space cleanly.
  2. In the context menu `Box` container:
     - Change alignment from `Alignment.TopEnd` to `Alignment.TopStart`.
     - Change padding from `padding(end = 12.dp, top = 8.dp)` to `padding(start = 12.dp, top = 8.dp)`.
  3. Inside the `DropdownMenu`:
     - Remove `DropdownMenuItem` for `R.string.known_location_show_map` (`location_show_on_map_action_${item.id}`).
     - Remove `DropdownMenuItem` for `R.string.Edit` (`location_edit_action_${item.id}`).
     - Retain `DropdownMenuItem` for `R.string.delete` (`location_delete_action_${item.id}`), triggering `onDelete()`.
  4. In `KnownLocationCard` signature, deprecate or remove the unused internal `onShowOnMap` parameter from the card composable while keeping `KnownLocationsScreen` top-level signature backward-compatible.

### Component 2: Global Context Menu Static Audit Test (`GlobalDeleteContextMenuAuditTest.kt`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/GlobalDeleteContextMenuAuditTest.kt`
* **Scope**: JVM Unit / AST & Regex Source Code Inspection Test
* **Validations**:
  1. Inspect source files of all card components with long-press context menus:
     - `KnownLocationsScreen.kt`
     - `RouteItem.kt`
     - `EquipmentTabsScreen.kt`
     - `SportTypesTabsScreen.kt`
     - `WorkoutClusterComponents.kt`
     - `WorkoutSummaryCompact.kt`
  2. Assert that context menu containers specify `Alignment.TopStart`.
  3. Assert that context menu container paddings use `start = 12.dp, top = 8.dp`.
  4. Assert that none of the long-press card menus contain unauthorized "Edit" or "Show on Map" action items.

### Component 3: `KnownLocationsScreenTest.kt` Updates
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* **Validations**:
  1. Add tests verifying `KnownLocationCard` does not reference `Icons.Default.MoreVert` or `location_overflow_button_`.
  2. Verify that context menu entries contain `R.string.delete` and exclude `R.string.known_location_show_map` and `R.string.Edit`.
  3. Verify 9-language localization parity for `delete` and `really_delete_format`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor `KnownLocationCard` in `KnownLocationsScreen.kt`
* Remove `Icons.Default.MoreVert` icon button.
* Re-align context menu `Box` to `Alignment.TopStart` with `padding(start = 12.dp, top = 8.dp)`.
* Strip out "Show on Map" and "Edit" menu items, leaving only the "Delete" action.

### Step 2: Author `GlobalDeleteContextMenuAuditTest.kt`
* Create new unit test class `GlobalDeleteContextMenuAuditTest` under `app/src/test/java/com/atrainingtracker/trainingtracker/ui/`.
* Implement automated inspection across the 6 list card components verifying alignment, padding, and action purity.

### Step 3: Extend `KnownLocationsScreenTest.kt`
* Add layout and resource verification tests for `KnownLocationCard` context menu structure and localization keys.

### Step 4: Run Targeted Unit Tests
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreenTest" --tests "com.atrainingtracker.trainingtracker.ui.GlobalDeleteContextMenuAuditTest"
  ```
* Verify 100% test pass.

### Step 5: Run Full Test Suite Regression (Stage 5 Pre-check)
* Execute:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Ensure zero regressions across all unit, repository, database, and ViewModel tests.

---

## 6. Verification & Traceability Matrix

| Test ID | Method / Test Class | Requirement | Verification Target |
| :--- | :--- | :--- | :--- |
| `TST-UI-070.1` | `GlobalDeleteContextMenuAuditTest` | `REQ-UI-061` | AST audit of TopStart alignment and delete-only purity across all 6 list cards |
| `TST-UI-070.2` | `KnownLocationsScreenTest` | `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-180` | `KnownLocationCard` overflow button removal and context menu content |
| `TST-UI-070.3` | `KnownLocationsScreenTest` | `REQ-UI-061`, `REQ-UI-106` | Localization parity for delete actions across all 9 supported locales |
| `TST-UI-070.4` | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Full clean-room regression across entire application test suite |
