# Stage 2: Requirement & Test Specification - ATT-6178: TrackerService.onDestroy Non-Blocking Finalization & Idempotent Cleanup

**Ticket**: [ATT-6178](https://issue-tracker/ATT-6178) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Blocks `ATT-6034`*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`1 SP`)  
**Requirement Mapping**: `REQ-TRK-034` (`REQ-TRK-034.4` amended; upholds `REQ-TRK-033.2`, `REQ-TRK-034.3`, `REQ-TRK-076.1`/`.3`)  
**Test Spec ID**: `TST-TRK-092` (`TST-TRK-092.1`–`TST-TRK-092.3`, atomically reserved via `jira_util.py reserve-id`)  
**Living Docs Shards**: `docs/requirements/req_tracking_cockpit_part1.md` (`v1.0.23`), `docs/tests/tst_tracking_cockpit_part2.md` (`v1.0.0`, preserving `tst_tracking_cockpit.md` at `3,995/4,000` lines)  
**Branch**: `feature/ATT-6178` | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22

---

## 1. Amended Formal Requirement (`REQ-TRK-034.4`)

### 1.1 Attributes (`REQ-PRO-077`, `REQ-PRO-131.4`)
- **System Requirement(s)**: `SYSREQ-TRK-004` (and upholds `SYSREQ-TRK-005` / `REQ-TRK-033.2`) — zero `SYS.2`/`SYS.3` changes required
- **Priority**: Medium (parent `REQ-TRK-034`; `tier-a` concurrency safety) | **Type**: Non-Functional | **Verification Method**: Unit
- **Implementation**: `TrackerService.java`, `TrackerServiceFinalizationTest.kt`

### 1.2 Normative Specification (`REQ-TRK-034.4` Amended)
1. **Non-Blocking `onDestroy()` While `mIsStopping == true` (No Main-Thread Wait, No Premature Unbind)**:
   - When `TrackerService.onDestroy()` is invoked while `mIsStopping == true`, `TrackerService` SHALL immediately shut down `mScheduler` (`shutdownNow()`), `mRrFlushExecutor` (`shutdown()`), and `mDbExecutor` (`shutdown()`, allowing already-queued `finalizationTask` to complete in FIFO order per `REQ-TRK-076.1`).
   - `TrackerService.onDestroy()` MUST NOT invoke `mFinalizationFuture.get(...)` or any blocking wait on the Main Thread, and when `mFinalizationFuture != null && !mFinalizationFuture.isDone()` it MUST NOT prematurely invoke `safeUnbindBanalService()`, `performStopForeground()`, or `releaseWakeLock()` while `endWorkout(...)` is still pending or executing on `mDbExecutor` (`REQ-TRK-033.2`, `REQ-TRK-034.3`).
   - If `mIsStopping == true` and `mFinalizationFuture == null || mFinalizationFuture.isDone()` (no async finalization callback in flight), `onDestroy()` SHALL execute idempotent `safeUnbindBanalService()`, `mBanalService = null`, and `releaseWakeLock()` defensively before returning.
   - If `mIsStopping == false`, `onDestroy()` SHALL enqueue fallback async finalization on `mDbExecutor` (`REQ-TRK-033.2`) and invoke fault-isolated main-thread cleanup upon completion.
2. **Idempotent, Fault-Isolated Post-`endWorkout` Main-Thread Teardown**:
   - The Main-Thread cleanup Runnable posted by `finalizationTask` (`handleActionStop` L1513–1531) SHALL execute `safeUnbindBanalService()`, `mBanalService = null`, `releaseWakeLock()`, `performStopForeground()`, and `performStopSelf()` strictly **after** `endWorkout(...)` finishes (gated on `mSessionGeneration.get() == stoppingGeneration && mIsStopping` per `REQ-TRK-076.3`).
   - Every teardown step (`safeUnbindBanalService()`, `releaseWakeLock()`, `performStopForeground()`, `performStopSelf()`) MUST be individually fault-isolated (`catch (Throwable)`) and idempotent so that executing the Runnable after an earlier `onDestroy()` (including when `stopForeground`/`stopSelf`/`unbindService`/`wakeLock.release` throw on a destroyed `Service`) never aborts subsequent cleanup steps, never crashes the Main Looper, and always completes `finalizationFuture.complete(null)` in `finally`.

### Requirement Archaeology & Chesterton's Fence Audit
1. **Original Requirement ID & Target**: `REQ-TRK-034.4` in `docs/requirements/req_tracking_cockpit_part1.md:1874`, targeting `TrackerService.java` and `TrackerServiceFinalizationTest.kt`.
2. **Historical Origin & Commit Trace**: `ATT-4098` (commit `63618b5d`), subsequently extended by `ATT-4099` and `ATT-7746` (`REQ-TRK-076`).
3. **Root Reason for Existing Formulation**: `REQ-TRK-034.4` originally required `onDestroy()` to wait on `mFinalizationFuture` (up to 5 s, later 2 s) as a synchronous safety net before `finalizationFuture.complete(null)` was moved onto `runOnMainThread(...)`. With completion posted to the Main Looper, blocking the Main Thread in `onDestroy()` self-deadlocks for the full 2.0 s timeout and then unbinds `BANALService` prematurely if `endWorkout(...)` exceeds 2 s.
4. **Preservation of Core Invariants**: Removing the blocking wait and premature unbind preserves FIFO `endWorkout(...)` completion on `mDbExecutor`, keeps `mBanalService` and `wakeLock` valid throughout `endWorkout(...)` (`REQ-TRK-033.2`), preserves graceful executor shutdown in `onDestroy()` (`REQ-TRK-076.1`), and preserves session-generation suppression on rapid restart (`REQ-TRK-076.3`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC 1 (`TST-TRK-092.1` — Non-Blocking `onDestroy` & Zero Premature Unbind)**: *Given* `handleActionStop()` has started async finalization (`mIsStopping == true`, `endWorkout` blocked on `mDbExecutor`, `mFinalizationFuture` incomplete, queued Main-Thread dispatcher simulating Android Main Looper), *When* external `onDestroy()` is invoked on the caller (Main) thread, *Then* `onDestroy()` returns in `< 100 ms`, `mScheduler`/`mRrFlushExecutor`/`mDbExecutor` are shut down, and `safeUnbindBanalService()`, `performStopForeground()`, and `releaseWakeLock()` have NOT yet been called while `endWorkout` is still executing.
* **AC 2 (`TST-TRK-092.2` — Deferred Idempotent Post-`endWorkout` Cleanup on Destroyed Service)**: *Given* `onDestroy()` has already returned in AC 1 and `endWorkout` subsequently completes on `mDbExecutor`, *When* the queued Main-Thread cleanup Runnable drains (even with `performStopForeground()` and `performStopSelf()` throwing `IllegalStateException("Service destroyed")` and `wakeLock.release()` exercised twice), *Then* `safeUnbindBanalService()`, `mBanalService = null`, `releaseWakeLock()`, `performStopForeground()`, and `performStopSelf()` execute strictly *after* `endWorkout`, zero exceptions escape, `finalizationFuture.isDone()` becomes `true`, and re-running cleanup is a safe no-op.
* **AC 3 (`TST-TRK-092.3` — Failure Fallback & Zero Regressions)**: *Given* `endWorkout(...)` throws `RuntimeException` after external `onDestroy()`, or `onDestroy()` runs when `mIsStopping == true` and `mFinalizationFuture.isDone() == true`, *When* teardown completes, *Then* resources (`mBanalService`, `wakeLock`, executors) are still released cleanly without exception, `finalizationFuture` completes, and all existing `TrackerService` lifecycle and contract suites pass (`TrackerService.java <= 3,559` LoC).

---

## 2. Test Specification (`TST-TRK-092`) & Localization Parity
* **9-Language Localization Audit**: `N/A - no UI strings` (zero `values*/strings*.xml` modifications).
* **Automated Test Cases (`TrackerServiceFinalizationTest.kt`)**:
  1. `TST-TRK-092.1`: `testOnDestroy_duringActiveActionStopFinalization_returnsNonBlockingWithoutPrematureUnbind_tstTrk092_1` — uses a queued Main-Thread `Runnable` collector (reproducing the Main-Looper self-deadlock precondition), holds `endWorkout` on a `CountDownLatch`, invokes `onDestroy()`, asserts elapsed time `< 100 ms`, executors shut down, and `unbindService`/`performStopForeground`/`wakeLock.release` NOT invoked prior to `endWorkout` release; also covers the case where `endWorkout` already finished and posted its Runnable to the Main-Thread queue *before* `onDestroy()` runs (proving zero 2.0-s deadlock!).
  2. `TST-TRK-092.2`: `testPostEndWorkoutCleanupAfterOnDestroy_runsIdempotentlyWithoutExceptionOnDestroyedService_tstTrk092_2` — releases `endWorkout`, drains the queued Main-Thread Runnable while `performStopForeground()` and `performStopSelf()` throw `IllegalStateException` and `unbindService()` throws `IllegalArgumentException`, asserts strict `["endWorkout", "unbindService", "stopForeground", "stopSelf"]` order, `wakeLock.release()` called once (idempotent on repeat), `mBanalService == null`, `finalizationFuture.isDone() == true`, and zero uncaught exceptions.
  3. `TST-TRK-092.3`: `testOnDestroy_duringActionStopWithEndWorkoutFailureOrCompletedFuture_completesCleanupSafely_tstTrk092_3` — verifies (a) `endWorkout` throwing `RuntimeException` after external `onDestroy()` still drains idempotent cleanup and completes `finalizationFuture`, and (b) `onDestroy()` with `mIsStopping == true` and already-completed `mFinalizationFuture` safely cleans up any unreleased `wakeLock`/`BANAL` binding without blocking.

## 3. Traceability Matrix

| Requirement ID | Normative Contract | Test Case ID | Test File & Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-034.4`, `REQ-TRK-033.2`, `REQ-TRK-076.1` | Non-blocking `< 100 ms` `onDestroy()` when `mIsStopping == true`, zero premature unbind/WakeLock release, graceful executor shutdown | `TST-TRK-092.1` | `TrackerServiceFinalizationTest.kt#testOnDestroy_duringActiveActionStopFinalization_returnsNonBlockingWithoutPrematureUnbind_tstTrk092_1` |
| `REQ-TRK-034.3`, `REQ-TRK-034.4`, `REQ-TRK-076.3` | Post-`endWorkout` fault-isolated, idempotent Main-Thread cleanup after prior `onDestroy()` + `finalizationFuture` completion | `TST-TRK-092.2` | `TrackerServiceFinalizationTest.kt#testPostEndWorkoutCleanupAfterOnDestroy_runsIdempotentlyWithoutExceptionOnDestroyedService_tstTrk092_2` |
| `REQ-TRK-034.4`, `REQ-TRK-033.2` | `endWorkout` failure & completed-future fallback safety + `TrackerService.java <= 3,559` LoC & SWE.3 parity | `TST-TRK-092.3` | `TrackerServiceFinalizationTest.kt#testOnDestroy_duringActionStopWithEndWorkoutFailureOrCompletedFuture_completesCleanupSafely_tstTrk092_3`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest` |
