# Stage 3: Implementation Plan - ATT-3362: Consolidate duplicate TCX and GPX trackpoint parsing into shared XML utility

**Ticket**: [ATT-3362](https://rainerblind.atlassian.net/browse/ATT-3362)  
**Sub-task**: [ATT-3532](https://rainerblind.atlassian.net/browse/ATT-3532) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-MIG-037` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36)*), complementing `REQ-MIG-030`, `REQ-MAP-025`, and `REQ-MAP-026`  
**Test Mapping**: `TST-MIG-034` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification*)  
**Branch**: `feature/ATT-3362`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Background

In `aTrainingTracker`, track and workout ingestion is currently split across disconnected engines that independently reimplement duplicate XML parsing logic:
1. **Duplicate TCX Parsers**:
   - In `LegacyImportEngine.kt:523–781`, a 250-line custom `XmlPullParser` loops through XML tags (`<Trackpoint>`, `<LatitudeDegrees>`, `<LongitudeDegrees>`, `<AltitudeMeters>`, `<DistanceMeters>`, `<HeartRateBpm>`, `<Cadence>`, `<Watts>`, `<Speed>`, `<Lap>`, `<Activity>`) for workout imports.
   - In `TcxCourseParser.kt:42–140`, an independent `XmlPullParser` parses the exact same XML tags (`<Trackpoint>`, `<Position>`, `<LatitudeDegrees>`, `<LongitudeDegrees>`, `<AltitudeMeters>`, `<DistanceMeters>`, `<CoursePoint>`) for route course imports.
2. **Duplicate GPX Parsers**:
   - In `LegacyImportEngine.kt:1019–1175`, a custom 150-line `XmlPullParser` parses GPX elements (`<trkpt>`, `lat`, `lon`, `<ele>`, `<time>`, `<speed>`, `<hr>`, `<cad>`, `<power>`, `<atemp>`, `<trkseg>`).
   - In `GpxRouteImporter.kt:87`, an external third-party library (`io.ticofab.androidgpxparser.parser.GPXParser`) is imported and executed for GPX route imports.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
- **Behavioral Divergence**: Any bug fix or edge-case handling (e.g. malformed coordinates, missing timestamps, namespace variations, altitude smoothing) implemented in one parser is omitted from the other.
- **Unnecessary External Dependencies & Test Friction**: Relying on `androidgpxparser` in `GpxRouteImporter` introduces third-party library overhead and complicates unit testing with mock XML parsers, whereas a lightweight, pure-JVM streaming pull parser handles both formats cleanly.

ATT-3362 consolidates both parsers into a unified, shared streaming XML parsing module in package `com.atrainingtracker.trainingtracker.routes.parsers`, refactors all call sites, and guarantees 100% data fidelity across workout and route imports.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-037` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine (Rule 36)*)
* **Test Mapping**: `TST-MIG-034` (*Unified Streaming TCX and GPX XML Trackpoint Parsing Engine Verification*)
* **Living Documentation**:
  - `docs/requirements.md` (`REQ-MIG-037` specified)
  - `docs/tests.md` (`TST-MIG-034` specified)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 36 (Single Source of Truth & DRY)**: Zero duplicate XML parsing routines for TCX and GPX files; both workout ingestion and route imports route through canonical streaming parsers.
2. **Data Fidelity & Telemetry Invariant**:
   - TCX: Coordinates (lat/lon), altitude, cumulative distance, timestamps, heart rate (HR), cadence, cycling power (Watts), speed (m/s), and laps must be extracted identically.
   - GPX: Coordinates (lat/lon), altitude, cumulative distance, timestamps, speed, heart rate, cadence, power, temperature, laps (`trkseg`), and waypoints (`wpt`) must be extracted identically.
3. **Pure Java/JVM XML Streaming Invariant**:
   - Parsers must use `XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()` to execute purely on JVM without relying on Android framework stubs or throwing `NullPointerException` in local unit tests.
4. **Waypoints & CoursePoints Invariant**:
   - TCX CoursePoints and GPX Waypoints must be parsed with name, type, altitude, and notes, and accurately projected onto the polyline by `WaypointDistanceCalculator.projectWaypoints`.
5. **DEM Elevation Enrichment Invariant**:
   - If an imported GPX track lacks valid non-zero altitude data, `GpxRouteImporter` must continue to trigger automatic chunked DEM elevation enrichment via `ElevationService`.
6. **Clean-Room Full Suite Regression Invariant**:
   - 100% test pass rate across all existing unit test suites (`LegacyImportEngineGpxTest`, `TcxHighFidelityImportTest`, `TcxCourseParserTest`, `GpxRouteImporterWaypointTest`, `GpxRouteImporterElevationTest`, and full project suite `./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component Architecture & Package Structure

New package: `com.atrainingtracker.trainingtracker.routes.parsers`
1. **`TrackpointModels.kt`**: Immutable DTOs representing extracted track data:
   - `RawTrackpoint`: lat, lng, altitude, distanceMeters, time, hr, cadence, power, speedMps, temperature.
   - `RawCoursePoint`: name, lat, lng, altitude, type, notes, time.
   - `RawLap`: lapNr, startTime, totalTimeSeconds, distanceMeters, maxSpeed, calories, avgHeartRate, name, description, trackpoints.
   - `ParsedTcxData`: name, notes, sport, creator, laps, coursePoints.
   - `ParsedGpxData`: name, description, sport, laps, waypoints.

2. **`TcxTrackpointParser.kt`**:
   - Streaming XML pull parser for Garmin TCX format.
   - Handles both `<Activity>` (workout) and `<Course>` (route) root structures.
   - Extracts laps, trackpoints, telemetry, and course points.
   - Pure JVM execution via `XmlPullParserFactory`.

3. **`GpxTrackpointParser.kt`**:
   - Streaming XML pull parser for GPX format.
   - Handles `<trk>`, `<rte>`, `<trkseg>`, and `<wpt>`.
   - Extracts coordinates, elevations, timestamps, telemetry (standard and Garmin TPX extensions), and waypoints.
   - Pure JVM execution via `XmlPullParserFactory`.

### 4.2 Call Site Integration

1. **`TcxCourseParser.kt`**:
   - Replace the internal 100-line XML loop with `TcxTrackpointParser.parse(inputStream)`.
   - Map `parsedTcx.laps.flatMap { it.trackpoints }` to `PathPoint(LatLng(lat, lng), altitude, distance)`.
   - Map `parsedTcx.coursePoints` to `RouteWaypoint`.
   - Retain distance calculation fallback, waypoint projection, and `RouteImportResult` assembly.

2. **`GpxRouteImporter.kt`**:
   - Replace the external `io.ticofab.androidgpxparser.parser.GPXParser` execution with `GpxTrackpointParser.parse(inputStream)`.
   - Map `parsedGpx.laps.flatMap { it.trackpoints }` to `PathPoint`.
   - Map `parsedGpx.waypoints` to `RouteWaypoint`.
   - Retain cumulative distance calculation, DEM enrichment (`enrichElevations`), and waypoint projection.

3. **`LegacyImportEngine.kt`**:
   - In `importTcxWorkoutInternal`:
     - Replace the ~250-line inline pull parser loop with `TcxTrackpointParser.parse(fis)`.
     - Populate `sportName`, `workoutName`, `workoutNotes`, `parsedLaps`, `bufferedSamples`, `points`, and min/max altitudes from the parsed DTO.
   - In `importGpxWorkoutInternal`:
     - Replace the ~150-line inline pull parser loop with `GpxTrackpointParser.parse(fis)`.
     - Populate `sportName`, `workoutName`, `workoutNotes`, `parsedLaps`, `bufferedSamples`, `points`, and min/max altitudes from the parsed DTO.

---

## 5. Atomic Implementation Steps

| Step | Action | Files Affected | Invariant / Validation |
| :--- | :--- | :--- | :--- |
| **Step 1** | Create pure Kotlin DTO models for trackpoints, laps, and parsed results. | `TrackpointModels.kt` | Immutable, null-safe, pure JVM DTOs. |
| **Step 2** | Implement `TcxTrackpointParser` for streaming TCX parsing. | `TcxTrackpointParser.kt` | Handles activities, courses, trackpoints, laps, coursepoints, telemetry. Pure JVM `XmlPullParserFactory`. |
| **Step 3** | Implement `GpxTrackpointParser` for streaming GPX parsing. | `GpxTrackpointParser.kt` | Handles trkpt, rtept, trkseg, wpt, and Garmin TPX extensions. Pure JVM `XmlPullParserFactory`. |
| **Step 4** | Refactor `TcxCourseParser` to delegate to `TcxTrackpointParser`. | `TcxCourseParser.kt` | Existing `TcxCourseParserTest` passes 100%. |
| **Step 5** | Refactor `GpxRouteImporter` to delegate to `GpxTrackpointParser`. | `GpxRouteImporter.kt` | Existing `GpxRouteImporterWaypointTest` and `GpxRouteImporterElevationTest` pass 100%. |
| **Step 6** | Refactor `LegacyImportEngine` TCX & GPX import methods to delegate to shared parsers. | `LegacyImportEngine.kt` | Existing `TcxHighFidelityImportTest` and `LegacyImportEngineGpxTest` pass 100%. |
| **Step 7** | Create dedicated unit tests `TrackpointXmlParserTest.kt` verifying edge cases and parser robustness. | `TrackpointXmlParserTest.kt` | 100% pass on pure JVM test runner. |
| **Step 8** | Run full project clean-room regression suite. | Entire Project | `./gradlew testDebugUnitTest` passes 100% (0 errors, 0 failures). |

---

## 6. Verification & Gate Criteria

- [x] Traceability established to `REQ-MIG-037` and `TST-MIG-034`.
- [x] Living documentation (`docs/requirements.md` and `docs/tests.md`) in sync.
- [x] Zero duplication of XML parsing loops across workout and route modules (Rule 36).
- [x] Pure JVM execution without Android framework stub dependencies.
- [x] Gate 3 review audit passed via `review_agent.py`.
