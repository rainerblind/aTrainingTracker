# Stage 2: Requirement & Test Specification - ATT-58: Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points (Rework Cycle 2)

**Ticket**: [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)  
**Sub-task**: [ATT-2417](https://rainerblind.atlassian.net/browse/ATT-2417) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-026` (*Support Waypoints, POIs and TCX Course Points*)  
**Test Spec ID**: `TST-MAP-028`  
**Branch**: `feature/ATT-58`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-MAP-026)

### 1.1 Problem Statement & Rationale

During physical device evaluation of Cycle 1 on Google Pixel 10, the PO tested importing 5 real-world GPX routes from `/home/rainer/Downloads/Schwaebische_Alb` (containing a total of 29 `<wpt>` waypoints for viewpoints, summits, shelters, and picnic areas). The routes imported successfully, but zero waypoints appeared on the map.

Forensic analysis confirmed that while the parsing engine and SQLite schema v10 (`route_waypoints`) functioned properly, the UI save wiring in [GpxImportActivity.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/GpxImportActivity.kt) dropped `state.waypoints` when invoking `viewModel.saveRoute(updatedSummary, state.points)`, silently saving 0 waypoints to the database. Furthermore, `RouteItem.kt` thumbnails omitted waypoints, `MapLayers.kt` suppressed Google Maps InfoWindows on marker clicks, and `WaypointType.fromGpx` ignored descriptions (`<desc>` / `<cmt>`).

Rework Cycle 2 amends the requirement and establishes end-to-end integration specifications across the UI presentation layer, map renderers, and real-world test fixtures.

### 1.2 Functional & Architectural Requirements

1. **End-to-End GPX Waypoint Persistence (`GpxImportActivity.kt` / `GpxImportViewModel.kt`)**:
   - `GpxImportActivity.kt` SHALL pass `state.waypoints` to `viewModel.saveRoute(updatedSummary, state.points, state.waypoints)`.
   - `GpxImportViewModel.saveRoute` SHALL pass non-empty waypoints to `RoutesRepository.insertRoute(summary, points, waypoints)` so all extracted waypoints are persisted into table `route_waypoints` in `Routes.db`.
2. **Route List Thumbnail Waypoint Rendering (`RouteItem.kt` / `RouteList.kt`)**:
   - `RouteItem.kt` SHALL accept `waypoints: List<RouteWaypoint> = emptyList()` and forward them to `PathPreviewMap(MapRoute(..., waypoints = waypoints))`.
   - `RouteList.kt` SHALL extract `route.waypoints` from `RouteWithPath` and pass them to `RouteItem`.
3. **Interactive Waypoint Map Markers & InfoWindow (`MapLayers.kt` / `RouteOnMapScreen.kt`)**:
   - `RouteWaypointsLayer` in `MapLayers.kt` SHALL allow the Google Maps native InfoWindow to display the waypoint `title` and `snippet` (description and altitude), or trigger an interactive callback without dead-end suppression.
4. **Enhanced Heuristic Classification (`RouteWaypoint.kt` / `GpxRouteImporter.kt`)**:
   - `WaypointType.fromGpx` SHALL accept `(sym: String?, type: String?, name: String?, desc: String? = null)`.
   - Heuristics SHALL classify real-world outdoor terms:
     - `POI_BENCH`: `bench`, `bank`, `rast`, `picnic`, `picknick`, `unterstand`, `sitzgelegenheit`.
     - `POI_FOOD`: `food`, `cafe`, `restaurant`, `bäcker`, `essen`, `gasthof`, `hütte`, `grillplatz`, `schutzhütte`.
     - `POI_VIEWPOINT`: `view`, `aussicht`, `panorama`, `lookout`, `blick`.
     - `POI_SUMMIT`: `summit`, `gipfel`, `peak`, `pass`, `kreuz`.
     - `POI_WATER`: `water`, `wasser`, `quelle`, `fountain`, `trinkwasser`.
5. **TCX Course Import Support (`GpxImportViewModel.kt`)**:
   - The route import workflow SHALL support Garmin TCX course files (`.tcx`) by detecting `.tcx` extensions or `<Course>` tags and delegating to `TcxCourseParser.parse(...)`.
