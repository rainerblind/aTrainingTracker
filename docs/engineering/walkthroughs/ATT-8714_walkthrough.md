# Stage 5: Walkthrough & Verification - ATT-8714: Polar Live-IMU Toggle Handoff & HR Continuity

**DOC-ID**: `DOC-WLK-ATT-8714` | **Ticket**: `ATT-8714` (`3 SP`, Parent `ATT-5910`) | **Target Release**: `V5.0.0`  
**Sprint**: `2026-11-18_01_Früh` | **Requirement Mapping**: `REQ-CON-050` (`.1`..`.5`) | **Test Mapping**: `TST-CON-050` (`.1`..`.4`)  
**Branch**: `feature/ATT-8714` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Bounce 1/3 Heals (`064edfd3`)
* **Live-IMU Broadcast & Ordered Teardown Handoff (`REQ-CON-050.1`)**: Added `BANALService.POLAR_LIVE_IMU_CHANGED` / `POLAR_LIVE_IMU_ENABLED`; `DeviceDataRepository.updateDevice()` emits it post-DB commit on `polarLiveImuEnabled` toggle (idle or active). `DeviceManager.polarLiveImuChanged()` detaches `RrBeatSource`, serializes rapid repeat toggles via `mPendingTeardownDeviceIds` + `onLiveImuTeardownComplete`, and awaits `MyBTLEDevice.shutDown(Runnable)` (routing via polymorphic `shutDown()` so `BTLEHeartRateDevice.shutDown()` clears RR state before `mHandler` GATT close) or `PolarOnlineStreamingBridge.shutDown(Runnable)` before `createRemoteDevice()`, preserving `ProxySensor` / `< API 33` fallback.
* **Shared-Session Recovery & Dual-ID Hygiene (`REQ-CON-050.2`..`.4`)**: `PolarOnlineStreamingBridge` separates `coordinatorSessionKey` (for `acquirePersistentSession`/`releasePersistentSession`) from `activeSdkStreamId` (for `connectToDevice`, streaming, and `disconnectFromDevice`), cleans up alias entries in `PolarOfflineGattCoordinator.unregisterActiveSession()`, recovers `SESSION_OPEN` with both already-ready (`syncAlreadyOpenSessionIfReady`) and deferred `bleSdkFeatureReady` callbacks (`sessionOpenPendingFeatures` + `PolarOfflineGattCoordinator.isDeviceConnected()`), attaches `PolarRawTelemetryRecorder`, and cancels `hrJob`/`accJob`/`gyroJob` in `deviceDisconnected()`.
* **UI / Localization**: `N/A - no UI strings` (`values*/strings*.xml` and UI layouts untouched).

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method (Offline Mocked / Robolectric) | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-050.1` | `TST-CON-050.1` | `EditDevicePolarLiveImuTest` (`5/5`), `DeviceDataRepositoryEquipmentSyncTest` (`8/8`), `DeviceManagerTeardownTest` (`5/5`, rapid toggle) | **PASSED** (`18/18`) | `Verified` |
| `REQ-CON-050.2` | `TST-CON-050.2` | `PolarOnlineStreamingBridgeTest` (`24/24`, `SESSION_OPEN` immediate & deferred ready, dual-ID cleanup, real `BTLEHeartRateDevice` reverse handoff) | **PASSED** (`24/24`) | `Verified` |
| `REQ-CON-050.3` | `TST-CON-050.3` | `PolarOnlineStreamingBridgeTest` (`activeSdkStreamId` MAC->8-hex stability, `deviceDisconnected` job cancel) | **PASSED** | `Verified` |
| `REQ-CON-050.4`, `.5` | `TST-CON-050.4` | `PolarRawTelemetryRecorderTest` (`6/6`, `.polar_raw.csv.gz` `rowCount > 0`), `DetailedDesignParityContractTest` (`3/3`), `SourceTextReadingAuditContractTest` (`4/4`) | **PASSED** (`13/13`) | `Verified` |

## 3. Automated Test & Governance Evidence (`Rule 7.5` Reuse on Verified SHA `064edfd3`)
* **Zero Code Drift (`Rule 7.5`)**: `git diff --stat 064edfd3 -- app/` is empty; Bounce 1/3 verified XMLs at `app/build/test-results/testDebugUnitTest/` are reused directly:
  * **Tier 1 Targeted Offline Unit/Robolectric Scope**: **23 suites, 154 tests, 0 failures, 0 errors, 0 skipped**:
    * Primary & contract suites: `EditDevicePolarLiveImuTest` (`5/5`), `DeviceDataRepositoryEquipmentSyncTest` (`8/8`), `DeviceManagerTeardownTest` (`5/5`), `PolarOnlineStreamingBridgeTest` (`24/24`), `PolarRawTelemetryRecorderTest` (`6/6`), `DetailedDesignParityContractTest` (`3/3`), `SourceTextReadingAuditContractTest` (`4/4`), `BTLEHeartRateDeviceTest` (`12/12`).
    * Adjacent `DeviceManager*` (`Discovery`, `IndoorMode`, `LocationInit`, `QuotaClamping`, `RrSource`, `SearchQueue`): `19/19`.
    * Adjacent `polar.*` (`PolarVeritySenseOfflineControllerTest` `25/25`, `PolarH10OfflineControllerTest` `17/17`, `PolarOfflineGattCoordinatorCoexistenceTest` `5/5`, `PolarLiveRunningCadenceEstimatorTest` `5/5`, `PolarH10RunningDynamicsEstimatorTest` `4/4`, `PolarOfflineCapabilityStoreTest` `4/4`, `PolarRawTelemetryPrunerTest` `4/4`, `PolarRawTelemetryReaderTest` `3/3`, `WorkoutSamplesHrBackfillTest` `1/1`): `68/68`.
* **Governance & SWE.3 Parity**: `banal_service.md` (`v1.0.3`) and `my_btle_device.md` (`v1.0.2`) synchronized (`REQ-PRO-114`); `0` `app/src/main` reads (`SourceTextReadingAuditContractTest` `4/4`, `REQ-PRO-049.1`); `verify_requirement_governance.py` exited `0`.

## 4. Physical Hardware Verification Boundary (`Rule 7.6`, `REQ-TST-004`)
* **Pending PO Field Verification on Pixel 10 + Polar Verity Sense/H10**: Physical BLE device toggling and live capture on Pixel 10 with Polar Verity Sense (`24:AC:AC:24:82:56`) and Polar H10 (`24:AC:AC:25:0F:A6`) are deferred exclusively to optional PO field execution per `Rule 7.6` (zero physical device results synthesized in Phase 2).
