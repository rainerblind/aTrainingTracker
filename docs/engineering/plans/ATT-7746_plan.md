# Stage 3: Implementation Plan - ATT-7746: Re-Entrant Concurrency in TrackerService

**Ticket**: [ATT-7746](https://rainerblind.atlassian.net/browse/ATT-7746) | **Sub-task**: `[Impl-Plan]` | **Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7746`  
**Requirement Mapping**: `REQ-TRK-076` | **Test Mapping**: `TST-TRK-076`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Immediate UI readiness (`TrackingMode.READY`) upon `ACTION_STOP` allows starting a workout while `finalizationTask` is finalizing the prior session on `mDbExecutor`. Premature shutdown of `mDbExecutor` and `mScheduler` in `handleActionStop()` caused fatal `RejectedExecutionException`, shared field corruption (`mWorkoutID`, `mBaseFileName`, `mLiveSession`), and premature service termination by stale completion callbacks.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-076` (*Defensive Concurrency and Re-Entrant Stop-to-Start Lifecycle in TrackerService*)
* **Test Mapping**: `TST-TRK-076` (*Defensive Concurrency and Re-Entrant Stop-to-Start Lifecycle Verification Suite*)
  * `REQ-TRK-076.1` $\rightarrow$ `TST-TRK-076.1`, `TST-TRK-076.4` (Deferred executor shutdown strictly to `onDestroy()`)
  * `REQ-TRK-076.2` $\rightarrow$ `TST-TRK-076.2` (Session-isolated snapshot parameters for finalization)
  * `REQ-TRK-076.3` $\rightarrow$ `TST-TRK-076.3` (Monotonic session-generation teardown gating)
  * `REQ-TRK-076.4` $\rightarrow$ `TST-TRK-076.1`, `TST-TRK-076.5` (State re-initialization and receiver re-registration)

---

## 3. System Invariants & Preserved Behavior
1. **Single-Thread FIFO Execution**: All DB operations on `mDbExecutor` remain strictly serialized in FIFO order.
2. **Foreground LMK Protection**: Foreground service priority remains elevated until all stop tasks complete.
3. **Zero UI / Main-Thread Blocking**: Finalization runs asynchronously; main thread only performs lightweight state changes.
4. **Clean Terminal Resource Teardown**: Terminal shutdown of `mDbExecutor`, `mScheduler`, and `mRrFlushExecutor` strictly guaranteed in `onDestroy()`.

---

## 4. Proposed Architectural Changes
* **`TrackerService.java`**:
  * Defer `mDbExecutor.shutdown()`, `mScheduler.shutdownNow()`, and `mRrFlushExecutor.shutdown()` from `handleActionStop()` to `onDestroy()`.
  * Add atomic monotonic counter `mSessionGeneration` and session state flags (`mIsStopping`, `mTrackingInterrupted`).
  * Capture immutable local session parameters (`stoppingWorkoutId`, `stoppingBaseFileName`, `stoppingLiveSession`) in `handleActionStop()` and pass explicitly into `endWorkout(...)` / `finalizeLiveSession(...)`.
  * Gate teardown callbacks (`performStopSelf`, `performStopForeground`, `safeUnbindBanalService`, `releaseWakeLock`) on `mSessionGeneration == stoppingGeneration && mIsStopping`.
  * Manage transient broadcast receivers with idempotent `registerTransientReceivers()` / `unregisterTransientReceivers()`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Concurrency Decoupling & Parameter Isolation in `TrackerService.java`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes: Omit executor shutdown from `handleActionStop()`; capture snapshot parameters; gate `finalizationTask.finally` teardown behind `mSessionGeneration == stoppingGeneration && mIsStopping`.

### Step 2: Session-Isolated Overloads for Finalization Pipeline
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes: Implement `endWorkout(snapshot, workoutId, baseFileName, liveSession)`, `finalizeLiveSession(...)`, and `createNewLap(...)` overloads; preserve legacy signatures as delegating wrappers.

### Step 3: Re-Initialization & Receiver Lifecycle on Restart
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Changes: In `onStartCommand(START_NORMAL)`, increment `mSessionGeneration`, reset `mIsStopping = false` and `mTrackingInterrupted = false`, re-register receivers, ensure wake lock. In `onDestroy()`, ensure `mDbExecutor.shutdown()` in `mIsStopping` branch.

### Step 4: Unit Test Suite Implementation
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceFinalizationTest.kt`
* Changes: Add test cases verifying re-entrant `START_NORMAL` during stop, snapshot parameter isolation, teardown suppression on restart, and clean terminal exit.

### Step 5: Verification
* Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in `TrackerServiceFinalizationTest.kt` followed by full tracker package suite.
* **Rollback**: Atomic Git branch revert of `feature/ATT-7746` cleanly restores prior state without side-effects.
