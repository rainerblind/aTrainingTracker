# Stage 1 Analysis: ATT-7530 - Fix Orphaned RR_INTERVAL Sensor Across BLE/ANT+ Reconnects

**Ticket**: [ATT-7530](https://rainerblind.atlassian.net/browse/ATT-7530)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.5`  
**Branch**: `feature/ATT-7530`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
When a BLE (e.g. Polar H10) or ANT+ heart rate strap disconnects and reconnects during tracking, the live Tracking Tab sensor tile for `RR_INTERVAL` permanently freezes at `-- ms`, while the Polar H10 sensor detail dialog displays live, updating RR data (`RR: 722`). This creates user confusion and breaks live RR-interval cockpit monitoring during outdoor workouts.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* In `BTLEHeartRateDevice.unregisterSensors()` and `ANTHeartRateDevice.unregisterSensors()`, `mRrIntervalSensor = null;` is executed on disconnect without calling `mMySensorManager.unregisterSensor(mRrIntervalSensor)` or `removeSensor(SensorType.RR_INTERVAL)`.
* Upon reconnect, arrival of the first RR packet sees `mRrIntervalSensor == null`, instantiates instance `#2`, and calls `addAndRegisterSensor(#2)`.
* In `MyDevice.addSensor()`, `mSensorMap.put(RR_INTERVAL, #2)` overwrites `#1` in the device's map (so `MyDevice.getAllSensorData()` in the sensor dialog shows live data).
* However, in `MySensorManager.registerSensor()`, `#2` is appended (`addLast()`) to `mAllSensorsMapMap.get(RR_INTERVAL).get(deviceType)`. Since `#1` was never unregistered, `sensorList` contains `[#1, #2]`.
* Consequently, `MySensorManager.getBestSensor(RR_INTERVAL)` (`sensorList.peekFirst()`) and `ProxySensor(RR_INTERVAL)` remain bound to dead instance `#1` at index 0 (value `null`), while live RR values are dispatched exclusively to `#2` at index 1.
* **Independent Peer Review & Verification**:
  * Confirmed in `BTLEHeartRateDevice.java:288`, `ANTHeartRateDevice.java:293`, `MyDevice.java:175-182` (which contains `TODO; maybe, we should check whether this sensor is already added and registered???`), and `MySensorManager.java:220` (`addLast`).
  * Verified that tearing down `mRrIntervalSensor` in `unregisterSensors()` AND de-duplicating same-device sensors in `MyDevice.addSensor()` and `MySensorManager.registerSensor()` prevents orphaned instances completely.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Explicitly unregister and remove `mRrIntervalSensor` in `BTLEHeartRateDevice.unregisterSensors()` and `ANTHeartRateDevice.unregisterSensors()`.
  * Guard `MyDevice.addSensor()` / `addAndRegisterSensor()` to deactivate and unregister any prior instance of the same `SensorType`.
  * Guard `MySensorManager.registerSensor()` to remove any existing instance from the same `MyDevice` before `addLast()`, and trigger `setBestSensorForProxySensor()`.
  * Add unit tests in `BTLEHeartRateDeviceTest`, `ANTHeartRateDeviceTest`, `MySensorManagerProxyTest`, and `MyDeviceTest`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Kubios/Lipponen artifact filters, DFA $\alpha_1$ windowing, or FIT export pipelines.
  * Modifying Bluetooth LE connection management or BLE GATT MTU negotiation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-027` (*Beat-to-Beat RR-Interval Acquisition, BLE GATT 0x2A37 Multi-RR Unpacking & ANT+ Continuity Guard*), targeting `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`, `MyDevice.java`, and `MySensorManager.java`.
* **Historical Origin & Commit Trace**: `ATT-3540` (`98df60c9`) added lazy `mRrIntervalSensor` allocation; `ATT-4096`/`ATT-4097` (`REQ-TRK-055`) introduced persistent `ProxySensor` retention across disconnects.
* **Root Reason for Existing Formulation**: `mRrIntervalSensor` was lazily created to economize memory for optical HR straps omitting Bit 4. `unregisterSensors()` nulled the field locally assuming garbage collection without considering `MySensorManager`'s internal multi-device list.
* **Preservation of Core Invariants**: Optical HR-only sensors without Bit 4 RR intervals continue operating without creating `mRrIntervalSensor`; `ProxySensor` thread safety outside monitors is preserved; zero GATT crashes.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-027` (amended with `REQ-CON-027.6` for clean teardown and reconnect continuity).
* **Affected Architecture Elements**: `BTLEHeartRateDevice.java`, `ANTHeartRateDevice.java`, `MyDevice.java`, `MySensorManager.java`.
* **Verification Impact & Test Surface**: `BTLEHeartRateDeviceTest.kt`, `ANTHeartRateDeviceTest.kt`, `MySensorManagerProxyTest.kt`, `MyDeviceTest.kt`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Eliminates memory leak and list pollution; protects all `MyDevice` subclasses from duplicate sensor registrations; zero API breaks.
