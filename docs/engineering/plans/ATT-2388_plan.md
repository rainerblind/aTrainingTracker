# Stage 3: Implementation Plan - ATT-2388: Increase Visibility and Visual Prominence of Climbs on Routes

**Ticket**: [ATT-2388](https://atrainingtracker.atlassian.net/browse/ATT-2388)  
**Sub-task**: [ATT-2501](https://atrainingtracker.atlassian.net/browse/ATT-2501) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*)  
**Test Mapping**: `TST-UI-234` (*Route Climbs Visual Prominence Verification*)  
**Branch**: `feature/ATT-2388`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Problem Description & Background

During Sprint 2026-40.15 (`ATT-1281` / `REQ-MAP-027`), automated climb detection, SQLite persistence in `Climbs.db`, and the real-time Cockpit bottom sheet (`LiveClimbSheet.kt`) during live tracking were verified and deployed.

However, athletes inspecting routes prior to or during workouts currently experience near-zero visibility into the climbs that comprise a route:
1. **Elevation Profile Blindness**: In `ElevationProfile.kt`, the elevation profile is rendered strictly as micro-gradient segments without indication of where recognized sustained ascents begin, peak, or end, displaying zero category ratings (Cat 4, Cat 3, Cat 2, Cat 1, HC).
2. **Route Map Polyline Uniformity**: On the route map (`RouteOnMapScreen` / `ATrainingTrackerMap`), the entire route polyline is rendered as a uniform single-color stroke with zero climb start markers or category badges.
3. **Route Cards & Overview Header Absence**: In `RouteSummaryHeader.kt` and `RouteItem.kt`, the summary metrics row displays only total distance and cumulative elevation gain, omitting climb counts and difficulty indicators.
4. **Missing Climb Breakdown**: In route details (`RouteOnMapScreen.kt`), there is no dedicated interactive breakdown of individual climbs displaying starting kilometer, length, average grade, vertical gain, and category.

Athletes planning training rides or pacing efforts cannot easily assess the climbing difficulty or spatial distribution of hills along their chosen routes.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*)
  * Refines and extends `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*) and `REQ-UI-267` / `REQ-UI-273` (*Routes & Segments Elevation Profile and Map Detail Layout*).
  * Enriches `RouteWithPath` and `MapRoute` with `val climbs: List<Climb> = emptyList()`.
  * Enhances `RoutesRepository` to load climbs from `ClimbsDatabaseManager` with backward-compatible on-the-fly detection via `ClimbDetector.detectClimbs`.
  * Enhances `RouteSummaryHeader` with climb count and difficulty metrics.
  * Enhances `ElevationProfile` and `MapDetailLayout` with climb ridge accent strokes and summit category badge pills.
  * Enhances `RouteOnMapScreen` with climb start markers and interactive climb breakdown section.
  * Enforces 100% 9-language translation parity.
* **Test Mapping**: `TST-UI-234` (*Route Climbs Visual Prominence Verification*)
  * `TST-UI-234.1`: Model & Repository Climb Propagation Unit Tests (`RouteClimbsRepositoryTest.kt`).
  * `TST-UI-234.2`: UI & Elevation Profile Contract Tests (`RouteClimbsUiContractTest.kt`).
  * `TST-UI-234.3`: 9-Language Localization Audit (`RouteClimbsLocalizationTest.kt`).
  * `TST-UI-234.4`: Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly.
2. **Climb Detection Formula Invariance**: Mathematical thresholds in `ClimbDetector` (500m min distance, 3% min grade, 20m min gain) and UCI score category tiers (`HC`, `CAT_1`–`CAT_4`) remain unchanged.
3. **Cockpit Live Tracking Invariant**: `LiveClimbSheet.kt` and `LiveClimbsRepository` real-time tracking behavior and Strava Live Segment precedence in the cockpit are 100% preserved.
4. **Elevation Zoom & Scrubbing Invariant**: `ElevationProfileZoomMath` zooming, panning, and distance scrubbing mechanics are preserved with zero regression.
5. **Subtask Self-Sufficiency**: Subtask [ATT-2501](https://atrainingtracker.atlassian.net/browse/ATT-2501) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2388](https://atrainingtracker.atlassian.net/browse/ATT-2388) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Data Models & Extensions
* **`RouteWithPath` (`RoutesDatabaseManager.kt`)**: Add `val climbs: List<Climb> = emptyList()`.
* **`MapRoute` (`MapModels.kt`)**: Add `val climbs: List<Climb> = emptyList()`.
* **`RouteWithPath.toMapRoute()` (`MapModels.kt`)**: Forward `climbs = this.climbs`.

### Component 2: Repository Enrichment & Backward Compatibility (`RoutesRepository.kt`)
* In `RoutesRepository`:
  * Inject / instantiate `ClimbsDatabaseManager`.
  * During route loading (`refreshRoutes()` / `getRouteByClusterId` / `getRouteById`):
    - For each route, query `climbsDb.getClimbsForRoute(route.summary.id)`.
    - If empty and `route.path.size >= 2`, invoke `ClimbDetector.detectClimbs(route.path, routeId = route.summary.id)`.
    - Enrich route instance: `route.copy(climbs = climbs)`.

### Component 3: Reusable Climb Category UI Components (`ClimbCategoryChip.kt` / `LiveClimbSheet.kt`)
* Make `ClimbCategoryChip` publicly accessible from `com.atrainingtracker.trainingtracker.ui.climbs` or extract to shared composable.
* Provide helper `getClimbCategoryColors(category: ClimbCategory)` mapping each category to background color, text color, and string resource.

### Component 4: Route Summary Header Prominence (`RouteSummaryHeader.kt`)
* Add parameter `climbs: List<Climb> = emptyList()`.
* When `climbs.isNotEmpty()`: render a climb count metric item (`MetricItem(iconRes = R.drawable.ic_ascent, value = stringResource(R.string.routes_climb_count, climbs.size), isPrimary = true)`).

### Component 5: Elevation Profile Canvas Accents & Category Badges (`ElevationProfile.kt` & `MapDetailLayout.kt`)
* In `MapDetailLayout.kt`:
  * Add parameter `climbs: List<Climb> = emptyList()`.
  * Forward `climbs = climbs` to `ElevationProfile`.
* In `ElevationProfile.kt`:
  * Add parameter `climbs: List<Climb> = emptyList()`.
  * In distance domain (`!isTimeDomain` and `climbs.isNotEmpty()`):
    - Highlight climb ridge: draw accented line (3.5 dp) with category color across each climb segment.
    - Category badges: compute peak coordinate of each climb and render category badge pill with high-contrast text directly above the peak.

### Component 6: Route Details Map Markers & Interactive Breakdown (`RouteOnMapScreen.kt`)
* Forward `climbs = route?.climbs ?: emptyList()` to `MapDetailLayout` and `RouteSummaryHeader`.
* In `mapContent`: add climb start markers for each climb at `climb.startLatLng`.
* Add interactive `RouteClimbsBreakdownSection` displaying each climb's category chip, sequence index, starting km, length, average grade, and vertical gain.

### UI Consistency (Rule 23)
* **Reference screen / component**: [LiveClimbSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt), [RouteSummaryHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt), and [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt).
* **Reused components**: `ClimbCategoryChip`, `MetricItem`, `MapDetailLayout`, `ElevationProfile`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.onSurface`, `TTColor.RouteSelected`, `R.drawable.ic_ascent`, standard typography.
* **New one-off styles & justification**: None. Reuses established category color palette from `LiveClimbSheet.kt` (`HC`: `#880E4F`, `CAT_1`: `#C62828`, `CAT_2`: `#EF6C00`, `CAT_3`: `#F9A825`, `CAT_4`: `#2E7D32`, `UNCATEGORIZED`: `#757575`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2501`

### Step 2: Extend Data Models (`RouteWithPath` & `MapRoute`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt`
* **Changes**:
  - Add `val climbs: List<Climb> = emptyList()` to `RouteWithPath` and `MapRoute`.
  - Update `RouteWithPath.toMapRoute()` to copy `climbs = this.climbs`.

### Step 3: Enrich `RoutesRepository` with Climb Loading & Fallback
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/RoutesRepository.kt`
* **Changes**:
  - Load climbs from `ClimbsDatabaseManager.getClimbsForRoute(routeId)`.
  - On-the-fly fallback to `ClimbDetector.detectClimbs(path, routeId = routeId)` if database returns empty.

### Step 4: Expose Climb UI Helpers (`LiveClimbSheet.kt`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
* **Changes**:
  - Make `ClimbCategoryChip` public/internal.
  - Expose `getClimbCategoryColors(category: ClimbCategory)`.

### Step 5: Update `RouteSummaryHeader.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt`
* **Changes**:
  - Accept `climbs: List<Climb> = emptyList()`.
  - Render climb count MetricItem when `climbs.isNotEmpty()`.

### Step 6: Enhance `ElevationProfile.kt` & `MapDetailLayout.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Changes**:
  - Pass `climbs: List<Climb> = emptyList()` through `MapDetailLayout` into `ElevationProfile`.
  - In `ElevationProfile`, render category-colored ridge stroke and summit badge pills.

### Step 7: Update `RouteOnMapScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`
* **Changes**:
  - Pass `climbs = route?.climbs ?: emptyList()` to header and layout.
  - Add climb start markers to map.
  - Add interactive climb breakdown section.

### Step 8: Add 9-Language Localization
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales).
* **Changes**:
  - Add new climb metric, breakdown, and formatting strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Step 9: Author Targeted Unit, Contract, and Localization Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/RouteClimbsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbsUiContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteClimbsLocalizationTest.kt`

### Step 10: Run Targeted Tests & Clean-Room Regression
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.RouteClimbsRepositoryTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.*"`
* Command: `./gradlew testDebugUnitTest`
