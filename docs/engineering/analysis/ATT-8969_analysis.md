# Stage 1 Analysis: ATT-8969 - Polar Verity Sense Retry Banner Scope Crash & Stop-Time Stall

**Ticket**: [ATT-8969](https://issue-tracker/ATT-8969) (`2 SP`, `severity-major`) | **Parent Epic**: [ATT-5910](https://issue-tracker/ATT-5910) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh` | **Branch**: `feature/ATT-8969` | **Date**: 2026-11-19

## 1. Problem Statement & Motivation
* **Defect 1 (Aftermath Retry Crash & Banner Flicker)**: Tapping *Sync Now* on `PolarPendingRetrySyncSection` in Workout Details caused the banner to disappear, reappear/flicker, and fail without updating the HR graph; `logcat` recorded `androidx.compose.runtime.ForgottenCoroutineScopeException: rememberCoroutineScope left the composition` inside `PolarVeritySenseOfflineController.syncPendingWorkout()`.
* **Defect 2 (Long Stop-Time Stall)**: Stopping workout `2195` while Polar Verity Sense was disconnected stalled `TrackerService.endWorkout()` (`mDbExecutor`) for up to `45 s` (`30 s` for H10) before broadcasting `TRACKING_FINISHED_INTENT` and opening the Edit Workout dialog.
* **Expected**: *Sync Now* runs on a non-composition-bound lifecycle-safe `CoroutineScope` with observable sync state and refreshes the Workout Details HR graph on completion; stopping while a Polar sensor is disconnected fast-fails / bounds connection wait so Edit Workout opens promptly and `polarSyncPending = 1` is persisted for Aftermath retry.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Cause 1 — Composition-Bound Coroutine & Unrefreshed Telemetry (`PolarH10RetrySyncBanner.kt:84-121`)**:
  * `PolarPendingRetrySyncSection` launches `syncPendingPolarWorkout(...)` on `rememberCoroutineScope()` (`L91, 105`) and stores `isSyncingPolar` / `polarSyncError` in local `remember(workoutId, polarExerciseId)` state (`L94-95`). Any recomposition/navigation/overlay transition removing the banner from composition triggers `RememberObserver.onForgotten()`, cancelling the in-flight PFTP sync with `ForgottenCoroutineScopeException` (`CancellationException` subclass) and resetting/flickering banner UI state on re-entry.
  * Additionally, `PolarVeritySenseOfflineController.kt:420, 861, 879` (and `PolarH10OfflineController.kt:602`) catch `Throwable` without rethrowing `CancellationException`.
  * Downstream HR graph blind spot: `applyBackfillAndCommit` (`PolarVeritySenseOfflineController.kt:1192`) calls `WorkoutRepository.refreshWorkout(workoutId)`, which updates summary `WorkoutData`, but `WorkoutSummariesTabbedScreen.kt:272-279` skips `trackOnMapViewModel.loadAftermathData(...)` when `loadedWorkoutId == selectedWorkoutIdForDetails`, and `WorkoutSummariesListFragment.kt:184-220` only calls it on `onMapClick` — leaving `TrackOnMapAftermathViewModel.uiState` (`telemetryPath`, `hrZoneDistribution`) stale after deferred sync.
* **Cause 2 — Unbounded `waitForConnection` on Disconnected Sensor (`TrackerService.java:2495-2508`)**:
  * `TrackerService.endWorkout()` calls `PolarH10OfflineController.stopAndBackfillWorkoutBlocking()` (`L2497`) and `PolarVeritySenseOfflineController.stopAndBackfillWorkoutBlocking()` (`L2504`) synchronously on `mDbExecutor` (`handleActionStop` `L1502`, `onDestroy` `L1736`) before `ExportManager.workoutFinished()` (`L2544`) and `TRACKING_FINISHED_INTENT` (`L2559` -> `TrainingApplication.java:2091` `WorkoutNavigationEvents.triggerEdit(mWorkoutID)`).
  * `PolarVeritySenseOfflineController.stopAndBackfillWorkout` (`L360, 737-743, 849-850`) and `PolarH10OfflineController.stopAndBackfillWorkout` (`L277-286`) call `api.connectToDevice(...)` + `api.waitForConnection(...)` inside `withGattYieldAndPolarApi` with no pre-check of live `DeviceManager` / `PolarOfflineGattCoordinator.isDeviceConnected()` connection state and no short connection timeout — stalling for the full `BACKFILL_TIMEOUT_MS` (`45,000 ms` Verity / `30,000 ms` H10) when the sensor is disconnected.
* **Independent Peer Review & Verification**:
  * Verified all cited paths and line ranges against `sprint/2026-11-19_01_Früh`. Confirmed `TrackerService.java` is `3,556` LoC (at cap — all new logic must reside in Kotlin controllers/helpers). Confirmed `PolarOfflineGattCoordinator.isDeviceConnected()` (`L450-454`), `PolarOnlineStreamingBridge.isReceivingData()` (`L484`), and `MyBTLEDevice.isReceivingData()` (`L915-917`) already expose live connection state without touching `WorkoutRepository.kt` (Lane A) or IMU/location estimators (Lane B).

## 3. Scope Grounding & Non-Goals
* **In-Scope (`2 SP`)**:
  * Move `syncPendingPolarWorkout` execution & per-workout sync/error/completion state into a controller/manager-scoped `CoroutineScope` (`SupervisorJob() + Dispatchers.IO`) observed by `PolarPendingRetrySyncSection` (`rememberCoroutineScope` never owns backfill).
  * Refresh `TrackOnMapAftermathViewModel` telemetry/HR graph when deferred Polar backfill completes (`WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `TrackOnMapAftermathViewModel.kt`) without modifying `WorkoutRepository.kt`.
  * Add live disconnected fast-fail + bounded stop-connection timeout (`<= 5 s`) in `PolarVeritySenseOfflineController.kt` / `PolarH10OfflineController.kt` (preserving `apiOverride` test paths and recording `polarSyncPending = 1` when `needsBackfill == true`).
  * Targeted unit tests for non-composition scope survival, disconnected fast-fail / bounded connection timeout, and `CancellationException` propagation.
* **Out-of-Scope**: `TrackerService.java` LoC growth (`<= 3,556` LoC), `WorkoutRepository.kt` (Lane A), `ClimbsDatabaseManager` / `LiveClimbsRepository` / `LegacyImportEngine` (Lane A), `SmartphoneImuCadenceEstimator` / `SpeedAndLocationDevice_ATTFused` (Lane B), DB schema bumps, or physical Pixel 10 execution.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-109` (`REQ-SET-109.1`, `.3`), `REQ-SET-106` (`REQ-SET-106.1`–`.3`), `REQ-SET-105` (`REQ-SET-105.5`) in `docs/requirements/req_settings_profiles.md:2314-2491`.
* **Historical Origin & Commit Trace**: `5795a8d2` (`ATT-7924`), `34b78941` (`ATT-8690`).
* **Root Reason for Existing Formulation**: Export gating (`stopAndBackfillWorkoutBlocking` before `ExportManager.workoutFinished`) guarantees complete HR/RR data in `.fit`/`.tcx`/Strava exports when the sensor is reachable at stop (`REQ-SET-105.5`, `REQ-SET-109.1`); `rememberCoroutineScope()` was used in `PolarH10RetrySyncBanner.kt` assuming the banner remained mounted for the duration of sync.
* **Preservation of Core Invariants**: Connected sensors still complete stop-time backfill before `ExportManager.workoutFinished()`; disconnected sensors still persist `polarSyncPending = 1` (`verity:` / H10 tag) when `needsBackfill == true`; strict `WorkoutRr.db` exclusion for Verity Sense, post-commit single-entry flash cleanup, and `apiOverride` unit-test behavior remain unchanged.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-SET-109.1`, `REQ-SET-109.3` (and sibling `REQ-SET-106.3`, `REQ-SET-105.2`); no `SYS.2` / `SYS.3` (`SYSREQ-*`) external hardware/BLE protocol interface changes (`REQ-PRO-131.4`).
* **Affected Architecture Elements**: `PolarH10RetrySyncBanner.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`. (`TrackerService.java` unchanged; `WorkoutRepository.kt` untouched per Lane A isolation.)
* **Verification Impact & Test Surface**: `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `TrackerServiceExportGatingTest`, plus new unit tests for `PolarPendingRetrySyncSection` / retry coordinator lifecycle & disconnected stop fast-fail (`N/A - no UI strings added`).
* **Risk Assessment & Invariants**: **MEDIUM** — Mitigated by gating fast-fail strictly on active `DeviceManager` (`apiOverride == null`) + bounded `waitForConnection` timeout, keeping `TrackerService.java <= 3,556` LoC, and preserving lane isolation.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: `TrackerService.java <= 3,556` LoC; zero `WorkoutRr.db` writes from Verity Sense; post-commit-only flash deletion; zero `WorkoutSummaries.db` schema bump; Lane A/B files untouched.
* **Risk Rating**: **MEDIUM** — Concurrency/BLE stop-path timing and Compose state hoisting across two aftermath hosts, well-bounded by unit tests and `apiOverride` seams.
