# Stage 3 Implementation Plan: ATT-2940 - Increase selected route polyline thickness and remove dashed overlay at standard zoom levels

**Ticket**: [ATT-2940](https://atrainingtracker.atlassian.net/browse/ATT-2940)  
**Sub-task**: [ATT-2981](https://atrainingtracker.atlassian.net/browse/ATT-2981) (`[Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2940`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Design & Approach

To enhance visual glanceability on handlebar mounts under outdoor sunlight and eliminate stippled visual fragmentation, the route polyline rendering pipeline in `MapModels.kt` will be updated:
1. **Dimension Constants**:
   - `MapVisualization.ROUTE_ACTIVE_NAVIGATION_WIDTH`: Scaled from `16f` to `26f`.
   - `MapVisualization.ROUTE_WIDTH`: Scaled from `10f` to `18f`.
   - `MapVisualization.ROUTE_UNSELECTED_WIDTH`: Preserved at `6f`.
   - `MapStyle`: Synchronize default `routeWidth = 18f` and `routeActiveNavigationWidth = 26f`.
2. **Solid Polyline Ribbon Architecture**:
   - In `MapRoute`: Set `override val pattern: List<com.google.android.gms.maps.model.PatternItem>? get() = null`.
   - In `MapLayers.kt`, `XRayPolyline` evaluates `path.pattern`. When `pattern == null`, it bypasses the secondary overlay and renders exclusively the solid base polyline.
   - Forward travel chevrons (`ActiveRouteDecorations`) continue to render directly on top of the 26f solid ribbon at zoom levels > 13f.
3. **Z-Ordering Invariant Preservation**:
   - Stacking order remains: `ROUTE_BASE_Z_INDEX (20f) < ROUTE_ACTIVE_BASE_Z_INDEX (26f) < CLIMB_Z_INDEX (28f) < SEGMENT_Z_INDEX (30f) < USER_LOCATION_Z_INDEX (100f)`.

---

## 2. Atomic Implementation Steps

### Step 1: Map Dimension & Pattern Updates (`MapModels.kt`)
* Modify `MapVisualization`:
  ```kotlin
  const val ROUTE_WIDTH = 18f
  const val ROUTE_ACTIVE_NAVIGATION_WIDTH = 26f
  ```
* Modify `MapStyle`:
  ```kotlin
  val routeWidth: Float = 18f,
  val routeActiveNavigationWidth: Float = 26f,
  ```
* Modify `MapRoute`:
  ```kotlin
  override val pattern: List<com.google.android.gms.maps.model.PatternItem>?
      get() = null
  ```

### Step 2: Test Suite Realignment (`MapRouteActiveNavigationTest.kt`)
* Update existing unit test assertions to reflect the 26f active width, 18f passive width, and null pattern:
  - Assert `activeRoute.width == 26f`
  - Assert `passiveRoute.width == 18f`
  - Assert `unselectedRoute.width == 6f`
  - Assert `activeRoute.pattern == null`
  - Assert `passiveRoute.pattern == null`
  - Assert Z-index layering: $20\text{f} < 26\text{f} < 28\text{f} < 30\text{f} < 100\text{f}$

### Step 3: Targeted Unit Test Execution
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.MapRouteActiveNavigationTest"`.

### Step 4: Clean-Room Full Regression Verification
* Execute full clean-room unit test suite: `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Guardrails

* **Zero Regressions**: 100% test pass rate across the entire test suite.
* **Chesterton's Fence Compliance**: Climbing overlays (28f) and Strava segments (30f) remain above the 26f route polyline.
* **Localization Parity**: No new strings introduced.
