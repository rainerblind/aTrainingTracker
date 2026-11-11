# Stage 5: Walkthrough & Verification - ATT-6706: BLE V2 Notification Routing, SensorType Registration & MTU/LESC Handshake

**Ticket**: [ATT-6706](https://rainerblind.atlassian.net/browse/ATT-6706)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-CON-030` (`REQ-CON-030.1` - `REQ-CON-030.7`)  
**Test Mapping**: `TST-CON-030` (`TST-CON-030.1` - `TST-CON-030.8`)  
**Branch**: `feature/ATT-6706`  
**Author**: AI Agent 1 (Implementer - Flash)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

This walkthrough validates the complete resolution of `ATT-6706`:
1. **Measurement Characteristic Delegation (`MyBTLEDevice.java` & `BTLERadarDevice.java`)**:
   - Extracted overridable `protected boolean isMeasurementCharacteristic(@NonNull UUID uuid)` on `MyBTLEDevice.java` defaulting to checking `BluetoothConstants.getCharacteristicUUID(getDeviceType())`.
   - Overrode `isMeasurementCharacteristic` in `BTLERadarDevice.java` to accept both legacy V1 (`6a4e3203`) and high-resolution V2 (`6a4e3204`), completely resolving the silent dropping of 100% of live V2 notifications.
2. **GATT Queue MTU Sequencing & LESC Handshake Choreography**:
   - Introduced `RequestMtuOp` implementing `GattOperation` and `enqueueRequestMtu(int mtu)` on `MyBTLEDevice.java`.
   - Implemented `onMtuChanged` in `mGattCallback` with sequential `onGattOperationCompleted()` and `onMtuConfigurationCompleted` hook.
   - Enforced `BluetoothDevice.BOND_BONDED` check in `BTLERadarDevice.configureServices()` before attempting V2 setup (LESC requirement for Garmin Varia RearVue 820). If unbonded, automatically falls back to legacy V1 via `fallbackToV1()`.
   - Sequenced `enqueueRequestMtu(247)` ahead of V2 CCCD descriptor write through `mGattQueue`, preventing GATT operation races.
   - Implemented `onDescriptorWriteCompleted` hook in `BTLERadarDevice.java` to detect CCCD failures (e.g. `GATT_INSUFFICIENT_AUTHENTICATION`) and trigger automatic fallback to V1 without packet starvation.
3. **SensorType Attribution in DeviceType (`DeviceType.java`)**:
   - Added `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` to `DeviceType.getDeviceTypeList(SensorType)`, mapping them to `DeviceType.BIKE_RADAR`. Both metrics are now fully discoverable in the Cockpit Tile Editor and Sensor Source dialogs.
4. **Traffic Simulator V2 Verification**:
   - Confirmed `RadarTrafficSimulator.generateV2Targets()` produces synthetic frames with lateral offsets and vehicle classifications, verified by automated unit tests.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-030.1` | `TST-CON-030.1` | Unit Test (`BTLERadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.2` | `TST-CON-030.2` | Unit Test (`BTLERadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.3` | `TST-CON-030.3` | Unit Test (`BTLERadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.4` | `TST-CON-030.4` | Unit Test (`RadarWindowAggregatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.5` | `TST-CON-030.5` | Unit Test (`DeviceTypeSensorMappingTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.6` | `TST-CON-030.6` | Unit Test (`RadarTrafficSimulatorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-030.7` | `TST-CON-030.7` | Localization Parity Test (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001`   | `TST-CON-030.8` | Tier 1 Targeted Test Suite & Resource Compilation | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Executed under `/tmp/att_build_merge.lock` via `python3 tools/parallel_sprint_lock.py`:
- `com.atrainingtracker.banalservice.devices.bluetooth_le.*` (`BTLERadarDeviceTest`)
- `com.atrainingtracker.banalservice.devices.radar.*` (`RadarTrafficSimulatorTest`, `RadarWindowAggregatorTest`, `RadarSnoozeControllerTest`, `RadarLightControllerTest`)
- `com.atrainingtracker.banalservice.devices.DeviceTypeSensorMappingTest`
- `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`

```text
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 55s
33 actionable tasks: 2 executed, 31 up-to-date
```
- Total tests executed across scope: 63 tests (0 failures, 0 errors, 100% green).
  * `BTLERadarDeviceTest`: 19 passed, 0 failed.
  * `RadarTrafficSimulatorTest`: 8 passed, 0 failed.
  * `RadarWindowAggregatorTest`: 6 passed, 0 failed.
  * `RadarSnoozeControllerTest`: 11 passed, 0 failed.
  * `RadarLightControllerTest`: 9 passed, 0 failed.
  * `DeviceTypeSensorMappingTest`: 1 passed, 0 failed.
  * `TranslationParityTest`: 9 passed, 0 failed across all 9 localized languages.

### Living Documentation Governance Audit
```bash
python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6706_test_spec.md
```
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
Modified/Altered existing requirement(s) detected: REQ-CON-030.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. UI & Visual Asset Inspection

- **Cockpit Sensor Attribution & Tile Discoverability**: Verified `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` map to `DeviceType.BIKE_RADAR` in `DeviceType.java`, making both scalar metrics selectable in the Cockpit Tile Editor and Sensor Source dialogs under Bike Radar.
- **Zero Drawable Modifications**: Confirmed via `git status` that zero drawables or vector assets were touched for this ticket (Rule 71 check satisfied).

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Tier 1 targeted unit test suite executed under lock with 100% pass rate.
2. **Living Documentation Synchronized**: Status for `REQ-CON-030` and `TST-CON-030` verified with master index tables synchronized via `--sync-indexes`.
3. **Subtask Governance**: Stage 4 subtask `ATT-6979` submitted for Gate 4 review.
4. **Parent Ticket Final Review Handover**: Parent ticket `ATT-6706` ready for Gate 5 sign-off and advancement to `Final Review (Human)` assigned to Rainer Blind. Single squashed commit on `sprint/2026-46.2` under `/tmp/att_build_merge.lock`.
