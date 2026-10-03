# Stage 3 Implementation Plan: ATT-58 - Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points

**Ticket**: [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)  
**Sub-task**: [ATT-2289](https://rainerblind.atlassian.net/browse/ATT-2289) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-58`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Design (SWE.2)

```
+-----------------------------------------------------------------------------------+
| UI & Map Presentation Layer (Jetpack Compose & Google Maps)                       |
|                                                                                   |
|  [ATrainingTrackerMap] / [MapLayers.kt]                                           |
|    - RouteWaypointLayer: Renders vector markers for waypoints on top of polyline  |
|      (Z >= 50f, cached BitmapDescriptor icons)                                    |
|    - On waypoint marker click: Displays interactive POI info tooltip/card        |
|      (Name, Category, Altitude, Distance from Start)                              |
|                                                                                   |
|  [RouteDetailScreen] / [EditRouteScreen]                                          |
|    - RouteCueSheet: Displays sequential list of upcoming waypoints sorted by     |
|      distance_from_start                                                          |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Importers & Parser Engine                                                         |
|                                                                                   |
|  [GpxRouteImporter]                                                               |
|    - Extracts <trkpt> track points                                                |
|    - Extracts <wpt> waypoints via parsedGpx.wayPoints                             |
|    - Classifies sym/type/name -> WaypointType                                    |
|                                                                                   |
|  [TcxCourseParser]                                                                |
|    - Streams <Courses><Course> via XmlPullParser                                  |
|    - Extracts <Trackpoint> -> List<PathPoint>                                     |
|    - Extracts <CoursePoint> -> List<RouteWaypoint>                                |
|    - Maps TCX PointType -> WaypointType                                          |
|                                                                                   |
|  [WaypointDistanceCalculator]                                                     |
|    - Projects waypoints onto polyline segments                                    |
|    - Calculates distance_from_start (meters)                                      |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Domain Models & Classification                                                    |
|                                                                                   |
|  [RouteWaypoint]                                                                  |
|    (id, routeId, latLng, altitude, name, description, type, distanceFromStart)     |
|                                                                                   |
|  [WaypointType]                                                                   |
|    - POI_WATER, POI_BENCH, POI_SUMMIT, POI_FOOD, POI_VIEWPOINT, POI_DANGER,        |
|      POI_FIRST_AID, TURN_LEFT, TURN_RIGHT, TURN_STRAIGHT, GENERIC                 |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
| Persistence Layer (SQLite / RoutesDatabaseManager)                                |
|                                                                                   |
|  [RoutesDbHelper] (DB_VERSION = 10)                                                |
|    - Table: route_waypoints                                                       |
|      (id, route_id, lat, lng, altitude, name, description, type,                  |
|       distance_from_start, FOREIGN KEY(route_id) REFERENCES routes(id)            |
|       ON DELETE CASCADE)                                                          |
|    - Index: idx_route_waypoints_route_id                                          |
|                                                                                   |
|  [RoutesDatabaseManager]                                                          |
|    - insertWaypoints(routeId, waypoints)                                          |
|    - getWaypointsForRoute(routeId): List<RouteWaypoint>                           |
|    - deleteWaypointsForRoute(routeId): Int                                        |
|    - RouteWithPath(summary, path, waypoints = emptyList())                        |
+-----------------------------------------------------------------------------------+
```

---

## 2. Order-Dependent Construction Steps

### Step 1: Waypoint Domain Model & Enumerations
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/RouteWaypoint.kt`
* **Contents**:
  - `enum class WaypointCategory { LANDMARK, TURN_CUE, HAZARD }`
  - `enum class WaypointType(category, iconResId, displayNameResId)`
  - Keyword classification helpers:
    - `WaypointType.fromGpx(sym: String?, type: String?, name: String?): WaypointType`
    - `WaypointType.fromTcx(pointType: String?): WaypointType`
  - `data class RouteWaypoint(id: Long, routeId: Long, latLng: LatLng, altitude: Double, name: String, description: String, type: WaypointType, distanceFromStart: Double)`
* **Vector Icons**:
  - Create vector drawables in `app/src/main/res/drawable/`:
    - `ic_poi_bench.xml` (resting bench)
    - `ic_poi_water.xml` (water droplet)
    - `ic_poi_summit.xml` (mountain peak)
    - `ic_poi_food.xml` (restaurant / cafe)
    - `ic_poi_viewpoint.xml` (scenic viewpoint)
    - `ic_poi_danger.xml` (hazard triangle)
    - `ic_poi_first_aid.xml` (first aid cross)
    - `ic_turn_left.xml` (turn left arrow)
    - `ic_turn_right.xml` (turn right arrow)
    - `ic_turn_straight.xml` (straight arrow)
    - `ic_poi_generic.xml` (standard marker pin)

### Step 2: 9-Language Localization Parity
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml` (Strict invariant: escape all apostrophes as `\'`)
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Tokens**:
  - `waypoint_type_bench`: "Bench / Rest Spot" / "Bank / Rastplatz"
  - `waypoint_type_water`: "Water Source" / "Trinkwasser"
  - `waypoint_type_summit`: "Summit / Pass" / "Gipfel / Pass"
  - `waypoint_type_food`: "Food / Cafe" / "Einkehr / Café"
  - `waypoint_type_viewpoint`: "Viewpoint" / "Aussichtspunkt"
  - `waypoint_type_danger`: "Danger / Hazard" / "Gefahrenstelle"
  - `waypoint_type_first_aid`: "First Aid" / "Erste Hilfe"
  - `waypoint_type_turn_left`: "Turn Left" / "Links abbiegen"
  - `waypoint_type_turn_right`: "Turn Right" / "Rechts abbiegen"
  - `waypoint_type_turn_straight`: "Continue Straight" / "Geradeaus weiter"
  - `waypoint_type_generic`: "Waypoint" / "Wegpunkt"
  - `route_cue_sheet_title`: "Cue Sheet" / "Wegpunkte-Liste"
  - `route_waypoints_header`: "Waypoints & POIs (%1$d)" / "Wegpunkte & POIs (%1$d)"

### Step 3: Polyline Distance Projection Engine
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/WaypointDistanceCalculator.kt`
* **Logic**:
  - `fun projectWaypoints(waypoints: List<RouteWaypoint>, pathPoints: List<PathPoint>): List<RouteWaypoint>`
  - For each waypoint, finds the closest segment $[P_i, P_{i+1}]$.
  - Uses vector dot product / cross-track projection to compute fractional offset $t \in [0, 1]$.
  - Assigns `distanceFromStart = P_i.distance + t * (P_{i+1}.distance - P_i.distance)`.
  - Clamps result strictly to $[0.0, \text{totalDistance}]$.
  - Sorts resulting list by `distanceFromStart`.

### Step 4: Database Schema Upgrade (v9 -> v10) in `RoutesDatabaseManager.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManager.kt`
* **Changes**:
  - In `RouteContract`:
    - Define `TABLE_ROUTE_WAYPOINTS = "route_waypoints"`.
    - Columns: `COLUMN_WAYPOINT_ID`, `COLUMN_WAYPOINT_ROUTE_ID_FK`, `COLUMN_WAYPOINT_LAT`, `COLUMN_WAYPOINT_LNG`, `COLUMN_WAYPOINT_ALTITUDE`, `COLUMN_WAYPOINT_NAME`, `COLUMN_WAYPOINT_DESCRIPTION`, `COLUMN_WAYPOINT_TYPE`, `COLUMN_WAYPOINT_DIST_FROM_START`.
    - SQL DDL `CREATE_TABLE_ROUTE_WAYPOINTS` with `FOREIGN KEY(route_id) REFERENCES routes(id) ON DELETE CASCADE`.
    - SQL DDL `CREATE_INDEX_ROUTE_WAYPOINTS`.
  - In `RoutesDbHelper`:
    - Bump `DB_VERSION = 10`.
    - In `onCreate(db)`: Execute table and index creation.
    - In `onUpgrade(db, oldVersion, newVersion)`:
      ```kotlin
      if (oldVersion < 10) {
          db.execSQL(RouteContract.CREATE_TABLE_ROUTE_WAYPOINTS)
          db.execSQL(RouteContract.CREATE_INDEX_ROUTE_WAYPOINTS)
      }
      ```
  - In `RoutesDatabaseManager`:
    - Update `RouteWithPath(summary, path, val waypoints: List<RouteWaypoint> = emptyList())`.
    - Add `insertWaypoints(routeId: Long, waypoints: List<RouteWaypoint>)`.
    - Add `getWaypointsForRoute(routeId: Long): List<RouteWaypoint>`.
    - Add `deleteWaypointsForRoute(routeId: Long): Int`.
    - Update `insertRoute(summary, path, waypoints)` to persist waypoints within the transaction.
    - Update `getRouteWithPath(routeId)` and `getAllRoutesWithPaths()` to load waypoints.

### Step 5: GPX `<wpt>` Parser Integration in `GpxRouteImporter.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt`
* **Changes**:
  - In `importRouteFromGpx`:
    - Read `parsedGpx.wayPoints` (if non-null/non-empty).
    - Map each `WayPoint` to `RouteWaypoint`:
      ```kotlin
      val rawWaypoints = (parsedGpx.wayPoints ?: emptyList()).map { wpt ->
          RouteWaypoint(
              latLng = LatLng(wpt.latitude, wpt.longitude),
              altitude = wpt.elevation ?: 0.0,
              name = wpt.name ?: "",
              description = wpt.desc ?: wpt.cmt ?: "",
              type = WaypointType.fromGpx(wpt.sym, wpt.type, wpt.name)
          )
      }
      val projectedWaypoints = WaypointDistanceCalculator.projectWaypoints(rawWaypoints, pathPoints)
      ```
    - Return `Pair<RouteWithPath, ...>` or `Result<RouteImportResult>` preserving backwards compatibility.

### Step 6: TCX Course & Course Point Parser
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/TcxCourseParser.kt`
* **Changes**:
  - Streaming XML parser with `XmlPullParser`:
    - Parses `<Courses><Course>`:
      - Reads `<Name>` for route title.
      - Reads `<Track><Trackpoint>` extracting `Position` (`LatitudeDegrees`, `LongitudeDegrees`), `AltitudeMeters`, `DistanceMeters`.
      - Reads `<CoursePoint>` extracting `Name`, `Position`, `AltitudeMeters`, `PointType`, `Notes`.
    - Maps TCX `PointType` via `WaypointType.fromTcx(pointType)`.
    - Calculates/verifies cumulative distance.
    - Projects waypoints along the track.
    - Returns `Result<Pair<RouteSummary, List<PathPoint>, List<RouteWaypoint>>>`.

### Step 7: Map Layer Marker Rendering & Interactive Tooltip
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt`
* **Changes**:
  - Implement `RouteWaypointsLayer(waypoints: List<RouteWaypoint>, onWaypointClick: (RouteWaypoint) -> Unit)`.
  - Map `RouteWaypoint` into `LocationMarker` with custom cached `BitmapDescriptor` icons.
  - Set marker zIndex to $50\text{f}$ so markers render above both base and overlay polylines.
  - Implement interactive detail card showing waypoint name, icon, altitude, and distance.

### Step 8: Automated Unit, Repository & Regression Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporterWaypointTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/TcxCourseParserTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/WaypointDistanceCalculatorTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/RoutesDatabaseManagerWaypointTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/RouteWaypointLayerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Full Regression**:
  - `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Invariants & Risk Mitigation

1. **Database Backward Compatibility**:
   - `RouteWithPath.waypoints` defaults to `emptyList()`.
   - Existing code calling `insertRoute(summary, path)` or reading `RouteWithPath` continues to compile and execute without modification.
2. **Cascade Deletion Invariant**:
   - `FOREIGN KEY(route_id) REFERENCES routes(id) ON DELETE CASCADE` guarantees that deleting a route leaves zero orphan waypoints.
3. **No Third-Party Bloat**:
   - TCX course parsing uses Android framework `XmlPullParser`, adding zero runtime dependencies.
4. **Elevation Enrichment Preservation (`REQ-MAP-025`)**:
   - GPX elevation deficiency detection and Open-Meteo DEM enrichment remain completely intact.
