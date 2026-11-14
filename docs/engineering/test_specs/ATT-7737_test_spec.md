# Stage 2: Requirement & Test Specification - ATT-7737: EditDeviceDialog Radar Tail Light Mode Reverts to Off

**Ticket**: [ATT-7737](https://rainerblind.atlassian.net/browse/ATT-7737)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-CON-031` / `REQ-CON-031.2`, `REQ-CON-031.5`  
**Test Spec ID**: `TST-CON-031` / `TST-CON-031.2`, `TST-CON-031.5`  
**Branch**: `feat/ATT-7737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-CON-031 Refinement)

### 1.1 Problem Statement & Rationale
On Garmin RearVue 820 radar peripherals, light mode commands sent via `BTLERadarDevice.setLightMode()` failed to actuate the physical light because the command characteristic `6a4e2f11` requires `WRITE_TYPE_NO_RESPONSE` (Write Command). Android defaults to `WRITE_TYPE_DEFAULT` (Write Request with response), causing the peripheral to reject the write. Subsequently, periodic notifications (`6a4e2f14`) reporting `0x1F` (OFF) overwrote `RadarLightController.currentMode` after the 1000ms echo guard expired, reverting `EditDeviceDialog` back to `Off`.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit Test / Integration Test

### 1.3 Functional & Architectural Requirements
1. `BTLERadarDevice` SHALL configure `mRadarControlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` upon discovering Control Service `6a4e2f00`.
2. In `MyBTLEDevice.java`, `WriteCharacteristicOp.execute()` SHALL ensure that characteristics supporting `PROPERTY_WRITE_NO_RESPONSE` without `PROPERTY_WRITE` are dispatched using `WRITE_TYPE_NO_RESPONSE`.
3. In `BTLERadarDevice.setLightMode()`, `mRadarControlWriteChar` SHALL explicitly dispatch commands using `WRITE_TYPE_NO_RESPONSE`.
4. `RadarLightController.setLightMode()` and `updateHardwareLightMode()` SHALL ensure optimistic mode updates are preserved during hardware transitions and properly synchronized with physical confirmations.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Write Type No Response Configuration)**:
  * *Given* a discovered RearVue 820 Control Service (`6a4e2f00`),
  * *When* `configureServices()` resolves `controlWriteChar` (`6a4e2f11`),
  * *Then* `controlWriteChar.getWriteType()` is set to `BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE`.
* **Criterion 2 (Mode Command Delivery Without Reversion)**:
  * *Given* an active RearVue 820 connection,
  * *When* `setLightMode(RadarLightMode.NIGHT_FLASH)` is called from `EditDeviceDialog`,
  * *Then* the 4-byte command `06 09 01 14` is enqueued with `WRITE_TYPE_NO_RESPONSE` and `RadarLightController.currentMode.value` remains `RadarLightMode.NIGHT_FLASH`.
* **Criterion 3 (WriteCharacteristicOp Fallback Enactment)**:
  * *Given* a characteristic with `PROPERTY_WRITE_NO_RESPONSE` and without `PROPERTY_WRITE`,
  * *When* `WriteCharacteristicOp.execute(gatt)` runs,
  * *Then* `characteristic.getWriteType()` is verified to be `WRITE_TYPE_NO_RESPONSE`.

### 1.5 System Invariants
- Zero regression on legacy `6a4e3202` write operations.
- StVZO lighting mode constraints remain strictly enforced.
- Zero change to EditDeviceDialog layout or Compose tree.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-031` (`REQ-CON-031.2`, `REQ-CON-031.5`), targeting `BTLERadarDevice.java`, `MyBTLEDevice.java`, `RadarLightController.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-7545` (Sprint 2026-46.5).
* **Root Reason for Existing Formulation**: `RadarLightController.updateHardwareLightMode` was introduced to reflect external physical button toggles. The echo guard was added to ignore immediate echoes.
* **Preservation of Core Invariants**: Hardware synchronization remains intact; fixing write type ensures physical commands are accepted by hardware, preventing false reversion.

---

## 2. Test Specification (TST-CON-031.2)

### Test Case 1: `testRearVueWriteCharacteristic_setsWriteTypeNoResponse` (`TST-CON-031.2a`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDeviceTest.kt`
* **Assertions**:
  - `assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, writeChar.getWriteType())`

### Test Case 2: `testWriteCharacteristicOp_enforcesNoResponseWhenPropertyMatches` (`TST-CON-031.2b`)
* **Scope**: Unit Test (`MyBTLEDeviceTest.kt` or `BTLERadarDeviceTest.kt`)
* **Assertions**:
  - For characteristic with `PROPERTY_WRITE_NO_RESPONSE`, write operation sets and verifies `WRITE_TYPE_NO_RESPONSE`.

### Test Case 3: `testSetLightMode_dispatchesRearVueCommandWithoutReversion` (`TST-CON-031.2c`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`, `RadarLightControllerTest.kt`)
* **Assertions**:
  - `device.setLightMode(RadarLightMode.NIGHT_FLASH)` enqueues `06 09 01 14`.
  - `RadarLightController.currentMode.value == RadarLightMode.NIGHT_FLASH`.
