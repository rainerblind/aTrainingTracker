# Stage 2 Test Specification: ATT-58 - Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points

**Ticket**: [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)  
**Sub-task**: [ATT-2288](https://rainerblind.atlassian.net/browse/ATT-2288) (`[Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-58`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Overview & Verification Strategy

This test specification defines the verification procedures for `REQ-MAP-026` under ticket [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58).

The verification strategy guarantees that:
1. **GPX Waypoint Extraction & Heuristic Classification**:
   - `GpxRouteImporter` extracts `<wpt>` elements from parsed GPX files (`parsedGpx.wayPoints`).
   - Landmark symbols, category types, and names are heuristically classified into standard `WaypointType` enums: Water (`POI_WATER`), Benches (`POI_BENCH`), Summits/Passes (`POI_SUMMIT`), Food/Cafes (`POI_FOOD`), Viewpoints (`POI_VIEWPOINT`), Hazards (`POI_DANGER`), First Aid (`POI_FIRST_AID`), and Generic landmarks (`GENERIC`).
2. **TCX Course & Course Point Streaming Parsing**:
   - A dedicated, lightweight `TcxCourseParser` parses Garmin TCX files (`.tcx`) containing `<Courses><Course>`, extracting trackpoints and `<CoursePoint>` elements.
   - TCX `PointType` values (`Water`, `Food`, `Summit`, `Valley`, `Danger`, `First Aid`, `Left`, `Right`, `Straight`, `Generic`) are mapped deterministically to corresponding `WaypointType` enums.
3. **Distance Along Route Polyline Projection**:
   - For each imported waypoint $(lat_w, lng_w)$, the importer calculates its orthogonal or nearest projection onto the route's polyline segments and determines the cumulative `distance_from_start` (in meters).
   - Distance values are verified to be monotonic and clamped to the route's total distance interval $[0, D_{\text{total}}]$.
4. **Database Schema v10 & SQLite Foreign Key Cascade Deletion**:
   - `RoutesDbHelper` upgrades `DB_VERSION` from 9 to 10 by creating table `route_waypoints` with an index on `route_id` and foreign key constraint `FOREIGN KEY(route_id) REFERENCES routes(id) ON DELETE CASCADE`.
   - Deleting a route from `RoutesDatabaseManager` automatically cascades and deletes all associated waypoint records from SQLite.
   - Backwards compatibility is preserved: existing routes lacking waypoints load seamlessly with `RouteWithPath.waypoints = emptyList()`.
5. **Map Layer Marker Rendering & Interactive POI Inspection**:
   - `ATrainingTrackerMap` renders vector marker icons for waypoints above the route polyline ($Z \ge 50\text{f}$).
   - Tapping a waypoint marker displays an interactive detail card showing name, category, altitude, and distance from start.
6. **9-Language Localization Audit**:
   - All newly introduced waypoint types, category descriptions, and cue sheet strings are verified across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.
7. **Clean-Room Full Suite Regression Invariant**:
   - Full regression suite execution (`./gradlew testDebugUnitTest`) with 100% pass rate and zero regressions.

---

## 2. Requirement Traceability Matrix

| Requirement Clause | Test Specification ID | Test Classes / Suites | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-026` (1: GPX Waypoint Parsing & Classification) | `TST-MAP-028` (Group 1) | `GpxRouteImporterWaypointTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-026` (2: TCX Course & Course Point Parsing) | `TST-MAP-028` (Group 2) | `TcxCourseParserTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-026` (3: Distance Along Route Projection) | `TST-MAP-028` (Group 3) | `WaypointDistanceCalculatorTest.kt` | JUnit 4 Unit Test | Defined |
| `REQ-MAP-026` (4: Schema v10 & Cascade Deletion) | `TST-MAP-028` (Group 4) | `RoutesDatabaseManagerWaypointTest.kt` | Robolectric / JUnit 4 SQLite | Defined |
| `REQ-MAP-026` (5: Map Layer Marker & Click Contract) | `TST-MAP-028` (Group 5) | `RouteWaypointLayerTest.kt` | Compose / JUnit 4 | Defined |
| `REQ-MAP-026` (6: 9-Language Localization Parity) | `TST-MAP-028` (Group 6) | `TranslationParityTest.kt` | Resource Parity Test | Defined |
| `REQ-ALL` (Clean-Room Full Suite Regression) | `TST-MAP-028` (Group 7) | Full `./gradlew testDebugUnitTest` | CI Suite Execution | Defined |

---

## 3. Concrete Test Cases (`TST-MAP-028`)

### Group 1: GPX Waypoint Extraction & Heuristic Classification
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.GpxRouteImporterWaypointTest`
* **Test Cases**:
  1. `testImportRoute_withGpxWaypoints_extractsAndClassifiesPoiTypes`:
     - Given a GPX file containing trackpoints and 4 `<wpt>` elements:
       - `<wpt lat="48.1" lon="11.5"><name>Trinkbrunnen</name><sym>Water</sym></wpt>`
       - `<wpt lat="48.2" lon="11.6"><name>Aussichtsbank</name><sym>Bench</sym></wpt>`
       - `<wpt lat="48.3" lon="11.7"><name>Zugspitze</name><sym>Summit</sym><ele>2962.0</ele></wpt>`
       - `<wpt lat="48.4" lon="11.8"><name>Gasthof Hirsch</name><sym>Restaurant</sym></wpt>`
     - When `GpxRouteImporter.importRouteFromGpx(uri)` parses the file.
     - Asserts 4 `RouteWaypoint` objects are extracted.
     - Asserts types are classified as `POI_WATER`, `POI_BENCH`, `POI_SUMMIT`, and `POI_FOOD`.
     - Asserts altitude `2962.0` is preserved on the summit waypoint.
  2. `testImportRoute_withGpxLackingWaypoints_returnsEmptyWaypointsList`:
     - Given a standard GPX file with `<trk>` trackpoints but zero `<wpt>` tags.
     - When parsed by `GpxRouteImporter`.
     - Asserts `waypoints` list in the returned result is `emptyList()`.
     - Asserts normal route polyline and distance calculation execute without error.

### Group 2: TCX Course & Course Point Extraction
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.TcxCourseParserTest`
* **Test Cases**:
  1. `testParseTcxCourse_extractsTrackpointsAndCoursePoints`:
     - Given a valid Garmin TCX course file with `<Courses><Course>` containing trackpoints and 3 `<CoursePoint>` elements:
       - `<CoursePoint><Name>Turn Left</Name><PointType>Left</PointType><Position><LatitudeDegrees>47.1</LatitudeDegrees><LongitudeDegrees>10.1</LongitudeDegrees></Position></CoursePoint>`
       - `<CoursePoint><Name>Water Refill</Name><PointType>Water</PointType><Position><LatitudeDegrees>47.2</LatitudeDegrees><LongitudeDegrees>10.2</LongitudeDegrees></Position></CoursePoint>`
       - `<CoursePoint><Name>Summit Pass</Name><PointType>Summit</PointType><AltitudeMeters>1800.0</AltitudeMeters><Position><LatitudeDegrees>47.3</LatitudeDegrees><LongitudeDegrees>10.3</LongitudeDegrees></Position></CoursePoint>`
     - When `TcxCourseParser.parse(inputStream)` is executed.
     - Asserts route metadata (name, total distance, coordinates) are parsed into `RouteSummary` and `List<PathPoint>`.
     - Asserts course points are mapped to `TURN_LEFT`, `POI_WATER`, and `POI_SUMMIT` with accurate coordinates.
  2. `testParseTcxCourse_whenNoCoursePointsPresent_returnsRouteWithZeroWaypoints`:
     - Given a TCX course file without `<CoursePoint>` tags.
     - When parsed by `TcxCourseParser`.
     - Asserts route points are extracted, and `waypoints` list is empty.

### Group 3: Distance Along Route Polyline Projection
* **Test Class**: `com.atrainingtracker.trainingtracker.routes.WaypointDistanceCalculatorTest`
* **Test Cases**:
  1. `testCalculateWaypointDistance_projectsAccuratelyOntoStraightPolyline`:
     - Given a straight route from $(0.0, 0.0)$ ($d=0\text{m}$) to $(0.0, 0.01)$ ($d=1113.2\text{m}$).
     - Given a waypoint located halfway along the track at $(0.0001, 0.005)$ (displaced 11m north of the midpoint).
     - When `WaypointDistanceCalculator.projectWaypoints(waypoints, pathPoints)` is executed.
     - Asserts calculated `distanceFromStart` is $\approx 556.6\text{m} \pm 5\text{m}$ (halfway along the segment).
  2. `testCalculateWaypointDistance_clampsMonotonicallyWithinTotalDistance`:
     - Given a multi-segment zigzag polyline with total distance $15,000\text{m}$.
     - Given waypoints positioned near the start, middle, and beyond the end of the route.
     - Asserts all waypoints receive `0.0 <= distanceFromStart <= 15000.0`.
     - Asserts waypoints ordered along travel direction have monotonically increasing `distanceFromStart`.

### Group 4: Database Schema v10 & Foreign Key Cascade Deletion
* **Test Class**: `com.atrainingtracker.trainingtracker.database.RoutesDatabaseManagerWaypointTest`
* **Test Cases**:
  1. `testDatabaseUpgrade_fromV9ToV10_createsRouteWaypointsTable`:
     - Given a SQLite database initialized at Version 9.
     - When `RoutesDbHelper.onUpgrade(db, 9, 10)` is invoked.
     - Asserts table `route_waypoints` exists with required columns: `id`, `route_id`, `lat`, `lng`, `altitude`, `name`, `description`, `type`, `distance_from_start`.
     - Asserts index `idx_route_waypoints_route_id` is created.
  2. `testInsertAndQueryWaypoints_persistsAndRetrievesDataAccurately`:
     - Given a route inserted into `Routes.db`.
     - When inserting 3 waypoints (`POI_WATER`, `POI_SUMMIT`, `TURN_LEFT`) via `insertWaypoints(routeId, waypoints)`.
     - When querying via `getWaypointsForRoute(routeId)`.
     - Asserts 3 waypoints are returned with matching coordinates, names, types, altitudes, and distances.
  3. `testDeleteRoute_cascadesAndDeletesAssociatedWaypoints`:
     - Given a route with 5 waypoints in `route_waypoints`.
     - When `deleteRoute(routeId)` is called.
     - Asserts `routes` row is removed.
     - Asserts `route_points` rows are removed.
     - Asserts `route_waypoints` rows for `routeId` are automatically purged by SQLite foreign key cascade.

### Group 5: Map Layer Marker Rendering & Interactive POI Inspection
* **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.RouteWaypointLayerTest`
* **Test Cases**:
  1. `testWaypointMarker_resolvesCorrectIconAndZIndex`:
     - Given `RouteWaypoint` with type `POI_WATER`.
     - Asserts mapped `LocationMarker` receives `ic_poi_water` icon and $Z \ge 50\text{f}$.
     - Asserts `POI_BENCH` receives `ic_poi_bench`, `POI_SUMMIT` receives `ic_poi_summit`.
  2. `testWaypointMarker_onMarkerClick_dispatchesCallbackWithWaypointDetails`:
     - Given a waypoint marker rendered on the map.
     - When tapped by the user.
     - Asserts `onWaypointClick(waypoint)` callback is triggered with full waypoint metadata.

### Group 6: 9-Language Localization Audit
* **Test Class**: `com.atrainingtracker.trainingtracker.TranslationParityTest`
* **Test Cases**:
  1. `testWaypointStrings_existAcrossAllLocales`:
     - Asserts strings `waypoint_type_bench`, `waypoint_type_water`, `waypoint_type_summit`, `waypoint_type_food`, `waypoint_type_viewpoint`, `waypoint_type_danger`, `waypoint_type_first_aid`, `waypoint_type_turn_left`, `waypoint_type_turn_right`, `waypoint_type_turn_straight`, `waypoint_type_generic`, `route_cue_sheet_title`, `route_waypoints_header` exist across:
       - `values/strings.xml` (EN)
       - `values-de/strings.xml` (DE)
       - `values-es/strings.xml` (ES)
       - `values-fr/strings.xml` (FR, with escaped apostrophes `\'`)
       - `values-it/strings.xml` (IT)
       - `values-ja/strings.xml` (JA)
       - `values-nl/strings.xml` (NL)
       - `values-pl/strings.xml` (PL)
       - `values-pt/strings.xml` (PT)
     - Asserts 0 missing translations.

### Group 7: Clean-Room Full Suite Regression Invariant
* **Test Command**: `./gradlew testDebugUnitTest`
* **Assertion**: 100% pass rate across the full test suite with 0 failures and 0 skipped.

---

## 4. Acceptance Criteria (Given-When-Then)

```gherkin
Scenario: GPX file with water and summit waypoints is imported
  Given a GPX route file containing trackpoints and <wpt> tags for "Trinkwasser" and "Alpspitzgipfel"
  When the athlete imports the GPX file via GpxRouteImporter
  Then table route_waypoints stores both waypoints with types POI_WATER and POI_SUMMIT
  And each waypoint is assigned a valid distanceFromStart calculated along the route polyline

Scenario: TCX course file with turn cues is imported
  Given a Garmin TCX course file containing <CoursePoint> elements for "Left" and "Danger"
  When imported via TcxCourseParser
  Then the waypoints are persisted with types TURN_LEFT and POI_DANGER

Scenario: Route deletion cascades to waypoints
  Given an existing route in Routes.db with 4 associated waypoints
  When the user deletes the route
  Then SQLite foreign key cascade purges all 4 records from route_waypoints immediately

Scenario: Waypoint rendering on route map
  Given an active or inspected route with waypoints
  When displayed in ATrainingTrackerMap
  Then waypoint markers render above the route polyline (Z >= 50f) with distinct visual icons
  And clicking a waypoint marker displays an info card with name, category, and remaining distance
```
