# Stage 1 Analysis: ATT-3987 — [High] Resumed workouts have no LiveWorkoutSession and miss extrema, map and derived metrics

## 1. Executive Summary & Problem Statement
When a workout is paused and resumed (`StartMode.RESUME_BY_USER`) or recovered after Android restarts `TrackerService` (`StartMode.RESUME_SERVICE_RECREATION`), `TrackerService.onStartCommand()` calls `recreateValuesWhenResuming()`, which restores scalar counters (`mWorkoutID`, `mActiveTime_s`, `mTotalDistance_m`, `mAscend_m`, `mDescend_m`, `mLapNr`, `mSportTypeId`), **but never instantiates or rehydrates `mLiveSession` (`LiveWorkoutSession`)**.

Because `mLiveSession` remains `null` for the remainder of the resumed workout:
1. **No post-resume running `MIN`/`MAX`/`AVG` extrema or stream increments**: In `TrackerService.sampleAndWriteToDb()`, every block guarded by `if (mLiveSession != null)` is bypassed. Post-resume telemetry samples never update `TABLE_EXTREMA_VALUES`, and 20-second map/altitude/distance points are never appended via `appendToMapAndStreams()`.
2. **No barometric altitude correction forwarding**: `mAltitudeCorrectionReceiver` skips `mLiveSession.applyAltitudeCorrection(altitudeCorrection)`.
3. **Complete omission of `finalizeLiveSession()` on `endWorkout()`**: When the athlete stops the workout, `if (mLiveSession != null)` in `endWorkout()` evaluates to `false`, completely skipping `finalizeLiveSession()`. As a result:
   - Simplified overview polyline (`PolyUtil.simplify`), spatial bounding box (`MIN_LAT`/`MAX_LAT`/`MIN_LON`/`MAX_LON`), and full altitude/distance streams are not finalized (`updateMapAndStreams`).
   - `START`/`END` coordinate extrema and `LINE_DISTANCE_m` apex distance are omitted.
   - Efficiency Factor (`EF`) and Aerobic Decoupling (`Pw:Hr` / `Pa:Hr`) are not computed.
   - Power training metrics (`NP`, `IF`, `TSS`, `Work kJ`, `W/kg`) are not computed.
   - Automatic commute/trainer detection (`guessCommuteAndTrainer()`), sport identity arbitration, route similarity clustering, and automatic workout naming are skipped.

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 Affected Code Paths & Files
* [`TrackerService.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
  * `onStartCommand()` (lines 573–626): Only `case START_NORMAL:` executes `mLiveSession = new LiveWorkoutSession(0, IMPORTANT_SENSOR_TYPES);` (line 583). Neither `case RESUME_BY_USER:` (line 608) nor `case RESUME_SERVICE_RECREATION:` (line 622) initializes `mLiveSession`.
  * `recreateValuesWhenResuming()` / `applyResumedState(ResumedWorkoutState)` (lines 783–877): Restores scalar columns from `WorkoutSummaries.TABLE` via `RESUME_WORKOUT_PROJECTION`, initializes `mAccumulatedSensors` and `mMaxLineDistanceTracker`, and binds `mLiveSession = null` implicitly because `mLiveSession` was never created.
  * `sampleAndWriteToDb()` (lines 1438–1554): Guards all `mLiveSession.updateSensor(...)` calls, `TABLE_EXTREMA_VALUES` upserts (`MIN`, `MAX`, `AVG`), and 20-second `recordStreamPoint(...)` calls behind `if (mLiveSession != null)`.
  * `endWorkout()` (lines 1306–1309): Guards `finalizeLiveSession()` behind `if (mLiveSession != null)`.
* [`LiveWorkoutSession.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)
  * Holds stateful in-memory accumulators:
    * `statsMap` (`Map<SensorType, RunningStats>`): Tracks `min`, `max`, `sum`, `count`, `minChanged`, `maxChanged`, and `updatedInCurrentSample` per sensor.
    * `lastLatE5`, `lastLngE5`, `lastAltE2`, `lastDistE2`: Delta-encoding anchors for `recordStreamPoint()`.
    * `rawLatLngs`, `sampledLatLngs`, `sampledAltitudes`, `sampledDistances`: In-memory coordinate and metric lists used by `finalizeLiveSession()` to rewrite the final simplified `MAP_POLYLINE`, `ALTITUDE_STREAM`, `DISTANCE_STREAM`, and bounding box via `updateMapAndStreams()`.
    * `efSegments`, `powerRing`, `npFourthPowerSum`, `npSampleCount`, `totalWorkJoules`: Accumulators for Efficiency Factor, Aerobic Decoupling, and Coggan Power Metrics.
* [`WorkoutSummariesDatabaseManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
  * `WorkoutSummariesDbHelper`: Currently at `DB_VERSION = 24`. `TABLE_EXTREMA_VALUES` stores `(WORKOUT_ID, SENSOR_TYPE, EXTREMA_TYPE, VALUE, LATITUDE, LONGITUDE)` with `UNIQUE(WORKOUT_ID, SENSOR_TYPE, EXTREMA_TYPE) ON CONFLICT REPLACE`.
  * `updateExtremaValue(long workoutId, SensorType sensorType, ExtremaType extremaType, double value)`: Writes extrema value without sample count.

### 2.2 Failure Mechanism Trace (Why Naive Instantiation Without Rehydration Fails)
Simply calling `mLiveSession = new LiveWorkoutSession(mActiveTime_s, IMPORTANT_SENSOR_TYPES)` on resume without rehydrating persisted state introduces two severe data-corruption hazards:

1. **Stream Delta-Encoding & Finalization Overwrite Corruption**:
   * Every 20 seconds (`activeTime_s % 20 == 0`), `LiveWorkoutSession.recordStreamPoint(lat, lng, altitude, distance)` delta-encodes the current sample relative to `lastLatE5`, `lastLngE5`, `lastAltE2`, and `lastDistE2`, and `TrackerService` appends the delta string directly to the existing SQLite column via `SET MAP_POLYLINE = COALESCE(MAP_POLYLINE, '') || ?`.
   * If `lastLatE5`, `lastLngE5`, `lastAltE2`, and `lastDistE2` start at `0` on resume, the first post-resume point encodes `(current - 0)` instead of `(current - lastPreResume)`. When decoded by `PolyUtil.decode()` or `EncodingUtils.decodeSignedStream()`, that point and every subsequent point are offset by `+lastPreResume` (e.g., shifting a latitude of `48.135°` to `96.270°` and doubling distance/altitude).
   * Furthermore, `finalizeLiveSession()` calls `summariesManager.updateMapAndStreams(mWorkoutID, polyline, altStream, distStream)` built from `mLiveSession.getRawLatLngs()`, `getSampledLatLngs()`, `getSampledAltitudes()`, and `getSampledDistances()`. If those lists do not contain the pre-resume 20s points decoded from `MAP_POLYLINE`, `ALTITUDE_STREAM`, and `DISTANCE_STREAM`, `finalizeLiveSession()` overwrites the pre-resume route and profiles with only the post-resume tail!
2. **Extrema & Running Average Overwrite Corruption (Including Mid-Workout Sensor Pairing)**:
   * In `sampleAndWriteToDb()`, whenever `stats.minChanged` or `stats.maxChanged` is true, `TrackerService` calls `updateExtremaValue(mWorkoutID, sensorType, ExtremaType.MIN/MAX, ...)`. If `RunningStats` starts at `min = Double.MAX_VALUE` and `max = -Double.MAX_VALUE`, the very first post-resume sample unconditionally triggers `minChanged = true` and `maxChanged = true`, overwriting the pre-resume `MIN` and `MAX` rows in `TABLE_EXTREMA_VALUES` (and overwriting `START` coordinates if `START` were re-emitted).
   * Similarly, `AVG` is written every sample as `stats.getAverage()` (`sum / count`). If `sum` and `count` start at `0`, the persisted `AVG` is immediately overwritten with only the post-resume average.
   * For sensors paired or connected mid-workout (e.g. a Heart Rate strap or Power meter connected 10 minutes into a 30-minute ride), the true sample `count` prior to pause is strictly less than `TIME_ACTIVE_s`. Persisting a `COUNT` column (`WorkoutSummaries.COUNT = "count"`) on `TABLE_EXTREMA_VALUES` (in DB Version 25) allows `TrackerService` to persist `stats.getCount()` alongside extrema updates and restore the exact pre-resume sample `count` on resume (with a fallback to `TIME_ACTIVE_s` when `COUNT` is `null` or `<= 0` for legacy pre-v25 rows), setting `sum = avg * count`.

---

## 3. Chesterton's Fence: Requirement Archaeology (`REQ-PRO-022`)
Before proposing changes to existing requirements or architecture, we inspect the historical origin of workout resumption and live session tracking:
* `git log -S "REQ-TRK-003" docs/requirements.md` and `git log -S "REQ-TRK-030" docs/requirements.md` show:
  * **Original Requirement ID & Target**: `REQ-TRK-003` (Core Workout State Machine: Start, Pause, Resume, Stop) and `REQ-TRK-030` (Targeted Column Projection on Workout Resume — ATT-3981).
  * **Historical Origin & Commit Trace**: `LiveWorkoutSession` was introduced during the workout finalization and stream encoding architecture to compute in-memory running stats, 20-second polyline/altitude/distance streams, Efficiency Factor, and Coggan Power metrics without querying full raw sample tables at workout end. Later, ATT-3981 (`REQ-TRK-030`) optimized `recreateValuesWhenResuming()` to use a targeted 7-column projection (`RESUME_WORKOUT_PROJECTION`) on `WorkoutSummaries.TABLE`.
  * **Root Reason for Existing Formulation**: When `LiveWorkoutSession` was originally wired into `TrackerService.onStartCommand()`, it was only added to `case START_NORMAL:`. Resume pathways (`RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`) were overlooked.
  * **Preservation of Core Invariants**: We are **not** modifying or weakening `REQ-TRK-003` or `REQ-TRK-030`. Specifically, `recreateValuesWhenResuming()` will continue to query `WorkoutSummaries.TABLE` with the exact 7-column `RESUME_WORKOUT_PROJECTION` required by `REQ-TRK-030` and verified by `TrackerServiceResumeTest`. We are adding a new requirement `REQ-TRK-031` specifying `LiveWorkoutSession` instantiation and state rehydration (streams + extrema with sample counts) on workout resume.

---

## 4. Scope Boundaries (In-Scope vs. Out-of-Scope)
### In-Scope
1. **Database Schema & Persistence (`WorkoutSummariesDatabaseManager.java`)**:
   * Increment `DB_VERSION` from `24` to `25`.
   * Add `COUNT = "count"` column (`INTEGER`) to `TABLE_EXTREMA_VALUES` (`CREATE_TABLE_EXTREMA_VALUES` and `onUpgrade` for `oldVersion < 25`).
   * Extend `ExtremaRecord` with `public final Integer count` (preserving the 5-argument constructor for backward compatibility alongside a 6-argument constructor) and map `COUNT` symmetrically in `getExtremaForWorkouts()`.
   * Add overloaded `updateExtremaValue(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, Integer count)` so `AVG` (and `MIN`/`MAX`) updates persist the exact sample `count`.
   * Update `WorkoutSummariesDatabaseSourceMigrationTest` to assert `DB_VERSION >= 24` (consistent with `WorkoutSummariesDatabaseRaceMigrationTest`).
2. **`LiveWorkoutSession` Rehydration API (`LiveWorkoutSession.java`)**:
   * Expose `RunningStats.getCount()` and `RunningStats.rehydrate(Double minValue, Double maxValue, Double avgValue, int sampleCount)` to restore `min`, `max`, `sum = avgValue * sampleCount`, and `count = sampleCount` without triggering false `minChanged`/`maxChanged` flags.
   * Add `rehydrateSensorStats(SensorType sensorType, Double minValue, Double maxValue, Double avgValue, int sampleCount)` on `LiveWorkoutSession`.
   * Add `rehydrateStreams(List<LatLng> preResumeLatLngs, List<Double> preResumeAltitudes, List<Double> preResumeDistances)` on `LiveWorkoutSession` to populate `rawLatLngs`, `sampledLatLngs`, `sampledAltitudes`, and `sampledDistances`, and set `lastLatE5`, `lastLngE5`, `lastAltE2`, and `lastDistE2` to the final pre-resume point so subsequent 20s delta increments and `finalizeLiveSession()` continue seamlessly.
3. **`TrackerService` Resume Rehydration (`TrackerService.java`)**:
   * In `applyResumedState(ResumedWorkoutState state)` (invoked by `recreateValuesWhenResuming()` for both `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`), instantiate `mLiveSession = new LiveWorkoutSession(mActiveTime_s, IMPORTANT_SENSOR_TYPES)` and rehydrate its streams (decoding existing `MAP_POLYLINE`, `ALTITUDE_STREAM`, `DISTANCE_STREAM` via `PolyUtil.decode` and `EncodingUtils.decodeSignedStream`) and its `RunningStats` (from `summariesManager.getExtremaForWorkouts(Collections.singletonList(mWorkoutID))`, using persisted `record.count` when `> 0` and falling back to `mActiveTime_s` for legacy rows).
   * In `sampleAndWriteToDb()`, pass `stats.getCount()` when persisting `AVG` extrema via `updateExtremaValue(mWorkoutID, sensorType, ExtremaType.AVG, stats.getAverage(), stats.getCount())` and avoid overwriting `ExtremaType.START` if `mActiveTime_s > 1` and a `START` extremum already exists on resume.
4. **Unit Tests (`LiveWorkoutSessionTest.kt`, `TrackerServiceResumeTest.kt`)**:
   * Verify stream delta continuity across pause/resume, `RunningStats` `MIN`/`MAX`/`AVG` continuity (with explicit sensor sample count and `TIME_ACTIVE_s` fallback), and `TrackerService` resume rehydration + `finalizeLiveSession()` execution.

### Out-of-Scope
* Replaying 1Hz raw `WorkoutSamples` tables to reconstruct historical 30-second rolling power rings (`powerRing`) or 5-minute `efSegments` from before the pause (post-resume segments and samples accumulate normally into `mLiveSession` and finalize cleanly).
* Changes to UI screens or Jetpack Compose components.

---

## 5. Proposed Technical Direction & Architectural Impact
* **Layering (SWE.2)**:
  * **Persistence Layer (`WorkoutSummariesDatabaseManager`)**: Stores and retrieves `COUNT` in `TABLE_EXTREMA_VALUES` (`DB_VERSION = 25`), maintaining DTO cursor mapping symmetry (Rule 20).
  * **Domain Session Layer (`LiveWorkoutSession`)**: Pure in-memory state rehydration methods (`rehydrateStreams`, `rehydrateSensorStats`) that restore delta-encoding anchors (`lastLatE5`, `lastLngE5`, `lastAltE2`, `lastDistE2`), historical sampled lists, and per-sensor `RunningStats` (`min`, `max`, `sum`, `count`).
  * **Service Layer (`TrackerService`)**: Rehydrates `mLiveSession` inside `applyResumedState()` using `WorkoutSummariesDatabaseManager` (`getEncodedStreams` and `getExtremaForWorkouts`) so both `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION` get a fully populated `LiveWorkoutSession` without altering the 7-column `RESUME_WORKOUT_PROJECTION` query in `recreateValuesWhenResuming()`.
