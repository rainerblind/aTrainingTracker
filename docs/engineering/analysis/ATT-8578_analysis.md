# Stage 1 Analysis: ATT-8578 - AccumulatedSensors Virtual Sensor Filtering

**Ticket**: [ATT-8578](https://rainerblind.atlassian.net/browse/ATT-8578) | **Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8578` | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
During `Zum Bäcker #56` (`workoutID=2182`, `GCData = "TDS----AG--"`), no HR, power, or cadence sensor was connected and all `HR`, `POWER`, `HRV_*`, `TSS`, `W_PRIME_BAL` columns in `WorkoutSamples.db` are `NULL`. Yet `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes()` persisted **40 rows** into `AccumulatedSensors` (including 15 `CyclingPowerMetricsDevice` and 3 `HRVMetricsDevice` virtual sensors). `AccumulatedSensors` should record only sensor types that produced valid non-null data during the session or via post-workout backfill.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Registration-Time Accumulation (`MySensorManager.java:318-361`)**: `registerSensor()` adds `sensorType` to `mAccumulatedSensorTypeSet` (`:357`) when `getBestSensor(sensorType) == null`, purely on registration regardless of whether any non-null value is ever emitted (`MySensor.newValue()`), and `mAccumulatedSensorTypeSet` is cleared only in `shutDown()` (`:182`).
* **Unconditional Virtual Devices (`BANALService.java:687-689`, `DeviceManager.java:359-464`)**: `BANALService.onCreate()` unconditionally creates `VerticalSpeedAndSlopeDevice` (4 sensors, `:148-160`), `CyclingPowerMetricsDevice` (15 sensors, `:365-411`), and `HRVMetricsDevice` (3 sensors, `:101-110`), alongside `ClockDevice` (5), `BatteryDevice` (2), and location/altitude devices (`10+1` with `ATTFused`), totaling 40 registered `SensorType`s even with zero HR/Power hardware.
* **Why `GCData` Remained Clean**: `MySensorManager.addGCSensor()` (`:553-585`) maps only 9 core `SensorType`s (`TIME_TOTAL`, `DISTANCE_m`, `SPEED_mps`, `POWER`, `HR`, `CADENCE`, `TORQUE`, `ALTITUDE`, `LATITUDE`/`LONGITUDE`), omitting virtual HRV/Power/VerticalSpeed sensors.
* **Independent Peer Review & Verification (Live `52440f3b`)**:
  * **Caller Audit**: `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes()` (`:1719-1731`) has one production caller (`WorkoutRepository.saveAccumulatedSensorTypes()` `:432-434` <- `TrackerService.endWorkout()` `:2411`). Contrary to the ticket's assumption, `getAccumulatedSensorTypes()` (`:1737-1762`) currently has **zero** production callers (`WorkoutRepository.loadWorkoutDetailTelemetry()` `:2583-2670` inspects `WorkoutSamples.db` columns directly; `OrphanDataDiagnosticHelper.kt:106,218` scans orphan `workout_id` rows; `ImportEngine.kt:265` copies rows).
  * **Polar Backfill Blind Spot**: Neither `PolarH10OfflineController.kt` (`:365,385,596,602`), `PolarVeritySenseOfflineController.kt` (`:984,1171`), nor `HrvPostWorkoutRecomputer.kt` (`:74-315`) updates `AccumulatedSensors`. Because `TrackerService.endWorkout()` saves `AccumulatedSensors` at `:2411` **before** Polar backfill (`:2453-2466`) and `HrvPostWorkoutRecomputer` (`:2472`), and deferred `syncPendingWorkout()` runs asynchronously later, filtering `AccumulatedSensors` strictly to live non-null samples requires backfill/recompute paths to upsert `HR` (and `HRV_*`) idempotently (`TABLE_ACCUMULATED_SENSORS` at `:2547-2550` lacks a `UNIQUE` constraint).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: (1) Persist into `AccumulatedSensors` only `SensorType`s that produced valid non-null telemetry during live recording (`TrackerService.sampleAndWriteToDb` / `finalizeLiveSession`) or post-workout backfill; (2) Preserve/upsert `HR` and `HRV_*` in `AccumulatedSensors` for Polar H10 / Verity Sense synchronous and deferred backfill + `HrvPostWorkoutRecomputer`; (3) Prevent duplicate `(workout_id, sensor_type)` inserts in `WorkoutSummariesDatabaseManager`.
* **Out-of-Scope Non-Goals**: Changing `MySensorManager.getAccumulatedSensorTypeSet()` registration semantics (`TST-TRK-022.3`), altering `WorkoutSamples.db` schema, modifying Aftermath chart pickers, or migrating historical rows in existing DBs.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-030.2` (`TST-TRK-022.3`) & `REQ-TRK-035.1`/`.3` (`TST-TRK-027.1`/`.3`) in `docs/requirements/req_tracking_cockpit.md:1632-1670,1901-1931`; `REQ-PER-034.3`, `REQ-SET-105`, `REQ-SET-108`.
* **Historical Origin & Commit Trace**: `ATT-3986` (`0bf6fb05`) added defensive copy to `MySensorManager.getAccumulatedSensorTypeSet()`; `ATT-4099` (`aa0e9097`) added `BanalFinalizationSnapshot`; `a7049ef9` removed legacy `CalcExtremaWorker` readers of `getAccumulatedSensorTypes()`.
* **Root Reason for Existing Formulation**: `TST-TRK-022.3` (`TrackerServiceFinalizationTest.kt:590-628`) asserts `registerSensor(hrSensor)` immediately populates `getAccumulatedSensorTypeSet()`, and `TST-TRK-027.1`/`.3` (`:875-997`) test `BanalFinalizationSnapshot` passing `accumulatedSensorTypes` to `saveAccumulatedSensorTypes()`.
* **Preservation of Core Invariants**: Keep `MySensorManager.getAccumulatedSensorTypeSet()` and `BanalFinalizationSnapshot` DTO/capture contracts intact while filtering persisted `AccumulatedSensors` via tracked valid non-null observations + backfill upserts.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-TRK-030` / `REQ-TRK-035` or introduces net-new `REQ-TRK-*` + updates `REQ-SET-105`/`REQ-SET-108`/`REQ-PER-034.3` backfill contract for `AccumulatedSensors` validity filtering.
* **Affected Architecture Elements**: `TrackerService.java`, `LiveWorkoutSession.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `HrvPostWorkoutRecomputer.kt`.
* **Verification Impact & Test Surface**: `TrackerServiceFinalizationTest.kt`, `WorkoutSummariesDatabaseManager*` tests, Polar offline backfill & `HrvPostWorkoutRecomputer` tests.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`). No DB schema migration; `FINISHED = 1` isolation (`REQ-TRK-030.1`), `MySensorManager.getAccumulatedSensorTypeSet()` (`TST-TRK-022.3`), `GCData` mask, and `ANT-Android-SDKs` untouched.

