# Stage 3 Implementation Plan: ATT-9243 — Never-Connected BLE Passive Workout Convergence & Scan-Assist Quota Protection

**Ticket**: `ATT-9243` (`2 SP`, `Bug`, `severity-major`, Risk: `MEDIUM`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Branch**: `feature/ATT-9243`  
**Traces**: `REQ-SET-123` (`REQ-SET-123.1`–`.4`) <-> `TST-SET-125` (`TST-SET-125.1`–`.4`)  
**UI Consistency / Localization**: `N/A - no UI or strings*.xml changes` | **DB Migrations**: `None` (`0` schema bumps)

---

## 1. SWE.2 / SWE.3 Architectural Design & Concurrency Boundaries
* **Layer Boundaries & Monolith Protection**: All BLE reconnect state-machine logic lives inside `MyBTLEDevice.java` (`1,324 -> ~1,385` LoC), and queue retry deduplication lives inside `DeviceSearchPeriodicRetryCoordinator.kt` (`234 -> ~240` LoC). `DeviceManager.java` (`1,707/1,708` LoC) and `PolarOnlineStreamingBridge.kt` (`1,492/1,500` LoC) remain strictly untouched.
* **SWE.2 / SWE.3 Living Docs (`Rule 8.8`, `REQ-PRO-114`)**: Zero new subsystems/FGS/DB managers/repositories (`docs/architecture.md` unchanged). `MyBTLEDevice` state transitions and lifecycle flags change -> Step 3 updates `docs/architecture/detailed_design/my_btle_device.md` (`DOC-ARC-DD-BTLE` `v1.0.7 -> v1.0.8`), verified by `DetailedDesignParityContractTest`.
* **Explicit `mCurrentGattAutoConnect` Tracking & Linearizable `READY -> TRACKING` Replacement (`REQ-SET-123.1`, Gate 2 Advisory 1)**:
  - Add `private static final long NEVER_CONNECTED_WORKOUT_RECONNECT_FLOOR_MS = 60_000L;` and `private boolean mCurrentGattAutoConnect = false;` guarded strictly under `mGattLock`:
    - Set `mCurrentGattAutoConnect = (mBluetoothGatt != null) && autoConnect;` in `triggerGattConnect()` right after `connectGatt(...)`;
    - Set `mCurrentGattAutoConnect = false;` in `mScanCallback.onScanResult()` (which opens `autoConnect = false`) and in `disconnectFromGattInternal(...)` whenever `mBluetoothGatt = null`.
  - Add private `disconnectFromGattInternal(@Nullable Runnable onGattClosed, boolean onlyIfNotAutoConnectDisconnected)` (delegated by existing `disconnectFromGatt(@Nullable Runnable)` with `false`). When `onlyIfNotAutoConnectDisconnected == true`, atomically re-check under `mGattLock` that `!mIsShutDown && mState == State.DISCONNECTED && mBluetoothGatt != null && !mCurrentGattAutoConnect`; if a concurrent Binder `STATE_CONNECTED` or `shutDown()` already won, abort immediately without closing GATT or firing `onGattClosed`.
  - Split `mSearchTimeoutRunnable` on `isMidSessionOrWorkoutActive()`:
    1. **`mHasConnectedInSession == true`**: unchanged fast exponential `mReconnectRunnable` (`1s..16s..finalPeriodMs`) + `State.DISCONNECTED` + `mIsBackgroundReconnecting = true` + `autoConnect = true` if `mBluetoothGatt == null` + `notifyStopSearching(false)`.
    2. **`!mHasConnectedInSession && isMidSessionOrWorkoutActive()` (`TRACKING`/`PAUSED`)**: under `mGattLock` re-verify `!mIsShutDown && mState != CONNECTED_TO_GATT && mState != CONNECTED_WITH_SERVICE`, set `mState = State.DISCONNECTED`, `mIsBackgroundReconnecting = true`, cancel `mReconnectRunnable` (MUST NOT reschedule), and inspect `(mBluetoothGatt, mCurrentGattAutoConnect)`:
       - `mBluetoothGatt != null && mCurrentGattAutoConnect`: retain existing passive `autoConnect = true` handle untouched;
       - `mBluetoothGatt == null`: open passive `triggerGattConnect(dev, true)` directly;
       - `mBluetoothGatt != null && !mCurrentGattAutoConnect` (`READY -> TRACKING` or scan-resolved timeout edge): invoke `disconnectFromGattInternal(onGattClosed, true)` where `onGattClosed` re-checks `!mIsShutDown && !mIsGattPaused && mState == State.DISCONNECTED && mBluetoothGatt == null && isMidSessionOrWorkoutActive()` under `mGattLock`, re-arms `mIsBackgroundReconnecting = true`, and opens `triggerGattConnect(dev, true)`.
       - Always release `DeviceManager` via `if (isSearching()) notifyStopSearching(false)`.
    3. **`!isMidSessionOrWorkoutActive()` (`READY`)**: unchanged `disconnectFromGatt()` + `notifyStopSearching(false)`.
* **Scan-Assist Gating, `attemptBackgroundReconnect` Timeout Skipping & Passive `resumeGattOperations()` (`REQ-SET-123.2`, Gate 2 Advisory 2)**:
  - Parameterize `private void initiateScanAndConnect(boolean allowScanAssist)`: call `startScanAssistance(btAdapter)` only when `allowScanAssist == true`; `startSearching()` passes `true`, `attemptBackgroundReconnect()` passes `mHasConnectedInSession`.
  - In `attemptBackgroundReconnect()`, compute `boolean passiveNeverConnectedWorkout = !mHasConnectedInSession && isMidSessionOrWorkoutActive();` under `mGattLock`: skip `mHandler.postDelayed(mSearchTimeoutRunnable, ...)` when `passiveNeverConnectedWorkout` is `true`, while retaining `mSearchTimeoutRunnable` when `mHasConnectedInSession || !isMidSessionOrWorkoutActive()` (`READY`). Because `resumeGattOperations()` routes `mIsBackgroundReconnecting == true` to `attemptBackgroundReconnect()`, post-yield resume for a never-connected workout sensor cleanly re-establishes passive `autoConnect = true` with `0` `mSearchTimeoutRunnable` and `0` `sScanTimestamps` consumed.
  - In `mGattCallback.onConnectionStateChange(STATE_DISCONNECTED)` (and `status != GATT_SUCCESS`), when `!mIsShutDown && !mHasConnectedInSession && isMidSessionOrWorkoutActive()`, floor `delay = Math.max(delay, NEVER_CONNECTED_WORKOUT_RECONNECT_FLOOR_MS)` (`>= 60_000 ms`) while preserving `1s -> 2s -> 4s -> ...` for `mHasConnectedInSession == true` and `READY` disconnects.
* **Keep-Alive Queue Retry Deduplication (`REQ-SET-123.3`)**:
  - In `DeviceSearchPeriodicRetryCoordinator.handleSearchStoppedForCurrentDevice()`, when `!success`: check `if (current.shouldKeepAliveOnSearchTimeout()) { deviceTries.remove(current); return }` **before** evaluating `tries <= 1`. When `false`, retain exact `tries <= 1` periodic re-search / `detachAndGiveUpDevice` and `tries > 1` decrement + `searchQueue.addLast(current)` behavior.

---

## 2. Atomic Implementation Steps (`Stage 4` — Locked Until Gate 3 `PASS`)

| Step | Target File(s) | Atomic Change | Traces |
| :---: | :--- | :--- | :--- |
| **1** | `MyBTLEDevice.java` | Add `NEVER_CONNECTED_WORKOUT_RECONNECT_FLOOR_MS = 60_000L` & `mCurrentGattAutoConnect` under `mGattLock`; extract `disconnectFromGattInternal(...)`; split `mSearchTimeoutRunnable` for `!mHasConnectedInSession && isMidSessionOrWorkoutActive()` (passive `autoConnect = true` convergence + `READY -> TRACKING` safe replacement, `0` `mReconnectRunnable`); parameterize `initiateScanAndConnect(boolean allowScanAssist)`; skip `mSearchTimeoutRunnable` in `attemptBackgroundReconnect()` when `!mHasConnectedInSession && isMidSessionOrWorkoutActive()`; floor never-connected workout `STATE_DISCONNECTED` delay `>= 60_000 ms`. | `REQ-SET-123.1`, `REQ-SET-123.2` |
| **2** | `DeviceSearchPeriodicRetryCoordinator.kt` | In `handleSearchStoppedForCurrentDevice()` (`!success`), evaluate `current.shouldKeepAliveOnSearchTimeout()` before `tries <= 1`, call `deviceTries.remove(current)`, and return without re-enqueueing tries `2..N`, scheduling periodic re-search, or calling `detachAndGiveUpDevice`. | `REQ-SET-123.3` |
| **3** | `docs/architecture/detailed_design/my_btle_device.md` | Bump `v1.0.7 -> v1.0.8`; add `REQ-SET-123` to Requirement Trace; document `mCurrentGattAutoConnect`, passive never-connected timeout convergence (`0` `mReconnectRunnable`), `allowScanAssist` gating & `mSearchTimeoutRunnable` skipping in `attemptBackgroundReconnect()`, `>= 60 s` never-connected disconnect floor, and passive `resumeGattOperations()` behavior in §§1–3 & Change History. | `REQ-SET-123.4` |
| **4** | `BLEReconnectRobustnessTest.kt`, `DeviceSearchPeriodicRetryCoordinatorTest.kt`, `DeviceManagerSearchQueueTest.kt`, `MyBTLEDeviceLifecycleTest.kt` | Refine `testSearchTimeout_duringTrackingAndPaused_*` for passive convergence and add tests covering `TST-SET-125.1`–`.3`: `3` absent never-connected BLE sensors (`<= 4` scans, `0` throttles, `0` `mReconnectRunnable`), `READY -> TRACKING` GATT replacement (and concurrent `STATE_CONNECTED`/`shutDown` race safety), passive wake-up -> `mHasConnectedInSession = true` fast drop recovery, never-connected `STATE_DISCONNECTED` `>= 60 s` floor + `attemptBackgroundReconnect()` / `resumeGattOperations()` (`0` scans, `0` timeout), `READY` & `BTLEHeartRateDevice` silent-HR preservation, and coordinator keep-alive `deviceTries` cleanup (`3` devices -> `3` searches). | `TST-SET-125.1`–`.3` |
| **5** | Tier 1 Verification & Living Docs | Run Tier 1 targeted Gradle suites (below), promote `REQ-SET-123` / `TST-SET-125` to `[Implemented]` with verbatim `TestClass#testMethod` citations, sync indexes, and verify `verify_requirement_governance.py --check-living-doc-layout`. | `TST-SET-125.4` |

---

## 3. Targeted Verification Command (Tier 1, `Rule 7.5`)
```bash
./gradlew :app:testDebugUnitTest --rerun \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BLEReconnectRobustnessTest" \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDeviceAddressAndSearchTest" \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDeviceLifecycleTest" \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDeviceTest" \
  --tests "com.atrainingtracker.banalservice.devices.DeviceSearchPeriodicRetryCoordinatorTest" \
  --tests "com.atrainingtracker.banalservice.devices.DeviceManagerSearchQueueTest" \
  --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"
```

---

## 4. Non-Negotiable System Invariants & Rollback Safety
* `DeviceManager.java <= 1,708` LoC and `PolarOnlineStreamingBridge.kt <= 1,500` LoC untouched; `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L`, `INITIAL_RECONNECT_DELAY_MS == 1000L`, `MAX_SCANS_IN_WINDOW == 4`, `isMidSessionOrWorkoutActive()` and all `DetailedDesignParityContractTest` symbols preserved; `mGattLock` thread safety with zero lock inversion (`gatt.disconnect()/close()` strictly outside `mGattLock`); `ATT-9155` `triggerSilentConnectionReconnect()` & `PolarOfflineGattCoordinator` pause/resume preserved; `READY` startup battery bounding preserved; zero DB schema or UI string changes; Rule 3 Human Gate preserved.
