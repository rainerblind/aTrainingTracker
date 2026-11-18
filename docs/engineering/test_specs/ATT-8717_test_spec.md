# Stage 2: Requirement & Test Specification - ATT-8717

**Ticket**: [ATT-8717](https://rainerblind.atlassian.net/browse/ATT-8717) (`3 SP`) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8717`  
**Requirement Mapping**: `REQ-CON-049` (refines `REQ-SET-104.3`, `REQ-SET-108.1`, `REQ-SET-112.1/.2`, `REQ-SET-118.2`) | **Test Spec ID**: `TST-CON-049` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-CON-049` `[Planned]`)
* **Attributes**: Priority: `Medium` | Type: `Functional` | Verification Method: `Unit`
* **Implementation**: `BluetoothConstants.java`, `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `BTSearchForNewDevicesEngine.java`, `MyBTLEDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `DeviceDataRepository.kt`, `EditDeviceDialog.kt`
* **`REQ-CON-049.1` (BLE GATT Service UUID Capability Separation)**: `PolarOfflineCapabilityStore` SHALL persist `hasPmdService` (`UUID_SERVICE_POLAR_PMD`, `fb005c80-02e7-f387-1cad-8acd2d8df0c8`) and `hasPftpService` (`UUID_SERVICE_POLAR_PFTP`, `0000feee-0000-1000-8000-00805f9b34fb`) independently from legacy `offline_rec_<MAC>` per normalized MAC address, ensuring Polar H10 PFTP presence never implies Verity Sense classification.
* **`REQ-CON-049.2` (DIS `0x180A` Hardware Metadata Persistence Without DB v15)**: `BluetoothConstants` SHALL define `UUID_CHARACTERISTIC_SERIAL_NUMBER` (`0x2A25`) alongside `UUID_CHARACTERISTIC_MODEL_NUMBER` (`0x2A24`). `PolarOfflineCapabilityStore` SHALL persist hardware `modelNumber` (`0x2A24`), canonical `polarDeviceId` (`0x2A25` / `PolarDeviceInfo.deviceId`), and derived `PolarDeviceType` (`H10`, `VERITY_SENSE`, `UNKNOWN`) in SharedPreferences + `ConcurrentHashMap` without bumping `DevicesDatabaseManager.DB_VERSION` (`14`).
* **`REQ-CON-049.3` (Three-Path DIS & Capability Hydration)**: The system SHALL capture and persist PMD/PFTP service UUIDs and DIS `0x2A24`/`0x2A25` metadata during: (a) pairing in `BTSearchForNewDevicesEngine`, (b) standard BLE HRM connect/reconnect in `MyBTLEDevice`/`BTLEHeartRateDevice`, and (c) Polar SDK sessions in `PolarOnlineStreamingBridge.sdkCallback` (`disInformationReceived`, `deviceConnected`, `bleSdkFeatureReady`).
* **`REQ-CON-049.4` (Deterministic Classification, Pre-Connect Alias Seeding & Legacy Fallback)**: `PolarOfflineGattCoordinator` SHALL classify Polar models by persisted `PolarDeviceType`/DIS `0x2A24`/SDK feature (`FEATURE_POLAR_H10_EXERCISE_RECORDING` vs. `FEATURE_POLAR_OFFLINE_RECORDING`) first—immune to user display-name edits—and resolve canonical `polarDeviceId` (`0x2A25`/`PolarDeviceInfo.deviceId` -> `POLAR_ID_REGEX` -> `macAddress`). Callers (`PolarOnlineStreamingBridge`, `PolarH10OfflineController` incl. `syncPendingWorkout`, `PolarVeritySenseOfflineController`, `DeviceDataRepository`, `EditDeviceDialog`) MUST route via coordinator hardware-backed checks and seed `registerDeviceAliases(polarDeviceId, mac)` BEFORE `deviceConnected` (`ATT-8690` & `ATT-8706` `RR_PRIORITY_PPG` parity). Legacy name/regex fallback SHALL execute strictly when `PolarDeviceType == UNKNOWN` (un-reconnected rows) until first reconnect.
* **`REQ-CON-049.5` (Localization & Offline Verification Hygiene)**: `N/A - no UI strings` (`values*/strings*.xml` untouched); all unit/Robolectric tests MUST run strictly offline (`Rule 5`) with `0` `app/src/main` source reads (`REQ-PRO-049.1`).
* **Given-When-Then**:
  * *Given* a Polar H10 renamed `"Chest Strap"` with persisted PMD+PFTP UUIDs and DIS `0x2A24="H10"` + `0x2A25="0A1B2C3D"`, *When* evaluated by `PolarOfflineGattCoordinator`, controllers, `DeviceDataRepository`, `EditDeviceDialog`, and `PolarOnlineStreamingBridge`, *Then* it classifies as `H10` (never Verity Sense), uses `0A1B2C3D` with pre-connect alias registration, and keeps `RR_PRIORITY_ECG`.
  * *Given* a Polar Verity Sense / OH1 renamed `"Armband"` with persisted DIS `0x2A24="Verity Sense"` (or `FEATURE_POLAR_OFFLINE_RECORDING`), *When* initialized before `deviceConnected`, *Then* `isVeritySense == true` (`RR_PRIORITY_PPG`), `registerDeviceAliases(polarDeviceId, mac)` is seeded pre-connect, and offline backup/IMU routing succeeds.
  * *Given* a legacy paired row with `PolarDeviceType == UNKNOWN`, *When* evaluated before first reconnect, *Then* legacy name/regex fallback preserves behavior until first reconnect persists DIS/UUID metadata.
* **Chesterton's Fence Audit (`REQ-PRO-022`)**: Refines `REQ-SET-104.3` (`53b53e87`), `REQ-SET-108.1` (`e7201bdb`), `REQ-SET-112.1/.2` (`f4bef44c`), `REQ-SET-118.2` (`34b78941`); preserves `Devices.db` v14 (`DevicesDatabaseManagerMigrationV14Test`), `ATT-8690` singleton/multiplexing, `RR_PRIORITY_ECG`/`RR_PRIORITY_PPG`, and un-reconnected row fallback.

---

## 2. Test Specification (`TST-CON-049`, Offline Mocked `Rule 5` & `REQ-PRO-049.1`)
* **`TST-CON-049.1` (`PolarOfflineCapabilityStoreTest` / `PolarOfflineGattCoordinatorCoexistenceTest`)**: Verify independent `hasPmdService`, `hasPftpService`, `modelNumber`, `polarDeviceId`, and `PolarDeviceType` persistence + reset, proving H10 with PFTP+`H10` never misclassifies as Verity Sense and legacy fallback yields once hardware metadata is stored.
* **`TST-CON-049.2` (`BTSearchForNewDevicesEngineTest`, `BTLEHeartRateDeviceTest`)**: Verify pairing and standard BLE HRM reconnect enqueue/read DIS `0x2A24` & `0x2A25` and persist `UUID_SERVICE_POLAR_PMD` / `UUID_SERVICE_POLAR_PFTP` into `PolarOfflineCapabilityStore` without blocking non-Polar HRM discovery.
* **`TST-CON-049.3` (`PolarOnlineStreamingBridgeTest`)**: Verify `sdkCallback.disInformationReceived` (both `UUID`/`String` and `DisInfo`) + `bleSdkFeatureReady` hydrate DIS/feature capability store, `polarId` prefers persisted `0x2A25`, `registerDeviceAliases(polarDeviceId, mac)` is seeded BEFORE `deviceConnected`, and renamed Verity Sense sets `isVeritySense == true` (`RR_PRIORITY_PPG`).
* **`TST-CON-049.4` (`PolarH10OfflineControllerTest`, `PolarVeritySenseOfflineControllerTest`, `EditDevicePolarBackupTest`, `EditDevicePolarLiveImuTest`)**: Verify renamed H10 (`"Chest Strap"`) and renamed Verity Sense/OH1 (`"Armband"`) classify deterministically in controllers, `syncPendingWorkout`, `DeviceDataRepository.isPolarLiveImuBridgeEligible`, and `EditDeviceDialog` (`N/A - no UI strings`; `0` `src/main` source reads per `REQ-PRO-049.1`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-049.1` | Unit (Offline) | `PolarOfflineCapabilityStore`, `PolarOfflineGattCoordinator` | `REQ-CON-049.1`, `.2`, `.4` | Proposed |
| `TST-CON-049.2` | Unit (Mocked GATT) | `BTSearchForNewDevicesEngine`, `BTLEHeartRateDevice` | `REQ-CON-049.2`, `.3` | Proposed |
| `TST-CON-049.3` | Unit (Mocked SDK) | `PolarOnlineStreamingBridge` | `REQ-CON-049.3`, `.4` | Proposed |
| `TST-CON-049.4` | Unit / Robolectric | Controllers, `DeviceDataRepository`, `EditDeviceDialog` | `REQ-CON-049.4`, `.5`, `REQ-PRO-049.1` | Proposed |
