# Stage 2 Requirement & Test Specification: ATT-8991 - Capability-Gated HRM Sources & BIKE_POWER Slope/VAM Removal

**Ticket**: [ATT-8991](https://rainerblind.atlassian.net/browse/ATT-8991) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Target Release**: `V5.0.0`  
**Requirement Mapping**: `REQ-CON-057` (`REQ-CON-057.1`..`REQ-CON-057.4`; Priority: `High`, Type: `Functional`, Verification Method: `Unit, Integration`, System Requirement: `SYSREQ-TRK-001`)  
**Test Mapping**: `TST-CON-057` (`TST-CON-057.1`..`TST-CON-057.4`) — authored in `docs/requirements/req_tracking_cockpit_part3.md` (`v1.0.18`) & `docs/tests/tst_tracking_cockpit_part2.md` (`v1.0.20`)  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Requirement Specification (`REQ-CON-057`) & Given-When-Then Acceptance Criteria
1. **`REQ-CON-057.1` — `DeviceType.getDeviceTypeList()` Polar HRM Live-IMU & Slope/VAM Cleanup**:
   - *Normative*: `DeviceType.getDeviceTypeList(SensorType)` SHALL include `DeviceType.HRM` for `SensorType.CADENCE` and `SensorType.STRIDES` (preserving `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, `RUN_SPEED`, `SPEED_AND_LOCATION_ATT_FUSED`), retain `DeviceType.HRM` for `SensorType.VERTICAL_OSCILLATION` and `SensorType.GROUND_CONTACT_TIME`, and SHALL remove `DeviceType.BIKE_POWER` from `SensorType.SLOPE`, `SensorType.VERTICAL_SPEED`, `SensorType.ASCENT`, and `SensorType.DESCENT` (leaving strictly `DeviceType.VERTICAL_SPEED_AND_SLOPE` and `DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED`).
   - *Given* `DeviceType.getDeviceTypeList(sensorType)`, *When* queried for `CADENCE` & `STRIDES`, *Then* `DeviceType.HRM` is present; *When* queried for `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, or `DESCENT`, *Then* `DeviceType.BIKE_POWER` is absent while `VERTICAL_SPEED_AND_SLOPE` and `ALTITUDE_AND_SLOPE_ATT_FUSED` remain present.
2. **`REQ-CON-057.2` — Shared Hardware-Grounded HRM Capability Helper (`HrmSensorCapabilityHelper`)**:
   - *Normative*: `HrmSensorCapabilityHelper.isSensorSupported(context, sensorType, protocol, deviceName, manufacturer, btAddress)` (`com.atrainingtracker.banalservice.devices`, parallel to `BikePowerSensorsHelper`) SHALL enforce:
     - For `CADENCE` & `STRIDES`: return `true` strictly when `protocol == Protocol.BLUETOOTH_LE` AND (`PolarOfflineGattCoordinator.isPolarH10Device(...)` OR `PolarOfflineGattCoordinator.isPolarVeritySenseDevice(...)`).
     - For `VERTICAL_OSCILLATION` & `GROUND_CONTACT_TIME`: return `true` strictly when `protocol == Protocol.BLUETOOTH_LE` AND `PolarOfflineGattCoordinator.isPolarH10Device(...)`.
     - For `HR`, `RR_INTERVAL`, `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, `HRV_SDNN` (and any non-IMU HRM sensor type): return `true` for all `DeviceType.HRM` devices regardless of protocol or brand (`REQ-HRV-050.1`).
     - Return `false` if `sensorType == null`, for all `Protocol.ANT_PLUS` HRMs on the 4 Live-IMU metrics (including dual-protocol ANT+ Polar H10), for confirmed non-Polar GATT HRMs (`REQ-CON-049.1`), and for rename-suppressed or non-Polar BLE HRMs (e.g., Wahoo TICKR, Garmin HRM-Pro, Polar H9).
   - *Given* BLE Polar H10 (hardware DIS/SDK `H10` even when renamed, or legacy name), BLE Polar Verity Sense (hardware `VERITY_SENSE` / `offline_rec_` / legacy name), ANT+ Polar H10, and ordinary/spoofed BLE HRMs, *When* `HrmSensorCapabilityHelper.isSensorSupported(...)` evaluates all 4 Live-IMU metrics plus `HR`/`HRV_RMSSD`, *Then* only BLE H10 & Verity Sense pass `CADENCE`/`STRIDES`, only BLE H10 passes `VERTICAL_OSCILLATION`/`GROUND_CONTACT_TIME`, and all HRMs pass `HR`/`HRV_RMSSD`.
3. **`REQ-CON-057.3` — `DevicesDatabaseManager` & `SensorSourceDialog` Capability Parity**:
   - *Normative*: `DevicesDatabaseManager.getDeviceIdAndNameLists(SensorType)` SHALL filter `DeviceType.HRM` rows through `HrmSensorCapabilityHelper.isSensorSupported(mContext, sensorType, protocol, name, manufacturer, btAddress)` using safe `>= 0` cursor column-index checks while preserving `BIKE_POWER` flag filtering and all non-HRM device types. `SensorSourceDialog.sortNotConnectedDevices()` SHALL apply the identical `HrmSensorCapabilityHelper.isSensorSupported(...)` gate for `DeviceType.HRM` entries so adding `HRM` to `CADENCE`/`STRIDES` never exposes non-IMU HRMs in *Not Connected* and `VO`/`GCT` exclude Verity Sense & ordinary HRMs while preserving `REQ-UI-2804.3` HR modality ordering.
   - *Given* a `Devices.db` containing BLE Polar H10, BLE Polar Verity Sense, ANT+ Polar H10, generic BLE HRM, ANT+ HRM, Bike Power meter, and Run Footpod, *When* `DevicesDatabaseManager.getDeviceIdAndNameLists()` and `SensorSourceDialog.sortNotConnectedDevices()` run, *Then* `CADENCE`/`STRIDES` include BLE H10 & Verity Sense (+ footpod/bike cadence/capable power) and exclude ANT+ H10 & generic HRMs; `VO`/`GCT` include ONLY BLE H10; `SLOPE`/`VAM`/`ASCENT`/`DESCENT` exclude Bike Power; and `HR`/`HRV_*` include all HRMs.
4. **`REQ-CON-057.4` — Architectural, Monolith & Zero-Regression Invariants**:
   - *Normative*: `DevicesDatabaseManager.java <= 1,671` LoC (`0` upward `.ui.*` imports), `SensorSourceDialog.kt <= 1,500` LoC, `HrmSensorCapabilityHelper.kt <= 1,500` LoC; `MySensorManager` priority tables unchanged; zero DB schema bumps; zero edits to `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, and `res/values*/strings*.xml` (`Localization Parity: N/A - 0 string changes`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Refines `REQ-SET-112` (`ATT-8016`), `REQ-SET-115` (`ATT-8018`), `REQ-CON-049` (`ATT-8717`/`ATT-8949`), `REQ-HRV-050.1` (`ATT-8990`), `REQ-UI-2804.3` (`ATT-9610`).
2. **Historical Origin & Commit Trace**: `ce73751c` (`SCRUM-140`/`144`), `75c1497e` (`ATT-6279`), `6be4eb31` (`ATT-8018`).
3. **Root Reason for Existing Formulation**: `DeviceType.getDeviceTypeList()` lacked per-device HRM capability filtering analogous to `BikePowerSensorsHelper`, and `SCRUM-140` speculatively added `BIKE_POWER` to slope/VAM.
4. **Preservation of Core Invariants**: Preserves all HR/RR/HRV HRM source selection, `BikePowerSensorsHelper` gating, `MySensorManager` priority arbitration, `SensorSourceDialog` HR modality/health ordering, `EditSensorFieldDialog` `"Best Sensor"` fallback, and monolith/schema limits.

---

## 3. Traceability Matrix & Localization Audit
| Requirement | Test Case | Target Suite / Contract | Planned Verification Procedure |
| :--- | :--- | :--- | :--- |
| `REQ-CON-057.1` | `TST-CON-057.1` | `DeviceTypeSensorMappingTest` | Assert `DeviceType.getDeviceTypeList()` includes `HRM` for `CADENCE` & `STRIDES`, retains `HRM` for `VO`/`GCT`, excludes `BIKE_POWER` from `SLOPE`/`VERTICAL_SPEED`/`ASCENT`/`DESCENT` while keeping `VERTICAL_SPEED_AND_SLOPE` & `ALTITUDE_AND_SLOPE_ATT_FUSED`, and keeps `CALORIES` isolated |
| `REQ-CON-057.2` | `TST-CON-057.2` | `HrmSensorCapabilityHelperTest` (new) | Matrix across BLE H10 (renamed hardware DIS/SDK & legacy name), BLE Verity Sense (renamed hardware/`offline_rec_` & legacy name), ANT+ H10, ordinary BLE/ANT+ HRMs, confirmed non-Polar GATT spoof, rename-suppressed HRM, Polar H9, and `HR`/`RR_INTERVAL`/`HRV_*` |
| `REQ-CON-057.3` | `TST-CON-057.3` | `DevicesDatabaseManagerTest`, `SensorSourceDialogPriorityTest`, `SensorSourceDialogContractTest` | Verify `DevicesDatabaseManager.getDeviceIdAndNameLists()` with mixed DB rows (incl. partial-column cursor safety & `BIKE_POWER` preservation) and `SensorSourceDialog` `sortNotConnectedDevices()` + behavioral Compose UI `createComposeRule()` rendering for `CADENCE`, `VO`, `SLOPE`, and `HR` |
| `REQ-CON-057.4` | `TST-CON-057.4` | 5 Contract Suites + `MySensorManagerPriorityTest` | `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `SystemLiveTrackingRequirementsContractTest`, `MySensorManagerPriorityTest` (`Localization Parity: N/A - 0 string changes`) |

- **9-Language Localization Audit (`EN/DE/ES/FR/IT/JA/NL/PL/PT`)**: `Localization Parity: N/A - 0 string changes`.
