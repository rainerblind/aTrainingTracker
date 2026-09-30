# Stage 1: Problem Domain & Root Cause Analysis - ATT-1541: Standardize List Item Heading Click Behavior Across All Application Lists

**Ticket**: [ATT-1541](https://atrainingtracker.atlassian.net/browse/ATT-1541)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & User Impact

Endurance athletes navigating through the application's card lists encounter diverging, inconsistent click interactions across different domains:
1. **Inspection-First Lists (Workouts, Clusters, Segments)**:
   - In `WorkoutSummary.kt` (governed by `REQ-SET-071`), clicking the card header, description, metrics, extrema, or map preview navigates directly to the detailed map inspection screen (`TrackOnMapScreen`). Editing is strictly segregated to a dedicated `Icons.Default.Edit` button (`REQ-UI-182`).
   - In `WorkoutClusterComponents.kt`, clicking the cluster card header or body navigates to the `WorkoutClusterHeatmapScreen`.
   - In `SegmentItem.kt`, clicking the segment header or card body navigates to `SegmentOnMapScreen`.
2. **Inconsistent Hybrid in Routes (`RouteItem.kt`)**:
   - In `RouteItem.kt`, clicking the map preview or elevation profile navigates to the detailed route inspection screen (`onMapClick` -> `selectedRouteIdForDetails`).
   - However, clicking `RouteSummaryHeader` directly launches `EditRouteScreen` (`onHeaderClick` -> `selectedRouteIdForEdit`).
   - This split behavior causes athlete confusion: tapping the title edits the entity, whereas tapping the map inspects it, and no dedicated edit button is provided.
3. **Configuration-First Entities (Equipment, Sport Types, Lieblingsorte)**:
   - In `EquipmentTabsScreen.kt` and `SportTypesTabsScreen.kt`, entities do not possess a secondary map or telemetry inspection view; clicking the card body directly launches their configuration/edit dialog.
   - In `KnownLocationsScreen.kt` (governed by `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`), the card body launches `EditKnownLocationDialog` directly, while specialized interactive badges drill down into filtered workout and cluster lists.

---

## 2. Chesterton's Fence & Requirement Archaeology

### 1. Requirement & Commit Lineage
* **Workouts Standard (`REQ-SET-071`, Ticket `ATT-850`, Commit `2bf79cdf`)**:
  - Explicitly established the rule: "navigate to `TrackOnMapScreen` whenever an athlete clicks any primary content section of a workout summary card (`WorkoutSummary.kt`), while reserving the workout editor (`EditWorkoutScreen`) strictly for the dedicated edit action button."
* **Universal Delete-Only Long-Press Contract (`REQ-UI-061`, Ticket `ATT-1523`, Commit `191ba05d`)**:
  - Strictly prohibits non-destructive secondary actions (such as "Edit", "Navigation", "Show on Map") from appearing in the long-press context menu of any deletable card.
  - *Invariant Enforcement*: We MUST NOT add an "Edit" option to the `RouteItem` context menu, as doing so would violate `REQ-UI-061`.
* **Known Start Locations Management (`REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`)**:
  - Preserved single-tap on `KnownLocationCard` (`onClick = onEdit`) to directly launch `EditKnownLocationDialog`, because the management screen was streamlined into a single list and secondary map viewing was relocated to the central navigation map (`NavRoutes.MAP`).
* **Unified Edit Action Symbols (`REQ-UI-182`, Ticket `ATT-1536`)**:
  - Mandated canonical `Icons.Default.Edit` with `MaterialTheme.colorScheme.primary` tinting and minimum $48\times 48\text{dp}$ touch target for all interactive edit action buttons across the app.
* **Routes Lineage (`RouteItem.kt`, `RoutesScreen.kt`)**:
  - In earlier iterations, `RouteSummaryHeader` was given an inline click handler that defaulted to editing because a dedicated edit button had not yet been designed into the header layout.

### 2. Core Invariants Preserved
* **Inspection-First Invariant for Spatial / Analytical Entities**: All lists displaying spatial routes or recorded telemetry (Workouts, Routes, Clusters, Segments) MUST navigate to the primary map/inspection view when the header or card body is clicked.
* **Dedicated Edit Trigger Invariant**: Editing an analytical/spatial entity MUST be initiated via a dedicated edit button (`Icons.Default.Edit`), never by accidental card title tapping.
* **Configuration Entity Direct Edit Invariant**: Simple configuration entities with no inspection screen (Equipment, Sport Types, Locations) preserve direct-to-editor single-tap access (`REQ-UI-061`, `REQ-UI-165`, `REQ-UI-180`).
* **Material 3 Touch Target Compliance**: The edit button MUST provide a minimum touch target bounding box of at least $48\times 48\text{dp}$ (`Modifier.size(48.dp)`) per Android and Material 3 accessibility guidelines.

---

## 3. Forensic Scope & Affected Components

1. **`RouteSummaryHeader.kt` (`ui.routes`)**:
   - Add parameter `onEditClick: (() -> Unit)? = null`.
   - In the top row (sport icon + route identity), insert a dedicated `IconButton` for editing when `onEditClick != null`.
   - Apply `modifier = Modifier.size(48.dp)` to guarantee $\ge 48\text{dp} \times 48\text{dp}$ touch target.
   - Render `Icons.Default.Edit` (size `24.dp`) with `tint = MaterialTheme.colorScheme.primary` per `REQ-UI-182`.
   - Set accessibility content description to localized `stringResource(R.string.route_edit)` (verified present across all 9 locales).

2. **`RouteItem.kt` (`ui.routes`)**:
   - Update `RouteSummaryHeader` invocation:
     - Header `combinedClickable(onClick = ...)` now invokes `onMapClick(summary.id)` (route details), matching the card body, preview map, and elevation profile.
     - Pass `onEditClick = { onHeaderClick(summary.id) }` (or rename callback to `onEditClick`).
   - Retain delete-only context menu intact per `REQ-UI-061` (no edit action in context menu).

3. **`RouteList.kt`, `RouteTabbedScreen.kt`, & `RoutesScreen.kt` (`ui.routes`)**:
   - Align parameter naming and wiring: route header click routes to details (`selectedRouteIdForDetails = id`), while dedicated edit button routes to editor (`selectedRouteIdForEdit = id`).

4. **Global Architecture Audit Across All Project Lists**:
   - `WorkoutSummary.kt` / `WorkoutSummaryCompact.kt`: Verified compliant with `REQ-SET-071`.
   - `WorkoutClusterComponents.kt`: Verified compliant (clicks open heatmap inspection; editing coordinates/name segregated).
   - `SegmentItem.kt`: Verified compliant (clicks open segment map inspection).
   - `EquipmentTabsScreen.kt`: Verified configuration-first.
   - `SportTypesTabsScreen.kt`: Verified configuration-first.
   - `KnownLocationsScreen.kt`: Verified configuration-first per `REQ-UI-061`, `REQ-UI-180`.

---

## 4. Draft Text for `REQ-UI-191` & Traceability Cross-References

The following requirement specification will be introduced into `docs/requirements.md` in Stage 2:

```markdown
| **REQ-UI-191** | **Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance.** | The system SHALL standardize card heading and body click interaction semantics across all application list views according to domain entity classification, while providing dedicated, theme-standardized edit affordances (ATT-1541):<br>1. *Domain Interaction Taxonomy*:<br>• *Spatial / Analytical Entities (Workouts, Routes, Workout Clusters, Segments)*: Tapping the card header, title, description, metrics, or media preview SHALL navigate directly to the primary detailed inspection view (`TrackOnMapScreen` for Workouts per `REQ-SET-071`, Route Details for Routes, `WorkoutClusterHeatmapScreen` for Clusters, and `SegmentOnMapScreen` for Segments). Navigating to edit mode via title or card body tap is strictly prohibited.<br>• *Configuration Entities (Equipment, Sport Types, Favorite Locations)*: Configuration entities without a secondary map or telemetry inspection view SHALL preserve direct-to-editor single-tap navigation (`onEdit` / `EditKnownLocationDialog` per `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`, `EquipmentTabsScreen`, `SportTypesTabsScreen`).<br>2. *Dedicated Route Header Edit Button*:<br>• `RouteSummaryHeader.kt` SHALL incorporate a dedicated edit action button (`IconButton`) placed in the header identity row.<br>• The edit button SHALL display `Icons.Default.Edit`, tinted with `MaterialTheme.colorScheme.primary` per `REQ-UI-182`.<br>• The edit button SHALL strictly comply with Material 3 accessibility guidelines with a minimum touch target bounding box of at least $48\times 48\text{dp}$ (`Modifier.size(48.dp)`), housing a $24\times 24\text{dp}$ icon.<br>• The edit button SHALL use localized accessibility content description `@string/route_edit`.<br>• Tapping this edit button SHALL invoke `onEditClick` (launching `EditRouteScreen`).<br>3. *Route Item Heading Click Harmonization*:<br>• In `RouteItem.kt`, tapping `RouteSummaryHeader` SHALL invoke `onMapClick(summary.id)` to open route details, eliminating the previous split navigation where tapping the header edited the route while tapping the map inspected it.<br>• The long-press context menu of `RouteItem.kt` SHALL remain strictly compliant with `REQ-UI-061` (delete-only, plus the documented `REQ-EXT-004` Strava duplication lifecycle action; no secondary non-destructive actions).<br><br>**Requirement Archaeology & Chesterton's Fence Audit**:<br>1. *Original Requirement ID & Target*: `REQ-SET-071` (Workouts click-to-map standard), `REQ-UI-061` (Universal delete-only context menu), `REQ-UI-165` / `REQ-UI-166` / `REQ-UI-180` (Favorite locations configuration single-tap), and `REQ-UI-182` (Unified edit symbol and primary tint).<br>2. *Historical Origin & Commit Trace*: `ATT-850` (`2bf79cdf`, Workouts inspection standard), `ATT-1449` (`191ba05d`, Locations single-perspective), `ATT-1523` (`191ba05d`, Delete-only context menu), `ATT-1536` (Edit button styling).<br>3. *Root Reason for Inconsistency*: In `RouteItem.kt`, clicking `RouteSummaryHeader` originally defaulted to edit navigation because a dedicated edit button had not been designed into the header, creating cognitive friction against the established `REQ-SET-071` inspection-first pattern.<br>4. *Preservation of Core Invariants*: Full touch target compliance ($\ge 48\text{dp}$), universal delete menu integrity (`REQ-UI-061`), configuration entity single-tap preservation, and 9-language localization parity (`@string/route_edit`) are strictly maintained.<br><br>**Acceptance Criteria (Given-When-Then)**:<br>• *Given* an athlete viewing the routes list in `RouteTabbedScreen` or `RoutesScreen`,<br>• *When* the athlete taps the route header, map preview, or elevation profile,<br>• *Then* the system SHALL navigate to the route inspection/details view (`selectedRouteIdForDetails = id`).<br>• *Given* an athlete viewing a route card in `RouteItem`,<br>• *When* inspecting the header row,<br>• *Then* a dedicated edit button with `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary` and a minimum $48\times 48\text{dp}$ touch target SHALL be visible.<br>• *When* the athlete taps the dedicated edit button in the header,<br>• *Then* the system SHALL open `EditRouteScreen` (`selectedRouteIdForEdit = id`).<br>• *Given* an athlete interacting with configuration list items (Lieblingsorte, Equipment, Sport Types),<br>• *When* the card body is tapped,<br>• *Then* the configuration/edit dialog SHALL open directly, preserving the configuration entity pattern.<br><br>**Invariants**: Long-press context menus MUST NOT include non-destructive edit items (`REQ-UI-061`); route selection toggle (`onToggleSelection`) and Strava duplication (`onDuplicateAsLocal`) MUST remain functional; 9-language localization parity MUST be preserved. | Standardize list item heading click behavior to inspection-first across analytical/spatial lists while providing a dedicated, accessible Material 3 edit button in RouteSummaryHeader and preserving configuration-first entities. | `RouteSummaryHeader.kt`, `RouteItem.kt`, `RouteList.kt`, `RouteTabbedScreen.kt`, `RoutesScreen.kt` | `TST-UI-145` | Proposed |
```

---

## 5. Touch Target & Layout Ergonomics Specification

- **Material 3 Guideline Adherence**:
  - Android and Material 3 accessibility guidelines require all interactive elements to have a minimum touch target size of $48\times 48\text{dp}$.
  - The edit button in `RouteSummaryHeader.kt` is implemented using:
    ```kotlin
    IconButton(
        onClick = onEditClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(R.string.route_edit),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
    ```
- **Spatial Collision Prevention**:
  - The edit button is located at the trailing end of the top identity row (adjacent to the route name/source column).
  - The visibility switch (`Switch`) is pinned to the bottom-right of the header card (`Alignment.BottomEnd`).
  - The metrics row (`MetricItem`) incorporates `padding(end = 48.dp)` to prevent overlap with the switch.
  - Bounding box analysis confirms zero spatial conflict between the $48\times 48\text{dp}$ edit button at the top-right and the switch at the bottom-right.

---

## 6. Risk Analysis & Mitigation Strategy

| Risk | Likelihood | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| Users accustomed to tapping route title to edit route cannot find editor | Low | Low | Provide dedicated primary-tinted edit button in `RouteSummaryHeader` with $48\times 48\text{dp}$ touch target and `@string/route_edit` accessibility label. |
| Violation of universal delete-only contract (`REQ-UI-061`) | Zero | High | Confirmed: no edit item in context menu; context menu remains delete-only. |
| Touch target conflict between Route title, edit button, and visibility switch | Low | Medium | Edit button sits at top-right of identity row; visibility switch sits at bottom-right overlay. Zero bounding box overlap. |
| Accidental regressions in Route selection toggle or GPX duplicate | Low | High | Maintain isolated callbacks and verify with comprehensive Compose unit tests. |
