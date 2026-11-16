# Stage 1 Analysis: ATT-7875 - Preserve accumulators on TrackerService crash recovery to prevent mid-workout time and distance reset

**Ticket**: [ATT-7875](https://rainerblind.atlassian.net/browse/ATT-7875) | **Sub-task**: [ATT-8246](https://rainerblind.atlassian.net/browse/ATT-8246) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7875`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
When Android restarts `START_STICKY` `TrackerService` after an unexpected process termination (`intent == null` -> `StartType.RESUME_SERVICE_RECREATION`), `TrackerService` binds to `BANALService` and invokes `recreateValuesWhenResuming()`. However, in `TrackerService.mBanalConnection.onServiceConnected()` (lines 960–966), immediately after restoring state, `TrackerService` unconditionally broadcasts `BANALService.RESET_ACCUMULATORS_INTENT`. This triggers `MyDevice.resetAccumulatorsReceiver` across all sensor devices, wiping `TIME_TOTAL`, `TIME_ACTIVE`, `DISTANCE_m`, `CALORIES`, and `LAP_NR` back to 0. Furthermore, `recreateValuesWhenResuming()` does not rehydrate `ASCENT`, `DESCENT`, `TIME_LAP`, or `DISTANCE_m_LAP`.
In workout `_id = 2176`, this caused total time (`643 s -> 1 s`), distance (`1,098.8 m -> 0 m`), and elevation (`21.0 m -> 0 m`) to collapse mid-ride, resulting in lost export totals, truncated telemetry graphs, and accordion folds (backward diagonal lines) in `ElevationProfile`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**:
  * `TrackerService.java:964-965`: Unconditionally sends `RESET_ACCUMULATORS_INTENT` inside `if (mResumeTrackingWhenConnectedToBanalService)`, triggering `((MyAccumulatorSensor) mySensor).reset()` across all device sensors.
  * `TrackerService.java:1844-1849` (`applyResumedState`): Only sets initial values for 5 sensor types (`TIME_TOTAL`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m`), omitting `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP`.
  * `WorkoutRepository.kt:418-456` (`getResumedWorkoutState`): Restricts its projection to 7 summary columns, ignoring `ASCENDING` and `DESCENDING`, and does not compute active lap metrics from `LapsDatabaseManager`.
* **Independent Peer Review & Verification**:
  * Verified live sprint code: `TrackerService.java:964` broadcasts `RESET_ACCUMULATORS_INTENT` specifically during resume connection, whereas `TrainingApplication.startTracking()` correctly guards with `if (!cResumeFromCrash)`.
  * Verified `MyDevice.java:60-70`: `resetAccumulatorsReceiver` calls `reset()` on all `MyAccumulatorSensor` instances, wiping `mInitialValue` to `-mValue` (`getValue() == 0`).
  * Verified `WorkoutSummariesDatabaseManager`: `ASCENDING` and `DESCENDING` are already continuously recorded at 1 Hz by `TrackerService.java:2687-2688`.
  * Verified lap metrics: For `lapNr > 1`, completed laps reside in `Laps.db`; active lap metrics equal `timeActive_s - sum(completedLapTime)` and `distanceTotal_m - sum(completedLapDistance)`. For `lapNr == 1`, active lap equals session totals.
  * Blind spot check: `SpeedAndLocationDevice` (GPS, Google Fused, ATT Fused) and altitude sensors inherit from `MyAccumulatorSensor`. Seeding them via `BANALService.setInitialSensorValue` establishes the pre-crash baseline; initializing `mInitialValue` from `MySensorManager.getInitialValue()` in `MyAccumulatorSensor` also protects against late-connecting providers.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Remove `RESET_ACCUMULATORS_INTENT` broadcast from `TrackerService.mBanalConnection.onServiceConnected()`.
  * Extend `ResumedWorkoutState` and `WorkoutRepository.getResumedWorkoutState()` to load `ASCENDING`, `DESCENDING`, and active lap metrics (`TIME_LAP`, `DISTANCE_m_LAP`).
  * Update `applyResumedState()` to seed `SensorType.ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP` via `BANALService.setInitialSensorValue()`.
  * Wire `MyAccumulatorSensor` to inherit `MySensorManager.getInitialValue(sensorType)` on instantiation.
  * Add unit tests in `TrackerServiceResumeTest.kt` verifying monotonic continuity of all 9 accumulators across `RESUME_SERVICE_RECREATION`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero alterations to `START_NORMAL` accumulator reset in `TrainingApplication.startTracking()`.
  * Zero alterations to `trackingStopped()` or `onTrackingAborted()` accumulator resets.
  * Zero SQLite schema changes (`ascending`, `descending`, and `Laps.db` already exist).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-TRK-026` (`TST-TRK-018`) and adds net-new `REQ-TRK-078` (`TST-TRK-078`). Complements `REQ-TRK-031` and `REQ-DAT-036`.
* **Historical Origin & Commit Trace**: `RESET_ACCUMULATORS_INTENT` at line 964 originated in initial commit `845818c2cfe0` (Aug 2019) as a copy-paste artifact. `REQ-TRK-026` (`ATT-3980` in commit `f4650d74e8b4`) ordered table name restoration in `onServiceConnected()` but did not audit the subsequent broadcast.
* **Root Reason for Existing Formulation**: In 2019, `TrackerService` lacked dedicated lifecycle separation between fresh workout starts and sticky process recovery.
* **Preservation of Core Invariants**: `START_NORMAL` fresh start resets via `TrainingApplication.startTracking()` remain untouched. Backward compatibility of `ResumedWorkoutState` constructors preserved. `mTableInitializationFuture` barrier (`REQ-TRK-026.3`) preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-026` (refinement) and `REQ-TRK-078` (net-new) in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `TrackerService.java`, `WorkoutRepository.kt`, `ResumedWorkoutState`, `BANALService.java`, `MyAccumulatorSensor.java`.
* **Verification Impact & Test Surface**: `TrackerServiceResumeTest.kt`, `TrackerServiceCrashRecoveryCharacterisationTest.kt`, and `ClockDeviceResilienceTest.kt`. Tier 1 unit test suite.
* **Risk Assessment & Invariants**: `LOW` (3 SP). Eliminates erroneous broadcast and seeds missing sensor accumulators. Thread safety and database single-thread executor contracts preserved.

---

## 6. Architectural Strategy & High-Level Solution
1. Remove `sendBroadcast(new Intent(BANALService.RESET_ACCUMULATORS_INTENT)...)` from `TrackerService.mBanalConnection.onServiceConnected()`.
2. Extend `ResumedWorkoutState` with `ascent: Double`, `descent: Double`, `timeLap_s: Int`, and `distanceLap_m: Double`, preserving backward-compatible 7-arg constructor.
3. In `WorkoutRepository.getResumedWorkoutState()`, query `ASCENDING` and `DESCENDING`, and compute active lap metrics using `LapsDatabaseManager.getLaps(workoutId)`.
4. In `TrackerService.applyResumedState()`, invoke `BANALService.setInitialSensorValue` for `ASCENT`, `DESCENT`, `TIME_LAP`, and `DISTANCE_m_LAP`.
5. In `MyAccumulatorSensor`, read `mMySensorManager.getInitialValue(sensorType)` during construction to support late-binding providers.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Monotonic non-decreasing accumulator progression across service recreation boundaries.
  2. Zero regression in existing unit tests (`TrackerServiceResumeTest`, `TrackerServiceCrashRecoveryCharacterisationTest`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (3 SP). Targeted lifecycle correction and accumulator baseline seeding.
