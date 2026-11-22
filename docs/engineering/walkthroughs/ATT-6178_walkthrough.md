# Stage 5 Walkthrough: ATT-6178 - TrackerService.onDestroy Non-Blocking Finalization & Idempotent Cleanup

**Ticket**: [ATT-6178](https://issue-tracker/ATT-6178) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Blocks `ATT-6034`*)  
**Target Release**: `V5.0.0` (`Rule 8.3`, FixVersion assigned on PO acceptance) | **Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`1 SP`)  
**Branch**: `feature/ATT-6178` (preserved intact for Stage 6 Cold Review per `Rule 3.3`) | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22  
**Requirements / Tests**: `REQ-TRK-034` (`REQ-TRK-034.4` `[Verified]`; upholds `REQ-TRK-033.2`, `REQ-TRK-034.3`, `REQ-TRK-076.1`/`.3`) / `TST-TRK-092` (`[Verified]`)  
**Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-6178/`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/test_evidence/tickets/ATT-6178/evidence_summary.json)

---

## 1. Summary of Changes & Gate 3 Guardrails Verified
1. **Non-Blocking `onDestroy()` in `mIsStopping` Branch (`TrackerService.java`, `3,550 / 3,559` LoC)**:
   * Removed the Main-Thread blocking `future.get(2, TimeUnit.SECONDS)` call and unconditional premature `safeUnbindBanalService()` / `performStopForeground()` from `onDestroy()`'s `mIsStopping` branch.
   * Preserved immediate `mScheduler.shutdownNow()`, `mRrFlushExecutor.shutdown()`, and `mDbExecutor.shutdown()` (`REQ-TRK-076.1`), plus a non-blocking defensive `if (future == null || future.isDone())` guard releasing `BANALService` and `wakeLock` only when no async finalization callback is in flight.
2. **Fault-Isolated, Idempotent Post-`endWorkout` Main-Thread Teardown**:
   * Added `performIdempotentMainThreadTeardown(boolean stopSelf)` wired into both `handleActionStop()` (`stopSelf = true`, gated by `mSessionGeneration`) and `onDestroy()`'s non-stopping fallback (`stopSelf = false`, preserving `mRrFlushExecutor.shutdown()` in `finally` per Gate 3 Guardrail #3).
   * Hardened `releaseWakeLock()` as `protected synchronized` with `try/catch (Throwable)` and `finally { wakeLock = null; }`.
   * Isolated `mBanalService.removeRrBeatListener(mRrBeatListener)` in its own inner `try/catch (Throwable)` inside `safeUnbindBanalService()` so binder errors cannot skip `unbindService(mBanalConnection)` (Gate 3 Guardrail #2).
3. **TOCTOU Race Elimination in `flushLiveRrQueueSync()` / `flushLiveRrQueueAsync()` (Gate 3 Guardrail #1)**:
   * Extracted `submitOrInsertRrBatch(...)` catching `RejectedExecutionException` on concurrent `mRrFlushExecutor.shutdown()` and falling back cleanly to inline `insertIntervalsBlocking(batch)` without acquiring `mRrFlushLock` in `onDestroy()`.
4. **SWE.3 Living Detailed Design & Living Docs**:
   * Updated `docs/architecture/detailed_design/tracker_service.md` (`v1.0.8`, exact `3,550 LOC`, transitions, threading contract, traceability).
   * Promoted `REQ-TRK-034` (`req_tracking_cockpit_part1.md` `v1.0.24`) and `TST-TRK-092` (`tst_tracking_cockpit_part2.md` `v1.0.1`, preserving `tst_tracking_cockpit.md` at `3,995/4,000` lines) to `[Verified]`.

## 2. Verification Evidence
* **Pre-Fix Reproduction Proof (TDD Red $\rightarrow$ Green, Gate 3 Guardrail #4)**:
  * Before modifying `TrackerService.java`, `TST-TRK-092.1` failed RED reproducing the exact 2.0-s deadlock: `AssertionError: Race A: onDestroy() must return non-blocking (< 100 ms), took 2001 ms`.
  * After the fix, both Race A (blocked `endWorkout`) and Race B (queued Main-Looper self-deadlock) passed in `< 100 ms`.
* **Targeted Unit & Contract Suites (`--rerun`, `163/163` PASS, `0` fail, `0` err, `0` skip, `0` new warnings)**:
  * `TrackerServiceFinalizationTest` (`33/33` incl. `TST-TRK-092.1`, `TST-TRK-092.2`, `TST-TRK-092.3`) + 17 other `TrackerService*` suites (`AsyncInit`, `CrashRecoveryCharacterisation`, `DecoupledSummary`, `DeviceNameQuoting`, `ErtsFinalization`, `ExportGating`, `FGSType`, `HeapWatchdog`, `Lap`, `MultiSensorSummary`, `PragmaSchema`, `Resilience`, `Resume`, `RrFlush`, `RrOpticalIsolation`, `StateMachineCharacterisation`, `WorkoutStart`).
  * `TrainingApplicationTrackingTest` (`15/15`), `ArchitectureConformanceContractTest` (`11/11`, `TrackerService.java 3,550 <= 3,559` LoC), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`), `JavaCoreMigrationStrategyContractTest` (`5/5`), `MeasuredNfrBenchmarkTest` (`11/11`).
* **Live `emulator-5554` Smoke Verification (`Rule 7.6`, `Rule 8.5`)**:
  * Locked `agent_emulator.py exec --ensure` `:app:installDebug` + `MainActivityWithNavigation` launch on `Pixel_8_Android_15(AVD) - 15`: `Status: ok` (`pid=29183`), `logcat -b crash -d` empty (`0` crashes).
  * Visual inspection via `read_file` of `docs/engineering/test_evidence/tickets/ATT-6178/screenshots/att6178_emulator_smoke.png`: clean Start Tracking cockpit screen, zero crashes or rendering defects.
* **Governance & Automated Gate**:
  * `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-11-22_01_Früh`: exit `0` (all 29 shards `<= 4,000` lines, 100% parity, Chesterton's Fence verified).
  * Machine evidence bundle generated via `tools/auto_gate.py --ticket ATT-6178 --tier tier-a --write-bundle`.
  * Localization Parity: `N/A - no UI strings`.
