# Stage 1: Problem Domain & Root Cause Analysis - ATT-6706: Route BLE V2 (6a4e3204) notifications, register V2 radar SensorTypes, and sequence V2 MTU/LESC handshake

**Ticket**: [ATT-6706](https://rainerblind.atlassian.net/browse/ATT-6706)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-030.1`, `REQ-CON-030.2`, `REQ-CON-030.5`, `REQ-CON-030.6`  
**Test Spec ID**: `TST-CON-030`  
**Branch**: `feature/ATT-6706`  
**Author**: AI Agent 1 (Analyst - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Symptoms
Next-generation Garmin Varia radar units (e.g. RearVue 820) fail to deliver high-resolution BLE V2 telemetry (`6a4e3204`) in production due to three architectural flaws:
1. `MyBTLEDevice.characteristicUpdate()` drops 100% of live V2 notifications because its characteristic equality filter strictly tests against `BluetoothConstants.getCharacteristicUUID(getDeviceType())`, which is statically mapped to V1 (`6a4e3203`).
2. `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` are omitted from `DeviceType.getDeviceTypeList()` (`DeviceType.java`), preventing cockpit tile selection and sensor attribution.
3. `BTLERadarDevice.configureServices()` calls unmanaged `gatt.requestMtu(247)` racing directly against concurrent CCCD write operations without waiting for `onMtuChanged`, skips LESC bonding checks (`BluetoothDevice.BOND_BONDED`), lacks automatic `fallbackToV1()` on CCCD write failure (e.g. `GATT_INSUFFICIENT_AUTHENTICATION`), and leaves `RadarTrafficSimulator.generateV2Targets()` unused.

---

## 2. Forensic Root Cause Analysis & Call Site Audit
1. **Notification Filtering Drop in `MyBTLEDevice.java` (`line 791`)**:
   `characteristicUpdate()` performs `BluetoothConstants.getCharacteristicUUID(getDeviceType()).equals(characteristic.getUuid())`. For `DeviceType.BIKE_RADAR`, `BluetoothConstants` maps to `UUID_CHARACTERISTIC_RADAR_V1` (`6a4e3203`). Incoming V2 packets on `6a4e3204` fail this check and are silently ignored.
2. **Missing Sensor Type Mappings in `DeviceType.java` (`line 225`)**:
   `getDeviceTypeList(SensorType)` handles `RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, and `RADAR_VEHICLE_COUNT`, but omits `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT`.
3. **Unsequenced MTU & Missing LESC Fallback in `BTLERadarDevice.java` (`line 144`)**:
   `gatt.requestMtu(247)` is executed directly outside `mGattQueue`, followed immediately by `enqueueWriteDescriptor(...)`. In Android BLE, overlapping GATT operations collide. Additionally, Garmin V2 requires LESC bonding; if bonding is absent or CCCD write fails with non-`GATT_SUCCESS`, the peripheral should gracefully fall back to V1 via `fallbackToV1()`.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
- Confirmed `MyBTLEDevice.java` contains no hook for multi-characteristic measurement filtering; extracting `protected boolean isMeasurementCharacteristic(@NonNull UUID uuid)` allows `BTLERadarDevice` to cleanly match both `6a4e3203` and `6a4e3204`.
- Verified `ActivityType.java` line 219 already includes all 6 radar sensors under `BSportType.BIKE`, confirming that only `DeviceType.java` lacks `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT`.
- Verified `MyBTLEDevice` GATT queue lacks `RequestMtuOp` and `onMtuChanged` handling. Implementing `RequestMtuOp` enables deterministic serialized execution.

---

## 4. Chesterton's Fence Archaeology & Invariants
- **Original Formulation (`ATT-5095` / commit `524e937d`)**: V2 parsing and window aggregation were implemented, but GATT routing assumed single-characteristic measurement filtering in `MyBTLEDevice`.
- **System Invariants**:
  1. Backward compatibility: Legacy V1 radar devices (`6a4e3203`) must continue functioning with 100% parity.
  2. Fallback reliability: Any V2 MTU, bonding, or CCCD failure must automatically downgrade to V1 without starvation.
  3. GATT queue serialization: Zero concurrent unmanaged GATT calls; all operations advance sequentially.
  4. Localization & Governance: Zero schema changes; 100% 9-language translation parity.

---

## 5. Scope Bounding & Complexity Evaluation
- **In-Scope**:
  - Extract `isMeasurementCharacteristic(UUID)` in `MyBTLEDevice.java` and override in `BTLERadarDevice.java`.
  - Add `RequestMtuOp`, `enqueueRequestMtu(int)`, and `onMtuChanged` advancement in `MyBTLEDevice.java`.
  - Add `RADAR_PASSING_DISTANCE_m` and `RADAR_LARGE_VEHICLE_COUNT` to `DeviceType.java`.
  - Enforce LESC bonding check and CCCD-failure fallback in `BTLERadarDevice.java`.
  - Wire V2 multi-target traffic generation in `RadarTrafficSimulator.kt`.
- **Out-of-Scope**: Modifications to ANT+ radar profile `0x28` (`ATT-5084`) or UI Edge Overlay rendering (`ATT-5096`).
- **Complexity Estimate**: 2 SP (Standard bugfix and GATT orchestration hardening).
