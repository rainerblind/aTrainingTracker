# Stage 2: Requirement & Test Specification - ATT-6701: Align BIKE_RADAR discovery in BTSearchForNewDevicesEngine, unify MyBTLEDevice GATT read queue, and scope RADAR_* SensorTypes to BSportType.BIKE

**Ticket**: [ATT-6701](https://rainerblind.atlassian.net/browse/ATT-6701)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-028` (*BLE Cycling Radar Protocol Driver, Serialized GATT Queue, Scalar Telemetry & Bike Equipment Integration*)  
**Test Spec ID**: `TST-CON-028`  
**Branch**: `feature/ATT-6701`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-CON-028)

### 1.1 Problem Statement & Rationale
Sprint `2026-46.1` review of `ATT-5083` identified two BLE GATT queuing/discovery discrepancies:
1. `BTSearchForNewDevicesEngine.java`: In `onServicesDiscovered`, an ad-hoc early return check for `DeviceType.BIKE_RADAR` bypassed standard metadata enqueuing (`UUID_CHARACTERISTIC_MANUFACTURER_NAME`, `UUID_CHARACTERISTIC_BATTERY_LEVEL`), while `matchesScanRecord(ScanResult, UUID)` remained as dead code with zero callers.
2. `MyBTLEDevice.java`: Split GATT read queues occurred because legacy `mReadCharacteristicQueue` and `mBatteryReadRunnable` executed `readCharacteristic()` directly outside `mGattQueue`, allowing `onCharacteristicRead` callbacks to prematurely complete `mGattOperationPending` and collide with in-flight descriptor/characteristic writes.
3. `ActivityType.java`: Radar scalar `SensorType`s were previously unscoped, though this was verified and already resolved under `BSportType.BIKE` in commit `e9db6552` (ATT-6726).

### 1.2 Functional & Architectural Requirements
1. **Unified Characteristic Read Execution (`REQ-CON-028.2`)**:
   - `MyBTLEDevice` SHALL serialize all GATT operations (CCCD descriptor writes, characteristic reads, characteristic writes, MTU requests) through single thread-safe FIFO queue `mGattQueue`.
   - `mBatteryReadRunnable` SHALL enqueue battery reads via `enqueueReadCharacteristic(batteryChar)` instead of bypassing the queue.
   - `readNextCharacteristic()` SHALL drain any pending items in `mReadCharacteristicQueue` by forwarding them to `enqueueReadCharacteristic(...)`, ensuring backward compatibility for legacy subclasses.
   - In `onCharacteristicRead`, queue advancement SHALL be handled strictly by `onGattOperationCompleted()`, eliminating redundant `readNextCharacteristic()` invocations.