6. **Mandatory Real-World Test Fixtures (`SchwaebischeAlbWaypointIntegrationTest.kt`)**:
   - The test suite SHALL execute against the 5 real-world GPX files in `/home/rainer/Downloads/Schwaebische_Alb`, validating that all 29 waypoints are parsed, classified, projected, and persisted.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (GPX Import Waypoint Persistence)**:
  * *Given* a GPX file containing trackpoints and waypoints (e.g. `2026-03-12_2823712016_GPX Download_ Gipfelkreuz auf dem Jusi...`)
  * *When* imported via `GpxImportActivity` and saved by the user
  * *Then* `RoutesDatabaseManager.getWaypointsForRoute(routeId)` returns all 9 waypoints with non-null coordinates, names, and semantic types.

* **Criterion 2 (Route List Thumbnail Waypoints)**:
  * *Given* a saved route with 9 waypoints in `Routes.db`
  * *When* browsing the route cards in `RoutesScreen`
  * *Then* `RouteItem` renders the thumbnail map preview with waypoint markers along the polyline.

* **Criterion 3 (Map Waypoint Detail & InfoWindow)**:
  * *Given* an imported route displayed in `RouteOnMapScreen`
  * *When* the user views or taps a waypoint marker (e.g. *Gipfelkreuz auf dem Jusi*)
  * *Then* the marker renders with `ic_poi_summit` and displays its title and description.

* **Criterion 4 (Heuristic Description Parsing)**:
  * *Given* a GPX waypoint with `name="Start Tour 13"` and `desc="Unterstand"`
  * *When* parsed by `GpxRouteImporter`
  * *Then* `WaypointType.fromGpx` inspects `desc` and classifies it as `POI_BENCH`.

* **Criterion 5 (TCX Course File Import)**:
  * *Given* a `.tcx` course file containing `<CoursePoint>` tags
  * *When* opened in `GpxImportActivity`
  * *Then* the course points are extracted and saved as route waypoints.

* **Criterion 6 (Full Suite Clean-Room Regression)**:
  * *Given* the entire application test suite
  * *When* running `./gradlew testDebugUnitTest`
  * *Then* 100% of tests pass with 0 errors and 0 regressions.

### 1.4 System Invariants

1. **Schema v10 Stability**: `route_waypoints` SQLite schema, table indices, and foreign key cascade deletion (`ON DELETE CASCADE`) must remain completely backwards-compatible.
2. **DEM Enrichment Uncompromised**: `GpxRouteImporter` DEM elevation querying (`REQ-MAP-025`) must continue to enrich coordinates when elevation data is missing.
3. **Thread Safety**: All database and parser operations must execute under `Dispatchers.IO`.

---

## 2. Test Specification (TST-MAP-028)

### Test Case 1: `testSaveRoute_withWaypoints_persistsWaypointsToRepository` (`TST-MAP-028.1`)
* **Scope**: ViewModel Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/GpxImportViewModelTest.kt`
* **Preconditions**: Mock `RoutesRepository` and `GpxRouteImporter`.
* **Action**: Invoke `viewModel.saveRoute(summary, points, waypoints)` where `waypoints` contains 9 test waypoints.
* **Expected Result**: `mockRepository.insertRoute(summary, points, waypoints)` is invoked with the exact 9 waypoints, and `uiState` transitions to `ImportState.Success`.

### Test Case 2: `testRouteItem_withWaypoints_propagatesToMapRoute` (`TST-MAP-028.2`)
* **Scope**: Architectural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItemWaypointContractTest.kt`
* **Preconditions**: RouteWithPath with 9 waypoints.
* **Action**: Inspect parameter mappings between `RouteList`, `RouteItem`, and `PathPreviewMap`.
* **Expected Result**: `RouteItem` accepts `waypoints: List<RouteWaypoint>` and assigns `waypoints` to `MapRoute.waypoints`.

