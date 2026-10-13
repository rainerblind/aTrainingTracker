# Stage 3: Implementation Plan - ATT-2763: Make Climbs, Segments, and Waypoints Toggleable on Route Map with Layers Menu while Anchoring Route Line

**Ticket**: [ATT-2763](https://atrainingtracker.atlassian.net/browse/ATT-2763)  
**Sub-task**: [ATT-2834](https://atrainingtracker.atlassian.net/browse/ATT-2834) (`[Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `Backlog`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring*)  
**Test Mapping**: `TST-UI-268` (*Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification*)  
**Branch**: `improvement/ATT-2763`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  
**Status**: Ready for Gate 3 Review  

---

## 1. Problem Description & Background

With the progressive enrichment of route inspection features in `RouteOnMapScreen.kt`—including climb category polylines across all 6 UCI categories (ATT-2509, ATT-2748), matched Strava segments (ATT-2583), and waypoint POI markers (REQ-MAP-026)—complex routes can become visually overloaded. When multiple climb spans, overlapping segments, and waypoints coincide along a route corridor, athletes face difficulty discerning specific course details.

Athletes require the ability to declutter the route map by dynamically toggling overlay layers via a standardized Layers menu (achieving 1:1 visual and ergonomic parity with `TrackOnMapScreen.kt`) and per-item toggles in the breakdown cards.

**Crucial Invariant**:
The primary selected route polyline (`TTColor.RouteSelected`, Royal Blue `#1565C0`) and route Start/End navigation markers (`control_start`, `control_stop`) are the core subject of the screen and must remain permanently anchored and visible at all times. They cannot be toggled off or hidden under any circumstances.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-308` (*Route Map Overlay Layer Visibility Controls, Layers Menu, and Item-Level Breakdown Toggles with Permanent Route Line Anchoring*)
* **Test Mapping**: `TST-UI-268` (*Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification*):
  - `TST-UI-268.1`: Route Map Layers Menu & Overlay Filtering Contract Tests (`RouteOnMapScreenLayersContractTest.kt`)
  - `TST-UI-268.2`: Breakdown Section Visibility Toggle Contract Tests (`RouteBreakdownVisibilityToggleContractTest.kt`)
  - `TST-UI-268.3`: 9-Language Localization Parity Audit (`RouteLayersLocalizationTest.kt`)
  - `TST-UI-268.4`: Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Permanent Selected Route Anchoring (`REQ-UI-308.1`)**: The selected route polyline (`TTColor.RouteSelected`) is rendered unconditionally in `mapContent`. It is excluded from the Layers menu and can never be hidden.
2. **Start & End Navigation Marker Invariance (`REQ-UI-308.3`)**: Start (`control_start`) and End (`control_stop`) markers remain rendered at the first and last route path points regardless of layer toggles.
3. **Card Navigation Invariance**: Tapping a climb card continues to open `ClimbDetailSheet`, and tapping a segment card continues to open `SegmentDetailSheet`. The visibility eye icon toggle consumes its own touch event without triggering sheet presentation.
4. **Localization Parity**: 100% 9-language localization parity across all supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing keys or broken format specifiers.
5. **Human Gate Invariance (Rule 2)**: Subtask `ATT-2834` transitions to `Erledigt` upon passing Gate 3 audit via `freigabe`. Parent ticket `ATT-2763` terminal state is `Final Review (Human)`.
6. **Zero Regression**: 100% clean-room test suite pass rate across all unit test classes.

---

## 4. UI Consistency & Living Design Guidelines Audit (Rule 23)

In compliance with `docs/design_guidelines.md` (Section 5 *Visual Consistency Baseline*):
- **Closest Reference Component**: `TrackOnMapScreen.kt` (lines 209–280).
- **Control Button Design**:
  - Floating circular Surface (`size = 44.dp`, `shape = CircleShape`, `color = MaterialTheme.colorScheme.surface.copy(alpha = TTAlpha.Overlay)`, `shadowElevation = 6.dp`, `tonalElevation = 2.dp`).
  - Positioned in `MapDetailLayout` `overlay` slot at `Alignment.TopEnd` with `padding(top = 76.dp, end = 16.dp)`, precisely beneath the top-right Share button.
  - Icon: `Icons.Default.Layers` (`size = 22.dp`).
  - Tint: `MaterialTheme.colorScheme.primary` if any layer is toggled off, or `MaterialTheme.colorScheme.onSurface` (or disabled alpha) when all layers are default.
- **Dropdown Menu Design**:
  - `DropdownMenu` styled with `DropdownMenuItem`, `Checkbox`, color swatch chip (`size = 12.dp`, `RoundedCornerShape(2.dp)`), and `MaterialTheme.typography.bodyMedium`.
  - Swatch colors: Climb category palette for Climbs; `TTColor.StravaOrange` (`Color(0xFFFC4C02)`) for Segments; Category/POI icon or disc for Waypoints.
  - Menu entries are enabled only if the corresponding entities exist on the route (`climbs.isNotEmpty()`, `matchedSegments.isNotEmpty()`, `route?.waypoints?.isNotEmpty() == true`).
- **Breakdown Card Visibility Toggles**:
  - Standard `IconButton` in card header with `Icons.Default.Visibility` (tint `onSurfaceVariant`) when visible, `Icons.Default.VisibilityOff` (tint `primary` / muted) when hidden.
  - Content description localized via `R.string.route_item_hide` and `R.string.route_item_show`.

---

## 5. Architectural Design (ASPICE SWE.2)

### 5.1 RouteOverlayLayer Enum
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOverlayLayer.kt`:
```kotlin
package com.atrainingtracker.trainingtracker.ui.routes

enum class RouteOverlayLayer {
    CLIMBS,
    SEGMENTS,
    WAYPOINTS
}
```

### 5.2 State Management in RouteOnMapScreen.kt
```kotlin
var enabledOverlayLayers by rememberSaveable {
    mutableStateOf(RouteOverlayLayer.entries.toSet())
}
var hiddenClimbIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
var hiddenSegmentIds by rememberSaveable { mutableStateOf(emptySet<Long>()) }
var showLayersMenu by remember { mutableStateOf(false) }
```

### 5.3 Filtering Logic in mapContent
```kotlin
if (route != null) {
    // 1. Primary route polyline: permanently anchored
    // 2. Waypoints: conditionally rendered
    val routeToRender = if (RouteOverlayLayer.WAYPOINTS in enabledOverlayLayers) {
        route
    } else {
        route.copy(waypoints = emptyList())
    }
    routes(listOf(routeToRender))

    // 3. Climbs: conditionally filtered
    if (RouteOverlayLayer.CLIMBS in enabledOverlayLayers) {
        val visibleClimbs = climbs.filterIndexed { index, climb ->
            val key = if (climb.id != 0L) climb.id else (index + 1).toLong()
            key !in hiddenClimbIds
        }
        climbs(visibleClimbs)
    }

    // 4. Segments: conditionally filtered
    if (RouteOverlayLayer.SEGMENTS in enabledOverlayLayers && matchedSegments.isNotEmpty()) {
        val visibleSegments = matchedSegments.filter { matched ->
            matched.segment.summary.stravaId !in hiddenSegmentIds
        }
        // render segments...
    }

    // 5. Start and End navigation markers: permanently anchored
    // render Start and End markers...
}
```

### 5.4 Card Item-Level Toggles
- `RouteClimbsBreakdownSection(climbs, hiddenClimbIds, onToggleClimbVisibility, onClimbClick)`
- `RouteSegmentsBreakdownSection(segments, hiddenSegmentIds, onToggleSegmentVisibility, onSegmentClick)`

---

## 6. Atomic Implementation Steps

### Step 1: Define `RouteOverlayLayer` & String Resources
- Create `RouteOverlayLayer.kt`.
- Add string keys (`route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show`) to all 9 locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### Step 2: Implement Breakdown Section Visibility Toggles
- In `RouteClimbsBreakdownSection.kt` (or within `RouteOnMapScreen.kt`):
  - Add parameters `hiddenClimbIds: Set<Long> = emptySet()` and `onToggleClimbVisibility: ((Long) -> Unit)? = null`.
  - Add visibility `IconButton` inside the card top row.
- In `RouteSegmentsBreakdownSection.kt`:
  - Add parameters `hiddenSegmentIds: Set<Long> = emptySet()` and `onToggleSegmentVisibility: ((Long) -> Unit)? = null`.
  - Add visibility `IconButton` inside the card top row.

### Step 3: Implement Layers Menu & Map Filtering in `RouteOnMapScreen.kt`
- Add `overlay` slot implementation in `RouteOnMapScreen.kt` featuring floating circular Layers button at `Alignment.TopEnd` with `padding(top = 76.dp, end = 16.dp)`.
- Implement `DropdownMenu` with checkboxes and swatches for Climbs, Segments, and Waypoints.
- Wire layer visibility filtering and individual hidden item filtering in `mapContent`.
- Maintain permanent rendering of the selected route polyline and Start/End markers.

### Step 4: Author Unit, Contract & Localization Tests
- Create `RouteLayersLocalizationTest.kt` verifying all 9 locales.
- Create `RouteOnMapScreenLayersContractTest.kt` verifying layer enum, initial state, and filtering behavior.
- Create `RouteBreakdownVisibilityToggleContractTest.kt` verifying breakdown card visibility callbacks.

### Step 5: Full Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 7. Verification & Regression Strategy

1. **Targeted Tests**:
   - `com.atrainingtracker.trainingtracker.ui.routes.RouteLayersLocalizationTest`
   - `com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenLayersContractTest`
   - `com.atrainingtracker.trainingtracker.ui.routes.RouteBreakdownVisibilityToggleContractTest`
2. **Regression Check**:
   - Existing climb and route contract tests (`RouteSegmentsLocalizationTest`, `RouteSegmentsBreakdownContractTest`, `RouteColorPaletteContractTest`, `ClimbPolylineContractTest`).
   - Clean-room build `./gradlew clean testDebugUnitTest`.

---

## 8. Gate 3 Readiness Checklist

- [x] Traceability matrix links `REQ-UI-308` to `TST-UI-268`.
- [x] Permanent route line and Start/End markers preserved as absolute invariants.
- [x] UI design verified against `TrackOnMapScreen.kt` reference implementation (Rule 23).
- [x] Localization planned across all 9 supported locales.
- [x] Atomic implementation steps defined with clear verification criteria.
