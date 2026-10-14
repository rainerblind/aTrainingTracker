# Stage 2: Requirement & Test Specification - ATT-3362: Consolidate duplicate TCX and GPX trackpoint parsing into shared XML utility

**Ticket**: [ATT-3362](https://rainerblind.atlassian.net/browse/ATT-3362)  
**Sub-task**: [ATT-3531](https://rainerblind.atlassian.net/browse/ATT-3531) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-037` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36)*), complementing `REQ-MIG-030`, `REQ-MAP-025`, and `REQ-MAP-026`  
**Test Spec ID**: `TST-MIG-034`  
**Branch**: `feature/ATT-3362`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-MIG-037)

### 1.1 Problem Statement & Rationale

Track and workout ingestion in `aTrainingTracker` is currently split across disconnected engines that independently reimplement duplicate XML parsing logic:
1. **Duplicate TCX Parsers**:
   - In `LegacyImportEngine.kt:523–781`, a 250-line custom `XmlPullParser` loops through XML tags (`<Trackpoint>`, `<LatitudeDegrees>`, `<LongitudeDegrees>`, `<AltitudeMeters>`, `<DistanceMeters>`, `<HeartRateBpm>`, `<Cadence>`, `<Watts>`, `<Speed>`, `<Lap>`, `<Activity>`) for workout imports.
   - In `TcxCourseParser.kt:42–140`, an independent `XmlPullParser` parses the exact same XML tags (`<Trackpoint>`, `<Position>`, `<LatitudeDegrees>`, `<LongitudeDegrees>`, `<AltitudeMeters>`, `<DistanceMeters>`, `<CoursePoint>`) for route course imports.
2. **Duplicate GPX Parsers**:
   - In `LegacyImportEngine.kt:1019–1175`, a custom 150-line `XmlPullParser` parses GPX elements (`<trkpt>`, `lat`, `lon`, `<ele>`, `<time>`, `<speed>`, `<hr>`, `<cad>`, `<power>`, `<atemp>`, `<trkseg>`).
   - In `GpxRouteImporter.kt:87`, an external third-party library (`io.ticofab.androidgpxparser.parser.GPXParser`) is imported and executed for GPX route imports.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
1. **Behavioral Divergence**: Any bug fix or edge-case handling (e.g. malformed coordinates, missing timestamps, namespace variations, altitude smoothing) implemented in one parser is omitted from the other.
2. **Unnecessary External Dependencies & Test Friction**: Relying on `androidgpxparser` in `GpxRouteImporter` introduces third-party library overhead and complicates unit testing with mock XML parsers, whereas a lightweight, pure-JVM streaming pull parser handles both formats cleanly.

### 1.2 Functional & Architectural Requirements

1. **Shared XML Trackpoint Domain DTOs (`TrackpointXmlParser.kt`)**:
   - The system SHALL provide immutable, pure Kotlin domain DTOs in package `com.atrainingtracker.trainingtracker.routes.parsers`:
     - `RawTrackpoint(lat: Double, lng: Double, altitude: Double? = null, distanceMeters: Double? = null, time: String? = null, hr: Int? = null, cadence: Int? = null, power: Int? = null, speedMps: Double? = null, temperature: Double? = null)`
     - `RawCoursePoint(name: String, lat: Double, lng: Double, altitude: Double? = null, type: String? = null, notes: String? = null, time: String? = null)`
     - `RawLap(lapNr: Long, startTime: String? = null, totalTimeSeconds: Double? = null, distanceMeters: Double? = null, maxSpeed: Double? = null, calories: Int? = null, avgHeartRate: Int? = null, name: String? = null, description: String? = null, trackpoints: List<RawTrackpoint> = emptyList())`
     - `ParsedTcxData(name: String? = null, notes: String? = null, sport: String? = null, creator: String? = null, laps: List<RawLap> = emptyList(), coursePoints: List<RawCoursePoint> = emptyList())`
     - `ParsedGpxData(name: String? = null, description: String? = null, sport: String? = null, laps: List<RawLap> = emptyList(), waypoints: List<RawCoursePoint> = emptyList())`

2. **Streaming TCX Parser (`TcxTrackpointParser` / `TrackpointXmlParser.kt`)**:
   - The system SHALL implement pure Java/Kotlin streaming pull parsing using `XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()`.
   - The parser SHALL handle both Garmin Activity documents (`<Activity>`) and Garmin Course documents (`<Course>`).
   - It SHALL stream through `<Trackpoint>`, `<Position>`, `<LatitudeDegrees>`, `<LongitudeDegrees>`, `<AltitudeMeters>`, `<DistanceMeters>`, `<Time>`, `<HeartRateBpm>`, `<Cadence>`, `<RunCadence>`, `<Watts>`, `<Speed>`, `<Lap>`, and `<CoursePoint>`.

3. **Streaming GPX Parser (`GpxTrackpointParser` / `TrackpointXmlParser.kt`)**:
   - The system SHALL implement pure Java/Kotlin streaming pull parsing for GPX XML.
   - It SHALL stream through `<trkpt>`, `<rtept>`, `<trkseg>`, and `<wpt>`.
   - It SHALL extract coordinates (`lat`, `lon`), elevation (`<ele>`), timestamp (`<time>`), and Garmin TPX extensions (`<gpxtpx:TrackPointExtension>`: `hr`, `cad`, `speed`, `atemp`, and `power`).

4. **Integration in `LegacyImportEngine.kt`**:
   - In `LegacyImportEngine.kt`, replace the inline TCX parsing loop (~250 lines) with a call to `TcxTrackpointParser.parse(inputStream)`, mapping the resulting `ParsedTcxData` to `ParsedLap`, `ContentValues` samples, `points`, and summary metrics.
   - In `LegacyImportEngine.kt`, replace the inline GPX parsing loop (~150 lines) with a call to `GpxTrackpointParser.parse(inputStream)`, mapping the resulting `ParsedGpxData` to `ParsedLap`, `ContentValues` samples, `points`, and summary metrics.

5. **Integration in Route Importers (`TcxCourseParser.kt` & `GpxRouteImporter.kt`)**:
   - In `TcxCourseParser.kt`, delegate stream parsing to `TcxTrackpointParser.parse(inputStream)`, mapping trackpoints to `PathPoint` and `coursePoints` to `RouteWaypoint`.
   - In `GpxRouteImporter.kt`, route stream parsing through `GpxTrackpointParser.parse(inputStream)`, mapping trackpoints to `PathPoint` and waypoints to `RouteWaypoint`, maintaining automatic DEM elevation enrichment.

6. **Preservation of System Invariants**:
   - 100% test pass rate across all existing unit test suites (`LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, `TcxCourseParserTest`, `GpxRouteImporterWaypointTest`, `GpxRouteImporterElevationTest`) MUST be preserved.
   - Single Source of Truth & DRY (Rule 36) strictly enforced.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-MIG-037`). Refactors and unifies implementation of `REQ-MIG-030` (*High-Fidelity GPX Workout Import & Full Telemetry Ingestion Parity*), `REQ-MAP-025` (*Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data*), and `REQ-MAP-026` (*Support Waypoints, POIs and TCX Course Points*).
2. **Historical Origin & Commit Trace**: Workout TCX/GPX import was authored in Sprint 2026-40.12 (`LegacyImportEngine.kt`), while Route TCX/GPX import was independently authored in Sprint 2026-41.2 (`TcxCourseParser.kt`, `GpxRouteImporter.kt`).
3. **Root Reason for Existing Formulation**: Independent implementation during rapid development cycles led to duplicate XML parsing logic in two modules, violating Rule 36.
4. **Preservation of Core Invariants**: Multi-lap segmentation, heart rate, cadence, power, temperature, elevation enrichment, and polyline waypoint projection are strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (TCX Workout & Course Ingestion)**:
  * *Given* a Garmin TCX workout or course file,
  * *When* parsed by `LegacyImportEngine` or `TcxCourseParser`,
  * *Then* both delegate to `TcxTrackpointParser`, extracting identical trackpoints, telemetry, laps, and course points with zero duplicate tag loops.
* **Criterion 2 (GPX Workout & Route Ingestion)**:
  * *Given* a GPX track or route file with waypoints and Garmin TPX extensions,
  * *When* parsed by `LegacyImportEngine` or `GpxRouteImporter`,
  * *Then* both delegate to `GpxTrackpointParser`, extracting identical coordinates, elevations, timestamps, and telemetry.
* **Criterion 3 (Backward Compatibility & Test Regressions)**:
  * *Given* all existing unit tests in `LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, `TcxCourseParserTest`, `GpxRouteImporterWaypointTest`, and `GpxRouteImporterElevationTest`,
  * *When* executed on pure JVM,
  * *Then* 100% of tests pass without regression.

---

## 4. Test Specification (TST-MIG-034)

### 4.1 Test Scope & Matrix

| Test Suite | File | Verifications |
| :--- | :--- | :--- |
| **TrackpointXmlParserTest** (New) | `TrackpointXmlParserTest.kt` | Pure streaming TCX & GPX unit tests: activity vs course, namespaces, waypoints, TPX telemetry, empty/corrupt XML. |
| **LegacyImportEngineGpxTest** (Regression) | `LegacyImportEngineGpxTest.kt` | Multi-segment GPX laps, distance accumulation, TPX extension parsing. |
| **TcxHighFidelityImportTest** (Regression) | `TcxHighFidelityImportTest.kt` | High-fidelity TCX workout ingestion, cadence, power, lap splits. |
| **TcxCourseParserTest** (Regression) | `TcxCourseParserTest.kt` | TCX course parsing, course point classification, polyline projection. |
| **GpxRouteImporterWaypointTest** (Regression) | `GpxRouteImporterWaypointTest.kt` | GPX waypoint extraction, type heuristics, polyline projection. |
| **GpxRouteImporterElevationTest** (Regression) | `GpxRouteImporterElevationTest.kt` | DEM elevation enrichment on imported GPX tracks. |

### 4.2 Clean-Room Test Execution
* Command: `./gradlew testDebugUnitTest`
* Gate Criteria: 100% pass rate across entire project test suite.
