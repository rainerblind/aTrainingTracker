# Stage 3: Implementation Plan - ATT-8714: Polar Live-IMU Toggle Handoff & HR Continuity

**DOC-ID**: `DOC-PLN-ATT-8714` | **Ticket**: `ATT-8714` (`3 SP`, Parent `ATT-5910`) | **Target Release**: `V5.0.0`  
**Sprint**: `2026-11-18_01_Früh` | **Requirement Mapping**: `REQ-CON-050` (`.1`..`.5`) | **Test Mapping**: `TST-CON-050` (`.1`..`.4`)  
**Branch**: `feature/ATT-8714` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Summary & Traceability Matrix
Enabling or disabling `polarLiveImuEnabled` while a Polar H10/Verity Sense is connected drops `HR`/`RR_INTERVAL` and leaves `files/raw_sensor/` empty because `DeviceDataRepository.updateDevice()` emits no Live-IMU broadcast, `DeviceManager` races async GATT/bridge teardown, and `PolarOnlineStreamingBridge` misses `SESSION_OPEN` / already-ready features, mutates `polarId` mid-session, and skips job cancellation on disconnect.

| Requirement | Atomic Step(s) | Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-CON-050.1` | Steps 1, 2, 3, 6 | `TST-CON-050.1` | `EditDevicePolarLiveImuTest`, `DeviceDataRepositoryEquipmentSyncTest`, `DeviceManagerTeardownTest` |
| `REQ-CON-050.2` | Step 4 | `TST-CON-050.2` | `PolarOnlineStreamingBridgeTest` |
| `REQ-CON-050.3` | Step 4 | `TST-CON-050.3` | `PolarOnlineStreamingBridgeTest` |
| `REQ-CON-050.4`, `.5` | Steps 3, 4, 5, 7 | `TST-CON-050.4` | `PolarRawTelemetryRecorderTest`, `DetailedDesignParityContractTest` |

## 2. System Invariants & Constraints
1. **No Schema / UI / String Changes**: `Devices.db` stays `DB_VERSION = 14`; zero UI edits; `Localization Parity: N/A - no UI strings`.
2. **Legacy & Hardware Invariants**: Preserve `< API 33` `BTLEHeartRateDevice` fallback, `ProxySensor` / `FilterManager` / `RrBeatSource` continuity (`RR_PRIORITY_ECG` vs `RR_PRIORITY_PPG`), single-GATT ref-counting, `ACC` mutex, and `PolarRawTelemetryRecorder` `rowCount == 0` cleanup.
3. **Offline Verification & Gate 3 Lock**: `0` `app/src/main` reads in unit tests (`REQ-PRO-049.1`); physical Pixel 10 + Polar check deferred to PO (`Rule 7.6`); `Rule 4.1` code lock enforced until Gate 3 `PASS`.

## 3. SWE.2 Architectural Design & Threading
* **Broadcast Channel (`BANALService` / `DeviceDataRepository`)**: Add `POLAR_LIVE_IMU_CHANGED` + `POLAR_LIVE_IMU_ENABLED` intent constants on `BANALService`; emit package-scoped broadcast from `DeviceDataRepository.updateDevice()` on `ioDispatcher` whenever `originalState.polarLiveImuEnabled != finalState.polarLiveImuEnabled` (idle or active workout).
* **Ordered Async Teardown (`DeviceManager` / `MyBTLEDevice` / `PolarOnlineStreamingBridge`)**: Register `mPolarLiveImuChangedReceiver` in `DeviceManager` (unregistered in `shutDown()`). On toggle for paired BLE HRM (`API >= 33`), detach `RrBeatSource`, remove from `mMyRemoteDevices`/`mSearchQueue`, stop old device via completion callback/teardown hook (`MyBTLEDevice.disconnectFromGatt()` / `PolarOnlineStreamingBridge.shutDown()` completion) before invoking `createRemoteDevice()`, re-attaching `RrBeatSource`, and triggering search/connect.
* **Bridge Session & Recorder Continuity (`PolarOnlineStreamingBridge`)**: Capture immutable `polarBleSessionId` at `connectPersistentSession()` (`connectToDevice`..`disconnectFromDevice` & streaming calls), detect `SESSION_OPEN` / already-ready `FEATURE_HR` & `FEATURE_POLAR_ONLINE_STREAMING` via `api.isFeatureReady(...)` to sync `isConnectedFlag`, `registerSensors()`, `activeBridges`, `PolarRawTelemetryRecorder`, `notifyStopSearching(true)`, and start streams; cancel `hrJob`/`accJob`/`gyroJob` in `sdkCallback.deviceDisconnected()`.

## 4. Atomic Stage 4 Implementation Sequence
1. **Step 1 (`REQ-CON-050.1`) — Broadcast Contract (`BANALService.java`, `DeviceDataRepository.kt`)**: Add `POLAR_LIVE_IMU_CHANGED` / `POLAR_LIVE_IMU_ENABLED` constants and emit `sendPolarLiveImuChangedBroadcast(deviceId, enabled)` from `DeviceDataRepository.updateDevice()` when `polarLiveImuEnabled` changes.
2. **Step 2 (`REQ-CON-050.1`) — Ordered Teardown Hook (`MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt`)**: Expose deterministic async teardown completion notification (GATT close completion on `MyBTLEDevice.mHandler`; SDK disconnect/release completion on `PolarOnlineStreamingBridge.shutDown()`) without breaking existing `shutDown()` callers.
3. **Step 3 (`REQ-CON-050.1`, `.4`) — Live Handoff Orchestration (`DeviceManager.java`)**: Wire `mPolarLiveImuChangedReceiver` (register/unregister symmetrically) to detach `RrBeatSource`, await old HRM teardown completion, recreate replacement via `createRemoteDevice()`, re-attach `RrBeatSource`/`FilterManager`, and initiate search/connect (idle & active workout, `< API 33` guarded).
4. **Step 4 (`REQ-CON-050.2`..`.4`) — Bridge Recovery & Job Hygiene (`PolarOnlineStreamingBridge.kt`)**: Lock in-session `polarBleSessionId` on `connectPersistentSession()`, add `SESSION_OPEN` / `isFeatureReady` sync for `isConnectedFlag`, `registerSensors()`, `activeBridges`, `PolarRawTelemetryRecorder`, and HR/ACC/GYRO streams, and cancel `hrJob`/`accJob`/`gyroJob` on `deviceDisconnected()`.
5. **Step 5 (`REQ-PRO-114`) — Living Detailed Design Sync**: Update `docs/architecture/detailed_design/banal_service.md` and `my_btle_device.md` for the new receiver/teardown handoff and verify `DetailedDesignParityContractTest`.
6. **Step 6 (`TST-CON-050.1`) — Repository & Manager Tests**: Extend `EditDevicePolarLiveImuTest` / `DeviceDataRepositoryEquipmentSyncTest` and `DeviceManagerTeardownTest` (Robolectric/MockK) for broadcast emission, teardown-before-connect ordering, `ProxySensor`/`RrBeatSource` continuity, and `< API 33` fallback.
7. **Step 7 (`TST-CON-050.2`..`.4`) — Bridge & Recorder Tests + Tier 1 Run**: Extend `PolarOnlineStreamingBridgeTest` (`SESSION_OPEN`, immutable `polarBleSessionId`, `deviceDisconnected` job cancel) and `PolarRawTelemetryRecorderTest` (`files/raw_sensor/*.polar_raw.csv.gz` `rowCount > 0`); execute Tier 1 `./gradlew :app:testDebugUnitTest --tests ...` and `verify_requirement_governance.py`.

## 5. Verification & Rollback Plan
* **Verification**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest"` + `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Isolated on `feature/ATT-8714`; clean revert before Stage 6 `--merge-ticket`.
