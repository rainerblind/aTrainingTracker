# Stage 1 Analysis: ATT-7923

Target Release: V5.0.0

## Problem Domain & Forensic RCA
- **Gap 1 (No Optical Offline Recording Controller)**: `PolarH10OfflineController.kt` exclusively supports `FEATURE_POLAR_H10_EXERCISE_RECORDING` (`PolarExerciseApi`), leaving Polar Verity Sense / optical sensors that implement `FEATURE_POLAR_OFFLINE_RECORDING` (`PolarOfflineRecordingApi`) without internal backup recording during pool swims or BLE dropouts.
- **Gap 2 (SDK 8.6.0 API Differences)**: Bytecode inspection of `polar-ble-sdk-8.6.0-api.jar` shows `PolarOfflineRecordingApi` uses `PolarBleApi.PolarDeviceDataType.HR` (not `PolarDataType.HR`), `getOfflineRecordingStatus(id)` returning `List<PolarDeviceDataType>`, and `PolarOfflineRecordingData.HrOfflineRecording` (`data: PolarHrData` with `PolarHrSample` list) rather than `HrOfflineData`. Errors like `SYSTEM_BUSY` arrive via `BlePsFtpUtils.PftpResponseError` (`getErrorCode() == PbPFtpError.SYSTEM_BUSY`).
- **Gap 3 (Device Recognition & EditDeviceDialog UI)**: `EditDeviceDialog.kt` only exposes the `@RequiresApi(33)` backup toggle for `isPolarH10Device(data)`, with H10-specific HR + HRV copy across 9 locales (`strings_devices.xml`). Polar Verity Sense needs optical HR-only copy and capability/name recognition.

## Independent Peer Review & Verification
- Verified `DevicesDatabaseManager.java` (`DB_VERSION = 13`, `POLAR_H10_OFFLINE_RECORDING = "polarH10OfflineRecording" DEFAULT 1`) and `DevicesDatabaseManagerMigrationV13Test.kt` asserting `DB_VERSION == 13`. Reusing `POLAR_H10_OFFLINE_RECORDING` as the device's offline-backup preference avoids a redundant SQLite version bump while keeping default-ON semantics on API 33+.
- Verified `MyBTLEDevice.java` (`pauseGattOperations()`, `resumeGattOperations()`), `BTSearchForNewDevicesEngine.java` (`onNewDeviceFound`), and `BlePsFtpUtils.class` (`0000FEEE-0000-1000-8000-00805f9b34fb` PFTP UUID).
- Verified `TrackerService.java` lines 1207–1211, 1398–1401, and 1619–1622 lifecycle hooks for `PolarH10OfflineController`.

## Scope Bounding & Complexity
- **Story Points**: `3 SP` (confirmed appropriate: shared GATT coordinator extraction, `PolarVeritySenseOfflineController`, `TrackerService` hook, capability/device detection, `EditDeviceDialog` + 9 locales, tests).
- **In Scope**: Shared GATT arbitration/device-lookup helper (`PolarOfflineGattCoordinator`), `PolarVeritySenseOfflineController` (`onWorkoutStarted`, `onWorkoutStopped`, `stopAndFetchOfflineHr`, `removeOfflineRecord`, `SYSTEM_BUSY` handling), Polar Verity Sense / `FEATURE_POLAR_OFFLINE_RECORDING` detection, `EditDeviceDialog` toggle + 9-locale HR-only copy, surgical `TrackerService.java` hook, unit/UI tests.
- **Out of Scope**: Post-workout Verity Sense HR backfill into `WorkoutSamplesDatabaseManager` and workout detail sync UI (`ATT-7924` consumes the exposed `stopAndFetchOfflineHr` / `removeOfflineRecord` coroutines).

## System Invariants
- **Mid-Workout Fringe Flap Invariant**: Arm `PolarVeritySenseOfflineController` once per active workout via `isArmed` CAS; mid-workout `0x180D` BLE disconnects/reconnects NEVER stop, restart, or re-query PFTP offline recording.
- **Android 13+ Isolation**: Offline controller stays a no-op below `Build.VERSION_CODES.TIRAMISU`; `EditDeviceDialog` shows the toggle disabled with Android 13 hint below API 33 and checked by default on API 33+.
- **Non-Blocking Workout Start**: `SYSTEM_BUSY` or GATT failures log/surface cleanly without throwing or delaying live `0x180D` HR streaming (`finally { btleDevice?.resumeGattOperations() }`).
- **Schema Preservation**: `Devices.db` stays at `DB_VERSION = 13`.

## Change Impact Analysis
- **Affected Requirements**: Adds `REQ-SET-108` (`docs/requirements/req_settings_profiles.md`) and `TST-SET-110` (`docs/tests/tst_settings_profiles.md`).
- **Affected Architecture Elements**: `PolarOfflineGattCoordinator` (new shared helper), `PolarH10OfflineController`, `PolarVeritySenseOfflineController` (new), `BTSearchForNewDevicesEngine` / `BluetoothConstants` / Polar capability store, `TrackerService.java`, `EditDeviceDialog.kt`, `res/values*/strings_devices.xml` (9 locales), `docs/architecture.md`.
- **Verification Impact & Test Surface**: Add `PolarVeritySenseOfflineControllerTest.kt`, update/add `EditDevicePolarBackupTest.kt`, run `PolarH10OfflineControllerTest`, `DevicesDatabaseManagerMigrationV13Test`, `TrackerServiceStateMachineCharacterisationTest`, `TranslationParityTest`, `:app:lintDebug`, Detekt, and capture `EditDeviceDialog` Verity Sense screenshot on `emulator-5554`.
- **Risk Assessment & Invariants**: `MEDIUM` risk — shared GATT yield between `PolarBleApi` (PFTP) and `MyBTLEDevice` (`0x180D`); mitigated by extracting `PolarOfflineGattCoordinator` with `finally` GATT resume and SDK mocks in unit tests.
