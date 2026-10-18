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
* Include `WorkoutSummaries.COUNT + " int"` in `WorkoutSummariesDbHelper.CREATE_TABLE_EXTREMA_VALUES` and add `if (oldVersion < 25)` migration in `onUpgrade()` reusing the existing deterministic `PRAGMA table_info` helper `addColumnIfNotExists(...)` (Rule 36 — no blind `try-catch` around `ALTER TABLE`):
  ```java
  if (oldVersion < 25) {
      Log.i(TAG, "upgrading to DB version 25 (Adding count column to extrema table)");
      addColumnIfNotExists(db, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.COUNT, "int", null);
  }
  ```
* Add `EncodedStreams` DTO (`@Nullable public final String mapPolyline`, `@Nullable public final String altitudeStream`, `@Nullable public final String distanceStream`) and `@Nullable public EncodedStreams getEncodedStreams(long workoutId)` to fetch `MAP_POLYLINE`, `ALTITUDE_STREAM`, and `DISTANCE_STREAM` for a workout in a single query.
* Extend `ExtremaRecord` with `@Nullable public final Integer count;`, keeping the 5-argument constructor `ExtremaRecord(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, LatLng position)` delegating to the 6-argument constructor `ExtremaRecord(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, LatLng position, @Nullable Integer count)` with `count = null`.
* In `getExtremaForWorkouts(Collection<Long> workoutIds)`, read `int countCol = cursor.getColumnIndex(WorkoutSummaries.COUNT);` and map `Integer count = (countCol != -1 && !cursor.isNull(countCol)) ? cursor.getInt(countCol) : null;` into `new ExtremaRecord(..., count)`.
* Add overloaded `updateExtremaValue(long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType, double value, @Nullable LatLng position, @Nullable Integer count)` and `updateExtremaValue(SQLiteDatabase db, long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType, double value, @Nullable LatLng position, @Nullable Integer count)` (with the 5-argument overloads delegating with `count = null`) that puts `WorkoutSummaries.COUNT, count` into `ContentValues` when `count != null`.

### Component 2: Domain Session Layer — `LiveWorkoutSession.java` (`REQ-TRK-031.2`)
* In `LiveWorkoutSession.RunningStats`:
  * Add `public int getCount() { return count; }`.
  * Add `public void rehydrate(@Nullable Double minValue, @Nullable LatLng minPosition, @Nullable Double maxValue, @Nullable LatLng maxPosition, @Nullable Double avgValue, int sampleCount)` (plus a 4-argument overload `rehydrate(@Nullable Double minValue, @Nullable Double maxValue, @Nullable Double avgValue, int sampleCount)` delegating with `null` positions):
    * Sets `this.min = (minValue != null && Double.isFinite(minValue)) ? minValue : Double.MAX_VALUE;` and `this.minPos = (this.min != Double.MAX_VALUE) ? minPosition : null;`.
    * Sets `this.max = (maxValue != null && Double.isFinite(maxValue)) ? maxValue : -Double.MAX_VALUE;` and `this.maxPos = (this.max != -Double.MAX_VALUE) ? maxPosition : null;`.
    * If `avgValue != null && Double.isFinite(avgValue) && sampleCount > 0`, sets `this.count = sampleCount;`, `this.sum = avgValue * sampleCount;`, and `this.last = avgValue;`; otherwise sets `this.count = 0;` and `this.sum = 0.0;`.
* In `LiveWorkoutSession`:
  * Add `public void setStartLatLng(@Nullable LatLng startLatLng)` so a persisted `ExtremaType.START` coordinate can anchor `startLatLng` even if the workout was paused before the first 20-second stream sample.
  * Add `public void rehydrateSensorStats(@NonNull SensorType sensorType, @Nullable Double minValue, @Nullable LatLng minPosition, @Nullable Double maxValue, @Nullable LatLng maxPosition, @Nullable Double avgValue, int sampleCount)` (plus a 5-argument overload without positions) which obtains or creates the `RunningStats` for `sensorType` and invokes `stats.rehydrate(...)`.
  * Add `public void rehydrateStreams(@Nullable List<LatLng> preResumeLatLngs, @Nullable List<Double> preResumeAltitudes, @Nullable List<Double> preResumeDistances)`:
    * Handles `null` or empty lists independently for each stream without throwing `NullPointerException`:
      * Clears `rawLatLngs` and `sampledLatLngs`; if `preResumeLatLngs != null && !preResumeLatLngs.isEmpty()`, adds all points to both lists, sets `if (startLatLng == null) startLatLng = preResumeLatLngs.get(0);`, `lastLatLng = preResumeLatLngs.get(preResumeLatLngs.size() - 1);`, `lastLatE5 = Math.round(lastLatLng.latitude * 1e5);`, and `lastLngE5 = Math.round(lastLatLng.longitude * 1e5);`; otherwise resets `lastLatE5 = 0;` and `lastLngE5 = 0;`.
      * Clears `sampledAltitudes`; if `preResumeAltitudes != null && !preResumeAltitudes.isEmpty()`, adds all altitudes and sets `lastAltE2 = Math.round(preResumeAltitudes.get(preResumeAltitudes.size() - 1) * 100);`; otherwise resets `lastAltE2 = 0;`.
      * Clears `sampledDistances`; if `preResumeDistances != null && !preResumeDistances.isEmpty()`, adds all distances and sets `lastDistE2 = Math.round(preResumeDistances.get(preResumeDistances.size() - 1) * 100);`; otherwise resets `lastDistE2 = 0;`.

