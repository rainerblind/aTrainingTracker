# Living Test Specification: Tracking & Live Sensor Cockpit (Part 2 of 2)

**Document ID**: `DOC-TST-TRK-P2`  
**Version**: 1.0.2  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `tracking_cockpit_part2`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle (continuation shard keeping `tst_tracking_cockpit.md` within the 4,000-line cap, `REQ-PRO-093.3`).  
**Total Tests**: 2

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

### TST-CON-055: **Radar Quick-Switch Modal Callback Forwarding & StVZO-Safe Deferred BLE GATT Registration Verification (`ATT-8123`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-CON-055` (`REQ-CON-055.1`–`REQ-CON-055.4`)
- **Component**: `RadarLightQuickSwitchSheet.kt`, `BTLERadarDevice.java`, `RadarLightQuickSwitchSheetTest.kt`, `BTLERadarDeviceTest.kt`, `RadarLightModeGridTest.kt`
- **Jira**: `ATT-8123` | **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Implemented Unit Tests**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarLightQuickSwitchSheetTest.kt`: `testModalWrapper_clickingAllSnoozePresets_triggersOnSnoozeSelectedBeforeDismiss_TST_CON_055_1`, `testModalWrapper_activeSnooze_clickingResume_triggersOnResumeAudioBeforeDismiss_TST_CON_055_1` (`11/11` PASS)
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDeviceTest.kt`: `testConfigureServices_legacyStvzoModel_defersRegistrationAndClampsActiveDayFlashToSolid_TST_CON_055_2`, `testConfigureServices_rearVueStvzoModel_defersRegistrationSetsHardwareBrakeAndClampsToSolid_TST_CON_055_2`, `testConfigureServices_nonStvzoModel_registersWithStvzoFalseAndPreservesDayFlash_TST_CON_055_2`, `testConfigureServices_modelNumberReadFailureNull_fallsBackImmediatelyOnce_TST_CON_055_3`, `testConfigureServices_timeoutFallbackAndLateStvzoSelfHeal_plusIdempotentLateTimeout_TST_CON_055_3`, `testDisconnectAndShutdown_clearsStvzoStateAndDropsLateModelReadOrTimeout_TST_CON_055_4`, `testCompleteLightControlRegistration_concurrentDisconnectDuringClampedSetLightMode_doesNotResurrectSolid_TST_CON_055_4` (`64/64` PASS)
  - `RadarLightModeGridTest` (`3/3`), `RadarLightControllerTest` (`39/39`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`), `JavaCoreMigrationStrategyContractTest` (`5/5`), `SystemLiveTrackingRequirementsContractTest` (`4/4`)
- **Test Description**:
  1. *Modal Wrapper Audio-Snooze & Resume Ordering (`TST-CON-055.1`, `RadarLightQuickSwitchSheetTest.kt`)*:
     • Render `RadarLightQuickSwitchSheet(...)` directly via Robolectric Compose; click `SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE`, and `RadarSnooze_Resume`; assert exact callback fires strictly before `onDismiss()` and compile-time required action parameters prevent omitted callbacks.
  2. *Deferred StVZO GATT Registration & Initial Mode Clamping (`TST-CON-055.2`, `BTLERadarDeviceTest.kt`)*:
     • With active `DAY_FLASH` and `UUID_CHARACTERISTIC_MODEL_NUMBER` exposed: assert `configureServices` neither registers with `RadarLightController` nor enqueues a light write before model-number completion; deliver `"Garmin Varia RTL516"` (legacy) and `"010-03180-10"` (RearVue 820) and assert single registration with `isStvzo == true` (plus `hasHardwareBrakeSensor == true` on RearVue) and clamped `SOLID` command (never `DAY_FLASH`).
  3. *Absent-DIS, Read-Error & Timeout Fallbacks (`TST-CON-055.3`, `BTLERadarDeviceTest.kt`)*:
     • Verify absent DIS/Model-Number registers immediately preserving FIFO CCCD-before-write order; verify `null`/empty read and `MODEL_NUMBER_READ_TIMEOUT_MS` (`3000L`) timeout each trigger exactly one idempotent `(false, false)` fallback registration + active mode dispatch, and late StVZO read self-heals `DAY_FLASH` to `SOLID`.
  4. *Disconnect/Shutdown Race Isolation & Architecture Contracts (`TST-CON-055.4`, `BTLERadarDeviceTest.kt`, `RadarLightModeGridTest.kt`, `RadarLightControllerTest.kt`, `ArchitectureConformanceContractTest.kt`, `JavaCoreMigrationStrategyContractTest.kt`)*:
     • Verify `disconnectFromGatt()` / `shutDown()` clears `mIsStvzo`/`mModelNumber`, drops late model-number updates or fallback timers without re-registering, and prevents clamped post-disconnect `setLightMode()` races from resurrecting `SOLID` in `RadarLightController`/`RadarStateRepository`; verify `BTLERadarDevice.java <= 1,500` LoC.
- **Verification Evidence**: Verified (`ATT-8123` Bounce 1, Gate 3 `ATT-9606` PASSED; `141/141` targeted unit/Compose/contract tests PASS across 8 suites via `./gradlew :app:testDebugUnitTest --rerun`, `0` fail, `0` err, `0` skip, `0` new compiler warnings; `BTLERadarDevice.java` `1,467/1,500` LoC; Light/Dark Compose preview + live `emulator-5554` boot smoke `CRASH_LOGCAT_CLEAN` visually inspected per `Rule 8.5`; `auto_gate.py` SHA-256 evidence bundle at `docs/engineering/test_evidence/tickets/ATT-8123/manifest.json`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-22 | Lane 1 Author (`ATT-6178`, `--as agent1`) | `ATT-6178` | Created Part 2 shard (`tst_tracking_cockpit_part2.md`, keeping `tst_tracking_cockpit.md` at `3,995/4,000` lines) and added `TST-TRK-092` `[Planned]` (`TST-TRK-092.1`–`.3`) for non-blocking `TrackerService.onDestroy()` and idempotent post-`endWorkout` cleanup (`N/A - no UI strings`). |
| `1.0.1` | 2026-11-22 | Lane 1 Author (`ATT-6178`, `--as agent1`) | `ATT-6178` | Promoted `TST-TRK-092` to `[Verified]` (`TrackerServiceFinalizationTest` `33/33` PASS incl. `TST-TRK-092.1`–`.3` after pre-fix `2,001 ms` RED proof, `163/163` PASS across 24 unit/contract suites, clean `emulator-5554` smoke, `docs/engineering/test_evidence/tickets/ATT-6178/`). |
| `1.0.2` | 2026-11-22 | Lane 3 Author (`ATT-8123`, `--as agent1`) | `ATT-8123` | Added & verified `TST-CON-055` (`ATT-8123` Bounce 1 concurrency hardening: `141/141` PASS across 8 suites incl. `RadarLightQuickSwitchSheetTest` `11/11`, `BTLERadarDeviceTest` `64/64`, `RadarLightControllerTest` `39/39`, `ArchitectureConformanceContractTest` `11/11`; Compose Light/Dark preview + live `emulator-5554` smoke `0` crashes). |