2. **Aligned Radar Peripheral Discovery (`REQ-CON-028.3`)**:
   - `BTSearchForNewDevicesEngine.onServicesDiscovered` SHALL enqueue Device Information and Battery Level characteristics uniformly for all devices without early-return bypassing.
   - When the characteristic queue empties in `readNextCharacteristic(String address)`, the engine SHALL verify `gatt.getService(BluetoothConstants.UUID_SERVICE_RADAR) != null` for `DeviceType.BIKE_RADAR` before invoking `newDeviceFound(address)`.
   - Scan filter construction in `startAsyncSearch` SHALL be encapsulated in helper `buildScanFilters(DeviceType)`, and unused dead method `matchesScanRecord` SHALL be removed.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-CON-028`, extending `REQ-CON-002` (*Bluetooth LE Sensor Support*), `REQ-CON-014` (*GATT Packet Resilience*), and `REQ-CON-020` (*GATT Queue Integrity*). Targets `DeviceType.java`, `SensorType.java`, `MyBTLEDevice.java`, `BTSearchForNewDevicesEngine.java`, `BTLERadarDevice.java`, `RadarWindowAggregator.kt`.
2. *Historical Origin & Commit Trace*: `ATT-1485` (`REQ-CON-014`) established safe BLE payload parsing; `ATT-3312` introduced chained battery CCCD writes; `ATT-3540` established multi-sensor push architecture; `ATT-6700` refined 1,000 ms rolling latch window, road-clear null distance reset, and workout-start accumulator reset; `ATT-6701` unified `MyBTLEDevice` GATT read queue and aligned `BTSearchForNewDevicesEngine` discovery.
3. *Root Reason for Existing Formulation*: Existing BLE devices relied on single-characteristic notifications or ad-hoc descriptor writes. Multi-target radar requires sequential CCCD setup, packet demuxing, and dual-rate (reactive 8 Hz vs. persisted 1 Hz) handling. In ATT-5083, `mGattQueue` was added for descriptor and control-point writes but legacy battery reads remained on `mReadCharacteristicQueue` with direct GATT calls. Similarly, an early-return guard was placed in `onServicesDiscovered` before metadata reads, and `matchesScanRecord` was added without callers.
4. *Preservation of Core Invariants*: Existing BLE sensors (HRM, Power, Speed, Cadence) remain completely unaffected; zero SQLite schema migrations; 100% 9-language translation parity; vehicle counting hysteresis and Rule 1.8 constructor parity preserved. Unifying reads into `mGattQueue` and verifying radar service at the end of discovery preserves strict FIFO serialization without dropped callbacks, while ensuring Device Information and Battery Level are read consistently across all peripheral types.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Unified GATT Read Serialization)**:
  * *Given* a connected BLE device running periodic battery reads via `mBatteryReadRunnable` while a GATT descriptor write is in-flight,
  * *When* the battery read runnable triggers,
  * *Then* it is enqueued into `mGattQueue` via `enqueueReadCharacteristic(batteryChar)` and does not issue a concurrent `readCharacteristic()` or prematurely clear `mGattOperationPending`.
* **Criterion 2 (Aligned Peripheral Discovery)**:
  * *Given* a BLE search for `DeviceType.BIKE_RADAR` in `BTSearchForNewDevicesEngine`,
  * *When* `onServicesDiscovered` is called,
  * *Then* Device Information and Battery Level characteristics are enqueued first, and `UUID_SERVICE_RADAR` is verified upon queue completion in `readNextCharacteristic` before calling `newDeviceFound`.
* **Criterion 3 (Non-Radar Peripheral Rejection)**:
  * *Given* a BLE peripheral that does not offer `UUID_SERVICE_RADAR`,
  * *When* searched under `DeviceType.BIKE_RADAR`,
  * *Then* `readNextCharacteristic` finishes without calling `newDeviceFound`.
* **Criterion 4 (Dead Code Removal)**:
  * *Given* the `BTSearchForNewDevicesEngine.java` codebase,
  * *When* audited for `matchesScanRecord(ScanResult, UUID)`,
  * *Then* the dead method is completely removed with zero compilation issues.

### 1.5 System Invariants
* Exactly one BLE GATT operation executes concurrently per connected peripheral (`mGattOperationPending` guard).
* Zero changes to SQLite schema or database versions (`DB_VERSION` unchanged).
* Full 9-language localization parity preserved across all resource files.

---

## 2. Test Specification (TST-CON-028)

### Test Case 1: Unified GATT Read Serialization (`TST-CON-028.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDeviceQueueTest.kt`
* **Preconditions**: `TestBTLEDevice` connected to mock GATT with pending descriptor write operation in `mGattQueue`.
* **Action**: Enqueue `ReadCharacteristicOp` (or execute `mBatteryReadRunnable`) and verify operation sequence.
* **Expected Result**: Read characteristic operation waits in `mGattQueue` until preceding descriptor write completes via `onDescriptorWrite`.

### Test Case 2: Aligned BIKE_RADAR Peripheral Discovery (`TST-CON-028.7`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**: `BTSearchForNewDevicesEngine` initialized with `DeviceType.BIKE_RADAR`.
* **Action**: Simulate `onServicesDiscovered` on mock GATT providing Device Info, Battery, and Radar services.
* **Expected Result**: Queue is populated with metadata characteristics, drained via `readNextCharacteristic`, radar service verified, and `mCallbackInterface.onNewDeviceFound` invoked.

### Test Case 3: BIKE_RADAR Discovery Rejection on Missing Radar Service (`TST-CON-028.8`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngineTest.kt`
* **Preconditions**: `BTSearchForNewDevicesEngine` initialized with `DeviceType.BIKE_RADAR`.
* **Action**: Simulate `onServicesDiscovered` on mock GATT providing only HRM/Battery service without Radar service.
* **Expected Result**: Queue drains cleanly; `newDeviceFound` is NOT invoked.

### Test Case 4: 9-Language Parity & String Resource Audit (`TST-CON-028.5`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Preconditions**: All localized XML string files loaded.
* **Action**: Run parity audit across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% key parity with matching format specifiers.

### Test Case 5: Tier 1 Targeted BLE & Engine Regression Suite (`TST-CON-028.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.ActivityTypeTest"`
* **Expected Result**: 100% test pass rate across modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-028.1` | Unit | `MyBTLEDeviceQueueTest.kt` (`MyBTLEDevice`) | `REQ-CON-028.2` | Specified |
| `TST-CON-028.7` | Unit | `BTSearchForNewDevicesEngineTest.kt` (`BTSearchForNewDevicesEngine`) | `REQ-CON-028.3` | Specified |
| `TST-CON-028.8` | Unit | `BTSearchForNewDevicesEngineTest.kt` (`BTSearchForNewDevicesEngine`) | `REQ-CON-028.3` | Specified |
| `TST-CON-028.5` | Localization | `TranslationParityTest.kt` | `REQ-CON-028.6` | Specified |
| `TST-CON-028.6` | Regression | `com.atrainingtracker.banalservice.*` | `REQ-CON-028`, `REQ-PRO-001` | Specified |
