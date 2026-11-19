# Stage 2: Requirement & Test Specification - ATT-8969

**Ticket**: [ATT-8969](https://rainerblind.atlassian.net/browse/ATT-8969) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-11-19_01_Früh`  
**Branch**: `feature/ATT-8969` | **Requirement Mapping**: `REQ-SET-119` | **Test Spec ID**: `TST-SET-121` | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-SET-119`)
- **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit, Integration` | Status: `[Verified]`
- **Rationale**: Executing `syncPendingPolarWorkout()` inside Compose's `rememberCoroutineScope()` with local `remember` state causes `ForgottenCoroutineScopeException` and banner flicker when `PolarPendingRetrySyncSection` leaves composition, while `catch (t: Throwable)` swallows `CancellationException` and `TrackOnMapAftermathViewModel` retains stale HR telemetry. Simultaneously, waiting `30–45 s` in `api.waitForConnection(...)` when a Polar sensor is disconnected stalls `TrackerService.endWorkout()` before the Edit Workout dialog opens (`REQ-SET-119` refines `REQ-SET-109.1`/`.3`, `REQ-SET-106.3`, `REQ-SET-105.2`).
- **Requirements**:
  1. **`REQ-SET-119.1` (Non-Composition Retry Scope, State Hoisting & Cancellation Hygiene)**: `PolarPendingRetrySyncSection` MUST NOT launch `syncPendingPolarWorkout(...)` on `rememberCoroutineScope()`. Tapping *Sync Now* SHALL dispatch onto a lifecycle-safe non-composition `CoroutineScope` (`Dispatchers.IO + SupervisorJob`) with per-`workoutId` deduplication and observable `StateFlow` (`isSyncing`, `errorMessage`, completion events) so recomposition/disposal never throws `ForgottenCoroutineScopeException` or flickers. `PolarVeritySenseOfflineController` and `PolarH10OfflineController` `catch (t: Throwable)` blocks MUST rethrow `CancellationException`.
  2. **`REQ-SET-119.2` (Aftermath HR Graph Refresh)**: Upon deferred Polar backfill completion for the active workout, `WorkoutSummariesTabbedScreen` and `WorkoutSummariesListFragment` (`TrackOnMapAftermathViewModel`) SHALL trigger a reload of detail telemetry (`telemetryPath`, `tracks`, `hrZoneDistribution`) without editing `WorkoutRepository.kt` (Lane A isolation), updating the Workout Details HR graph cleanly.
  3. **`REQ-SET-119.3` (Disconnected Stop-Time Fast-Fail & Bounded Connection Wait)**: During `stopAndBackfillWorkout()` in `PolarVeritySenseOfflineController` and `PolarH10OfflineController`, when `apiOverride == null` and `BANALService.getDeviceManager()` confirms the target Polar device is disconnected (`!isReceivingData()` and `!PolarOfflineGattCoordinator.isDeviceConnected(...)`), stop-time backfill SHALL fast-fail without calling `api.waitForConnection(...)`, persisting `setPolarSyncPending(...)` if `needsBackfill == true`, resetting armed state, and releasing `TrackerService.endWorkout()` promptly. Otherwise, `api.connectToDevice(...)` + `api.waitForConnection(...)` SHALL be bounded by `STOP_CONNECTION_TIMEOUT_MS = 5_000L` independent of `BACKFILL_TIMEOUT_MS` (`45 s` / `30 s`), preserving `apiOverride` unit tests and `TrackerService.java <= 3,556` LoC.

### Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `polarSyncPending = true` in Aftermath, *When* the user taps *Sync Now* and `PolarPendingRetrySyncSection` leaves and re-enters composition mid-sync, *Then* `syncPendingPolarWorkout` completes without `ForgottenCoroutineScopeException`, no banner flicker occurs, and `TrackOnMapAftermathViewModel` reloads HR telemetry/zones.
- *Given* a suspend stop/sync block throwing `CancellationException`, *When* caught in `PolarVeritySenseOfflineController` or `PolarH10OfflineController`, *Then* `CancellationException` is rethrown.
- *Given* an armed Polar Verity Sense or H10 confirmed disconnected in `DeviceManager` (`apiOverride == null`) at stop, *When* `stopAndBackfillWorkout()` runs, *Then* `api.waitForConnection()` is skipped, `setPolarSyncPending()` is saved if `needsBackfill == true`, `isRecordingArmed()` resets to `false`, and `endWorkout()` finishes promptly.
- *Given* a hanging `api.waitForConnection()` at stop, *When* `STOP_CONNECTION_TIMEOUT_MS` (`5_000 ms`) elapses, *Then* connection times out gracefully, `setPolarSyncPending()` is saved if `needsBackfill == true`, and GATT resumes.
- **Invariants & Chesterton's Fence**: Net-new `REQ-SET-119` preserves `REQ-SET-105`/`106`/`109` export gating for connected sensors, Verity Sense `WorkoutRr.db` exclusion, post-commit-only flash cleanup, `TrackerService.java <= 3,556` LoC, Lane A/B isolation (`WorkoutRepository.kt` untouched), and `apiOverride` unit-test seams. **Localization**: `N/A - reuses existing 9-locale strings in res/values*/strings_devices.xml; zero new string resources`.

---

## 2. Test Specification (`TST-SET-121`, Offline Mocked)
- **`TST-SET-121.1` (Non-Composition Retry Scope & Disposal Survival)**: Assert `PolarPendingRetrySyncSection` / retry coordinator executes `syncPendingPolarWorkout` on a non-composition `CoroutineScope`, deduplicates concurrent taps per `workoutId`, preserves `isSyncing`/`errorMessage` across composition disposal/re-entry, and never throws `ForgottenCoroutineScopeException`.
- **`TST-SET-121.2` (`CancellationException` Propagation)**: Assert `CancellationException` in `PolarVeritySenseOfflineController` and `PolarH10OfflineController` suspend stop/sync paths is rethrown rather than swallowed.
- **`TST-SET-121.3` (Aftermath HR Telemetry Refresh)**: Assert deferred backfill completion triggers `TrackOnMapAftermathViewModel.loadAftermathData(...)` to reload `telemetryPath` and `hrZoneDistribution` for the active workout.
- **`TST-SET-121.4` (Disconnected Stop Fast-Fail & 5s Connection Timeout)**: Assert disconnected `DeviceManager` fast-fail skips `api.waitForConnection()`, persists `setPolarSyncPending()` when `needsBackfill == true`, and resets `isRecordingArmed()`; assert hanging `waitForConnection()` times out at `STOP_CONNECTION_TIMEOUT_MS = 5_000L` without a `30–45 s` stall and resumes GATT.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-121.1` | Unit (Mocked / Compose) | `PolarRetrySyncCoordinatorTest`, `PolarH10RetrySyncBannerTest` | `REQ-SET-119.1` | Verified |
| `TST-SET-121.2` | Unit (Mocked) | `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest` | `REQ-SET-119.1` | Verified |
| `TST-SET-121.3` | Unit (Mocked) | `PolarRetrySyncCoordinatorTest`, `TrackOnMapAftermathViewModelTest` | `REQ-SET-119.2` | Verified |
| `TST-SET-121.4` | Unit (Mocked) | `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest` | `REQ-SET-119.3` | Verified |
