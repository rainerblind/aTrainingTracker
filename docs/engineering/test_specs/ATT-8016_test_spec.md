# Stage 2: Requirement & Test Specification - ATT-8016: Polar Live IMU Streaming Bridge & Arbitration

**Ticket**: [ATT-8016](https://rainerblind.atlassian.net/browse/ATT-8016) (`3 SP`) | **Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-SET-112` (`REQ-SET-112.1`..`REQ-SET-112.4`) | **Test Spec ID**: `TST-SET-114` (`TST-SET-114.1`..`TST-SET-114.5`)  
**Author**: AI Agent 2 (`Lane 3 Flash`, fused Stage 2) | **Stage 4 Mandate**: **HIGH-RISK** (`subagent_pro` Doing / `subagent_flash` Gate 4 per `Rule 7.1`)

---

## 1. Requirement Specification (`REQ-SET-112`)

- **Priority**: High | **Type**: Functional | **Verification Method**: Unit / Integration / On-Device Manual
- **Implementation**: `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `RawDeviceDataProvider.kt`, `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `res/values*/strings_devices.xml`

### 1.1 Normative Clauses
1. **Devices.db v14 Opt-In & `@RequiresApi(33)` UI Toggle (`REQ-SET-112.1`)**: `DevicesDbHelper.DB_VERSION` SHALL bump `13 -> 14` adding an opt-in live IMU column (`DEFAULT 0`) in `onCreate`/`onUpgrade`, tracked by `DatabaseSchemaRegistry`. `EditDeviceDialog` SHALL expose the toggle for Polar H10 and Verity Sense (API 33+ enabled, `< API 33` disabled with hint) via `DeviceDataRepository`, with 9-locale parity in `strings_devices.xml` and Stage 4 screenshot verification (`Rule 8.5`).
2. **Persistent Single-GATT Session & Live HR/RR + ACC/GYRO (`REQ-SET-112.2`)**: When live IMU is enabled on Android 13+, `DeviceManager` SHALL instantiate `PolarOnlineStreamingBridge` instead of `BTLEHeartRateDevice` on that MAC (`setAutomaticReconnection(true)`). `PolarOnlineStreamingApi` SHALL feed `PolarHrData` (`hr`, `rrsMs`, `rrAvailable`) into `SensorType.HR`, `SensorType.RR_INTERVAL`, and `RrBeatSource`, while streaming `ACC` (H10: 50/100/200 Hz; Verity: 52 Hz) and `GYRO` (Verity Blue Mode: 52 Hz).
3. **Offline Session Coexistence & `ACC` Mutual Exclusion (`REQ-SET-112.3`)**: `PolarOfflineGattCoordinator` SHALL reuse/hand off the active `PolarBleApi` session to `PolarH10OfflineController` / `PolarVeritySenseOfflineController` without dual-GATT clients, and MUST enforce per-channel `ACC` mutual exclusion on Verity Sense (never running `PolarOfflineRecordingApi` + `PolarOnlineStreamingApi` concurrently on `ACC`).
4. **`BSportType.RUN` Gating & Cadence Priority (`REQ-SET-112.4`)**: IMU-derived `SensorType.CADENCE` / `SensorType.STRIDES` SHALL register/publish only for `BSportType.RUN` (including `TTSportType.WALK`), never for `BSportType.BIKE`. `MySensorManager.CADENCE_PRIORITY` SHALL keep `RUN_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, and `BIKE_POWER` strictly above Polar IMU cadence.

### 1.2 Acceptance Criteria & Invariants
- *Given* Polar H10 or Verity Sense on Android 13+ with live IMU enabled during `BSportType.RUN`, *When* tracking, *Then* one persistent `PolarBleApi` delivers 1-Hz HR (+ RR on H10) and `ACC` (+ `GYRO` on Verity) with zero dual-GATT or `ACC` PFTP conflicts.
- *Given* an active dedicated ANT+/BLE footpod (`RUN_SPEED`) or a `BSportType.BIKE` workout, *When* tracking, *Then* dedicated footpod/bike sensors retain exclusive `SensorType.CADENCE` ownership and `BSportType.BIKE` receives zero Polar IMU cadence.
- **Invariants**: `BTLEHeartRateDevice` fallback unchanged (`opt-in=0` or `< API 33`); `Devices.db` v14 `DEFAULT 0`; `WorkoutRr.db` zero optical PPG writes; `HIGH-RISK` Stage 4 (`subagent_pro` Doing / `subagent_flash` Gate 4, `Rule 7.1`).

---

## 2. Test Specification (`TST-SET-114`)

- **TST-SET-114.1 (`DevicesDatabaseManagerMigrationV14Test`)**: Verify `DevicesDbHelper.DB_VERSION == 14`, `onUpgrade(13, 14)` + `onCreate` parity with `DEFAULT 0`, and `DatabaseSchemaRegistry` max version `14`.
- **TST-SET-114.2 (`EditDevicePolarLiveImuTest`, `TranslationParityTest`, `Rule 8.5` Screenshot)**: Verify `@RequiresApi(33)` toggle visibility/disablement on Polar H10/Verity Sense, repository persistence, 9-locale parity (zero emojis), and Stage 4 PNG visual check.
- **TST-SET-114.3 (`PolarOnlineStreamingBridgeTest`)**: Verify `DeviceManager` routing vs `BTLEHeartRateDevice` fallback, `PolarHrData` -> `HR`/`RR_INTERVAL`/`RrBeatSource`, and `ACC`/`GYRO` streaming with auto-reconnect.
- **TST-SET-114.4 (`PolarOfflineGattCoordinator` Coexistence Test)**: Verify active `PolarBleApi` session reuse for PFTP offline recording and per-channel `ACC` mutual exclusion on Verity Sense.
- **TST-SET-114.5 (`MySensorManager` Cadence Arbitration Test)**: Verify `BSportType.RUN` (`RUN` + `WALK`) gating, zero cadence in `BSportType.BIKE`, and automatic yield to `RUN_SPEED`/`BIKE_CADENCE`/`BIKE_SPEED_AND_CADENCE`/`BIKE_POWER`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-114.1` | SQLite / Migration | `DevicesDatabaseManager`, `DatabaseSchemaRegistry` | `REQ-SET-112.1` | Planned |
| `TST-SET-114.2` | UI / L10n / Visual | `EditDeviceDialog`, `DeviceDataRepository`, `strings_devices.xml` | `REQ-SET-112.1` | Planned |
| `TST-SET-114.3` | BLE / PMD Bridge | `PolarOnlineStreamingBridge`, `DeviceManager` | `REQ-SET-112.2` | Planned |
| `TST-SET-114.4` | GATT / Channel Lock | `PolarOfflineGattCoordinator`, `PolarVeritySenseOfflineController` | `REQ-SET-112.3` | Planned |
| `TST-SET-114.5` | Arbitration / Gating | `MySensorManager`, `PolarOnlineStreamingBridge` | `REQ-SET-112.4` | Planned |