### Component 3: Service Layer — `TrackerService.java` (`REQ-TRK-031.3`)
* **Thread-Safety & Non-Blocking Rehydration (`mDbExecutor` + `mIsRehydrating` Barrier)**:
  * Add `private volatile boolean mIsRehydrating = false;` (exposed via `@VisibleForTesting boolean isRehydrating()`).
  * In `applyResumedState(@NonNull ResumedWorkoutState state)`:
    * Instantiate `mLiveSession = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES);` and set `mIsRehydrating = true;`.
    * Offload `rehydrateLiveSession(mWorkoutID, mTimeActive_s)` to `mDbExecutor`:
      ```java
      Runnable rehydrateTask = () -> {
          try {
              rehydrateLiveSession(mWorkoutID, mTimeActive_s);
          } finally {
              mIsRehydrating = false;
              if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) {
                  mTableInitializationFuture.complete(null);
              }
              if (mWorkoutID > 0) {
                  notifyTrackingStarted(mWorkoutID);
              }
          }
      };
      if (mDbExecutor != null && !mDbExecutor.isShutdown()) {
          mDbExecutor.execute(rehydrateTask);
      } else {
          rehydrateTask.run();
      }
      ```
    * In `sampleAndWriteToDb()`, guard both the `mLiveSession.addSample(...)` loop and `mLiveSession.recordStreamPoint(...)` with `if (mLiveSession != null && !mIsRehydrating)`. Because `mIsRehydrating` is `volatile` and remains `true` until `rehydrateLiveSession()` finishes on `mDbExecutor` (and `mTableInitializationFuture` only completes in `finally`), the 1 Hz `mScheduler` thread can never race with `rehydrateLiveSession()` or mutate `mLiveSession`'s non-synchronized `HashMap`/`ArrayList`s during rehydration, and the Android main thread is never blocked by stream decoding or SQLite reads.
* **Independent Fault-Isolated Stream & Extrema Rehydration (`rehydrateLiveSession(long workoutId, int activeTimeSeconds)`)**:
  * Wrapped in a top-level `try / catch (Throwable t)` so unexpected errors never crash `TrackerService`.
  * Calls `summariesManager.getEncodedStreams(workoutId)`. If non-null, decodes `mapPolyline` (`PolyUtil.decode`), `altitudeStream` (`NumericalEncodingUtils.INSTANCE.decodeDoubles`), and `distanceStream` (`NumericalEncodingUtils.INSTANCE.decodeDoubles`) in **three separate, independent `try / catch (Exception e)` blocks**. If any stream is `null`, empty, or corrupted, that stream falls back to `Collections.emptyList()` while valid sibling streams are preserved. Calls `mLiveSession.rehydrateStreams(decodedLatLngs, decodedAltitudes, decodedDistances)`.
  * Calls `summariesManager.getExtremaForWorkouts(Collections.singletonList(workoutId))`. Groups `MIN` (value + position), `MAX` (value + position), `AVG` (value + `count`), and `START` (`LATITUDE`/`LONGITUDE`) per `SensorType`:
    * If `ExtremaType.START` exists, restores `mLiveSession.setStartLatLng(...)` so pre-resume start coordinates are never overwritten if the workout was paused before the first 20-second stream point.
    * For each `SensorType` present in `extremaRecords`, determines `int sampleCount = (persistedCount != null && persistedCount > 0) ? persistedCount : (activeTimeSeconds > 0 ? activeTimeSeconds : 1);` and calls `mLiveSession.rehydrateSensorStats(sensorType, minVal, minPos, maxVal, maxPos, avgVal, sampleCount)`.
