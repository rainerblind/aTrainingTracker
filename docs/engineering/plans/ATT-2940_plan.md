# Stage 3: Implementation Plan - ATT-2940: Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-3059](https://atrainingtracker.atlassian.net/browse/ATT-3059) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.7`  
**Requirement Mapping**: `REQ-MAP-040`  
**Test Mapping**: `TST-MAP-042`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During Sprint 2026-41.6 review of ATT-2940, the human user rejected the previous implementation because line width scaling (18f) and dashed pattern removal (`pattern = null`) were applied globally to all active/selected routes instead of exclusively to the single selected route the athlete is actively following (`isActiveNavigation == true`):
> *"Unfortunately, there was a misunderstanding. We have active routes and one selected route. The selected one is the one the user wants to follow. Only this one must be highlighted as requested by this ticket. The active routes should not have been touched."*

This implementation plan establishes the architectural steps to restore active/background route defaults (`10f`, standard dash pattern) while keeping the 26f prominent solid ribbon on the actively navigated route.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-040` (*High-Glanceability Route Polyline Scaling and Solid Line Rendering for Actively Navigated Route*)
* **Test Mapping**: `TST-MAP-042` (*High-Glanceability Route Polyline Scaling and Solid Line Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All existing map tests, segment overlays, and climb representations continue to pass cleanly.
2. **Layer Separation & Z-Index Stacking**: `ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (24f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)` strictly preserved.
3. **Active Background Routes Untouched**: Routes toggled visible on the map without active navigation retain their baseline width (10f) and dashed overlay (`Dash(15f)`, `Gap(15f)`).
4. **Directional Chevrons**: `ActiveRouteDecorations` continues to draw forward chevrons over the 26f solid ribbon.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket ATT-2940 remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Map Models (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`)
* In `MapStyle`:
  * `routeWidth: Float = 10f` (revert from 18f).
  * `routeActiveNavigationWidth: Float = 26f` (retained).
* In `MapVisualization`:
  * `ROUTE_WIDTH = 10f` (revert from 18f).
  * `ROUTE_ACTIVE_NAVIGATION_WIDTH = 26f` (retained).
* In `MapRoute`:
  ```kotlin
  override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
      get() = if (isActiveNavigation) {
          null
      } else {
          listOf(
              com.google.android.gms.maps.model.Dash(MapVisualization.ROUTE_DASH_LENGTH),
              com.google.android.gms.maps.model.Gap(MapVisualization.ROUTE_GAP_LENGTH)
          )
      }
  ```

### UI Consistency (Rule 23 — Mandatory UI Audit)
* **Reference screen / component**: `ATrainingTrackerMap.kt` / `MapLayers.kt` (map route polyline rendering)
* **Reused components**: `XRayPolyline` in `MapLayers.kt`
* **Theme tokens**: `TTColor.RouteActiveNavigation` (Royal Blue #1E88E5), `TTColor.RouteSelected` (Navy Blue #1565C0), `TTColor.RouteUnselected`
* **New one-off styles & justification**: None. Reusing existing semantic tokens and dimension constants.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Route Width Constants in `MapModels.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`
* Changes:
  * Revert `MapStyle.routeWidth` from `18f` to `10f`.
  * Revert `MapVisualization.ROUTE_WIDTH` from `18f` to `10f`.
  * Ensure `routeActiveNavigationWidth` and `ROUTE_ACTIVE_NAVIGATION_WIDTH` remain `26f`.

### Step 2: Contextualize Pattern in `MapRoute` (`MapModels.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`
* Changes:
  * Update `MapRoute.pattern`: return `null` if `isActiveNavigation == true`, otherwise return `listOf(Dash(ROUTE_DASH_LENGTH), Gap(ROUTE_GAP_LENGTH))`.

### Step 3: Align Unit Tests in `MapRouteActiveNavigationTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapRouteActiveNavigationTest.kt`
* Changes:
  * Assert `activeRoute.width == 26f` and `activeRoute.pattern == null`.
  * Assert `passiveSelectedRoute.width == 10f` and `passiveSelectedRoute.pattern != null` with `Dash(15f)` and `Gap(15f)`.
  * Assert `unselectedRoute.width == 6f` and `unselectedRoute.pattern != null`.
  * Assert Z-index stacking invariants.

### Step 4: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest"
  ```
* Expected: 100% pass rate in ~5–15 seconds.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit test execution during Stage 4, followed by full clean-room test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-2940` allows clean rollback to `sprint/2026-41.7` baseline without affecting `develop`.
