# Stage 5: Walkthrough & Verification - ATT-8578: AccumulatedSensors Virtual Sensor Filtering

**Ticket**: [ATT-8578](https://rainerblind.atlassian.net/browse/ATT-8578) | **Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_01_Früh` | **Branch**: `feature/ATT-8578` (rebased onto `e21292d5`, preserving `ATT-8158` `updateExtremaValueInMemory`)  
**Requirement**: `REQ-TRK-084` (`[Verified]`) | **Test Spec**: `TST-TRK-084` (`[Verified]`) | **Date**: 2026-11-17

---

## 1. Executive Summary & Implemented Changes
Eliminated false-positive persistence of ~40 unattached virtual/derived sensors (`CyclingPowerMetricsDevice`, `HRVMetricsDevice`, `VerticalSpeedAndSlopeDevice`) into `AccumulatedSensors` when HR/Power/Cadence sensors are absent:
* **`LiveWorkoutSession.java`**: Added `synchronized` `observedSensorTypes` (`EnumSet`), `recordObservedSensorType()`, `recordObservedSensorTypes()`, and unmodifiable `getObservedSensorTypes()`.
* **`WorkoutSummariesDatabaseManager.java` & `WorkoutRepository.kt`**: Updated `saveAccumulatedSensorTypes()` to deduplicate `(workout_id, sensor_type)` rows `synchronized` inside a SQLite transaction without schema migration; added `WorkoutRepository.addAccumulatedSensorTypes()` and `getAccumulatedSensorTypes()` (zero leaky DB/Cursor getters).
* **`TrackerService.java`**: Recorded non-null `sensorType` (excluding `SENSORS`/`ACCUMULATED_SENSORS`) in `sampleAndWriteToDb()` when `!mIsRehydrating`; rehydrated `observedSensorTypes` in `rehydrateLiveSession()`; persisted `liveSession.getObservedSensorTypes()` in `endWorkout()` when `liveSession != null` (preserving `snapshot.accumulatedSensorTypes` fallback when `liveSession == null` per `TST-TRK-027.3`); upserted non-null `finalizeLiveSession()` metrics (`LINE_DISTANCE_m`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`, `POWER_TO_WEIGHT`) after `FINISHED = 1`.
* **Polar Backfill & HRV Recompute**: Added `PolarOfflineGattCoordinator.patchMissingHeartRateAndRecordSensorType()` (calling `recordBackfilledSensorTypes()` strictly when `baseFileName` is non-empty and `patchedCount > 0`) for `SensorType.HR` across `PolarH10OfflineController` (`stopAndBackfillWorkout` & `syncPendingWorkout`) and `PolarVeritySenseOfflineController` (`applyBackfillAndCommit` & `applySwimBackfillAndCommit`), plus conditional upserts in `HrvPostWorkoutRecomputer` (`HRV_ARTIFACT_RATE`, non-empty `HRV_RMSSD`/`HRV_DFA_A1`, non-null `HRV_SDNN`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Case | Scope / Component | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-084.1` | `TST-TRK-084.1` | `TrackerServiceFinalizationTest` (28/28), `TrackerServiceIntegrationTest` (6/6) | **PASSED** | `Verified` |
| `REQ-TRK-084.2` | `TST-TRK-084.2` | `TrackerServiceFinalizationTest` (`finalizeLiveSession` upsert; also isolated 1/1) | **PASSED** | `Verified` |
| `REQ-TRK-084.3` | `TST-TRK-084.3` | `TrackerServiceResumeTest` (17/17), `WorkoutSummariesAccumulatedSensorsTest` (1/1), `WorkoutSummariesDatabaseManagerTest` (7/7) | **PASSED** | `Verified` |
| `REQ-TRK-084.4` | `TST-TRK-084.4` | `PolarH10OfflineControllerTest` (14/14), `PolarVeritySenseOfflineControllerTest` (22/22), `HrvPostWorkoutRecomputerTest` (9/9) | **PASSED** | `Verified` |
| `REQ-TRK-030.2`, `REQ-TRK-035` | `TST-TRK-022.3`, `TST-TRK-027.3`, `BANALServiceArbitrationCharacterisationTest` | `TrackerServiceFinalizationTest`, `BANALServiceArbitrationCharacterisationTest` (5/5) | **PASSED** | `Verified` |

---

## 3. Automated Test & Governance Evidence
* **Tier 1 Targeted Post-Heal Suite** (`parallel_sprint_lock.py -- ./gradlew :app:testDebugUnitTest --tests ...` across the 9 classes listed above): **`BUILD SUCCESSFUL in 2m 19s` — 109 tests, 0 failures, 0 errors, 0 skipped** (plus isolated single-method `testFinalizeLiveSession_upsertsComputedPowerAndLineDistanceMetrics` **`BUILD SUCCESSFUL in 1m 27s`**).
* **Governance**: `python3 tools/verify_requirement_governance.py` and `--check-specs --text-file docs/engineering/walkthroughs/ATT-8578_walkthrough.md` exited `0` (master index parity: `1210` REQs / `1217` TSTs; `REQ-TRK-084` & `TST-TRK-084` `[Verified]`; zero repository encapsulation violations).

---

## 4. Device Verification & UI Consistency
* **Non-UI Backend / DB Fix**: Pure service, repository, and SQLite persistence fix; APK deployment skipped in Stage 5 per two-tier policy (`N/A - no UI or strings modified`).
* **UI Consistency**: `N/A - no UI or strings modified`.

---

## 5. System Invariants
1. `REQ-TRK-030.1` (`FINISHED = 1` first commit), `REQ-TRK-030.2` (`MySensorManager.getAccumulatedSensorTypeSet()`), and `REQ-TRK-035` (`BanalFinalizationSnapshot` & `mLiveSession == null` fallback) preserved.
2. `ATT-8158` (`updateExtremaValueInMemory`) preserved cleanly across rebase; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
