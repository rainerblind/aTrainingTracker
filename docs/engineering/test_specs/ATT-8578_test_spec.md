# Stage 2: Requirement & Test Specification - ATT-8578: AccumulatedSensors Virtual Sensor Filtering

**Ticket**: [ATT-8578](https://rainerblind.atlassian.net/browse/ATT-8578) | **Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8578`  
**Requirement Mapping**: `REQ-TRK-084` (`[Planned]`, `req_tracking_cockpit.md`) | **Test Spec ID**: `TST-TRK-084` (`[Planned]`, `tst_tracking_cockpit.md`)  
**Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-TRK-084`)

### 1.1 Rationale & Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Rationale**: `MySensorManager.registerSensor()` adds every registered `SensorType` to `mAccumulatedSensorTypeSet` unconditionally while `BANALService.onCreate()` instantiates `CyclingPowerMetricsDevice` (15), `HRVMetricsDevice` (3), and `VerticalSpeedAndSlopeDevice` (4) regardless of hardware availability. Persisting that unfiltered set wrote 40 false-positive rows into `AccumulatedSensors` without HR/Power hardware (`ATT-8578`).

### 1.2 Functional & Architectural Requirements
1. **Live Valid-Sample Observation (`REQ-TRK-084.1`)**: `LiveWorkoutSession` SHALL record `SensorType`s into `observedSensorTypes` strictly when `TrackerService.sampleAndWriteToDb()` observes `sensorData != null && sensorData.getValue() != null`. `TrackerService.endWorkout(snapshot)` SHALL persist `observedSensorTypes` when `mLiveSession != null` (preserving `snapshot.accumulatedSensorTypes` fallback when `mLiveSession == null` per `REQ-TRK-035.3` / `TST-TRK-027.3`).
2. **`finalizeLiveSession()` Metric Upsert (`REQ-TRK-084.2`)**: After `FINISHED = 1` (`REQ-TRK-030.1`) and initial `saveAccumulatedSensorTypes()`, `finalizeLiveSession()` SHALL idempotently upsert computed non-null metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`, `LINE_DISTANCE_m`).
3. **Resume Rehydration & Idempotent DB Deduplication (`REQ-TRK-084.3`)**: `TrackerService.rehydrateLiveSession()` SHALL rehydrate `observedSensorTypes` from `WorkoutRepository.getAccumulatedSensorTypes(workoutId)` on `RESUME_BY_USER`/`RESUME_SERVICE_RECREATION`. `WorkoutSummariesDatabaseManager` SHALL deduplicate `(workout_id, sensor_type)` inserts transactionally without schema migration (`TABLE_ACCUMULATED_SENSORS` lacks `UNIQUE`).
4. **Polar Backfill & Conditional HRV Upserts (`REQ-TRK-084.4`)**: `PolarH10OfflineController` (sync + deferred `syncPendingWorkout`) and `PolarVeritySenseOfflineController` (`applyBackfillAndCommit` + `applySwimBackfillAndCommit`) SHALL upsert `SensorType.HR` on non-empty HR/RR backfill. `HrvPostWorkoutRecomputer` SHALL upsert `HRV_ARTIFACT_RATE` when written, and conditionally upsert `HRV_RMSSD` (`rmssdValues.isNotEmpty()`), `HRV_DFA_A1` (`dfaValues.isNotEmpty()`), and `HRV_SDNN` (`sessionSdnn != null`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* GPS/Clock/Battery active with `CyclingPowerMetricsDevice`, `HRVMetricsDevice`, and `VerticalSpeedAndSlopeDevice` emitting `null`, *When* `sampleAndWriteToDb()` and `endWorkout()` run, *Then* `AccumulatedSensors` contains zero `HR`, `POWER`, `HRV_*`, `POWER_*`, `TSS`, `W_PRIME_BAL`, or `LIVE_HR_DRIFT` rows.
* *Given* `finalizeLiveSession()` computes non-null `NORMALIZED_POWER`/`TSS`/`LINE_DISTANCE_m`, *When* finalization runs, *Then* `FINISHED = 1` commits first and those metrics are upserted idempotently.
* *Given* a resumed session or repeated `saveAccumulatedSensorTypes()`/`addAccumulatedSensorTypes()` calls, *When* persisted, *Then* prior types remain with `0` duplicate `(workout_id, sensor_type)` rows.
* *Given* Polar H10 (sync/deferred) or Verity Sense (land/swim) backfill + `HrvPostWorkoutRecomputer`, *When* executed, *Then* `HR` and only non-empty/non-null HRV types are upserted.
* **Invariants**: `REQ-TRK-030.1` (`FINISHED = 1` first), `REQ-TRK-030.2` (`TST-TRK-022.3`), `REQ-TRK-035` (`TST-TRK-027`), `GCData` mask, zero DB schema migration, and Localization Parity: `N/A - no UI strings`.

---

## 2. Test Specification (`TST-TRK-084`)
1. **`TST-TRK-084.1` (`TrackerServiceFinalizationTest.kt`)**: Verify `sampleAndWriteToDb()` + `endWorkout()` filters `null`-emitting virtual HRV/Power/Dynamics sensors even when `getAccumulatedSensorTypeSet()` has 40 types, while preserving `mLiveSession == null` fallback (`TST-TRK-027.3`) and `MySensorManager` contract (`TST-TRK-022.3`).
2. **`TST-TRK-084.2` (`TrackerServiceFinalizationTest.kt`)**: Verify `finalizeLiveSession()` upserts computed non-null `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`, `LINE_DISTANCE_m` after `FINISHED = 1`.
3. **`TST-TRK-084.3` (`TrackerServiceResumeTest.kt`, `WorkoutSummariesAccumulatedSensorsTest.kt`)**: Verify `rehydrateLiveSession()` preserves prior `AccumulatedSensors` across resume and DB persistence deduplicates overlapping `(workout_id, sensor_type)` inserts.
4. **`TST-TRK-084.4` (`PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`, `HrvPostWorkoutRecomputerTest.kt`)**: Verify H10 sync/deferred + Verity Sense land/swim backfill upsert `HR`, and `HrvPostWorkoutRecomputer` upserts `HRV_ARTIFACT_RATE` when written plus `HRV_RMSSD`/`HRV_DFA_A1`/`HRV_SDNN` strictly when non-empty/non-null.
5. **`TST-TRK-084.5` (Tier 1 & Localization)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.trainingtracker.build.*"` (`N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-084.1` | Unit | `TrackerService.sampleAndWriteToDb` / `endWorkout` | `REQ-TRK-084.1`, `REQ-TRK-030.2`, `REQ-TRK-035.3` | Specified |
| `TST-TRK-084.2` | Unit | `TrackerService.finalizeLiveSession` | `REQ-TRK-084.2`, `REQ-TRK-030.1` | Specified |
| `TST-TRK-084.3` | Unit | `TrackerService.rehydrateLiveSession`, `WorkoutSummariesDatabaseManager` | `REQ-TRK-084.3` | Specified |
| `TST-TRK-084.4` | Unit | `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `HrvPostWorkoutRecomputer` | `REQ-TRK-084.4`, `REQ-SET-105`, `REQ-SET-108`, `REQ-PER-034.3` | Specified |
| `TST-TRK-084.5` | Regression | Tier 1 Gradle + `TranslationParityTest` (`N/A - no UI strings`) | `REQ-PRO-001`, `REQ-UI-106` | Specified |
