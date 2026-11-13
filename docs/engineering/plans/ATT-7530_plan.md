# Stage 3: Implementation Plan - ATT-7530: Fix Orphaned RR_INTERVAL Sensor Across Reconnects

**Ticket**: [ATT-7530](https://rainerblind.atlassian.net/browse/ATT-7530)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-CON-027.6` (`REQ-CON-027.6a`..`REQ-CON-027.6d`)  
**Test Mapping**: `TST-CON-027.6`..`TST-CON-027.8`  
**Branch**: `feature/ATT-7530` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Description & Background
When a BLE (e.g. Polar H10) or ANT+ HR sensor reconnects during tracking, the live Tracking Tab `RR_INTERVAL` tile freezes at `-- ms` while the sensor detail dialog shows live data. `unregisterSensors()` nulled `mRrIntervalSensor` without unregistering from `MyDevice.mSensorMap` or `MySensorManager`. Upon reconnect, replacement sensor #2 was added and appended at index 1 of `mAllSensorsMapMap`, while `ProxySensor(RR_INTERVAL)` remained bound to orphaned instance #1 at index 0.

---

## 2. Traceability & Requirements Mapping
* `REQ-CON-027.6a`: Teardown `mRrIntervalSensor` in `BTLEHeartRateDevice` & `ANTHeartRateDevice` $\to$ `TST-CON-027.6`.
* `REQ-CON-027.6b`: De-duplicate same-device sensors in `MyDevice.addSensor()` / `addAndRegisterSensor()` $\to$ `TST-CON-027.7`.
* `REQ-CON-027.6c`: De-duplicate same-device sensors in `MySensorManager.registerSensor()` $\to$ `TST-CON-027.7`.
* `REQ-CON-027.6d`: Re-bind `ProxySensor` to active replacement sensor in `MySensorManager` $\to$ `TST-CON-027.7`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Optical Sensor Regressions**: Optical HR straps without Bit 4 RR never allocate `mRrIntervalSensor`.
2. **Lock Order Inversion Prevention**: `ProxySensor.setSourceSensor()` dispatches outside `synchronized (this)`.
3. **Thread Safety**: Sensor map updates synchronized on `this` device and manager monitors.
4. **Clean Sensor Priority**: Sensor priority ordering (`SPEED_PRIORITY`, `CADENCE_PRIORITY`, etc.) preserved.

---

## 4. Proposed Architectural Changes

### Component 1: Peripheral Sensor Teardown (`REQ-CON-027.6a`)
* In `BTLEHeartRateDevice.java` & `ANTHeartRateDevice.java`, in `unregisterSensors()` and `shutDown()`:
  * If `mRrIntervalSensor != null`: call `removeSensor(SensorType.RR_INTERVAL)`, call `mMySensorManager.unregisterSensor(mRrIntervalSensor)`, and set `mRrIntervalSensor = null`.

### Component 2: Device Sensor De-duplication (`REQ-CON-027.6b`)
* In `MyDevice.java`, in `addSensor()`: check if `mSensorMap.get(sensorType)` exists and differs from `mySensor`. If so, deactivate and unregister the previous instance from `mMySensorManager`.

### Component 3: Sensor Manager De-duplication & Proxy Re-binding (`REQ-CON-027.6c`, `REQ-CON-027.6d`)
* In `MySensorManager.java`, in `registerSensor()`: in `mAllSensorsMapMap.get(sensorType).get(deviceType)`, remove any existing sensor where `s.getDevice() == mySensor.getDevice()` before `addLast(mySensor)`. Ensure `setBestSensorForProxySensor(sensorType)` re-binds `ProxySensor` to the new active sensor.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence

### Step 1: Teardown in Heart Rate Devices (`REQ-CON-027.6a`)
* Files: `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`.
* Actions: Unregister and remove `mRrIntervalSensor` on disconnect / teardown.

### Step 2: Same-Device De-duplication in MyDevice (`REQ-CON-027.6b`)
* File: `MyDevice.java`.
* Actions: Guard `addSensor()` to unregister and deactivate superseded same-type sensors.

### Step 3: De-duplication & Proxy Re-binding in MySensorManager (`REQ-CON-027.6c`, `REQ-CON-027.6d`)
* File: `MySensorManager.java`.
* Actions: Filter same-device entries before `addLast()` and update proxy source.

### Step 4: Unit & Contract Tests (`TST-CON-027.6`..`TST-CON-027.8`)
* Files: `BTLEHeartRateDeviceTest.kt`, `ANTHeartRateDeviceTest.kt`, `MySensorManagerProxyTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted test suite and `verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows full revert (`git reset --hard HEAD~1`) without affecting `sprint/2026-46.5`.
