# Stage 3: Implementation Plan - ATT-8578: AccumulatedSensors Virtual Sensor Filtering

**Ticket**: [ATT-8578](https://rainerblind.atlassian.net/browse/ATT-8578) | **Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8578`  
**Requirement**: `REQ-TRK-084` (`[Planned]`) | **Test Mapping**: `TST-TRK-084` (`[Planned]`) | **Date**: 2026-11-17

---

## 1. Problem & Traceability Summary
`MySensorManager.registerSensor()` populates `mAccumulatedSensorTypeSet` unconditionally on first registration, causing `BANALService.onCreate()`'s virtual devices (`CyclingPowerMetricsDevice`, `HRVMetricsDevice`, `VerticalSpeedAndSlopeDevice`) to persist ~40 unattached sensor rows into `AccumulatedSensors` via `TrackerService.endWorkout()`.
* **Requirement / Test**: `REQ-TRK-084` (`REQ-TRK-084.1`..`REQ-TRK-084.4`) <-> `TST-TRK-084` (`TST-TRK-084.1`..`TST-TRK-084.5`).

---

## 2. SWE.2 Layering, Threading & Invariants
* **Threading**: `TrackerService` sample writes, resume rehydration, and `endWorkout()`/`finalizeLiveSession()` execute on single-threaded `mDbExecutor`; `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, and `HrvPostWorkoutRecomputer` execute on `Dispatchers.IO`; `LiveWorkoutSession` guards `observedSensorTypes` via `synchronized` methods; `WorkoutSummariesDatabaseManager` deduplicates inserts inside a `synchronized` SQLite transaction without schema migration (`TABLE_ACCUMULATED_SENSORS` lacks `UNIQUE`).
* **Preserved Invariants**: `REQ-TRK-030.1` (`FINISHED = 1` first commit), `REQ-TRK-030.2` (`TST-TRK-022.3` `MySensorManager.getAccumulatedSensorTypeSet()` registration contract), `REQ-TRK-035.1`/`.3` (`TST-TRK-027.1`/`.3` `BanalFinalizationSnapshot` & `mLiveSession == null` fallback), `GCData` 11-char mask, repository encapsulation (no public `*DatabaseManager`/`Cursor` getters), and `ANT-Android-SDKs` at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **UI Consistency**: `No UI changes.` (`N/A - no UI strings`).

---

## 3. Chesterton's Fence Audit
* **Original Target**: `REQ-TRK-030.2` (`MySensorManager.getAccumulatedSensorTypeSet()`), `REQ-TRK-035.3` (`BanalFinalizationSnapshot`), `REQ-SET-105` / `REQ-SET-108` (Polar backfill), `REQ-PER-034.3` (`HrvPostWorkoutRecomputer`).
* **Historical Origin**: `ATT-3986` (`0bf6fb05`) & `ATT-4099` (`aa0e9097`) hardened concurrency & service-unbind snapshots; `a7049ef9` retired legacy `getAccumulatedSensorTypes()` readers; `ATT-7918`/`ATT-7923` added post-workout Polar/HRV recompute.
* **Root Reason**: `TST-TRK-022.3` and `TST-TRK-027.3` assert registration-time `MySensorManager` and `mLiveSession == null` snapshot behavior directly, while `endWorkout()` ran `saveAccumulatedSensorTypes()` before `finalizeLiveSession()` and Polar/HRV backfill.
* **Preservation**: Keep `MySensorManager.getAccumulatedSensorTypeSet()` and `BanalFinalizationSnapshot` intact; filter live persistence via `LiveWorkoutSession.observedSensorTypes` and upsert post-finalization/backfill sensor types idempotently.

---

## 4. Atomic Stage 4 Implementation Sequence
1. **Step 1 — `LiveWorkoutSession` Observation State (`REQ-TRK-084.1`)**:
   * File: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`
   * Add `private final Set<SensorType> observedSensorTypes = new HashSet<>()` with `synchronized` `recordObservedSensorType(SensorType)`, `recordObservedSensorTypes(Collection<SensorType>)`, and defensive-copy `getObservedSensorTypes()`.
2. **Step 2 — Idempotent DB Deduplication & Repository Facade (`REQ-TRK-084.3`)**:
   * Files: `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`
   * Update `saveAccumulatedSensorTypes(long, Iterable<SensorType>)` (`:1719`) to query existing `SENSOR_TYPE` rows for `workoutId` inside a `synchronized` transaction and insert only non-duplicate `SensorType`s. Expose `addAccumulatedSensorTypes(workoutId, Set<SensorType>)` and `getAccumulatedSensorTypes(workoutId)` on `WorkoutRepository`.
3. **Step 3 — `TrackerService` Observation, Resume Rehydration & Finalization (`REQ-TRK-084.1`..`.3`)**:
   * File: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
   * In `sampleAndWriteToDb()` (`:2705-2725`), record `sensorType` into `mLiveSession.recordObservedSensorType(sensorType)` when `sensorData.getValue() != null` and `!mIsRehydrating`.
   * In `rehydrateLiveSession()` (`:1969`), rehydrate `session.recordObservedSensorTypes(workoutRepository.getAccumulatedSensorTypes(workoutId))` (plus `sensorsWithExtrema` fallback).
   * In `endWorkout(snapshot)` (`:2406-2415`), persist `liveSession.getObservedSensorTypes()` when `liveSession != null`, retaining `snapshot.accumulatedSensorTypes` / `banalService.getAccumulatedSensorTypeSet()` fallback when `liveSession == null` (`TST-TRK-027.3`).
   * In `finalizeLiveSession()` (`:3235-3326`), collect non-null derived metrics (`LINE_DISTANCE_m`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`) and call `repository.addAccumulatedSensorTypes(workoutId, finalizedSensors)` in an isolated `try/catch`.
4. **Step 4 — Polar Offline Backfill & Conditional HRV Upserts (`REQ-TRK-084.4`)**:
   * Files: `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `HrvPostWorkoutRecomputer.kt`
   * Call `WorkoutRepository.getInstance(appContext).addAccumulatedSensorTypes(workoutId, setOf(SensorType.HR))` after `patchMissingHeartRateSamples()` in `PolarH10OfflineController` (`stopAndBackfillWorkoutBlocking` `:365`, `syncPendingWorkout` `:596`) and `PolarVeritySenseOfflineController` (`applyBackfillAndCommit` `:1171`, `applySwimBackfillAndCommit` `:984`).
   * In `HrvPostWorkoutRecomputer.recomputeWorkout()` (`:251-278`), upsert `HRV_ARTIFACT_RATE` when written plus `HRV_RMSSD` (`rmssdValues.isNotEmpty()`), `HRV_DFA_A1` (`dfaValues.isNotEmpty()`), and `HRV_SDNN` (`sessionSdnn != null`).
5. **Step 5 — Targeted Verification Tests (`TST-TRK-084.1`..`.5`)**:
   * Files: `TrackerServiceFinalizationTest.kt`, `TrackerServiceResumeTest.kt`, `WorkoutSummariesDatabaseManagerTest.kt`, `PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`, `HrvPostWorkoutRecomputerTest.kt`.
   * Run under `python3 tools/parallel_sprint_lock.py` (with Gradle `-Dgradle.user.home=...`): `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.

---

## 5. Verification & Rollback Plan
* **Verification**: Targeted Tier 1 JVM/Robolectric tests in Stage 4, full clean-room verification + living doc `[Verified]` promotion in Stage 5, and Cold Final Review in Stage 6.
* **Rollback**: Branch-isolated on `feature/ATT-8578`; `git revert` cleanly restores prior behavior with zero schema or data-format impact.
