# Stage 5 Walkthrough - ATT-8016: Polar Live IMU Streaming Bridge & Arbitration

**Ticket**: [ATT-8016](https://rainerblind.atlassian.net/browse/ATT-8016) (`3 SP`) | **Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.1`  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8016` | **Stage 4 Commit**: `b79d5f00`  
**Requirements**: `REQ-SET-112` (`REQ-SET-112.1`..`REQ-SET-112.4`) | **Test Spec**: `TST-SET-114` (`TST-SET-114.1`..`TST-SET-114.5`)  
**Author**: AI Agent 2 (`Lane 3 Flash`, Stage 5 Verification)

---

## 1. Implementation Summary
- **Devices.db v14 Opt-In & `@RequiresApi(33)` UI (`REQ-SET-112.1`)**: Bumped `DevicesDbHelper.DB_VERSION` `13 -> 14` with idempotent `polarLiveImuEnabled` (`DEFAULT 0`) in `DevicesDatabaseManager`, tracked by `DatabaseSchemaRegistry`; extended `DeviceData`, `RawDeviceDataProvider`, `DeviceDataRepository`, `EditDeviceViewModel`, and `EditDeviceDialog` (`PolarLiveImuCard` / `PolarFeatureToggleCard`) with 9-locale `strings_devices.xml` parity.
- **Persistent Single-GATT Bridge (`REQ-SET-112.2`)**: Added `PolarOnlineStreamingBridge` (`PolarBleApi` with `setAutomaticReconnection(true)`) delivering live `HR`, `RR_INTERVAL`, and `RrBeatSource` beats alongside `ACC` (50/100/200 Hz H10; 52 Hz Verity Sense) and `GYRO` (52 Hz Verity Blue Mode); routed enabled Android 13+ devices in `DeviceManager.createNewBluetoothLEDevice()` while preserving `BTLEHeartRateDevice` fallback when disabled or `< API 33`.
- **Offline Coexistence & `ACC` Mutual Exclusion (`REQ-SET-112.3`)**: Added active `PolarBleApi` session registry and per-channel `ACC` ownership lock in `PolarOfflineGattCoordinator`, reused by `PolarH10OfflineController` and `PolarVeritySenseOfflineController` (`startOfflineRecordingGuarded()`).
- **`BSportType.RUN` Gating & Cadence Arbitration (`REQ-SET-112.4`)**: Added `BANALService.getActiveBSportType()`, gated `PolarOnlineStreamingBridge` `CADENCE`/`STRIDES` registration strictly on `BSportType.RUN` (including `WALK`), and appended `DeviceType.HRM` at lowest rank in `MySensorManager.CADENCE_PRIORITY` behind `RUN_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, and `BIKE_POWER`.
- **Architecture Docs**: Updated `docs/architecture.md` Section 3.4 (`REQ-SET-112`).

---

## 2. Verification & Visual Evidence (`Rule 7.5`, `Rule 7.8`, `Rule 8.5`)
- **Stage 4 Targeted Unit & Regression Suite (reused at unchanged code SHA `b79d5f00` per `Rule 7.5`)**:
  - `DevicesDatabaseManagerMigrationV14Test`, `DevicesDatabaseManagerMigrationV13Test`, `DevicesDatabaseManagerTest`, `EditDevicePolarLiveImuTest`, `PolarOnlineStreamingBridgeTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `MySensorManagerCadenceArbitrationTest`: **98/98 PASS** (0 failures, 0 errors, 0 skipped).
  - `TranslationParityTest` across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`): **10/10 PASS** (zero missing keys, zero emojis).
- **Visual UI Inspection (`Rule 7.8`, `Rule 8.5`)**:
  - Inspected `docs/engineering/walkthroughs/img/ATT-8016_polar_live_imu_toggle.png` via `read_file`: verifies `Polar H10 (Android 13+)` (`Polar H10 Backup Recording` + `Live IMU Streaming (ACC/GYRO)` enabled toggles) and `Polar Verity Sense (< Android 13)` (disabled toggle with `"Requires Android 13 or newer."` hint and Blue Sensor Mode description), clean M3 card spacing, zero text clipping or wrapping defects.

---

## 3. Traceability & Governance Matrix

| Spec ID | Scope | Evidence | Status |
| :--- | :--- | :--- | :--- |
| `TST-SET-114.1` | `Devices.db` v13->v14 + Registry | `DevicesDatabaseManagerMigrationV14Test`, `V13Test` | Verified |
| `TST-SET-114.2` | `EditDeviceDialog` + 9-Locale + PNG | `EditDevicePolarLiveImuTest`, `TranslationParityTest` (10/10), PNG | Verified |
| `TST-SET-114.3` | Persistent `PolarBleApi` HR/RR + IMU | `PolarOnlineStreamingBridgeTest` | Verified |
| `TST-SET-114.4` | Session Reuse & `ACC` Mutual Exclusion | `PolarOfflineGattCoordinatorCoexistenceTest` | Verified |
| `TST-SET-114.5` | `BSportType.RUN` & `CADENCE_PRIORITY` | `MySensorManagerCadenceArbitrationTest` | Verified |
