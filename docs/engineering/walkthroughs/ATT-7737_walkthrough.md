# Stage 5: Walkthrough & Verification - ATT-7737: EditDeviceDialog Radar Tail Light Mode Reverts to Off

**Ticket**: [ATT-7737](https://rainerblind.atlassian.net/browse/ATT-7737)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feat/ATT-7737`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.2`, `REQ-CON-031.5`)  
**Test Mapping**: `TST-CON-031` (`TST-CON-031.2`, `TST-CON-031.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

During physical testing on Pixel 10 with Garmin RearVue 820 radar, selecting a tail light mode in `EditDeviceDialog` (e.g. Night Flash) failed to switch the hardware light and subsequently reverted back to `Off`. Forensic analysis revealed that control characteristic `6a4e2f11` requires `WRITE_TYPE_NO_RESPONSE`, whereas Android defaults to `WRITE_TYPE_DEFAULT` (write with response), causing the peripheral to reject the command. After the 1000ms echo guard expired, periodic notifications reporting `0x1F` (Off) reverted `RadarLightController.currentMode`.

ATT-7737 resolves this issue via:
1. **Control Characteristic Write Type Configuration (`BTLERadarDevice.java`)**: Configures `controlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` upon discovering control service `6a4e2f00` (`REQ-CON-031.2`).
2. **Command Dispatch Write Type Guarantee (`BTLERadarDevice.java`)**: Guarantees `mRadarControlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` in `setLightMode()` prior to queueing the 4-byte command `06 09 01 TT` (`REQ-CON-031.2`).
3. **Queue Write Operation Enforcement (`MyBTLEDevice.java`)**: In `WriteCharacteristicOp.execute()`, automatically enforces `WRITE_TYPE_NO_RESPONSE` when the characteristic supports `PROPERTY_WRITE_NO_RESPONSE` and lacks `PROPERTY_WRITE` (`REQ-CON-028.2`, `REQ-CON-031.2`).
4. **Verification & Testing**: Extended `BTLERadarDeviceTest.kt` and `MyBTLEDeviceQueueTest.kt` verifying write type configuration and command dispatch; verified full `RadarLightControllerTest.kt` suite.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-031.2` | `[TST-CON-031.2a]` | Unit Test (`BTLERadarDeviceTest.testConfigureServices_discoversRearVueControlService`) | **PASSED** | `Verified` |
| `REQ-CON-031.2` | `[TST-CON-031.2b]` | Unit Test (`MyBTLEDeviceQueueTest.testWriteCharacteristicOp_enforcesNoResponseWhenPropertyMatches`) | **PASSED** | `Verified` |
| `REQ-CON-031.2`, `REQ-CON-031.5` | `[TST-CON-031.2c]` | Unit Test (`BTLERadarDeviceTest.testSetLightMode_rearVueEnforcesWriteTypeNoResponseAndPreventsReversion`) | **PASSED** | `Verified` |
| `REQ-CON-031.1`–`031.5` | `[TST-CON-031]` | Tier 1 Suite (`RadarLightControllerTest`, `BTLERadarDeviceTest`, `MyBTLEDeviceQueueTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDeviceQueueTest" --tests "com.atrainingtracker.banalservice.devices.radar.RadarLightControllerTest" --max-workers=2`
- Result: **All targeted tests passed** (0 failures, 0 errors, 0 skipped under sprint lock).
- Governance Verification: `python3 tools/verify_requirement_governance.py` $\to$ **PASSED** (100% requirement & test spec parity, 0 violations).

---

## 4. Invariant & Governance Verification

1. **Write Characteristic Invariant**: BLE characteristics requiring response (HRM, Speed/Cadence control points) continue using default write type without side effects.
2. **StVZO Compliance**: Steady-only mode enforcement for StVZO models (`010-03180-10`, `RTL516`) remains strictly intact.
3. **UI Decoupling**: Zero changes to `EditDeviceDialog.kt` layout or Compose hierarchy.
4. **Target Release Governance**: Aligned to active unreleased release `V5.0.0` (Core Rule 8).
