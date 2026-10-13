# Stage 2: Requirement & Test Specification - ATT-2667: Simplify raw GPS track with Douglas-Peucker for workout summary map previews

**Ticket**: [ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667)  
**Sub-task**: [ATT-2848](https://atrainingtracker.atlassian.net/browse/ATT-2848) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-DAT-023` (*Workout Route Preview Map Simplification via Douglas-Peucker & Compact Scalar Streams*)  
**Test Spec ID**: `TST-DAT-018`  
**Branch**: `improvement/ATT-2667`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-DAT-023)

### 1.1 Problem Statement & Rationale
Workout summary list items (`WorkoutSummary.kt`) display a route thumbnail preview (`PathPreviewMap.kt`) driven by the encoded polyline string stored in `WorkoutSummaries.MAP_POLYLINE`. In addition, summary rows persist scalar streams for elevation and distance (`WorkoutSummaries.ALTITUDE_STREAM` and `WorkoutSummaries.DISTANCE_STREAM`) for elevation profile rendering (`ElevationProfile.kt`).

Previously, imported workouts (`LegacyImportEngine.kt`) stored 100% of raw 1 Hz GPS trackpoints into `WorkoutSummaries.MAP_POLYLINE` without simplification. A 2-hour workout (~7,200 points) produced polyline strings of tens of kilobytes, causing high memory usage, heavy GC pressure, visual noise, and scrolling jank in `WorkoutSummary` cards. Furthermore, `ALTITUDE_STREAM` and `DISTANCE_STREAM` were written with thousands of raw 1 Hz entries.

In contrast, live-tracked workouts (`LiveWorkoutSession.java`) only sampled positions every 20 seconds (`WorkoutSummaries.ENCODING_STEP_SIZE = 20`). While lightweight, time-based stepping kept redundant collinear points on straight roads and cut corners on sharp turns. Simplifying already downsampled (20-second spaced) points flattens curves. Ramer-Douglas-Peucker simplification must always operate directly on the raw, full-resolution GPS points.

### 1.2 Functional & Architectural Requirements
1. **Imported Workout Route Simplification (`LegacyImportEngine.kt`)**:
   - In `recalculateStats()`, when generating `WorkoutSummaries.MAP_POLYLINE` from parsed trackpoints (`points: List<LatLng>`), the system SHALL apply the Ramer-Douglas-Peucker simplification algorithm via `PolyUtil.simplify(points, 10.0)` (10.0 meters tolerance) before encoding to polyline string.
   - If `points` contains fewer than 3 coordinates, `PolyUtil.simplify` SHALL return `points` unchanged without runtime exceptions.
2. **Scalar Stream Downsampling (`LegacyImportEngine.kt`)**:
   - In `recalculateStats()`, the system SHALL downsample parsed `altitudes` and `distances` using `WorkoutSummaries.ENCODING_STEP_SIZE` (20) intervals (`(index + 1) % 20 == 0`, with fallback to the last element if non-empty but fewer than 20 points) before encoding via `NumericalEncodingUtils.encodeDoubles`.
   - The downsampled streams SHALL maintain identical sample counts and delta encoding.
3. **Live-Tracked Workout Raw Point Collection (`LiveWorkoutSession.java`)**:
   - `LiveWorkoutSession` SHALL accumulate every valid 1 Hz GPS fix (`currentPos != null`) into an internal `rawLatLngs` collection across active session ticks.
   - It SHALL expose `getRawLatLngs(): List<LatLng>` and `addRawLatLng(LatLng latLng)`.
   - Incremental 20-second stream updates (`StreamIncrement`) during active tracking SHALL be preserved untouched to ensure real-time in-progress map and elevation preview compatibility.
4. **Live Session Finalization Simplification (`TrackerService.java`)**:
   - In `finalizeLiveSession()`, the system SHALL simplify the full raw 1 Hz GPS track via `PolyUtil.simplify(mLiveSession.getRawLatLngs(), 10.0)` directly (falling back to `mLiveSession.getSampledLatLngs()` only if `rawLatLngs` is empty) and persist the resulting simplified polyline to `WorkoutSummaries.MAP_POLYLINE` in SQLite and `WorkoutRepository`.
