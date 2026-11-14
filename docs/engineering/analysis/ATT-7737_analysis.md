# Stage 1 Analysis: ATT-7737 - EditDeviceDialog Tail Light Mode Reverts to Off

## 1. Problem Description & Operational Context
During physical testing of Sprint 2026-46.5 on Pixel 10 with Garmin Varia / RearVue 820 radar, selecting a tail light mode in `EditDeviceDialog` (e.g., Night Flash, Day Flash, Solid) did not change the physical light on the radar unit. Furthermore, closing and reopening the dialog showed that the mode selection reverted back to `Off`.

---

## 2. Forensic Root Cause Analysis (RCA)
Investigation of BLE traffic and command execution revealed two compounding root causes:
1. **Missing `WRITE_TYPE_NO_RESPONSE` on Command Characteristic (`6a4e2f11`)**:
   `BTLERadarDevice.setLightMode()` generates the 4-byte command `06 09 01 <type>` and enqueues it to `mRadarControlWriteChar` (`6a4e2f11`). In `MyBTLEDevice.java:WriteCharacteristicOp.execute()`, `gatt.writeCharacteristic(characteristic)` is called. However, Android's `BluetoothGattCharacteristic` write type defaults to `WRITE_TYPE_DEFAULT` (Write Request with response). The Garmin RearVue 820 control point `6a4e2f11` requires `WRITE_TYPE_NO_RESPONSE` (Write Command). The peripheral rejects/ignores write requests, so the physical hardware never switches light modes.
2. **Premature Echo Guard Expiration and Stale Hardware Status Echo**:
   Because the physical light mode never changed, the radar continues emitting its periodic status notifications (`6a4e2f14`) containing payload `01 <slot> ff 1F` (where `0x1F` corresponds to `RadarLightMode.OFF`). `BTLERadarDevice` employs an echo guard (`ECHO_GUARD_WINDOW_MS = 1000ms`). Once 1000ms expires, incoming status notification `0x1F` is decoded by `parseRearVueModeType(0x1F) == OFF`, which calls `RadarLightController.updateHardwareLightMode(OFF)`. This overwrites `currentMode` with `OFF`, causing `EditDeviceDialog` to display `Off`.
3. **Queue Write Type Enforcement**:
   `MyBTLEDevice.java` did not inspect characteristic properties (`PROPERTY_WRITE_NO_RESPONSE` vs `PROPERTY_WRITE`) when executing write operations, nor did `BTLERadarDevice` explicitly configure `mRadarControlWriteChar.setWriteType(WRITE_TYPE_NO_RESPONSE)` upon discovery.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Ensure RearVue 820 write characteristic (`6a4e2f11`) explicitly sets `WRITE_TYPE_NO_RESPONSE`.
  * Ensure `MyBTLEDevice.WriteCharacteristicOp` respects `PROPERTY_WRITE_NO_RESPONSE` when `PROPERTY_WRITE` is absent.
  * Ensure `setLightMode()` in `BTLERadarDevice` reliably delivers commands to physical hardware.
  * Ensure `RadarLightController` state remains resilient during command dispatch and status echo.
  * Add unit tests verifying `WRITE_TYPE_NO_RESPONSE` configuration and RearVue 820 command payload formatting.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No UI changes to `EditDeviceDialog` layout or Compose components.
  * No modifications to ANT+ radar tail light command encoding.
  * No modification to StVZO restrictions logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-031.2` (RearVue 820 Control Point Command Encoding), `REQ-CON-031.5` (Bidirectional Mode Synchronization).
* **Historical Origin & Commit Trace**: Added in ATT-7545 (Sprint 2026-46.5).
* **Root Reason for Existing Formulation**: `RadarLightController.updateHardwareLightMode` was introduced to maintain bidirectional synchronization when the light mode is toggled physically on the hardware or externally. The 1000ms echo guard was added to filter initial echoes.
* **Preservation of Core Invariants**: Bidirectional synchronization must be preserved; setting `WRITE_TYPE_NO_RESPONSE` allows the hardware to execute the command, so its subsequent echo confirms rather than reverts the selected mode.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-031.2` (explicit write-without-response specification), `REQ-CON-031.5` (state persistence & confirmation).
* **Affected Architecture Elements**: `BTLERadarDevice.java`, `MyBTLEDevice.java`, `RadarLightControllerTest.kt`, `BTLERadarDeviceTest.kt`.
* **Verification Impact & Test Surface**: BLE unit tests simulating RearVue 820 GATT write operations and notification echo cycles; end-to-end mode transition tests.
* **Risk Assessment & Invariants**: Low-to-Medium risk. Isolated to BLE GATT write characteristic handling for radar control points. Existing sensor writes (e.g. CSC, HRM) remain unaffected.

---

## 6. Architectural Strategy & High-Level Solution
* Explicitly configure `mRadarControlWriteChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)` upon service discovery in `BTLERadarDevice.java`.
* In `MyBTLEDevice.java:WriteCharacteristicOp.execute()`, check `(characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0` and `(characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0`, ensuring `WRITE_TYPE_NO_RESPONSE` is applied.
* Add unit tests covering write type configuration, write execution, and mode confirmation.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Backward compatibility with standard BLE write characteristics requiring responses.
  2. StVZO mode restrictions remain inviolate.
  3. UI state reflects hardware state without premature reversion.
* **Risk Rating**: **LOW** (surgical BLE GATT configuration fix, 2 SP).
