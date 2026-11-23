# Stage 3 Implementation Plan: ATT-8991 - Capability-Gated HRM Sources & BIKE_POWER Slope/VAM Removal

**Ticket**: [ATT-8991](https://rainerblind.atlassian.net/browse/ATT-8991) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Target Release**: `V5.0.0`  
**Requirements / Tests**: `REQ-CON-057` (`REQ-CON-057.1`..`.4`) / `TST-CON-057` (`TST-CON-057.1`..`.4`)  
**UI / Localization Consistency**: Existing `EditSensorFieldDialog` spinner & `SensorSourceDialog` list rows unchanged (`0` new components, `0` string edits, `0` `R.jar` invalidation; behavioral `createComposeRule()` verification in `SensorSourceDialogContractTest`)  
**SQLite / Schema Impact**: `None` (zero DB version bump, zero DDL/migration edits; reads existing `Devices4` columns `protocol`, `name`, `manufacturerName`, `BT_adress` + persisted `PolarOfflineCapabilityStore`)  
**Date**: 2026-11-23 | **Author**: Lane 2 Author (`--as agent1`)

---

## 1. Architecture, Layering & Monolith Budgets (`SWE.2`)
- **Single Source of Truth (`docs/coding_standards.md` §1.1 DRY)**:
  - `com.atrainingtracker.banalservice.devices.HrmSensorCapabilityHelper` (new Kotlin `object`, `@JvmStatic`, `~65` LoC, right beside `BikePowerSensorsHelper.java`): encapsulates the HRM Live-IMU capability matrix by delegating to `PolarOfflineGattCoordinator.isPolarH10Device()` / `isPolarVeritySenseDevice()` (which consult `PolarOfflineCapabilityStore` hardware DIS/SDK/GATT metadata with guarded legacy fallback per `REQ-CON-049.4`) behind an explicit `protocol == Protocol.BLUETOOTH_LE` gate.
  - `com.atrainingtracker.banalservice.devices.DeviceType`: updates `getDeviceTypeList(SensorType)` (`+HRM` on `CADENCE`/`STRIDES`, `-BIKE_POWER` on `SLOPE`/`VERTICAL_SPEED`/`ASCENT`/`DESCENT`).
  - `com.atrainingtracker.banalservice.database.DevicesDatabaseManager`: adds `else if (deviceType == DeviceType.HRM)` branch inside `getDeviceIdAndNameLists(SensorType)` delegating to `HrmSensorCapabilityHelper.isSensorSupported(...)` with safe `>= 0` cursor column-index extraction (`0` upward `.ui.*` imports).
  - `com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SensorSourceDialog`: applies the same `HrmSensorCapabilityHelper.isSensorSupported(...)` predicate inside `sortNotConnectedDevices()` for `DeviceType.HRM` so disconnected non-IMU HRMs never leak into `CADENCE`/`STRIDES`/`VO`/`GCT`.
- **Monolith Budgets**:
  - `HrmSensorCapabilityHelper.kt`: new `~65` LoC (`<= 1,500`).
  - `DeviceType.java`: `268 -> ~270 / 1,500` LoC.
  - `DevicesDatabaseManager.java`: `1,642 -> ~1,656 / 1,671` LoC (strictly under grandfathered ceiling; `0` `.ui.*` imports).
  - `SensorSourceDialog.kt`: `567 -> ~576 / 1,500` LoC.
  - **Strict `0` Edits**: `PolarOfflineCapabilityStore.kt`, `PolarOfflineGattCoordinator.kt`, `MySensorManager.java`, `EditSensorFieldViewModel.kt`, `EditSensorFieldDialog.kt`, `strings*.xml`.
- **Upgrade-Path Behavior (`docs/coding_standards.md` §5.6)**: Existing users with paired Polar H10/Verity Sense immediately gain Polar H10/Verity Sense as selectable `CADENCE`/`STRIDES` sources (and H10-only for `VO`/`GCT`), while ordinary HRMs and power meters disappear from incompatible source lists; any legacy field referencing a now-filtered device degrades safely to `"Best Sensor"` in `EditSensorFieldDialog.kt:206`.

---

## 2. Bite-Sized Atomic Implementation Steps
| Step | Target File(s) | Requirement / Test | Atomic Implementation Scope |
| :---: | :--- | :--- | :--- |
| **1** | `HrmSensorCapabilityHelper.kt` (new) | `REQ-CON-057.2` / `TST-CON-057.2` | Create `object HrmSensorCapabilityHelper` with `@JvmStatic fun isSensorSupported(context, sensorType, protocol, deviceName, manufacturer, btAddress): Boolean`: return `false` if `sensorType == null`; for `CADENCE`/`STRIDES` require `protocol == Protocol.BLUETOOTH_LE && (isPolarH10Device \|\| isPolarVeritySenseDevice)`; for `VERTICAL_OSCILLATION`/`GROUND_CONTACT_TIME` require `protocol == Protocol.BLUETOOTH_LE && isPolarH10Device`; for all other sensor types (`HR`, `RR_INTERVAL`, `HRV_*`) return `true`. |
| **2** | `DeviceType.java` | `REQ-CON-057.1` / `TST-CON-057.1` | In `getDeviceTypeList(SensorType)`: add `deviceTypes.add(DeviceType.HRM);` under `case CADENCE:` and `case STRIDES:`; remove `deviceTypes.add(DeviceType.BIKE_POWER);` from `case SLOPE: / VERTICAL_SPEED: / ASCENT: / DESCENT:`. |
| **3** | `DevicesDatabaseManager.java`, `SensorSourceDialog.kt` | `REQ-CON-057.3` / `TST-CON-057.3` | In `DevicesDatabaseManager.getDeviceIdAndNameLists(SensorType)`: add `else if (deviceType == DeviceType.HRM)` reading `PROTOCOL`, `NAME`, `MANUFACTURER_NAME`, and `BT_ADDRESS` via `>= 0` column-index guards and skipping rows where `!HrmSensorCapabilityHelper.isSensorSupported(mContext, sensorType, protocol, name, manufacturer, btAddress)`. In `SensorSourceDialog.sortNotConnectedDevices()`: add `device.deviceType != DeviceType.HRM \|\| HrmSensorCapabilityHelper.isSensorSupported(context, sensorType, device.protocol, device.deviceName, device.manufacturer, device.btAddress)`. |
| **4** | `HrmSensorCapabilityHelperTest.kt` (new), `DeviceTypeSensorMappingTest.kt`, `DevicesDatabaseManagerTest.kt`, `SensorSourceDialogPriorityTest.kt`, `SensorSourceDialogContractTest.kt` | `REQ-CON-057.1`..`.3` / `TST-CON-057.1`..`.3` | Add full capability matrix unit tests (`HrmSensorCapabilityHelperTest`), `DeviceType.getDeviceTypeList()` assertions (`DeviceTypeSensorMappingTest`), DB cursor filtering & partial-column safety tests (`DevicesDatabaseManagerTest`), `sortNotConnectedDevices()` capability filtering tests (`SensorSourceDialogPriorityTest`), and behavioral Robolectric `createComposeRule()` UI assertions (`SensorSourceDialogContractTest`). |
| **5** | Living Docs (`docs/architecture.md`, `docs/system/sysreq_live_tracking.md`, `docs/system/system_software_traceability.md`, `req_tracking_cockpit_part3.md`, `tst_tracking_cockpit_part2.md`, indexes) | `REQ-CON-057.4` / `TST-CON-057.4` | Ground `HrmSensorCapabilityHelper.kt` in `docs/architecture.md` (`Rule 8.8`) & `SYSREQ-TRK-001` (`Rule 8.7`) once created on disk; promote `REQ-CON-057`/`TST-CON-057` to `[Implemented]` -> `[Verified]` with verbatim `TestClass#testMethod` citations; run `--sync-indexes`, `verify_requirement_governance.py`, safety linters, locked incremental Gradle (`--rerun`), and `emulator-5554` smoke (since `SensorSourceDialog.kt` is modified). |

---

## 3. Non-Negotiable Invariants & Verification Command Plan
- **Invariants**: All ANT+/BLE HRMs remain selectable for `HR`, `RR_INTERVAL`, `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, `HRV_SDNN`; dual-protocol ANT+ Polar H10 never leaks into Live-IMU metrics; Verity Sense/OH1 never leaks into `VO`/`GCT`; confirmed non-Polar GATT HRMs (`REQ-CON-049.1`) never match via spoofed names; `BikePowerSensorsHelper` & `MySensorManager` priority tables unchanged; `DevicesDatabaseManager.java <= 1,671` LoC (`0` `.ui.*` imports); `0` DB schema or string changes.
- **Stage 4 Targeted Test Command** (`python3 tools/parallel_sprint_lock.py -- ./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun --tests ...`):
  - `HrmSensorCapabilityHelperTest`, `DeviceTypeSensorMappingTest`, `DevicesDatabaseManagerTest`, `SensorTypeHrvTest`, `MySensorManagerPriorityTest`, `EditSensorFieldViewModelTest`, `SensorSourceDialogPriorityTest`, `SensorSourceDialogContractTest`, plus 5 Contract suites (`ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `SystemLiveTrackingRequirementsContractTest`).
