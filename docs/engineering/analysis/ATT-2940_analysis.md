# Stage 1 Analysis: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-2979](https://atrainingtracker.atlassian.net/browse/ATT-2979) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During active route navigation and on-device desk testing with physical devices (Google Pixel 10 mounted on a handlebar, ATT-1841 review), route polyline visualization exhibited two critical usability shortcomings:
1. **Insufficient Polyline Thickness**: The selected route (`ROUTE_WIDTH = 10f`) and actively navigated route (`ROUTE_ACTIVE_NAVIGATION_WIDTH = 16f`) lines are not thick enough to be quickly glanceable at an arm's length under bright outdoor sunlight. Athletes glancing at their smartphone mounted on a bicycle handlebar or wrist strap have difficulty perceiving the upcoming path trajectory against dense topographic or satellite map backgrounds.
2. **Distracting Navy Dashed Overlay Clutter**: For actively navigated routes, `MapRoute` currently defines a dual-layer X-Ray polyline pattern: a solid vibrant base (`TTColor.RouteActiveNavigation = Color(0xFF1E88E5)`) and a dashed midnight navy centerline overlay (`TTColor.RouteActiveNavigationOverlay = Color(0xFF0D47A1)`, `Dash(30f)`, `Gap(15f)`). At standard navigation zoom levels, this produces visual fragmentation, making the route appear broken or dotted rather than a smooth, continuous, authoritative navigational course.

### Current State vs. Expected Behavior
* **Current State**:
  * Actively navigated routes render with base width 16f and a permanent 8f navy dashed overlay.
  * Selected routes render with width 10f and a 10f dashed overlay.
  * Route lines are hard to follow at high speeds or arm's length under direct sun.
* **Expected Behavior**:
  * Actively navigated routes render with prominent, thick polyline width (26f in `MapVisualization` and `MapStyle`), providing immediate glanceability at handlebar distance.
  * Selected passive routes render with increased base width (18f), distinct from unselected background routes (6f).
  * The dashed overlay is completely removed from standard active route navigation (`pattern = null`), rendering the route as a clean, crisp, solid ribbon.
  * Directional travel chevrons (`ActiveRouteDecorations`) continue to indicate heading along the solid ribbon.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Fixed Width Dimension Tokens (`MapModels.kt:81-84, 113-116`)
In `MapModels.kt`:
```kotlin
object MapVisualization {
    ...
    const val ROUTE_WIDTH = 10f
    const val ROUTE_ACTIVE_NAVIGATION_WIDTH = 16f
    ...
}

data class MapStyle(
    ...
    val routeWidth: Float = 10f,
    val routeActiveNavigationWidth: Float = 16f,
    ...
)
```
These dimensions were calibrated in Sprint 2026-40.14 (`ATT-1841`) before high-density displays (such as Pixel 10 440+ DPI) and handlebar desk mounts were formally evaluated. At 16f, the polyline is only ~4dp wide on modern screens, causing high visual strain in motion.

### 2.2 Unconditional Dashed Overlay Pattern (`MapModels.kt:295-306`)
In `MapRoute`:
```kotlin
override val pattern: List<com.google.android.gms.maps.model.PatternItem>
    get() = if (isActiveNavigation) {
        listOf(
            com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_ACTIVE_DASH_LENGTH),
            com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_ACTIVE_GAP_LENGTH)
        )
    } else {
        listOf(
            com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_DASH_LENGTH),
            com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_GAP_LENGTH)
        )
    }
```
In `MapLayers.kt:68-81`, `XRayPolyline` evaluates `path.pattern`. Because `pattern` is unconditionally populated, `XRayPolyline` always draws a second polyline on top of the solid base with `Dash` and `Gap` items. Originally designed to allow Strava segment X-Ray interleaving, this permanent dashed overlay degrades route legibility when no segments are present, creating a stippled, busy aesthetic.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: Amends `REQ-MAP-023` (*Prominent High-Contrast Rendering for Actively Navigated Routes*, ATT-1841) and `REQ-UI-306` (*Royal Blue Route Palette Transition*, ATT-2761).
2. **Historical Origin & Commit Trace**: Ticket `ATT-1841` (commit `f8194a2b`) established `isActiveNavigation`, `ROUTE_ACTIVE_NAVIGATION_WIDTH = 16f`, and the dual-layer dashed overlay. Ticket `ATT-2761` (commit `e4871ad2`) shifted colors to Royal Blue while preserving the 16f width and dashed pattern.
3. **Root Reason for Existing Formulation**: ATT-1841 introduced the dashed overlay to prevent an active route from completely covering Strava Live Segments running along the same road. However, in Sprint 2026-41.6 (`ATT-2864`), climb and segment overlays were elevated to dedicated Z-indices (`CLIMB_Z_INDEX = 28f`, `SEGMENT_Z_INDEX = 30f`) and climb spans are rendered as solid overlays, while segments are rendered as distinct orange dashed polylines. Because segments and climbs sit *above* the route polyline in Z-order, the route itself no longer needs a dashed overlay to let segments show through!
4. **Preservation of Core Invariants**:
   * Z-index stacking order (`ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`) remains strictly preserved.
   * Forward travel directional chevrons (`ActiveRouteDecorations`) remain rendered along active routes.
   * Strava Live Segment orange dashed styling and Climb Category colors remain completely intact.
   * 100% test pass rate across the full test suite must be preserved.

