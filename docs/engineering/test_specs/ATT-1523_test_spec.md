# Stage 2: Requirement & Test Specification - ATT-1523: [Bug] [UI/UX] Enforce global delete-only long-press context menu and fix Lieblingsorte context menu

**Ticket**: [ATT-1523](https://atrainingtracker.atlassian.net/browse/ATT-1523)  
**Sub-task**: [ATT-1526](https://atrainingtracker.atlassian.net/browse/ATT-1526) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-UI-061` (*Unified Deletion UI*), `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`  
**Test Spec ID**: `TST-UI-070`  
**Branch**: `feature/ATT-1523`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-061)

### 1.1 Problem Statement & Rationale
During user testing of the "Lieblingsorte" (Favorite Locations) list (`KnownLocationsScreen.kt`), a serious violation of global UI standards was discovered:
* Long-pressing a `KnownLocationCard` (or tapping its 3-dots overflow icon) opens a context menu positioned at the **Top-Right** (`Alignment.TopEnd`) containing multiple heterogeneous actions: "Show on Map", "Edit", and "Delete".
* This violates the global application requirement (`REQ-UI-061`), where all deletable list items across the app (Workouts, Routes, Clusters, Equipment, Sport Types, Favorite Locations / Lieblingsorte) must strictly and exclusively present the "Delete" option on long-press, anchored at the **Top-Left** (`Alignment.TopStart`, covering the card header).
* Because `REQ-UI-061` did not explicitly forbid non-deletion actions, and legacy specifications in `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` historically mentioned "Edit" and "Center on Map" / "Show on Map" inside an overflow menu, this inconsistency slipped through.

### 1.2 Functional & Architectural Requirements
1. **Universal Delete-Only Long-Press Contract (`REQ-UI-061`)**:
   * All deletable list items across the entire application (Workouts, Routes, Clusters, Equipment, Sport Types, Favorite Locations / Lieblingsorte) SHALL strictly and exclusively present the deletion option in their long-press context menu (`DropdownMenu`).
   * The context menu SHALL be anchored consistently at the **Top-Left corner** (`Alignment.TopStart` with `start = 12.dp, top = 8.dp`) of the card, covering the header.
   * Non-destructive secondary actions (such as "Edit", "Navigation", "Show on Map") are **strictly prohibited** from appearing in the long-press context menu.
   * *Documented Lifecycle Exceptions*: Marking an unfinished workout as finished (`REQ-UI-046` in `WorkoutHeader.kt` / `WorkoutSummaryCompact.kt`) and saving a Strava route as a local route (`REQ-EXT-004` in `RouteItem.kt`) are preserved as documented lifecycle options.
2. **Lieblingsorte Card UI Harmonization (`KnownLocationsScreen.kt`)**:
   * `KnownLocationCard` SHALL anchor its `DropdownMenu` strictly to `Alignment.TopStart` (Top-Left corner).
   * The `DropdownMenu` SHALL contain strictly and exclusively the "Löschen" / "Delete" action (`R.string.delete`, `Icons.Default.Delete`).
   * The secondary actions "Show on Map" (`R.string.known_location_show_map`) and "Edit" (`R.string.Edit`) SHALL be removed from the `KnownLocationCard` context menu.
   * The redundant `IconButton(MoreVert)` overflow icon SHALL be removed from the `KnownLocationCard` header, allowing the location title to span the header cleanly and matching all other application cards (`RouteItem`, `EquipmentTabsScreen`, `WorkoutSummaryCompact`).
   * Single-tap on `KnownLocationCard` (`onClick = onEdit`) SHALL continue to open `EditKnownLocationDialog` (which includes embedded mini-map geofence visualization).
3. **Requirement Alignment (`REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`)**:
   * `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` SHALL be refined to eliminate any references to an action overflow menu containing "Edit" or "Show on Map", aligning them with the universal deletion contract.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-061` (*Unified Deletion UI*), targeting `MappableListItem.kt` and all deletable list card composables (`RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `WorkoutSummaryCompact.kt`, `KnownLocationsScreen.kt`), along with `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` in `docs/requirements.md`.
