# Stage 2: Requirement & Test Specification - ATT-3987: [High] Resumed workouts have no LiveWorkoutSession and miss extrema, map and derived metrics

**Ticket**: [ATT-3987](https://rainerblind.atlassian.net/browse/ATT-3987)  
**Sub-task**: [ATT-4075](https://rainerblind.atlassian.net/browse/ATT-4075) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-3979](https://rainerblind.atlassian.net/browse/ATT-3979) (*Tracking Resilience & Data Integrity*)  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-TRK-031` (*LiveWorkoutSession Instantiation & State Rehydration on Workout Resume*)  
**Test Spec ID**: `TST-TRK-023`  
**Branch**: `feature/ATT-3987`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-TRK-031`)

### 1.1 Problem Statement & Rationale
When `TrackerService` resumes a paused or interrupted workout (`StartMode.RESUME_BY_USER` or `StartMode.RESUME_SERVICE_RECREATION`), `recreateValuesWhenResuming()` / `applyResumedState()` restores scalar summary fields (`mWorkoutID`, `mActiveTime_s`, `mTotalDistance_m`, `mAscend_m`, `mDescend_m`, `mLapNr`, `mSportTypeId`), but leaves `mLiveSession` (`LiveWorkoutSession`) as `null`. Because `mLiveSession` is `null`:
1. Post-resume 1 Hz ticks in `sampleAndWriteToDb()` skip all `mLiveSession.updateSensor(...)` and `recordStreamPoint(...)` blocks, failing to update `MIN`/`MAX`/`AVG` rows in `TABLE_EXTREMA_VALUES` or append 20-second map polyline, altitude, and distance increments.
2. Barometric altitude corrections in `mAltitudeCorrectionReceiver` are not applied to `mLiveSession`.
3. `endWorkout()` skips `finalizeLiveSession()` entirely, leaving the resumed workout without a simplified map polyline (`PolyUtil.simplify`), spatial bounding box (`MIN_LAT`/`MAX_LAT`/`MIN_LON`/`MAX_LON`), `START`/`END` positions, `LINE_DISTANCE_m` apex distance, Efficiency Factor / Aerobic Decoupling, Power Training Metrics (`NP`, `IF`, `TSS`, `Work kJ`, `W/kg`), commute/trainer inference, sport identity arbitration, route clustering, and auto-naming.

Furthermore, naively instantiating `new LiveWorkoutSession(mActiveTime_s, IMPORTANT_SENSOR_TYPES)` without rehydrating its persisted state would corrupt both delta-encoded 20-second streams (`lastLatE5`, `lastLngE5`, `lastAltE2`, `lastDistE2` starting at `0` instead of the last pre-resume sample, and `finalizeLiveSession()` overwriting pre-resume streams) and running `MIN`/`MAX`/`AVG` sensor statistics (overwriting pre-resume extrema and distorting averages for sensors paired mid-workout).

### 1.2 Functional & Architectural Requirements
The system SHALL instantiate and rehydrate `LiveWorkoutSession` whenever a workout is resumed (`RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`), preserving seamless continuity of delta-encoded 20-second streams, running `MIN`/`MAX`/`AVG` sensor extrema (including mid-workout sensor sample counts), and workout finalization (`REQ-TRK-031`):

1. **Sample Count Persistence in `TABLE_EXTREMA_VALUES` (`REQ-TRK-031.1` — Rule 20)**:
   * `WorkoutSummariesDatabaseManager` (`WorkoutSummariesDbHelper`) SHALL increment `DB_VERSION` from `24` to `25` and define column `WorkoutSummaries.COUNT = "count"` (`INTEGER`) on `TABLE_EXTREMA_VALUES` (`ExtremumValues`) in both `CREATE_TABLE_EXTREMA_VALUES` and `onUpgrade` (`if (oldVersion < 25) db.execSQL("ALTER TABLE ... ADD COLUMN count INTEGER")`).
   * `WorkoutSummariesDatabaseManager.ExtremaRecord` SHALL include `@Nullable public final Integer count` with a 6-argument constructor `ExtremaRecord(workoutId, sensorType, extremaType, value, position, count)` and a backward-compatible 5-argument constructor delegating with `count = null`.
   * `WorkoutSummariesDatabaseManager.getExtremaForWorkouts(List<Long> workoutIds)` SHALL symmetrically extract `COUNT` (`cursor.isNull(countCol) ? null : cursor.getInt(countCol)`) into `ExtremaRecord` (Rule 20).
   * `WorkoutSummariesDatabaseManager` SHALL provide `updateExtremaValue(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, @Nullable Integer count)` (with the existing 4-argument overload delegating with `count = null`), persisting `COUNT` when non-null.

2. **`LiveWorkoutSession` Stream & Sensor Stats Rehydration API (`REQ-TRK-031.2`)**:
   * `LiveWorkoutSession.RunningStats` SHALL expose `public int getCount()` returning `count` and `public void rehydrate(@Nullable Double minValue, @Nullable Double maxValue, @Nullable Double avgValue, int sampleCount)` which restores `min`, `max`, `count`, and `sum = avgValue * count` (when `avgValue != null && Double.isFinite(avgValue) && sampleCount > 0`) while keeping `minChanged = false`, `maxChanged = false`, and `updatedInCurrentSample = false`.
   * `LiveWorkoutSession` SHALL provide `public void rehydrateSensorStats(@NonNull SensorType sensorType, @Nullable Double minValue, @Nullable Double maxValue, @Nullable Double avgValue, int sampleCount)` to restore `RunningStats` for any tracked `SensorType`.
   * `LiveWorkoutSession` SHALL provide `public void rehydrateStreams(@Nullable List<LatLng> preResumeLatLngs, @Nullable List<Double> preResumeAltitudes, @Nullable List<Double> preResumeDistances)` which:
     * Clears and populates `rawLatLngs` and `sampledLatLngs` from `preResumeLatLngs`, and sets `lastLatE5 = (int) Math.round(last.latitude * 1e5)` and `lastLngE5 = (int) Math.round(last.longitude * 1e5)` from the last coordinate when non-empty.
     * Clears and populates `sampledAltitudes` from `preResumeAltitudes`, and sets `lastAltE2 = (int) Math.round(lastAlt * 100)` from the last altitude when non-empty.
     * Clears and populates `sampledDistances` from `preResumeDistances`, and sets `lastDistE2 = (int) Math.round(lastDist * 100)` from the last distance when non-empty.

3. **`TrackerService` Resume Rehydration & Finalization Continuity (`REQ-TRK-031.3`)**:
   * In `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)` (invoked for both `StartMode.RESUME_BY_USER` and `StartMode.RESUME_SERVICE_RECREATION`), `TrackerService` SHALL instantiate `mLiveSession = new LiveWorkoutSession(mTimeActive_s, IMPORTANT_SENSOR_TYPES)` and invoke `rehydrateLiveSession(mWorkoutID, mTimeActive_s)`.
   * `rehydrateLiveSession(long workoutId, int activeTimeSeconds)` SHALL:
     * Query `WorkoutSummariesDatabaseManager.getInstance(getApplicationContext()).getEncodedStreams(workoutId)` and decode any non-empty `mapPolyline` (`PolyUtil.decode`), `altitudeStream` (`EncodingUtils.INSTANCE.decodeSignedStream(s, 100.0)`), and `distanceStream` (`EncodingUtils.INSTANCE.decodeSignedStream(s, 100.0)`), passing them to `mLiveSession.rehydrateStreams(...)`.
     * Query `summariesManager.getExtremaForWorkouts(Collections.singletonList(workoutId))`, group persisted `MIN`, `MAX`, and `AVG` records per `SensorType` (plus detect whether `ExtremaType.START` already exists for `LATITUDE`/`LONGITUDE`), resolve the effective pre-resume `sampleCount` from the `AVG` (or `MIN`/`MAX`) record's `count` when `count != null && count > 0` or fall back to `Math.max(0, activeTimeSeconds)` for legacy rows where `count == null`, and invoke `mLiveSession.rehydrateSensorStats(sensorType, minVal, maxVal, avgVal, sampleCount)`.
   * In `TrackerService.sampleAndWriteToDb()`, when persisting `ExtremaType.AVG`, `TrackerService` SHALL pass `stats.getCount()` to `summariesManager.updateExtremaValue(mWorkoutID, sensorType, ExtremaType.AVG, stats.getAverage(), stats.getCount())` (and pass `stats.getCount()` on `MIN`/`MAX` updates), and SHALL NOT overwrite `ExtremaType.START` if a `START` position was already persisted prior to resume.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Stream Delta-Encoding & Finalization Continuity Across Resume)**:
  * *Given* a workout with pre-resume 20s stream points encoded in `MAP_POLYLINE`, `ALTITUDE_STREAM`, and `DISTANCE_STREAM`,
  * *When* the workout is resumed (`rehydrateStreams` is called with the decoded pre-resume points) and new 20s points are recorded via `recordStreamPoint(lat, lng, alt, dist)`,
  * *Then* the newly returned `StreamIncrement` strings are delta-encoded relative to the last pre-resume point (not `0`), concatenating `preResumeEncoded + increment` decodes to the exact combined sequence of pre-resume and post-resume coordinates/altitudes/distances without any coordinate jump, and `getRawLatLngs()` / `getSampledAltitudes()` / `getSampledDistances()` contain the full combined session history for `finalizeLiveSession()`.
* **Criterion 2 (RunningStats `MIN`/`MAX`/`AVG` Continuity & Mid-Workout Sensor Sample Count)**:
  * *Given* a workout paused at `TIME_ACTIVE_s = 600` where a sensor connected mid-workout has persisted `MIN = 120.0`, `MAX = 175.0`, `AVG = 150.0`, and `COUNT = 100` (or `COUNT = null` for a legacy row),
  * *When* `LiveWorkoutSession` is rehydrated and a post-resume sample of `160.0` is recorded,
  * *Then* `minChanged` and `maxChanged` remain `false` (preserving `MIN = 120.0` and `MAX = 175.0`), `getCount()` advances to `101` (or `601` when falling back to `TIME_ACTIVE_s` on legacy rows), and `getAverage()` evaluates to `(150.0 * 100 + 160.0) / 101` (or `(150.0 * 600 + 160.0) / 601`).
* **Criterion 3 (`TrackerService` Resume Instantiates & Rehydrates `mLiveSession` and Executes `finalizeLiveSession()` on Stop)**:
  * *Given* an existing workout row in `WorkoutSummaries` with persisted encoded streams and extrema rows,
  * *When* `TrackerService` resumes the workout (`RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`), records post-resume samples, and stops the workout (`endWorkout()`),
  * *Then* `mLiveSession` is non-null and rehydrated with the pre-resume streams and extrema, post-resume samples update `mLiveSession` and `TABLE_EXTREMA_VALUES` with sample counts without overwriting `ExtremaType.START`, and `endWorkout()` invokes `finalizeLiveSession()` to persist the combined map polyline, streams, and derived metrics.

### 1.4 System Invariants
* **Targeted Resume Projection Invariant (`REQ-TRK-016`)**: `TrackerService.recreateValuesWhenResuming()` MUST continue querying `WorkoutSummaries.TABLE` with the exact 7-column `RESUME_WORKOUT_PROJECTION`.
* **DTO Mapping Symmetry (Rule 20)**: `ExtremaRecord` 5-argument constructor backward compatibility (`WorkoutDataMappingSymmetryTest`) MUST be preserved alongside the 6-argument constructor with `count`.
* **Fault-Isolated Finalization (`REQ-TRK-030`)**: All `try / catch (Throwable)` guards in `endWorkout()` and `finalizeLiveSession()` MUST remain intact.
* **Zero Localization Changes Required**: This ticket operates purely on backend service, session state, and SQLite persistence layers; existing 9-language localization parity MUST remain 100% intact.

---

## 2. Test Specification (`TST-TRK-023`)

### Test Case 1: `LiveWorkoutSession` Stream Rehydration & Delta-Encoding Continuity (`TST-TRK-023.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionTest.kt`
* **Preconditions**: Pre-resume `LiveWorkoutSession` records 20s stream points at `t = 20s` and `t = 40s`, producing encoded `MAP_POLYLINE`, `ALTITUDE_STREAM`, and `DISTANCE_STREAM`.
* **Action**: Decode pre-resume streams with `PolyUtil.decode` and `EncodingUtils.decodeSignedStream`, instantiate a new `LiveWorkoutSession(40, sensors)`, call `rehydrateStreams(...)`, and record a post-resume point at `t = 60s`.
* **Expected Result**: Concatenating the pre-resume encoded strings with the `t = 60s` `StreamIncrement` decodes to the exact 3-point coordinate, altitude, and distance sequences without any delta jump; `getRawLatLngs()`, `getSampledLatLngs()`, `getSampledAltitudes()`, and `getSampledDistances()` contain all 3 points.

### Test Case 2: `LiveWorkoutSession` Sensor `RunningStats` Rehydration with Explicit `COUNT` & `TIME_ACTIVE_s` Fallback (`TST-TRK-023.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSessionTest.kt`
* **Preconditions**: A resumed `LiveWorkoutSession` is rehydrated via `rehydrateSensorStats(SensorType.HR, min=110.0, max=180.0, avg=150.0, sampleCount=100)`.
* **Action**: Feed an interior sample (`160.0`), then a new minimum (`105.0`), then a new maximum (`185.0`).
* **Expected Result**:
  * On interior sample `160.0`: `minChanged == false`, `maxChanged == false`, `min == 110.0`, `max == 180.0`, `count == 101`, `average == (150.0 * 100 + 160.0) / 101.0`.
  * On `105.0`: `minChanged == true`, `min == 105.0`, `maxChanged == false`.
  * On `185.0`: `maxChanged == true`, `max == 185.0`, `minChanged == false`.

### Test Case 3: `TrackerService` Resume Rehydration & `finalizeLiveSession()` Execution (`TST-TRK-023.3`)
* **Scope**: Unit / Service Integration Test (Robolectric + MockK)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* **Preconditions**: `WorkoutSummariesDatabaseManager` returns pre-resume `EncodedStreams` (`mapPolyline`, `altitudeStream`, `distanceStream`) and `ExtremaRecord`s (including explicit `count` for a mid-workout sensor and `null` count fallback for a legacy sensor, plus `START` coordinates).
* **Action**: Invoke `recreateValuesWhenResuming()` / `applyResumedState()`, verify `mLiveSession` is non-null and rehydrated, execute post-resume `sampleAndWriteToDb()`, and invoke `endWorkout()`.
* **Expected Result**:
  * `mLiveSession` is initialized and rehydrated with pre-resume streams and sensor stats (honoring explicit `count` and `mTimeActive_s` fallback).
  * Post-resume `sampleAndWriteToDb()` updates `AVG` with incremented `count` via `updateExtremaValue(..., count)` and does not overwrite existing `ExtremaType.START`.
  * `endWorkout()` executes `finalizeLiveSession()` and persists the combined polyline and streams via `updateMapAndStreams`.

### Test Case 4: Database Version 25 Migration & Clean-Room Regression Suite (`TST-TRK-023.4`)
* **Scope**: Database Migration & Full Regression Suite
* **Target Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseSourceMigrationTest.kt` & `./gradlew testDebugUnitTest`
* **Expected Result**: `DB_VERSION` is `25` (`>= 24`), `TABLE_EXTREMA_VALUES` includes `COUNT`, and 100% of unit tests in `./gradlew testDebugUnitTest` pass with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-023.1` | Unit | `LiveWorkoutSession.rehydrateStreams`, `recordStreamPoint` | `REQ-TRK-031` | Specified |
| `TST-TRK-023.2` | Unit | `LiveWorkoutSession.rehydrateSensorStats`, `RunningStats.rehydrate` | `REQ-TRK-031` | Specified |
| `TST-TRK-023.3` | Unit / Service | `TrackerService.applyResumedState`, `rehydrateLiveSession`, `sampleAndWriteToDb`, `endWorkout` | `REQ-TRK-031` | Specified |
| `TST-TRK-023.4` | DB & Regression | `WorkoutSummariesDatabaseManager`, `./gradlew testDebugUnitTest` | `REQ-TRK-031`, `REQ-PRO-001` | Specified |
