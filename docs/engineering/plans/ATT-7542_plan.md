# Stage 3: Implementation Plan - ATT-7542: RearVue 820 BLE V2 24-Bit Decoder & Fallback

**Ticket**: [ATT-7542](https://rainerblind.atlassian.net/browse/ATT-7542) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7542`  
**Requirement Mapping**: `REQ-CON-030` (`REQ-CON-030.1`..`REQ-CON-030.3`)  
**Test Mapping**: `TST-CON-030` (`TST-CON-030.1`..`TST-CON-030.3`, `TST-CON-030.8`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Physical Garmin Varia RearVue 820 radar units fail to stream over BLE V2 (`6a4e3204`) due to inverted Battery/V2 GATT configuration, lack of a V2 silence watchdog (leaving silent bonded devices trapped without falling back to V1 `6a4e3203`), and a protocol mismatch in `parseV2ThreatPacket()` (expecting synthetic `0x01` + 8 bytes instead of 2-byte LE headers and 9-byte target structs with 24-bit packed lateral/longitudinal coordinates).

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-030.1**: Enqueue Battery read/CCCD before V2; never subscribe V1 prior to V2 (`TST-CON-030.1`).
* **REQ-CON-030.2**: 1500 ms V2 silence watchdog fallback to V1 with reconnect reset (`TST-CON-030.2`).
* **REQ-CON-030.3**: 2-byte idle heartbeats, 5/6-byte status frames, and `2 + 9*N` 24-bit packed range decoder (`TST-CON-030.3`).

---

## 3. System Invariants & Preserved Behavior
1. **Starvation Watchdog**: 3.5s watchdog remains active across V2 and fallback V1 streams.
2. **Backward Compatibility**: Unbonded peripherals or non-V2 hardware seamlessly fallback to V1.
3. **Hardware Preemption**: Physical radar frames preempt `RadarTrafficSimulator` immediately.
4. **Zero Regressions & Schema Invariance**: Zero SQLite schema migrations; 100% 9-language parity.

---

## 4. Proposed Architectural Changes
* **BTLERadarDevice.java**:
  - Reorder `configureServices()`: Battery `0x2A19` CCCD precedes V2; V1 subscription deferred.
  - Add `mV2SilenceRunnable` (1500 ms) armed on V2 CCCD success; cancel on first V2 notification; on timeout invoke `fallbackToV1()`. Reset fallback state on disconnect.
  - Rewrite `parseV2ThreatPacket()`: Check status frame (bit 2 `0x0004`) and 2-byte idle heartbeats (`len == 2`) before target parsing. Unpack `2 + 9*N` bodies with 24-bit packed LE words (11-bit signed lateral $x$, 13-bit signed longitudinal $y$ range), map vehicle classes, derive closing speed ($v = -speedY * 0.5\text{ m/s}$), and derive `ThreatLevel` / `LaneState`.
* **UI Consistency**: No UI changes. `TrackingTabsScreen.kt` and overlay consume `StateFlow<RadarState>`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Handshake GATT Ordering & Silence Fallback Watchdog (`REQ-CON-030.1`, `REQ-CON-030.2`)
* Files: `BTLERadarDevice.java`
* Enqueue Battery CCCD before V2. Arm 1500 ms `mV2SilenceRunnable` on V2 CCCD success; cancel on V2 notification arrival. Implement `fallbackToV1()` invocation on timeout and reset state on disconnect.

### Step 2: Real RearVue 820 V2 Frame Demuxer & 24-Bit Range Decoder (`REQ-CON-030.3`)
* Files: `BTLERadarDevice.java`
* Demux 2-byte idle heartbeats (`02 00` / `02 90`) and 5/6-byte status frames (`0x0004`). Unpack `2 + 9*N` target structs: 24-bit packed sign extension (11-bit $x$, 13-bit $y$), vehicle class mapping (`TRUCK_OR_BUS`, `CAR`, `UNKNOWN_OR_LIGHT`), and kinematic speed/threat/lane derivation.

### Step 3: Comprehensive Unit Tests (`TST-CON-030.1`, `TST-CON-030.2`, `TST-CON-030.3`)
* Files: `BTLERadarDeviceTest.kt`
* Add tests for Battery priority, 1500 ms fallback, reconnect reset, authentic capture frames (`02389c17f50f0310070080b5243b48093c09fd80`), idle heartbeats, and status frames.

### Step 4: Targeted Verification & Governance (`TST-CON-030.8`)
* Run: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"`
* Run: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-7542_test_spec.md`

---

## 6. Verification & Rollback Plan
* **Verification**: 100% green on `com.atrainingtracker.banalservice.devices.bluetooth_le.*` and `verify_requirement_governance.py`.
* **Rollback**: Clean revert via `git reset --hard feature/ATT-7542` to Stage 2 commit `ecb9c788`.
