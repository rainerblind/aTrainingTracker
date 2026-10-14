# Stage 5 Walkthrough - ATT-3362: Consolidate duplicate TCX and GPX trackpoint parsing into shared XML utility

**Ticket**: [ATT-3362](https://rainerblind.atlassian.net/browse/ATT-3362)  
**Stage 5 Subtask**: [ATT-3535](https://rainerblind.atlassian.net/browse/ATT-3535) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-037` (`Verified`)  
**Test Mapping**: `TST-MIG-034` (`Verified`)  
**Branch**: `feature/ATT-3362`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Implementation Details

Prior to ATT-3362, XML trackpoint, course, and route ingestion was fragmented across multiple disconnected engines that independently reimplemented duplicate pull parser loops:
1. **Duplicate TCX Parsers**:
   - `LegacyImportEngine.kt:523–781` maintained an inline ~250-line `XmlPullParser` loop for workout history imports.
   - `TcxCourseParser.kt:42–140` maintained an independent ~100-line `XmlPullParser` loop for route and course imports.
2. **Duplicate GPX Parsers**:
   - `LegacyImportEngine.kt:1019–1175` maintained an inline ~150-line `XmlPullParser` loop parsing `<trkpt>`, `<ele>`, `<time>`, and telemetry extensions.
   - `GpxRouteImporter.kt:87` imported and invoked a third-party library (`io.ticofab.androidgpxparser.parser.GPXParser`) for GPX route ingestion.

This violated **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, leading to code bloat, divergence in tag handling (e.g., Garmin TPX telemetry extensions, course point types, altitude fallback logic), and unnecessary external library dependencies.

ATT-3362 establishes a unified, shared streaming XML trackpoint parsing architecture in `com.atrainingtracker.trainingtracker.routes.parsers`, refactors all workout and route ingestion sites to delegate to canonical parsers, and verifies 100% data fidelity and backward compatibility.

### Key Architectural Changes & Refactorings

1. **Shared Domain DTOs (`TrackpointModels.kt`)**:
   - Authored immutable, pure-JVM data transfer objects:
     - `RawTrackpoint(lat, lng, altitude, distanceMeters, time, hr, cadence, power, speedMps, temperature)`
     - `RawCoursePoint(name, lat, lng, altitude, type, notes, time)`
     - `RawLap(lapNr, startTime, totalTimeSeconds, distanceMeters, maxSpeed, calories, avgHeartRate, name, description, trackpoints)`
     - `ParsedTcxData(name, notes, sport, creator, laps, coursePoints)`
     - `ParsedGpxData(name, description, sport, laps, waypoints)`

2. **Streaming TCX Trackpoint Parser (`TcxTrackpointParser.kt`)**:
   - Pure-JVM streaming parser utilizing `XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()`.
   - Unified support for both Garmin Activity (`<Activity>`) and Garmin Course (`<Course>`) formats.
   - Accurately parses coordinates, altitude, distance, timestamps, sensor telemetry (`HeartRateBpm`, `Cadence`, `RunCadence`, `Watts`, `Speed`), laps, and course points.

3. **Streaming GPX Trackpoint Parser (`GpxTrackpointParser.kt`)**:
   - Pure-JVM streaming parser supporting `<trk>`, `<rte>`, `<trkseg>`, and `<wpt>`.
   - Comprehensive extraction of track coordinates (`lat`, `lon`), altitude (`<ele>`), timestamps (`<time>`), and Garmin TPX extensions (`<gpxtpx:TrackPointExtension>`: `hr`, `cad`, `speed`, `atemp`).
   - Unified waypoint ingestion for POIs and route cues.

4. **Integration into `LegacyImportEngine.kt`**:
   - Replaced the inline ~250-line TCX XML pull parsing loop with `TcxTrackpointParser.parse(fis)`.
   - Replaced the inline ~150-line GPX XML pull parsing loop with `GpxTrackpointParser.parse(fis)`.
   - Mapped immutable raw trackpoints and laps directly into `ParsedLap`, `ContentValues` sample rows, and workout summary records with 100% telemetry fidelity.

5. **Integration into Route Importers (`TcxCourseParser.kt` & `GpxRouteImporter.kt`)**:
   - Refactored `TcxCourseParser.kt` to delegate course and course-point ingestion directly to `TcxTrackpointParser.parse(inputStream)`, mapping points to `PathPoint` and `RouteWaypoint`.
   - Refactored `GpxRouteImporter.kt` to delegate GPX parsing to `GpxTrackpointParser.parse(inputStream)`, completely removing the external `androidgpxparser` dependency while preserving DEM elevation enrichment and waypoint polyline projection.

6. **Dedicated Parser Unit Testing (`TrackpointXmlParserTest.kt`)**:
   - Created test suite verifying TCX activity parsing, TCX course parsing, GPX track parsing with Garmin TPX extensions, GPX route and waypoint parsing, and edge cases (empty documents, malformed XML, missing optional telemetry).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-037` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36)*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-MIG-034` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Rule 36 (Single Source of Truth & DRY)**: Single authoritative XML parsing architecture for TCX and GPX across both workouts and routes; zero duplicated pull parser tag-processing routines.
2. **Telemetry Ingestion Parity**: Preserves heart rate, cadence, power, speed, temperature, and cumulative distances across all parsed workouts.
3. **Pure JVM Test Safety**: Formatted XML parsers execute purely on JVM without Android framework stub dependencies, eliminating NPEs in headless unit testing.
4. **Course & Waypoint Polyline Projection**: All TCX course points and GPX waypoints accurately extracted and projected onto polylines.
5. **DEM Elevation Enrichment**: Route imports without altitude continue to seamlessly trigger automated DEM elevation enrichment.
6. **Clean-Room Regression Free**: Full regression test suite passes 100% with zero failures or errors.

---

## 4. Test Verification Results

### Unit & Regression Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `TrackpointXmlParserTest` | Dedicated unit tests for streaming TCX and GPX parsers, Garmin TPX extensions, course points, waypoints, and edge cases | **PASS** |
| `LegacyImportEngineGpxTest` | Verifies high-fidelity workout ingestion from GPX files via `LegacyImportEngine` | **PASS** |
| `TcxHighFidelityImportTest` | Verifies high-fidelity workout ingestion from TCX files via `LegacyImportEngine` | **PASS** |
| `TcxCourseParserTest` | Verifies route course and course-point parsing from TCX files via `TcxCourseParser` | **PASS** |
| `GpxRouteImporterWaypointTest` | Verifies GPX route and waypoint ingestion via `GpxRouteImporter` | **PASS** |
| `GpxRouteImporterElevationTest`| Verifies DEM elevation enrichment integration in `GpxRouteImporter` | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).
