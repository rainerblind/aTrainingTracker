# Stage 2: Requirement & Test Specification - ATT-2388: Increase Visibility and Visual Prominence of Climbs on Routes

**Ticket**: [ATT-2388](https://atrainingtracker.atlassian.net/browse/ATT-2388)  
**Sub-task**: [ATT-2500](https://atrainingtracker.atlassian.net/browse/ATT-2500) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*)  
**Test Spec ID**: `TST-UI-234` (*Route Climbs Visual Prominence Verification*)  
**Branch**: `feature/ATT-2388`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-05  

---

## 1. Requirement Specification (`REQ-UI-274`)

### 1.1 Problem Statement & Rationale
During Sprint 2026-40.15 (`ATT-1281` / `REQ-MAP-027`), automated climb detection, SQLite persistence in `Climbs.db`, and the real-time Cockpit bottom sheet (`LiveClimbSheet.kt`) during live tracking were verified and deployed. However, athletes inspecting routes prior to or during workouts currently experience near-zero visibility into the climbs that comprise a route:
1. **Elevation Profile Blindness**: In `ElevationProfile.kt`, the elevation profile is rendered strictly as micro-gradient segments without indication of where recognized sustained ascents begin, peak, or end, displaying zero category ratings (Cat 4, Cat 3, Cat 2, Cat 1, HC).
2. **Route Map Polyline Uniformity**: On the route map (`RouteOnMapScreen` / `ATrainingTrackerMap`), the entire route polyline is rendered as a uniform single-color stroke with zero climb start markers or category badges.
3. **Route Cards & Overview Header Absence**: In `RouteSummaryHeader.kt` and `RouteItem.kt`, the summary metrics row displays only total distance and cumulative elevation gain, omitting climb counts and difficulty indicators.
4. **Missing Climb Breakdown**: In route details (`RouteOnMapScreen.kt`), there is no dedicated interactive breakdown of individual climbs displaying starting kilometer, length, average grade, vertical gain, and category.

### 1.2 Functional & Architectural Requirements
The system SHALL surface and accentuate recognized climbs on routes across data models, route summary cards, the elevation profile canvas, the route map, and dedicated route details breakdown (ATT-2388):
1. **Data Model & Extension Enrichment**:
   - `RouteWithPath` (`RoutesDatabaseManager.kt`) and `MapRoute` (`MapModels.kt`) SHALL include `val climbs: List<Climb> = emptyList()`.
   - `RouteWithPath.toMapRoute(isActiveNavigation: Boolean = false)` SHALL propagate `climbs = this.climbs`.
2. **Repository Enrichment & Backward-Compatible Fallback Detection**:
   - In `RoutesRepository`, routes loaded via `allRoutes` / `refreshRoutes()` or queried by ID SHALL be enriched with their associated `climbs: List<Climb>` loaded via `ClimbsDatabaseManager.getClimbsForRoute(routeId)`.
   - If `ClimbsDatabaseManager.getClimbsForRoute(routeId)` returns an empty list and `route.path.size >= 2`, `RoutesRepository` SHALL perform on-the-fly detection via `ClimbDetector.detectClimbs(path, routeId = routeId)` to ensure pre-existing routes display climbs seamlessly without requiring manual database migration.
3. **Route Summary Header Climb Prominence (`RouteSummaryHeader.kt`)**:
   - `RouteSummaryHeader` SHALL accept `climbs: List<Climb> = emptyList()`.
   - When `climbs.isNotEmpty()`, the header metrics row SHALL display a climb summary metric item indicating total climbs on the route (e.g. `MetricItem` with `R.drawable.ic_ascent` and climb count).
4. **Elevation Profile Climb Ridge Highlighting & Category Badges (`ElevationProfile.kt`)**:
   - `ElevationProfile` and `MapDetailLayout` SHALL accept `climbs: List<Climb> = emptyList()`.
   - In distance domain (`!isTimeDomain`), for each climb in `climbs`:
     - (a) *Ridge Accent Stroke*: The system SHALL render an accented stroke along the climb's ridge (width 3.5 dp) using the climb category's distinct color.
     - (b) *Summit Category Badge*: The system SHALL draw a category badge pill (e.g. "Cat 4", "Cat 3", "Cat 2", "Cat 1", "HC") directly above each climb peak on the elevation profile canvas.
5. **Map Polyline Markers & Interactive Climb Breakdown (`RouteOnMapScreen.kt`)**:
   - When `route != null && route.climbs.isNotEmpty()`:
     - (a) *Map Climb Start Markers*: The system SHALL render prominent climb start markers at `climb.startLatLng` on the route map.
     - (b) *Climb Breakdown Section*: The system SHALL provide a dedicated climb section in route inspection displaying each climb's category chip, sequential index, start kilometer along the route, length, average grade, and vertical gain.
6. **9-Language Localization Parity**:
   - All newly introduced user-facing string resources SHALL be translated and maintained with 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. **Preservation of System Invariants**:
   - Mathematical climb detection parameters in `ClimbDetector` (500m min distance, 3% min grade, 20m min gain), live Cockpit ClimbPro sheet (`LiveClimbSheet.kt`), zoom toolbar ergonomics, scrubbing synchronization, and 100% clean-room test suite pass rate MUST NOT be compromised.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines and extends `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*) and `REQ-UI-267` / `REQ-UI-273` (*Routes & Segments Elevation Profile and Map Detail Layout*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.14` (`ATT-1281`, commit `d5c90d81`) introduced `REQ-MAP-027`, establishing `ClimbDetector`, `ClimbsDatabaseManager`, and `LiveClimbSheet.kt` for live tracking. Sprint `2026-41.1` (`ATT-2386`, commit `5e07f332`) established `REQ-UI-273` for bottom-anchored elevation profiles in routes.
3. *Root Reason for Existing Formulation*: `REQ-MAP-027` focused strictly on active ride pacing and live Cockpit HUD bottom sheets. Route inspection screens (`RouteOnMapScreen`, `RouteItem`, `ElevationProfile`) were decoupled from `ClimbsDatabaseManager` during initial ClimbPro rollout to bound sprint scope.
4. *Preservation of Core Invariants*: Climb detection mathematics in `ClimbDetector`, live tracking state machine in `LiveClimbsRepository`, zoom math in `ElevationProfileZoomMath`, and 100% clean-room test suite pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Climb Propagation to Route Models)**:
  * *Given* a route with detected climbs in `Climbs.db` or a trackpoint path with qualifying ascents,
  * *When* loaded by `RoutesRepository` and converted via `toMapRoute()`,
  * *Then* `RouteWithPath.climbs` and `MapRoute.climbs` SHALL contain the complete list of climbs with accurate distance coordinates.
* **AC-2 (Elevation Profile Visual Prominence)**:
  * *Given* an elevation profile rendered in `RouteOnMapScreen` or `ElevationProfile` with `climbs.isNotEmpty()`,
  * *When* drawn on canvas in distance mode,
  * *Then* each climb's ridge SHALL be accented with a 3.5 dp stroke in the category color, and high-contrast category badge pills SHALL appear directly above each summit.
* **AC-3 (Route Summary Header Metrics)**:
  * *Given* a route header rendered in `RouteSummaryHeader`,
  * *When* `climbs.isNotEmpty()`,
  * *Then* the metrics row SHALL display a climb metric indicating the number of climbs on the route.
* **AC-4 (Map Markers & Interactive Breakdown)**:
  * *Given* an athlete inspecting a route in `RouteOnMapScreen`,
  * *When* climbs are present,
  * *Then* climb start markers SHALL be placed on the map at each climb's starting coordinate, and a structured breakdown of each climb SHALL be presented.
* **AC-5 (Backward-Compatible On-the-Fly Detection)**:
  * *Given* an older route created prior to `ATT-1281` with zero records in `Climbs.db`,
  * *When* loaded by `RoutesRepository`,
  * *Then* `ClimbDetector.detectClimbs` SHALL be invoked on-the-fly and populate `climbs` without data loss or error.
* **AC-6 (9-Language Parity)**:
  * *Given* all newly introduced climb strings,
  * *When* verified across all 9 localized resource directories,
  * *Then* zero missing entries or token mismatches SHALL exist.

---

## 2. Test Specification (`TST-UI-234`)

### Test Case 1: `RouteClimbsRepositoryTest` (`TST-UI-234.1`)
* **Scope**: Unit & Data Model Tests
* **Test Goals**:
  1. Verify `RouteWithPath` and `MapRoute` hold `climbs: List<Climb> = emptyList()`.
  2. Verify `RouteWithPath.toMapRoute()` propagates `climbs` to `MapRoute`.
  3. Verify `RoutesRepository` enriches `allRoutes` with climbs queried from `ClimbsDatabaseManager`.
  4. Verify fallback on-the-fly detection via `ClimbDetector.detectClimbs` when `ClimbsDatabaseManager` has zero cached climbs for a route.
* **Expected Result**: PASS.

### Test Case 2: `RouteClimbsUiContractTest` (`TST-UI-234.2`)
* **Scope**: Architectural UI Contract Tests
* **Test Goals**:
  1. Verify `ElevationProfile.kt` accepts `climbs: List<Climb> = emptyList()`.
  2. Verify `MapDetailLayout.kt` accepts `climbs: List<Climb> = emptyList()` and forwards it to `ElevationProfile`.
  3. Verify `RouteSummaryHeader.kt` accepts `climbs: List<Climb> = emptyList()` and renders a climb metric item when `climbs.isNotEmpty()`.
  4. Verify `RouteOnMapScreen.kt` forwards `climbs = route?.climbs ?: emptyList()` to `MapDetailLayout` and places climb start markers on the map.
* **Expected Result**: PASS.

### Test Case 3: `RouteClimbsLocalizationTest` (`TST-UI-234.3`)
* **Scope**: 9-Language Localization Audit
* **Test Goals**:
  1. Verify all new climb string resources exist and are non-empty across all 9 locales: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  2. Verify zero AAPT2 entity violations (`&#10;`) and identical formatting specifier counts (`%d`, `%s`, etc.).
* **Expected Result**: PASS.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-234.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Full suite verification with 100% pass rate across all modules.
* **Expected Result**: 100% PASS.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-234.1` | Unit / Repository | `RouteClimbsRepositoryTest` | `REQ-UI-274` (Clauses 1, 2) | Specified |
| `TST-UI-234.2` | Contract / UI | `RouteClimbsUiContractTest` | `REQ-UI-274` (Clauses 3, 4, 5) | Specified |
| `TST-UI-234.3` | Localization | `RouteClimbsLocalizationTest` | `REQ-UI-274` (Clause 6) | Specified |
| `TST-UI-234.4` | Full Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-274` | Specified |