5. **Preservation of Core Invariants**:
   - Spatial bounding boxes (`BOUND_MIN_LAT`, `BOUND_MAX_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LNG`) SHALL remain calculated from raw GPS coordinates to guarantee accurate zero-latency viewport framing.
   - Raw 1 Hz telemetry samples in `WorkoutSamples` database tables MUST remain full-resolution and unaltered.
   - Zero SQLite database schema upgrade or table rewrite is required (`DB_VERSION = 24` preserved).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Imported Workout Simplification)**:
  * *Given* a workout file (TCX, GPX, or FIT) with 1 Hz trackpoints (~7,000 points)
  * *When* the file is imported via `LegacyImportEngine`
  * *Then* `MAP_POLYLINE` is generated from `PolyUtil.simplify(points, 10.0)`, reducing the point count to ~150–350 points without losing curve fidelity.
* **Criterion 2 (Tracked Workout Simplification from Raw GPS)**:
  * *Given* an active tracking session receiving 1 Hz GPS location fixes
  * *When* the athlete finishes and finalizes the workout
  * *Then* `TrackerService.finalizeLiveSession()` simplifies the full raw GPS points using `PolyUtil.simplify(rawLatLngs, 10.0)` rather than simplifying the 20-second sampled points.
* **Criterion 3 (Scalar Stream Compactness)**:
  * *Given* an imported workout with altitude and distance data
  * *When* `recalculateStats` writes `ALTITUDE_STREAM` and `DISTANCE_STREAM` to `WorkoutSummaries`
  * *Then* the streams are sampled at `ENCODING_STEP_SIZE = 20` intervals to prevent summary row bloat.
* **Criterion 4 (Zero DB Migration Requirement)**:
  * *Given* existing workouts in the database
  * *When* the app runs with the new simplification logic
  * *Then* no database version upgrade or background table rewrite is triggered.

### 1.4 System Invariants
- Database schemas for `WorkoutSummaries.db`, `WorkoutSamples.db`, and `Laps.db` are immutable (`DB_VERSION = 24`).
- Raw 1 Hz sample points recorded in `WorkoutSamplesDatabaseManager` are 100% preserved.
- Incremental 20s stream updates during active tracking are preserved for in-progress preview maps.
- Full clean-room regression pass rate: 100%.

---

## 2. Test Specification (TST-DAT-018)

### 2.1 Scope & Test Matrix

| Test Suite / Method | Type | Description |
|:---|:---|:---|
| `LegacyImportEngineSimplificationTest.testImportPolylineSimplification()` | Unit | Verify `recalculateStats()` simplifies 7,200 raw 1 Hz trackpoints to ~150–350 points using `PolyUtil.simplify(points, 10.0)`. |
| `LegacyImportEngineSimplificationTest.testScalarStreamDownsampling()` | Unit | Verify `altitudes` and `distances` (7,200 points) are downsampled by factor of 20 to exactly 360 points. |
| `LegacyImportEngineSimplificationTest.testShortWorkoutHandling()` | Unit | Verify workouts with $<20$ points retain at least 1 scalar sample and do not throw exceptions. |
| `LiveWorkoutSessionTest.testRawLatLngsCollection()` | Unit | Verify `LiveWorkoutSession` accumulates all raw 1 Hz GPS fixes in `rawLatLngs` while keeping 20s `sampledLatLngs` separate. |
| `TrackerServiceFinalizePolylineTest.testFinalizeSimplifiesRawPoints()` | Unit | Verify `TrackerService.finalizeLiveSession()` runs `PolyUtil.simplify(rawLatLngs, 10.0)` and persists the simplified polyline to `WorkoutSummaries.MAP_POLYLINE`. |
| `Full Clean-Room Regression` | Integration | Execute `./gradlew testDebugUnitTest` verifying 0 regressions across all modules. |

### 2.2 Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Status |
|:---|:---|:---|:---|
| REQ-DAT-023 Cl. 1 (Import Simplification) | `LegacyImportEngineSimplificationTest.testImportPolylineSimplification` | `LegacyImportEngine.kt` | Planned |
| REQ-DAT-023 Cl. 2 (Scalar Stream Downsampling) | `LegacyImportEngineSimplificationTest.testScalarStreamDownsampling` | `LegacyImportEngine.kt` | Planned |
| REQ-DAT-023 Cl. 3 (Live Raw Point Collection) | `LiveWorkoutSessionTest.testRawLatLngsCollection` | `LiveWorkoutSession.java` | Planned |
| REQ-DAT-023 Cl. 4 (Live Finalization Simplification) | `TrackerServiceFinalizePolylineTest.testFinalizeSimplifiesRawPoints` | `TrackerService.java` | Planned |
| REQ-DAT-023 Cl. 5 (Invariants & Regression) | Full clean-room suite (`testDebugUnitTest`) | Entire Repository | Planned |
