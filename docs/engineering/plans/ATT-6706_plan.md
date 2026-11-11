# Stage 3: Implementation Plan - ATT-6706: Route BLE V2 (6a4e3204) notifications, register V2 radar SensorTypes, and sequence V2 MTU/LESC handshake

**Ticket**: [ATT-6706](https://rainerblind.atlassian.net/browse/ATT-6706)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-030.1`, `REQ-CON-030.2`, `REQ-CON-030.5`, `REQ-CON-030.6`  
**Test Mapping**: `TST-CON-030.1`, `TST-CON-030.2`, `TST-CON-030.5`, `TST-CON-030.6`, `TST-CON-030.8`  
**Branch**: `feature/ATT-6706`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
BLE V2 high-resolution radar streaming (`6a4e3204`) fails in production due to three architectural obstacles:
1. `MyBTLEDevice.characteristicUpdate()` drops 100% of incoming V2 notifications because its characteristic filter strictly tests against `BluetoothConstants.getCharacteristicUUID(getDeviceType())`, which maps to V1 (`6a4e3203`).
2. `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` are omitted from `DeviceType.getDeviceTypeList()` (`DeviceType.java`), preventing sensor attribution and tile configuration in the Cockpit UI.
3. `BTLERadarDevice.configureServices()` invokes unmanaged `gatt.requestMtu(247)` outside `mGattQueue`, colliding with concurrent descriptor writes, omits checking `BluetoothDevice.BOND_BONDED` (LESC security requirement for V2), and lacks automatic `fallbackToV1()` on CCCD write failure.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-030.1 (V2 Handshake Choreography & Characteristic Routing)**: Serializes `RequestMtuOp(247)` through `mGattQueue`; delegates notification filtering to overridable `isMeasurementCharacteristic(UUID)` in `MyBTLEDevice` -> `TST-CON-030.1`
* **REQ-CON-030.2 (Automatic Legacy Fallback Invariant)**: Checks `BOND_BONDED` and catches CCCD write failures (`status != GATT_SUCCESS`) to trigger immediate `fallbackToV1()` -> `TST-CON-030.2`
* **REQ-CON-030.5 (Scalar Sensor Publishing & DeviceType Registration)**: Maps `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` to `DeviceType.BIKE_RADAR` in `DeviceType.java` -> `TST-CON-030.5`
* **REQ-CON-030.6 (Traffic Simulator V2 Extension)**: Verifies `RadarTrafficSimulator` produces V2 multi-target trajectories with lateral offsets and vehicle classifications -> `TST-CON-030.6`

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: Legacy V1 radar devices (`6a4e3203`) must continue operating without regression.
2. **GATT Operation Serialization**: All MTU requests, descriptor writes, and characteristic reads execute strictly via `mGattQueue`.
3. **Resilient Fallback**: Unbonded devices or rejected V2 CCCD writes automatically fall back to V1 without packet starvation.
4. **No Schema Changes**: Pure in-memory / sensor registry changes; zero SQLite migrations.

---

## 4. UI Consistency (Design Guidelines Section 5)
* **Reference Screens**: Cockpit Tile Editor / Sensor Source Dialog (`SensorGridScreen.kt`).
* **Component Reuse**: `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` become discoverable in existing bike sensor selection dialogs under `DeviceType.BIKE_RADAR`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Measurement Characteristic Delegation (`MyBTLEDevice.java` & `BTLERadarDevice.java`)
* In `MyBTLEDevice.java`, extract `protected boolean isMeasurementCharacteristic(@NonNull UUID uuid)` defaulting to checking `BluetoothConstants.getCharacteristicUUID(getDeviceType())`.
* In `BTLERadarDevice.java`, override `isMeasurementCharacteristic` to return `true` for both `UUID_CHARACTERISTIC_RADAR_V1` and `UUID_CHARACTERISTIC_RADAR_V2`.

### Step 2: Serialized MTU Request Operation (`MyBTLEDevice.java`)
* Add `RequestMtuOp` implementing `GattOperation` in `MyBTLEDevice.java`.
* Add `public void enqueueRequestMtu(int mtu)` routing through `mGattQueue`.
* Implement `onMtuChanged(BluetoothGatt gatt, int mtu, int status)` in `mGattCallback` calling `onGattOperationCompleted()` and notifying `onMtuConfigurationCompleted(...)`.

### Step 3: LESC Bonding Verification & Fallback Hardening (`BTLERadarDevice.java`)
* In `BTLERadarDevice.configureServices()`, verify `gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED` before attempting V2. If unbonded, call `fallbackToV1(gatt, btGattService)`.
* Replace raw `gatt.requestMtu(247)` with `enqueueRequestMtu(247)` followed by V2 CCCD descriptor write.
* In `onDescriptorWriteCompleted`, if descriptor write for `6a4e3204` fails (`status != BluetoothGatt.GATT_SUCCESS`), log warning and trigger `fallbackToV1(gatt, btGattService)`.

### Step 4: SensorType Registration in DeviceType (`DeviceType.java`)
* In `DeviceType.getDeviceTypeList(SensorType sensorType)`, add `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` cases mapping to `DeviceType.BIKE_RADAR`.

### Step 5: Test Suite Expansion & Clean-Room Verification
* Update `BTLERadarDeviceTest.kt` verifying V2 notification routing through `mGattCallback`, `RequestMtuOp` queueing, and fallback on unbonded / CCCD failure.
* Update `MyBTLEDeviceTest.kt` verifying MTU operation queueing and completion advancement.
* Run targeted unit tests and requirement governance audit under `/tmp/att_build_merge.lock`.

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*"`
* **Rollback Plan**: `git checkout feature/ATT-6706` to restore clean state.
