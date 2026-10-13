# Stage 5 Walkthrough - ATT-2509

**Ticket**: [ATT-2509](https://atrainingtracker.atlassian.net/browse/ATT-2509)  
**Summary**: Color Climb Polylines on Route Map to Highlight Climb Spans  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-UI-298`  
**Test Mapping**: `TST-UI-258`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Overview & Verification Scope

`ATT-2509` establishes spatial climb span highlighting directly on the 2D route map (`RouteOnMapScreen.kt`). Previously, cycling climbs were designated only by a start marker pin (`ic_ascent` with category background color) at `climb.startLatLng`. Athletes could not discern where the climb extended to or where it crested along the route curve.

With this change:
1. `MapContentScope` provides a declarative `climbs(climbs: List<Climb>)` primitive.
2. Each classified climb (`CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`) is rendered with its category color via canonical `getClimbCategoryColors(climb.category).first`.
3. Unclassified/flat/downhill sections retain standard route coloring (`RouteSelected` / ForestGreen).
4. `RouteOnMapScreen.kt` integrates `climbs(climbs)` into `mapContent`.
5. Visual, color, and layer depth harmony are 100% maintained:
   - `route.zIndex (20-21f)` < `climbHighlight.zIndex (25f)` < `markers.zIndex (50f)`.

---

## 2. Changes Implemented

### 2.1 Map Content DSL Extension (`MapContentScope.kt`)
- Added `fun climbs(climbs: List<Climb>)` to `MapContentScope` interface.
- Added `internal data class ClimbHighlightData(val path: List<LatLng>, val color: Color, val zIndex: Float = 25f, val width: Float = 10f)`.
- Implemented `override fun climbs(climbs: List<Climb>)`:
  - Filters out `ClimbCategory.UNCATEGORIZED`.
  - Maps `climb.pathPoints.map { it.latLng }`, cleanly falling back to `listOf(climb.startLatLng, climb.endLatLng)` if `pathPoints` is empty.
  - Oromotes point arrays with length $\ge 2$ into `climbHighlights`.
  - Maps color to `getClimbCategoryColors(climb.category).first`.
- Added rendering pass in `Render()`:
  - Renders `Polyline` with `zIndex = 25f`, `width = 10f`, and `jointType = JointType.ROUND`.
- Added `climbHighlights.clear()` to `collect()`.

### 2.2 Route On Map Integration (`RouteOnMapScreen.kt`)
- In `mapContent`, right after `routes(listOf(route))`, added `climbs(climbs)`.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit & Contract Tests
Executed via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ClimbPolylineContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteOnMapScreenClimbContractTest"`:
- `testClimbsHighlightCreation_forAllStandardCategories`: **PASSED** (verifies all 5 standard categories generate correct colors, coordinates, zIndex=25f, width=10f).
- `testUncategorizedClimbs_areOmittedFromHighlightLayer`: **PASSED** (verifies `UNCATEGORIZED` produces 0 highlights).
- `testFallbackToEndpoints_whenPathPointsEmpty`: **PASSED** (verifies fallback to `[startLatLng, endLatLng]`).
- `testDegenerateSinglePointClimb_isIgnored`: **PASSED** (verifies degenerate 1-point paths are omitted).
- `testClear_resetsClimbHighlights`: **PASSED** (verifies lifecycle cleanup in `collect()`).
- `testRouteOnMapScreen_invokesClimbsWithinMapContent`: **PASSED** (verifies AST integration).

### 3.2 Full Regression Suite
- Clean-room build: `./gradlew testDebugUnitTest` executed with **BUILD SUCCESSFUL**. Zero regressions across the entire application test suite.

---

## 4. Invariants & Non-Functional Compliance

| Aspect | Status | Notes |
| :--- | :--- | :--- |
| **Route Interaction** | Preserved | Clicking, elevation profile scrubbing, zoom-to-fit unchanged. |
| **Depth Sorting** | Preserved | `20f (route) < 25f (climb overlay) < 50f (markers)`. |
| **Color Tokens** | 100% Harmonic | Canonical `getClimbCategoryColors(category).first` matches chips, pins, and polylines. |
| **Localization Parity** | 100% | Zero new strings needed. Existing category strings have 9-language parity. |
| **Database Schemas** | Preserved | Zero changes to SQLite databases. |
