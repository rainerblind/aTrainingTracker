# Stage 1 Analysis: ATT-8991 - Capability-Gated HRM Sources (Polar Live-IMU) & BIKE_POWER Removal from Slope/VAM/Ascent/Descent

**Ticket**: [ATT-8991](https://rainerblind.atlassian.net/browse/ATT-8991) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Story Points**: `2 SP` (`severity-major`, `tier-a` — no split required per `REQ-PRO-029`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-23_02_Spät` (Sprint 475) | **Base SHA**: `d74bdbf8`  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Problem Statement
Cockpit sensor source selection (`EditSensorFieldDialog` -> `TrackingViewsRepository.getDeviceLists()` -> `DevicesDatabaseManager.getDeviceIdAndNameLists(SensorType)` -> `DeviceType.getDeviceTypeList(SensorType)`) has two source-mapping defects:
1. **Polar Live-IMU HRM Sources (`CADENCE`, `STRIDES`, `VERTICAL_OSCILLATION`, `GROUND_CONTACT_TIME`)**: `PolarOnlineStreamingBridge` (persisted as `DeviceType.HRM`, `Protocol.BLUETOOTH_LE`) emits `CADENCE` & `STRIDES` on Polar H10 and Polar Verity Sense/OH1 (`PolarOnlineStreamingBridge.kt:1251-1262`), and `VERTICAL_OSCILLATION` & `GROUND_CONTACT_TIME` exclusively on Polar H10 (`:1263-1274`). Currently `DeviceType.getDeviceTypeList()` omits `DeviceType.HRM` from `CADENCE` (`:173-179`) and `STRIDES` (`:186-189`) completely, while `VERTICAL_OSCILLATION` & `GROUND_CONTACT_TIME` (`:208-215`) list **every** `DeviceType.HRM` row unconditionally (including ANT+ HRMs, non-Polar BLE HRMs, and Polar Verity Sense).
2. **Spurious `BIKE_POWER` on `SLOPE`, `VERTICAL_SPEED`, `ASCENT`, `DESCENT`**: `DeviceType.getDeviceTypeList()` (`:229-236`) includes `DeviceType.BIKE_POWER`, causing paired power meters to appear as selectable sources even though no `BIKE_POWER` device emits altitude/slope/VAM/ascent/descent.

---

## 2. Independent Peer Review & Verification (`Rules 69 & 70`)
Audited against live `d74bdbf8` across all callers (`grep` of `getDeviceTypeList` & `getDeviceIdAndNameLists`):
1. **Confirmed — `DeviceType.java:173-236` & `DevicesDatabaseManager.java:392-490`**: `getDeviceIdAndNameLists()` already selects all columns (`columns = null`) from `Devices4` (including `PROTOCOL`, `NAME`, `MANUFACTURER_NAME`, `BT_ADDRESS`) and filters `BIKE_POWER` via `BikePowerSensorsHelper`, but performs zero capability checks for `DeviceType.HRM`.
2. **Confirmed — Hardware-Grounded Polar Identity Already Exists (`REQ-CON-049`, `ATT-8717`/`ATT-8949`)**: `PolarOfflineGattCoordinator.isPolarH10Device(context, name, manufacturer, mac)` and `isPolarVeritySenseDevice(context, name, manufacturer, mac)` already enforce strict precedence over `PolarOfflineCapabilityStore` (confirmed non-Polar GATT rejection -> DIS `0x2A24` / SDK `PolarDeviceType` -> backfilled legacy type -> guarded name fallback), handling renamed devices, disconnected devices, and non-Polar spoofs with zero SQLite schema change.
3. **Critical Edge Case Found — Dual-Protocol Polar H10 (`ANT_PLUS` vs `BLUETOOTH_LE`)**: Polar H10 broadcasts both ANT+ and BLE; only `Protocol.BLUETOOTH_LE` supports Polar PMD Live-IMU (`DeviceManager.java:1468-1476`, `EditDeviceDialog.kt:1572, 1591`). Gating strictly on `protocol == Protocol.BLUETOOTH_LE` prevents an ANT+ Polar H10 from leaking into Live-IMU `CADENCE`/`STRIDES`/`VO`/`GCT`.
4. **Critical Side-Effect Found (`Rule 68`) — `SensorSourceDialog.kt:86, 543-567`**: The ONLY other production caller of `DeviceType.getDeviceTypeList(sensorType)` is `SensorSourceDialog`, which builds `notConnected` via `sortNotConnectedDevices()`. Adding `DeviceType.HRM` to `CADENCE`/`STRIDES` without applying the same HRM capability check in `sortNotConnectedDevices()` would regress `SensorSourceDialog` by displaying every disconnected ANT+/BLE HRM under *Not Connected* for `CADENCE`/`STRIDES` (and already showed Verity Sense/non-Polar HRMs for `VO`/`GCT`). `DeviceUiData.btAddress` is already populated for BLE HRMs by `RawDeviceDataProvider.kt:87-92` (`ATT-9610`).
5. **Verified — Zero `BIKE_POWER` Slope/VAM Producers & `MySensorManager` Pin**: `grep` across `banalservice/devices/` confirms only `VerticalSpeedAndSlopeDevice` and `AltitudeAndSlopeDevice_ATTFused` produce `SLOPE`/`VERTICAL_SPEED`/`ASCENT`/`DESCENT`. `MySensorManager.java` already ranks `DeviceType.HRM` for `CADENCE`/`STRIDES`, and `MySensorManagerPriorityTest.kt:396-407` pins `SLOPE_AND_VERTICAL_SPEED_PRIORITY`, so `MySensorManager.java` stays untouched.

---

## 3. Root Cause Summary
1. `DeviceType.getDeviceTypeList()` omitted `DeviceType.HRM` for `CADENCE`/`STRIDES` and erroneously retained `DeviceType.BIKE_POWER` for `SLOPE`/`VERTICAL_SPEED`/`ASCENT`/`DESCENT`.
2. Neither `DevicesDatabaseManager.getDeviceIdAndNameLists()` nor `SensorSourceDialog.sortNotConnectedDevices()` gated `DeviceType.HRM` candidates by protocol (`BLUETOOTH_LE`) and Polar hardware capability (`H10` vs `VERITY_SENSE`) for Live-IMU sensor types.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Refines `REQ-SET-112` (`ATT-8016`), `REQ-SET-115` (`ATT-8018`), `REQ-CON-049` (`ATT-8717`/`ATT-8949`), `REQ-HRV-050.1` (`ATT-8990`), and `REQ-UI-2804.3` (`ATT-9610`) across `DeviceType.java`, `DevicesDatabaseManager.java`, and `SensorSourceDialog.kt`.
- **Historical Origin & Commit Trace**: `ce73751c` (`SCRUM-140`/`144`) added `DeviceType.BIKE_POWER` under `SLOPE`/`VERTICAL_SPEED`; `75c1497e` (`ATT-6279`) grouped `ASCENT`/`DESCENT` into the same switch case inheriting `BIKE_POWER`; `6be4eb31` (`ATT-8018`) mapped `VERTICAL_OSCILLATION`/`GROUND_CONTACT_TIME` to `DeviceType.HRM` without per-device HRM capability filtering.
- **Root Reason for Existing Formulation**: `DeviceType.getDeviceTypeList()` operates purely at enum-type granularity; `ATT-8018` relied on `HRM` type mapping without an HRM analog to `BikePowerSensorsHelper`, and `SCRUM-140` speculatively added `BIKE_POWER` to slope/VAM.
- **Preservation of Core Invariants**: All ANT+ & BLE HRMs remain selectable for `HR`, `RR_INTERVAL`, `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, and `HRV_SDNN` (`REQ-HRV-050.1`); `BikePowerSensorsHelper` gating for `BIKE_POWER` unchanged; `MySensorManager` priority tables unchanged; `SensorSourceDialog` HR health/modality ordering (`REQ-UI-2804.3`) unchanged; `EditSensorFieldDialog` `"Best Sensor"` fallback unchanged; zero DB schema bump; `DevicesDatabaseManager.java <= 1,671` LoC.

---

## 5. Change-Request Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10 & `REQ-PRO-131.4`)
- **Affected Requirements**: Reserve `REQ-CON-057` (`REQ-CON-057.1`..`.4`) & `TST-CON-057` (`TST-CON-057.1`..`.4`) in `docs/requirements/req_tracking_cockpit_part3.md` (`1,731` LoC) and `docs/tests/tst_tracking_cockpit_part2.md` (`2,218` LoC). Traces to `SYSREQ-TRK-001` (`docs/system/sysreq_live_tracking.md`, `docs/system/system_software_traceability.md`); no new BLE/ANT+ profiles or `SYS-EL-*`/`SYS-IF-*` changes (`REQ-PRO-131.4`). `Localization Parity: N/A - 0 string changes`.
- **Affected Architecture Elements**: New `HrmSensorCapabilityHelper.kt` (`~65` LoC in `banalservice/devices/`, parallel to `BikePowerSensorsHelper.java`), `DeviceType.java` (`268 -> ~270` LoC), `DevicesDatabaseManager.java` (`1,642 -> ~1,656 / 1,671` LoC, `0` upward `.ui.*` imports), `SensorSourceDialog.kt` (`567 -> ~576 / 1,500` LoC). **Untouched**: `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `MySensorManager.java`, `EditSensorFieldViewModel.kt`, `strings*.xml`, DB schemas.
- **Verification Impact & Test Surface**: New `HrmSensorCapabilityHelperTest`, expanded `DeviceTypeSensorMappingTest`, `DevicesDatabaseManagerTest`, `SensorSourceDialogPriorityTest`, `SensorSourceDialogContractTest`, plus Contract suites (`ArchitectureConformance`, `InterfaceSignatureDrift`, `DeadCodeElimination`, `SourceTextReadingAudit`, `SystemLiveTrackingRequirements`).
- **Risk Assessment & Invariants**: **HIGH (`tier-a`)** — shared sensor capability & cockpit source selection across `EditSensorFieldDialog` and `SensorSourceDialog`. Mitigated by delegating Polar classification to battle-tested `PolarOfflineGattCoordinator`/`PolarOfflineCapabilityStore` (`REQ-CON-049.4`), strict `Protocol.BLUETOOTH_LE` gating, safe cursor column-index guards in `DevicesDatabaseManager`, and behavioral Compose + Robolectric/JVM tests.

---

## 6. Scope Bounding
- **In Scope**: `HrmSensorCapabilityHelper`, `DeviceType.getDeviceTypeList()` (`+HRM` on `CADENCE`/`STRIDES`, `-BIKE_POWER` on `SLOPE`/`VERTICAL_SPEED`/`ASCENT`/`DESCENT`), `DevicesDatabaseManager.getDeviceIdAndNameLists()` HRM capability gating, `SensorSourceDialog.sortNotConnectedDevices()` HRM capability parity, and unit/Compose/contract tests.
- **Out of Scope**: `MySensorManager` priority tables, `BIKE_POWER` filtering inside `SensorSourceDialog`, `POLAR_LIVE_IMU_ENABLED` runtime toggle gating (source selection reflects hardware capability, parallel to `BikePowerSensorsHelper`), DB schema changes, and UI string changes.
