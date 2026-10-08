# Stage 2 Requirement & Test Specification - ATT-2509

**Ticket**: [ATT-2509](https://atrainingtracker.atlassian.net/browse/ATT-2509)  
**Summary**: Color Climb Polylines on Route Map to Highlight Climb Spans  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-UI-298`  
**Test Mapping**: `TST-UI-258`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement & Test Specification Mapping

| Requirement ID | Test Case ID | Scope | Target File(s) | Status |
| :--- | :--- | :--- | :--- | :--- |
| **`REQ-UI-298`** | **`TST-UI-258`** | Route Map Climb Span Polyline Highlighting by Climb Category Classification | `MapContentScope.kt`, `RouteOnMapScreen.kt` | Specified |

---

## 2. Formal Requirement Specification (`REQ-UI-298`)

The system SHALL highlight the spatial polyline path of recognized cycling climbs on the route map (`RouteOnMapScreen.kt`) using their respective climb category color classification (`REQ-UI-274`, `REQ-MAP-027`, ATT-2509):

1. **MapContentScope Declarative API (`MapContentScope.kt`)**:
   - `MapContentScope` SHALL provide a declarative function `fun climbs(climbs: List<Climb>)`.
   - For each classified climb (`climb.category != ClimbCategory.UNCATEGORIZED`), the system SHALL render a dedicated `Polyline` along `climb.pathPoints.map { it.latLng }` (or `listOf(climb.startLatLng, climb.endLatLng)` as fallback when `pathPoints` is empty).
   - The climb polyline SHALL be rendered at `zIndex = 25f` (above the base route at 20-21f, below interactive markers at 50f) and `width = 10f` (matching `MapVisualization.ROUTE_WIDTH`).
   - The polyline color SHALL match the canonical climb category color: `getClimbCategoryColors(climb.category).first`.
2. **Visual & Structural Harmony in `RouteOnMapScreen.kt`**:
   - In `mapContent`, after calling `routes(listOf(route))`, `RouteOnMapScreen` SHALL invoke `climbs(climbs)`.
   - Unclassified, flat, and downhill sections of the route SHALL retain their standard route polyline coloring (`RouteSelected` / ForestGreen).
   - Climb start markers (`ic_ascent`) continue to mark the inception of each climb at `climb.startLatLng`.
3. **Preservation of Invariants**:
   - Route selection, clicking, panning, zoom-to-fit, and scrubbing MUST remain 100% operational without regression.
   - Non-classified routes or routes with 0 climbs SHALL render cleanly without visual glitches or empty polyline overlays.

---

## 3. Acceptance Criteria (Given-When-Then)

### AC-1: Category Color Coding
- **Given** a route containing categorized climbs (`CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`),
- **When** `RouteOnMapScreen` renders the map,
- **Then** each climb's polyline is rendered with its category color:
  - `CAT_4` -> `0xFF2E7D32` (Material Green 800)
  - `CAT_3` -> `0xFFF9A825` (Amber / Yellow)
  - `CAT_2` -> `0xFFEF6C00` (Orange)
  - `CAT_1` -> `0xFFC62828` (Red)
  - `HC` -> `0xFF880E4F` (Dark Magenta / Purple)

### AC-2: Exclusion of Uncategorized Spans
- **Given** a climb classified as `ClimbCategory.UNCATEGORIZED` or flat/downhill sections,
- **When** the route map renders,
- **Then** no colored highlight polyline is generated for that section, preserving the standard route green (`RouteSelected`).

### AC-3: Path Geometry & Endpoint Fallback
- **Given** a climb with populated `pathPoints`,
- **When** the climb polyline is rendered,
- **Then** its vertices match `climb.pathPoints.map { it.latLng }` with exact 1:1 coordinate identity.
- **Given** a climb with empty `pathPoints`,
- **When** the climb polyline is rendered,
- **Then** it cleanly falls back to `listOf(climb.startLatLng, climb.endLatLng)`.

### AC-4: Depth Sorting & Z-Index Invariant
- **Given** rendered map layers on `RouteOnMapScreen`,
- **When** depth sorting is evaluated,
- **Then** `baseRoute.zIndex (20f)` < `climbHighlight.zIndex (25f)` < `locationMarkers.zIndex (50f)`.

---

## 4. Test Cases (`TST-UI-258`)

### 4.1 Unit & Contract Tests: `ClimbPolylineContractTest.kt`
- `testClimbsHighlightCreation_forAllStandardCategories`:
  Instantiate `MapContentScopeImpl`, invoke `climbs()` with climbs of categories `CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, and `HC`. Assert 5 highlight entries created with expected colors and points.
- `testUncategorizedClimbs_areOmittedFromHighlightLayer`:
  Invoke `climbs()` with a list containing only an `UNCATEGORIZED` climb. Assert 0 highlight entries created.
- `testFallbackToEndpoints_whenPathPointsEmpty`:
  Invoke `climbs()` with a climb where `pathPoints` is empty. Assert highlight entry uses `listOf(climb.startLatLng, climb.endLatLng)`.
- `testClimbPolylineZIndexAndWidth`:
  Verify highlight entries use `zIndex = 25f` and `width = 10f`.

### 4.2 Integration Contract Test: `RouteOnMapScreenClimbContractTest.kt`
- `testRouteOnMapScreen_invokesClimbsWithinMapContent`:
  Verify AST / contract structure of `RouteOnMapScreen.kt` confirming that `climbs(climbs)` is invoked inside `mapContent`.

### 4.3 Clean-Room Regression Test
- Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with 0 regressions.

---

## 5. Localization Audit

- **Strings Required**: Zero new strings.
- Existing category strings (`R.string.climb_category_hc`, `R.string.climb_category_cat1`..`cat4`, `R.string.climb_category_uc`) already maintain 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
