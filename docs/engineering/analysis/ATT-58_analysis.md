# Stage 1 Analysis: ATT-58 - Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points

**Ticket**: [ATT-58](https://rainerblind.atlassian.net/browse/ATT-58)  
**Sub-task**: [ATT-2287](https://rainerblind.atlassian.net/browse/ATT-2287) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-58`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

When athletes plan, record, or download cycling and running routes (from tools like Komoot, Strava, RideWithGPS, Garmin Connect, BRouter, or outdoor mapping portals), the route files frequently contain rich contextual landmarks and cues:
1. **GPX Waypoints (`<wpt>`)**:
   - Landmark Points of Interest (POIs) such as public water fountains (*Trinkwasser / Quellen*), resting benches (*Bänke / Rastplätze*), mountain passes and summits (*Gipfel / Pässe*), cafes and bakeries (*Bäcker / Einkehr*), viewpoints (*Aussichtspunkte*), and danger / hazard alerts (*Gefahrenstellen*).
2. **TCX Course Points (`<CoursePoint>`)**:
   - Course landmarks and directional turn-by-turn navigation cues (*Turn Left, Turn Right, Straight, Water, Food, Summit, Danger, First Aid*).

### Current Application Limitations
In the current implementation of `aTrainingTracker`:
1. **Parser Omission**:
   - [GpxRouteImporter.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt) exclusively parses `<trkpt>` elements from track segments. It completely ignores `<wpt>` tags in GPX files, discarding all landmark POIs.
   - The application provides no course importer for `.tcx` files; [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt) only parses `<Activity>` elements for historical workout recovery, with zero support for `<Courses><Course>` or `<CoursePoint>` elements.
2. **Database Schema Deficiency**:
   - `RoutesDatabaseManager.kt` (`Routes.db`, Schema v9) only defines two tables: `routes` (summary metadata) and `route_points` (stream of polyline coordinates). There is no data table or model for waypoints or course points.
3. **Map Visualization Gap**:
   - The map renderer ([ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt) / [MapLayers.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapLayers.kt)) renders route paths as static polylines without any interactive or visual markers for POIs along the way.
4. **Impact on Downstream Route Navigation**:
   - Upcoming navigation epics and tickets—specifically [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450) (*Abbiegehinweise während der Streckennavigation / Turn-by-Turn Navigation Cues*) and [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281) (*ClimbPro & Climb Cockpit*)—depend fundamentally on having an underlying waypoint and course point data structure.

### Business & Athletic Value
Enriching routes with waypoints and POIs elevates `aTrainingTracker` from a basic polyline follower into an intelligent, contextual navigation system. Athletes can plan water stops, anticipate tough mountain passes, and view upcoming course landmarks during training rides and runs.

---

## 2. Forensic Investigation & Architectural Gap Analysis

### 2.1 GPX Parser Capabilities (`android-gpx-parser`)
- The project includes `com.github.ticofab:android-gpx-parser:2.3.1`.
- Forensic inspection of the library's compiled bytecode (`Gpx.class`, `WayPoint.class`, `Point.class`) confirms:
  - `parsedGpx.wayPoints` returns a `List<WayPoint>`.
  - `WayPoint` inherits from `Point`, exposing:
    - `latitude: Double`
    - `longitude: Double`
    - `elevation: Double?`
    - `name: String?`
    - `desc: String?`
    - `cmt: String?`
    - `sym: String?` (symbol, e.g. "Water", "Summit", "Bench", "Flag")
    - `type: String?` (category type)
- **Current Defect**: [GpxRouteImporter.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/routes/GpxRouteImporter.kt) parses the stream via `parser.parse(inputStream)` but never accesses `parsedGpx.wayPoints`.

### 2.2 TCX Course Parsing Architecture
- The Garmin Training Center XML (TCX) specification defines course tracks and course points under:
  ```xml
  <Courses>
      <Course>
          <Name>Tour de Pain</Name>
          <Track>
              <Trackpoint>
                  <Time>2026-06-01T08:00:00Z</Time>
                  <Position>
                      <LatitudeDegrees>47.50</LatitudeDegrees>
                      <LongitudeDegrees>11.20</LongitudeDegrees>
                  </Position>
                  <AltitudeMeters>850.0</AltitudeMeters>
                  <DistanceMeters>0.0</DistanceMeters>
              </Trackpoint>
              ...
          </Track>
          <CoursePoint>
              <Name>Water Refill</Name>
              <Time>2026-06-01T08:15:00Z</Time>
              <Position>
                  <LatitudeDegrees>47.52</LatitudeDegrees>
                  <LongitudeDegrees>11.23</LongitudeDegrees>
              </Position>
              <AltitudeMeters>910.0</AltitudeMeters>
              <PointType>Water</PointType>
              <Notes>Fountain at town church</Notes>
          </CoursePoint>
      </Course>
  </Courses>
  ```
- **TCX Standard `PointType` Enums**:
  - Navigation / Turn cues: `Generic`, `Left`, `Right`, `Straight`
  - Hazards & Health: `Danger`, `First Aid`
  - Landmarks: `Water`, `Food`, `Summit`, `Valley`, `Sprint`
- Android's native `XmlPullParser` provides optimal, memory-efficient streaming parsing without external third-party dependencies, matching the existing pattern in [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt).

### 2.3 Database Schema & Persistence Gap (`Routes.db`)
- Current `RoutesDatabaseManager.kt`:
  - `RoutesDbHelper.DB_VERSION = 9`.
  - Tables:
    - `routes`: `id`, `external_id`, `name`, `description`, `distance`, `elevation_gain`, `sport_type`, `source`, `is_selected`, `cluster_id`, `bound_min_lat`, `bound_min_lng`, `bound_max_lat`, `bound_max_lng`, `synced_at`.
    - `route_points`: `id`, `route_id`, `lat`, `lng`, `distance_from_start`, `elevation`.
- **Architectural Requirement**:
  - Extend schema to Version 10:
    ```sql
    CREATE TABLE route_waypoints (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        route_id INTEGER NOT NULL,
        lat REAL NOT NULL,
        lng REAL NOT NULL,
        altitude REAL DEFAULT 0.0,
        name TEXT,
        description TEXT,
        type TEXT NOT NULL,
        distance_from_start REAL DEFAULT 0.0,
        FOREIGN KEY(route_id) REFERENCES routes(id) ON DELETE CASCADE
    );
    CREATE INDEX idx_route_waypoints_route_id ON route_waypoints(route_id);
    ```
  - Invariant: `ON DELETE CASCADE` ensures that deleting a route removes all associated points and waypoints atomically without leaving orphan records.

### 2.4 Distance Along Route Calculation Gap
- External GPX `<wpt>` entries typically define latitude and longitude but do NOT embed cumulative distance along the track polyline.
- To display a linear "Cue Sheet" or announce upcoming waypoints (e.g. "Water fountain in 2.5 km"), the importer must calculate the orthogonal or closest point along the route's `PathPoint` polyline and assign `distance_from_start`.
- Algorithm: For each waypoint $(lat_w, lng_w)$, compute the minimum distance to all polyline segments $(p_i, p_{i+1})$, and interpolate `distance_from_start` along the polyline. If the waypoint is within a reasonable corridor ($\le 100\text{m}$) of the track, assign the interpolated distance; otherwise assign the distance of the nearest point.

### 2.5 Map Visualization Gap (`ATrainingTrackerMap.kt`, `MapLayers.kt`)
- `ATrainingTrackerMap` renders routes, live tracks, and segments via `MapContentScope`.
- Currently, route rendering is handled by `MappablePathLayer` (drawing polylines and chevrons).
- There is no layer for route waypoints.
- **Architectural Requirement**:
  - Introduce `RouteWaypointLayer` in `MapLayers.kt`.
  - Render vector markers with distinct, intuitive icons and colors for each `WaypointType`:
    - `POI_BENCH`: Resting bench icon (Brown/Wood or Slate).
    - `POI_WATER`: Water droplet icon (Blue).
    - `POI_SUMMIT`: Mountain peak icon (Orange/Amber).
    - `POI_FOOD`: Cutlery/coffee cup icon (Teal).
    - `POI_DANGER`: Warning triangle icon (Red/Amber).
    - `POI_VIEWPOINT`: Binoculars/eye icon (Purple).
    - `POI_FIRST_AID`: Medical cross icon (Red).
    - `TURN_LEFT`, `TURN_RIGHT`, `TURN_STRAIGHT`: Directional turn arrow icons.
    - `GENERIC`: Classic pin/dot icon.
  - Interactive marker click: Tapping a waypoint displays a lightweight bottom sheet or tooltip with the waypoint's name, description, altitude, and distance from start.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-PRO-022`)

### 3.1 Related Existing Requirements
- **`REQ-MAP-005`** (*Strava Segments and Routes as Map Overlays*):
  - Invariant: Route polylines are rendered as overlays alongside live sessions.
- **`REQ-MAP-023`** (*Prominent High-Contrast Rendering for Actively Navigated Routes*):
  - Invariant: Active route navigation renders with 16f thickness, emerald green color, and directional chevrons; segments render between base and overlay. Waypoint markers must render on top of the polyline ($Z \ge 50\text{f}$).
- **`REQ-MAP-025`** (*Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data*):
  - Invariant: When importing GPX routes lacking altitude data, trackpoints are enriched with DEM elevations. Waypoints lacking altitude should also benefit from altitude assignment or nearest trackpoint altitude interpolation.

### 3.2 Formulation of Net-New Requirement
This ticket introduces:
- **`REQ-MAP-026`**: **Support Waypoints, POIs (Benches, Water, Summits) and TCX Course Points.**
- **`TST-MAP-028`**: Verification test specification for waypoints, POIs, and TCX course points.

### 3.3 Core Invariants to Preserve
1. **Backwards Compatibility**:
   - Existing routes stored in `Routes.db` (Schema v9) that have no waypoints must continue to load seamlessly without error.
2. **Cascade Deletion Integrity**:
   - `ON DELETE CASCADE` must ensure zero orphan records in `route_waypoints` when routes are deleted or updated.
3. **Streamlined UI Performance**:
   - Waypoint marker rendering on the map must use cached `BitmapDescriptor` icons and not cause frame drops during map panning or zooming.
4. **9-Language Localization Parity**:
   - All newly introduced waypoint type names, dialogs, and cue sheet labels must maintain 100% translation parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 4. Scope Bounding (`ATT-1250`)

### 4.1 In-Scope Objectives
1. **GPX Waypoint Parser Integration**:
   - Parse `<wpt>` elements in `GpxRouteImporter` (lat, lon, ele, name, desc, sym, type).
   - Classify GPX `sym`, `type`, and `name` into `WaypointType` enums (Bench, Water, Summit, Food, Danger, Viewpoint, Generic).
2. **TCX Course Parser Integration**:
   - Implement `TcxCourseParser` to parse `<Courses><Course>` including `<Trackpoint>` and `<CoursePoint>`.
   - Map TCX `PointType` (Left, Right, Straight, Water, Food, Summit, Danger, etc.) to `WaypointType`.
3. **Database Schema & Management (`Routes.db`)**:
   - Upgrade `RoutesDbHelper.DB_VERSION` from 9 to 10.
   - Create `route_waypoints` table with index on `route_id` and foreign key cascade deletion.
   - Add CRUD methods to `RoutesDatabaseManager`: `insertWaypoints`, `getWaypointsForRoute`, `deleteWaypointsForRoute`.
   - Update `RouteWithPath` to include `waypoints: List<RouteWaypoint> = emptyList()`.
4. **Spatial Distance Calculation**:
   - Implement nearest-segment projection to compute `distanceFromStart` for imported waypoints along the route polyline.
5. **Map Visualization & Interactive POIs**:
   - Implement `RouteWaypointLayer` in `MapLayers.kt` / `ATrainingTrackerMap.kt`.
   - Provide custom vector icons for each `WaypointType`.
   - Add marker click listener displaying waypoint details (Name, Altitude, Distance from Start, Description).
6. **Cue Sheet / Waypoint List UI**:
   - Provide a sequential list of upcoming waypoints sorted by `distanceFromStart` in route preview / details.
7. **Comprehensive Automated Tests**:
   - Unit tests for GPX `<wpt>` parsing and classification.
   - Unit tests for TCX `<CoursePoint>` parsing.
   - Database migration and CRUD tests for `route_waypoints`.
   - Spatial projection distance calculation tests.
   - Clean-room full-suite regression.

### 4.2 Out-of-Scope (Deliberately Deferred)
- **Live Auditory Turn-by-Turn TTS Announcements**: Handled in [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450).
- **Display Wakeup / Battery Saver Integration**: Handled in [ATT-1450](https://rainerblind.atlassian.net/browse/ATT-1450) and [ATT-1268](https://rainerblind.atlassian.net/browse/ATT-1268).
- **Climb Detection & ClimbPro Cockpit**: Handled in [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281).
- **Manual Waypoint Authoring / Map Pin Dropping**: In-app route creation/editing tools are not part of this ticket.

---

## 5. Architectural Design & Implementation Strategy

### 5.1 Waypoint Domain Model & Classification

```kotlin
enum class WaypointCategory {
    LANDMARK,
    TURN_CUE,
    HAZARD
}

enum class WaypointType(
    val category: WaypointCategory,
    val iconResId: Int,
    val displayNameResId: Int
) {
    // Landmark POIs
    POI_BENCH(WaypointCategory.LANDMARK, R.drawable.ic_poi_bench, R.string.waypoint_type_bench),
    POI_WATER(WaypointCategory.LANDMARK, R.drawable.ic_poi_water, R.string.waypoint_type_water),
    POI_SUMMIT(WaypointCategory.LANDMARK, R.drawable.ic_poi_summit, R.string.waypoint_type_summit),
    POI_FOOD(WaypointCategory.LANDMARK, R.drawable.ic_poi_food, R.string.waypoint_type_food),
    POI_VIEWPOINT(WaypointCategory.LANDMARK, R.drawable.ic_poi_viewpoint, R.string.waypoint_type_viewpoint),
    POI_FIRST_AID(WaypointCategory.LANDMARK, R.drawable.ic_poi_first_aid, R.string.waypoint_type_first_aid),
    
    // Hazards
    POI_DANGER(WaypointCategory.HAZARD, R.drawable.ic_poi_danger, R.string.waypoint_type_danger),
    
    // Turn cues
    TURN_LEFT(WaypointCategory.TURN_CUE, R.drawable.ic_turn_left, R.string.waypoint_type_turn_left),
    TURN_RIGHT(WaypointCategory.TURN_CUE, R.drawable.ic_turn_right, R.string.waypoint_type_turn_right),
    TURN_STRAIGHT(WaypointCategory.TURN_CUE, R.drawable.ic_turn_straight, R.string.waypoint_type_turn_straight),
    
    // Fallback
    GENERIC(WaypointCategory.LANDMARK, R.drawable.ic_poi_generic, R.string.waypoint_type_generic);

    companion object {
        fun fromGpx(sym: String?, type: String?, name: String?): WaypointType {
            val text = "${sym ?: ""} ${type ?: ""} ${name ?: ""}".lowercase()
            return when {
                text.contains("water") || text.contains("wasser") || text.contains("quelle") || text.contains("fountain") -> POI_WATER
                text.contains("bench") || text.contains("bank") || text.contains("rast") || text.contains("picnic") -> POI_BENCH
                text.contains("summit") || text.contains("gipfel") || text.contains("peak") || text.contains("pass") -> POI_SUMMIT
                text.contains("food") || text.contains("cafe") || text.contains("restaurant") || text.contains("bäcker") || text.contains("essen") -> POI_FOOD
                text.contains("danger") || text.contains("gefahr") || text.contains("hazard") || text.contains("caution") -> POI_DANGER
                text.contains("view") || text.contains("aussicht") || text.contains("panorama") -> POI_VIEWPOINT
                text.contains("first aid") || text.contains("erste hilfe") || text.contains("hospital") -> POI_FIRST_AID
                text.contains("left") || text.contains("links") -> TURN_LEFT
                text.contains("right") || text.contains("rechts") -> TURN_RIGHT
                text.contains("straight") || text.contains("geradeaus") -> TURN_STRAIGHT
                else -> GENERIC
            }
        }

        fun fromTcx(pointType: String?): WaypointType {
            return when (pointType?.trim()?.lowercase()) {
                "water" -> POI_WATER
                "food" -> POI_FOOD
                "summit" -> POI_SUMMIT
                "valley" -> POI_VIEWPOINT
                "danger" -> POI_DANGER
                "first aid" -> POI_FIRST_AID
                "left" -> TURN_LEFT
                "right" -> TURN_RIGHT
                "straight" -> TURN_STRAIGHT
                else -> GENERIC
            }
        }
    }
}

data class RouteWaypoint(
    val id: Long = 0L,
    val routeId: Long = 0L,
    val latLng: LatLng,
    val altitude: Double = 0.0,
    val name: String = "",
    val description: String = "",
    val type: WaypointType = WaypointType.GENERIC,
    val distanceFromStart: Double = 0.0
)
```

### 5.2 Distance Along Route Calculation
For each waypoint $W$, we calculate its projection onto the polyline formed by `pathPoints` $[P_0, P_1, \dots, P_n]$:
1. Find segment $[P_i, P_{i+1}]$ that minimizes the perpendicular distance to $W$.
2. Compute the fractional distance $t \in [0, 1]$ along segment $[P_i, P_{i+1}]$.
3. `distanceFromStart` = $P_i.\text{distance} + t \times (P_{i+1}.\text{distance} - P_i.\text{distance})$.

### 5.3 Database Schema & Contract
```kotlin
object RouteContract {
    ...
    const val TABLE_ROUTE_WAYPOINTS = "route_waypoints"
    const val COLUMN_WAYPOINT_ID = "id"
    const val COLUMN_WAYPOINT_ROUTE_ID_FK = "route_id"
    const val COLUMN_WAYPOINT_LAT = "lat"
    const val COLUMN_WAYPOINT_LNG = "lng"
    const val COLUMN_WAYPOINT_ALTITUDE = "altitude"
    const val COLUMN_WAYPOINT_NAME = "name"
    const val COLUMN_WAYPOINT_DESCRIPTION = "description"
    const val COLUMN_WAYPOINT_TYPE = "type"
    const val COLUMN_WAYPOINT_DIST_FROM_START = "distance_from_start"

    const val CREATE_TABLE_ROUTE_WAYPOINTS = """
        CREATE TABLE $TABLE_ROUTE_WAYPOINTS (
            $COLUMN_WAYPOINT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_WAYPOINT_ROUTE_ID_FK INTEGER NOT NULL,
            $COLUMN_WAYPOINT_LAT REAL NOT NULL,
            $COLUMN_WAYPOINT_LNG REAL NOT NULL,
            $COLUMN_WAYPOINT_ALTITUDE REAL DEFAULT 0.0,
            $COLUMN_WAYPOINT_NAME TEXT,
            $COLUMN_WAYPOINT_DESCRIPTION TEXT,
            $COLUMN_WAYPOINT_TYPE TEXT NOT NULL,
            $COLUMN_WAYPOINT_DIST_FROM_START REAL DEFAULT 0.0,
            FOREIGN KEY($COLUMN_WAYPOINT_ROUTE_ID_FK) REFERENCES $TABLE_ROUTES($COLUMN_ID) ON DELETE CASCADE
        );
    """
    const val CREATE_INDEX_ROUTE_WAYPOINTS = """
        CREATE INDEX IF NOT EXISTS idx_route_waypoints_route_id ON $TABLE_ROUTE_WAYPOINTS($COLUMN_WAYPOINT_ROUTE_ID_FK);
    """
}
```

---

## 6. Verification & Test Strategy

| Test Identifier | Test Target | Description / Verification Objective |
| :--- | :--- | :--- |
| **TST-MAP-028.1** | `GpxRouteImporterWaypointTest` | **GPX `<wpt>` Extraction**: Parse a GPX file containing trackpoints and `<wpt>` tags. Verify waypoints are extracted with coordinates, altitude, name, description, and classified into `POI_WATER`, `POI_BENCH`, `POI_SUMMIT`. |
| **TST-MAP-028.2** | `TcxCourseParserTest` | **TCX `<CoursePoint>` Extraction**: Parse a TCX course file containing trackpoints and `<CoursePoint>` elements. Verify course points are extracted with `PointType` correctly mapped to `WaypointType` (`Left`, `Right`, `Water`, `Food`, `Danger`). |
| **TST-MAP-028.3** | `WaypointDistanceCalculatorTest` | **Distance-Along-Route Projection**: Verify that waypoint coordinates are projected accurately onto the polyline, computing monotonic `distanceFromStart` values matching track segments. |
| **TST-MAP-028.4** | `RoutesDatabaseManagerWaypointTest` | **Database Schema v10 & Cascade Deletion**: Verify `RoutesDbHelper` upgrades to v10; insert route with waypoints; query waypoints by route ID; delete route and verify associated waypoints are automatically purged by SQLite `ON DELETE CASCADE`. |
| **TST-MAP-028.5** | `RouteWaypointLayerTest` | **Map Layer Model & Click Contract**: Verify `RouteWaypoint` converts to `LocationMarker` or `WaypointMarker` with correct icon descriptor, zIndex ($Z \ge 50\text{f}$), and tap callback. |
| **TST-MAP-028.6** | `TranslationParityTest` | **9-Language Localization Audit**: Verify all new waypoint strings and labels exist across EN, DE, ES, FR, IT, JA, NL, PL, PT. |
| **TST-MAP-028.7** | Regression Suite | **Clean-Room Full Suite Regression**: Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions. |

---

## 7. Next Steps (Stage 2)
Upon Gate 1 approval:
1. Transition `ATT-2287` to `Erledigt`.
2. Create Stage 2 subtask: `[Req & Test Spec] Support waypoints, POIs (benches, water, summits) and TCX course points`.
3. Add `REQ-MAP-026` to `docs/requirements.md` and `TST-MAP-028` to `docs/tests.md`.
4. Author Stage 2 Test Specification deliverable: `docs/engineering/test_specs/ATT-58_test_spec.md`.
