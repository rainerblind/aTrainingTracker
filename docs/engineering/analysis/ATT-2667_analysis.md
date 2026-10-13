# Stage 1 Analysis: ATT-2667 - Simplify raw GPS track with Douglas-Peucker for workout summary map previews

**Ticket**: [ATT-2667](https://atrainingtracker.atlassian.net/browse/ATT-2667)  
**Sub-task**: [ATT-2847](https://atrainingtracker.atlassian.net/browse/ATT-2847) (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2667`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

Workout summary list items (`WorkoutSummary.kt`) display a route thumbnail preview (`PathPreviewMap.kt`) driven by the encoded polyline string stored in `WorkoutSummaries.MAP_POLYLINE`. In addition, summary rows persist scalar streams for elevation and distance (`WorkoutSummaries.ALTITUDE_STREAM` and `WorkoutSummaries.DISTANCE_STREAM`) for elevation profile rendering (`ElevationProfile.kt`).

Currently, there is an architectural inconsistency in how these columns are populated depending on the workout's provenance:
1. **Imported Workouts (`LegacyImportEngine.kt`)**:
   - `recalculateStats()` directly passes 100% of raw 1 Hz parsed GPS trackpoints to `PolyUtil.encode(points)` without geometric simplification.
   - For a 2-hour workout (~7,200 trackpoints), the resulting polyline string spans tens of kilobytes. When decoded repeatedly during list scrolling in `WorkoutSummary` cards, this causes heavy memory allocation, GC pressure, visual noise, and scrolling frame drops (jank).
   - Furthermore, `altitudes` and `distances` are written as raw 1 Hz streams without downsampling, storing thousands of scalar entries per row.
2. **Tracked Workouts (`LiveWorkoutSession.java` & `TrackerService.java`)**:
   - During live tracking, `LiveWorkoutSession` only records positions every 20 seconds (`WorkoutSummaries.ENCODING_STEP_SIZE = 20`) in `sampledLatLngs`.
   - While time-based sampling produces ~180 points/hour, it retains redundant collinear points on straight roads and cuts corners on sharp turns between 20-second ticks.
3. **The Double-Downsampling Pitfall**:
   - Applying Douglas-Peucker simplification to already time-downsampled (20-second spaced) points cuts corners and flattens curves.
   - To achieve crisp, faithful route previews, Ramer-Douglas-Peucker simplification must always operate directly on the raw, full-resolution GPS trackpoints.

**Expected Solution**:
- For imported workouts (`LegacyImportEngine.kt`), simplify raw 1 Hz trackpoints using `PolyUtil.simplify(points, 10.0)` before encoding to `MAP_POLYLINE`. Downsample `ALTITUDE_STREAM` and `DISTANCE_STREAM` at `ENCODING_STEP_SIZE = 20` intervals.
- For tracked workouts (`LiveWorkoutSession.java` & `TrackerService.java`), collect raw 1 Hz GPS positions during active tracking, maintain 20-second incremental stream updates during tracking for live preview compatibility, and upon workout completion in `finalizeLiveSession()`, simplify the raw 1 Hz track via `PolyUtil.simplify(rawLatLngs, 10.0)` into `MAP_POLYLINE`.
- Zero database schema migrations or table rewrites (`DB_VERSION` remains 24).

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Import Engine Discrepancy (`LegacyImportEngine.kt`)
In `LegacyImportEngine.kt:1954-1961`:
```kotlin
// 3. Map & Streams
val polyline = if (points.isNotEmpty()) PolyUtil.encode(points) else ""
values.put(WorkoutSummaries.MAP_POLYLINE, polyline)
if (altitudes.isNotEmpty()) {
    values.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.encodeDoubles(altitudes))
}
if (distances.isNotEmpty()) {
    values.put(WorkoutSummaries.DISTANCE_STREAM, NumericalEncodingUtils.encodeDoubles(distances))
}
```
Here, `points`, `altitudes`, and `distances` contain the complete raw track from the parsed file (TCX, GPX, or FIT).
- `points`: 1 Hz GPS coordinates. A 3-hour ride contains ~10,800 points. Encoding all 10,800 points produces a polyline string of ~30 KB.
- `altitudes` & `distances`: Raw 1 Hz samples, storing thousands of delta-encoded values.
Because no simplification or downsampling occurs, imported workouts bloat `WorkoutSummaries.db`.

### 2.2 Live Tracking Discrepancy (`LiveWorkoutSession.java` & `TrackerService.java`)
In `LiveWorkoutSession.java`:
```java
public StreamIncrement recordStreamPoint(LatLng latLng, Double altitude, Double distance) {
    stepCounter++;
    if (stepCounter >= WorkoutSummaries.ENCODING_STEP_SIZE) {
        stepCounter = 0;
        ...
        if (latLng != null) {
            sampledLatLngs.add(latLng);
            ...
        }
    }
}
```
`LiveWorkoutSession` only retains `sampledLatLngs` (1 point every 20 seconds).
In `TrackerService.java:1364`:
```java
String polyline = PolyUtil.encode(mLiveSession.getSampledLatLngs());
```
When finalizing a session, `TrackerService` simply encodes the 20-second sampled points. It does not retain or simplify the raw 1 Hz GPS points received during tracking.

### 2.3 Douglas-Peucker Utility in Codebase
`PolyUtil.simplify(List<LatLng> polyline, double tolerance)` is provided by the Google Maps Android API Utility library (`com.google.maps.android.PolyUtil`).
- `tolerance` is in meters.
- A tolerance of 10.0 meters preserves all real switchbacks, turns, and curves while eliminating straight-line redundancy and microscopic GPS jitter, reducing point counts from ~7,200 to ~150–350 points (a ~95% reduction in vector complexity with negligible perceptual loss).
- If `polyline` has fewer than 3 points, `PolyUtil.simplify` returns the input list unchanged without errors.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * In `LegacyImportEngine.kt`:
    - Apply `PolyUtil.simplify(points, 10.0)` to raw 1 Hz GPS trackpoints before encoding into `WorkoutSummaries.MAP_POLYLINE`.
    - Downsample scalar streams (`altitudes` and `distances`) using `WorkoutSummaries.ENCODING_STEP_SIZE` (20) intervals before encoding into `ALTITUDE_STREAM` and `DISTANCE_STREAM`.
  * In `LiveWorkoutSession.java`:
    - Collect raw 1 Hz `LatLng` coordinates into `rawLatLngs` list on every sampling tick where `latLng != null`.
    - Expose `getRawLatLngs()` and `addRawLatLng(LatLng)`.
    - Retain existing 20-second `sampledLatLngs`, `sampledAltitudes`, and `sampledDistances` incremental stream logic during live tracking.
  * In `TrackerService.java`:
    - In `finalizeLiveSession()`, simplify the raw GPS points via `PolyUtil.simplify(mLiveSession.getRawLatLngs(), 10.0)` (falling back to `sampledLatLngs` if `rawLatLngs` is empty) and persist the resulting simplified polyline to `WorkoutSummaries.MAP_POLYLINE`.
  * Unit and contract tests:
    - Test imported workout polyline simplification and scalar stream downsampling in `LegacyImportEngineTest`.
    - Test raw coordinate collection and finalization in `LiveWorkoutSessionTest` and `TrackerServiceTest`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No SQLite database schema migrations or table altering (`DB_VERSION` remains 24).
  * No retroactive recalculation or batch rewriting of historical `WorkoutSummaries.db` records.
  * No changes to `WorkoutSamplesDatabaseManager` (per-second detailed samples tables remain 100% full-resolution 1 Hz).
  * No modification to map rendering components (`ATrainingTrackerMap.kt`, `PathPreviewMap.kt`, `TrackOnMapScreen.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-DAT-004` (*Encoded polylines and streams for storage/map*) in `docs/requirements.md`, targeting `LegacyImportEngine.kt`, `TrackerService.java`, and `LiveWorkoutSession.java`.
* **Historical Origin & Commit Trace**:
  - `REQ-DAT-004` (Sprint 2015-V15) introduced `WorkoutSummaries.ENCODING_STEP_SIZE = 20` and delta encoding via `NumericalEncodingUtils`.
  - In `LiveWorkoutSession`, points were downsampled to 20-second steps.
  - In `LegacyImportEngine.kt`, `PolyUtil.encode(points)` took raw 1 Hz points without simplification or scalar downsampling.
* **Root Reason for Existing Formulation**:
  - In `LegacyImportEngine.kt`, raw points were directly encoded to preserve fidelity, but for a 2-hour workout this stores ~7,200 points in `WorkoutSummaries.MAP_POLYLINE`, causing high memory consumption, visual noise, and scrolling jank in `PathPreviewMap`.
  - In `LiveWorkoutSession.java`, points were downsampled naively by fixed 20-second intervals to minimize DB storage, but time-based downsampling retains redundant collinear points on straight segments and cuts corners when simplified subsequently.
* **Preservation of Core Invariants**:
  - Raw GPS data in `WorkoutSamples` database is completely untouched (full 1 Hz fidelity preserved for deep analysis).
  - During live tracking, incremental 20-second stream persistence (`appendToMapAndStreams`) is preserved so in-progress preview maps continue to function without disruption.
  - Spatial bounding box calculations (`minLat`, `maxLat`, `minLng`, `maxLng`) remain grounded in true coordinate extremes (`points`).
  - Zero database schema migrations or table modifications (`DB_VERSION` remains 24).
  - `ALTITUDE_STREAM` and `DISTANCE_STREAM` retain matching sample counts and delta encoding.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Import Engine Simplification (`LegacyImportEngine.kt`)
In `recalculateStats()`:
```kotlin
// 3. Map & Streams
val simplifiedPoints = if (points.isNotEmpty()) PolyUtil.simplify(points, 10.0) else emptyList()
val polyline = if (simplifiedPoints.isNotEmpty()) PolyUtil.encode(simplifiedPoints) else ""
values.put(WorkoutSummaries.MAP_POLYLINE, polyline)

val sampledAltitudes = if (altitudes.isNotEmpty()) {
    altitudes.filterIndexed { index, _ -> (index + 1) % WorkoutSummaries.ENCODING_STEP_SIZE == 0 }
} else emptyList()
if (sampledAltitudes.isNotEmpty()) {
    values.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.encodeDoubles(sampledAltitudes))
}

val sampledDistances = if (distances.isNotEmpty()) {
    distances.filterIndexed { index, _ -> (index + 1) % WorkoutSummaries.ENCODING_STEP_SIZE == 0 }
} else emptyList()
if (sampledDistances.isNotEmpty()) {
    values.put(WorkoutSummaries.DISTANCE_STREAM, NumericalEncodingUtils.encodeDoubles(sampledDistances))
}
```
Bounding boxes (`BOUND_MIN_LAT`, etc.) continue to be computed from raw `points` to ensure 100% boundary accuracy.

### 5.2 Live Tracking Session (`LiveWorkoutSession.java`)
Maintain `rawLatLngs`:
```java
private final List<LatLng> rawLatLngs = new ArrayList<>();

public void addRawLatLng(LatLng latLng) {
    if (latLng != null) {
        rawLatLngs.add(latLng);
    }
}

public List<LatLng> getRawLatLngs() {
    return Collections.unmodifiableList(rawLatLngs);
}
```
In `recordStreamPoint`:
```java
if (latLng != null) {
    rawLatLngs.add(latLng);
}
```
On every 1 Hz sampling tick, `currentPos` is accumulated in `rawLatLngs`.

### 5.3 Live Session Finalization (`TrackerService.java`)
In `finalizeLiveSession()`:
```java
List<LatLng> rawPoints = mLiveSession.getRawLatLngs();
List<LatLng> pointsToEncode;
if (!rawPoints.isEmpty()) {
    pointsToEncode = PolyUtil.simplify(rawPoints, 10.0);
} else {
    pointsToEncode = mLiveSession.getSampledLatLngs();
}
String polyline = PolyUtil.encode(pointsToEncode);
```
The simplified polyline is written to `WorkoutSummaries.MAP_POLYLINE` via `summariesManager.updateMapAndStreams(mWorkoutID, polyline, altStream, distStream)` and `repository.setMapPolyline(mWorkoutID, polyline)`.

---

## 6. Test Strategy & Risk Assessment

1. **Unit Tests**:
   - `LegacyImportEngineSimplificationTest`: Test that raw 1 Hz imported track (~7,000 points) simplifies with `PolyUtil.simplify(points, 10.0)` to ~150–350 points, and scalar streams are downsampled by factor of 20.
   - `LiveWorkoutSessionRawPointsTest`: Verify `LiveWorkoutSession` records all raw GPS fixes and maintains 20-second sampled points independently.
   - `TrackerServiceFinalizePolylineTest`: Verify `finalizeLiveSession()` encodes the Douglas-Peucker simplified polyline from raw GPS points.
2. **Clean-Room Verification**:
   - Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all 100+ test suites.
3. **Risk Analysis**:
   - *Risk*: Short workouts with fewer than 3 GPS points.
   - *Mitigation*: `PolyUtil.simplify` on lists of size $<3$ returns the list verbatim without throwing exceptions.
   - *Risk*: Workouts with fewer than 20 scalar points.
   - *Mitigation*: If `altitudes.isNotEmpty()` but `sampledAltitudes.isEmpty()`, fallback to `listOf(altitudes.last())` ensures the stream is not dropped.
