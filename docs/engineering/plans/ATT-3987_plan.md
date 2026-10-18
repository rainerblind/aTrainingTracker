# Stage 3: Implementation Plan - ATT-3987: [High] Resumed workouts have no LiveWorkoutSession and miss extrema, map and derived metrics

**Ticket**: [ATT-3987](https://rainerblind.atlassian.net/browse/ATT-3987)  
**Sub-task**: [ATT-4076](https://rainerblind.atlassian.net/browse/ATT-4076) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3979](https://rainerblind.atlassian.net/browse/ATT-3979) (*Tracking Resilience & Data Integrity*)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-031`  
**Test Mapping**: `TST-TRK-023`  
**Branch**: `feature/ATT-3987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background
When `TrackerService` resumes a paused or interrupted workout (`StartMode.RESUME_BY_USER` or `StartMode.RESUME_SERVICE_RECREATION`), `recreateValuesWhenResuming()` / `applyResumedState()` restores scalar summary fields (`mWorkoutID`, `mTimeActive_s`, `mDistanceTotal_m`, `mAscend_m`, `mDescend_m`, `mLapNr`, `mSportTypeId`), **but never instantiates or rehydrates `mLiveSession` (`LiveWorkoutSession`)**. Because `mLiveSession` remains `null`, all post-resume `MIN`/`MAX`/`AVG` updates, 20-second map/altitude/distance stream increments, barometric altitude corrections, and `finalizeLiveSession()` operations (simplified polyline, spatial bounds, `START`/`END` positions, `LINE_DISTANCE_m`, Efficiency Factor / Aerobic Decoupling, Coggan Power metrics, commute/trainer inference, sport identity arbitration, route clustering, and auto-naming) are skipped.

Furthermore, naively instantiating `new LiveWorkoutSession(mTimeActive_s, IMPORTANT_SENSOR_TYPES)` without rehydrating its persisted state would corrupt both delta-encoded 20-second streams (`lastLatE5`, `lastLngE5`, `lastAltE2`, `lastDistE2` starting at `0` instead of the last pre-resume point, and `finalizeLiveSession()` overwriting pre-resume streams) and running `MIN`/`MAX`/`AVG` sensor statistics (overwriting pre-resume extrema and distorting averages for sensors paired mid-workout).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-031` (*LiveWorkoutSession Instantiation & State Rehydration on Workout Resume (`RESUME_BY_USER` & `RESUME_SERVICE_RECREATION`)*)
* **Test Mapping**: `TST-TRK-023` (*`LiveWorkoutSession` Stream & Extrema Rehydration on Workout Resume, Sample Count Persistence & `finalizeLiveSession()` Continuity Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Targeted Resume Projection Invariant (`REQ-TRK-016`)**: `TrackerService.recreateValuesWhenResuming()` MUST continue querying `WorkoutSummaries.TABLE` with the exact 7-column `RESUME_WORKOUT_PROJECTION`. Stream and extrema rehydration is performed via `WorkoutSummariesDatabaseManager` (`getEncodedStreams` and `getExtremaForWorkouts`) inside `applyResumedState()`.
2. **DTO Mapping Symmetry (Rule 20)**: `WorkoutSummariesDatabaseManager.ExtremaRecord` MUST preserve its 5-argument constructor (`ExtremaRecord(workoutId, sensorType, extremaType, value, position)`) delegating to the 6-argument constructor with `count = null`, and `getExtremaForWorkouts()` MUST extract `WorkoutSummaries.COUNT` symmetrically.
3. **Fault-Isolated Finalization (`REQ-TRK-030`)**: All `try / catch (Throwable)` guards in `endWorkout()` and `finalizeLiveSession()` MUST remain intact.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Database Layer — `WorkoutSummariesDatabaseManager.java` (`REQ-TRK-031.1`)
* Add constant `public static final String COUNT = "count";` to `WorkoutSummaries`.
* Increment `WorkoutSummariesDbHelper.DB_VERSION` from `24` to `25`.
* Include `COUNT + " INTEGER"` in `WorkoutSummariesDbHelper.CREATE_TABLE_EXTREMA_VALUES` and add `if (oldVersion < 25)` migration in `onUpgrade()`:
  ```java
  if (oldVersion < 25) {
      try {
          db.execSQL("ALTER TABLE " + WorkoutSummaries.TABLE_EXTREMA_VALUES + " ADD COLUMN " + WorkoutSummaries.COUNT + " INTEGER");
      } catch (SQLException e) {
          Log.w(TAG, "Column count might already exist in " + WorkoutSummaries.TABLE_EXTREMA_VALUES, e);
      }
  }
  ```
* Extend `ExtremaRecord` with `@Nullable public final Integer count;`, keeping the 5-argument constructor delegating to the 6-argument constructor `ExtremaRecord(long workoutId, String sensorType, String extremaType, double value, @Nullable LatLng position, @Nullable Integer count)`.
* In `getExtremaForWorkouts(List<Long> workoutIds)`, read `int countCol = cursor.getColumnIndex(WorkoutSummaries.COUNT);` and map `Integer count = (countCol != -1 && !cursor.isNull(countCol)) ? cursor.getInt(countCol) : null;` into `new ExtremaRecord(..., count)`.
* Add overloaded `public void updateExtremaValue(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, @Nullable Integer count)` (with the 4-argument overload delegating with `null`) that puts `WorkoutSummaries.COUNT, count` into `ContentValues` when `count != null`.

### Component 2: Domain Session Layer — `LiveWorkoutSession.java` (`REQ-TRK-031.2`)
* In `LiveWorkoutSession.RunningStats`:
  * Add `public int getCount() { return count; }`.
  * Add `public void rehydrate(@Nullable Double minValue, @Nullable Double maxValue, @Nullable Double avgValue, int sampleCount)`:
    * Sets `this.min = (minValue != null && Double.isFinite(minValue)) ? minValue : Double.MAX_VALUE;`
    * Sets `this.max = (maxValue != null && Double.isFinite(maxValue)) ? maxValue : -Double.MAX_VALUE;`
    * If `avgValue != null && Double.isFinite(avgValue) && sampleCount > 0`, sets `this.count = sampleCount;` and `this.sum = avgValue * sampleCount;`; otherwise sets `this.count = 0;` and `this.sum = 0;`.
    * Resets `this.minChanged = false;`, `this.maxChanged = false;`, and `this.updatedInCurrentSample = false;`.
* In `LiveWorkoutSession`:
  * Add `public void rehydrateSensorStats(@NonNull SensorType sensorType, @Nullable Double minValue, @Nullable Double maxValue, @Nullable Double avgValue, int sampleCount)` which obtains (or creates via `computeIfAbsent`) the `RunningStats` for `sensorType` and invokes `stats.rehydrate(minValue, maxValue, avgValue, sampleCount)`.
  * Add `public void rehydrateStreams(@Nullable List<LatLng> preResumeLatLngs, @Nullable List<Double> preResumeAltitudes, @Nullable List<Double> preResumeDistances)`:
    * Clears `rawLatLngs` and `sampledLatLngs`; if `preResumeLatLngs != null && !preResumeLatLngs.isEmpty()`, adds all to both lists and sets `lastLatE5 = (int) Math.round(last.latitude * 1e5)` and `lastLngE5 = (int) Math.round(last.longitude * 1e5)`.
    * Clears `sampledAltitudes`; if `preResumeAltitudes != null && !preResumeAltitudes.isEmpty()`, adds all and sets `lastAltE2 = (int) Math.round(lastAlt * 100)`.
    * Clears `sampledDistances`; if `preResumeDistances != null && !preResumeDistances.isEmpty()`, adds all and sets `lastDistE2 = (int) Math.round(lastDist * 100)`.

### Component 3: Service Layer — `TrackerService.java` (`REQ-TRK-031.3`)
* Add field `private boolean mHasStartLocationExtrema = false;` (reset to `false` on `START_NORMAL`).
* In `applyResumedState(@NonNull ResumedWorkoutState state)`:
  * Instantiate `mLiveSession = new LiveWorkoutSession(mTimeActive_s, IMPORTANT_SENSOR_TYPES);`
  * Call `rehydrateLiveSession(mWorkoutID, mTimeActive_s);`
* Implement `void rehydrateLiveSession(long workoutId, int activeTimeSeconds)` (wrapped in defensive `try / catch (Throwable t)`):
  * Obtain `WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(getApplicationContext());`.
  * Call `WorkoutSummariesDatabaseManager.EncodedStreams encodedStreams = summariesManager.getEncodedStreams(workoutId);`. If `encodedStreams != null`:
    * Decode `mapPolyline` via `PolyUtil.decode(encodedStreams.mapPolyline)` when non-empty.
    * Decode `altitudeStream` via `EncodingUtils.INSTANCE.decodeSignedStream(encodedStreams.altitudeStream, 100.0)` when non-empty.
    * Decode `distanceStream` via `EncodingUtils.INSTANCE.decodeSignedStream(encodedStreams.distanceStream, 100.0)` when non-empty.
    * Call `mLiveSession.rehydrateStreams(decodedLatLngs, decodedAltitudes, decodedDistances)`.
  * Call `List<WorkoutSummariesDatabaseManager.ExtremaRecord> extremaRecords = summariesManager.getExtremaForWorkouts(Collections.singletonList(workoutId));`. If `extremaRecords != null && !extremaRecords.isEmpty()`:
    * Group `MIN`, `MAX`, `AVG`, and `count` per `SensorType` (and detect if `ExtremaType.START` is already present for `LATITUDE`/`LONGITUDE`, setting `mHasStartLocationExtrema = true`).
    * For each `SensorType` with persisted extrema, determine `int sampleCount = (persistedCount != null && persistedCount > 0) ? persistedCount : Math.max(0, activeTimeSeconds);` and call `mLiveSession.rehydrateSensorStats(sensorType, minVal, maxVal, avgVal, sampleCount)`.
* In `sampleAndWriteToDb()`:
  * Guard the `if (mTimeActive_s == 1 || mLiveSession.getRawLatLngs().size() == 1)` `ExtremaType.START` write with `if (!mHasStartLocationExtrema && (mTimeActive_s == 1 || mLiveSession.getRawLatLngs().size() == 1))` and set `mHasStartLocationExtrema = true;` once written so resuming a workout that had 0 or 1 pre-resume 20s points does not overwrite a pre-existing `START` coordinate.
  * Pass `stats.getCount()` to `summariesManager.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.MIN/MAX/AVG, ..., stats.getCount())`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Version 25 Migration & `COUNT` Column in `WorkoutSummariesDatabaseManager.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseSourceMigrationTest.kt`
* Changes:
  * Add `WorkoutSummaries.COUNT = "count"`, bump `DB_VERSION` to `25`, update `CREATE_TABLE_EXTREMA_VALUES` and `onUpgrade(oldVersion < 25)`, extend `ExtremaRecord` and `getExtremaForWorkouts`, and add the 5-argument `updateExtremaValue(..., Integer count)` overload.
  * Update `WorkoutSummariesDatabaseSourceMigrationTest.testDbVersion_is24()` to assert `DB_VERSION >= 24`.

### Step 2: Stream & `RunningStats` Rehydration API in `LiveWorkoutSession.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`
* Changes:
  * Add `RunningStats.getCount()`, `RunningStats.rehydrate(...)`, `LiveWorkoutSession.rehydrateSensorStats(...)`, and `LiveWorkoutSession.rehydrateStreams(...)` with full JavaDoc headers (`REQ-PRO-011`).

### Step 3: `mLiveSession` Instantiation & Rehydration in `TrackerService.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes:
  * Instantiate and rehydrate `mLiveSession` in `applyResumedState()`, implement `rehydrateLiveSession(long workoutId, int activeTimeSeconds)`, guard `ExtremaType.START` overwrite with `mHasStartLocationExtrema`, and pass `stats.getCount()` to `updateExtremaValue(...)`.

### Step 4: Unit Tests in `LiveWorkoutSessionTest.kt` and `TrackerServiceResumeTest.kt`
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSessionTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseSourceMigrationTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`LiveWorkoutSessionTest`, `TrackerServiceResumeTest`, `WorkoutSummariesDatabaseSourceMigrationTest`) during Stage 4 construction, followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3987` allows full revert without affecting `sprint/2026-42.9` or `develop`.