---

## 6. Architectural Strategy & High-Level Solution
1. **Valid-Sample Observation (`TrackerService.java` / `LiveWorkoutSession.java`)**: Track `observedSensorTypes` in `LiveWorkoutSession` whenever `sampleAndWriteToDb()` (`:2705-2725`) observes a non-null `sensorData.getValue()` (and rehydrate from `getAccumulatedSensorTypes()` / `WorkoutSamples.db` on workout resume); also record computed non-null `finalizeLiveSession()` metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`, `W_PRIME_BAL`). In `endWorkout()`, when live observation tracking is active, persist `observedSensorTypes` rather than unfiltered `mAccumulatedSensorTypeSet` (preserving `snapshot.accumulatedSensorTypes` fallback when `mLiveSession == null` for `TST-TRK-027.3`).
2. **Idempotent Persistence (`WorkoutSummariesDatabaseManager.java` & `WorkoutRepository.kt`)**: Deduplicate against existing `(workout_id, sensor_type)` rows in `saveAccumulatedSensorTypes()` / `addAccumulatedSensorTypes()`.
3. **Backfill Guard (`PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `HrvPostWorkoutRecomputer`)**: After patching `HR` into `WorkoutSamples.db` (synchronous or deferred), upsert `SensorType.HR`; after `HrvPostWorkoutRecomputer` writes HRV metrics, upsert `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `REQ-TRK-030.1` (`FINISHED = 1` first), `REQ-TRK-030.2` (`TST-TRK-022.3` defensive copy), `REQ-TRK-035` (`TST-TRK-027` snapshot lifecycle), Polar offline HR/RR backfill completeness, zero duplicate `AccumulatedSensors` rows.
* **Risk Rating**: **LOW** — Isolated persistence filtering and post-backfill upsert without schema changes or `MySensorManager` lock changes.
