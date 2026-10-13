# Stage 5: Walkthrough & Verification - ATT-2388: Increase Visibility and Visual Prominence of Climbs on Routes

**Ticket**: [ATT-2388](https://atrainingtracker.atlassian.net/browse/ATT-2388)  
**Sub-task**: [ATT-2503](https://atrainingtracker.atlassian.net/browse/ATT-2503) (`[Test]`)  
**Parent Epic**: [ATT-284](https://atrainingtracker.atlassian.net/browse/ATT-284) (*Navigation & Map View*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-274` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown*)  
**Test Mapping**: `TST-UI-234` (*Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown Verification*)  
**Branch**: `feature/ATT-2388`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Executive Summary & Verification Overview

Prior to ATT-2388, recognized climbs (`Climb` entities identified by `ClimbDetector`) were utilized exclusively during active navigation in the live `LiveClimbSheet` (ClimbPro cockpit bottom sheet, introduced in `ATT-1281` / `REQ-MAP-027`). During route inspection (`RouteOnMapScreen`, `RouteItem`, `ElevationProfile`), climbs were completely invisible:
1. `RouteWithPath` and `MapRoute` omitted the `climbs` list, discarding climb metadata when loading and passing routes to UI components.
2. In `ElevationProfile.kt`, the elevation profile rendered only uniform elevation/grade polyline curves, without highlighting climb segments or summit categories.
3. In `RouteSummaryHeader.kt`, metrics were limited to distance, elevation gain, and estimated duration, with zero indication of climb count.
4. In `RouteOnMapScreen.kt`, the map rendered route polyline without climb start points, and no breakdown section was available for inspecting individual climb metrics.

### Architectural Solution
* **Data Model & Extension Enrichment**: Added `val climbs: List<Climb> = emptyList()` to `RouteWithPath` (`RoutesDatabaseManager.kt`) and `MapRoute` (`MapModels.kt`), propagated automatically via `RouteWithPath.toMapRoute()`.
* **Repository Enrichment & Fallback Detection**: `RoutesRepository` enriches routes loaded via `allRoutes` / `refreshRoutes()` or queried by ID with climbs from `ClimbsDatabaseManager.getClimbsForRoute(routeId)`. For routes lacking pre-calculated climbs in the database, `RoutesRepository` falls back on-the-fly to `ClimbDetector.detectClimbs(route.path, routeId = routeId)`.
* **Elevation Profile Visual Prominence**: In distance domain (`!isTimeDomain`), `ElevationProfile` renders:
  1. **Accented Ridge Stroke**: A 3.5 dp accented stroke along each climb's ridge path styled with the climb category color (`LiveClimbSheet.getClimbCategoryColors`).
  2. **Summit Category Badges**: High-contrast rounded pill badges (e.g., "Cat 4", "Cat 3", "Cat 2", "Cat 1", "HC") positioned directly above each climb peak on the canvas with vertical stem pins.
* **Route Summary Header Metrics**: `RouteSummaryHeader` accepts `climbs` and renders a prominent climb count `MetricItem` whenever `climbs.isNotEmpty()`.
* **Map Markers & Dedicated Climb Breakdown**: `RouteOnMapScreen` renders climb start markers at `climb.startLatLng` using `ic_ascent` tinted with category colors, and adds `RouteClimbsBreakdownSection` under `analyticsContent` with expandable climb cards displaying category chips, start distance, length, average grade, and vertical gain.
* **9-Language Localization Parity**: Added string resources `routes_climb_count`, `routes_climbs_section_title`, `routes_climb_start_at`, `routes_climb_avg_grade` across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-274` | `TST-UI-234.1` | Model & Repository Unit Tests (`RouteClimbsRepositoryTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-UI-274` | `TST-UI-234.2` | UI & Profile Contract Tests (`RouteClimbsUiContractTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-UI-274` | `TST-UI-234.3` | 9-Language Localization Parity (`RouteClimbsLocalizationTest.kt`) | **PASSED** (100%) | `Verified` |
| `REQ-PRO-001` | `TST-UI-234.4` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit, Contract & Localization Suite
```text
./gradlew testDebugUnitTest \
  --tests com.atrainingtracker.trainingtracker.routes.RouteClimbsRepositoryTest \
  --tests com.atrainingtracker.trainingtracker.ui.routes.RouteClimbsUiContractTest \
  --tests com.atrainingtracker.trainingtracker.ui.routes.RouteClimbsLocalizationTest

BUILD SUCCESSFUL in 26s
32 actionable tasks: 3 executed, 29 up-to-date
```
* **`RouteClimbsRepositoryTest`**:
  - `testRouteWithPath_toMapRoute_propagatesClimbs`: Validates `RouteWithPath.toMapRoute()` transfers `climbs` list intact to `MapRoute`.
  - `testRoutesRepository_enrichesRouteWithClimbsFromDatabase`: Validates cached climbs from `ClimbsDatabaseManager` are enriched onto loaded routes.
  - `testRoutesRepository_fallsBackToClimbDetector_whenNoDatabaseClimbs`: Validates on-the-fly detection via `ClimbDetector.detectClimbs` when database has no pre-saved climbs.
* **`RouteClimbsUiContractTest`**:
  - `testElevationProfile_declaresClimbsParameter_andRidgeRendering`: Verifies `ElevationProfile` accepts `climbs` and renders ridge accent stroke and summit pills.
  - `testMapDetailLayout_forwardsClimbsToElevationProfile`: Verifies `MapDetailLayout` forwards `climbs` to `ElevationProfile`.
  - `testRouteSummaryHeader_declaresClimbsParameter`: Verifies `RouteSummaryHeader` accepts `climbs`.
  - `testRouteOnMapScreen_integratesClimbStartMarkers_andClimbBreakdownSection`: Verifies map climb start markers and interactive climb breakdown section integration.
* **`RouteClimbsLocalizationTest`**:
  - `testClimbStrings_presentAcrossAllNineLocales`: Verifies `routes_climb_count`, `routes_climbs_section_title`, `routes_climb_start_at`, `routes_climb_avg_grade` exist and are non-empty in all 9 locales.
  - `testClimbStrings_formatSpecifiersMatchAcrossLocales`: Verifies parameter specifiers (`%d`, `%1$d`, `%2$s`, `%3$.1f%%`, `%4$d`) match 100% across all 9 languages.

---

## 4. Hardware / Physical Verification (Pixel 10) & UI Consistency (Rule 23)

* **Physical Verification**:
  - Debug APK built and installed on physical test device (Google Pixel 10).
  - Verified route inspection screen renders without crashes or stutters.
  - Elevation profile clearly displays category-colored ridge strokes and summit badges.
  - Route summary header shows accurate climb counts.
  - Map displays climb start markers at correct coordinates.
  - Dedicated climb breakdown section lists all climbs with category chips and grade metrics.
* **Visual Consistency (Rule 23)**:
  * **Reference Components**: [LiveClimbSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt) category chips and [RouteSummaryHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt) metric cards.
  * **Tokens**: Colors (`getClimbCategoryColors`), typography tokens (`MaterialTheme.typography.labelSmall`, `titleSmall`), and elevation canvas styling conform to Material 3 and `docs/design_guidelines.md`.
  * **Checked against `docs/design_guidelines.md` §5**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
  * **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Full clean-room test suite executed with 100% pass rate.
2. **Backward Compatibility**: Routes created prior to ClimbPro database storage automatically resolve climbs via on-the-fly fallback detection with zero schema migrations required.
3. **Live ClimbPro Invariants Preserved**: Live cockpit `LiveClimbSheet` pacing state machine, distance and elevation math in `ClimbDetector`, and elevation zoom math remain 100% intact.
4. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-274`) and `docs/tests.md` (`TST-UI-234`) updated to `Verified`.
5. **Requirement Governance Validation**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.1` passed with zero violations.
6. **Parent Ticket Final Review**: Parent ticket [ATT-2388](https://atrainingtracker.atlassian.net/browse/ATT-2388) transitioned to `Final Review (Human)` and assigned to `human` for final sign-off.
