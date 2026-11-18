# Stage 2: Requirement & Test Specification - ATT-8714

**DOC-ID**: `DOC-TST-ATT-8714` | **Ticket**: [ATT-8714](https://rainerblind.atlassian.net/browse/ATT-8714) (`3 SP`) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-18_01_Früh` | **Branch**: `feature/ATT-8714`  
**Requirement Mapping**: `REQ-CON-050` | **Test Spec ID**: `TST-CON-050` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-CON-050` `[Planned]`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit`
* **Implementation**: `BANALService.java`, `DeviceDataRepository.kt`, `DeviceManager.java`, `MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, `PolarRawTelemetryRecorder.kt`
* **`REQ-CON-050.1` (Live `polarLiveImuEnabled` Broadcast & Ordered GATT Handoff)**: `DeviceDataRepository.updateDevice()` SHALL broadcast a dedicated Live-IMU change intent (`deviceId`, `polarLiveImuEnabled`) whenever `polarLiveImuEnabled` changes (workout idle OR active). `DeviceManager` SHALL detach `RrBeatSource`, stop/close the active Polar HRM (`MyBTLEDevice` GATT or `PolarOnlineStreamingBridge.shutDown()`), wait/yield for teardown completion before activating the replacement Polar device via `createRemoteDevice()`, re-attach `RrBeatSource`/`FilterManager`, and initiate connection.
* **`REQ-CON-050.2` (Shared `PolarBleApi` `SESSION_OPEN` & Already-Ready Sync)**: When `PolarOnlineStreamingBridge.connectPersistentSession()` attaches to an already-open (`SESSION_OPEN`) session on the shared `PolarOfflineGattCoordinator` / `PolarBleApi` singleton without re-emitted `deviceConnected` or `bleSdkFeatureReady` callbacks, it SHALL synchronize `isConnectedFlag`, `registerSensors()`, `activeBridges`, `PolarRawTelemetryRecorder` attachment, `notifyStopSearching(true)`, and start already-ready `FEATURE_HR` / `FEATURE_POLAR_ONLINE_STREAMING` streams.
* **`REQ-CON-050.3` (Consistent `polarBleSessionId` & Disconnect Job Hygiene)**: `PolarOnlineStreamingBridge` MUST retain the exact `polarBleSessionId` passed to `connectToDevice(...)` for all in-session SDK calls (`startHrStreaming`, `requestStreamSettings`, `startAccStreaming`, `startGyroStreaming`, `stopStreaming`, `disconnectFromDevice`), preventing mid-session `macAddress`-to-8-hex flips from breaking session lookup. `sdkCallback.deviceDisconnected()` MUST cancel stale `hrJob`/`accJob`/`gyroJob` before auto-reconnect.
* **`REQ-CON-050.4` (Continuous `HR`/`RR_INTERVAL` & `PolarRawTelemetryRecorder` Capture)**: Across Live-IMU enable/disable transitions, `ProxySensor`/`FilterManager`/`RrBeatSource` SHALL continue delivering `HR`/`RR_INTERVAL`, and active `PolarRawTelemetryRecorder` (`files/raw_sensor/*.polar_raw.csv.gz`) SHALL attach on bridge readiness and persist `rowCount > 0`.
* **`REQ-CON-050.5` (Localization & `Rule 7.6` Hardware Boundary)**: `Localization Parity: N/A - no UI strings` (`values*/strings*.xml` untouched). Phase 2 verifies via offline unit/Robolectric tests (`0` `src/main` reads); physical Pixel 10 + Polar Verity Sense/H10 field verification is PO-executed/optional (`Rule 7.6`, never synthesized).

## 2. Requirement Archaeology
* **Chesterton's Fence Audit (`REQ-PRO-022`)**: Refines `REQ-SET-112.1`, `REQ-SET-112.2` (`f4bef44c`), `REQ-CON-045.2` (`34b78941`), and `REQ-CON-049.4` (`9f9e5967`); preserves `< API 33` `BTLEHeartRateDevice` fallback, `ProxySensor` continuity, single-GATT ref-counting (`REQ-SET-118.3`), `ACC` channel mutual exclusion (`REQ-SET-112.3`), `RR_PRIORITY_ECG`/`PPG`, and `PolarRawTelemetryRecorder` `rowCount == 0` empty-file cleanup (`REQ-PRO-049.1` offline test hygiene).

## 3. Acceptance Criteria
* *Given* a connected Polar H10/Verity Sense on `BTLEHeartRateDevice` (idle or active workout), *When* `DeviceDataRepository.updateDevice()` persists `polarLiveImuEnabled = true`, *Then* `DeviceManager` receives the broadcast, completes GATT close before opening `PolarOnlineStreamingBridge`, preserves `HR`/`RR_INTERVAL`, and writes frames to `PolarRawTelemetryRecorder`.
* *Given* a `SESSION_OPEN` shared `PolarBleApi` session opened by `macAddress` with ready HR/streaming features, *When* `connectPersistentSession()` runs and later receives `deviceConnected` (8-hex ID) or `deviceDisconnected`, *Then* the bridge immediately syncs connected state and starts streams with consistent `polarBleSessionId`, and cancels `hrJob`/`accJob`/`gyroJob` on disconnect.
* *Given* an active `PolarOnlineStreamingBridge`, *When* `polarLiveImuEnabled` toggles to `false`, *Then* the bridge shuts down cleanly before `BTLEHeartRateDevice` connects and resumes `HR`/`RR_INTERVAL`.

## 4. Test Specification (`TST-CON-050`, Offline Unit/Robolectric — `Rule 7.6`)
* **`TST-CON-050.1` (`EditDevicePolarLiveImuTest`, `DeviceManagerTeardownTest`)**: Verify `polarLiveImuEnabled` toggle emits the Live-IMU broadcast (idle & active workout), non-IMU edits do not, `DeviceManager` tears down GATT/bridge strictly before opening replacement device, and `< API 33` fallback + `ProxySensor`/`RrBeatSource` wiring remain intact.
* **`TST-CON-050.2` (`PolarOnlineStreamingBridgeTest`)**: Mock shared `PolarBleApi` in `SESSION_OPEN` with `isFeatureReady(FEATURE_HR / FEATURE_POLAR_ONLINE_STREAMING) == true`; verify immediate connected-state sync, `registerSensors()`, `activeBridges`, recorder attachment, and HR/ACC/GYRO stream startup.
* **`TST-CON-050.3` (`PolarOnlineStreamingBridgeTest`)**: Connect by `macAddress` then deliver `deviceConnected` with 8-hex `deviceId`; assert `startHrStreaming`/`requestStreamSettings`/`startAccStreaming`/`startGyroStreaming` use consistent `polarBleSessionId`, and `deviceDisconnected` cancels `hrJob`/`accJob`/`gyroJob`.
* **`TST-CON-050.4` (`PolarRawTelemetryRecorderTest`, `Rule 7.6`)**: Verify active `PolarRawTelemetryRecorder` attaches on bridge readiness and writes non-zero `.polar_raw.csv.gz` frames (`files/raw_sensor/`) across Live-IMU enable (`N/A - no UI strings`; `0` `src/main` reads; physical Pixel 10 + Verity Sense/H10 check PO-only per `Rule 7.6`).

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-050.1` | Unit / Robolectric | `DeviceDataRepository`, `DeviceManager`, `MyBTLEDevice` | `REQ-CON-050.1`, `.4`, `.5` | Proposed |
| `TST-CON-050.2` | Unit (Mocked SDK) | `PolarOnlineStreamingBridge`, `PolarOfflineGattCoordinator` | `REQ-CON-050.2`, `.4` | Proposed |
| `TST-CON-050.3` | Unit (Mocked SDK) | `PolarOnlineStreamingBridge` | `REQ-CON-050.3` | Proposed |
| `TST-CON-050.4` | Unit / Robolectric | `PolarRawTelemetryRecorder`, `PolarOnlineStreamingBridge` | `REQ-CON-050.4`, `.5` | Proposed |