---

## 4. Proposed Technical Solution & Architecture

### 4.1 Dimension Token Scaling (`MapModels.kt`)
Update dimension constants in both `MapVisualization` and `MapStyle`:
* `ROUTE_ACTIVE_NAVIGATION_WIDTH`: Increase from `16f` to `26f` (within the planned 24f–28f range).
* `ROUTE_WIDTH`: Increase from `10f` to `18f` (within the planned 18f–20f range).
* `ROUTE_UNSELECTED_WIDTH`: Retain at `6f` to preserve visual hierarchy.

### 4.2 Clean Solid Polyline Architecture (`MapModels.kt`)
In `MapRoute`:
* Set `override val pattern: List<PatternItem>? get() = null`.
* Setting `pattern` to `null` causes `XRayPolyline` in `MapLayers.kt` to bypass the secondary dashed polyline, rendering only the clean, solid base polyline.
* Actively navigated routes render as a solid 26f ribbon in `TTColor.RouteActiveNavigation` (`Color(0xFF1E88E5)`).
* Selected passive routes render as a solid 18f ribbon in `TTColor.RouteSelected` (`Color(0xFF1565C0)`).

### 4.3 Directional Chevrons Preservation
`ActiveRouteDecorations` in `MapLayers.kt` continues to draw forward chevrons over the 26f solid polyline at zoom > 13f, providing unambiguous heading indication without dashed clutter.

---

## 5. User Scope Grounding (`ATT-1250`)

### In-Scope
* Scaling `ROUTE_ACTIVE_NAVIGATION_WIDTH` to `26f` and `ROUTE_WIDTH` to `18f` in `MapVisualization` and `MapStyle`.
* Setting `MapRoute.pattern` to `null` to eliminate the distracting dashed overlay.
* Updating unit tests in `MapRouteActiveNavigationTest.kt` to assert the new 26f and 18f widths and solid pattern behavior.
* Synchronizing `docs/requirements.md` (`REQ-MAP-040`) and `docs/tests.md` (`TST-MAP-042`).

### Out-of-Scope
* Modifying Strava segment polyline widths or dash patterns (`SEGMENT_WIDTH = 10f`).
* Modifying Climb Category colors or widths (`CLIMB_WIDTH = 10f`).
* Changing turn prompt banners or HUD cards.

---

## 6. Verification & Test Strategy

1. **`MapRouteActiveNavigationTest.kt`**:
   * Verify `activeRoute.width` equals `26f` (`MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`).
   * Verify `passiveRoute.width` equals `18f` (`MapVisualization.ROUTE_WIDTH`).
   * Verify `unselectedRoute.width` equals `6f` (`MapVisualization.ROUTE_UNSELECTED_WIDTH`).
   * Verify `activeRoute.pattern` is `null` (asserting clean solid line rendering).
   * Verify Z-index hierarchy is preserved: `ROUTE_BASE_Z_INDEX < ROUTE_ACTIVE_BASE_Z_INDEX < CLIMB_Z_INDEX < SEGMENT_Z_INDEX < USER_LOCATION_Z_INDEX`.
2. **Full Clean-Room Regression**:
   * Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

---

## 7. Invariants Enforced

* **Z-Index Layering Integrity**: Routes remain below climbs ($26\text{f} < 28\text{f}$) and segments ($26\text{f} < 30\text{f}$), while the GPS user puck sits above all ($100\text{f}$).
* **Directional Glanceability**: Active routes retain directional chevrons on top of the prominent solid 26f ribbon.
* **Backward Compatibility**: `MapStyle` and `MapVisualization` maintain compatible API contracts.
