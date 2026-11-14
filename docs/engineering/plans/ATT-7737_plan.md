# Stage 3: Implementation Plan - ATT-7737: EditDeviceDialog Radar Tail Light Mode Reverts to Off

**Ticket**: [ATT-7737](https://rainerblind.atlassian.net/browse/ATT-7737)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-CON-031` / `REQ-CON-031.2`, `REQ-CON-031.5`  
**Test Mapping**: `TST-CON-031` / `TST-CON-031.2`, `TST-CON-031.5`  
**Branch**: `feat/ATT-7737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
During physical testing of Garmin Varia / RearVue 820 radar, tail light mode selection in `EditDeviceDialog` (e.g. Night Flash) failed to change the physical hardware light and subsequently reverted back to `Off`. This occurred because RearVue 820 write characteristic `6a4e2f11` requires `WRITE_TYPE_NO_RESPONSE`. Because Android defaults to `WRITE_TYPE_DEFAULT` (Write Request), writes were rejected by the peripheral. After the 1000ms echo guard expired, periodic hardware notification `0x1F` (Off) reverted `RadarLightController.currentMode`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-031` (`REQ-CON-031.2`, `REQ-CON-031.5`)
* **Test Mapping**: `TST-CON-031` (`TST-CON-031.2`, `TST-CON-031.5`)
* **Verification Targets**: `BTLERadarDeviceTest.kt`, `RadarLightControllerTest.kt`

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: Standard BLE sensor write operations requiring response (e.g. HRM, Speed/Cadence control points) must remain undisturbed.
2. **StVZO Compliance**: Steady-only mode enforcement for StVZO hardware remains strictly enforced.
3. **UI State Integrity**: `EditDeviceDialog` draft state (`EditDeviceViewModel`) remains decoupled from immediate hardware light commands.
4. **Code Lock**: Strict execution of `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>` prior to production code edits.

---

## 4. Proposed Architectural Changes

### Component 1: `BTLERadarDevice.java`
* In `configureServices()`: When `controlWriteChar` (`6a4e2f11`) is discovered, explicitly configure `controlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)`.
* In `setLightMode()`: Ensure `mRadarControlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` is guaranteed prior to enqueueing the 4-byte command `06 09 01 TT`.

### Component 2: `MyBTLEDevice.java`
* In `WriteCharacteristicOp.execute()`: Verify characteristic properties. If `(properties & PROPERTY_WRITE_NO_RESPONSE) != 0` and `(properties & PROPERTY_WRITE) == 0`, enforce `characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)`.

### Component 3: Unit Tests (`BTLERadarDeviceTest.kt` & `RadarLightControllerTest.kt`)
* Assert `WRITE_TYPE_NO_RESPONSE` configuration upon service discovery.
* Assert 4-byte command dispatch with `WRITE_TYPE_NO_RESPONSE`.
* Verify `RadarLightController` optimistic update and status echo handling.

### UI Consistency (Rule 23)
* No UI layout or styling changes in `EditDeviceDialog.kt` (re-uses existing `RadarLightControlSection`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.

### Step 2: Update `BTLERadarDevice.java`
* Set `controlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` in `configureServices()`.
* Set `mRadarControlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` in `setLightMode()`.

### Step 3: Update `MyBTLEDevice.java`
* In `WriteCharacteristicOp.execute()`, check properties and set `WRITE_TYPE_NO_RESPONSE` if characteristic only supports write without response.

### Step 4: Unit Test Authoring & Verification
* Add unit test in `BTLERadarDeviceTest.kt` testing write type and mode command dispatch.
* Execute targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest" --tests "com.atrainingtracker.banalservice.devices.radar.RadarLightControllerTest"`
* Verify requirement governance:
  `python3 tools/verify_requirement_governance.py`
