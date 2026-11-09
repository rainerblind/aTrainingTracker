# Stage 5: Walkthrough & Verification - ATT-6257: Persistent Sensor Proxy Binding & Automatic Filter Re-Connection

**Ticket**: [ATT-6257](https://rainerblind.atlassian.net/browse/ATT-6257)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6257`  
**Requirement Mapping**: `REQ-TRK-055` (`REQ-TRK-055.1`–`REQ-TRK-055.4`) | **Test Mapping**: `TST-TRK-055` (`TST-TRK-055.1`–`TST-TRK-055.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Executive Summary & Verification Overview
Resolved Cockpit telemetry tile (`SPEED_mps`, `DISTANCE_m`, `ALTITUDE`, `SLOPE`) and derived sensor device (`VerticalSpeedAndSlopeDevice`) freezing/dropouts caused by sensor disconnect/reconnect and service lifecycles:
* **Persistent `ProxySensor` Retention (`REQ-TRK-055.1`)**: `MySensorManager` retains `ProxySensor` instances in `mSensorMap` across sensor unregister/register cycles instead of deleting them. Calls `proxy.setSourceSensor(null)` on disconnect and `proxy.setSourceSensor(newBestSensor)` on reconnect, preserving listener continuity and preventing orphaned filter bindings.
* **Dynamic Sensor Filter Re-Binding (`REQ-TRK-055.2`, `REQ-TRK-055.3`)**: `FilterManager` tracks all filter specifications (`mAllFilterData`) and dynamically checks backing sensor instances in `createFilter()` and upon receiving `BANALService.SENSORS_CHANGED`. Migrates filter listeners to active sensors for both best sensors and recreated peripheral devices.
* **Defensive Lifecycle Teardown & Static Hygiene (`REQ-TRK-055.4`)**: `BANALService.onDestroy()` nullifies static manager references (`cDeviceManager`, `cSensorManager`, `cFilterManager`). `DeviceManager.shutDown()` nullifies device instances. `TrainingApplication` and `DevicesDatabaseManager` hardened with defensive null and resource exception guards.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-055.1` | `TST-TRK-055.1` | `MySensorManagerProxyTest` (Proxy retention, source switching, listener migration) | **PASSED** | `Verified` |
| `REQ-TRK-055.2` | `TST-TRK-055.2` | `FilterManagerRebindTest` (Dynamic re-binding in `createFilter` & `SENSORS_CHANGED`) | **PASSED** | `Verified` |
| `REQ-TRK-055.3` | `TST-TRK-055.3` | `FilterManagerRebindTest` (Named peripheral device re-creation re-binding) | **PASSED** | `Verified` |
| `REQ-TRK-055.4` | `TST-TRK-055.4` | `BANALServiceLifecycleTest` (`onDestroy` static nullification, device cleanup) | **PASSED** | `Verified` |
| `REQ-TRK-055` | `TST-TRK-055.5` | Targeted test suite wildcard execution under `parallel_sprint_lock.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope
* **Executed Scope**:
  * `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: `BUILD SUCCESSFUL` (100% PASS, 519 executed unit and concurrency stress tests, 0 failures).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` -> `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`
* **On-Device Status**: No physical device connected in `adb devices`; software emulator prohibited under Core Rule 79. Verified via unit and component lifecycle tests.

---

## 4. Key Implementation Highlights
* **Listener Continuity**: Reusing `ProxySensor` avoids dropping listener registrations during transient sensor disconnects (e.g. GPS dropouts or BLE reconnects).
* **Dynamic Re-Binding**: `FilterManager.rebindFilter()` migrates listeners and re-seeds current values without restarting filters or resetting active accumulators.
* **Service Restart Safety**: Nullifying static managers in `BANALService.onDestroy()` ensures service re-creations instantiate fresh managers bound to active hardware.
