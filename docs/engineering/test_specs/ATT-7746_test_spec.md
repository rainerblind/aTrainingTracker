# Stage 2: Requirement & Test Specification - ATT-7746: Fix RejectedExecutionException in TrackerService.onStartCommand

**Ticket**: [ATT-7746](https://rainerblind.atlassian.net/browse/ATT-7746)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-TRK-076` (*Defensive Concurrency and Re-Entrant Stop-to-Start Lifecycle in TrackerService*)  
**Test Spec ID**: `TST-TRK-076`  
**Branch**: `feature/ATT-7746`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-TRK-076)

### 1.1 Problem Statement & Rationale
Immediate UI readiness (`TrackingMode.READY`) upon `ACTION_STOP` permits starting a new workout while `finalizationTask` is still processing. `handleActionStop()` previously shut down `mDbExecutor` and `mScheduler`, causing `RejectedExecutionException`, field cross-contamination, and premature service termination.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `TrackerService.handleActionStop()` SHALL NOT shut down `mDbExecutor`, `mScheduler`, or `mRrFlushExecutor`; complete executor shutdown MUST occur strictly in `onDestroy()`.
2. `TrackerService` SHALL capture immutable local snapshots of `workoutId`, `baseFileName`, and `liveSession` in `handleActionStop()` and pass them explicitly into `endWorkout(...)` and `finalizeLiveSession(...)`.
3. `TrackerService` SHALL gate teardown (`performStopSelf`, `performStopForeground`, `safeUnbindBanalService`, `releaseWakeLock`) behind a monotonic `mSessionGeneration` counter, executing only if `mSessionGeneration == stoppingGeneration && mIsStopping`.
4. `TrackerService.onStartCommand(START_NORMAL)` SHALL reset `mIsStopping = false`, `mTrackingInterrupted = false`, increment `mSessionGeneration`, and re-register transient broadcast receivers.

### 1.4 Acceptance Criteria (Given-When-Then)
- *Given* `TrackerService` is finalizing a workout asynchronously on `mDbExecutor`,
  *When* `onStartCommand(START_NORMAL)` is delivered to the same service instance,
  *Then* zero `RejectedExecutionException` is thrown, the new session task queues in FIFO order behind the finalization task, and the previous session's teardown callback does not invoke `performStopSelf()`.
- *Given* a normal stop without restart,
  *When* finalization completes and the service is destroyed,
  *Then* all executors (`mDbExecutor`, `mScheduler`, `mRrFlushExecutor`) are cleanly shut down in `onDestroy()`.

### 1.5 System Invariants
Zero `RejectedExecutionException`; strict single-thread FIFO execution on `mDbExecutor`; zero state contamination between consecutive sessions.

---

## 2. Test Specification (TST-TRK-076)

### Test Case 1: Re-Entrant Start Concurrency (`TST-TRK-076.1`, `TST-TRK-076.2`, `TST-TRK-076.3`)
- **Scope**: Unit Test in `TrackerServiceFinalizationTest.kt`
- **Preconditions**: `TrackerService` executing `finalizationTask` on `mDbExecutor`.
- **Action**: Call `onStartCommand(START_NORMAL)`.
- **Expected Result**: No rejection, task queued in FIFO order, previous session finalizes with isolated parameters, teardown for new session suppressed.

### Test Case 2: Clean Terminal Teardown (`TST-TRK-076.4`)
- **Scope**: Unit Test in `TrackerServiceFinalizationTest.kt`
- **Preconditions**: `TrackerService` stopped without restart (`mIsStopping == true`).
- **Action**: Call `onDestroy()`.
- **Expected Result**: `mDbExecutor`, `mScheduler`, and `mRrFlushExecutor` are shut down; no thread leaks.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-TRK-076.5`)
- **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*"`
- **Expected Result**: 100% pass rate across all tracking lifecycle unit tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-076.1` | Unit | `TrackerService.onStartCommand` | `REQ-TRK-076.1` | Specified |
| `TST-TRK-076.2` | Unit | `TrackerService.handleActionStop` | `REQ-TRK-076.2` | Specified |
| `TST-TRK-076.3` | Unit | `TrackerService.finalizationTask` | `REQ-TRK-076.3` | Specified |
| `TST-TRK-076.4` | Unit | `TrackerService.onDestroy` | `REQ-TRK-076.1` | Specified |
| `TST-TRK-076.5` | Tier 1 Unit | `com.atrainingtracker.trainingtracker.tracker.*` | `REQ-PRO-001` | Specified |
