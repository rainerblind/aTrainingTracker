# Stage 2: Requirement & Test Specification - ATT-6381: BTLERunSpeedDevice Thread Leak & Telemetry Hygiene (Teilpaket B)

**Ticket**: [ATT-6381](https://rainerblind.atlassian.net/browse/ATT-6381)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6381`  
**Requirement Mapping**: `REQ-CON-026` (*BLE Run Speed Device Lifecycle & Telemetry Hygiene*)  
**Test Spec ID**: `TST-CON-026`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-026)

### 1.1 Problem Statement & Rationale
Under approved complexity split (REQ-PRO-029), this ticket addresses **Teilpaket B** (1 SP):
1. In `BTLERunSpeedDevice.shutDown()`, `mScheduler` is never shut down (`mScheduler.shutdownNow()` omitted), permanently leaking a thread worker per device instance.
2. In `distanceCalculator`, when hardware distance is absent (`mDistancePresent == false`), `mSpeed` is continuously added to `mDistance`. If sensor packets stop, `mSpeed` is never cleared and stale speed accumulates indefinitely into `mDistance`.
3. Fields `mSpeed`, `mDistance`, and `mDistancePresent` lack `volatile` cross-thread visibility guarantees.

### 1.2 Functional & Architectural Requirements
* **REQ-CON-026.1** (*Scheduler Thread Pool Termination*): In `BTLERunSpeedDevice.shutDown()`, `mScheduler.shutdownNow()` SHALL be invoked in addition to cancelling `mDistanceCalculatorHandle`, releasing the background worker.
* **REQ-CON-026.2** (*Stale Speed Timeout & Runaway Distance Prevention*): The device SHALL track packet arrival timestamp (`mLastMeasurementTimestamp`). If elapsed time since last measurement exceeds 5000 ms, `distanceCalculator` SHALL NOT accumulate stale speed. In `unregisterSensors()`, `mSpeed` SHALL be reset to `0.0`.
* **REQ-CON-026.3** (*Cross-Thread Memory Visibility*): `mSpeed`, `mDistance`, and `mDistancePresent` SHALL be declared `volatile` to guarantee safe publication across GATT and scheduler threads.
* **REQ-CON-026.4** (*Inheritance & Shadowing Cleanliness*): `BTLERunSpeedDevice` SHALL NOT declare redundant `mAddress` or `mTotalDistance` fields, relying on inherited device fields.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Thread Cleanup)**: *Given* active `BTLERunSpeedDevice`, *When* `shutDown()` invoked, *Then* `mScheduler.isShutdown()` is true and thread terminates without leak.
* **Criterion 2 (Stale Speed Gating)**: *Given* `mDistancePresent == false` and non-zero `mSpeed`, *When* no packets arrive for $> 5000\text{ ms}$, *Then* `distanceCalculator` does not accumulate stale speed into `mDistance`.
* **Criterion 3 (Disconnect Reset)**: *Given* active device, *When* `unregisterSensors()` invoked, *Then* `mSpeed` is reset to 0.0.

---

## 2. Test Specification (TST-CON-026)

### Test Case 1: Scheduler Shutdown & Thread Leak Prevention (`TST-CON-026.1`)
* **Scope**: Unit Test (`BTLERunSpeedDeviceTest.kt`)
* **Assertion**: Verify `shutDown()` executes `mScheduler.shutdownNow()` and cancels calculator handle.

### Test Case 2: Stale Speed Timeout & Runaway Distance Prevention (`TST-CON-026.2`)
* **Scope**: Unit Test (`BTLERunSpeedDeviceTest.kt`)
* **Assertion**: Verify packet timeout $> 5$ s suppresses stale distance accumulation.

### Test Case 3: Sensor Unregistration Speed Reset (`TST-CON-026.3`)
* **Scope**: Unit Test (`BTLERunSpeedDeviceTest.kt`)
* **Assertion**: Verify `unregisterSensors()` resets `mSpeed` to 0.0.

### Test Case 4: Field Shadowing & Inheritance Audit (`TST-CON-026.4`)
* **Scope**: Reflection Test (`BTLERunSpeedDeviceTest.kt`)
* **Assertion**: Verify `BTLERunSpeedDevice` declares no duplicate `mAddress` or `mTotalDistance` fields.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-CON-026.5`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-026.1` | Unit | `BTLERunSpeedDeviceTest.kt` | `REQ-CON-026.1` | Planned |
| `TST-CON-026.2` | Unit | `BTLERunSpeedDeviceTest.kt` | `REQ-CON-026.2` | Planned |
| `TST-CON-026.3` | Unit | `BTLERunSpeedDeviceTest.kt` | `REQ-CON-026.2` | Planned |
| `TST-CON-026.4` | Reflection | `BTLERunSpeedDeviceTest.kt` | `REQ-CON-026.4` | Planned |
| `TST-CON-026.5` | Regression | `banalservice.devices.bluetooth_le.*` | `REQ-PRO-001` | Planned |
