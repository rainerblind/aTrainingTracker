# Stage 2: Requirement & Test Specification - ATT-1541: Standardize List Item Heading Click Behavior Across All Application Lists

**Ticket**: [ATT-1541](https://atrainingtracker.atlassian.net/browse/ATT-1541)  
**Sub-task**: [ATT-1543](https://atrainingtracker.atlassian.net/browse/ATT-1543) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-191` (*Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance*)  
**Test Spec ID**: `TST-UI-145`  
**Branch**: `feature/ATT-1541`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-191)

### 1.1 Problem Statement & Rationale
Endurance athletes navigating through the application's card lists encounter diverging, inconsistent click interactions across different domains:
1. Spatial / analytical entities (Workouts, Clusters, Segments) navigate to map / detailed inspection views when clicking headers or content sections, with editing strictly isolated to dedicated edit buttons or context actions.
2. In Routes (`RouteItem.kt`), clicking `RouteSummaryHeader` originally opened the editor (`EditRouteScreen`), while clicking preview maps opened route details, and no dedicated edit button was present.
3. Configuration entities (Equipment, Sport Types, Favorite Locations / Lieblingsorte) have no secondary map or telemetry inspection view; their cards open configuration editors directly on single-tap (`REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`).

Standardizing heading click interaction semantics across analytical/spatial lists to "inspection-first", introducing a dedicated Material 3 edit button in `RouteSummaryHeader`, and preserving configuration-first entities establishes intuitive, predictable navigation.

### 1.2 Functional & Architectural Requirements
1. **Domain Interaction Taxonomy**:
   - *Spatial / Analytical Entities (Workouts, Routes, Workout Clusters, Segments)*: Tapping the card header, title, description, metrics, or media preview SHALL navigate directly to the primary detailed inspection view (`TrackOnMapScreen` for Workouts per `REQ-SET-071`, Route Details for Routes, `WorkoutClusterHeatmapScreen` for Clusters, and `SegmentOnMapScreen` for Segments). Navigating to edit mode via title or card body tap is strictly prohibited.
   - *Configuration Entities (Equipment, Sport Types, Favorite Locations)*: Configuration entities without a secondary map or telemetry inspection view SHALL preserve direct-to-editor single-tap navigation (`onEdit` / `EditKnownLocationDialog` per `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`, `EquipmentTabsScreen`, `SportTypesTabsScreen`).
2. **Dedicated Route Header Edit Button**:
   - `RouteSummaryHeader.kt` SHALL incorporate a dedicated edit action button (`IconButton`) placed in the header identity row.
   - The edit button SHALL display `Icons.Default.Edit`, tinted with `MaterialTheme.colorScheme.primary` per `REQ-UI-182`.
   - The edit button SHALL strictly comply with Material 3 accessibility guidelines with a minimum touch target bounding box of at least $48\times 48\text{dp}$ (`Modifier.size(48.dp)`), housing a $24\times 24\text{dp}$ icon.
   - The edit button SHALL use localized accessibility content description `@string/route_edit`.
   - Tapping this edit button SHALL invoke `onEditClick` (launching `EditRouteScreen`).
3. **Route Item Heading Click Harmonization**:
   - In `RouteItem.kt`, tapping `RouteSummaryHeader` SHALL invoke `onMapClick(summary.id)` to open route details, eliminating the previous split navigation where tapping the header edited the route while tapping the map inspected it.
   - The long-press context menu of `RouteItem.kt` SHALL remain strictly compliant with `REQ-UI-061` (delete-only, plus the documented `REQ-EXT-004` Strava duplication lifecycle action; no secondary non-destructive actions).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Route Inspection Navigation)**:
  * *Given* an athlete viewing the routes list in `RouteTabbedScreen` or `RoutesScreen`,
  * *When* the athlete taps the route header, map preview, or elevation profile,
  * *Then* the system SHALL navigate to the route inspection/details view (`selectedRouteIdForDetails = id`).
