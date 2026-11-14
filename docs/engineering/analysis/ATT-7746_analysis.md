# Stage 1 Analysis: ATT-7746 - Fix RejectedExecutionException in TrackerService.onStartCommand

**Ticket**: [ATT-7746](https://rainerblind.atlassian.net/browse/ATT-7746) | **Sub-task**: `[Analysis]` | **Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7746`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
In Firebase Crashlytics session `6AF06FAC03D6000135CE3E21949E1423_DNE_0_v2` (v4.9.41), starting a workout immediately after stopping a long session (~121h, 436k samples) crashed fatally with `RejectedExecutionException` at `TrackerService.java:837`. Immediate UI readiness (`TrackingMode.READY`) allowed `onStartCommand(START_NORMAL)` re-entry into the still-alive `TrackerService` while `finalizationTask` was executing `finalizeLiveSession()`. `handleActionStop()` had already shut down `mDbExecutor` and `mScheduler`, causing task rejection, state corruption, and premature service teardown.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Premature Executor Shutdown**: `handleActionStop()` invoked `mDbExecutor.shutdown()` and `mScheduler.shutdownNow()` synchronously while `finalizationTask` was still processing on `mDbExecutor`. Subsequent `onStartCommand` tasks (`mDbExecutor.submit` / `mScheduler.scheduleAtFixedRate`) threw `RejectedExecutionException`.
2. **Shared Mutable State Bleed**: `mWorkoutID`, `mBaseFileName`, and `mLiveSession` were mutated on main thread in `START_NORMAL` while background `endWorkout()` was reading them, cross-contaminating finished session data.
3. **Unconditional Teardown & Receiver Loss**: `finalizationTask`'s `finally` block unconditionally invoked `stopForeground()`, `stopSelf()`, `safeUnbindBanalService()`, and `releaseWakeLock()`, killing newly restarted workouts. Stop also unregistered transient broadcast receivers without re-registering on restart.
* **Independent Peer Review & Verification**: Confirmed hypothesis against live codebase. Verified that deferring executor shutdown to `onDestroy()` is safe because `mDbExecutor` is a FIFO single-thread queue. Identified critical audit finding: `onDestroy()`'s `if (mIsStopping)` early exit must also call `mDbExecutor.shutdown()` to prevent thread leaks, `mTrackingInterrupted` must be reset in `START_NORMAL`, receivers must be re-registered, and `acquireWakeLock()` ensured.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Defer `mDbExecutor`, `mScheduler`, and `mRrFlushExecutor` shutdown strictly to `onDestroy()`.
  * Pass isolated snapshot parameters (`workoutId`, `baseFileName`, `liveSession`) into `endWorkout()` / `finalizeLiveSession()`.
  * Guard `finalizationTask` teardown (`stopSelf`, `stopForeground`, `unbind`, `wakeLock`) with monotonic `mSessionGeneration` counter.
  * Reset `mIsStopping = false`, `mTrackingInterrupted = false`, and re-register receivers in `onStartCommand`.
* **Out-of-Scope Non-Goals**: No changes to UI layer or database schema migrations; no modifications to `BANALService` internal engine.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-034` (Clauses 2 & 3), targeting `TrackerService.java` and `TrackerServiceFinalizationTest.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-42.9 (`ATT-4098`, commit `4795a2cf`). Introduced `ACTION_STOP` to keep service in foreground during `endWorkout()`.
* **Root Reason for Existing Formulation**: Assumed linear single-session lifecycle where `ACTION_STOP` terminates before new sessions start.
* **Preservation of Core Invariants**: Retains 1-Hz tick suppression (`cancelTrackerHandle()`), foreground LMK protection, and dedicated `FINISHED = 1` commit, while adding safe multi-session re-entrancy.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-034` (Amended Clauses 2 & 3) / Net-New `REQ-TRK-076`.
* **Affected Architecture Elements**: `TrackerService.java`, `TrackerServiceFinalizationTest.kt`.
* **Verification Impact & Test Surface**: Unit tests in `TrackerServiceFinalizationTest.kt` simulating immediate `START_NORMAL` during in-flight `finalizationTask`, asserting zero `RejectedExecutionException`, FIFO execution, and state isolation.
* **Risk Assessment & Invariants**: Low risk. Preserves FIFO execution, foreground protection, and strict ASPICE governance.

---

## 6. Architectural Strategy & High-Level Solution
1. In `handleActionStop()`, call `cancelTrackerHandle()` but omit `mDbExecutor.shutdown()`, `mScheduler.shutdownNow()`, and `mRrFlushExecutor.shutdown()`. Move complete executor shutdown into `onDestroy()`.
2. Capture local snapshots of `workoutId`, `baseFileName`, and `liveSession` in `handleActionStop()`, passing them into `endWorkout(...)` and `finalizeLiveSession(...)`.
3. Track `mSessionGeneration`. In `onStartCommand(START_NORMAL)`, increment generation, reset `mIsStopping = false` and `mTrackingInterrupted = false`, re-register receivers, and ensure `acquireWakeLock()`.
4. In `finalizationTask.finally`, execute teardown (`safeUnbindBanalService()`, `performStopForeground()`, `performStopSelf()`, `releaseWakeLock()`) ONLY if `mSessionGeneration == stoppingGeneration && mIsStopping`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero `RejectedExecutionException`, zero state cross-contamination, strict single-thread FIFO execution, clean service destruction on terminal exit.
* **Risk Rating**: **LOW** (Defensive, targeted concurrency hardening with isolated parameter passing and generation gating).
