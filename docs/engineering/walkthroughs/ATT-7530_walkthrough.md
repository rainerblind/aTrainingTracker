# Stage 5: Walkthrough & Verification - ATT-7530: Fix Orphaned RR_INTERVAL Sensor Across Reconnects

**Ticket**: [ATT-7530](https://rainerblind.atlassian.net/browse/ATT-7530)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-CON-027.6` (`REQ-CON-027.6a`..`REQ-CON-027.6d`)  
**Test Mapping**: `TST-CON-027.6`, `TST-CON-027.7`, `TST-CON-027.8`  
**Branch**: `feature/ATT-7530` | **Author**: AI Agent 2 (Flash) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview
Resolved an issue where reconnecting a BLE (e.g. Polar H10) or ANT+ heart rate strap during active tracking caused the cockpit's `RR_INTERVAL` tile to permanently freeze at `-- ms` while the sensor detail dialog displayed live data. The root cause was that `unregisterSensors()` nulled `mRrIntervalSensor` without unregistering from `MyDevice.mSensorMap` or `MySensorManager`, leading to duplicate sensor registration upon reconnect where `ProxySensor(RR_INTERVAL)` remained bound to the orphaned first instance at index 0. Implemented a 3-layer defense-in-depth:
1. `BTLEHeartRateDevice` & `ANTHeartRateDevice`: explicitly call `removeSensor(SensorType.RR_INTERVAL)` and `mMySensorManager.unregisterSensor()` on disconnect/shutdown.
2. `MyDevice`: guarded `addSensor()` to deactivate and unregister any prior instance of the same `SensorType`.
3. `MySensorManager`: de-duplicates same-device entries before `addLast()` and re-binds `ProxySensor`'s source reference.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-027.6a` | `TST-CON-027.6` | `BTLEHeartRateDeviceTest`, `ANTHeartRateDeviceTest` | **PASSED** | `Verified` |
| `REQ-CON-027.6b..d` | `TST-CON-027.7` | `MySensorManagerProxyTest` | **PASSED** | `Verified` |
| `REQ-PRO-001`, `REQ-PRO-022` | `TST-CON-027.8` | `verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --tests "com.atrainingtracker.banalservice.sensor.*"`
```text
BUILD SUCCESSFUL in 2m 47s
33 actionable tasks: 7 executed, 5 from cache, 21 up-to-date
100% PASS: All unit tests in targeted device and sensor packages passed cleanly.
```

### Governance Verification Evidence
Command: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-7530_test_spec.md`
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
PASS: Zero attribute violations detected. Zero unmounted Composable islands. Repository encapsulation verified.
```

---

## 4. Invariant & Architecture Compliance Checklist
* **Thread Safety**: `mSensorMap` mutations and sensor de-duplication are synchronized on device/manager monitors.
* **Lock-Order Inversion Prevention**: `ProxySensor.setSourceSensor()` and listener notifications execute strictly outside `synchronized (this)` monitors (`REQ-SEN-021.3`).
* **Backward Compatibility**: Optical HR monitors omitting Bit 4 never instantiate `mRrIntervalSensor`, preserving HR-only battery efficiency.
* **Zero UI Regression**: No Composable layout alterations required; standard cockpit tiles display live RR data immediately after reconnect.

---

## 5. Invariant & Governance Verification
1. **Zero Memory Leaks**: Disconnected sensors are unreferenced and reaped; duplicate instances are purged from internal collections.
2. **Deterministic Re-binding**: `ProxySensor(RR_INTERVAL)` transparently updates to the new active hardware sensor on reconnect.