### Test Case 3: `testWaypointType_fromGpx_withDescriptionAndExtendedKeywords` (`TST-MAP-028.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/WaypointTypeClassificationTest.kt`
* **Preconditions**: None.
* **Action**: Evaluate `WaypointType.fromGpx` across diverse test tuples:
  - `(sym = "Circle, Red", type = "user", name = "Start Tour 13", desc = "Unterstand")` -> `POI_BENCH`
  - `(sym = "Picnic Area", type = null, name = "Holz-Sitzgelegenheit am Gansteichweg")` -> `POI_BENCH`
  - `(sym = "Fishing Hot Spot Facility", type = null, name = "Schutzhütte und Grillplatz auf dem Jusi-Gipfel")` -> `POI_FOOD`
  - `(sym = "Flag, Blue", type = null, name = "Panoramablick vom Jusi")` -> `POI_VIEWPOINT`
  - `(sym = "Summit", type = null, name = "Gipfelkreuz auf dem Jusi")` -> `POI_SUMMIT`
  - `(sym = "Flag, Blue", type = null, name = "Wanderparkplatz Raupental")` -> `GENERIC`
* **Expected Result**: Assert exact matching enum types for each input.

### Test Case 4: `testSchwaebischeAlbRealFiles_allWaypointsExtractedAndClassified` (`TST-MAP-028.4`)
* **Scope**: Real-World Integration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/SchwaebischeAlbWaypointIntegrationTest.kt`
* **Preconditions**: Real-world GPX files in `/home/rainer/Downloads/Schwaebische_Alb`.
* **Action**: Parse each of the 5 GPX files using `GpxRouteImporter` with mocked XML parser and calculate projected distances.
* **Expected Result**:
  - `Gipfelkreuz auf dem Jusi...`: exactly 9 waypoints extracted, summit cross classified as `POI_SUMMIT`, viewpoints as `POI_VIEWPOINT`.
  - `Wentaler Felsenmeer...`: exactly 9 waypoints extracted, picnic area classified as `POI_BENCH`.
  - `Aussichtspunkt Floriansberg...`: exactly 9 waypoints extracted.
  - `Nebelhoehle...`: exactly 1 waypoint extracted, classified as `POI_BENCH` via `desc="Unterstand"`.
  - `Uracher Wasserfall...`: exactly 1 waypoint extracted.
  - Total extracted waypoints = 29.
  - All waypoints have valid LatLng and monotonic projected `distanceFromStart`.

### Test Case 5: `testTcxCourseImport_extractsWaypointsAndPoints` (`TST-MAP-028.5`)
* **Scope**: Parser Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TcxCourseParserTest.kt`
* **Preconditions**: Sample TCX course XML with `<CoursePoint>` entries.
* **Action**: Call `TcxCourseParser.parse(inputStream)`.
* **Expected Result**: Returns `RouteImportResult` with trackpoints, distance calculation, and mapped `RouteWaypoint` items.

### Test Case 6: 9-Language Localization & Specifier Audit (`TST-MAP-028.6`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: String resources in all 9 supported locales:
  - EN (`values/`), DE (`values-de/`), ES (`values-es/`), FR (`values-fr/`), IT (`values-it/`), JA (`values-ja/`), NL (`values-nl/`), PL (`values-pl/`), PT (`values-pt/`).
* **Expected Result**: 100% parity across all 9 locales, zero missing translation keys, zero format specifier mismatches.

### Test Case 7: Clean-Room Full Suite Regression (`TST-MAP-028.7`)
* **Scope**: Clean-Room Full Suite Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate (>1,760 tests), 0 failures, 0 errors.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-028.1` | ViewModel Unit | `GpxImportViewModel.saveRoute` | `REQ-MAP-026` (1) | Specified |
| `TST-MAP-028.2` | Contract / UI | `RouteItem`, `RouteList`, `PathPreviewMap` | `REQ-MAP-026` (2) | Specified |
| `TST-MAP-028.3` | Classification Unit | `WaypointType.fromGpx` | `REQ-MAP-026` (4) | Specified |
| `TST-MAP-028.4` | Real-World Integration | `GpxRouteImporter` (Schwäbische Alb 5 files) | `REQ-MAP-026` (1, 6) | Specified |
| `TST-MAP-028.5` | Parser Unit | `TcxCourseParser.parse` | `REQ-MAP-026` (5) | Specified |
| `TST-MAP-028.6` | Localization | `TranslationParityTest` | `REQ-MAP-026` | Specified |
| `TST-MAP-028.7` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
