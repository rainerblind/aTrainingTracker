# Stage 1 Analysis: ATT-5103 - Support ANT+ Bike Lights profile (Device Type 0x23) for radar tail light mode control and workout auto-switch

**Ticket**: [ATT-5103](https://rainerblind.atlassian.net/browse/ATT-5103)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-5103`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Cyclists pairing cycling radar units (e.g. Garmin Varia RTL515, RTL516, RearVue 820, Magene L508, Bryton Gardia R300L) over ANT+ can track approaching vehicles via the ANT+ Bike Radar profile (Device Type `40` / `0x28`, `ATT-5084`), but cannot control the integrated tail light from *aTrainingTracker*. Unlike newer BLE firmware, legacy units and pure ANT+ setups expose light mode control exclusively via the standardized ANT+ Bike Lights profile (Device Type `35` / `0x23`).

Without ANT+ Bike Lights support:
1. Cyclists cannot switch light modes (Day Flash, Night Flash, Solid, Peloton, Off) from the Cockpit overlay.
2. Workout start/stop auto-switch (`ATT-5102`) fails to control ANT+ radar lights.
3. Users would be forced to discover and pair duplicate sensors if light channels were treated as independent devices.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * *Hypothesis Verification against Codebase*:
    1. **Predefined Network Slot 1**: `AntChannelHelper.kt` successfully acquires raw ANT channels on Predefined Network slot 1 (`ANT_PLUS`, 2457 MHz / RF 57) for `Device Type 40`. Bike Lights (`Device Type 35` / `0x23`, Period 4084 counts / ~8.02 Hz) runs on the identical RF slot.
    2. **Companion Channel Model**: Physical radar units host both Device Type 40 (radar) and Device Type 35 (light) under the same transmission ID or adjacent device number. Managing `ANTBikeLightDevice` as an automated companion channel attached to `ANTBikeRadarDevice` avoids duplicate sensor rows in `DevicesDatabaseManager` and prevents database schema pollution.
    3. **Three-Step Command Handshake State Machine**:
       - **Broadcast Monitoring**: Peripheral broadcasts Data Page `0x01` (Main Light State) and `0x02` (Capabilities). When unassociated, Page `0x01` reports `Light Index == 0`.
       - **Step 1 — Connect Command (Page `0x21` / 33)**: Must send acknowledged message Page `0x21` assigning `Light Index = 1`, phone-derived Controller ID, and Light Type (`2 = Tail Light`). Mode changes are strictly ignored by ANT+ hardware while `Light Index == 0`.
       - **Step 2 — Mode Command (Page `0x22` / 34)**: Once `Light Index == 1` is confirmed in Page `0x01`, controller sends acknowledged Page `0x22` with target mode byte (`OFF`, `SOLID`, `PELOTON`, `DAY_FLASH`, `NIGHT_FLASH`) and incrementing sequence counter.
       - **Step 3 — Disconnect Command (Page `0x20` / 32)**: On workout finish or sensor disconnect, controller sends acknowledged Page `0x20` with `Light Index = 1` and Controller ID to release the light.
    4. **Unified Controller Abstraction**: `RadarLightController.kt` currently holds a direct reference to `BTLERadarDevice`. Refactoring the binding contract to a common `RadarLightDeviceClient` interface allows both BLE and ANT+ light peripherals to register seamlessly.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit
* **In-Scope Goals (ATT-5103 - 3 SP)**:
  * Extend `AntChannelHelper.kt` with Bike Lights profile constants (`BIKE_LIGHT_DEVICE_TYPE = 35`).
  * Implement `ANTBikeLightDevice` handling channel acquisition, Page `0x01`/`0x02` decoding, and Pages `0x20`, `0x21`, `0x22` command lifecycle.
  * Integrate companion lifecycle into `ANTBikeRadarDevice.kt` (auto-connect, wildcard fallback, teardown).
  * Generalize `RadarLightController.kt` via `RadarLightDeviceClient` interface to support ANT+ light control.
  * Comprehensive unit tests validating byte serialization, handshake transitions, and error recovery.
* **Out-of-Scope Non-Goals**:
  * Front headlight / turn signal multi-light mesh coordination (`ATT-5122`).
  * Ambient light sensor / solar escalation (`ATT-5104`) and IMU deceleration braking (`ATT-5121`).
  * Database schema migrations (zero SQLite changes).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Shard**: `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Refined Requirements**: Net-new `REQ-CON-033` (*ANT+ Bike Lights Profile 0x23 Companion Channel & Tail Light Control*) and `TST-CON-033`.
* **Chesterton's Fence**: `ANTBikeRadarDevice` radar target parsing (Pages `0x30`/`0x31`) and 3.5s starvation watchdog must remain completely unaffected by companion channel latency or acknowledged write retries. Companion light channel runs as an independent asynchronous `AntChannel` instance.

---

## 5. Architectural Strategy & High-Level Solution
```
[ ANTBikeRadarDevice (Device Type 40) ]
        │
        ├── acquires companion ──► [ ANTBikeLightDevice (Device Type 35) ]
        │                                    │
        │                           Page 0x01 (Index == 0)
        │                                    │
        │                           Send Page 0x21 (Connect, Index -> 1)
        │                                    │
        │                           Page 0x01 (Index == 1 Confirmed)
        │                                    │
        │                           Registers with RadarLightController
        ▼                                    ▼
[ RadarStateRepository ]        [ RadarLightController ] ◄── Cockpit Sheet / Auto-Switch
                                             │
                                    Send Page 0x22 (Mode Command)
                                    Send Page 0x20 (Disconnect on Stop)
```

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero duplicate device rows: Companion light channel lifecycle is bound to paired radar.
  2. Non-blocking ACK dispatch: `startSendAcknowledgedData` runs on background AIDL dispatcher.
  3. Fail-safe degradation: Radars without light endpoints (e.g. RVR315) cleanly drop companion channel without affecting radar telemetry.
  4. Unified UI: Cockpit quick-switch and workout auto-switch function identically across BLE and ANT+.
* **Risk Rating**: **MEDIUM** (Multi-channel ANT+ concurrency and stateful connect handshake).
