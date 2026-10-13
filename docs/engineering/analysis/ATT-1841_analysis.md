# Stage 1 Analysis: ATT-1841 - Prominent High-Contrast Rendering for Actively Navigated Routes Preserving Multi-Layer X-Ray Segment Synergy

**Ticket**: [ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841)  
**Sub-task**: [ATT-2263](https://rainerblind.atlassian.net/browse/ATT-2263) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1841`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

During outdoor training sessions (road cycling, mountain biking, trail running), athletes mount their smartphones on bike handlebars or glance at wrist mounts under challenging visual conditions: direct glare from midday sunlight, rapid vibrations across cobblestones or rough trails, dynamic map rotations, and complex background tile textures (green forest terrain, dark satellite imagery, or high-contrast AMOLED dark mode).

Currently, all selected routes displayed on the live tracking map (`ATrainingTrackerMap.kt`, `MapLayers.kt`) share a uniform, modest styling:
- Line width: Fixed at `ROUTE_WIDTH = 10f` (via `MapVisualization.ROUTE_WIDTH`).
- Line color: Muted `ForestGreen #228B22` (`TTColor.RouteSelected`).
- Z-Index: Base polyline at `ROUTE_BASE_Z_INDEX = 20.0f` and dashed overlay at `ROUTE_OVERLAY_Z_INDEX = 40.0f`.

This leads to several critical usability deficiencies:
1. **Low Glanceability at Speed**: Under bright sunlight and against green park/forest map tiles, the subtle `ForestGreen` 10f line blends into the terrain, forcing athletes to squint and divert their attention from the road.
2. **Absence of Active Navigation Hierarchy**: Multiple routes can be checked for visibility (`isSelected = true`). However, there is zero visual differentiation between passive background routes and the single **actively followed / navigated route** that the athlete is currently riding.
3. **Directional Ambiguity on Complex Tracks**: When a route features intersecting loops, figure-eight shapes, or out-and-back spur sections along the same road, a plain polyline gives no indication of forward travel direction, resulting in navigation disorientation.
4. **Preservation of Proven Multi-Layer X-Ray Polyline Synergy**: The application has an established, battle-tested 3-tier X-Ray polyline architecture (`XRayPolyline` in `MapLayers.kt`):
   - Tier 1: Solid Base Polyline (`ROUTE_BASE_Z_INDEX = 20.0f`)
   - Tier 2: Strava Live Segments Layer (`SEGMENT_Z_INDEX = 30.0f`, Orange `#FC4C02`)
   - Tier 3: Dashed Pattern Overlay (`ROUTE_OVERLAY_Z_INDEX = 40.0f`)
   Through the gaps in Tier 3's dashed overlay, overlapping Strava segments remain visible beneath without being completely hidden by the route. Any enhancement to the active route's prominence must strictly preserve this interleaving synergy.

---

## 2. Forensic Architectural & Gap Analysis

### 2.1 Existing Route Modeling (`MapModels.kt` & `MapRoute`)
In `MapModels.kt`:
```kotlin
@Immutable
data class MapRoute(
    override val id: Long,
    val name: String,
    val isSelected: Boolean,
    override val bSportType: BSportType,
    override val path: List<PathPoint>,
    ...
) : MappablePath {
    override val latLngs: List<LatLng> by lazy { path.map { it.latLng } }
    override val color: Color get() = if (isSelected) TTColor.RouteSelected else TTColor.RouteUnselected
    override val width: Float get() = if (isSelected) MapVisualization.ROUTE_WIDTH else MapVisualization.ROUTE_UNSELECTED_WIDTH
    override val zIndex: Float get() = if (isSelected) MapVisualization.ROUTE_BASE_Z_INDEX else MapVisualization.ROUTE_UNSELECTED_Z_INDEX
    override val overlayZIndex: Float get() = MapVisualization.ROUTE_OVERLAY_Z_INDEX
    override val pattern: List<PatternItem>
        get() = listOf(Dash(MapVisualization.ROUTE_DASH_LENGTH), Gap(MapVisualization.ROUTE_GAP_LENGTH))
}
```
Currently, `MapRoute` only distinguishes `isSelected` (boolean). It has no awareness of active navigation (`isActiveNavigation`).

### 2.2 Active Navigation State Tracking (`RoutesRepository.kt`)
In `RoutesRepository.kt`:
- `allRoutes: StateFlow<List<RouteWithPath>>` emits all saved routes from SQLite.
- `toggleRouteSelection(routeId, isSelected)` persists checkbox state to SQLite `routes` table (`RouteContract.COLUMN_IS_SELECTED`).
- **Gap**: There is currently no centralized in-memory or reactive state tracking which route is the active navigation target. Introducing `activeNavigatedRouteId: StateFlow<Long?>` with `setActiveNavigatedRoute(routeId: Long?)` establishes a single source of truth for active route following, serving this ticket as well as upcoming features (Quick Route Selector ATT-1835, Turn-by-Turn Cues ATT-1450, and ClimbPro ATT-1281).

### 2.3 Visual Prominence Parameters for Active Navigation
To ensure immediate optical recognition without overwhelming map readability:
1. **High-Visibility Color**:
   - `TTColor.RouteActiveNavigation = Color(0xFF00E676)` (Electric Emerald / Vibrant Neon Green).
   - Achieves maximum contrast against dark mode `#121212`, satellite imagery, and standard light terrain.
   - Distinct from Strava Orange (`#FC4C02`), Live Track Cyan (`#00E5FF`) / Blue, and passive ForestGreen (`#228B22`).
2. **Line Width**:
   - `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH = 16f` (vs. passive `ROUTE_WIDTH = 10f` and unselected `6f`).
3. **Z-Index Layering Preserving X-Ray Synergy**:
   - Tier 1 Active Base: `ROUTE_ACTIVE_BASE_Z_INDEX = 25.0f`.
     - Strictly greater than passive route base (`20.0f`).
     - Strictly less than `SEGMENT_Z_INDEX` (`30.0f`).
   - Tier 2 Segments: `SEGMENT_Z_INDEX = 30.0f`.
   - Tier 3 Passive Overlay: `ROUTE_OVERLAY_Z_INDEX = 40.0f`.
   - Tier 4 Active Overlay: `ROUTE_ACTIVE_OVERLAY_Z_INDEX = 45.0f`.
   Because Tier 1 ($25\text{f}$) is below segments ($30\text{f}$) and Tier 4 ($45\text{f}$) is dashed, segments running along the active route remain clearly visible through the dash gaps!

### 2.4 Directional Chevrons (Forward Travel Arrows)
In `MapLayers.kt`, `SegmentDecorations` already implements directional arrow rendering using `directionIcons: Triple<BitmapDescriptor?, BitmapDescriptor?, BitmapDescriptor?>` (`R.drawable.ic_navigation_arrow`):
```kotlin
segment.path.windowed(2, 20).forEach { pair ->
    val midPos = LatLng(...)
    Marker(
        state = remember(midPos) { MarkerState(position = midPos) },
        icon = arrowIcon,
        rotation = calculateBearing(pair[0].latLng, pair[1].latLng).toFloat(),
        flat = true,
        anchor = Offset(0.5f, 0.5f),
        alpha = alpha,
        zIndex = style.segmentZIndex
    )
}
```
We can generalize this into `RouteDecorations` for `MapRoute` when `path.isActiveNavigation == true`:
- Chevrons render when `currentZoom > 13f`.
- Windowing/stepping adapts gracefully to route length and zoom level to avoid marker overcrowding.
- Clearly displays forward travel bearing along loops and intersections.

---

## 3. User Scope Grounding (ATT-1250)

### 3.1 In-Scope Objectives
1. **`MapRoute` Domain Model Enhancement**:
   - Add `isActiveNavigation: Boolean = false` property.
   - Update `color` resolution: `isActiveNavigation -> TTColor.RouteActiveNavigation`, `isSelected -> TTColor.RouteSelected`, else `TTColor.RouteUnselected`.
   - Update `width` resolution: `isActiveNavigation -> 16f`, `isSelected -> 10f`, else `6f`.
   - Update `zIndex` resolution: `isActiveNavigation -> 25f`, `isSelected -> 20f`, else `5f`.
   - Update `overlayZIndex` resolution: `isActiveNavigation -> 45f`, `isSelected -> 40f`.
2. **`RoutesRepository` Active Navigation State**:
   - Expose `activeNavigatedRouteId: StateFlow<Long?>` and `setActiveNavigatedRoute(routeId: Long?)`.
   - Support `RouteWithPath.toMapRoute(isActiveNavigation: Boolean)`.
3. **Multi-Layer X-Ray Synergy Validation**:
   - Verify that overlapping Strava segments render visibly over the solid base ($Z=25\text{f}$) and through the dashed overlay ($Z=45\text{f}$).
4. **Directional Chevrons (`RouteDecorations` in `MapLayers.kt`)**:
   - Render periodic directional arrows along actively navigated routes when zoomed in (`currentZoom > 13f`), utilizing cached `directionIcons`.
5. **View Integration**:
   - In `TrackingViewModel`: map `allRoutes` with `isActiveNavigation = (it.summary.id == activeNavigatedRouteId)`.
   - In `RouteOnMapScreen` / `RoutesScreen`: when inspecting a specific route in detail, mark it as active navigation preview (`isActiveNavigation = true`).
6. **Comprehensive Unit & Visual Tests**:
   - Author unit tests for `MapRoute` styling, color, width, and Z-index contracts.
   - Test repository active route state propagation.
   - Verify directional chevron geometry and rotation calculations.

### 3.2 Out-of-Scope Objectives
- Voice or audio turn-by-turn prompts (scope of ATT-1450).
- ClimbPro cockpit bottom sheet (scope of ATT-1281).
- Quick route picker bottom sheet UI (scope of ATT-1835).
- Modifying Strava segment detection logic or live tracking GPS sampling.

---

## 4. Chesterton's Fence Requirement Archaeology (REQ-PRO-022)

1. **Original Requirement ID & Target**:
   - Net-new requirement: **`REQ-MAP-023`** (*Prominent High-Contrast Rendering for Actively Navigated Routes Preserving Multi-Layer X-Ray Segment Synergy*).
   - Complements:
     - `REQ-MAP-005` (*Strava Segments and Routes as map overlays*)
     - `REQ-MAP-006` (*Declarative Map DSL with modular layers*)
     - `REQ-MAP-009` (*Reactive Map Layer Redraw*)
     - `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking & Cockpit*)
2. **Historical Origin & Commit Trace**:
   - Ticket `ATT-1841`, sprint `2026-40.14`, target release `V4.9.39`.
   - Epic `ATT-66` (*[Epic] Improve Routes*).
3. **Root Reason for Existing Formulation**:
   - The initial route overlay implementation (`REQ-MAP-005`, commit `1c89f54a`) used uniform 10f green polylines because early versions only displayed a single selected route. As multi-route selection, route clustering, and Strava segment overlays evolved, the 10f green line became insufficiently differentiated from passive background routes and struggled in high-glare outdoor environments.
4. **Preservation of Core Invariants**:
   - Passive selected routes strictly retain `ROUTE_WIDTH = 10f` and `TTColor.RouteSelected` (`ForestGreen`).
   - Unselected background routes strictly retain `ROUTE_UNSELECTED_WIDTH = 6f` and disabled alpha.
   - Strava segments strictly retain `SEGMENT_Z_INDEX = 30.0f` and `TTColor.StravaOrange`.
   - The 3-tier X-Ray polyline sandwich (`baseZIndex < SEGMENT_Z_INDEX < overlayZIndex`) remains mathematically preserved.
   - 100% clean-room unit test pass rate.

---

## 5. Proposed Requirement Formulation (`REQ-MAP-023`)

```markdown
| **REQ-MAP-023** | **Prominent High-Contrast Rendering for Actively Navigated Routes Preserving Multi-Layer X-Ray Segment Synergy.** | The system SHALL visually differentiate actively followed/navigated routes from passive background routes on the tracking and detail maps, rendering active routes with prominent thickness, vibrant high-contrast color, and directional chevrons while strictly preserving the multi-layer X-Ray polyline synergy with Strava Live Segments (ATT-1841):<br>1. *Active Navigation State in MapRoute & RoutesRepository*:<br>• `MapRoute` SHALL include property `isActiveNavigation: Boolean = false`.<br>• `RoutesRepository` SHALL expose `val activeNavigatedRouteId: StateFlow<Long?>` and provide `fun setActiveNavigatedRoute(routeId: Long?)`, establishing a reactive single source of truth for active route navigation.<br>2. *High-Contrast Visual Hierarchy & Prominence Tokens*:<br>• *Color*: When `isActiveNavigation == true`, `MapRoute.color` SHALL resolve to `TTColor.RouteActiveNavigation` (Electric Emerald `Color(0xFF00E676)`). When `isSelected == true && !isActiveNavigation`, `color` SHALL resolve to `TTColor.RouteSelected` (`Color(0xFF228B22)` ForestGreen). When unselected, it SHALL resolve to `TTColor.RouteUnselected`.<br>• *Width*: When `isActiveNavigation == true`, `MapRoute.width` SHALL resolve to `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH` (16f). When `isSelected == true`, it SHALL resolve to `MapVisualization.ROUTE_WIDTH` (10f). When unselected, it SHALL resolve to `MapVisualization.ROUTE_UNSELECTED_WIDTH` (6f).<br>3. *Preservation of Multi-Layer X-Ray Polyline Synergy (`XRayPolyline`)*:<br>• The Z-index hierarchy SHALL strictly enforce:<br>  - Active Route Solid Base: `MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX` (25.0f).<br>  - Passive Route Solid Base: `MapVisualization.ROUTE_BASE_Z_INDEX` (20.0f).<br>  - Strava Segments: `MapVisualization.SEGMENT_Z_INDEX` (30.0f).<br>  - Passive Route Dashed Overlay: `MapVisualization.ROUTE_OVERLAY_Z_INDEX` (40.0f).<br>  - Active Route Dashed Overlay: `MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX` (45.0f).<br>• This invariant guarantees that Strava segments running along the active route render above the solid green base ($30\text{f} > 25\text{f}$) and remain visible through the dash gaps of the active overlay ($45\text{f}$ dashed), preventing polyline masking.<br>4. *Directional Chevrons (Forward Travel Arrows)*:<br>• In `MapLayers.kt`, when `path is MapRoute && path.isActiveNavigation && currentZoom > 13f`, the system SHALL render periodic forward-pointing directional chevrons along the polyline using cached `directionIcons`, rotating markers to match polyline segment bearing.<br>5. *Passive Route Subordination*:<br>• Passive routes marked visible on the map SHALL retain their 10f width and muted green styling, preventing visual clutter when displayed alongside the active route.<br><br>**Acceptance Criteria (Given-When-Then)**:<br>• *Given* an active route set in `RoutesRepository.setActiveNavigatedRoute(routeId)`,<br>• *When* rendered on the tracking map,<br>• *Then* the active route SHALL render with width 16f and vibrant Electric Emerald `#00E676` color.<br>• *Given* an active route overlapping with a Strava segment,<br>• *When* rendered on the map,<br>• *Then* the orange segment SHALL render between the solid active base ($Z=25\text{f}$) and dashed active overlay ($Z=45\text{f}$), fully visible through the overlay dash gaps.<br>• *Given* the map view zoomed to `zoom > 13f`,<br>• *When* viewing the active route,<br>• *Then* directional chevrons SHALL appear along the polyline indicating the route's travel direction.<br>• *Given* passive routes selected on the map alongside the active route,<br>• *When* rendered,<br>• *Then* passive routes SHALL render with width 10f and ForestGreen `#228B22`, establishing a clear visual hierarchy.<br><br>**Invariants**: Strava Live Segment detection and rendering (`SEGMENT_Z_INDEX = 30f`), existing route database schema, and 100% full-suite test pass rate MUST NOT be broken. | Provide prominent, high-contrast, directionally clear rendering for actively navigated routes while preserving multi-layer X-Ray Strava segment visibility. | `MapModels.kt`, `MapLayers.kt`, `RoutesRepository.kt`, `Color.kt` | `TST-MAP-025` | In Bearbeitung |
```

---

## 6. Risk Analysis & Mitigation Strategies

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Masking Overlapping Strava Segments** | Segments hidden behind active route line | Medium | Strictly enforce Z-index sandwich: `ROUTE_ACTIVE_BASE_Z_INDEX = 25f < SEGMENT_Z_INDEX = 30f < ROUTE_ACTIVE_OVERLAY_Z_INDEX = 45f`. |
| **Visual Clutter from Excessive Chevrons** | Map overcrowded with arrow markers | Medium | Gate chevron rendering to `currentZoom > 13f` and sample track points with proportional spacing (`windowed(2, step = ...)`). |
| **Performance Overhead on Long Routes** | Frame drops during map pan/zoom | Low | Calculate chevrons lazily and memoize markers by route ID and zoom level. |
| **Color Clash in Dark Mode / AMOLED** | Inadequate contrast or blinding glare | Low | `#00E676` (Electric Emerald) tested for high luminance against AMOLED dark `#121212` and outdoor sunlight. |

---

## 7. Next Steps & Stage 2 Transition

1. Post this Stage 1 Analysis to subtask `ATT-2263`.
2. Transition `ATT-2263` to `In Überprüfung`.
3. Execute automated Gate 1 audit (`python3 tools/review_agent.py audit ATT-2263`).
4. Upon Gate 1 approval, register `REQ-MAP-023` in `docs/requirements.md` and commit.
5. Advance to Stage 2 (`[Req & Test Spec]`).
