# Stage 3: Implementation Plan - ATT-7545: RearVue 820 BLE Tail Light Control Service

**Ticket**: [ATT-7545](https://rainerblind.atlassian.net/browse/ATT-7545) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7545`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.1`, `REQ-CON-031.2`)  
**Test Mapping**: `TST-CON-031` (`TST-CON-031.1`, `TST-CON-031.2`, `TST-CON-031.8`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
Physical Garmin Varia RearVue 820 radar units host tail light control on dedicated Control Service `6a4e2f00-667b-11e3-949a-0800200c9a66` instead of legacy characteristic `6a4e3202` on service `6a4e3200`. Command writes require a 4-byte payload `06 09 01 TT` on characteristic `6a4e2f11`, while physical button presses emit 4-byte slot notifications `01 [slot] ff [type]` on `6a4e2f14`.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-031.1**: Control Service `6a4e2f00` discovery, `6a4e2f14` CCCD notification subscription, and legacy `6a4e3202` fallback (`TST-CON-031.1`).
* **REQ-CON-031.2**: 4-byte command dispatch (`06 09 01 TT`) on `6a4e2f11` with immediate optimistic state update, 4-byte slot decoding on `6a4e2f14`, and legacy 1-byte dispatch (`TST-CON-031.2`).
* **REQ-PRO-001**: 100% green on `com.atrainingtracker.banalservice.devices.bluetooth_le.*` (`TST-CON-031.8`).

---

## 3. System Invariants & Preserved Behavior
1. **Serialized GATT FIFO Routing**: All writes to `6a4e2f11` and descriptor writes to `6a4e2f14` route through `enqueueWriteCharacteristic` and `enqueueWriteDescriptor`.
2. **Transparent Backward Compatibility**: Peripherals lacking `6a4e2f00` (e.g. RTL515, RTL516) transparently fallback to legacy 1-byte `6a4e3202` on `6a4e3200`.
3. **StVZO Legal Compliance**: Flashing modes remain pruned under StVZO regulations (`SOLID` and `OFF` only).
4. **Optimistic Sync & Guard Window**: `setLightMode()` updates `RadarLightController` immediately; a 1000 ms guard suppresses stale echo notifications.

---

## 4. Proposed Architectural Changes
* **BluetoothConstants.java**:
  - Add `SERVICE_RADAR_CONTROL` (`6a4e2f00-...`) and `UUID_SERVICE_RADAR_CONTROL`.
  - Add `CHARACTERISTIC_RADAR_CONTROL_WRITE` (`6a4e2f11-...`) and `UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE`.
  - Add `CHARACTERISTIC_RADAR_CONTROL_NOTIFY` (`6a4e2f14-...`) and `UUID_CHARACTERISTIC_RADAR_CONTROL_NOTIFY`.
* **BTLERadarDevice.java**:
  - Store `mRadarControlWriteChar`, `mRadarControlNotifyChar`, and `mLastLightModeCommandTimeMs`.
  - In `configureServices()`: Probe `6a4e2f00`, bind `6a4e2f11` and `6a4e2f14`, subscribe CCCD, and fallback to `6a4e3202`.
  - In `setLightMode()`: If `6a4e2f11` present, dispatch `06 09 01 TT` and immediately call `RadarLightController.updateHardwareLightMode(mode)`. Else dispatch 1-byte on `6a4e3202`.
  - In `onOtherCharacteristicUpdate()`: Decode `01 [slot] ff [type]` from `6a4e2f14`, filter config/ack blobs, and update `RadarLightController`.
* **UI Consistency**: No UI changes; UI observes `RadarStateRepository` and `RadarLightController`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Constants & RearVue 820 Protocol Encoding/Decoding (`REQ-CON-031.1`, `REQ-CON-031.2`)
* Files: `BluetoothConstants.java`, `BTLERadarDevice.java`
* Declare UUIDs in `BluetoothConstants`. Implement `getRearVueModeType(mode)`, `parseRearVueModeType(type)`, and `isControlSlotNotification(payload)` in `BTLERadarDevice`.

### Step 2: Control Service Discovery, CCCD Subscription & Fallback (`REQ-CON-031.1`)
* Files: `BTLERadarDevice.java`
* In `configureServices()`, discover service `6a4e2f00`, bind characteristics, enqueue `6a4e2f14` CCCD subscription, and fallback to `6a4e3202` on `6a4e3200` if absent. Re-apply active mode on reconnect.

### Step 3: 4-Byte Mode Override Dispatch & Slot State Sync (`REQ-CON-031.2`)
* Files: `BTLERadarDevice.java`
* In `setLightMode()`, format `06 09 01 TT`, enqueue write to `6a4e2f11`, record timestamp, and immediately sync `RadarLightController`. In `onOtherCharacteristicUpdate()`, decode `6a4e2f14` notifications with stale guard.

### Step 4: Unit Test Coverage & Verification (`TST-CON-031.1`, `TST-CON-031.2`, `TST-CON-031.8`)
* Files: `BTLERadarDeviceTest.kt`
* Add tests for 4-byte command encoding, 4-byte notification decoding, non-state frame rejection, and fallback.
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"`
* Run: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-7545_test_spec.md`

---

## 6. Verification & Rollback Plan
* **Verification**: All unit tests green; requirement governance passes with 0 violations.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7545` to commit `5f082f95`.
