# Living Test Specification: Tracking & Live Sensor Cockpit (Part 2 of 2)

**Document ID**: `DOC-TST-TRK-P2`  
**Version**: 1.0.1  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `tracking_cockpit_part2`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Tests**: 1

---

### TST-TRK-092: **Non-Blocking TrackerService.onDestroy() During Active Finalization & Idempotent Post-endWorkout Cleanup Regression Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-TRK-034`
- **Component**: `TrackerService.java`, `TrackerServiceFinalizationTest.kt`
- **Jira**: `ATT-6178`
- **Test Description**:
  1. *Non-Blocking External `onDestroy()` During In-Flight `ACTION_STOP` Finalization (`TST-TRK-092.1`, `TrackerServiceFinalizationTest.kt#testOnDestroy_duringActiveActionStopFinalization_returnsNonBlockingWithoutPrematureUnbind_tstTrk092_1`)*:
  • Configure `TestableTrackerService` with a queued Main-Thread `Runnable` dispatcher (simulating Android's Main Looper where posted cleanup cannot run while the caller thread is blocked) and hold `endWorkout(...)` on a `CountDownLatch`.
  • Invoke `handleActionStop()` (`mIsStopping == true`, `mFinalizationFuture` incomplete), then invoke `onDestroy()` on the caller thread.
  • Verify `onDestroy()` returns in `< 100 ms` (zero 2.0-s deadlock), `mScheduler`/`mRrFlushExecutor`/`mDbExecutor` are shut down (`REQ-TRK-076.1`), and `safeUnbindBanalService()`, `performStopForeground()`, and `releaseWakeLock()` have NOT yet been called while `endWorkout(...)` is still active (`REQ-TRK-033.2`, `REQ-TRK-034.3`).
  • Also verify the race where `endWorkout(...)` finishes and posts its cleanup Runnable to the Main-Thread queue *before* `onDestroy()` runs: `onDestroy()` still returns `< 100 ms` without deadlocking on `future.get(...)`.
  2. *Deferred Idempotent Fault-Isolated Cleanup After Prior `onDestroy()` (`TST-TRK-092.2`, `TrackerServiceFinalizationTest.kt#testPostEndWorkoutCleanupAfterOnDestroy_runsIdempotentlyWithoutExceptionOnDestroyedService_tstTrk092_2`)*:
  • Release `endWorkout(...)` after `onDestroy()` has returned, then drain the queued Main-Thread cleanup Runnable while `performStopForeground()` / `performStopSelf()` throw `IllegalStateException` and `unbindService()` throws `IllegalArgumentException` (simulating an already-destroyed `Service`).
  • Verify strict `endWorkout` $\rightarrow$ `unbindService` $\rightarrow$ `performStopForeground` $\rightarrow$ `performStopSelf` order, `mBanalService == null`, `wakeLock.release()` invoked once (idempotent on repeated cleanup), `finalizationFuture.isDone() == true`, and zero uncaught exceptions.
  3. *`endWorkout` Failure Fallback, Completed-Future Safety & Contract Parity (`TST-TRK-092.3`, `TrackerServiceFinalizationTest.kt#testOnDestroy_duringActionStopWithEndWorkoutFailureOrCompletedFuture_completesCleanupSafely_tstTrk092_3`)*:
  • Verify (a) an exception thrown inside `endWorkout(...)` after external `onDestroy()` still runs fault-isolated cleanup and completes `finalizationFuture`, and (b) `onDestroy()` when `mIsStopping == true` and `mFinalizationFuture.isDone() == true` safely releases any remaining `wakeLock`/`BANAL` binding without blocking.
  • Verify `TrackerService.java <= 3,559` LoC (`ArchitectureConformanceContractTest`) and SWE.3 living design parity (`DetailedDesignParityContractTest`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-22 | Lane 1 Author (`ATT-6178`, `--as agent1`) | `ATT-6178` | Created Part 2 shard (`tst_tracking_cockpit_part2.md`, keeping `tst_tracking_cockpit.md` at `3,995/4,000` lines) and added `TST-TRK-092` `[Planned]` (`TST-TRK-092.1`–`.3`) for non-blocking `TrackerService.onDestroy()` and idempotent post-`endWorkout` cleanup (`N/A - no UI strings`). |
| `1.0.1` | 2026-11-22 | Lane 1 Author (`ATT-6178`, `--as agent1`) | `ATT-6178` | Promoted `TST-TRK-092` to `[Verified]` (`TrackerServiceFinalizationTest` `33/33` PASS incl. `TST-TRK-092.1`–`.3` after pre-fix `2,001 ms` RED proof, `163/163` PASS across 24 unit/contract suites, clean `emulator-5554` smoke, `docs/engineering/test_evidence/tickets/ATT-6178/`). |
