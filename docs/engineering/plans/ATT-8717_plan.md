# Stage 3: Implementation Plan - ATT-8717: Hardware-Grounded Polar Detection & Canonical ID

**Ticket**: `ATT-8717` (`3 SP`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-CON-049` (`REQ-CON-049.1`..`.5`) | **Test Mapping**: `TST-CON-049` (`TST-CON-049.1`..`.4`)  
**Branch**: `feature/ATT-8717` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
Polar H10 vs. Verity Sense classification and 8-hex Polar ID extraction currently rely on user-editable display names (`isPolarH10Name`, `isPolarVeritySenseName`, `POLAR_ID_REGEX`). Moreover, `BTSearchForNewDevicesEngine` marks any HRM exposing `UUID_SERVICE_POLAR_PFTP` in `PolarOfflineCapabilityStore`, so renaming an H10 causes it to misclassify as Verity Sense. Replacing name dominance with persisted BLE Service UUIDs (`UUID_SERVICE_POLAR_PMD`, `UUID_SERVICE_POLAR_PFTP`), DIS `0x180A` characteristics (`0x2A24` Model, `0x2A25` Serial), and Polar SDK callbacks fixes detection without a `Devices.db` v15 migration.

## 2. Traceability & Requirements Mapping
| Requirement | Architectural Step(s) | Test Spec | Target Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-CON-049.1`, `.2` | Steps 1, 2 | `TST-CON-049.1` | `PolarOfflineCapabilityStoreTest`, `PolarOfflineGattCoordinatorCoexistenceTest` |
| `REQ-CON-049.3` | Steps 3, 4, 5 | `TST-CON-049.2`, `.3` | `BTSearchForNewDevicesEngineTest`, `BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest` |
| `REQ-CON-049.4`, `.5` | Steps 2, 5, 6, 7 | `TST-CON-049.3`, `.4` | `PolarOnlineStreamingBridgeTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest` |

## 3. System Invariants & Preserved Behavior
1. **No `Devices.db` Schema Bump**: `DevicesDatabaseManager.DB_VERSION` stays `14` (`DevicesDatabaseManagerMigrationV14Test` intact).
2. **ATT-8690 & ATT-8706 Parity**: Single 8-feature `PolarBleApi` singleton, callback multiplexer, `RR_PRIORITY_ECG` vs. `RR_PRIORITY_PPG` (`DeviceManager.getRrSourcePriority`), and pre-connect `registerDeviceAliases(polarDeviceId, mac)` preserved.
3. **Legacy Row Compatibility**: Already-paired rows with `PolarDeviceType == UNKNOWN` keep legacy name/regex fallback strictly until first reconnect hydrates DIS/GATT metadata.
4. **Offline & String Hygiene**: `N/A - no UI strings`; unit tests run strictly offline (`Rule 5`) with `0` `app/src/main` reads (`REQ-PRO-049.1`); `MyBTLEDevice.java` state machine/queue remains unmodified (`my_btle_device.md` invariant preserved).

## 4. Proposed Architectural Changes (SWE.2)
* **GATT Constants & Store (`BluetoothConstants.java`, `PolarOfflineCapabilityStore.kt`)**: Add `UUID_CHARACTERISTIC_SERIAL_NUMBER` (`0x2A25`); extend `PolarOfflineCapabilityStore` with `PolarDeviceType` (`H10`, `VERITY_SENSE`, `UNKNOWN`), `hasPmdService`, `hasPftpService`, `modelNumber` (`0x2A24`), and `polarDeviceId` (`0x2A25` / `PolarDeviceInfo.deviceId`) keyed by normalized MAC.
* **Three-Path Hydration**: Capture PMD/PFTP UUIDs + DIS `0x2A24`/`0x2A25` during (a) pairing in `BTSearchForNewDevicesEngine`, (b) standard HRM reconnect via `BTLEHeartRateDevice.configureServices()` + `onOtherCharacteristicUpdate()`, and (c) Polar SDK sessions via `PolarOnlineStreamingBridge.sdkCallback` (`disInformationReceived`, `deviceConnected`, `bleSdkFeatureReady`) + offline controllers.
* **Deterministic Resolution & Consumers**: Update `PolarOfflineGattCoordinator` (`isPolarH10Device`, `isPolarVeritySenseDevice`, `extractPolarId`, `findMacAddressForPolarId`) and callers (`PolarOnlineStreamingBridge`, `PolarH10OfflineController`, `PolarVeritySenseOfflineController`, `DeviceDataRepository`, `EditDeviceDialog`) to prefer hardware-backed identity and seed aliases pre-connect.
* **UI Consistency**: No UI changes. (`PolarOfflineDeviceSection` in `EditDeviceDialog.kt` passes `macAddress` to both H10 and Verity checks without altering layout or strings.)

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-CON-049.2`) — DIS Serial UUID**: Add `UUID_CHARACTERISTIC_SERIAL_NUMBER` (`00002a25-0000-1000-8000-00805f9b34fb`) in `BluetoothConstants.java`.
2. **Step 2 (`REQ-CON-049.1`, `.2`, `.4`) — `PolarOfflineCapabilityStore` & `PolarOfflineGattCoordinator`**: Add `PolarDeviceType`, independent `hasPmdService`/`hasPftpService`/`modelNumber`/`polarDeviceId` persistence, deterministic precedence (`PolarDeviceType` -> DIS/SDK feature -> legacy fallback only when `UNKNOWN`), `isPolarH10Device(context, ...)`, `extractPolarId(context, ...)`, and alias/MAC resolution in `findMacAddressForPolarId`.
3. **Step 3 (`REQ-CON-049.3`) — Pairing Hydration (`BTSearchForNewDevicesEngine.java`)**: Enqueue `0x2A24` & `0x2A25` reads in `onServicesDiscovered`, persist values in `onCharacteristicRead`, and persist `hasPmdService`/`hasPftpService` in `readNextCharacteristic` without tagging H10 as Verity.
4. **Step 4 (`REQ-CON-049.3`) — Standard HRM Reconnect Hydration (`BTLEHeartRateDevice.java`)**: Override `configureServices(gatt)` to record PMD/PFTP presence and enqueue `0x2A24`/`0x2A25` reads via `MyBTLEDevice.enqueueReadCharacteristic`; persist DIS values & register aliases in `onOtherCharacteristicUpdate()`.
5. **Step 5 (`REQ-CON-049.3`, `.4`) — Polar SDK Bridge Hydration & Pre-Connect Aliases (`PolarOnlineStreamingBridge.kt`)**: Pre-seed `registerDeviceAliases(polarId, macAddress)` at init before `deviceConnected`; hydrate `PolarOfflineCapabilityStore` in `deviceConnected`, `bleSdkFeatureReady`, and both `disInformationReceived` overloads; evaluate `isVeritySense` deterministically for `RR_PRIORITY_PPG` (`DeviceManager.java`) and IMU setup.
6. **Step 6 (`REQ-CON-049.4`) — Controller, Repository & Dialog Callers**: Wire `PolarH10OfflineController` (incl. `syncPendingWorkout`), `PolarVeritySenseOfflineController`, `DeviceDataRepository.isPolarLiveImuBridgeEligible`, and `EditDeviceDialog.PolarOfflineDeviceSection` to pass MAC address into hardware-backed coordinator checks.
7. **Step 7 (`TST-CON-049.1`..`.4`, `REQ-CON-049.5`) — Offline Unit Tests**: Add/extend mocked tests in `PolarOfflineCapabilityStoreTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `BTSearchForNewDevicesEngineTest`, `BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest` (`0` `src/main` reads); run Tier 1 unit tests.

## 6. Verification & Rollback Plan
* **Verification**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.*"` + `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation on `feature/ATT-8717` permits clean revert before Stage 5 squash merge.
