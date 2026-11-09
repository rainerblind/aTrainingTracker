# Stage 2: Requirement & Test Specification - ATT-6257: Persistent Sensor Proxy Binding & Filter Re-Connection

**Ticket**: [ATT-6257](https://rainerblind.atlassian.net/browse/ATT-6257) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6257`  
**Requirement Mapping**: `REQ-TRK-055` (`REQ-TRK-055.1`..`REQ-TRK-055.4`) | **Test Spec ID**: `TST-TRK-055` (`TST-TRK-055.1`..`TST-TRK-055.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Requirement Specification (`REQ-TRK-055`)

### 1.1 Functional Scope & Technical Invariants
* **Persistent `ProxySensor` Retention (`REQ-TRK-055.1`)**: `MySensorManager` retains its `ProxySensor` per `SensorType` in `mSensorMap` across unregistration/re-registration cycles instead of deleting it. When `newBestSensor == null`, it calls `proxy.setSourceSensor(null)`. On reconnect, it reuses the existing `ProxySensor` and calls `proxy.setSourceSensor(newBestSensor)`. Disconnected sensors are not marked available in `computeGCDataLocked()`.
* **Dynamic Filter Re-Binding in `FilterManager` (`REQ-TRK-055.2`)**: In `createFilter()` and upon receiving `BANALService.SENSORS_CHANGED`, `FilterManager` audits existing filters in `myFilterMap`. If the active sensor differs from `mMapFilter2Sensor`, it migrates listeners to the active sensor and updates the map.
* **Device-Specific Filter Re-Binding (`REQ-TRK-055.3`)**: For named devices (`deviceName != null`), when `DeviceManager` recreates devices with new `MySensor` instances, `FilterManager` dynamically rebinds device-specific filters on `SENSORS_CHANGED`.
* **Service Lifecycle & Static Reference Hygiene (`REQ-TRK-055.4`)**: `BANALService.onDestroy()` resets static manager references (`cDeviceManager`, `cSensorManager`, `cFilterManager`) to `null`. `DeviceManager.shutDown()` nullifies device instances, preventing stale references on service restart.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Reconnection Continuity)**: *Given* an active workout with Cockpit filters on `SPEED_mps`, `DISTANCE_m`, and `ALTITUDE`, *When* the primary sensor unregisters and re-registers, *Then* existing filters continue receiving samples through the persistent `ProxySensor`.
* **AC-2 (Dynamic Re-Binding)**: *Given* an existing filter bound to an old sensor instance, *When* a new sensor instance registers and `SENSORS_CHANGED` fires, *Then* `FilterManager` rebinds the listener and delivers samples without recreating the filter.
* **AC-3 (Service Restart Hygiene)**: *Given* `BANALService` is stopped and restarted, *Then* static managers reinitialize cleanly with no references to shut-down instances.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-055`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-055.1` | `TST-TRK-055.1` | `MySensorManagerProxyTest` | Persistent `ProxySensor` retention, `setSourceSensor(null)` on unregister, source update on reconnect |
| `REQ-TRK-055.2` | `TST-TRK-055.2` | `FilterManagerRebindTest` | Dynamic re-binding of existing filters in `createFilter()` and on `SENSORS_CHANGED` broadcast |
| `REQ-TRK-055.3` | `TST-TRK-055.3` | `FilterManagerRebindTest` | Device-specific filter re-binding to new `MySensor` instances after peripheral device re-creation |
| `REQ-TRK-055.4` | `TST-TRK-055.4` | `BANALServiceLifecycleTest` | Static managers nullified in `onDestroy()`; clean re-instantiation and device reference cleanup |
| `REQ-TRK-055` | `TST-TRK-055.5` | Targeted Test Suite | Package wildcard tests in `com.atrainingtracker.banalservice.filters.*` and `.sensor.*` |
