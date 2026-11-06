# Stage 3: Implementation Plan - ATT-5479: Reject (0.0, 0.0) Null-Island EXIF GPS Coordinates

**Ticket**: [ATT-5479](https://rainerblind.atlassian.net/browse/ATT-5479)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5479`  
**Requirement Mapping**: `REQ-UI-448.3`, `REQ-UI-448.4`, `REQ-UI-447.2`  
**Test Mapping**: `TST-UI-448.3`, `TST-UI-448.4`, `TST-UI-447.1`, `TST-UI-448.6`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Zeroed EXIF GPS tags (`0/1, 0/1`) return `true` with `(0.0, 0.0)` in `ExifInterface.getLatLong()`. `WorkoutPhotoManager.extractExifMetadata` accepts these as non-null, bypassing fallback trackpoint interpolation and storing `(0.0, 0.0)` into SQLite `workout_photos`. `WorkoutRepository.getWorkoutMarkers` and `MapBoundsController` include these coordinates in camera bounds, zooming out across 5,000 km to the equator and hiding local workout tracks (observed on Workout 457 *"Zum Bäcker #47"*).

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-448.3`**: `GeoUtils.isValidGpsCoordinate` predicate & marker filtering (`TST-UI-448.3`).
* **`REQ-UI-448.4`**: EXIF `(0.0, 0.0)` rejection, track interpolation fallback & DB self-healing (`TST-UI-448.4`).
* **`REQ-UI-447.2`**: Photo track overlay silhouette & bounds coordinate sanitization (`TST-UI-447.1`).
* **`REQ-UI-448`**: System invariants, clean-room regression & zero continental zoom (`TST-UI-448.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Authentic Coordinates Preserved**: Genuine non-zero GPS coordinates remain untouched.
2. **Track Interpolation Priority**: Photos taken during workouts with invalid EXIF GPS acquire valid track coordinates.
3. **Safe Fallback**: Photos taken outside workouts or without trackpoints safely retain images with `null` GPS.
4. **Tight Map Bounds**: Map camera strictly encloses authentic workout tracks and valid markers.
5. **Non-Destructive DB Updates**: `updatePhotoCoordinates` only updates `latitude` and `longitude` fields.

---

## 4. Proposed Architectural Changes
* **`GeoUtils.kt`**: Add pure predicate `isValidGpsCoordinate(lat: Double?, lng: Double?): Boolean` (finite, `lat in -90..90`, `lng in -180..180`, `abs(lat) >= 1e-5 || abs(lng) >= 1e-5`).
* **`WorkoutSummariesDatabaseManager.java`**: Add `updatePhotoCoordinates(long photoId, @Nullable Double latitude, @Nullable Double longitude): boolean`.
* **`WorkoutPhotoManager.kt`**:
  * In `extractExifMetadata`: Reject coordinates failing `isValidGpsCoordinate`, returning `null, null`.
  * Add `selfHealPhotos(context, workoutId, photos)`: Detect `(0.0, 0.0)` rows, re-interpolate against `WorkoutSamples.db` trackpoints using `taken_at_ms`, and persist via `updatePhotoCoordinates`.
* **`WorkoutRepository.kt`**: Wire `selfHealPhotos` in `getWorkoutPhotos` and filter photo markers with `isValidGpsCoordinate` in `getWorkoutMarkers`.
* **`MapBehaviors.kt`**: In `MapBoundsController`, guard `builder.include` calls with `isValidGpsCoordinate`.
* **`PhotoTrackOverlayComposer.kt`**: Filter polyline points with `isValidGpsCoordinate`.
* **UI Consistency**: No layout changes; markers retain existing `LocationMarker` pin style and Material 3 tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Pure Coordinate Validator (`GeoUtils.kt`, `GeoUtilsTest.kt`)**:
   * Add `GeoUtils.isValidGpsCoordinate(lat: Double?, lng: Double?): Boolean`.
   * Add unit tests verifying Null Island rejection, boundary limits, `NaN`/`Infinity`, and authentic coordinates.
2. **Step 2: SQLite Update Method (`WorkoutSummariesDatabaseManager.java`)**:
   * Add `updatePhotoCoordinates(long photoId, @Nullable Double latitude, @Nullable Double longitude)`.
3. **Step 3: EXIF Guard & Self-Healing (`WorkoutPhotoManager.kt`, `WorkoutPhotoManagerTest.kt`)**:
   * Guard `extractExifMetadata` to return `null, null` when `!isValidGpsCoordinate(lat, lng)`.
   * Implement `selfHealPhotos()` to re-interpolate `(0.0, 0.0)` rows using `taken_at_ms` or clear to `null`.
   * Add unit tests covering EXIF `(0.0, 0.0)` rejection and DB self-healing.
4. **Step 4: Marker & Bounds Guard (`WorkoutRepository.kt`, `MapBehaviors.kt`, `PhotoTrackOverlayComposer.kt`)**:
   * Guard `WorkoutRepository.getWorkoutMarkers` and `MapBoundsController` against invalid coordinates.
   * Add contract tests in `WorkoutPhotoUiContractTest.kt`.
5. **Step 5: Governance & Regression**:
   * Run targeted test suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.*" --tests "com.atrainingtracker.trainingtracker.database.WorkoutPhotoManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.
   * Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted package tests, contract tests, and full governance verification.
* **Rollback**: Branch isolation on `feature/ATT-5479` ensures easy revert before squashing onto sprint branch.
