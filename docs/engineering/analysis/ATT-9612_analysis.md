# Stage 1 Analysis: ATT-9612 - Polar Retry Sync GATT Contention & Mid-Discovery Recovery

**Ticket**: [ATT-9612](https://rainerblind.atlassian.net/browse/ATT-9612) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Story Points**: `3 SP` (`severity-major`, `tier-a` — no split required per `REQ-PRO-029`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-23_01_Früh` (Sprint 474) | **Base SHA**: `ff1bf0d6`  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Problem Statement & Field Evidence
On Pixel 10 (`5.0.0-dev-6cba8883`, `2026-11-22`, Workout `2431`, Polar Verity Sense `24825632` / `24:AC:AC:24:82:56` powered off before stop and turned back on afterward), Aftermath *Sync Now* required 3 attempts:
- `10:57:06.331`: `PolarOnlineImuBridge` logged `extending handshake timeout to 15000ms` (arming `searchTimeoutJob` for `10:57:21.331`; Polar SDK `ConnectionHandler` simultaneously armed `CONNECTION_WATCHDOG_TIMEOUT_MS = 30_000 ms` for `10:57:36.331`).
- `10:57:18.191`: Workout `2431` stopped; `PolarVerityOfflineCtrl` fast-failed cleanly (`isTargetConfirmedDisconnected == true`), leaving `PolarOnlineStreamingBridge`'s reconnect timer active.
- `10:57:21.666` (**Attempt 1**): User tapped *Sync Now*. At `10:57:36.337` (`+30.006 s` from `10:57:06.331` / `+15.006 s` from `10:57:21.331`), `BluetoothGatt.disconnect() / refresh() / close()` tore down the GATT session mid-sync. `PolarVerityOfflineCtrl` stayed blocked in `waitForConnection` until `45_000 ms` expired (`10:58:06.672`). **31 ms later** (`10:58:06.703`), immediately on yield release, `82:56` connected and ran GATT service discovery/notifications (`10:58:16`–`10:58:21`).
- `10:58:11` (**Attempt 2**): Failed while GATT discovery & PFTP handshake were still in flight (`10:58:16`–`10:58:21`).
- `10:58:31.412` (**Attempt 3**): Once handshake finished, backfill succeeded in `1.8 s` (`10:58:33.187`).

---

## 2. Independent Peer Review & Verification (`Rules 69 & 70`)
Audited against live `ff1bf0d6` (which already contains `ATT-9175` `ed05d679`) and cached `polar-ble-sdk:8.6.0` (`ConnectionHandler.class`, `BDBleApiImpl.class`):
1. **Partially Confirmed + Deeper Race Found (`PolarOnlineStreamingBridge.kt:342-355, 580-632, 845-875`)**: `ATT-9175` already cancels `reconnectJob`/`searchTimeoutJob` in `pauseForOfflineSync()`, **but** `onSearchTimeout()` (`line 613-632`), `scheduleBackoffReconnect()` post-delay (`line 597-601`), `connectPersistentSession()` (`line 845-863`), and `onSilentHrWatchdogTimeout()` (`line 494-513`) check `isOfflineGattYielded()` **outside any lock** before invoking `disconnectFromDevice()` / `connectToDevice()` / `forceDisconnectDevice()`. Thus an already-woken timer coroutine can still tear down or perturb GATT *after* `pauseForOfflineSync()` returns.
2. **New Critical Root Cause Found (`PolarOfflineGattCoordinator.kt:541-551` + Polar SDK `ConnectionHandler`)**: Even when `pauseForOfflineSync()` cancels `searchTimeoutJob`, either `onSearchTimeout()` fired 335 ms earlier at `10:57:21.331` (arming Polar SDK `DISCONNECT_GUARD_TIME_MS = 15_000 ms` -> `10:57:36.331`) or the stale `SESSION_OPENING` from `10:57:06.331` triggered Polar SDK `CONNECTION_WATCHDOG_TIMEOUT_MS = 30_000 ms` -> `10:57:36.331`. When `10:57:36.337` emits `deviceDisconnected` (`SESSION_CLOSED`), `connectWithinTimeout()` had called `api.connectToDevice(polarId)` **only once** at `10:57:21.666` and remained passively blocked in `api.waitForConnection(polarId)` for **30.3 s** until `45_000 ms` timed out; the instant yield released, the bridge called `connectToDevice()` and connected in **31 ms**.
3. **New Critical Root Cause Found — Attempt 2 Mid-Discovery Failure (`PolarOfflineGattCoordinator.kt:61, 1085-1138`)**: When tapping *Sync Now* while already connected/discovering (`10:58:11`), `connectWithinTimeout()` unconditionally re-issues `api.connectToDevice(polarId)` instead of reusing `isDeviceConnected()` or awaiting `isDeviceConnecting()`, and `awaitFeaturesReady()` uses `FEATURE_READY_TIMEOUT_MS = 10_000 ms` (`10:58:11 + 10 s = 10:58:21`, expiring right as 8-feature discovery finishes!) plus checks `api.isFeatureReady(polarId, it)` on only a single string rather than across registered MAC/8-hex aliases.
4. **Sibling Parity Gap Found (`PolarH10OfflineController.kt:424-431, 623-637, 800-805`; `PolarVeritySenseOfflineController.kt:555, 610, 635`)**: `PolarH10OfflineController.syncPendingWorkout()` and `executeArming()` bypass `connectWithinTimeout()` entirely and never call `awaitFeaturesReady(..., FEATURE_POLAR_H10_EXERCISE_RECORDING)` on managed APIs, so H10 fails identically on mid-wait disconnects and mid-discovery *Sync Now* taps. Three Verity Sense helper methods (`performArmingSequence`, `stopAndFetchOfflineHr`, `removeOfflineRecord`) also call raw `connectToDevice`/`waitForConnection`.

---

## 3. Root Cause Summary
1. **Unserialized Bridge Yield Quiescence**: Missing `gattActionLock` quiescence barrier between `pauseForOfflineSync()` and waking `onSearchTimeout()` / `connectPersistentSession()` / `onSilentHrWatchdogTimeout()` allows late bridge teardowns/connects after offline yield begins.
2. **Single-Shot `connectWithinTimeout()` Blind to Mid-Wait Disconnects & In-Flight Handshakes**: Does not track `connectingDeviceIds`, does not reuse already-connected/in-flight managed sessions, and never re-issues `api.connectToDevice()` when a pre-yield teardown or SDK watchdog fires `deviceDisconnected` mid-wait.
3. **Tight 10 s Non-Alias `awaitFeaturesReady()` & H10/Verity Sibling Parity Gaps**: `10_000 ms` feature timeout expires during 10–15 s cold 8-feature GATT discovery; `isFeatureReady` misses MAC/8-hex aliases; H10 `syncPendingWorkout()` bypasses `connectWithinTimeout()` and `awaitFeaturesReady()`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Refines `REQ-SET-120` (`ATT-9175`, `ed05d679`), `REQ-SET-119` (`ATT-8969`, `8e44039d`), `REQ-SET-118` (`ATT-8690`, `34b78941`), `REQ-CON-050` (`ATT-8714`), `REQ-CON-054` (`ATT-9155`) across `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`.
- **Historical Origin & Commit Trace**: `ATT-8690` (`34b78941`) unified the 8-feature `PolarBleApi` singleton; `ATT-8969` (`8e44039d`) added `connectWithinTimeout()` (`5 s` stop-time cap); `ATT-9175` (`ed05d679`) added alias-aware offline yield + `15 s` `deviceConnecting` grace.
- **Root Reason for Existing Formulation**: `connectWithinTimeout()` assumed a single `connectToDevice()` call would remain uncontested until `waitForConnection()` returned; `FEATURE_READY_TIMEOUT_MS = 10 s` pre-dated 8-feature dual-service discovery overhead; `PolarH10OfflineController` predated `awaitFeaturesReady()` so legacy `apiOverride` unit tests leave `isFeatureReady` unstubbed.
- **Preservation of Core Invariants**: Keep `PolarH10OfflineController.BACKFILL_TIMEOUT_MS = 30_000L`, `PolarVeritySenseOfflineController.BACKFILL_TIMEOUT_MS = 45_000L`, `STOP_CONNECTION_TIMEOUT_MS = 5_000L` (`REQ-SET-119.3`), `5 s` absent-sensor timeout (`REQ-CON-045.1`), sibling Polar isolation (`REQ-SET-118.3`), `CancellationException` rethrow (`REQ-SET-119.2`), unmanaged `apiOverride` mock compatibility, and all pinned public JVM signatures (`InterfaceSignatureDriftContractTest`).

---

## 5. Change-Request Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10 & `REQ-PRO-131.4`)
- **Affected Requirements**: Reserve `REQ-SET-129` (`REQ-SET-129.1`–`.4`) and `TST-SET-131` (`TST-SET-131.1`–`.4`) in `docs/requirements/req_settings_profiles.md` and `docs/tests/tst_settings_profiles.md`. Refines `SYSREQ-DEX-007` (`docs/system/sysreq_data_exchange.md`) and `SYSREQ-TRK-002` (`docs/system/sysreq_live_tracking.md`, `docs/system/system_software_traceability.md`); no new hardware profiles or `SYS-EL-*`/`SYS-IF-*` topology changes (`REQ-PRO-131.4`). `Localization Parity: N/A - 0 string changes`.
- **Affected Architecture Elements**: `PolarOfflineGattCoordinator.kt` (`1,315 -> ~1,400 / 1,500` LoC), `PolarOnlineStreamingBridge.kt` (`1,477 -> ~1,488 / 1,500` LoC, unchanged public JVM signatures), `PolarH10OfflineController.kt` (`868 -> ~893 / 1,500` LoC), `PolarVeritySenseOfflineController.kt` (`1,483 -> ~1,485 / 1,500` LoC), `docs/architecture/detailed_design/my_btle_device.md` (`REQ-PRO-114`). **Untouched**: `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `PolarRetrySyncCoordinator.kt`, `strings*.xml`.
- **Verification Impact & Test Surface**: Targeted suites (`PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`, `PolarVeritySenseOfflineControllerTest`, `PolarH10OfflineControllerTest`, `PolarPmdStreamCoordinatorTest`, `PolarRetrySyncCoordinatorTest`, `BleHrmSilentConnectionWatchdogTest`) + 7 contract suites (`ArchitectureConformance`, `InterfaceSignatureDrift`, `DeadCodeElimination`, `DetailedDesignParity`, `SourceTextReadingAudit`, `SystemDataExchangeRequirements`, `SystemLiveTrackingRequirements`).
- **Risk Assessment & Invariants**: **HIGH (`tier-a`)** — multi-threaded BLE/GATT concurrency & lock ordering. Mitigated by strict acyclic lock DAG (`gattActionLock` -> leaf `offlineYieldLock` read-only; zero callbacks under `offlineYieldLock` or `apiLock`), `finally`-scoped transient multiplexer listeners, unmanaged `apiOverride` fallback preserving legacy tests, and `< 1,500` LoC monolith budgets.

---

## 6. Scope Bounding
- **In Scope**: Linearizable `PolarOnlineStreamingBridge` yield quiescence (`gattActionLock`), disconnect-recovering & in-flight-reusing `PolarOfflineGattCoordinator.connectWithinTimeout()`, alias-aware `20 s` `awaitFeaturesReady()`, Verity Sense + H10 controller sibling parity, detailed design (`my_btle_device.md`), and deterministic concurrency unit tests.
- **Out of Scope**: UI/Banner changes (`EditWorkoutScreen`, `PolarH10RetrySyncBanner`), `PolarRetrySyncCoordinator`, `MyBTLEDevice`, DB schema, localization strings, and physical Pixel 10 RF validation (PO Ceremony 2 per `Rule 7.6`).
