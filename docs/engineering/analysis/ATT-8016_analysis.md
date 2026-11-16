# Stage 1 Analysis: ATT-8016 — Polar Live IMU Streaming Bridge & Arbitration

**Ticket**: [ATT-8016](https://issue-tracker/ATT-8016) (`3 SP`) | **Parent Epic**: `ATT-5910` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8016` | **Author**: `agent1` (`subagent_pro`)  

## 1. Problem Statement & Motivation
- Polar H10 (3-axis `ACC` at 25–200 Hz) and Verity Sense (3-axis `ACC` + `GYRO` at 52 Hz in Blue Sensor Mode) expose real-time PMD streams via `PolarOnlineStreamingApi` (`FEATURE_POLAR_ONLINE_STREAMING`).
- Currently, `DeviceManager` connects all BLE HRMs via `BTLEHeartRateDevice` (`0x180D`), while `PolarOfflineGattCoordinator` opens only transient PFTP `PolarBleApi` sessions at workout start/stop (`ATT-7762`/`ATT-7923`). Live running biomechanics/cadence on Android 13+ requires a single persistent `PolarBleApi` GATT session delivering `HR`/`RR` and `ACC`/`GYRO` without dual-GATT contention or overwriting dedicated footpod/bike cadence sensors.

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Current Architecture Gaps**:
  - `DeviceManager.createNewBluetoothLEDevice()` (`L1230-1277`) unconditionally instantiates `BTLEHeartRateDevice` (`MyBTLEDevice`), leaving no persistent `PolarBleApi` path or `PolarOnlineStreamingApi` integration (`app/build.gradle:244` uses `polar-ble-sdk:8.6.0`).
  - `PolarOfflineGattCoordinator.withGattYieldAndPolarApi()` (`L241-284`) assumes `MyBTLEDevice` owns GATT and tears down `PolarBleApi` (`disconnectFromDevice` + `cleanup`) on exit.
  - `MySensorManager.CADENCE_PRIORITY` (`L68-74`) contains only `[RUN_SPEED, BIKE_CADENCE, BIKE_SPEED_AND_CADENCE, BIKE_POWER]`; `getBestSensor()` (`L501-523`) ignores any `DeviceType` outside that array.
- **Independent Peer Review & Verification (4 Blind Spots Found)**:
  1. *Dual-GATT & Offline Sharing*: If `PolarOnlineStreamingBridge` replaces `BTLEHeartRateDevice` when live IMU is enabled, `PolarH10OfflineController` and `PolarVeritySenseOfflineController` (`TrackerService.java:1244-1253, 2441-2454`) would otherwise open a competing `PolarBleApi` on the same MAC—requiring shared `PolarBleApi` session handoff/reuse and Verity Sense per-channel mutual exclusion (never starting `PolarOfflineRecordingApi` + `PolarOnlineStreamingApi` concurrently on `ACC`).
  2. *Cadence Priority & Sport Gating*: Registering `CADENCE` directly on `DeviceType.HRM` is ignored by `CADENCE_PRIORITY`, whereas adding `HRM` unconditionally to `CADENCE_PRIORITY` would contaminate `BSportType.BIKE` workouts lacking a crank sensor. Bridge `CADENCE`/`STRIDES` registration MUST be gated on `BSportType.RUN` (which includes `TTSportType.WALK`, `SportTypeDatabaseManager.java:675`) and ranked below all dedicated sensors (`RUN_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`).
  3. *Distinct Opt-In Setting*: `Devices.db` v13 `polarH10OfflineRecording` (`DEFAULT 1`) governs offline flash backup (`ATT-7762`/`ATT-7923`); live IMU streaming requires a separate opt-in column (`DEFAULT 0`) in `Devices.db` v14.
  4. *HR/RR Source Parity*: `PolarHrData.PolarHrSample` delivers `hr`, `rrsMs`, and `rrAvailable` (`PolarOnlineStreamingApi.startHrStreaming`), which must feed `SensorType.HR`, `SensorType.RR_INTERVAL`, and `RrBeatSource` -> `HRVMetricsDevice` identically to `BTLEHeartRateDevice` (`L162-275`).

## 3. Scope Grounding & Non-Goals
- **In-Scope**: `Devices.db` v14 opt-in column + `EditDeviceDialog` `@RequiresApi(33)` toggle (9 locales); `PolarOnlineStreamingBridge` persistent single-GATT `PolarBleApi` lifecycle (`HR`/`RR` + `ACC`/`GYRO` with `setAutomaticReconnection(true)`); `DeviceManager` routing; `PolarOfflineGattCoordinator` session coexistence; `BSportType.RUN`-gated `CADENCE`/`STRIDES` arbitration yielding to dedicated footpod/bike sensors.
- **Out-of-Scope**: Advanced Running Dynamics DSP (GCT, vertical oscillation, stride Kalman); legacy `< API 33` support; `ANT-Android-SDKs` submodule edits; `WorkoutRr.db` schema changes.
- **Stage 4 Risk Classification (`Rule 7.1`)**: **HIGH-RISK** — Binary BLE/Polar PMD protocol, persistent GATT arbitration, `DeviceManager`/`MySensorManager` core infrastructure, and `Devices.db` v14 migration require `subagent_pro` for Stage 4 Doing and `subagent_flash` for Gate 4 review.

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-SET-104`/`108` (`PolarOfflineGattCoordinator.kt`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`), `REQ-TRK-069.3` (`BTLEHeartRateDevice.java`, `RrBeatSource`), `REQ-SEN-021` (`MySensorManager.java`).
- **Historical Origin & Commit Trace**: `ATT-7762` (`Devices.db` v13 `polarH10OfflineRecording`), `ATT-7923` (`PolarVeritySenseOfflineController`), `ATT-3540`/`ATT-4520` (`BTLEHeartRateDevice` `RrBeatSource`, `MySensorManager.CADENCE_PRIORITY`).
- **Root Reason for Existing Formulation**: Transient PFTP yield preserved `0x180D` compatibility for all standard HRMs; `CADENCE_PRIORITY` ensured dedicated footpods (`RUN_SPEED`) and bike crank sensors always preempt fallback sources.
- **Preservation of Core Invariants**: Standard `BTLEHeartRateDevice` path stays unchanged when live IMU is disabled or API < 33; dedicated `ANTRunSpeedDevice`/`BTLERunSpeedDevice` and `BIKE_*` cadence sensors retain top `CADENCE_PRIORITY`; `PolarVeritySenseOfflineController` still never writes to `WorkoutRr.db`.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-SET-112` (Polar live IMU streaming bridge & arbitration); preserves `REQ-SET-104`, `REQ-SET-108`, `REQ-TRK-069`, `REQ-SEN-021`.
- **Affected Architecture Elements**: `DevicesDatabaseManager.java` (`DB_VERSION = 14`), `RawDeviceDataProvider.kt`, `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `values*/strings_devices.xml` (9 locales), `DeviceManager.java`, `MySensorManager.java`, `PolarOfflineGattCoordinator.kt`, new `PolarOnlineStreamingBridge.kt`, `docs/architecture.md`.
- **Verification Impact & Test Surface**: Targeted Robolectric/MockK unit tests for `DevicesDatabaseManagerMigrationV14Test`, `EditDevicePolarLiveImuTest`, `PolarOnlineStreamingBridgeTest` (HR/RR dispatch, ACC/GYRO streaming, Verity ACC exclusivity, `BSportType.RUN` gating), `MySensorManager` cadence arbitration test, `TranslationParityTest`.
- **Risk Assessment & Invariants**: Dual-GATT race on reconnect, PMD `ACC` conflict on Verity Sense, and bike cadence contamination mitigated via single-owner `PolarBleApi` session registry, per-channel lock, and `BSportType.RUN`-only sensor registration.

## 6. System Invariants & Risk Assessment
- **Core Invariants**: `DatabaseSchemaRegistry` tracks `DevicesDbHelper.DB_VERSION`; `onCreate`/`onUpgrade` v13->v14 parity with `DEFAULT 0`; `BIKE_CADENCE`/`BIKE_POWER`/`RUN_SPEED` always outrank IMU cadence; API < 33 isolated; `ANT-Android-SDKs` stays `160000`.
- **Risk Rating**: **HIGH** (Binary BLE PMD streaming, GATT ownership handoff, SQLite v14 migration, `DeviceManager`/`MySensorManager` arbitration).
