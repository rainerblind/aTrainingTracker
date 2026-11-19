# Stage 3: Implementation Plan - ATT-8969: Polar Retry Sync Lifecycle Safety & Disconnected Stop Fast-Fail

**Ticket**: `ATT-8969` (`2 SP`, `severity-major`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-19_01_Früh`  
**Requirement Mapping**: `REQ-SET-119` (`REQ-SET-119.1`..`.3`) | **Test Mapping**: `TST-SET-121` (`TST-SET-121.1`..`.4`)  
**Branch**: `feature/ATT-8969` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-19

---

## 1. Problem Description & Background
`PolarPendingRetrySyncSection` (`PolarH10RetrySyncBanner.kt:91,105`) launches `syncPendingPolarWorkout()` on Compose's `rememberCoroutineScope()` with local `remember` state, throwing `ForgottenCoroutineScopeException` and flickering when leaving composition, while `catch (t: Throwable)` swallows `CancellationException` and `TrackOnMapAftermathViewModel` retains stale HR telemetry post-sync. At workout stop, `TrackerService.endWorkout()` blocks `mDbExecutor` inside `api.waitForConnection(...)` for `30–45 s` when the Polar sensor is disconnected before `TRACKING_FINISHED_INTENT` opens Edit Workout.

## 2. Traceability & Requirements Mapping
| Requirement | Architectural Step(s) | Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-SET-119.1` | Steps 1, 2, 4 | `TST-SET-121.1`, `.2` | `PolarRetrySyncCoordinatorTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest` |
| `REQ-SET-119.2` | Steps 1, 3 | `TST-SET-121.3` | `PolarRetrySyncCoordinatorTest`, `TrackOnMapAftermathViewModelTest` |
| `REQ-SET-119.3` | Steps 4, 5 | `TST-SET-121.4` | `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest` |

## 3. System Invariants & Preserved Behavior
1. **Monolith & Lane Isolation Invariants**: `TrackerService.java` remains untouched (`<= 3,556` LoC); `WorkoutRepository.kt` / Lane A & Lane B files untouched.
2. **Offline & Export Invariants**: Connected sensors still complete stop-time backfill before `ExportManager.workoutFinished()`; Verity Sense never writes `WorkoutRr.db` or invokes `HrvPostWorkoutRecomputer`; flash deletion runs strictly post-SQLite-commit; `apiOverride` test seams preserved.
3. **UI Consistency (`docs/design_guidelines.md`)**: `PolarH10RetrySyncBanner` preserves existing M3 layout (filled primary *Sync Now* `Button`, `OutlinedButton` *Dismiss*, `maxLines = 1` + `TextOverflow.Ellipsis` for German 360dp fit, zero emojis, zero new string keys).

## 4. Proposed Architectural Changes (SWE.2)
* **`PolarRetrySyncCoordinator.kt`** (`banalservice/.../polar/`): Lifecycle-safe singleton owning `CoroutineScope(Dispatchers.IO + SupervisorJob())`, per-`workoutId` deduplication, `StateFlow<Map<Long, RetrySyncState>>` (`isSyncing`, `errorMessage`), and `SharedFlow<Long>` completion events (rethrows `CancellationException`; exposes `@VisibleForTesting` `dispatcher` / `syncExecutorOverride` / `resetForTesting()`).
* **`PolarH10RetrySyncBanner.kt`** (`ui/aftermath/`): `PolarPendingRetrySyncSection` observes `PolarRetrySyncCoordinator.stateFlow` via `collectAsState()` and delegates *Sync Now* to `PolarRetrySyncCoordinator.startSync(...)` (removing `rememberCoroutineScope()` from backfill execution).
* **Aftermath HR Refresh** (`WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `TrackOnMapAftermathViewModel.kt`): Collect `PolarRetrySyncCoordinator.completedWorkoutEvents` for the active `workoutId` and call `trackOnMapViewModel.loadAftermathData(selectedWorkout)` (reloading `telemetryPath` and `hrZoneDistribution` without touching `WorkoutRepository.kt`).
* **Disconnected Fast-Fail & Bounded Connection Wait** (`PolarOfflineGattCoordinator.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`): Add `STOP_CONNECTION_TIMEOUT_MS = 5_000L` and `isTargetConfirmedDisconnected(polarId, macAddress)` (checks `DeviceManager` when `apiOverride == null`); fast-fail stop backfill immediately when confirmed disconnected (persisting `setPolarSyncPending` if `needsBackfill`), bound `connectToDevice` + `waitForConnection` by `STOP_CONNECTION_TIMEOUT_MS`, and rethrow `CancellationException` in all suspend `catch (t: Throwable)` blocks.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-SET-119.1`, `.2`) — Create `PolarRetrySyncCoordinator.kt`**: Implement non-composition `SupervisorJob() + Dispatchers.IO` scope, per-`workoutId` deduplication, `StateFlow` state map, and `completedWorkoutEvents` `SharedFlow` with test seams.
2. **Step 2 (`REQ-SET-119.1`) — Refactor `PolarH10RetrySyncBanner.kt`**: Replace `rememberCoroutineScope()` launch and local `isSyncingPolar`/`polarSyncError` state in `PolarPendingRetrySyncSection` with `PolarRetrySyncCoordinator` state collection and `startSync(...)` dispatch (live hosts `WorkoutDetailTabbedContent.kt` and `TrackOnMapScreen.kt` already mount `PolarPendingRetrySyncSection`).
3. **Step 3 (`REQ-SET-119.2`) — Wire HR Telemetry Refresh**: Collect `PolarRetrySyncCoordinator.completedWorkoutEvents` in `WorkoutSummariesTabbedScreen.kt` and `WorkoutSummariesListFragment.kt` (invoking `trackOnMapViewModel.loadAftermathData(...)` for matching active workout; ensure `TrackOnMapAftermathViewModel.loadAftermathData` cleanly reloads telemetry without collapsing tabs).
4. **Step 4 (`REQ-SET-119.1`, `.3`) — Cancellation Hygiene, Fast-Fail & 5s Connection Bound**: Add `STOP_CONNECTION_TIMEOUT_MS = 5_000L` + `isTargetConfirmedDisconnected(...)` in `PolarOfflineGattCoordinator.kt`; update `PolarVeritySenseOfflineController.kt` and `PolarH10OfflineController.kt` to fast-fail when confirmed disconnected (`apiOverride == null`), bound `waitForConnection` at `5_000L`, and rethrow `CancellationException` in suspend `catch (t: Throwable)` blocks.
5. **Step 5 (`TST-SET-121.1`..`.4`, SWE.2/SWE.3 Sync) — Tests & Docs**: Add unit tests in `PolarRetrySyncCoordinatorTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`, `PolarH10OfflineControllerTest.kt`, and `TrackOnMapAftermathViewModelTest.kt`; update `docs/architecture.md` §3.4 (`PolarRetrySyncCoordinator` + disconnected stop fast-fail); verify `TrackerService.java <= 3,556` LoC, targeted Gradle tests, and `verify_requirement_governance.py`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier 1 unit tests (`PolarRetrySyncCoordinatorTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `TrackOnMapAftermathViewModelTest`, `TrackerServiceExportGatingTest`) + `verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-8969` prior to Stage 5/6 squash merge.
