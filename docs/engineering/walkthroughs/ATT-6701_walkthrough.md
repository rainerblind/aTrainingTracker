# Stage 5: Walkthrough & Verification - ATT-6701: Align BIKE_RADAR discovery in BTSearchForNewDevicesEngine, unify MyBTLEDevice GATT read queue, and scope RADAR_* SensorTypes to BSportType.BIKE

**Ticket**: [ATT-6701](https://rainerblind.atlassian.net/browse/ATT-6701)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-CON-028` (`REQ-CON-028.2`, `REQ-CON-028.3`, `REQ-TRK-070.3`)  
**Test Mapping**: `TST-CON-028` (`TST-CON-028.1`, `TST-CON-028.5`, `TST-CON-028.6`, `TST-CON-028.7`, `TST-CON-028.8`)  
**Branch**: `feature/ATT-6701`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the successful construction, verification, and regression gating for **ATT-6701**:
1. **`BTSearchForNewDevicesEngine.java` Peripheral Discovery Alignment (`REQ-CON-028.3`)**:
   - Removed the ad-hoc early return check in `onServicesDiscovered` for `DeviceType.BIKE_RADAR`, aligning it with other BLE devices (`BIKE_POWER`) where Device Information (`UUID_CHARACTERISTIC_MANUFACTURER_NAME`) and Battery Level (`UUID_CHARACTERISTIC_BATTERY_LEVEL`) characteristics are read first.
   - In `readNextCharacteristic(String address)`, when the characteristic queue empties, verified `UUID_SERVICE_RADAR` before dispatching `newDeviceFound(address)`.
   - Encapsulated BLE scan filter construction into `buildScanFilters(DeviceType)` helper and eliminated dead code `matchesScanRecord(ScanResult, UUID)` with zero callers across the codebase.
2. **`MyBTLEDevice.java` Unified GATT Read Queue (`REQ-CON-028.2`)**:
   - Routed `mBatteryReadRunnable` through `enqueueReadCharacteristic(batteryChar)` instead of bypassing the queue.
   - Refactored `readNextCharacteristic()` to forward any queued items in `mReadCharacteristicQueue` to `enqueueReadCharacteristic(gattChar)` via FIFO `mGattQueue`, maintaining backward compatibility for legacy callers.
   - Removed redundant `readNextCharacteristic()` invocation in `onCharacteristicRead` (queue advancement is handled solely by `onGattOperationCompleted()`), completely resolving the split-queue race condition where in-flight writes were prematurely cleared.
   - Hardened `processNextGattOperation()` with defensive synchronized TOCTOU null guards and Looper dispatching.
3. **`ActivityType.java` Sensor Scoping (`REQ-TRK-070.3`)**:
   - Verified that all 6 `RADAR_*` scalar metrics remain strictly scoped to `BSportType.BIKE` (established in ATT-6726).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-028.2` | `TST-CON-028.1` | Unit Test (`MyBTLEDeviceQueueTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-028.3` | `TST-CON-028.7` | Unit Test (`BTSearchForNewDevicesEngineTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-028.3` | `TST-CON-028.8` | Unit Test (`BTSearchForNewDevicesEngineTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-070.3` | `TST-CON-028` | Unit Test (`ActivityTypeTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-028.6` | `TST-CON-028.5` | Localization Parity (`TranslationParityTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-CON-028.6` | Tier 1 Targeted Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted BLE & BANAL Regression Scope
Command:
```bash
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.ActivityTypeTest"
```

Output:
```text
> Task :app:compileDebugUnitTestJavaWithJavac UP-TO-DATE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 56s
33 actionable tasks: 6 executed, 27 up-to-date
```

### 9-Language Localization Parity Scope
Command:
```bash
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
```

Output:
```text
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 6s
33 actionable tasks: 1 executed, 32 up-to-date
```

### Requirement Governance Audit
Command:
```bash
python3 tools/verify_requirement_governance.py --sync-indexes
```

Output:
```text
PASS: Master index tables successfully synchronized with 100% parity.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure background peripheral driver and GATT connection lifecycle enhancement; zero UI runtime layout or SQLite schema changes. Verified via targeted JVM Robolectric and MockK unit test suites under thread concurrency constraints.

### Visual Consistency (Rule 23)
* No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Tier 1 test suite executed cleanly with 100% pass rate.
2. **Living Documentation Synchronized**: Status in `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`, `docs/requirements.md`, and `docs/tests.md` updated to `Verified`.
3. **Subtask Governance**: Stage 5 subtask submitted to `In Überprüfung` and passed to `agent2` for Gate 5 freigabe.
4. **Parent Ticket Final Review (Core Rule 3)**: Parent ticket transitioned to `Final Review (Human)` and assigned to human (`rainer.blind`).