* **Statistical Drift & Mid-Workout Sensor Pairing Behavior**:
  * *v25+ Exact Sample Count vs. Legacy Pre-v25 Fallback Drift*: In `sampleAndWriteToDb()` and `finalizeLiveSession()`, `ExtremaUpdate` carries `final Integer count` (`stats.getCount()`) and persists it via `summariesManager.updateExtremaValue(summariesDb, workoutId, update.type, update.extrema, update.value, update.pos, update.count)`, giving `0.0%` drift on v25+ workouts. For legacy pre-v25 rows where `COUNT` is `NULL`, `activeTimeSeconds` (`TIME_ACTIVE_s`) is used as a 1 Hz continuous-sampling approximation (`count ≈ activeTimeSeconds`). If a sensor was temporarily disconnected or paired late in a legacy pre-v25 workout (`actualCount < activeTimeSeconds`), the fallback weights the pre-resume mean by `activeTimeSeconds / actualCount`, keeping the post-resume combined average strictly bounded between the pre-resume average and post-resume samples.
  * *Mid-Workout Sensor Pairing After Resume*: `rehydrateSensorStats` is called **only** for sensors present in `extremaRecords`. Any sensor paired for the first time *after* resume has no pre-resume row in `TABLE_EXTREMA_VALUES` and retains its clean initial `RunningStats` (`count = 0, sum = 0.0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE`), tracking post-resume samples accurately from sample 1 without overwriting already-active sensors.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Version 25 Migration, `EncodedStreams`, & `COUNT` Column in `WorkoutSummariesDatabaseManager.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseSourceMigrationTest.kt`
* Changes:
  * Add `WorkoutSummaries.COUNT = "count"`, bump `DB_VERSION` to `25`, update `CREATE_TABLE_EXTREMA_VALUES`, and call `addColumnIfNotExists(db, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.COUNT, "int", null)` in `onUpgrade(oldVersion < 25)`.
  * Add `EncodedStreams` and `getEncodedStreams(long workoutId)`.
  * Extend `ExtremaRecord` with `@Nullable public final Integer count`, update `getExtremaForWorkouts`, and add the `updateExtremaValue(..., @Nullable Integer count)` overloads.
  * Update `WorkoutSummariesDatabaseSourceMigrationTest` to verify `DB_VERSION == 25` and `COUNT` migration via `addColumnIfNotExists`.

### Step 2: Stream & `RunningStats` Rehydration API in `LiveWorkoutSession.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`
* Changes:
  * Add `RunningStats.getCount()`, `RunningStats.rehydrate(...)`, `LiveWorkoutSession.setStartLatLng(...)`, `LiveWorkoutSession.rehydrateSensorStats(...)`, and `LiveWorkoutSession.rehydrateStreams(...)` with full JavaDoc headers (`REQ-PRO-011`).

### Step 3: Asynchronous `mLiveSession` Rehydration & `mIsRehydrating` Guard in `TrackerService.java`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes:
  * Add `volatile boolean mIsRehydrating`, instantiate `mLiveSession = new LiveWorkoutSession(mWorkoutID, IMPORTANT_SENSOR_TYPES)` in `applyResumedState()`, offload `rehydrateLiveSession(mWorkoutID, mTimeActive_s)` to `mDbExecutor` with `mIsRehydrating = false` and `mTableInitializationFuture.complete(null)` in `finally`, guard `sampleAndWriteToDb()` with `!mIsRehydrating`, and persist `stats.getCount()` in `ExtremaUpdate`.

### Step 4: Unit Tests in `LiveWorkoutSessionTest.kt` and `TrackerServiceResumeTest.kt`
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* Test Cases:
  * `LiveWorkoutSessionTest`:
    * `rehydrateStreams` restores `rawLatLngs`, `sampledLatLngs`, `sampledAltitudes`, `sampledDistances`, `startLatLng`, `lastLatLng`, and delta anchors (`lastLatE5`, `lastLngE5`, `lastAltE2`, `lastDistE2`) so post-resume `recordStreamPoint` increments concatenate without jumps.
    * `rehydrateStreams(null, emptyList(), null)` and partial stream inputs safely reset empty streams without throwing `NullPointerException`.
    * `rehydrateSensorStats` restores `min`, `minPos`, `max`, `maxPos`, `sum`, and `count` (including mid-workout sensor `count = 300` vs `activeTime = 1800` and newly paired post-resume sensor starting at `count = 0`).
  * `TrackerServiceResumeTest`:
    * `applyResumedState` instantiates `mLiveSession`, rehydrates encoded streams and extrema on `mDbExecutor`, and completes `mTableInitializationFuture` after rehydration.
    * `sampleAndWriteToDb` skips `mLiveSession` mutation while `mIsRehydrating == true` (concurrency barrier verification).
    * Corrupt polyline with valid altitude/distance stream recovers valid streams independently without aborting rehydration.
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSessionTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest" --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseSourceMigrationTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests (`LiveWorkoutSessionTest`, `TrackerServiceResumeTest`, `WorkoutSummariesDatabaseSourceMigrationTest`) during Stage 4 construction, followed by the full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-3987` allows full revert without affecting `sprint/2026-42.9` or `develop`.
