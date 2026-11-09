# Stage 3: Implementation Plan - ATT-6257: Persistent Sensor Proxy Binding & Automatic Filter Re-Connection

**Ticket**: [ATT-6257](https://rainerblind.atlassian.net/browse/ATT-6257)  
**Sub-task**: [ATT-6302](https://rainerblind.atlassian.net/browse/ATT-6302) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6257`  
**Requirement Mapping**: `REQ-TRK-055` (`REQ-TRK-055.1`..`REQ-TRK-055.4`) | **Test Mapping**: `TST-TRK-055` (`TST-TRK-055.1`..`TST-TRK-055.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  

---

## 1. Problem Description & Objective
Cockpit tiles (`SPEED_mps`, `DISTANCE_m`, `ALTITUDE`, `SLOPE`) and `VerticalSpeedAndSlopeDevice` read through `FilterManager`. When a primary sensor unregisters (e.g. GPS loss/recovery via `LocationUnavailable()`, BLE/ANT+ reconnect, pairing change, or 5-min background service timeout), `MySensorManager` removes the `ProxySensor`, creating a new instance on reconnection. `FilterManager.createFilter()` skips already-registered hashkeys and `mSensorsChangedReceiver` only checks pending filters, leaving existing filters permanently bound to orphaned or discarded sensor instances. This plan introduces persistent `ProxySensor` retention in `MySensorManager`, dynamic filter re-binding in `FilterManager`, and static reference cleanup in `BANALService.onDestroy()`.

---

## 2. Requirements & Living Documentation Mapping
* **`REQ-TRK-055.1` (Persistent ProxySensor Retention)**: `MySensorManager` retains `ProxySensor` in `mSensorMap` when `bestSensor == null`, dispatching `proxy.setSourceSensor(null)`. On reconnect, it reuses the proxy and calls `proxy.setSourceSensor(newBestSensor)`. Disconnected proxies are excluded from `computeGCDataLocked()` and `getAvailableSensorTypeSet()`.
* **`REQ-TRK-055.2` (Dynamic Filter Re-Binding in FilterManager)**: `FilterManager.createFilter()` checks if the backing sensor instance has changed and re-binds listeners. On `SENSORS_CHANGED`, `FilterManager` audits existing filters in `myFilterMap` and rebinds changed sensors.
* **`REQ-TRK-055.3` (Named Device Filter Re-Binding)**: For named devices (`deviceName != null`), when devices are recreated in `DeviceManager`, `FilterManager` rebinds filters to new `MySensor` instances.
* **`REQ-TRK-055.4` (Defensive Service Teardown & Static Hygiene)**: `BANALService.onDestroy()` resets static references (`cDeviceManager`, `cSensorManager`, `cFilterManager`) to `null`. `DeviceManager.shutDown()` nullifies device instances.
* **Test Mapping**: `TST-TRK-055.1` (`MySensorManagerProxyTest`), `TST-TRK-055.2` & `TST-TRK-055.3` (`FilterManagerRebindTest`), `TST-TRK-055.4` (`BANALServiceLifecycleTest`), `TST-TRK-055.5` (Targeted suite).

---

## 3. System Invariants & UI Consistency
1. **Zero Lock-Order Inversion**: `ProxySensor.setSourceSensor()` executes listener migration strictly outside monitor locks (`REQ-SEN-021.2/.3`).
2. **Sensor Priority Hierarchy**: Selection priority (`SPEED_PRIORITY`, `CADENCE_PRIORITY`) remains strictly preserved (`REQ-CON-003`).
3. **Accumulator Reset Baseline**: Accumulators retain baseline zero-seeding (`REQ-FIL-013`).
4. **UI Consistency**: No UI changes (`docs/design_guidelines.md` §5). Pure backend telemetry service/filter synchronization.

---

## 4. Architectural Strategy & Component Design
* **`MySensorManager.java`**:
  * In `unregisterSensor()`: when `newBestSensor == null`, do NOT call `removeSensor(sensorType)`; set `sensorRemoved = true` for GC data update.
  * In `setBestSensorForProxySensor()`: retain `ProxySensor` in `mSensorMap`; call `proxy.setSourceSensor(bestSensor)` (with `null` when no source).
  * In `registerSensor()`: if `getSensor(sensorType)` is already a `ProxySensor`, reuse it instead of instantiating a new one.
  * In `computeGCDataLocked()` & `BANALService.getAvailableSensorTypeSet()`: skip `ProxySensor`s where `getSourceSensor() == null`.
* **`FilterManager.java`**:
  * Add `resolveSensor(String deviceName, SensorType sensorType)` and `rebindFilter(MyFilter filter, MySensor oldSensor, MySensor newSensor)`.
  * In `createFilter()`: if `myFilterMap.containsKey(hashKey)`, check if current sensor differs from `mMapFilter2Sensor.get(filter)`. If so, rebind.
  * In `mSensorsChangedReceiver`: iterate `myFilterMap.values()`, rebind any filter whose resolved active sensor changed, and process `mRequiredButNotAvailableFilters`.
* **`BANALService.java` & `DeviceManager.java`**:
  * In `BANALService.onDestroy()`: set `cDeviceManager = null`, `cSensorManager = null`, `cFilterManager = null`.
  * In `DeviceManager.shutDown()`: set device instance fields (`mSpeedAndLocationDevice_GPS`, `mVerticalSpeedAndSlopeDevice`, etc.) to `null`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check
* Verify Gate 3 PASS before modifying production code: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.

### Step 2: Implement Persistent `ProxySensor` in `MySensorManager.java` & `BANALService.java`
* Retain `ProxySensor` on unregister; reuse on register; filter out unbound proxies in `computeGCDataLocked()` and `getAvailableSensorTypeSet()`.

### Step 3: Implement Dynamic Filter Re-Binding in `FilterManager.java`
* Implement `resolveSensor()`, `rebindFilter()`, and `rebindExistingFilters()`. Wire into `createFilter()` and `mSensorsChangedReceiver`.

### Step 4: Implement Lifecycle Teardown in `BANALService.java` & `DeviceManager.java`
* Nullify static manager fields in `BANALService.onDestroy()` and device references in `DeviceManager.shutDown()`.

### Step 5: Unit & Contract Tests
* Author `MySensorManagerProxyTest.kt`, `FilterManagerRebindTest.kt`, and `BANALServiceLifecycleTest.kt`.

### Step 6: Targeted Test Verification
* Run targeted tests: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*" --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.banalservice.devices.*"`
