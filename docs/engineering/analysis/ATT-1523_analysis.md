# Stage 1: Problem Domain & Root Cause Analysis - ATT-1523: Global Delete-Only Context Menu Enforcement

**Ticket**: [ATT-1523](https://atrainingtracker.atlassian.net/browse/ATT-1523)  
**Sub-task**: [ATT-1525](https://atrainingtracker.atlassian.net/browse/ATT-1525) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Requirement Mapping**: `REQ-UI-061` (*Unified Deletion UI*), `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`  
**Test Mapping**: `TST-UI-070`  
**Branch**: `feature/ATT-1523`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Executive Summary & Problem Statement

During user testing of the "Lieblingsorte" (Favorite Locations) list (`KnownLocationsScreen.kt`), a significant divergence from the application's global UI/UX design standard was identified:
- Long-pressing a `KnownLocationCard` (or tapping its 3-dots overflow icon) triggers a context menu positioned at the **Top-Right** (`Alignment.TopEnd`) containing multiple heterogeneous actions: "Show on Map", "Edit", and "Delete".
- This directly violates the core application design contract established in `REQ-UI-061` ("Unified Deletion UI"), which mandates that long-pressing any deletable list item across the entire application (Workouts, Routes, Clusters, Equipment, Sport Types) SHALL strictly and exclusively display a deletion context menu anchored at the **Top-Left** (`Alignment.TopStart`).
- The root cause is a specification loophole in `REQ-UI-061` (which established the Top-Left delete menu pattern but did not explicitly forbid the inclusion of secondary navigation or editing options) coupled with historical specifications in `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` that specified "Edit" and "Show on Map" inside an overflow menu for `KnownLocationsScreen`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

### A. Archaeological Trace of Target Requirements
1. **`REQ-UI-061` (Unified Deletion UI)**:
   - *Origin*: Established during earlier UI standardization sprints to eliminate disparate delete buttons and swipe-to-delete gestures across list screens.
   - *Original Formulation*: Specified that all deletable list items (Workouts, Routes, Clusters, Equipment, Sport Types) must trigger a `DropdownMenu` positioned at the Top-Left corner of the card on long-press.
   - *Chesterton's Fence Insight*: The primary intent was preventing accidental deletion while establishing an intuitive, predictable interaction model across all lists. Primary interaction is always card tap (`onClick`); long-press is reserved exclusively for destructive lifecycle actions (deletion). Only workouts have a documented lifecycle exception (marking an unfinished workout as finished).

2. **`REQ-UI-165` & `REQ-UI-166` (Known Start Locations / Lieblingsorte UI/UX)**:
   - *Origin*: Introduced in `ATT-919` / `ATT-1382` (commits `48d0bdc8`, `b4ebca54`).
   - *Original Formulation*: Included an action overflow menu providing Edit, Center on Map, and Delete.
   - *Root Reason for Divergence*: At the time `ATT-919` was developed, `KnownLocationsScreen` was implemented with a custom card layout rather than adhering strictly to the `MappableListItem` delete-only contract.

3. **`REQ-UI-180` (Central Navigation Map Integration)**:
   - *Origin*: Introduced in `ATT-1449` to display favorite start locations on the central navigation map (`NavRoutes.MAP`) alongside routes and tracks.
   - *Root Reason for Divergence*: While REQ-UI-180 successfully eliminated the redundant "Karte" tab in `KnownLocationsScreen`, it retained the mention of "Show on Map" in the list item overflow menu.

### B. Core Invariants to Preserve
1. **Universal Interaction Model**: Single tap on `KnownLocationCard` invokes `onClick = onEdit`, opening `EditKnownLocationDialog` (which includes embedded mini-map geofence visualization).
2. **Context Menu Exclusivity & Top-Left Anchor**: Long-press on `KnownLocationCard` (`onLongClick`) must anchor to `Alignment.TopStart` (Top-Left) and present exclusively "Löschen" / "Delete" (`R.string.delete`).
3. **No Secondary Noise**: Non-destructive actions (navigation, editing) must never pollute the long-press context menu.
4. **Delete Confirmation Invariant**: Selecting "Delete" must trigger `DeleteConfirmationDialog` before mutating SQLite.
5. **Localization Parity**: 100% translation parity across all 9 supported locales.

---

## 3. Scope Bounding & Impact Analysis

### In-Scope
- Update `docs/requirements.md` (`REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`) to formalize the universal delete-only invariant.
- Refactor `KnownLocationCard` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`:
  - Anchor `DropdownMenu` to `Alignment.TopStart` (Top-Left, covering header).
  - Remove "Show on Map" and "Edit" from the context menu, leaving strictly and exclusively "Delete".
  - Remove redundant `IconButton(MoreVert)` from the card header so the title spans naturally and matches all other application cards (`RouteItem`, `EquipmentTabsScreen`, `WorkoutSummaryCompact`).
- Author automated tests verifying the delete-only context menu contract.

### Out-of-Scope
- Mutating SQLite schema or `KnownLocationsRepository`.
- Altering the central map favorite locations layer in `MapContentScope.kt`.
- Changing `DeleteConfirmationDialog` or deletion persistence workflows.

---

## 4. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Long-Press on Known Location Card**
  - *Given* the athlete viewing the "Lieblingsorte" list (`KnownLocationsScreen`),
  - *When* the athlete long-presses any location card,
  - *Then* a `DropdownMenu` SHALL appear anchored strictly at the **Top-Left** (`Alignment.TopStart`) corner of the card.
  - *And* the menu SHALL contain strictly and exclusively the "Löschen" / "Delete" action (`R.string.delete`, `Icons.Default.Delete`).
  - *And* the menu SHALL NOT contain "Show on Map" or "Edit".

- **Scenario 2: Single Tap on Known Location Card**
  - *Given* the athlete viewing the "Lieblingsorte" list,
  - *When* the athlete taps any location card,
  - *Then* `EditKnownLocationDialog` SHALL launch immediately to edit the location's name, altitude, and geofence radius.

- **Scenario 3: Universal System Invariant Verification**
  - *Given* the automated test suite,
  - *When* static AST / layout analysis runs across all list card components (`RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `KnownLocationsScreen.kt`),
  - *Then* all long-press context menus SHALL anchor to `TopStart` and contain strictly deletion (or documented lifecycle actions).
