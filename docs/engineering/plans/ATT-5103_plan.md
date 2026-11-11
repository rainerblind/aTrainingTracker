# Stage 3: Implementation Plan - ATT-5103: Support ANT+ Bike Lights profile (Device Type 0x23) for radar tail light mode control and workout auto-switch

**Ticket**: [ATT-5103](https://rainerblind.atlassian.net/browse/ATT-5103)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-033` (`REQ-CON-033.1`..`REQ-CON-033.5`)  
**Test Mapping**: `TST-CON-033` (`TST-CON-033.1`..`TST-CON-033.5`)  
**Branch**: `feature/ATT-5103`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Cycling rearview radars (Garmin Varia RTL515, RTL516, RearVue 820, Magene L508, Bryton Gardia R300L) broadcast radar telemetry over ANT+ Bike Radar (`0x28`), but expose tail light control, battery state, and mode switching exclusively via the standardized ANT+ Bike Lights profile (Device Type `35` / `0x23`).

This plan establishes an automated companion channel model bound to `ANTBikeRadarDevice` without duplicate database pairing, implements the mandatory 3-step handshake (Connect Page `0x21`, Mode Command Page `0x22`, Disconnect Page `0x20`), and abstracts `RadarLightController` via `RadarLightDeviceClient` for full BLE/ANT+ Cockpit and auto-switch feature parity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CON-033` (*ANT+ Bike Lights Profile [Device Type 0x23] Companion Channel, Connect Handshake & Tail Light Mode Control*)
* **Test Mapping**: `TST-CON-033` (*ANT+ Bike Lights Profile [Device Type 0x23] Companion Channel, Handshake & Mode Control Test Suite*)
  - `REQ-CON-033.1` / `TST-CON-033.1`: Channel acquisition on Predefined Network slot 1 (`ANT_PLUS`), Period 4084, companion lifecycle.
  - `REQ-CON-033.2` / `TST-CON-033.2`: Broadcast decoding for Page `0x01` (State, Index, Battery) and Page `0x02` (Capabilities).
  - `REQ-CON-033.3` / `TST-CON-033.3`: Stateful handshake (Connect Page `0x21`, Mode Command Page `0x22`, Disconnect Page `0x20`).
  - `REQ-CON-033.4` / `TST-CON-033.4`: Generalization of `RadarLightController` via `RadarLightDeviceClient`.
  - `REQ-CON-033.5` / `TST-CON-033.5`: Fail-safe channel isolation and radar telemetry (0x28) non-interference.

---

## 3. System Invariants & Preserved Behavior
1. **Predefined Network Slot 1 Protection**: Strictly uses slot 1 (`ANT_PLUS`, 2457 MHz / RF 57), avoiding raw key rejection.
2. **Zero Duplicate Pairing Rows**: Companion light channel is owned by `ANTBikeRadarDevice`; `DevicesDatabaseManager` schema remains unchanged.
3. **Radar Telemetry Immunity**: Acknowledged light commands execute asynchronously; radar target stream (Pages `0x30`/`0x31`) and 3.5s watchdog run without stalls.
4. **Graceful Degradation**: Radar-only units (e.g. Garmin Varia RVR315) without light channels gracefully set `isLightControlSupported = false`.
5. **Zero SQLite Migration**: `DB_VERSION` unchanged.

---

## 4. Proposed Architectural Changes
### Component 1: `AntChannelHelper.kt` Constants
- Add `BIKE_LIGHT_DEVICE_TYPE = 35` (0x23), `BIKE_LIGHT_RF_FREQUENCY = 57`, `BIKE_LIGHT_CHANNEL_PERIOD = 4084`.

### Component 2: `RadarLightDeviceClient.kt` & `RadarLightController.kt` Generalization
- Introduce interface `RadarLightDeviceClient { fun setLightMode(mode: RadarLightMode): Boolean; fun getLightDeviceName(): String }`.
- Have `BTLERadarDevice` implement `RadarLightDeviceClient`.
- Refactor `RadarLightController` to manage `activeRadarDevice: RadarLightDeviceClient?` (with backwards-compatible overloads).

### Component 3: `ANTBikeLightDevice.kt` Companion Driver
- Handles `AntChannel` setup (Device Type 35, RF 57, Period 4084, `BIDIRECTIONAL_SLAVE`).
- Decodes Page `0x01` (Light Index, Light Type, Battery) and Page `0x02` (Capabilities bitmask).
- Implements 3-step handshake:
  - Step 1: Transmits acknowledged Page `0x21` when Page `0x01` indicates `Light Index == 0`.
  - Step 2: Transmits acknowledged Page `0x22` with target mode and incremented sequence number once `Light Index == 1`.
  - Step 3: Transmits acknowledged Page `0x20` on disconnect/release.
- Maps `RadarLightMode` <-> ANT+ Bike Light mode byte (`OFF=0`, `SOLID=1`, `PELOTON=2`, `DAY_FLASH=5`, `NIGHT_FLASH=6`).

### Component 4: `ANTBikeRadarDevice.kt` Companion Binding
- On radar connection, instantiates and starts companion `ANTBikeLightDevice` with the radar's ANT device number.
- On radar disconnect/shutdown, tears down companion channel cleanly.

### UI Consistency (Rule 23)
- No UI changes. Reuses existing Cockpit quick-switch sheet (`RadarLightQuickSwitchSheet.kt`) and Settings (`RadarSettingsSection.kt`) introduced in ATT-5102.

---

## 5. Step-by-Step Implementation Sequence
### Step 1: Channel Constants & Controller Abstraction (`REQ-CON-033.1`, `REQ-CON-033.4`)
* Files: `AntChannelHelper.kt`, `RadarLightDeviceClient.kt`, `RadarLightController.kt`, `BTLERadarDevice.java`
* Define `BIKE_LIGHT_*` constants, create interface `RadarLightDeviceClient`, generalize `RadarLightController`.

### Step 2: Implement ANTBikeLightDevice Companion Driver (`REQ-CON-033.2`, `REQ-CON-033.3`)
* Files: `ANTBikeLightDevice.kt`
* Implement broadcast decoding (Pages `0x01`, `0x02`) and acknowledged command builder (Pages `0x20`, `0x21`, `0x22`).

### Step 3: Wire Companion Lifecycle in ANTBikeRadarDevice (`REQ-CON-033.1`, `REQ-CON-033.5`)
* Files: `ANTBikeRadarDevice.kt`
* Launch companion channel on connect, release on shutdown, ensure asynchronous isolation.

### Step 4: Unit Test Suites (`TST-CON-033.1`..`TST-CON-033.5`)
* Files: `ANTBikeLightDeviceTest.kt`, `RadarLightControllerTest.kt`, `ANTBikeRadarDeviceTest.kt`
* Validate page encoding/decoding, handshake state machine, and radar non-interference.

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --tests "com.atrainingtracker.banalservice.devices.radar.*"`
* **Rollback**: `git checkout sprint/2026-46.2` safely discards branch without affecting production.
