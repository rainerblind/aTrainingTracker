# Stage 1 Analysis: ATT-7545 - Garmin Varia RearVue 820 BLE Tail Light Control Service (6a4e2f00 / 6a4e2f11 / 6a4e2f14)

**Ticket**: [ATT-7545](https://rainerblind.atlassian.net/browse/ATT-7545)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7545`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
On physical Garmin Varia RearVue 820 hardware, integrated tail light mode control and physical button state updates do not use characteristic `6a4e3202` on service `6a4e3200`. Instead, physical hardware exposes a dedicated Control Service `6a4e2f00-667b-11e3-949a-0800200c9a66`:
- **Command Characteristic `6a4e2f11` (WRITE, INDICATE)**: Accepts 4-byte set-mode override writes `06 09 01 TT` (`0x11` SOLID, `0x12` PELOTON, `0x13` DAY_FLASH, `0x14` NIGHT_FLASH, `0x1F` OFF, `0x01` custom/solid).
- **Mode-State Characteristic `6a4e2f14` (NOTIFY)**: Publishes 4-byte physical slot state notifications `01 [slot] ff [type]` (`len == 4 && byte[0] == 0x01 && (byte[2] & 0xFF) == 0xFF`), ignoring 11-byte config blobs and 2-byte acks.
Because `BluetoothConstants.java` and `BTLERadarDevice.java` currently only implement legacy 1-byte writes on `6a4e3202`, RearVue 820 tail light cannot be controlled from the app and physical button presses are undetected.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification**:
  - `BluetoothConstants.java:55-66`: Lacks `UUID_SERVICE_RADAR_CONTROL` (`6a4e2f00`), `UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE` (`6a4e2f11`), and `UUID_CHARACTERISTIC_RADAR_CONTROL_NOTIFY` (`6a4e2f14`).
  - `BTLERadarDevice.java:280-315`: `configureServices()` only checks `btGattService.getCharacteristic(6a4e3202)`. It never inspects `gatt.getService(UUID_SERVICE_RADAR_CONTROL)`.
  - `BTLERadarDevice.java:535-544`: `setLightMode()` only dispatches 1-byte payloads `new byte[]{mode.getCommandByte()}` to `6a4e3202`.
  - `BTLERadarDevice.java:582-589`: `onOtherCharacteristicUpdate()` only parses 1-byte values from `6a4e3202`.
  - **Hypothesis Verification**: Verified. RearVue 820 output override `06 09 01 TT` does not emit slot notifications on `6a4e2f14`; `BTLERadarDevice.setLightMode()` must immediately call `RadarLightController.updateHardwareLightMode(mode)`. Older devices (RTL515/RTL516) must retain legacy 1-byte fallback on `6a4e3202`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Add `6a4e2f00`, `6a4e2f11`, and `6a4e2f14` UUID constants to `BluetoothConstants.java`.
  - In `BTLERadarDevice.configureServices()`: discover `6a4e2f00`, bind `6a4e2f11` (write) and `6a4e2f14` (notify), subscribe to CCCD notifications, and fallback to `6a4e3202` if not present.
  - In `BTLERadarDevice.setLightMode()`: dispatch 4-byte `06 09 01 TT` when `6a4e2f11` active, and immediately update `RadarLightController.updateHardwareLightMode(mode)`. Maintain 1-byte dispatch on legacy `6a4e3202`.
  - In `BTLERadarDevice.onOtherCharacteristicUpdate()`: decode 4-byte `01 [slot] ff [type]` notifications on `6a4e2f14` to update `RadarLightController`.
  - Author unit tests in `BTLERadarDeviceTest.kt` for 4-byte command encoding, 4-byte slot decoding, and legacy fallback.
* **Out-of-Scope Non-Goals**:
  - Radar threat telemetry / V1 / V2 parsing (`6a4e3203` / `6a4e3204`).
  - ANT+ Bike Light profile `0x23` modifications (`ANTBikeLightDevice.kt`).
  - UI or settings layout redesigns (`RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-CON-031` (`REQ-CON-031.1`, `REQ-CON-031.2`), targeting `BTLERadarDevice.java`, `BluetoothConstants.java`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 via `ATT-5102` (commit `3532c253`), refined in `ATT-6707`.
* **Root Reason for Existing Formulation**: Prior implementation assumed all Garmin Varia peripherals used unified service `6a4e3200` with 1-byte control characteristic `6a4e3202`.
* **Preservation of Core Invariants**: Transparent 1-byte fallback on `6a4e3202` preserved for RTL515/RTL516; StVZO mode pruning preserved; serialized non-blocking GATT queue preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-031` (`REQ-CON-031.1`, `REQ-CON-031.2`) in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `BluetoothConstants.java`, `BTLERadarDevice.java`, `BTLERadarDeviceTest.kt`.
* **Verification Impact & Test Surface**: `TST-CON-031.1`, `TST-CON-031.2`, `com.atrainingtracker.banalservice.devices.bluetooth_le.*` test suite.
* **Risk Assessment & Invariants**: Medium-Risk BLE command/notification change. Mitigated by legacy fallback to `6a4e3202` and comprehensive unit test assertions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Serialized GATT FIFO queue routing for all descriptor and characteristic writes.
  2. Legacy 1-byte `6a4e3202` backward compatibility for non-RearVue devices.
  3. StVZO regulatory mode filtering (`SOLID` / `OFF` only).
* **Risk Rating**: **MEDIUM** (BLE GATT service discovery and multi-characteristic light command protocol).
