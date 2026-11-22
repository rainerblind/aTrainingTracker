# Stage 1 Analysis: ATT-6178 - TrackerService.onDestroy Non-Blocking Finalization & Idempotent Cleanup

**Ticket**: [ATT-6178](https://issue-tracker/ATT-6178) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Blocks `ATT-6034`*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`1 SP`)  
**Branch**: `feature/ATT-6178` (from `sprint/2026-11-22_01_Früh`) | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22

---

## 1. Problem Statement & Root Cause Analysis
In `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java` (`3,537` LoC), `handleActionStop()` (L1455–1545) sets `mIsStopping = true` (L1465), creates `mFinalizationFuture` (L1499–1500), and enqueues `finalizationTask` onto `mDbExecutor` (L1502–1536). In `finalizationTask`'s `finally` block (L1509–1532), `endWorkout(...)` completes on `mDbExecutor` and posts teardown + `finalizationFuture.complete(null)` (L1529) to the **Main Looper** via `runOnMainThread(...)` (L1513, L1551–1563).

When `onDestroy()` (L1670–1769) is invoked on the Main Thread while `mIsStopping == true` (L1714–1727):
1. **2.0-Second Main-Thread Self-Deadlock (L1715–1721)**: `onDestroy()` blocks the Main Looper in `future.get(2, TimeUnit.SECONDS)` (L1718). Because `finalizationFuture.complete(null)` is queued on the *same* Main Looper (L1513–1529), the posted Runnable cannot execute until `future.get(...)` times out after the full **2,000 ms**, even if `endWorkout(...)` already finished in milliseconds.
2. **Premature Unbind & Foreground Drop (`REQ-TRK-033.2` / `REQ-TRK-034.3` Gap, L1724–1725)**: Immediately after the timeout, `onDestroy()` invokes `safeUnbindBanalService()` (L1724) and `performStopForeground()` (L1725) even if `endWorkout(...)` is still running on `mDbExecutor` (`> 2 s`).

## 2. Independent Peer Review & Verification (4-Augen-Prinzip)
Verified directly against live `sprint/2026-11-22_01_Früh` source code (confirming Reviewer Rounds 1–3):
* **Trigger Precondition Confirmed**: `TrainingApplication.stopTracking()` (`TrainingApplication.java:2020–2027`) calls `stopService()` only when `!TrackerService.isRunning()` (`sInstance == null`); duplicate `ACTION_STOP` intents are ignored (`TrackerService.java:1456–1459`). Thus the deadlock triggers on **external** `onDestroy()` calls during active finalization (`mIsStopping == true`, `!mFinalizationFuture.isDone()`), e.g., OS/external stop before the posted cleanup Runnable runs.
* **Why Existing Tests Missed It**: `TrackerServiceFinalizationTest.kt:182` overrides `runOnMainThread(action)` to run `action.run()` *inline* on `mDbExecutor`, bypassing Main-Looper queueing. Stage 4 MUST first reproduce the bug with a queued Main-Thread dispatcher and an active `endWorkout` latch.
* **Simplest AC-Compliant Fix Confirmed (Reviewer Round 3)**: Because `finalizationTask`'s already-posted Main-Thread Runnable (L1513–1531) performs `safeUnbindBanalService()`, `mBanalService = null`, `releaseWakeLock()`, `performStopForeground()`, `performStopSelf()`, and `finalizationFuture.complete(null)` strictly *after* `endWorkout`, removing `future.get(2, TimeUnit.SECONDS)` and premature `safeUnbindBanalService()`/`performStopForeground()` from `onDestroy()`'s `mIsStopping` branch (while keeping `mRrFlushExecutor.shutdown()` + `mDbExecutor.shutdown()` per `REQ-TRK-076.1`, plus a non-blocking `if (future == null || future.isDone())` fallback for already-completed/unsubmitted cleanup) satisfies AC1 (`< 100 ms`, no premature unbind) and AC2 without an unnecessary `whenComplete` callback.
* **Additional Hardening Discovered**:
  * `performStopForeground()` (L1422, L1519, L1753) and `performStopSelf()` (L1410, L1520) are currently unguarded against `Throwable` on an already-destroyed `Service`; if `performStopForeground()` throws, `performStopSelf()` is skipped and the uncaught exception crashes the Main Looper.
  * `releaseWakeLock()` (L1100–1106) is neither `synchronized` nor `try/catch`-guarded and does not clear `wakeLock = null` if `!isHeld()` or if `release()` throws.
  * `mRrFlushExecutor.shutdown()` during in-flight `endWorkout` is safe: `flushLiveRrQueueSync()` (L383–398) already falls back to inline `insertIntervalsBlocking(batch)` when `mRrFlushExecutor.isShutdown()` and still awaits any already-queued `mPendingRrFlushFuture`.
  * `REQ-TRK-034.4` (`docs/requirements/req_tracking_cockpit_part1.md:1874`) still mandates the obsolete blocking `future.get` wait and must be amended with Chesterton's Fence archaeology.
  * `TrackerService.java` LoC cap in `ArchitectureConformanceContractTest.kt:118` is `3,559` (current `3,537`; planned `~3,543`).

## 3. Scope Grounding & Non-Goals (`1 SP`)
* **In-Scope**: Fix `onDestroy()` `mIsStopping` branch (L1714–1727), centralize fault-isolated idempotent Main-Thread teardown (`handleActionStop` L1513–1531 & `onDestroy` L1747–1758), harden `releaseWakeLock()` (L1100–1106), add reproduction & idempotency unit tests (`TST-TRK-092`), amend `REQ-TRK-034.4`, and sync `docs/architecture/detailed_design/tracker_service.md` (`REQ-PRO-114`).
* **Out-of-Scope**: `TrackerService` decomposition (`ATT-6034`), `TrainingApplication.stopTracking()`, DB schema, UI/localization strings.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-034.4` in `docs/requirements/req_tracking_cockpit_part1.md:1874`, targeting `TrackerService.java` and `TrackerServiceFinalizationTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4098` (commit `63618b5d`, *Protect async workout finalization with foreground service lifecycle (`ACTION_STOP`)*), later adjusted by `ATT-4099` and `ATT-7746` (`REQ-TRK-076`).
* **Root Reason for Existing Formulation**: Added a bounded `future.get(...)` "safety net" in `onDestroy()` assuming `mFinalizationFuture` was completed directly on `mDbExecutor`. Once `finalizationFuture.complete(null)` was moved inside `runOnMainThread(...)` to keep teardown atomic with `mSessionGeneration` on the Main Looper (`REQ-TRK-076.3`), blocking the Main Looper on `future.get(...)` created a self-deadlock and premature unbind after timeout.
* **Preservation of Core Invariants**: Removing the Main-Thread wait and premature unbind preserves `REQ-TRK-033.2` (non-blocking `onDestroy`, FIFO `endWorkout`, `mBanalService`/WakeLock intact through `endWorkout`), `REQ-TRK-034.3` (deferred teardown), and `REQ-TRK-076.1`/`.3` (graceful executor shutdown strictly in `onDestroy()`, session-generation gating on rapid restart).

## 5. Impact Analysis (SUP.10)
* **Affected Requirements**: Amends `REQ-TRK-034` (`REQ-TRK-034.4` in `req_tracking_cockpit_part1.md`, `[Planned]`); upholds `REQ-TRK-033.2`, `REQ-TRK-034.3`, `REQ-TRK-076.1`/`.3`; adds regression test `TST-TRK-092` in new shard `docs/tests/tst_tracking_cockpit_part2.md` (keeping `tst_tracking_cockpit.md` untouched at `3,995/4,000` lines). `SYS.2`/`SYS.3`: `SYSREQ-TRK-004`/`005` satisfied with zero system-level change. Localization: `N/A - no UI strings`.
* **Affected Architecture Elements**: `TrackerService.java` (kept `<= 3,559` LoC) and SWE.3 living detailed design `docs/architecture/detailed_design/tracker_service.md` (`REQ-PRO-114`). `docs/architecture.md` unchanged (no new subsystem/FGS/DB/prefix).
* **Verification Impact & Test Surface**: `TrackerServiceFinalizationTest.kt` (new `TST-TRK-092.1`–`.3` reproduction, idempotency, and error-fallback tests + all existing tests), `TrackerServiceResilienceTest.kt`, `TrackerServiceStateMachineCharacterisationTest.kt`, `ArchitectureConformanceContractTest.kt`, `DetailedDesignParityContractTest.kt`, and Stage 6 emulator smoke verification.
* **Risk Assessment & Invariants**: **HIGH (`tier-a`)** — core Foreground Service lifecycle & concurrency. Non-negotiable invariants: `< 100 ms` non-blocking `onDestroy()`; zero premature `BANALService` unbind or WakeLock release during `endWorkout`; `mScheduler.shutdownNow()`, `mRrFlushExecutor.shutdown()`, and `mDbExecutor.shutdown()` executed on all `onDestroy()` exits (`REQ-TRK-076.1`); rapid stop-to-start `mSessionGeneration` suppression intact (`REQ-TRK-076.3`); `TrackerService.java <= 3,559` LoC; `0` production edits before Gate 3 `PASS` (`Rule 4.1`).
