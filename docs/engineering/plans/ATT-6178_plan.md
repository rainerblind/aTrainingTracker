# Stage 3 Implementation Plan: ATT-6178 - TrackerService.onDestroy Non-Blocking Finalization & Idempotent Cleanup

**Ticket**: [ATT-6178](https://issue-tracker/ATT-6178) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Blocks `ATT-6034`*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`1 SP`)  
**Requirement Mapping**: `REQ-TRK-034` (`REQ-TRK-034.4` amended; `REQ-TRK-033.2`, `REQ-TRK-034.3`, `REQ-TRK-076.1`/`.3`) | **Test Spec**: `TST-TRK-092`  
**Branch**: `feature/ATT-6178` | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22

---

## 1. Architectural & Concurrency Design (SWE.2 / SWE.3)
* **Layer & Threading Contract (`TrackerService.java`, `3,537 / 3,559` LoC)**:
  * **Main Looper**: `handleActionStop()` (L1455–1545), `onDestroy()` (L1670–1769), and the deferred `runOnMainThread(...)` teardown callback.
  * **`mDbExecutor` (single-thread FIFO)**: executes queued 1-Hz sample writes followed by `endWorkout(...)` inside `finalizationTask`; gracefully shut down via `mDbExecutor.shutdown()` strictly in `onDestroy()` (`REQ-TRK-076.1`) so queued `finalizationTask` finishes normally.
  * **`mRrFlushExecutor` (single-thread)**: shut down in `onDestroy()`; if `endWorkout(...)` subsequently runs `flushLiveRrQueueSync()` (L383–398), it falls back safely to inline `insertIntervalsBlocking(batch)` and awaits any already-queued `mPendingRrFlushFuture`.
* **Exact Code Changes in `TrackerService.java` (est. `+6` LoC $\rightarrow$ `~3,543 / 3,559` LoC)**:
  1. **Harden `releaseWakeLock()` (L1100–1106)**: Change visibility to `protected synchronized void releaseWakeLock()`, wrap `if (wakeLock != null && wakeLock.isHeld()) { wakeLock.release(); }` in `try / catch (Throwable t)` with `Log.w`, and clear `wakeLock = null` in `finally` for thread-safe idempotency.
  2. **Add shared helper `performIdempotentMainThreadTeardown(boolean stopSelf)`**:
     - Calls `safeUnbindBanalService()` (which removes `mRrBeatListener` while `mBanalService` is non-null, catches `Throwable`, and clears `mBanalBound = false` in `finally`), sets `mBanalService = null`, calls `releaseWakeLock()`, calls `performStopForeground()` inside `try/catch (Throwable)`, and if `stopSelf` calls `performStopSelf()` inside `try/catch (Throwable)`.
  3. **Wire helper in `handleActionStop()` (L1513–1531)**: Replace lines L1516–1520 inside `if (mSessionGeneration.get() == stoppingGeneration && mIsStopping)` with `performIdempotentMainThreadTeardown(true);`, keeping `finally { finalizationFuture.complete(null); }`.
  4. **Eliminate blocking wait & premature unbind in `onDestroy()` `mIsStopping` branch (L1714–1727)**:
     - Remove `future.get(2, TimeUnit.SECONDS)` (L1716–1721) and unconditional `safeUnbindBanalService()` / `performStopForeground()` (L1724–1725).
     - Execute `mRrFlushExecutor.shutdown(); mDbExecutor.shutdown();` immediately.
     - Check `CompletableFuture<Void> future = mFinalizationFuture; if (future == null || future.isDone()) { safeUnbindBanalService(); mBanalService = null; releaseWakeLock(); }` so already-finished/unsubmitted stops still leave zero leaked bindings/WakeLocks without blocking or prematurely unbinding an active `endWorkout`.
  5. **Wire helper in `onDestroy()` non-stopping fallback (L1747–1757)**: Call `performIdempotentMainThreadTeardown(false);` inside `runOnMainThread(...)` before `finalizationFuture.complete(null)` (preserving `mBanalService == null` and `releaseWakeLock()` before future completion, plus crash-proof `performStopForeground()`).
* **SWE.2 / SWE.3 & UI Consistency**:
  * **SWE.3 (`REQ-PRO-114`)**: Update `docs/architecture/detailed_design/tracker_service.md` (§3 Transitions, §4.2 Threading Contract, §5 Verification Traceability, LOC header, version `1.0.8`, Change History) and verify via `DetailedDesignParityContractTest`.
  * **SWE.2 (`Rule 8.8`)**: `docs/architecture.md` requires zero changes (no new subsystem/FGS/DB/prefix).
  * **UI Consistency**: `N/A - no UI or localization changes`.

## 2. Atomic Implementation & Verification Steps

| Step | Action & Target Files | Mapped Req / Test | Targeted Verification Command |
| :--- | :--- | :--- | :--- |
| **0** | **Code Lock Verification (`Rule 4.1`)**: Confirm Gate 3 `PASS` via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`exit 0`) before touching `app/src/`. | `Rule 4.1` | `python3 tools/jira_util.py check-gate <KEY>` |
| **1** | **Reproduction Test First (`ATT-6178` AC1)**: Add `TST-TRK-092.1` to `TrackerServiceFinalizationTest.kt` using a queued Main-Thread dispatcher (simulating Android Main Looper) so `onDestroy()` during active `handleActionStop()` demonstrably reproduces the 2.0-s deadlock & premature unbind before the fix. | `REQ-TRK-034.4`, `TST-TRK-092.1` | `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest"` (expect red before Step 2) |
| **2** | **Production Fix in `TrackerService.java`**: Implement `releaseWakeLock()` hardening, `performIdempotentMainThreadTeardown(boolean)`, `handleActionStop()` L1513–1531 wiring, non-blocking `onDestroy()` `mIsStopping` branch (L1714–1727), and `onDestroy()` fallback L1747–1757 wiring; update `onDestroy()` Javadoc. | `REQ-TRK-034.4`, `REQ-TRK-033.2`, `REQ-TRK-076.1`/`.3` | `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest"` (`TST-TRK-092.1` turns green) |
| **3** | **Idempotency & Error-Fallback Tests**: Add `TST-TRK-092.2` (post-`endWorkout` cleanup on destroyed service with throwing `stopForeground`/`stopSelf`/`unbindService` + repeated WakeLock release) and `TST-TRK-092.3` (`endWorkout` exception fallback & completed-future `onDestroy`) to `TrackerServiceFinalizationTest.kt`. | `TST-TRK-092.2`, `TST-TRK-092.3` | `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest"` |
| **4** | **SWE.3 Detailed Design & Living Docs Sync (`REQ-PRO-114`)**: Update `docs/architecture/detailed_design/tracker_service.md` (`v1.0.8`), promote `REQ-TRK-034` & `TST-TRK-092` to `[Implemented]` (and `[Verified]` in Stage 5), bump versions + `--sync-indexes`. | `REQ-PRO-114`, `REQ-TRK-034`, `TST-TRK-092` | `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-22_01_Früh` |
| **5** | **Tier 1 Regression & Contract Verification (`Rule 7.5`)**: Run targeted `TrackerService` suites, `ArchitectureConformanceContractTest` (`TrackerService.java <= 3,559` LoC), `DetailedDesignParityContractTest`, `:app:assembleDebug`, and `tools/auto_gate.py --ticket ATT-6178 --tier tier-a --write-bundle`. | `TST-TRK-092`, `TST-TRK-025`, `TST-TRK-026`, `TST-TRK-076`, `TST-PRO-114` | `./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.tracker.TrackerService*" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest"` |

## 3. System Invariants & Rollback Safety
* **Non-Negotiable Invariants**: `onDestroy()` returns `< 100 ms` on all paths; `BANALService` and `wakeLock` stay valid throughout `endWorkout(...)` (`REQ-TRK-033.2`, `REQ-TRK-034.3`); `mScheduler.shutdownNow()`, `mRrFlushExecutor.shutdown()`, and `mDbExecutor.shutdown()` always run in `onDestroy()` (`REQ-TRK-076.1`); rapid stop-to-start `mSessionGeneration` guard stays intact (`REQ-TRK-076.3`); `TrackerService.java <= 3,559` LoC; zero new Kotlin/Java warnings; zero `values*/strings*.xml` edits.
* **Rollback Safety**: Isolated single-class concurrency change on `feature/ATT-6178` with zero schema, persistence, or public API changes; trivially revertible cleanly via a single commit.
