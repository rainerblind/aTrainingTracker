# Stage 1 Analysis: ATT-3986 - [High] Workout finalization can leave FINISHED unset and skip map and streams

**Ticket**: [ATT-3986](https://rainerblind.atlassian.net/browse/ATT-3986)  
**Sub-task**: [ATT-4069](https://rainerblind.atlassian.net/browse/ATT-4069) (`[Analysis]`)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `2026-42.9`  
**Branch**: `feature/ATT-3986`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

When an athlete stops a workout—especially a short or immediately stopped workout (`mTimeActive_s == 0`), a workout where `BANALService` disconnects concurrently during stop, or a workout where a sensor registers concurrently during teardown—`TrackerService.endWorkout()` and `TrackerService.finalizeLiveSession()` can fail to persist `WorkoutSummaries.FINISHED = 1` or abort before writing the final map polyline and altitude/distance streams.

As a result:
1. **Unfinished Zombie Workouts**: If `FINISHED = 1` is rolled back, the stopped workout remains `FINISHED = 0` in `WorkoutSummaries.TABLE`, causing the app to repeatedly offer the stopped workout as an "unfinished workout to resume" on subsequent launches.
2. **`NaN` / `Infinity` Speed Propagation**: When a workout is stopped at `mTimeActive_s == 0`, `getAverageSpeed()` computes `mDistanceTotal_m / mTimeActive_s` (`0.0 / 0 = Double.NaN` or `d / 0 = Double.POSITIVE_INFINITY`), which is passed unconditionally into `EquipmentAndSportTypeDiscoveryManager.resolveIdentity(...)` and `getCandidateBSportTypes(...)` inside `finalizeLiveSession()`.
3. **Missing Map Polyline & Streams**: Because `finalizeLiveSession()` executes all post-workout steps inside a single unguarded method body and places map polyline and elevation/distance stream persistence at the very end (Step 5), any runtime exception in earlier steps (extrema persistence, aerobic efficiency/power metrics, commute/trainer heuristics, or clustering/auto-naming) aborts `finalizeLiveSession()` and skips persisting the workout's map polyline and streams.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `TrackerService.java` and `MySensorManager.java` confirms all four failure mechanisms reported in the independent code review:

### 2.1 Coupled SQLite Transaction in `TrackerService.endWorkout()` (`TrackerService.java:1235-1267`)
```java
        summariesDb.beginTransaction();
        try {
            // save the accumulated SensorTypes
            if (banalService != null) {
                summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, banalService.getAccumulatedSensorTypeSet());
            }

            // update the summaries
            ContentValues summaryValues = new ContentValues();
            summaryValues.put(WorkoutSummaries.FINISHED, 1);

            if (banalService != null) {
                summaryValues.put(WorkoutSummaries.GC_DATA, banalService.getAccumulatedGCDataString());
            }

            long sportTypeId = getSportTypeId();
            summaryValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
            summaryValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name());

            summariesDb.update(WorkoutSummaries.TABLE,
                    summaryValues,
                    WorkoutSummaries.C_ID + "=" + mWorkoutID,
                    null);

            summariesDb.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error updating summaries table during endWorkout transaction", e);
        } finally {
            summariesDb.endTransaction();
        }
```
* **Failure Mechanism**: Setting `WorkoutSummaries.FINISHED = 1` is bundled in the exact same SQLite transaction as `saveAccumulatedSensorTypes(...)`, `banalService.getAccumulatedGCDataString()`, `getSportTypeId()`, and `SportTypeDatabaseManager.getInstance(this).getBSportType(sportTypeId).name()`.
* If any of those auxiliary operations throws an exception (e.g., `ConcurrentModificationException` while iterating over `getAccumulatedSensorTypeSet()`, or a failure in sport type resolution / `GC_DATA` retrieval), `summariesDb.setTransactionSuccessful()` is never reached, and `summariesDb.endTransaction()` rolls back the entire transaction—leaving `FINISHED = 0`.

### 2.2 Live Mutable `HashSet` Returned by `MySensorManager.getAccumulatedSensorTypeSet()` (`MySensorManager.java:86, 158-160, 325`)
```java
    protected Set<SensorType> mAccumulatedSensorTypeSet = new HashSet<SensorType>();
    ...
    public synchronized Set<SensorType> getAccumulatedSensorTypeSet() {
        return mAccumulatedSensorTypeSet;
    }
```
* **Failure Mechanism**: Although `getAccumulatedSensorTypeSet()` is `synchronized`, it returns a direct reference to the internal mutable `HashSet` `mAccumulatedSensorTypeSet`.
* When `WorkoutSummariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, banalService.getAccumulatedSensorTypeSet())` iterates over the returned `Set` (`for (SensorType sensorType : sensorTypes)` at `WorkoutSummariesDatabaseManager.java:1212`) outside `MySensorManager`'s monitor lock while a concurrent sensor discovery/registration thread calls `MySensorManager.registerSensor()` -> `notifyNewSensor()` -> `mAccumulatedSensorTypeSet.add(mySensor.getSensorType())`, `HashSet`'s fail-fast iterator throws `java.util.ConcurrentModificationException`.
* Because `saveAccumulatedSensorTypes()` runs inside the shared `endWorkout()` transaction before `FINISHED = 1` is committed, this `ConcurrentModificationException` aborts the transaction and leaves `FINISHED = 0`.

### 2.3 Unguarded Division by Zero in `TrackerService.getAverageSpeed()` (`TrackerService.java:1812-1818, 2000, 2020`)
```java
    protected boolean averageSpeedCalculateable() {
        return mTimeActive_s != 0;
    }

    protected double getAverageSpeed() {
        return mDistanceTotal_m / mTimeActive_s;
    }
```
* **Failure Mechanism**:
  1. `averageSpeedCalculateable()` checks `mTimeActive_s != 0` rather than `mTimeActive_s > 0`.
  2. `getAverageSpeed()` performs raw floating-point division `mDistanceTotal_m / mTimeActive_s` without guarding `mTimeActive_s <= 0`. When `mTimeActive_s == 0` and `mDistanceTotal_m == 0.0`, IEEE 754 floating-point division yields `Double.NaN`; when `mDistanceTotal_m > 0.0` and `mTimeActive_s == 0`, it yields `Double.POSITIVE_INFINITY`.
  3. In `finalizeLiveSession()`, lines 2000 and 2020 invoke `getAverageSpeed()` **unconditionally** without checking `averageSpeedCalculateable()`:
     - `discoveryManager.resolveIdentity(activeRemoteDeviceIds, banalSportType, getAverageSpeed());` (line 2000)
     - `discoveryManager.getCandidateBSportTypes(banalSportType, getAverageSpeed());` (line 2020)
  4. Furthermore, if `mDistanceTotal_m` were non-finite, `NaN`/`Infinity` could propagate into `WorkoutSummaries.SPEED_AVERAGE_mps` or `ExtremaType.AVG` for `SensorType.SPEED_mps`.

### 2.4 Non-Snapshotted `mBanalService` Dereference & Monolithic Execution in `finalizeLiveSession()` (`TrackerService.java:1822-2087`)
* **Direct `mBanalService` Read Race (`TrackerService.java:1894-1896`)**:
  ```java
  BSportType liveSport = (mBanalService != null && mBanalService.getBSportType() != null)
          ? mBanalService.getBSportType()
          : BSportType.UNKNOWN;
  ```
  Here `mBanalService` (which is `volatile`) is read three separate times instead of using a single local snapshot (`final BANALServiceComm banalService = mBanalService;`). If `onServiceDisconnected` sets `mBanalService = null` between the null check and `.getBSportType()`, a `NullPointerException` is thrown.
* **Sub-Step Coupling & Late Map/Stream Persistence (`TrackerService.java:1822-2087`)**:
  In `finalizeLiveSession()`, Step 5 (*Finalize Map and Streams*, lines 2073-2086) is executed last, after:
  - Step 1: START / END position extrema updates (lines 1829-1841)
  - Step 2: Sensor running stats MIN / MAX / AVG / END extrema updates (lines 1843-1868)
  - Step 2b: Aerobic Efficiency / Decoupling and Power Training metrics (lines 1870-1966)
  - Step 2c: Max line distance and apex calculation (lines 1968-1983)
  - Step 3: `guessCommuteAndTrainer()` (line 1986)
  - Step 4: Hardware/route identity arbitration, `WorkoutClusterEngine.suggestCluster()`, `assignClusterToWorkout()`, and `WorkoutAutoNamingHelper` (lines 1988-2071)
  Because Steps 1, 2, 2c, 3, and 4 have no individual `try/catch` isolation and run before Step 5, any exception in extrema persistence, `guessCommuteAndTrainer()`, `EquipmentAndSportTypeDiscoveryManager`, `WorkoutClusterEngine`, or `WorkoutAutoNamingHelper` aborts `finalizeLiveSession()` and prevents the map polyline and altitude/distance streams from being saved.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Persist `WorkoutSummaries.FINISHED = 1` first in its own dedicated, minimal SQLite transaction/update in `TrackerService.endWorkout()`, completely decoupled from auxiliary metadata updates (`saveAccumulatedSensorTypes`, `GC_DATA`, `SPORT_ID`, `B_SPORT`), so auxiliary failures never roll back `FINISHED = 1`.
  2. Isolate `saveAccumulatedSensorTypes`, `GC_DATA`, and `SPORT_ID`/`B_SPORT` updates in independent `try/catch` guards within `endWorkout()`.
  3. Return a thread-safe defensive copy (`new HashSet<>(mAccumulatedSensorTypeSet)`) from `MySensorManager.getAccumulatedSensorTypeSet()` and ensure all mutations to `mAccumulatedSensorTypeSet` (including `notifyNewSensor`) are synchronized on the `MySensorManager` monitor so concurrent sensor registration cannot cause `ConcurrentModificationException`.
  4. Harden `TrackerService.averageSpeedCalculateable()` (`mTimeActive_s > 0`) and `TrackerService.getAverageSpeed()` to return `0.0` whenever `mTimeActive_s <= 0` or the quotient is non-finite (`!Double.isFinite(...)`), preventing `NaN` or `Infinity` at `0 s` active time.
  5. Snapshot `final BANALServiceComm banalService = mBanalService;` once at the top of `finalizeLiveSession()` with null-safe access throughout, and split `finalizeLiveSession()` into independently `try/catch`-guarded steps with **Map Polyline & Streams persisted first**, followed by Extrema (START/END/MIN/MAX/AVG/LINE_DISTANCE), Physiological/Power Metrics, Commute/Trainer Heuristics, and Identity/Clustering/Auto-Naming.
  6. Add comprehensive unit tests verifying 0 s active time finalization without `NaN`/`Infinity`, fault isolation when `mBanalService == null` or when individual finalization sub-steps throw (still persisting `FINISHED = 1`, polyline, and streams), and concurrent sensor registration immunity in `MySensorManager.getAccumulatedSensorTypeSet()`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to database table schemas (`WorkoutSummaries` or `WorkoutSamples`).
  * No changes to UI composables, clustering algorithms, or FIT/TCX/GPX file exporters.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  * Net-new requirement `REQ-TRK-030` (*Fault-Isolated Workout Finalization, Dedicated `FINISHED=1` Commit, Defensive `AccumulatedSensorTypeSet` Copy, and Zero-Duration Average Speed Guard*), complementing `REQ-TRK-015` / `REQ-TRK-016` (`ATT-2641`, `ATT-2972`) and `REQ-TRK-027` (`ATT-3981`).
  * Targets `TrackerService.java` and `MySensorManager.java`.
* **Historical Origin & Commit Trace**:
  * `MySensorManager.getAccumulatedSensorTypeSet()` and `TrackerService.endWorkout()` date back to the initial `TrackerService` implementation and were wrapped in a single SQLite transaction during `ATT-2641` / `ATT-2972` (Rule 29).
  * `TrackerService.finalizeLiveSession()` was expanded across `SCRUM-108`, `SCRUM-200`, `ATT-528`, `ATT-773`, `ATT-1398`, `ATT-3860`, and `ATT-3861` as post-workout features (extrema, clustering, auto-naming, aerobic efficiency, NP/IF/TSS) were progressively appended before the final map/stream write.
* **Root Reason for Existing Formulation**:
  * The single transaction in `endWorkout()` was originally written to batch summary updates at workout stop, assuming `saveAccumulatedSensorTypes()`, `getAccumulatedGCDataString()`, and `getSportTypeId()` were infallible in-memory reads.
  * `finalizeLiveSession()` placed map/stream encoding at the end as "step 5 (one last check)" before clustering and auto-naming grew into complex database-backed subsystems.
* **Preservation of Core Invariants**:
  * Committing `FINISHED = 1` first in its own dedicated SQLite transaction guarantees Rule 29 (`FINISHED = 1` must never be lost on workout completion) while still persisting `GC_DATA`, `SPORT_ID`, `B_SPORT`, and accumulated sensor types when available.
  * Persisting map polyline and elevation/distance streams first and isolating each subsequent finalization stage in its own `try/catch (Throwable t)` block preserves 100% of existing post-workout enrichment (extrema, EF/decoupling, NP/IF/TSS, commute/trainer flag, cluster matching, and Lieblingsorte auto-naming) while guaranteeing that a failure in any single enrichment step never suppresses map/stream persistence or other independent steps.

---

## 5. Architectural Strategy & High-Level Solution

1. **`MySensorManager.java` Concurrency Hardening**:
   * In `getAccumulatedSensorTypeSet()`, return `new HashSet<>(mAccumulatedSensorTypeSet)` while holding the `synchronized` monitor on `MySensorManager`.
   * Mark `notifyNewSensor(MySensor mySensor)` as `synchronized` so every read and mutation of `mAccumulatedSensorTypeSet` and `mAccumulatedGCData` is strictly serialized under the `MySensorManager` instance lock.
2. **`TrackerService.java` Zero-Duration Average Speed Guard**:
   * Update `averageSpeedCalculateable()` to `return mTimeActive_s > 0 && Double.isFinite(mDistanceTotal_m) && mDistanceTotal_m >= 0.0;`.
   * Update `getAverageSpeed()` to return `0.0` when `!averageSpeedCalculateable()` (or if `mDistanceTotal_m / mTimeActive_s` is not finite), ensuring `getAverageSpeed()` never returns `NaN` or `Infinity` under any caller.
3. **`TrackerService.endWorkout()` Transaction Decoupling**:
   * **Step A (Critical `FINISHED = 1` Transaction)**: Immediately mark `WorkoutSummaries.FINISHED = 1` for `mWorkoutID` in its own dedicated, minimal SQLite transaction on `summariesDb`.
   * **Step B (Auxiliary Accumulated Sensor Types)**: In a separate `try/catch (Throwable t)` block, if `banalService != null`, call `summariesDatabaseManager.saveAccumulatedSensorTypes(mWorkoutID, banalService.getAccumulatedSensorTypeSet())`.
   * **Step C (Auxiliary Summary Metadata — `GC_DATA`, `SPORT_ID`, `B_SPORT`)**: In a separate `try/catch (Throwable t)` block (with each field resolved defensively), update `GC_DATA`, `SPORT_ID`, and `B_SPORT` on `WorkoutSummaries.TABLE`.
4. **`TrackerService.finalizeLiveSession()` Snapshot, Reordering & Step Isolation**:
   * Snapshot `final BANALServiceComm banalService = mBanalService;` once at the start of `finalizeLiveSession()` and use `banalService` across all sub-steps.
   * Execute **Map Polyline & Streams Persistence FIRST** in its own `try/catch (Throwable t)` block (`PolyUtil.simplify`, `PolyUtil.encode`, `NumericalEncodingUtils.encodeDoubles`, `summariesManager.updateMapAndStreams`, `repository.setMapPolyline`, `repository.setElevationStreams`).
   * Execute **Start/End Position, Sensor Extrema & Max Line Distance Persistence** in its own `try/catch (Throwable t)` block, guarding against `NaN`/`Infinity` values.
   * Execute **Aerobic Efficiency / Decoupling & Power Training Metrics** in its own `try/catch (Throwable t)` block using the local `banalService` snapshot.
   * Execute **`guessCommuteAndTrainer()`** in its own `try/catch (Throwable t)` block.
   * Execute **Identity Arbitration, Clustering & Auto-Naming** in its own `try/catch (Throwable t)` block using the local `banalService` snapshot and safe `getAverageSpeed()`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. Thread safety on database and `MySensorManager` sensor registration locks.
  3. Guaranteed dispatch of `TRACKING_FINISHED_INTENT` (both system and `LocalBroadcastManager`) in `endWorkout()`'s `finally` block (Rule 29).
  4. Parent ticket Human Decision Gate remains strictly enforced (Rule 3).
* **Risk Rating**: **LOW**
  * The changes strictly add fault isolation (`try/catch`), defensive collection copying, and division-by-zero guards without altering normal-path business logic or database schemas.
