# Stage 2: Requirement & Test Specification - ATT-7545: Garmin Varia RearVue 820 BLE Tail Light Control Service

**Ticket**: [ATT-7545](https://rainerblind.atlassian.net/browse/ATT-7545) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7545`  
**Requirement Mapping**: `REQ-CON-031` (`REQ-CON-031.1`, `REQ-CON-031.2`) | **Test Spec ID**: `TST-CON-031` (`TST-CON-031.1`, `TST-CON-031.2`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (`REQ-CON-031`)

### 1.1 Attributes & Problem Statement
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- Next-generation Garmin Varia RearVue 820 tail lights route light control through dedicated Control Service `6a4e2f00-667b-11e3-949a-0800200c9a66` (write characteristic `6a4e2f11`, notify characteristic `6a4e2f14`), rather than legacy characteristic `6a4e3202` on service `6a4e3200`. Command writes require 4-byte payload `06 09 01 TT` (`0x11` SOLID, `0x12` PELOTON, `0x13` DAY_FLASH, `0x14` NIGHT_FLASH, `0x1F` OFF), while physical device button pushes emit 4-byte notifications `01 [slot] ff [type]` on `6a4e2f14`.

### 1.2 Functional & Architectural Requirements
1. `BluetoothConstants.java` SHALL define `UUID_SERVICE_RADAR_CONTROL` (`6a4e2f00-667b-11e3-949a-0800200c9a66`), `UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE` (`6a4e2f11-667b-11e3-949a-0800200c9a66`), and `UUID_CHARACTERISTIC_RADAR_CONTROL_NOTIFY` (`6a4e2f14-667b-11e3-949a-0800200c9a66`).
2. `BTLERadarDevice.configureServices()` SHALL discover Control Service `6a4e2f00`, bind `6a4e2f11` (write) and `6a4e2f14` (notify), subscribe to `6a4e2f14` CCCD notifications, register light control with `RadarLightController`, and transparently fallback to legacy `6a4e3202` on service `6a4e3200` when `6a4e2f00` is not exposed.
3. `BTLERadarDevice.setLightMode()` SHALL dispatch 4-byte payload `06 09 01 TT` to `6a4e2f11` when active, immediately calling `RadarLightController.updateHardwareLightMode(mode)`, and SHALL retain 1-byte command dispatch on legacy `6a4e3202`.
4. `BTLERadarDevice.onOtherCharacteristicUpdate()` SHALL decode 4-byte notifications `01 [slot] ff [type]` on `6a4e2f14` (`len == 4 && byte[0] == 0x01 && (byte[2] & 0xFF) == 0xFF`) and update `RadarLightController.updateHardwareLightMode(mode)`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* a connected RearVue 820 with service `6a4e2f00`, *When* `configureServices()` runs, *Then* `6a4e2f11` and `6a4e2f14` are bound, `6a4e2f14` CCCD subscription is enqueued, and `RadarLightController` is registered.
- *Given* a legacy radar (RTL515/RTL516) without `6a4e2f00`, *When* `configureServices()` runs, *Then* driver falls back to `6a4e3202` on `6a4e3200`.
- *Given* an active `6a4e2f11` control characteristic, *When* `setLightMode(SOLID)` is called, *Then* 4-byte payload `[0x06, 0x09, 0x01, 0x11]` is written to `6a4e2f11` and hardware light mode is immediately updated to `SOLID`.
- *Given* an active notify characteristic `6a4e2f14`, *When* notification `01 00 ff 13` arrives, *Then* physical button press updates hardware light mode to `DAY_FLASH`.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: `REQ-CON-031` (`REQ-CON-031.1`, `REQ-CON-031.2`), targeting `BTLERadarDevice.java`, `BluetoothConstants.java`.
- *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.1 via `ATT-5102` (commit `3532c253`), refined in `ATT-6707`, and updated in `ATT-7545` for RearVue 820 Control Service.
- *Root Reason for Existing Formulation*: Prior driver assumed unified service `6a4e3200` with 1-byte control characteristic `6a4e3202`; next-gen RearVue 820 splits light control into dedicated service `6a4e2f00` with 4-byte command and notification frames.
- *Preservation of Core Invariants*: Transparent 1-byte fallback on `6a4e3202` preserved for RTL515/RTL516; StVZO mode pruning preserved; serialized non-blocking GATT queue routing preserved; 100% 9-language parity maintained.

---

## 2. Test Specification (`TST-CON-031`)

### Test Cases
- `[TST-CON-031.1]` (Unit - `BTLERadarDeviceTest.kt`): Verify Control Service `6a4e2f00` discovery, `6a4e2f14` CCCD subscription, legacy `6a4e3202` fallback, StVZO detection, and serialized write queueing.
- `[TST-CON-031.2]` (Unit - `BTLERadarDeviceTest.kt`): Verify 4-byte mode override dispatch `06 09 01 TT` on `6a4e2f11` with immediate hardware state reflection, 4-byte `01 [slot] ff [type]` notification decoding on `6a4e2f14`, and legacy 1-byte command write on `6a4e3202`.
- `[TST-CON-031.8]` (Tier 1 Regression): Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-031.1]` | Unit | `BTLERadarDevice.configureServices` | `REQ-CON-031.1` | Specified |
| `[TST-CON-031.2]` | Unit | `BTLERadarDevice.setLightMode` / `onOtherCharacteristicUpdate` | `REQ-CON-031.2` | Specified |
| `[TST-CON-031.8]` | Regression | `com.atrainingtracker.banalservice.devices.bluetooth_le.*` | `REQ-PRO-001` | Specified |
