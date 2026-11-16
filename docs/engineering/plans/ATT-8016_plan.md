# Stage 3: Implementation Plan - ATT-8016: Polar Live IMU Streaming Bridge & Arbitration

**Ticket**: [ATT-8016](https://issue-tracker/ATT-8016) (`3 SP`) | **Epic**: `ATT-5910` | **Sprint**: `2026-47.1`  
**Target Release**: `V5.0.0` | **Req**: `REQ-SET-112` | **Test**: `TST-SET-114` | **Branch**: `feature/ATT-8016`  
**Author**: `agent1` (`subagent_pro`) | **Stage 4 Risk**: **HIGH-RISK** (`subagent_pro` Doing / `subagent_flash` Gate 4 per `Rule 7.1`)  

## 1. Problem Description & Traceability
- Polar H10 (`ACC`) and Verity Sense (`ACC`+`GYRO` in Blue Sensor Mode) require a persistent single-GATT `PolarBleApi` session on Android 13+ delivering `HR`/`RR` and live IMU streams without dual-GATT disconnects, offline PFTP conflicts, or cycling/footpod cadence hijacking.
- Traceability: `REQ-SET-112.1`->`TST-SET-114.1/.2`; `REQ-SET-112.2`->`TST-SET-114.3`; `REQ-SET-112.3`->`TST-SET-114.4`; `REQ-SET-112.4`->`TST-SET-114.5`.

## 2. System Invariants & UI Consistency
1. **Standard HRM Fallback**: Opt-in `DEFAULT 0` and `< API 33` keep `BTLEHeartRateDevice` (`0x180D`) untouched.
2. **Database & Registry Parity**: `DevicesDbHelper.DB_VERSION` `13 -> 14` (`onCreate`/`onUpgrade` parity, `DEFAULT 0`); `DatabaseSchemaRegistry` references `DB_VERSION` directly; existing v13 tests updated from hardcoded `13` to `>= 13` / `DB_VERSION`.
3. **Verity Sense Single-Owner `ACC` Lock & HRV Integrity**: Never start `PolarOfflineRecordingApi` + `PolarOnlineStreamingApi` concurrently on `ACC`; zero optical PPG writes to `WorkoutRr.db`.
4. **Cadence Ownership**: `RUN_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, and `BIKE_POWER` always outrank Polar IMU cadence; `BSportType.BIKE` receives zero IMU cadence.
5. **UI Consistency (`docs/design_guidelines.md`)**: `EditDeviceDialog.kt` reuses `PolarBackupRecordingCard`/`AttFusedToggleRow` pattern (no `FilterChip`, no raw unscaled `Switch` in Expert tuning, zero emojis across 9 locales); Stage 4 captures & inspects PNG via `read_file` (`Rule 8.5`).

## 3. Proposed Architectural Changes
- **Persistence & UI (`REQ-SET-112.1`)**: `DevicesDatabaseManager` (`DB_VERSION = 14`, `polarLiveImuEnabled`), `DeviceData.kt`, `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt` (`@RequiresApi(33)` toggle for H10 & Verity Sense), `values*/strings_devices.xml` (9 locales).
- **Persistent Bridge & GATT Arbitration (`REQ-SET-112.2/.3`)**: New `PolarOnlineStreamingBridge.kt` (`MyRemoteDevice`, `RrBeatSource`, `@RequiresApi(33)` `PolarBleApi` with `setAutomaticReconnection(true)`, `startHrStreaming`, `startAccStreaming`, `startGyroStreaming`, step-cadence extraction); `PolarOfflineGattCoordinator.kt` active-session registry & per-device `ACC` mutex reused by `PolarH10OfflineController` / `PolarVeritySenseOfflineController`.
- **Routing & Cadence Priority (`REQ-SET-112.4`)**: `DeviceManager.createNewBluetoothLEDevice()` routes enabled API 33+ Polar HRM MACs to `PolarOnlineStreamingBridge`; `MySensorManager.CADENCE_PRIORITY` appends `DeviceType.HRM` at lowest rank (`[RUN_SPEED, BIKE_CADENCE, BIKE_SPEED_AND_CADENCE, BIKE_POWER, HRM]`) while bridge gates `CADENCE`/`STRIDES` registration on `BSportType.RUN` (incl. `TTSportType.WALK`).

## 4. Atomic Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`Devices.db` v14 + Compatibility Audit)**: Bump `DevicesDbHelper.DB_VERSION` `13 -> 14`, add `polarLiveImuEnabled` (`DEFAULT 0`) in `onCreate`/`onUpgrade` + getters/setters; update `DevicesDatabaseManagerTest.kt` (`L453`) & `DevicesDatabaseManagerMigrationV13Test.kt` (`L59,L66`); add `DevicesDatabaseManagerMigrationV14Test.kt` (`// TST-SET-114.1`).
2. **Step 2 (Repository, ViewModel, UI & 9-Locale Strings)**: Extend `DeviceRawData`/`DeviceUiData` (defaulted params), `RawDeviceDataProvider`, `DeviceDataRepository`, `EditDeviceViewModel`, `EditDeviceDialog` (`PolarLiveImuCard` inside `PolarOfflineDeviceSection`), and `values*/strings_devices.xml` (9 locales); add `EditDevicePolarLiveImuTest.kt` (`// TST-SET-114.2`) + capture/inspect Stage 4 Compose PNG (`Rule 8.5`).
3. **Step 3 (`PolarOfflineGattCoordinator` Session Reuse & `ACC` Mutex)**: Add active `PolarBleApi` session registry and per-channel `ACC` exclusivity lock in `PolarOfflineGattCoordinator.kt`, integrating `PolarH10OfflineController` and `PolarVeritySenseOfflineController`; add `PolarOfflineGattCoordinatorCoexistenceTest.kt` (`// TST-SET-114.4`).
4. **Step 4 (`PolarOnlineStreamingBridge` + `DeviceManager` Routing)**: Implement `PolarOnlineStreamingBridge.kt` (`HR`/`RR` -> `RrBeatSource`, `ACC` 50/100/200/52 Hz, `GYRO` 52 Hz, auto-reconnect, IMU cadence/strides hook) and wire `DeviceManager.createNewBluetoothLEDevice()`; add `PolarOnlineStreamingBridgeTest.kt` (`// TST-SET-114.3`).
5. **Step 5 (`MySensorManager` Cadence Arbitration & SWE.2 Docs)**: Append `DeviceType.HRM` at lowest rank in `MySensorManager.CADENCE_PRIORITY` and enforce `BSportType.RUN` (`RUN` + `WALK`) gating in `PolarOnlineStreamingBridge`; add `MySensorManagerCadenceArbitrationTest.kt` (`// TST-SET-114.5`) and update `docs/architecture.md` (`Rule 8.8`).

## 5. Verification & Rollback Plan
- **Tier 1 Command** *(all new Kotlin tests carry `// TST-SET-114.*` tags per `ATT-8143`)*:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerMigrationV14Test" --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerMigrationV13Test" --tests "com.atrainingtracker.banalservice.database.DevicesDatabaseManagerTest" --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDevicePolarLiveImuTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridgeTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinatorCoexistenceTest" --tests "com.atrainingtracker.banalservice.sensor.MySensorManagerCadenceArbitrationTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
- **Rollback**: Isolated on `feature/ATT-8016`; `Devices.db` v14 `DEFAULT 0` preserves existing devices.