* **Historical Origin & Commit Trace**: Commit `ceaf4070` (ATT-238 / ATT-1479), commit `48d0bdc8` (ATT-919, 2026-09-24), commit `b4ebca54` (ATT-1382, 2026-09-25), and commit `191ba05d` (ATT-1449 / ATT-1553, 2026-09-28).
* **Root Reason for Existing Formulation**: `REQ-UI-061` originally established the Top-Left delete menu pattern to prevent accidental deletion and replace disparate swipe/delete buttons, but did not explicitly prohibit secondary actions. `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` predated full UI consolidation and carried legacy specifications for a 3-dots overflow menu containing "Edit" and "Show on Map".
* **Preservation of Core Invariants**: Primary single-tap card interaction (`onClick = onEdit`) opening `EditKnownLocationDialog` remains 100% intact; central navigation map (`NavRoutes.MAP`) continues to render all favorite locations with peek sheets; SQLite schema V5, single-thread database serialization (`KnownLocationsDB-Thread`), and 9-language localization parity are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Long-Press Context Menu Anchor & Exclusivity)**:
  * *Given* the athlete viewing the "Lieblingsorte" list (`KnownLocationsScreen`),
  * *When* the athlete long-presses any location card,
  * *Then* a `DropdownMenu` SHALL appear anchored strictly at the **Top-Left** (`Alignment.TopStart`) corner of the card.
  * *And* the menu SHALL contain strictly and exclusively the "Löschen" / "Delete" action (`R.string.delete`, `Icons.Default.Delete`).
  * *And* the menu SHALL NOT contain "Show on Map" or "Edit".
* **Criterion 2 (Card Header Cleanliness)**:
  * *Given* the athlete viewing the "Lieblingsorte" list,
  * *When* inspecting `KnownLocationCard`,
  * *Then* the card header SHALL NOT display any `MoreVert` / 3-dots overflow icon button.
* **Criterion 3 (Single-Tap Editing)**:
  * *Given* the athlete viewing the "Lieblingsorte" list,
  * *When* the athlete taps any location card,
  * *Then* `EditKnownLocationDialog` SHALL launch immediately to edit the location's name, altitude, and geofence radius.
* **Criterion 4 (Universal System Invariant Verification)**:
  * *Given* the automated test suite,
  * *When* static AST / layout analysis runs across all list card components (`RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `KnownLocationsScreen.kt`),
  * *Then* all long-press context menus SHALL anchor to `TopStart` and contain strictly deletion (or documented lifecycle actions).

### 1.5 System Invariants
* Zero disruption to `EditKnownLocationDialog` invocations from single-tap or central map peek cards.
* Zero modification to SQLite database schemas or `KnownLocationsRepository` data access routines.
* 100% localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Test Specification (TST-UI-070)

### Test Case 1: Global AST & Layout Structural Audit (`TST-UI-070.1`)
* **Scope**: JVM Unit / AST Structural Audit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/GlobalDeleteContextMenuAuditTest.kt`
* **Preconditions**: All UI composables implementing list card context menus.
* **Actions**:
  1. Inspect source files: `KnownLocationsScreen.kt`, `RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `WorkoutSummaryCompact.kt`.
  2. Verify that context menu container boxes are aligned strictly with `Alignment.TopStart`.
  3. Verify that context menu paddings use `start = 12.dp, top = 8.dp` (or standard header-covering offsets).
  4. Verify that no list item context menu introduces unauthorized secondary actions like "Edit" or "Show on Map".
* **Expected Result**: 100% compliance across all tested card components.

### Test Case 2: `KnownLocationCard` Context Menu Specification (`TST-UI-070.2`)
* **Scope**: JVM Unit / Structural Layout Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source and test harness.
* **Actions**:
  1. Verify `KnownLocationCard` does not reference `Icons.Default.MoreVert` or `location_overflow_button_`.
  2. Verify `KnownLocationCard` context menu does not contain `R.string.known_location_show_map` or `R.string.Edit`.
  3. Verify `KnownLocationCard` context menu contains `R.string.delete` and `Icons.Default.Delete`.
  4. Verify context menu alignment is `Alignment.TopStart`.
* **Expected Result**: Card header title expands without trailing overflow icon; long-press menu contains delete only.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-070.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`
* **Goal**: Verify string presence and matching format specifiers across all 9 locales:
  * `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  * Check `@string/delete` and `@string/really_delete_format`.
* **Expected Result**: 100% parity, zero missing resources, zero format specifier mismatches.

### Test Case 4: Clean-Room Full Regression Suite (`TST-UI-070.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Validate that all existing unit, repository, and ViewModel tests continue to pass with 100% success rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-070.1` | Static Audit | `KnownLocationsScreen`, `RouteItem`, `EquipmentTabsScreen`, `WorkoutSummaryCompact` | `REQ-UI-061` | Specified |
| `TST-UI-070.2` | Unit / Layout | `KnownLocationCard` | `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180` | Specified |
| `TST-UI-070.3` | Localization | `strings.xml` (all 9 locales) | `REQ-UI-061`, `REQ-UI-106` | Specified |
| `TST-UI-070.4` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
