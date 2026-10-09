# Stage 1 Analysis: ATT-2763 - Make climbs, segments, and waypoints toggleable on route map with layers menu while anchoring route line

**Ticket**: [ATT-2763](https://atrainingtracker.atlassian.net/browse/ATT-2763)  
**Sub-task**: [ATT-2832](https://atrainingtracker.atlassian.net/browse/ATT-2832) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Backlog`  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2763`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

With the incremental additions of:
1. Full-spectrum climb category polyline highlighting (`climbs(climbs)`, ATT-2509, ATT-2748),
2. Matched Strava segments (`segments(segmentPaths)`, ATT-2583, ATT-2774), and
3. Waypoint / POI markers (`RouteWaypointsLayer`, REQ-MAP-026),

complex routes can accumulate dense, overlapping visual elements in `RouteOnMapScreen.kt`. On winding courses or crowded alpine routes, overlapping climb polylines, Strava segments, and waypoint pins can visually obscure critical turns, segment boundaries, and elevation transitions.

Athletes require the ability to declutter the map view by selectively showing or hiding specific overlay layers (Climbs, Segments, Waypoints) via a dedicated map Layers menu, as well as toggling individual climbs or segments within their breakdown lists.

### Crucial Architectural Invariant
Within Route Details, the selected route line (`TTColor.RouteSelected`, Royal Blue `#1565C0`) is the central subject of the screen and **must always remain visible**. It cannot be toggled off or hidden under any circumstances; layer controls strictly govern overlay entities (Climbs, Segments, Waypoints).

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Current Architectural State
1. **Unconditional Overlay Rendering in `RouteOnMapScreen.kt`**:
   - `RouteOnMapScreen.kt` directly invokes `climbs(climbs)` with the full list of climbs.
   - `RouteOnMapScreen.kt` directly invokes `segments(segmentPaths)` with the full list of matched segments.
   - `RouteOnMapScreen.kt` invokes `routes(listOf(route))` where `route.waypoints` are unconditionally rendered by `MappablePathLayer` via `RouteWaypointsLayer`.
   - `MapDetailLayout` provides an `overlay: @Composable BoxScope.() -> Unit = {}` slot, but `RouteOnMapScreen` currently does not supply any content to it.
2. **Design Reference in `TrackOnMapScreen.kt`**:
   - In `TrackOnMapScreen.kt` (lines 219–280), `overlay` is utilized to render a floating circular `Surface` containing `Icons.Default.Layers` positioned at `Alignment.TopEnd` with `padding(top = 76.dp, end = 16.dp)` (neatly below the 44.dp Share button positioned at `padding(16.dp)`).
   - Tapping the icon toggles `showTrackMenu`, displaying a Material 3 `DropdownMenu` with checkboxes, color legend chips, and localized item titles.
3. **Breakdown Lists (`RouteClimbsBreakdownSection.kt` and `RouteSegmentsBreakdownSection.kt`)**:
   - In `RouteClimbsBreakdownSection`, each card occupies the full width and handles `onClick` to open `ClimbDetailSheet` (ATT-2511). There is currently no per-item visibility toggle.
   - In `RouteSegmentsBreakdownSection`, each card occupies the full width and handles `onClick` to open `SegmentDetailSheet` (ATT-2774). There is currently no per-item visibility toggle.
   - When an athlete wants to isolate a single climb or compare two specific climbs, all other climbs remain rendered on the map, causing visual confusion.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Permanent Route Invariant**: Ensure the selected route polyline (`TTColor.RouteSelected`) is permanently anchored and rendered unconditionally in `mapContent`. It cannot be toggled off.
  2. **Route Map Layers Menu (`Icons.Default.Layers`)**:
     - Provide a floating circular Layers button in the `overlay` slot of `MapDetailLayout` at `Alignment.TopEnd` with `padding(top = 76.dp, end = 16.dp)`, matching `TrackOnMapScreen.kt`.
     - Present a dropdown menu with checkboxes, color legend swatches, and localized labels for:
       - **Climbs** (legend: climb category color palette swatch / chip, disabled if route has 0 climbs).
       - **Segments** (legend: `TTColor.StravaOrange` `#FC4C02` swatch, disabled if route has 0 matched segments).
       - **Waypoints** (legend: POI generic icon or category disc swatch, disabled if route has 0 waypoints).
  3. **Item-Level Visibility Toggles in Breakdown Lists**:
     - Add an individual visibility toggle (eye icon button: `Icons.Default.Visibility` when visible, `Icons.Default.VisibilityOff` when hidden) to each card in `RouteClimbsBreakdownSection`.
     - Add an individual visibility toggle (eye icon button) to each card in `RouteSegmentsBreakdownSection`.
     - Tapping the eye icon toggles the item's hidden state without triggering the card's `onClick` (which opens the detail bottom sheet).
  4. **Dynamic Map Filtering**:
     - Filter climbs passed to `climbs(...)`: only include climbs where `RouteOverlayLayer.CLIMBS` is enabled and `climb.id !in hiddenClimbIds`.
     - Filter segments passed to `segments(...)`: only include segments where `RouteOverlayLayer.SEGMENTS` is enabled and `matched.segment.summary.stravaId !in hiddenSegmentIds`.
     - Filter waypoints rendered in `routes(...)`: if `RouteOverlayLayer.WAYPOINTS` is disabled, pass `route.copy(waypoints = emptyList())` to suppress waypoint markers while preserving the route polyline.
  5. **Navigation Markers Preserved**:
     - Start (`control_start`) and End (`control_stop`) navigation markers remain strictly visible and are never toggled off.
  6. **Localization Parity**:
     - Provide string resources for layer titles (`route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show`) across all 9 supported languages.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying `TrackOnMapScreen.kt` or workout telemetry track layers.
  - Modifying live tracking screens (`TrackingScreen`, `ClimbProDialog`, etc.).
  - Altering SQLite database schemas, DAO queries, or route persistence entities.
  - Adding route line toggles (violates the core invariant).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement: `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring*).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-298` (ATT-2509) introduced climb polyline highlights.
  - `REQ-UI-302` (ATT-2583) introduced matched segment overlays.
  - `REQ-UI-307` (ATT-2748) expanded climb polylines to UC climbs and eliminated climb start pins.
  - `REQ-MAP-026` introduced waypoint badge markers along route courses.
* **Root Reason for Existing Formulation**: Previously, each overlay entity was introduced independently without global layer toggling because route analysis was in its infancy. Now that all three entity types (climbs, segments, waypoints) coexist, simultaneous rendering on dense routes produces visual crowding.
* **Preservation of Core Invariants**:
  - The base route line remains 100% visible at all times.
  - Start and End route pins remain 100% visible at all times.
  - Card tapping continues to open `ClimbDetailSheet` and `SegmentDetailSheet` seamlessly.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Data Modeling
Declare `RouteOverlayLayer` enum:
```kotlin
enum class RouteOverlayLayer {
    CLIMBS,
    SEGMENTS,
    WAYPOINTS
}
```

### 5.2 State Management in `RouteOnMapScreen.kt`
```kotlin
var visibleOverlayLayers by rememberSaveable {
    mutableStateOf(setOf(RouteOverlayLayer.CLIMBS, RouteOverlayLayer.SEGMENTS, RouteOverlayLayer.WAYPOINTS))
}
var hiddenClimbIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
var hiddenSegmentIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
var showLayerMenu by remember { mutableStateOf(false) }
```

### 5.3 Map Overlay Slot (`MapDetailLayout.overlay`)
```kotlin
overlay = {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 76.dp, end = 16.dp)
    ) {
        Surface(
            onClick = { showLayerMenu = true },
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay),
            shadowElevation = 6.dp,
            tonalElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = stringResource(R.string.route_layers),
                    modifier = Modifier.size(22.dp),
                    tint = if (visibleOverlayLayers.size < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        DropdownMenu(
            expanded = showLayerMenu,
            onDismissRequest = { showLayerMenu = false }
        ) {
            // Climbs item, Segments item, Waypoints item
        }
    }
}
```

### 5.4 Breakdown List Integration
Pass `hiddenClimbIds` and `onToggleClimbVisibility: (Long) -> Unit` to `RouteClimbsBreakdownSection`.
Pass `hiddenSegmentIds` and `onToggleSegmentVisibility: (Long) -> Unit` to `RouteSegmentsBreakdownSection`.
Each card renders an `IconButton` at the top right with `Icons.Default.Visibility` (tinted `onSurfaceVariant`) or `Icons.Default.VisibilityOff` (tinted `outline`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Selected route polyline (`TTColor.RouteSelected`) is never hidden or toggled off.
  2. Route Start and End navigation markers remain visible at all times.
  3. Clicking on climb/segment cards continues to open `ClimbDetailSheet` and `SegmentDetailSheet`.
  4. 100% pass rate maintained across all 2,172 existing clean-room unit tests.
* **Risk Rating**: **LOW**.
  - All changes are strictly UI presentation and state management within `RouteOnMapScreen` and its sub-composables.
  - Zero database or asynchronous background processing changes.
