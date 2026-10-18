# Stage 1 Analysis: ATT-3980 - [Highest] Resumed workout writes all samples to a table named "null"

**Ticket**: [ATT-3980](https://rainerblind.atlassian.net/browse/ATT-3980)  
**Sub-task**: [ATT-4045](https://rainerblind.atlassian.net/browse/ATT-4045) (`[Analysis]`)  
**Parent Epic**: None (Standalone Defect from `code-review-2026-10`)  
**Target Release**: `TBD (Set on completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.9`  
**Branch**: `feature/ATT-3980`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

When an active workout is interrupted (either by user pause/resume via `StartType.RESUME_BY_USER` or by Android OS process death and service recreation via `StartType.RESUME_SERVICE_RECREATION`), `TrackerService` is restarted and binds to `BANALService` asynchronously via `bindService(new Intent(this, BANALService.class), mBanalConnection, Context.BIND_AUTO_CREATE)` in `onCreate()`.

Because Android service binding is asynchronous, `mBanalService` is `null` when `TrackerService.onStartCommand()` executes immediately after `onCreate()`. Consequently:
1. `recreateValuesWhenResuming()` is deferred to `mBanalConnection.onServiceConnected()`, leaving `mBaseFileName` as `null` during `onStartCommand()`.
2. `TrackerService.onStartCommand()` (lines 612–614) prematurely computes:
   ```java
   if (startType != StartType.START_NORMAL) {
       mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName);
   }
   ```
   Since `mBaseFileName` is `null`, `WorkoutSamplesDatabaseManager.getTableName(null)` returns `"\"null\""` (targeting a SQLite table literally named `"null"`).
3. Furthermore, `onStartCommand()` sets `mTableInitializationFuture = CompletableFuture.completedFuture(null)` (lines 570 and 581) and dispatches `notifyTrackingStarted(mWorkoutID)` with `mWorkoutID == 0` (line 627) before the resumed state is actually loaded.
4. When `mBanalConnection.onServiceConnected()` subsequently fires and invokes `recreateValuesWhenResuming()` -> `applyResumedState(ResumedWorkoutState state)` (lines 843–863), `applyResumedState()` restores `mBaseFileName`, `mWorkoutID`, `mTimeTotal_s`, `mTimeActive_s`, and `mDistanceTotal_m`, **but never recomputes `mSamplesTableName`**.
5. For the entire remainder of the resumed workout, every 1 Hz tick in `sampleAndWriteToDb()` attempts `samplesDb.insertOrThrow("\"null\"", null, samplingValues)`, which fails with `SQLiteException: no such table: null`, falls into `handleMissingColumns(...)` where the second failure is caught and logged, and then `summariesDb.update(WorkoutSummaries.TABLE, summaryValues, ...)` still commits. Thus, workout summary totals continue to increment on screen while **100% of 1 Hz sensor samples after the resume are silently lost**. Additionally, any barometric altitude correction broadcast (`mAltitudeCorrectionReceiver`) executes `UPDATE "null" SET ALTITUDE = ...`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Asynchronous Service Binding vs. Synchronous `onStartCommand` Table Name Resolution
In `TrackerService.java`:
- **Line 265 & 270**: `mBaseFileName` and `mSamplesTableName` are initialized to `null` upon `TrackerService` instantiation.
- **Line 428**: `onCreate()` initiates asynchronous binding to `BANALService`. `mBanalConnection.onServiceConnected()` will only be invoked on a later main-thread looper turn after `onStartCommand()` returns.
- **Lines 568–614 (`onStartCommand`)**:
  - In `case RESUME_BY_USER:` (lines 568–577) and `case RESUME_SERVICE_RECREATION:` (lines 579–609), when `mBanalService == null`, `recreateValuesWhenResuming()` is not executed; instead, `mResumeFromPausedWhenConnectedToBanalService = true` or `mResumeTrackingWhenConnectedToBanalService = true` is set.
  - Immediately after the `switch (startType)` block, lines 612–614 execute:
    ```java
    if (startType != StartType.START_NORMAL) {
        mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName);
    }
    ```
  - And in `WorkoutSamplesDatabaseManager.java:627`:
    ```java
    @NonNull
    public static String getTableName(String workoutName) {
        return "\"" + workoutName + "\"";
    }
    ```
    Passing `workoutName = null` performs Java string concatenation `"\"null\""`, assigning `mSamplesTableName = "\"null\""`.

### 2.2 Missing `mSamplesTableName` Recomputation in `applyResumedState()`
In `TrackerService.java:843–863`:
```java
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
void applyResumedState(@NonNull ResumedWorkoutState state) {
    mBaseFileName = state.baseFileName;
    mWorkoutID = state.workoutId;
    mTimeTotal_s = state.timeTotal_s;
    mTimeActive_s = state.timeActive_s;
    mDistanceTotal_m = state.distanceTotal_m;
    ...
```
When `ATT-3549` refactored `recreateValuesWhenResuming()` into `ResumedWorkoutState` and `applyResumedState()`, `mBaseFileName` was updated from `state.baseFileName`, but `mSamplesTableName` was never updated from `WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)`.

### 2.3 Premature `mTableInitializationFuture` Completion & `onServiceConnected` Ordering
1. **Un-gated Resume Initialization**:
   In `START_NORMAL` (`REQ-STB-009` / `ATT-1245`), `mTableInitializationFuture` is created as an incomplete `new CompletableFuture<>()` that completes only after `mWorkoutID` and the sample table are ready, and `notifyTrackingStarted(mWorkoutID)` is emitted only upon completion.
   By contrast, in `RESUME_BY_USER` (line 570) and `RESUME_SERVICE_RECREATION` (line 581), `mTableInitializationFuture` was immediately assigned `CompletableFuture.completedFuture(null)` in `onStartCommand()`, even when `mBanalService == null` and `recreateValuesWhenResuming()` had not yet run.
2. **Order of Operations in `mBanalConnection.onServiceConnected` (lines 374–395)**:
   In `onServiceConnected`, `onSearchingFinished()` (line 375) and `createNewLap()` (line 380) were invoked *before* `mResumeFromPausedWhenConnectedToBanalService` (line 383) and `mResumeTrackingWhenConnectedToBanalService` (line 389). Thus, if `BANALService` had already finished searching when `TrackerService` connected on resume, `onSearchingFinished()` executed while `mWorkoutID` was still `0`.
3. **Early Capture in `mAltitudeCorrectionReceiver` (lines 287–288)**:
   `mAltitudeCorrectionReceiver` captured `final long workoutId = mWorkoutID;` and `final String samplesTable = mSamplesTableName;` *outside* `mTableInitializationFuture.thenAcceptAsync(...)`. If an altitude correction broadcast arrived before initialization completed, it captured `workoutId = 0` and `samplesTable = null` (or `"\"null\""`).
4. **Swallowed Sample Insert Errors in `sampleAndWriteToDb` / `handleMissingColumns` (lines 1393–1416, 1467–1486)**:
   When `samplesDb.insertOrThrow(tableName, null, samplingValues)` threw `SQLException`, `handleMissingColumns` caught the resulting `Exception` without rethrowing or rate-limited warning/error surfacing, and `sampleAndWriteToDb` still marked `samplesDb.setTransactionSuccessful()` and `summariesDb.setTransactionSuccessful()`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Recompute `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` inside `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)` and provide a safe accessor `resolveSamplesTableName()` / `getSamplesTableName()` that never returns `"\"null\""`.
  2. Remove the premature `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` and premature `notifyTrackingStarted(mWorkoutID)` calls in `onStartCommand()` when `mBaseFileName` is not yet restored.
  3. Gate resume initialization (`RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`) with an incomplete `CompletableFuture<Void>` barrier (`mTableInitializationFuture`) in `onStartCommand()` that completes inside `applyResumedState()` (or fails gracefully if no resume record exists / DB query fails), and ensure `mBanalConnection.onServiceConnected()` executes `recreateValuesWhenResuming()` *before* `onSearchingFinished()` and `createNewLap()`.
  4. Gate the 1 Hz `tracker` runnable and `sampleAndWriteToDb()` so no sample tick or DB write executes before `mTableInitializationFuture` has completed normally, and read `mWorkoutID` and `resolveSamplesTableName()` inside `mTableInitializationFuture.thenAcceptAsync(...)` in both `sampleAndWriteToDb()` and `mAltitudeCorrectionReceiver`.
  5. Guard `WorkoutSamplesDatabaseManager.getTableName(String workoutName)` and `TrackerService` sample persistence against `null` / `"null"` table names, and log sample insert / schema-fix failures rate-limited at `WARN`/`ERROR` rather than silently succeeding the sample transaction.
  6. Add comprehensive unit tests in `TrackerServiceResumeTest.kt` verifying both asynchronous (`mBanalService == null` in `onStartCommand`, followed by `onServiceConnected`) and synchronous (`mBanalService != null` in `onStartCommand`) resume paths for `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `WorkoutSummaries.db` or `WorkoutSamples.db` database schema versions.
  * Altering UI screens, Compose components, or localization files.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-026` (*Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier, and Sample Write Error Surfacing in TrackerService*), complementing `REQ-STB-009` (`ATT-1245`) and `ATT-3549`. Targets `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, and `TrackerServiceResumeTest.kt`.
* **Historical Origin & Commit Trace**:
  * `ATT-1245` (`REQ-STB-009`): Introduced `mTableInitializationFuture` for `START_NORMAL` to decouple `CREATE TABLE` from the main thread, while setting `mTableInitializationFuture = CompletableFuture.completedFuture(null)` on `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`.
  * `ATT-3549`: Extracted `ResumedWorkoutState` and `applyResumedState()` from `recreateValuesWhenResuming()` to replace an unbounded full-table query with a `LIMIT 1` query, while retaining line 613 `if (startType != StartType.START_NORMAL) { mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName); }` in `onStartCommand()`.
* **Root Reason for Existing Formulation**:
  * Line 613 in `onStartCommand()` was written under the legacy assumption that `recreateValuesWhenResuming()` always ran synchronously during `onStartCommand()`. However, on cold process recreation or fresh service start, `bindService()` in `onCreate()` has not yet delivered `onServiceConnected()` when `onStartCommand()` runs, so `mBanalService` is `null` and `recreateValuesWhenResuming()` is deferred to `onServiceConnected()`.
* **Preservation of Core Invariants**:
  * Recomputing `mSamplesTableName` inside `applyResumedState()`, gating resume on `mTableInitializationFuture` until `applyResumedState()` completes, and restoring resume state in `onServiceConnected()` prior to `onSearchingFinished()` preserves all `REQ-STB-009` main-thread ANR protections, `ATT-3549` $O(1)$ cursor projections, and Android 14+ Foreground Service startup SLAs (`REQ-STB-002`, `REQ-TRK-015`).

---

## 5. Architectural Strategy & High-Level Solution

1. **`TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)`**:
   - Immediately compute `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName);` after setting `mBaseFileName = state.baseFileName;`.
   - Complete `mTableInitializationFuture` (`if (mTableInitializationFuture != null && !mTableInitializationFuture.isDone()) { mTableInitializationFuture.complete(null); }`) and invoke `notifyTrackingStarted(mWorkoutID)` once resumed state is applied.
2. **`TrackerService.onStartCommand()` & `mBanalConnection.onServiceConnected()`**:
   - In `case RESUME_BY_USER:` and `case RESUME_SERVICE_RECREATION:`, initialize `mTableInitializationFuture = new CompletableFuture<>();`.
   - Remove the premature `if (startType != StartType.START_NORMAL) { mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName); }` and `if (startType != StartType.START_NORMAL) { notifyTrackingStarted(mWorkoutID); }` blocks from `onStartCommand()`.
   - In `mBanalConnection.onServiceConnected()`, execute the `mResumeFromPausedWhenConnectedToBanalService` and `mResumeTrackingWhenConnectedToBanalService` blocks *before* `if (!BANALService.isSearching()) { onSearchingFinished(); }` and `if (mCreateNewLapWhenConnectedToBanalService) { ... }` so `mWorkoutID` and `mSamplesTableName` are initialized before any downstream callback uses them.
   - In `recreateValuesWhenResuming()`, if the cursor is empty or an exception occurs, complete `mTableInitializationFuture` exceptionally so queued sample/altitude tasks do not execute against an uninitialized state.
3. **Sampling Loop & Database Write Guards (`TrackerService` & `WorkoutSamplesDatabaseManager`)**:
   - In `tracker.run()`, gate execution on `mTableInitializationFuture.isDone() && !mTableInitializationFuture.isCompletedExceptionally()`.
   - Provide `resolveSamplesTableName()` in `TrackerService` that returns `mSamplesTableName` if valid, or derives it from `mBaseFileName` when non-null, and rejects `null`, `""`, or `"\"null\""`.
   - In `mAltitudeCorrectionReceiver.onReceive()`, resolve `workoutId = mWorkoutID` and `samplesTable = resolveSamplesTableName()` *inside* `mTableInitializationFuture.thenAcceptAsync(...)` and guard against invalid table names.
   - In `sampleAndWriteToDb()` and `handleMissingColumns()`, guard against invalid/`"null"` table names, return `boolean` from `handleMissingColumns(...)`, and rate-limit `Log.e` / `Log.w` error logging when sample insertion fails so failures are clearly surfaced in logcat without flooding at 1 Hz.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing `TrackerService` unit tests (`TrackerServiceAsyncInitTest`, `TrackerServiceResilienceTest`, `TrackerServiceFinalizationTest`, `TrackerServiceFGSTypeTest`, `TrackerServiceLapTest`, `TrackerServiceResumeTest`, `TrackerServiceWorkoutStartTest`, `FreshInstallLocationInitTest`).
  2. `performStartForeground()` in `onStartCommand()` continues to execute synchronously on the main thread without blocking on binder connection or disk I/O (Android 14+ FGS SLA).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * The fix is localized to `TrackerService.java` and `WorkoutSamplesDatabaseManager.java`, directly restoring the intended causal ordering between `recreateValuesWhenResuming()` / `applyResumedState()` and `mSamplesTableName` resolution.