* **Criterion 2 (Dedicated Edit Button Affordance & Geometry)**:
  * *Given* an athlete viewing a route card in `RouteItem`,
  * *When* inspecting the header row,
  * *Then* a dedicated edit button with `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary` and a minimum $48\times 48\text{dp}$ touch target SHALL be visible.
* **Criterion 3 (Edit Route Invocation)**:
  * *Given* an athlete viewing a route card in `RouteItem`,
  * *When* the athlete taps the dedicated edit button in the header,
  * *Then* the system SHALL open `EditRouteScreen` (`selectedRouteIdForEdit = id`).
* **Criterion 4 (Configuration Entity Invariant)**:
  * *Given* an athlete interacting with configuration list items (Lieblingsorte, Equipment, Sport Types),
  * *When* the card body is tapped,
  * *Then* the configuration/edit dialog SHALL open directly, preserving the configuration entity pattern.

### 1.4 System Invariants
* Long-press context menus MUST NOT include non-destructive edit items per `REQ-UI-061`.
* Route selection toggle (`onToggleSelection`) and Strava duplication (`onDuplicateAsLocal`) MUST remain fully functional.
* 9-language localization parity for `@string/route_edit` MUST be preserved.
* Material 3 minimum touch target guidelines ($\ge 48\text{dp} \times 48\text{dp}$) MUST be enforced.

---

## 2. Test Specification (TST-UI-145)

### Test Case 1: Route Item Heading Click Standardized Navigation (`TST-UI-145.1`)
* **Scope**: Unit / Compose UI Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemClickStandardizationTest.kt`
* **Preconditions**: A `RouteSummary` entity with mock path points rendered inside `RouteItem`.
* **Action**: Perform click on `RouteSummaryHeader`, click on map preview, and click on elevation profile.
* **Expected Result**: In all cases, `onMapClick(summary.id)` is invoked. `onHeaderClick` (edit action) is NOT invoked by tapping the header text or card background.

### Test Case 2: Dedicated Edit Button Contract & Accessibility Touch Target (`TST-UI-145.2`)
* **Scope**: Unit / Compose UI Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemClickStandardizationTest.kt`
* **Preconditions**: `RouteSummaryHeader` rendered with `onEditClick` provided.
* **Action**: Find `IconButton` with content description matching `@string/route_edit` and perform click.
* **Expected Result**: `onEditClick` is invoked. The button bounding box is at least $48\text{dp} \times 48\text{dp}$. Icon tint is `MaterialTheme.colorScheme.primary`.

### Test Case 3: Universal Delete-Only Context Menu Invariant (`TST-UI-145.3`)
* **Scope**: Unit / Compose UI Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemClickStandardizationTest.kt`
* **Preconditions**: `RouteItem` displayed with long-press trigger.
* **Action**: Long-press on the header surface to expand `DropdownMenu`.
* **Expected Result**: Context menu contains strictly "Löschen" / "Delete" (and "Als lokale Route speichern" if Strava route per `REQ-EXT-004`). Zero "Edit" or "Bearbeiten" menu items exist per `REQ-UI-061`.

### Test Case 4: 9-Language Localization Audit (`TST-UI-145.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/translations/TranslationParityTest.kt`
* **Goal**: Verify string resource `@string/route_edit` exists across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
* **Expected Result**: 100% parity across all 9 locales.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-145.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the complete application unit test suite.
* **Expected Result**: All tests pass cleanly with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-145.1` | Unit / UI | `RouteItem.kt` / `RouteSummaryHeader.kt` | `REQ-UI-191` (Sec 1, 3) | Specified |
| `TST-UI-145.2` | Unit / UI | `RouteSummaryHeader.kt` | `REQ-UI-191` (Sec 2), `REQ-UI-182` | Specified |
| `TST-UI-145.3` | Unit / UI | `RouteItem.kt` context menu | `REQ-UI-191` (Sec 3), `REQ-UI-061` | Specified |
| `TST-UI-145.4` | Localization | `strings.xml` (all 9 locales) | `REQ-UI-191`, `REQ-UI-106` | Specified |
| `TST-UI-145.5` | Regression | Full test suite | `REQ-PRO-014` | Specified |
