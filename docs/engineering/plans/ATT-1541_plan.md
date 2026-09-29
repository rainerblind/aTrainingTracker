# Stage 3: Implementation Plan - ATT-1541: Standardize List Item Heading Click Behavior Across All Application Lists

**Ticket**: [ATT-1541](https://atrainingtracker.atlassian.net/browse/ATT-1541)  
**Sub-task**: [ATT-1544](https://atrainingtracker.atlassian.net/browse/ATT-1544) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-191` (*Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance*)  
**Test Mapping**: `TST-UI-145` (*List Item Heading Click Standardization & Dedicated Route Edit Button Verification*)  
**Branch**: `feature/ATT-1541`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

Endurance athletes navigating through the application's card lists encounter diverging, inconsistent click interactions across different domains:
1. Spatial / analytical entities (Workouts, Clusters, Segments) navigate to map / detailed inspection views when clicking headers or content sections, with editing strictly isolated to dedicated edit buttons or context actions.
2. In Routes (`RouteItem.kt`), clicking `RouteSummaryHeader` originally opened the editor (`EditRouteScreen`), while clicking preview maps opened route details, and no dedicated edit button was present.
3. Configuration entities (Equipment, Sport Types, Favorite Locations / Lieblingsorte) have no secondary map or telemetry inspection view; their cards open configuration editors directly on single-tap (`REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`).

Standardizing heading click interaction semantics across analytical/spatial lists to "inspection-first", introducing a dedicated Material 3 edit button in `RouteSummaryHeader`, and preserving configuration-first entities establishes intuitive, predictable navigation.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-191` (*Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance*)
* **Test Mapping**: `TST-UI-145` (*List Item Heading Click Standardization & Dedicated Route Edit Button Verification*)
* **Cross-References**:
  - `REQ-SET-071` (Workouts click-to-map standard & dedicated edit button)
  - `REQ-UI-061` (Universal delete-only long-press context menu contract)
  - `REQ-UI-182` (Unified edit symbol `Icons.Default.Edit` & `primary` tinting)
  - `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180` (Favorite locations configuration single-tap preservation)

---

## 3. System Invariants & Preserved Behavior

1. **Inspection-First Invariant for Spatial / Analytical Entities**: All lists displaying spatial routes or recorded telemetry (Workouts, Routes, Clusters, Segments) MUST navigate to the primary map/inspection view when the header or card body is clicked.
2. **Dedicated Edit Affordance Invariant**: Editing an analytical/spatial entity MUST be initiated via a dedicated edit button (`Icons.Default.Edit`), never by accidental card title tapping.
3. **Universal Delete-Only Long-Press Contract (`REQ-UI-061`)**: The long-press context menu of `RouteItem.kt` MUST strictly remain delete-only (plus Strava duplicate per `REQ-EXT-004`). Secondary non-destructive actions ("Edit") are strictly prohibited.
4. **Configuration Entity Direct Edit Invariant**: Simple configuration entities with no inspection screen (Equipment, Sport Types, Locations) preserve direct-to-editor single-tap access (`REQ-UI-061`, `REQ-UI-180`).
5. **Material 3 Touch Target Compliance**: The edit button MUST provide a minimum touch target bounding box of at least $48\times 48\text{dp}$ (`Modifier.size(48.dp)`) per Android and Material 3 accessibility guidelines.
6. **Subtask Self-Sufficiency & Human Gate Invariance**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RouteSummaryHeader.kt` (`ui.routes`)
- Add parameter `onEditClick: (() -> Unit)? = null`.
- In the top identity Row (`Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)`), after the route title/source column (`Modifier.weight(1f)`), insert:
  ```kotlin
  if (onEditClick != null) {
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
  }
  ```
- This ensures a $48\times 48\text{dp}$ touch target, `Icons.Default.Edit`, `primary` color tint per `REQ-UI-182`, and localized content description `@string/route_edit`.

### Component 2: `RouteItem.kt` (`ui.routes`)
- Harmonize click delegation in `RouteSummaryHeader`:
  ```kotlin
  RouteSummaryHeader(
      summary = summary,
      onToggleSelection = { onToggleSelection(summary.id, it) },
      onEditClick = { onHeaderClick(summary.id) },
      switchScale = 0.6f,
      modifier = Modifier
          .fillMaxWidth()
          .combinedClickable(
              onClick = { onMapClick(summary.id) },
              onLongClick = { showContextMenu = true }
          )
  )
  ```
- Tapping the header surface now invokes `onMapClick(summary.id)` (route inspection / details), matching the card body, preview map, and elevation profile.
- Tapping the dedicated edit button invokes `onEditClick` -> `onHeaderClick(summary.id)` (which triggers `selectedRouteIdForEdit = id` in `RoutesScreen.kt`).
- The long-press context menu is verified to remain strictly delete-only (plus Strava duplication).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `RouteSummaryHeader.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt`
* Changes:
  - Add `onEditClick: (() -> Unit)? = null` parameter.
  - Insert Material 3 `IconButton` with $48\text{dp} \times 48\text{dp}$ touch target, `Icons.Default.Edit`, `MaterialTheme.colorScheme.primary` tint, and `@string/route_edit`.
  - Update preview composables.

### Step 2: Update `RouteItem.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt`
* Changes:
  - Re-wire `RouteSummaryHeader`'s `onClick` to `onMapClick(summary.id)`.
  - Pass `onEditClick = { onHeaderClick(summary.id) }`.
  - Verify context menu contains zero non-destructive edit items per `REQ-UI-061`.

### Step 3: Implement Unit Test Suite `RouteItemClickStandardizationTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemClickStandardizationTest.kt`
* Changes:
  - Test 1: Header click triggers `onMapClick` (inspection view), not edit.
  - Test 2: Preview map click triggers `onMapClick`.
  - Test 3: Elevation profile click triggers `onMapClick`.
  - Test 4: Card body click triggers `onMapClick`.
  - Test 5: Dedicated edit button triggers `onEditClick` (edit route screen).
  - Test 6: Dedicated edit button touch target meets $\ge 48\text{dp} \times 48\text{dp}$.
  - Test 7: Context menu contains strictly delete (and Strava duplicate if Strava), NO edit option per `REQ-UI-061`.
* Targeted Execution Command:
  `./gradlew testDebugUnitTest --tests "*RouteItemClickStandardizationTest*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by clean-room test suite `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Isolated branch `feature/ATT-1541` allows full git reset or discard without impacting `sprint/2026-40.4`.
