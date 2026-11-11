# Stage 1 Analysis: ATT-6701 - Align BIKE_RADAR discovery in BTSearchForNewDevicesEngine, unify MyBTLEDevice GATT read queue, and scope RADAR_* SensorTypes to BSportType.BIKE

**Ticket**: [ATT-6701](https://rainerblind.atlassian.net/browse/ATT-6701)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6701`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 Joint Review of ATT-5083, three architectural and lifecycle discrepancies were identified across BLE peripheral discovery, GATT queue management, and sport-scoping:
1. **Divergent Discovery & Dead Code in `BTSearchForNewDevicesEngine.java`**:
   - In `onServicesDiscovered`, an ad-hoc early return check for `DeviceType.BIKE_RADAR` (checking `UUID_SERVICE_RADAR` before enqueuing Device Information or Battery characteristics) diverges from surrounding structure where Manufacturer and Battery characteristics are read first, and service/feature verification happens cleanly at the end of `readNextCharacteristic(String address)` (lines 507–518, alongside `BIKE_POWER`).
   - In `startAsyncSearch`, scan filter construction for `BIKE_RADAR` uses an inline conditional branch, while `matchesScanRecord(ScanResult, UUID)` was added at the bottom with zero production callers across the entire repository (violating Rule 5.3).
2. **Split-Brain GATT Queues in `MyBTLEDevice.java`**:
   - `ATT-5083` introduced `mGattQueue` (`enqueueWriteDescriptor`, `enqueueReadCharacteristic`, `enqueueWriteCharacteristic`) for serialized GATT operations, but left the legacy `mReadCharacteristicQueue` (`readNextCharacteristic()` and periodic `mBatteryReadRunnable`) calling `gatt.readCharacteristic(...)` directly outside `mGattQueue`.
   - `onCharacteristicRead` (line 557) unconditionally calls `onGattOperationCompleted()`, which prematurely clears `mGattOperationPending` and advances `mGattQueue` if a periodic battery read finishes while a CCCD descriptor write or characteristic write is pending, creating `GATT_BUSY` (132/0x84) race conditions.
3. **Unscoped `RADAR_*` SensorTypes in `ActivityType.java`**:
   - Radar scalar sensors (`RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT`, `RADAR_PASSING_DISTANCE_m`, `RADAR_LARGE_VEHICLE_COUNT`) were originally appended across all activity types (including `RUN_SPEED_AND_CADENCE`, `GENERIC_HR`), although radar is strictly a cycling peripheral (`BSportType.BIKE`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Hypothesis 1 (`BTSearchForNewDevicesEngine.java`) — Verified**:
    Inspection of lines 130–136 confirms `UUID_SERVICE_RADAR` check prematurely short-circuits `onServicesDiscovered` before Device Info (`UUID_CHARACTERISTIC_MANUFACTURER_NAME`) and Battery Level (`UUID_CHARACTERISTIC_BATTERY_LEVEL`) are enqueued. In contrast, `BIKE_POWER` (lines 507–518) verifies service availability in `readNextCharacteristic()` once metadata characteristics are drained. Lines 524–536 confirm `matchesScanRecord()` has zero callers across the codebase.
  * **Hypothesis 2 (`MyBTLEDevice.java`) — Verified**:
    Lines 441–454 (`mBatteryReadRunnable`) add to `mReadCharacteristicQueue` and invoke `readNextCharacteristic()` directly on the GATT client. Meanwhile, `onCharacteristicRead` (line 557) unconditionally invokes `onGattOperationCompleted()`. If `mGattQueue` had an inflight descriptor write (e.g. CCCD enable) or write command, the battery read completion prematurely releases `mGattOperationPending`, risking concurrency collisions and dropped callbacks on the BLE controller.
  * **Hypothesis 3 (`ActivityType.java`) — Verified & Already Resolved**:
    Code archaeology reveals that commit `e9db6552` (ATT-6726: *EditSensorFieldDialog & SensorType*) already scoped all 6 `RADAR_*` metrics strictly under `if (activityType != null && activityType.getSportType() == BSportType.BIKE)` in `ActivityType.java` (lines 234–243) and verified by `ActivityTypeTest.kt` (`testGetSensorTypeArray_radarScopedToCyclingOnly()`). This part of the ticket is already verified and intact on `sprint/2026-46.2`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Refactor `BIKE_RADAR` in `BTSearchForNewDevicesEngine.java` to follow the standard `onServicesDiscovered` -> `readNextCharacteristic(address)` flow, reading Device Info & Battery before verifying `UUID_SERVICE_RADAR`.
  * Clean up scan filter construction in `BTSearchForNewDevicesEngine.startAsyncSearch` and remove the dead `matchesScanRecord()` helper.
  * Unify characteristic reads in `MyBTLEDevice.java` (`mBatteryReadRunnable` and `mReadCharacteristicQueue`) into `mGattQueue` (`enqueueReadCharacteristic`), eliminating race conditions in `onCharacteristicRead`.
  * Maintain backward compatibility for `readNextCharacteristic()` and service discovery state transitions to `State.CONNECTED_WITH_SERVICE`.
  * Add unit tests covering unified GATT read queue serialization and `BTSearchForNewDevicesEngine` radar discovery.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to radar protocol decoders (`BTLERadarDevice`, `RadarStateRepository`).
  * No modification to ANT+ radar acquisition (`MyANTDevice`).
  * No modification to database schema or UI layer.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-CON-028.2` (Serialized GATT Queue), `REQ-CON-028.3` (Resilient BLE Scanner Filtering), `REQ-TRK-070.3` (Radar Sensor Scoping).
* **Historical Origin & Commit Trace**:
  * `dfbe39791ab8` (ATT-5083: BLE Cycling Radar protocol driver, domain model, scalar SensorTypes).
  * `e9db6552086f` (ATT-6726: Scoped `RADAR_*` sensors to `BSportType.BIKE`).
* **Root Reason for Existing Formulation**:
  * `mReadCharacteristicQueue` was the legacy Android BLE discovery queue pre-dating the introduction of `mGattQueue` in ATT-5083. During ATT-5083, `mGattQueue` was added for CCCD descriptor writes and radar characteristic control, but legacy battery reads were left using `mReadCharacteristicQueue`, resulting in split queues.
  * `matchesScanRecord` was added during ATT-5083 as a candidate software fallback filter, but was superseded by Android `ScanFilter` hardware matching.
* **Preservation of Core Invariants**:
  * Unifying all reads into `mGattQueue` preserves strictly FIFO serialized execution across all GATT operations (descriptor writes, characteristic reads, characteristic writes, MTU requests), eliminating `GATT_BUSY` and dropped callbacks.
  * Initial service discovery sensor registration (`registerSensors()`) and battery reads will continue to execute seamlessly upon connection.

---

## 5. Architectural Strategy & High-Level Solution

1. **`BTSearchForNewDevicesEngine.java`**:
   - In `onServicesDiscovered`: Remove the early return `if (getDeviceType() == DeviceType.BIKE_RADAR)` guard at lines 130–136. Allow `mReadCharacteristicQueue` to enqueue Device Info and Battery characteristics normally.
   - In `readNextCharacteristic(String address)`: When the queue is empty, include `DeviceType.BIKE_RADAR` alongside `DeviceType.BIKE_POWER`. Check `gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) != null` before invoking `newDeviceFound(address)`.
   - In `startAsyncSearch`: Refactor scan filter creation into a clean helper method `buildScanFilters(DeviceType)`.
   - Remove unused dead method `matchesScanRecord(ScanResult, UUID)`.
2. **`MyBTLEDevice.java`**:
   - Route `mBatteryReadRunnable` through `enqueueReadCharacteristic(batteryChar)` instead of `mReadCharacteristicQueue.add(...)` and `readNextCharacteristic()`.
   - Refactor `readNextCharacteristic()` to drain `mReadCharacteristicQueue` by delegating items to `enqueueReadCharacteristic(...)`. Once drained (or if empty), transition state to `State.CONNECTED_WITH_SERVICE` and call `registerSensors()`.
   - In `onCharacteristicRead`: Rely solely on `onGattOperationCompleted()` (which advances `mGattQueue`), removing redundant and conflicting `readNextCharacteristic()` call.
3. **`ActivityType.java`**:
   - Validate and ensure existing `BSportType.BIKE` scoping remains fully covered by unit tests.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. BLE GATT operations are strictly serialized: exactly one operation executing at any time on the BLE controller.
  2. Peripheral discovery identifies valid Garmin Varia and compatible radar devices while rejecting non-radar peripherals when searching for `DeviceType.BIKE_RADAR`.
  3. All unit tests pass with zero regressions.
* **Risk Rating**: **LOW**
  - Well-isolated refactoring in BLE discovery and GATT queue management with comprehensive unit test coverage and no database or cross-service side effects.
