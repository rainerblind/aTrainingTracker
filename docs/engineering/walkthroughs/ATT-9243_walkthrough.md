# Stage 5 Verification Walkthrough: ATT-9243 — Stop Never-Connected Paired BLE Devices From Triggering Aggressive Scan-Assist Reconnect Storms During Active Workouts

**Ticket**: `ATT-9243` (`2 SP`, `Bug`, `severity-major`, Risk: `MEDIUM`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Verified Code SHA**: `85386e63` (`feature/ATT-9243` preserved intact for Stage 6 Cold Review per `Rule 3.3`)  
**Living Docs Promoted**: `REQ-SET-123` (`docs/requirements/req_settings_profiles.md` `v1.0.28`, `[Verified]`) <-> `TST-SET-125` (`docs/tests/tst_settings_profiles.md` `v1.0.27`, `[Verified]`), `docs/architecture/detailed_design/my_btle_device.md` (`v1.0.8`), `docs/system/sysreq_live_tracking.md` (`v1.2.23`), `docs/system/system_software_traceability.md` (`v1.2.23`) | **Localization**: `N/A - 0 UI strings`

---

## 1. Root Cause & Implemented Fix (`85386e63`)
* **Root Cause**: During active workouts (`TRACKING`/`PAUSED`), `MyBTLEDevice.isMidSessionOrWorkoutActive()` returned `true` even for paired BLE sensors left at home (`mHasConnectedInSession == false`), causing `mSearchTimeoutRunnable` to launch aggressive `1s -> 2s -> 4s -> 8s -> 16s` `mReconnectRunnable` + `startScanAssistance()` loops across every absent paired sensor, while `DeviceSearchPeriodicRetryCoordinator` re-enqueued keep-alive sensors for tries `2..N` before checking `shouldKeepAliveOnSearchTimeout()` — generating 54 Android scan-quota throttle warnings (`>= 4` scans in `30 s`) on Pixel 10 (`2026-11-20`) and starving legitimate mid-workout drop recovery.
* **Implemented Fix**:
  1. `MyBTLEDevice.java` (`1,424` LoC): Added `NEVER_CONNECTED_WORKOUT_RECONNECT_FLOOR_MS = 60_000L` and `mCurrentGattAutoConnect` guarded strictly under `mGattLock` (set in `triggerGattConnect`, cleared in `onScanResult` and `disconnectFromGattInternal`). Implemented atomic `disconnectFromGattInternal(onGattClosed, onlyIfNotAutoConnectDisconnected)` executing the `synchronized(mGattLock)` guard before clearing queues/runnables and preserving `mIsBackgroundReconnecting = true` during replacement. Split `mSearchTimeoutRunnable` so `!mHasConnectedInSession && isMidSessionOrWorkoutActive()` converges passively to OS-managed `autoConnect = true` (cleanly replacing non-`autoConnect` `READY -> TRACKING` GATT, `0` `mReconnectRunnable`), while preserving fast `1s..16s..finalPeriodMs` recovery for `mHasConnectedInSession == true` and `READY` battery bounding. Parameterized `initiateScanAndConnect(boolean allowScanAssist)` so `startSearching()` passes `true` while `attemptBackgroundReconnect()` passes `mHasConnectedInSession` and skips `mSearchTimeoutRunnable` when `!mHasConnectedInSession && isMidSessionOrWorkoutActive()` (including passive `resumeGattOperations()`). Floored never-connected workout `STATE_DISCONNECTED` delay `>= 60_000 ms`.
  2. `DeviceSearchPeriodicRetryCoordinator.kt` (`236` LoC): Checked `current.shouldKeepAliveOnSearchTimeout()` before `tries <= 1` in `handleSearchStoppedForCurrentDevice()` and removed `current` from `deviceTries` without re-enqueueing tries `2..N`, preserving `READY` multi-try and periodic re-search invariants.
  3. **Untouched Ceilings & SWE.3 Parity**: `DeviceManager.java` (`1,707/1,708`) and `PolarOnlineStreamingBridge.kt` (`1,492/1,500`) strictly untouched; `docs/architecture/detailed_design/my_btle_device.md` (`v1.0.8`) synchronized (`DetailedDesignParityContractTest`).

---

## 2. Bug Fix Verification Evidence (`REQ-PRO-121.2`, `Rule 7.5` Doc-Only Fast Path)
* **Zero Code Drift Since Gate 4 (`Rule 7.5`)**: `git diff --name-only 85386e63..HEAD` touches `0` files under `app/`; Stage 5 directly reuses Gate 4 (`ATT-9483` @ `85386e63`) independently verified Tier-1 test evidence (`88/88 PASS`, `0` new production compiler warnings) without redundant Gradle re-execution.
* **Reproduction & Regression Coverage (`TST-SET-125.1`–`.4`)**:
  - `BLEReconnectRobustnessTest#testNeverConnectedThreeAbsentSensors_duringWorkout_convergePassiveAndUpgradeOnWakeUp_TST_SET_125_1` & `testSearchTimeout_duringTrackingAndPaused_clearsSearchingAndReleasesQueueWhilePersistingReconnect`: Reproduces 3 absent never-connected paired BLE sensors during `TRACKING`/`PAUSED`, verifying `3 <= 4` initial scans (`0` quota throttles), passive `autoConnect = true` convergence with `0` `mReconnectRunnable`, and full fast `1s` + scan-assist upgrade once passively awakened mid-ride.
  - `BLEReconnectRobustnessTest#testSearchTimeout_readyToTrackingEdge_replacesNonAutoConnectGattAndGuardsConcurrentRaces_TST_SET_125_1`: Proves Try-1 `READY -> TRACKING` atomically closes/replaces `autoConnect = false` GATT with passive `autoConnect = true` while preserving `mIsBackgroundReconnecting` and guarding against concurrent `shutDown`/`STATE_CONNECTED`.
  - `BLEReconnectRobustnessTest#testNeverConnectedWorkoutDisconnect_floorsDelay60sAndSkipsScanAssistAndTimeout_TST_SET_125_2`, `MyBTLEDeviceAddressAndSearchTest#testScanAssistance_onScanResult_connectsDuringDecoupledBackgroundReconnect`, `MyBTLEDeviceLifecycleTest#testResumeGattOperations_whileBackgroundReconnecting_preservesNonBlockingState`, & `BTLEHeartRateDeviceTest`: Proves `>= 60_000 ms` floor on spurious GATT `133`/`8` disconnects, `0` scans / `0` `mSearchTimeoutRunnable` on never-connected `attemptBackgroundReconnect()` and passive `resumeGattOperations()`, preserved `READY` timeout & `ATT-9155` silent-HR reconnect.
  - `DeviceSearchPeriodicRetryCoordinatorTest#testHandleSearchStopped_keepAliveDevice_removesDeviceTriesAndSkipsTries2ToN_TST_SET_125_3` & `DeviceManagerSearchQueueTest#testThreeAbsentKeepAliveDevices_withTries3_searchedOnceAndDeviceTriesCleared_TST_SET_125_3`: Proves keep-alive devices clean `deviceTries` and search strictly once (`3` total instead of `9`), while non-keep-alive `READY` devices retain tries `2..N`.

| Tier-1 Targeted Suite (`--rerun`, SHA `85386e63`) | Result | Traces |
| :--- | :---: | :--- |
| `BLEReconnectRobustnessTest` | **`16/16 PASS`** | `TST-SET-125.1`/`.2`, `ATT-7755`, `ATT-8998`, `ATT-8965` |
| `MyBTLEDeviceAddressAndSearchTest` | **`11/11 PASS`** | `TST-SET-125.2`, `REQ-CON-023` |
| `MyBTLEDeviceLifecycleTest` | **`14/14 PASS`** | `TST-SET-125.2`, `REQ-CON-012`, `ATT-9155` |
| `BTLEHeartRateDeviceTest` | **`16/16 PASS`** | `TST-SET-125.2`, `ATT-9155` silent-HR watchdog |
| `DeviceSearchPeriodicRetryCoordinatorTest` | **`6/6 PASS`** | `TST-SET-125.3`, `ATT-8965` |
| `DeviceManagerSearchQueueTest` | **`7/7 PASS`** | `TST-SET-125.3`, `ATT-8998`, `ATT-8965` |
| `DetailedDesignParityContractTest` | **`3/3 PASS`** | `TST-SET-125.4` (`my_btle_device.md` `v1.0.8`) |
| `SystemLiveTrackingRequirementsContractTest` | **`4/4 PASS`** | `TST-SET-125.4` (`SYSREQ-TRK-002` parity) |
| `ArchitectureConformanceContractTest` | **`11/11 PASS`** | `TST-SET-125.4` (`DeviceManager`/`PolarOnlineStreamingBridge` untouched) |
| **Tier-1 Targeted Total** | **`88/88 PASS`** (`0` fail, `0` err, `0` skip, `0` new prod warnings) | Scope: 9 BLE/device/contract suites; full suite deferred to Tier-2 (`Rule 7.5`) |

* **Non-UI Backend Scope (`Rule 7.6`)**: Pure BLE reconnection state-machine bugfix with zero UI/resource changes; physical outdoor multi-sensor ride validation is deferred to Ceremony 2 (`Pixel 10`) per `Rule 7.6`.
* **Safety Linters & Governance**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` (`3/3 PASS`); `verify_requirement_governance.py --sync-indexes`, `--check-living-doc-layout`, and `--check-verified-evidence` (`EXIT 0`); `ANT-Android-SDKs` (`160000`) intact.
* **Branch Readiness (`Rule 3.3`)**: `feature/ATT-9243` remains intact (NOT squashed onto `sprint/2026-11-21_01_Früh`) and ready for Gate 5 + Stage 6 Cold Review.
