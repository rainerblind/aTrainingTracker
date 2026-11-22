# Stage 2: Requirement & Test Specification - ATT-9498

**Ticket**: [ATT-9498](https://atrainingtracker.atlassian.net/browse/ATT-9498) | **Parent Epic**: [ATT-9494](https://atrainingtracker.atlassian.net/browse/ATT-9494) (`Finding M11`)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-22_02_Spät` (Sprint 472) | **Risk Tier**: `tier-a` (`3 SP`, Critical Bug)  
**Reserved IDs**: `REQ-TRK-095` / `TST-TRK-095` (`req_tracking_cockpit_part3.md` `v1.0.6`, `tst_tracking_cockpit_part2.md` `v1.0.9`); `REQ-PRO-138` / `TST-PRO-138` (`req_process_stability_part2.md` `v1.0.63`, `tst_process_stability_part2.md` `v1.0.56`)  
**Branch**: `feature/ATT-9498` | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22

---

## 1. Formal Requirement Specification

### 1.1 `REQ-TRK-095`: Race-Free Foreground Service Startup/Teardown Handshake for `TrackerService` and `WorkoutPostProcessingService` [Planned]
- **System Requirement(s)**: `SYSREQ-TRK-005`, `SYSREQ-TRK-013` (zero `SYS.2`/`SYS.3` changes required)
- **Priority**: Critical | **Type**: Functional | **Verification Method**: Unit, Integration
- **Implementation**: `TrackerService.java`, `TrainingApplication.java`, `WorkoutPostProcessingService.kt`, `WorkoutPostProcessingCoordinator.kt`, `MultiLocaleUiSweepAndroidTest.kt`, `docs/architecture/detailed_design/tracker_service.md`
- **Normative Clauses (`REQ-TRK-095.1`–`.4`)**:
  1. **`TrackerService` Pending-FGS Handshake, Early Promotion & FIFO `mWorkoutID` Resolution (`REQ-TRK-095.1`)**:
     - `TrainingApplication.startTracking()` SHALL call `TrackerService.markForegroundStartRequested()` upon successful `ContextCompat.startForegroundService(this, intent)`. When `TrainingApplication.stopTracking()` runs while `!TrackerService.isRunning()`, it SHALL invoke `TrackerService.requestStopIfForegroundStartPending()` and MUST suppress `stopService(TrackerService)` when `true` (preserving the fallback `stopService()` strictly when no FGS start is pending).
     - `TrackerService.onStartCommand()` SHALL invoke `performStartForeground(...)` *before* submitting `createNewWorkout()` to `mDbExecutor` or scheduling `mScheduler` (preserving the `intent == null && !hasBackgroundLocationPermission()` early abort without `startForeground()`). If a pre-start stop was requested (`consumePendingStopBeforeStart()`), `onStartCommand()` SHALL promote first, mark `mIsStopping = true; mTrackingInterrupted = true`, execute `performIdempotentMainThreadTeardown(true)`, and return `START_NOT_STICKY` without creating an orphan SQLite workout. `handleActionStop()` MUST satisfy any pending unpromoted FGS contract before early `performStopSelf()`.
     - `START_NORMAL` SHALL reset `mWorkoutID = -1` synchronously prior to `mDbExecutor.submit(...)`, and `enqueueAsyncFinalization()`'s FIFO `finalizationTask` SHALL resolve `mWorkoutID` for the matching `stoppingBaseFileName` when `stoppingWorkoutId <= 0` so rapid Start$\rightarrow$Stop always commits `FINISHED = 1`.
  2. **`WorkoutPostProcessingService` Generation-Guarded FGS Lifecycle (`REQ-TRK-095.2`)**:
     - `WorkoutPostProcessingService` SHALL coordinate `pendingFgsGeneration`, `promotedFgsGeneration`, `stopRequestedForGeneration`, and `activeInstance` under `lifecycleLock`. `startForPostProcessing()` SHALL avoid redundant `startForegroundService()` while already promoted/pending (clearing any pending stop request); `stopIfRunning()` MUST NOT invoke `appContext.stopService(...)` while `pendingFgsGeneration > 0L`, instead recording `stopRequestedForGeneration = pendingFgsGeneration` so `onStartCommand()` promotes via `ServiceCompat.startForeground(...)` first and immediately calls `demoteForeground()` + `performStopSelf()`. `ACTION_STOP` arriving with `pendingFgsGeneration > 0L` MUST also promote before demoting and stopping.
  3. **Multi-OS Android 12/15/16 & 9-Locale Sweep Verification (`REQ-TRK-095.3`)**:
     - `MultiLocaleUiSweepAndroidTest` SHALL retain its rapid Start$\rightarrow$Pause$\rightarrow$Stop lifecycle stress sequence, reliably capture `syntheticWorkoutId` before stop for `finally` cleanup, and await post-processing drain cleanly. Live execution of `tools/run_multi_os_emulator_sweep.py` (with `ATT-9510` `/data/local/tmp/att_sweep_dump.xml`) MUST achieve `12/12` `PASS` across Android 12 (`Pixel_5_Android_12`), Android 15 (`Pixel_8_Android_15`), Android 16 (`Pixel_7_Android_36`), and all 9 locales with `0` `ForegroundServiceDidNotStartInTimeException` crashes.
  4. **Monolith & SWE.3 Parity Invariants (`REQ-TRK-095.4`)**:
     - `TrackerService.java` MUST remain `<= 3,220` LoC, `TrainingApplication.java` MUST remain `<= 2,050` LoC, `docs/architecture/detailed_design/tracker_service.md` SHALL document the FGS handshake (`REQ-PRO-114`), and `0` UI strings SHALL be modified (`Localization Parity: N/A`).

### 1.2 `REQ-PRO-138`: Baseline Manifest Surfacing of Open Emulator Sweep Failures [Planned]
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit
- **Implementation**: `tools/baseline_manager.py`
- **Normative Clauses (`REQ-PRO-138.1`–`.2`)**:
  1. **Latest Sweep Verdict Discovery (`REQ-PRO-138.1`)**: `tools/baseline_manager.py` SHALL inspect the lexicographically latest `YYYY-MM-DD_sweep.md` report under `docs/engineering/emulator_sweeps/` (when present) and parse its `**Verdict**:` status (`PASS` / `FAIL` / missing-or-unknown fail-closed).
  2. **Open Release Deviation Surfacing (`REQ-PRO-138.2`)**: Whenever the latest emulator sweep verdict is `FAIL` (or unparseable/missing), `_collect_open_risks_and_deviations()` SHALL append a `**DEVIATION**:` entry naming the sweep file, verdict, and failed/total run counts so `manifest.md` reports `Open Release Deviations >= 1` (`0` added when latest sweep is `PASS` or when `docs/engineering/emulator_sweeps/` has no sweep files).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (`TST-TRK-095.1`)**: *Given* `startTracking()` succeeded and `stopTracking()` fires before `TrackerService.onCreate()`, *When* `TrackerService.onStartCommand(START_NORMAL)` subsequently executes, *Then* `stopService()` was never called prematurely, `performStartForeground()` runs before `performStopForeground()`/`performStopSelf()`, `0` SQLite workout row is created, and `0` `ForegroundServiceDidNotStartInTimeException` occurs; *Given* rapid `START_NORMAL` $\rightarrow$ `ACTION_STOP` before `createNewWorkout()` finishes, *Then* FIFO `endWorkout()` receives the created `workoutId` and commits `FINISHED = 1`.
* **AC 2 (`TST-TRK-095.2`)**: *Given* `WorkoutPostProcessingService.startForPostProcessing()` succeeded and `drainQueue()` finishes before `onStartCommand()`, *When* `stopIfRunning()` runs followed by `onStartCommand()`, *Then* `stopService()` is suppressed while `pendingFgsGeneration > 0L`, `onStartCommand()` calls `promoteForeground()` then `demoteForeground()` + `performStopSelf()`, and a re-enqueued job clears the pending stop cleanly.
* **AC 3 (`TST-TRK-095.3`, `TST-PRO-138.1`)**: *Given* a latest `FAIL` sweep report (`2026-11-21_sweep.md`), *When* `generate_baseline_manifest()` runs, *Then* `Open Release Deviations` is `>= 1` citing the failing report (and `0` once superseded by a newer `PASS` report); *Given* the fixed APK on Android 12, 15, 16 + all 9 locales, *When* `run_multi_os_emulator_sweep.py` executes, *Then* all `12/12` runs `PASS`.

---

## 2. Test Specification (`TST-TRK-095`, `TST-PRO-138`) & Traceability Matrix
* **9-Language Localization Parity**: `N/A - no UI strings`.

| Requirement ID | Test Case ID | Verification Scope & Target Test File(s) |
| :--- | :--- | :--- |
| `REQ-TRK-095.1` | `TST-TRK-095.1` | `TrainingApplicationTrackingTest.kt` (suppress `stopService` when FGS pending; preserve fallback when idle; restart clears pending stop) & `TrackerServiceFinalizationTest.kt` / `TrackerServiceResilienceTest.kt` (early `performStartForeground` before `mDbExecutor.submit`, pre-start deferred stop promotes-then-stops with 0 DB init, `ACTION_STOP` pre-promotion guard, FIFO `mWorkoutID` resolution) |
| `REQ-TRK-095.2` | `TST-TRK-095.2` | `WorkoutPostProcessingServiceTest.kt` & `WorkoutPostProcessingCoordinatorTest.kt` (fast-drain `stopIfRunning` suppresses `stopService` while `pendingFgsGeneration > 0`, `onStartCommand` promotes then self-stops, re-enqueue cancels pending stop, post-promotion `stopIfRunning` calls `stopService`, `ACTION_STOP` pre-promotion guard, `onTimeout` isolation) |
| `REQ-TRK-095.3` | `TST-TRK-095.3` | `MultiLocaleUiSweepAndroidTest.kt` (`:app:compileDebugAndroidTestKotlin`) + live `tools/run_multi_os_emulator_sweep.py` across `Pixel_5_Android_12`, `Pixel_8_Android_15`, `Pixel_7_Android_36` and all 9 locales (`12/12` `PASS`, `Rule 8.5` PNG audit) |
| `REQ-TRK-095.4` | `TST-TRK-095.4` | `ArchitectureConformanceContractTest.kt` (`TrackerService.java <= 3,220`, `TrainingApplication.java <= 2,050`) & `DetailedDesignParityContractTest.kt` (`tracker_service.md` parity) |
| `REQ-PRO-138.1`–`.2` | `TST-PRO-138.1` | `tools/test_baseline_manager.py` (latest `FAIL` sweep surfaces `Open Release Deviations: 1` with `8/12` count; malformed/missing verdict fails closed; newer `PASS` sweep supersedes older `FAIL` yielding `0`; absent directory yields `0`) |
