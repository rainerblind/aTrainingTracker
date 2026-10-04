# Stage 3 Implementation Plan: ATT-1841 - Prominent High-Contrast Rendering for Actively Navigated Routes Preserving Multi-Layer X-Ray Segment Synergy

**Ticket**: [ATT-1841](https://rainerblind.atlassian.net/browse/ATT-1841)  
**Sub-task**: [ATT-2265](https://rainerblind.atlassian.net/browse/ATT-2265) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-1841`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architectural Design (SWE.2)

```
+-----------------------------------------------------------------------------------+
| UI Layer (Jetpack Compose / Google Maps Compose)                                  |
|                                                                                   |
|  [MapTrackingScreen] / [MapDetailLayout]                                          |
|         |                                                                         |
|         v                                                                         |
|  [MapRoute] (Polylines + Directional Chevrons)                                    |
|    - Z=45.0f : Active Route Overlay (Dashed #004D20 / 8f width)                   |
|    - Z=40.0f : Passive Route Overlay (Dashed #003300 / 5f width)                  |
|    - Z=30.0f : Strava Live Segment Polyline (Solid Coral/Orange #FF5722 / 8f)    |
|    - Z=25.0f : Active Route Base Polyline (Solid Electric Emerald #00E676 / 16f)  |
|    - Z=20.0f : Passive Route Base Polyline (Solid ForestGreen #228B22 / 10f)      |
|    - Z=5.0f  : Unselected Route Polyline (Muted Slate / 6f)                      |
+-----------------------------------------------------------------------------------+
                                         ^
                                         | StateFlow<Long?>
+-----------------------------------------------------------------------------------+
| Repository Layer                                                                  |
|  [RoutesRepository]                                                               |
|    - activeNavigatedRouteId: StateFlow<Long?>                                     |
|    - setActiveNavigatedRoute(routeId: Long?)                                      |
+-----------------------------------------------------------------------------------+
```

---

## 2. Order-Dependent Construction Steps

### Step 1: Design Tokens in `TTColor.kt` & `MapVisualization.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/TTColor.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapVisualization.kt`
* **Changes**:
  - In `TTColor.kt`:
    - Add `val RouteActiveNavigation = Color(0xFF00E676)` (Electric Emerald).
    - Add `val RouteActiveNavigationOverlay = Color(0xFF004D20)` (Deep Forest Green for dashed overlay).
  - In `MapVisualization.kt`:
    - Add `const val ROUTE_ACTIVE_NAVIGATION_WIDTH = 16f`.
    - Add `const val ROUTE_ACTIVE_BASE_Z_INDEX = 25.0f`.
    - Add `const val ROUTE_ACTIVE_OVERLAY_Z_INDEX = 45.0f`.
    - Add `const val ACTIVE_ROUTE_DASH_LENGTH = 30f`.
    - Add `const val ACTIVE_ROUTE_GAP_LENGTH = 15f`.
    - Add `const val ROUTE_ACTIVE_OVERLAY_WIDTH = 8f`.

### Step 2: `MapRoute.kt` Model Enhancement
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapRoute.kt`
* **Changes**:
  - Add `val isActiveNavigation: Boolean = false` to `MapRoute` constructor.
  - In `fromRoute(...)` or model builders:
    - If `isActiveNavigation == true`:
      - `color = TTColor.RouteActiveNavigation`
      - `width = MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`
      - `zIndex = MapVisualization.ROUTE_ACTIVE_BASE_Z_INDEX`
      - `overlayColor = TTColor.RouteActiveNavigationOverlay`
      - `overlayWidth = MapVisualization.ROUTE_ACTIVE_OVERLAY_WIDTH`
      - `overlayZIndex = MapVisualization.ROUTE_ACTIVE_OVERLAY_Z_INDEX`
      - `overlayPattern = listOf(Dash(MapVisualization.ACTIVE_ROUTE_DASH_LENGTH), Gap(MapVisualization.ACTIVE_ROUTE_GAP_LENGTH))`
    - Maintain existing logic when `isSelected == true && !isActiveNavigation` and `!isSelected`.

### Step 3: `RoutesRepository.kt` Active Route State
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`
* **Changes**:
  - Expose `private val _activeNavigatedRouteId = MutableStateFlow<Long?>(null)`.
  - Expose `val activeNavigatedRouteId: StateFlow<Long?> = _activeNavigatedRouteId.asStateFlow()`.
  - Expose `fun setActiveNavigatedRoute(routeId: Long?) { _activeNavigatedRouteId.value = routeId }`.

### Step 4: Directional Chevrons Geometry & Map Integration
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapVisualization.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapTrackingScreen.kt`
* **Changes**:
  - Implement bearing calculation utility `calculateBearing(p1: LatLng, p2: LatLng): Float`.
  - Implement chevron sampling utility `getChevronPositions(points: List<LatLng>, intervalMeters: Double): List<Pair<LatLng, Float>>`.
  - Integrate chevron markers along active route polylines when map zoom $> 13f$.

### Step 5: Unit Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepositoryActiveNavigationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteChevronsTest.kt`
* **Execution**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest" --tests "com.atrainingtracker.trainingtracker.repositories.RoutesRepositoryActiveNavigationTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteChevronsTest"`

### Step 6: Full Clean-Room Regression
* **Execution**: `./gradlew testDebugUnitTest`

---

## 3. Invariants & Safety Measures

1. **Strava Segment Multi-Layer Sandwich Invariant**:
   - $Z_{\text{base,passive}} (20\text{f}) < Z_{\text{base,active}} (25\text{f}) < Z_{\text{segment}} (30\text{f}) < Z_{\text{overlay,passive}} (40\text{f}) < Z_{\text{overlay,active}} (45\text{f})$.
   - This ensures live segment highlights never get buried beneath the 16f active navigation line, but shine clearly through the center while the dashed overlay borders it.
2. **GPS Puck Prominence**:
   - User location pin remains anchored at $Z = 100.0\text{f}$, completely unoccluded.
3. **Null & Degenerate Polyline Safety**:
   - Routes with $<2$ points or null coordinates do not trigger chevron calculation errors or IndexOutOfBoundsException.
4. **Zero Regressions**:
   - Standard route previews and route editing remain visually intact with standard 10f width.
