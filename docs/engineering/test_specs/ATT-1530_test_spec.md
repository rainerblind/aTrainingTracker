# Stage 2 Requirement & Test Specification: ATT-1530 Card Header Actions & 3-Dots Removal

**Ticket**: [ATT-1530](https://atrainingtracker.atlassian.net/browse/ATT-1530)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Subtask**: `ATT-1532`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Traceability & Specification

This ticket addresses the removal of the 3-dots overflow button and card header optimization for `KnownLocationCard` within `KnownLocationsScreen.kt`.

### Primary Requirement: `REQ-UI-061` (*Universal Delete-Only Long-Press Contract & Lieblingsorte Card UI Harmonization*)
* **Clause 2 (`KnownLocationsScreen.kt`)**:
  - The redundant `IconButton(MoreVert)` overflow icon SHALL be excised from the `KnownLocationCard` header, allowing the location title to span the header cleanly (`Modifier.fillMaxWidth()`) and matching all other application cards (`RouteItem`, `EquipmentTabsScreen`, `WorkoutSummaryCompact`).
  - Single-tap on `KnownLocationCard` (`onClick = onEdit`) SHALL launch `EditKnownLocationDialog`.
  - Long-press on `KnownLocationCard` (`onLongClick`) SHALL anchor `DropdownMenu` strictly to `Alignment.TopStart` containing strictly and exclusively the "Löschen" / "Delete" action (`R.string.delete`).

---

## 2. Acceptance Criteria (Given-When-Then)

### AC-1: Header Layout & Zero 3-Dots Button
* **Given** an athlete viewing the "Lieblingsorte" management screen (`KnownLocationsScreen.kt`),
* **When** inspecting any `KnownLocationCard`,
* **Then** the card header SHALL NOT display any `MoreVert` / 3-dots overflow icon button.
* **And** the location title text SHALL utilize full available card width with single-line ellipsis.

### AC-2: Primary Card Interactions
* **Given** an athlete viewing the "Lieblingsorte" list,
* **When** tapping directly on a location card,
* **Then** `EditKnownLocationDialog` SHALL open immediately to edit the location.
* **When** long-pressing a location card,
* **Then** a delete-only `DropdownMenu` SHALL open anchored at Top-Left.

---

## 3. Test Specification (`TST-UI-070`)

* **Target Test Suite**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* **Test Cases**:
  1. `testKnownLocationCard_noMoreVertButton`: Validates that no `IconButton` with `Icons.Default.MoreVert` or content description `More` is rendered in `KnownLocationCard`.
  2. `testKnownLocationCard_tapOpensEdit`: Validates that clicking `location_card_{id}` invokes `onEdit`.
  3. `testKnownLocationCard_longClickOpensDeleteMenu`: Validates that long-clicking `location_card_{id}` triggers the delete context menu.
* **Full Clean-Room Regression**:
  - Execute `./gradlew testDebugUnitTest` to confirm 100% pass rate.
