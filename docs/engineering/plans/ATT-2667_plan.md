# Stage 3: Implementation Plan - ATT-2667: Simplify raw GPS track with Douglas-Peucker for workout summary map previews

**Ticket**: [ATT-2667](https://rainerblind.atlassian.net/browse/ATT-2667)  
**Sub-task**: [ATT-2849](https://rainerblind.atlassian.net/browse/ATT-2849) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2455](https://rainerblind.atlassian.net/browse/ATT-2455) (*Architecture Redesign - Legacy Replacement*)  
**Target Release**: `4.9.38.4`  
**Active Sprint**: `Sprint 2026-41.4`  
**Requirement Mapping**: `REQ-DAT-023` (*Workout Route Preview Map Simplification via Douglas-Peucker & Compact Scalar Streams*)  
**Test Mapping**: `TST-DAT-018` (*Raw GPS Track Douglas-Peucker Simplification and Scalar Stream Downsampling Verification*)  
**Branch**: `improvement/ATT-2667`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

In aTrainingTracker, workout route previews (`WorkoutSummaries.MAP_POLYLINE`) and scalar elevation/distance streams are saved in the `WorkoutSummaries` table for instant thumbnail rendering in workout lists and aftermath maps.
Currently:
1. `LegacyImportEngine.kt` (`recalculateStats()`): Encodes every single 1 Hz trackpoint without simplification, resulting in 10,000+ point polylines that balloon memory and SQLite storage for long workouts. Additionally, scalar streams (`ALTITUDE_STREAM`, `DISTANCE_STREAM`) are encoded at 1 Hz rather than adhering to the established 20-second sampling interval (`WorkoutSummaries.ENCODING_STEP_SIZE`).
2. `LiveWorkoutSession.java` & `TrackerService.java`: Live recording samples track points at 20-second fixed time intervals (`mLiveSession.getSampledLatLngs()`), which cuts corners and truncates tight switchbacks and sharp curves in map previews.

The solution applies the Ramer-Douglas-Peucker algorithm (`PolyUtil.simplify(points, 10.0)`) across raw 1 Hz trackpoints upon finalization and import, while downsampling scalar streams in `recalculateStats()` to match `WorkoutSummaries.ENCODING_STEP_SIZE` (20s).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-023` (*Workout Route Preview Map Simplification via Douglas-Peucker & Compact Scalar Streams*)
  - Scope: `LegacyImportEngine.kt`, `LiveWorkoutSession.java`, `TrackerService.java`.
  - Simplification tolerance: 10.0 meters.
  - Scalar stream downsampling: `ENCODING_STEP_SIZE` (20 samples).
  - Spatial bounds: Computed from full raw points to preserve bounding extents.
* **Test Mapping**: `TST-DAT-018` (*Raw GPS Track Douglas-Peucker Simplification and Scalar Stream Downsampling Verification*)
  - Unit test verification covering Douglas-Peucker compression ratio, curve preservation, and scalar downsampling.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Schema Alteration**: `DB_VERSION = 24` remains unchanged. No SQLite migrations or column alterations in `WorkoutSummaries` or `WorkoutSamples`.
2. **Raw High-Resolution Preservation**: Raw 1 Hz data points stored in `WorkoutSamples` (GPS, heart rate, cadence, power, elevation) remain completely untouched and unaltered.
3. **Bounding Box Fidelity**: Spatial bounding coordinates (`BOUND_MIN_LAT`, `BOUND_MAX_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LNG`) are derived from the unsimplified raw point collection to ensure zero shrinkage of map framing extents.
4. **Live Incremental Compatibility**: Live session `StreamIncrement` generation every 20 seconds during active recording is preserved for incremental database writes and real-time UI previews.
5. **Thread Safety & Dispatcher Affinity**: Database updates in `LegacyImportEngine` remain confined to `Dispatchers.IO` / database thread.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2667` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `LiveWorkoutSession.java` (`com.atrainingtracker.trainingtracker.tracker`)
- Add an in-memory raw trackpoint collection:
  ```java
  private final List<LatLng> rawLatLngs = new ArrayList<>();
  ```
- In `recordStreamPoint(LatLng latLng, Double altitude, Double distance)`:
  - If `latLng != null`, append `latLng` to `rawLatLngs`.
- Expose methods:
  - `public List<LatLng> getRawLatLngs()` returning a defensive copy `new ArrayList<>(rawLatLngs)`.
  - `public void addRawLatLng(LatLng latLng)` for testability and point injection.

### Component 2: `TrackerService.java` (`com.atrainingtracker.trainingtracker.tracker`)
- In `finalizeLiveSession()`:
  - Retrieve raw trackpoints from `mLiveSession.getRawLatLngs()`.
  - If `rawLatLngs` is non-empty, simplify with `PolyUtil.simplify(rawLatLngs, 10.0)`.
  - If `rawLatLngs` is empty (e.g. legacy/mock session), fallback gracefully to `mLiveSession.getSampledLatLngs()`.
  - Encode the resulting points via `PolyUtil.encode(...)` and store in `WorkoutSummaries.MAP_POLYLINE` and `WorkoutRepository`.

### Component 3: `LegacyImportEngine.kt` (`com.atrainingtracker.trainingtracker.migration`)
- In `recalculateStats(...)`:
  - Simplify raw points: `val simplifiedPoints = if (points.isNotEmpty()) PolyUtil.simplify(points, 10.0) else emptyList()`
  - Encode simplified points: `val polyline = if (simplifiedPoints.isNotEmpty()) PolyUtil.encode(simplifiedPoints) else ""`
  - Downsample scalar streams `altitudes` and `distances` using `WorkoutSummaries.ENCODING_STEP_SIZE` (20) so imported workouts match live recording stream granularity:
    ```kotlin
    val sampledAltitudes = if (altitudes.size > WorkoutSummaries.ENCODING_STEP_SIZE) {
        altitudes.filterIndexed { index, _ -> (index + 1) % WorkoutSummaries.ENCODING_STEP_SIZE == 0 }
    } else {
        altitudes
    }
    val sampledDistances = if (distances.size > WorkoutSummaries.ENCODING_STEP_SIZE) {
        distances.filterIndexed { index, _ -> (index + 1) % WorkoutSummaries.ENCODING_STEP_SIZE == 0 }
    } else {
        distances
    }
    ```
  - Bounding box calculations continue to use the complete raw `points` list.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. (Under-the-hood data simplification and stream compaction without modifying UI composables or layouts).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `LiveWorkoutSession.java`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`
* Changes:
  - Add `rawLatLngs` list.
  - Collect `latLng` in `recordStreamPoint`.
  - Add `getRawLatLngs()` and `addRawLatLng(LatLng)`.

### Step 2: Update `TrackerService.java`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes:
  - In `finalizeLiveSession()`, apply `PolyUtil.simplify(pointsToSimplify, 10.0)` where `pointsToSimplify` defaults to `mLiveSession.getRawLatLngs()`.

### Step 3: Update `LegacyImportEngine.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* Changes:
  - In `recalculateStats()`, simplify `points` using `PolyUtil.simplify(points, 10.0)`.
  - Downsample `altitudes` and `distances` using step size 20 before `NumericalEncodingUtils.encodeDoubles`.

### Step 4: Unit Test Implementation (`WorkoutTrackSimplificationTest.kt` / `LiveWorkoutSessionTest.java`)
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionTest.java` (or new test file `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/WorkoutTrackSimplificationTest.kt`)
* Verifications:
  - Verify Douglas-Peucker 10.0m tolerance reduces collinear points while preserving sharp bends.
  - Verify `LiveWorkoutSession` accumulates `rawLatLngs` on every `recordStreamPoint` call while maintaining 20-step `StreamIncrement`.
  - Verify downsampling of scalar streams down to 1/20th length when input size exceeds 20.
* Targeted Unit Test Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSessionTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.WorkoutTrackSimplificationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted unit tests during Stage 4 construction, followed by full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Changes are entirely isolated to git feature branch `improvement/ATT-2667`. If any unexpected regressions occur, branch can be cleanly reset or deleted without impacting `sprint/2026-41.4`.
