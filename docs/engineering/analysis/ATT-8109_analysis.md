# Stage 1 Analysis: ATT-8109 - Instance-Isolated isReceivingData for BLE & ANT+ Radar Devices

**Ticket**: [ATT-8109](https://rainerblind.atlassian.net/browse/ATT-8109)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Schicht 2026-11-16_02_Spät`  
**Branch**: `feature/ATT-8109`  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
When an athlete has multiple paired BLE radar peripherals in `DeviceManager` (e.g. `RearVue8` on bike A and `ATT-Rdr` on bike B), connecting to one radar causes the Start screen and Cockpit headers to falsely display **all** paired BLE radar devices as active and connected:
* **Current Behavior**: `BTLERadarDevice.isReceivingData()` returns `true` for any paired BLE radar device as soon as `RadarStateRepository.getState(RadarSource.BLE).getConnectionState() == CONNECTED`, regardless of whether that specific peripheral is connected or powered off.
* **Expected Behavior**: Each `BTLERadarDevice` (and `ANTBikeRadarDevice`) instance MUST evaluate its own physical connection and packet reception state, returning `true` strictly if that specific peripheral is actively connected and streaming.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Global Transport vs. Instance State Disconnect**: In `BTLERadarDevice.java:174-176`, `isReceivingData()` queries the transport-level singleton `RadarStateRepository.getState(RadarSource.BLE)`, completely discarding `MyBTLEDevice`'s per-instance connection state (`mState == State.CONNECTED_WITH_SERVICE`).
2. **Companion ANT+ Vulnerability**: In `ANTBikeRadarDevice.kt:226-227`, `isReceivingData()` similarly inspects only `RadarSource.ANT_PLUS`, lacking instance-level packet reception tracking.
* **Independent Peer Review & Verification**:
  * Audited `MyBTLEDevice.java:846-848`: `super.isReceivingData()` accurately tests `mState == State.CONNECTED_WITH_SERVICE`.
  * Audited `DeviceManager.java:876, 998`: `getActiveRemoteDevices()` iterates all paired devices, polling `isReceivingData()`. Because `BTLERadarDevice` ignored `super.isReceivingData()`, disconnected instances were included in active device sets.
  * Verified that combining `super.isReceivingData() && RadarStateRepository.getState(RadarSource.BLE).getConnectionState() == CONNECTED` preserves both per-device GATT isolation AND packet-starvation watchdog detection (`TST-CON-041.6`).

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  1. Update `BTLERadarDevice.isReceivingData()` to require `super.isReceivingData()` in conjunction with repository state.
  2. Update `ANTBikeRadarDevice.isReceivingData()` to require instance-level packet reception within the watchdog window.
  3. Add multi-instance unit test in `BTLERadarDeviceTest.kt` verifying one connected and one disconnected radar.
* **Out-of-Scope Non-Goals**:
  1. Altering multi-source arbitration in `RadarStateRepository.kt`.
  2. Modifying UI overlays (`RadarEdgeOverlay.kt`) or audio alerts (`RadarAlertManager.kt`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-041.4` (*Isolated Transport Inspection* in `ATT-7575`).
* **Historical Origin & Commit Trace**: Commit `663c69ea` (ATT-7575) introduced `BTLERadarDevice.isReceivingData()` to prevent ANT+ connections from falsely activating BLE radar tiles, but inadvertently bypassed `super.isReceivingData()`.
* **Root Reason for Existing Formulation**: Scoping was focused on cross-protocol arbitration (BLE vs. ANT+), not multi-device same-protocol pairings.
* **Preservation of Core Invariants**: Retains watchdog fail-safe timeout (packet starvation returns `false` even if GATT remains open) and cross-protocol isolation.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-CON-044` (*Instance-Isolated Radar Connection State*, refined from `SYSREQ-TRK-001`, `SYSREQ-TRK-002`). Recommended prefix: `CON`.
* **Affected Architecture Elements**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`.
* **Verification Impact & Test Surface**: Unit tests in `BTLERadarDeviceTest.kt` and `ANTBikeRadarDeviceTest.kt` (`TST-CON-044`).
* **Risk Assessment & Invariants**: `LOW` — additive boolean conjunction (`super.isReceivingData() && ...`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Watchdog timeout on packet starvation continues to report `isReceivingData() == false`.
  2. BLE and ANT+ streams remain isolated.
  3. Single-radar setups retain 100% behavioral parity.
* **Risk Rating**: `LOW` — localized instance qualification.
