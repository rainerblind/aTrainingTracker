# Stage 2: Requirement & Test Specification - ATT-7530: Fix Orphaned RR_INTERVAL Sensor Across Reconnects

**Ticket**: [ATT-7530](https://rainerblind.atlassian.net/browse/ATT-7530)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-CON-027` (`REQ-CON-027.6`) (*Clean Sensor Teardown, De-duplication & Reconnect Continuity*)  
**Test Spec ID**: `TST-CON-027` (`TST-CON-027.6`–`TST-CON-027.8`) | **Branch**: `feature/ATT-7530`  
**Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-CON-027.6)

### 1.1 Problem Statement & Rationale
When a BLE (e.g. Polar H10) or ANT+ HR sensor reconnects during tracking, the live Tracking Tab `RR_INTERVAL` tile permanently freezes at `-- ms` while the sensor detail dialog shows live data. This is caused by `unregisterSensors()` setting `mRrIntervalSensor = null` without unregistering from `MyDevice.mSensorMap` or `MySensorManager`. Upon reconnect, a second sensor is allocated and appended at index 1 of `mAllSensorsMapMap`, leaving `ProxySensor(RR_INTERVAL)` permanently bound to dead instance #1 at index 0.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `BTLEHeartRateDevice.unregisterSensors()` and `ANTHeartRateDevice.unregisterSensors()` SHALL explicitly invoke `removeSensor(SensorType.RR_INTERVAL)` before setting `mRrIntervalSensor = null` (`REQ-CON-027.6a`).
2. `MyDevice.addSensor(mySensor)` and `addAndRegisterSensor(mySensor)` SHALL verify if a sensor of `mySensor.getSensorType()` already exists in `mSensorMap`. If a previous instance exists and differs from `mySensor`, `MyDevice` SHALL deactivate and unregister it from `MySensorManager` before registering the replacement (`REQ-CON-027.6b`).
3. `MySensorManager.registerSensor(mySensor)` SHALL remove any existing `MySensor` belonging to the same `MyDevice` (`existing.getDevice() == mySensor.getDevice()`) from `sensorList` before `addLast(mySensor)`, preventing duplicate same-device entries and list ordering pollution (`REQ-CON-027.6c`).
4. When a replacement or lazy sensor is registered in `MySensorManager`, `setBestSensorForProxySensor(sensorType)` SHALL immediately update `ProxySensor`'s source reference to the active sensor (`REQ-CON-027.6d`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (BLE & ANT+ Reconnect Continuity)**:
  * *Given* a connected BLE or ANT+ HR strap streaming RR intervals with `ProxySensor(RR_INTERVAL)` active,
  * *When* the sensor disconnects and reconnects and new RR packets arrive,
  * *Then* `ProxySensor(RR_INTERVAL)` updates its source to the newly registered instance and the cockpit displays live RR values.
* **AC2 (No Duplicate Same-Device Sensors)**:
  * *Given* `MyDevice` registers a sensor for a `SensorType` that already has an instance for that same device,
  * *When* registered,
  * *Then* the previous instance is unregistered and replaced in `MySensorManager` with `getBestSensor()` resolving to the active instance.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-CON-027`, extending `REQ-CON-002`, `REQ-CON-003`, `REQ-CON-014`. Targets `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`, `MyDevice.java`, `MySensorManager.java`.
* `Historical Origin & Commit Trace`: `ATT-1485` (null safety); `ATT-3540` (lazy `mRrIntervalSensor` allocation); `ATT-7530` (reconnect continuity).
* `Root Reason for Existing Formulation`: Early code assumed garbage collection would reclaim nulled sensor references, overlooking `mSensorMap` retention and `MySensorManager` list ordering.
* `Preservation of Core Invariants`: Optical HR straps without Bit 4 never allocate `mRrIntervalSensor`; `ProxySensor` thread safety outside monitors is preserved; zero GATT crashes.

---

## 2. Test Specification (TST-CON-027)

### Test Case 1: Clean Sensor Teardown on Disconnect (`TST-CON-027.6`)
* **Scope**: Unit (`BTLEHeartRateDeviceTest.kt`, `ANTHeartRateDeviceTest.kt`)
* **Verification**: Assert calling `unregisterSensors()` removes `RR_INTERVAL` from `mSensorMap` and resets `mRrIntervalSensor = null`.

### Test Case 2: MyDevice and MySensorManager De-duplication (`TST-CON-027.7`)
* **Scope**: Unit (`MySensorManagerProxyTest.kt`, `MyDeviceTest.kt`)
* **Verification**: Assert registering a second sensor for the same `SensorType` replaces the prior instance without duplicate entries in `mAllSensorsMapMap`, and `ProxySensor` updates its source.

### Test Case 3: Tier 1 Targeted Unit Test Regression Suite (`TST-CON-027.8`)
* **Scope**: Integration / Unit
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-027.6` | Unit | `BTLEHeartRateDeviceTest.kt`, `ANTHeartRateDeviceTest.kt` | `REQ-CON-027.6a` | Specified |
| `TST-CON-027.7` | Unit | `MySensorManagerProxyTest.kt`, `MyDeviceTest.kt` | `REQ-CON-027.6b..d` | Specified |
| `TST-CON-027.8` | Tier 1 Regr | Targeted test suites | `REQ-PRO-001` | Specified |
