# Stage 1 Analysis: ATT-7543 - Real-Hardware Garmin Varia / RearVue 820 BLE V1 (6a4e3203) Packet Decoding, GATT Connection State & Stateful Track Ageing

**Ticket**: [ATT-7543](https://rainerblind.atlassian.net/browse/ATT-7543)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7543` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
When pairing and connecting to physical Garmin Varia / RearVue 820 hardware, `RadarEdgeOverlay` is never displayed on tracking screens (`isRadarActive` evaluates false):
1. *Missing Connection State*: `BTLERadarDevice` never transitions `RadarStateRepository` to `CONNECTING` in `startSearching()` or `CONNECTED` in `configureServices()`; connection state was only updated upon packet arrival in `processTargets()`.
2. *Real-Hardware V1 Packet Rejection*: Real hardware notifications on `6a4e3203` use a sequence header `(seq << 4) | 0x02` (cycling `0x02..0xF2`). Because `parseV1ThreatPacket()` strictly required `payload[0] == 0x00`, 100% of real frames (1-byte heartbeats, 6-byte sector diagnostics, and `1 + 3*N` threat frames) were rejected as `null`, leaving `RadarStateRepository` permanently `DISCONNECTED`.
3. *Advertisement Discovery*: Real Garmin Varia devices advertise 16-bit Member Service UUID `0xFE1F` in BLE scan records, which is omitted from `BTSearchForNewDevicesEngine.buildScanFilters`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call-Site & Architectural Audit**:
  * `TrackingTabsScreen.kt#L1097`: Gates `RadarEdgeOverlay` strictly on `radarState.connectionState != DISCONNECTED`.
  * `BTLERadarDevice.java`: Does not override `startSearching()` and does not set `CONNECTED` or arm the 3.5s watchdog upon CCCD subscription in `configureServices()`.
  * `BTLERadarDevice.java#L478`: Hardcoded `payload[0] != PACKET_TYPE_THREAT (0x00)` check rejects valid hardware packets where `(payload[0] & 0x0F) == 0x02`.
  * `BTSearchForNewDevicesEngine.java#L515`: `buildScanFilters(BIKE_RADAR)` only filters for 128-bit `6a4e3200`, dropping advertisements exposing 16-bit `0xFE1F`.
* **Independent Peer Review & Verification**:
  * *Heartbeats & Sector Frames*: 1-byte frames (`len == 1 && (b & 0x0F) == 0x02`) and 6-byte frames (`len == 6 && b == 0x06`) are valid link-alive packets. They must re-arm the 3.5s watchdog, prune stale tracks (`STALE_MS = 2000L`), and publish `CONNECTED`.
  * *V1 Threat Triplets*: Packets (`len >= 4 && (len - 1) % 3 == 0 && (b & 0x0F) == 0x02`) contain triplets `[vid, dist, flag]`. Bit 7 (`0x80`) indicates valid tracks (`trackId = vid & 0x7F`); `vid < 0x80`, `vid == 0xFD`, and `dist == 0xFF` are invalid markers.
  * *Stateful Closing Speed & Threat*: Byte 3 (`flag`) is a discrete state (`0x00`/`0x01`), not velocity in m/s. Closing speed (`approachSpeedMps`) must be derived statefully via $\Delta d / \Delta t$ from track history with ageing (`STALE_MS = 2000L`). `ThreatLevel` is classified by closing speed (`>= 15.0 m/s` $\to$ `FAST_APPROACHING`, `> 0.0 m/s` $\to$ `APPROACHING`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `CONNECTING` transition in `BTLERadarDevice.startSearching()` and `CONNECTED` + watchdog arming in `configureServices()`.
  2. Demux real V1 heartbeats, sector frames, and threat frames (`(payload[0] & 0x0F) == 0x02` and legacy `0x00`).
  3. Stateful track history (`STALE_MS = 2000L`) to derive approach speed and classify threat levels.
  4. Add Garmin Member UUID `0xFE1F` to `BluetoothConstants` and `BTSearchForNewDevicesEngine.buildScanFilters`.
  5. Author comprehensive unit tests in `BTLERadarDeviceTest.kt` verifying real RearVue 820 packet sequences.
* **Out-of-Scope Non-Goals**: Modifying BLE V2 (`6a4e3204`), ANT+ radar (`0x28`), or radar UI layout.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refine `REQ-CON-028.3` (Scan Filter) and `REQ-CON-028.4` (V1 Threat Decoding & Connection Lifecycle) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: `ATT-5083` (commit `dfbe3979`) established initial BLE radar driver; `ATT-6701` unified GATT queue and discovery.
* **Root Reason for Existing Formulation**: Early driver relied on synthetic simulator assumptions (`header = 0x00`, speed in byte 3). Real hardware uses sequence-nibble framing and stateful speed derivation.
* **Preservation of Core Invariants**: BLE V2, ANT+ radar, and scalar 1 Hz latch aggregation remain 100% intact; zero SQLite migrations.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-028.3` (Scan Filter), `REQ-CON-028.4` (V1 Decoding & Lifecycle).
* **Affected Architecture Elements**: `BTLERadarDevice.java`, `BluetoothConstants.java`, `BTSearchForNewDevicesEngine.java`, `BTLERadarDeviceTest.kt`.
* **Verification Impact & Test Surface**: `TST-CON-028.2`, `BTLERadarDeviceTest.kt`, full `com.atrainingtracker.banalservice.devices.bluetooth_le.*` test suite.
* **Risk Assessment & Invariants**: **HIGH** (Rule 7: Low-level binary protocol driver & real-hardware GATT integration). Invariants: 3.5s watchdog fail-safe, thread-safe track map access, zero regression in 1 Hz scalar metrics.

---

## 6. Architectural Strategy & High-Level Solution
1. **Lifecycle Integration**: Override `startSearching()` to publish `RadarConnectionState.CONNECTING`. In `configureServices()`, publish `RadarConnectionState.CONNECTED` and arm the 3.5s watchdog upon subscription.
2. **V1 Packet Parser & Demuxer**: Support sequence counter header `(payload[0] & 0x0F) == 0x02` (plus legacy `0x00`). Demux 1-byte heartbeats, 6-byte sector frames, and `1 + 3*N` threat triplets. Filter `vid < 0x80`, `0xFD`, and `0xFF`.
3. **Stateful Track History**: Maintain `TrackHistory` (`lastRangeMeters`, `lastTimestampMs`) keyed by `trackId`. Compute closing speed $\Delta d / \Delta t$ across observations; age out tracks $> 2000\text{ ms}$.
4. **Scan Filter Expansion**: Register `UUID_SERVICE_GARMIN_MEMBER` (`0000fe1f-0000-1000-8000-00805f9b34fb`) in `BluetoothConstants` and include in `BTSearchForNewDevicesEngine.buildScanFilters`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Watchdog 3.5s timeout transitions to `DISCONNECTED_ERROR`; `RadarTrafficSimulator` preemption preserved; zero SQLite schema changes.
* **Risk Rating**: **HIGH** (Binary BLE protocol decoding and GATT connection lifecycle per Rule 7).
