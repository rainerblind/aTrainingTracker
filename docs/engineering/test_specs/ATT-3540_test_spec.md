# Stage 2: Requirement & Test Specification - ATT-3540: Parse Beat-to-Beat RR Intervals from BLE and ANT+ Heart Rate Sensors

**Ticket**: [ATT-3540](https://atrainingtracker.atlassian.net/browse/ATT-3540) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-3539](https://atrainingtracker.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3540`  
**Requirement Mapping**: `REQ-CON-027` (`REQ-CON-027.1`..`REQ-CON-027.5`) | **Test Spec ID**: `TST-CON-027` (`TST-CON-027.1`..`TST-CON-027.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-CON-027`)

### 1.1 Problem Statement & Rationale
Real-time HRV metrics (DFA $\alpha_1$, $rMSSD$, aerobic threshold pacing) require lossless beat-to-beat RR interval acquisition. Existing `BTLEHeartRateDevice` parses only 1-Hz HR from GATT `0x2A37`, dropping Bit 4 RR intervals and creating duplicate timestamps for multi-beat packets. `ANTHeartRateDevice` ignores beat count continuity, risking severe artifact spikes on RF dropouts ($\Delta c \ge 2$).

### 1.2 Functional & Architectural Requirements
* **REQ-CON-027.1** (*SensorType.RR_INTERVAL & Push Dispatch Contract*): The system SHALL activate `SensorType.RR_INTERVAL` (`ms`, `SensorValueType.INTEGER`, category `HEART_RATE_AND_ENERGY`) with 9-language translation parity. Devices SHALL dispatch immutable `RrBeatEvent(beatTimestampMs, rrMs, rawCounter, protocol, gapBeats, artifactFlag)` instances to registered `RrBeatListener` subscribers, bypassing 1-Hz polling.
* **REQ-CON-027.2** (*BLE GATT 0x2A37 Multi-RR Decoding*): `BTLEHeartRateDevice` SHALL inspect flag Bit 4 (`0x10`). When set, it SHALL parse all Little-Endian `UINT16` RR values ($\text{rrMs} = \text{raw} \times 1000.0 / 1024.0$) following the HR value (offset based on Bit 0) and optional Energy Expended field (Bit 3).
* **REQ-CON-027.3** (*Cumulative Beat-Timestamp Reconstruction & Drift Guard*): For bundled multi-RR BLE packets, timestamps SHALL be reconstructed as $t_{\text{beat},k} = t_{\text{beat},k-1} + \text{round}(\text{rrMs}_k)$ anchored to packet arrival time $t_{\text{pkt}}$. If $|t_{\text{pkt}} - t_{\text{beat},k}| > 2000\text{ ms}$, the system SHALL record `gapBeats = max(0, round((t_pkt - t_prev) / rrMs) - 1)` and re-anchor $t_{\text{beat},k} = t_{\text{pkt}}$.
* **REQ-CON-027.4** (*ANT+ Beat Count Continuity & Gap Emission*): `ANTHeartRateDevice` SHALL evaluate $\Delta c = (\text{heartBeatCount} - \text{prevCount}) \pmod{256}$. It SHALL emit normal beats only when $\Delta c == 1$, discard duplicate packets when $\Delta c == 0$, and emit an `RrBeatEvent` with `artifactFlag = 5 (GAP)` and `gapBeats = deltaC - 1` when $\Delta c \ge 2$.
* **REQ-CON-027.5** (*Defensive Parsing & Backward Compatibility*): Malformed/truncated BLE packets SHALL NOT crash (`REQ-CON-014`). Optical sensors omitting Bit 4 SHALL NOT activate `mRrIntervalSensor`, preserving backward compatibility for HR-only tracking.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (BLE Multi-RR Packet Streaming)**: *Given* a BLE `0x2A37` packet with `flag = 0x10` containing 3 bundled RR intervals (`1024`, `512`, `768`), *When* parsed, *Then* 3 sequential events (`1000.0 ms`, `500.0 ms`, `750.0 ms`) are dispatched with monotonically reconstructed timestamps without dropouts.
* **AC-2 (BLE Wall-Clock Drift Guard)**: *Given* a sensor reconnect or packet delay where $|t_{\text{pkt}} - t_{\text{beat}}| > 2000\text{ ms}$, *When* parsed, *Then* timestamp is re-anchored to $t_{\text{pkt}}$ with computed `gapBeats`.
* **AC-3 (ANT+ Beat Continuity & Gap Guard)**: *Given* `heartBeatCount` jumps by $\Delta c = 2$, *When* received, *Then* the combined delta is NOT emitted as a single beat; instead a `GAP` event (`artifactFlag = 5`, `gapBeats = 1`) is emitted.
* **AC-4 (Optical HR Compatibility)**: *Given* an optical sensor emitting `flag = 0x00`, *When* received, *Then* `SensorType.HR` updates normally and `mRrIntervalSensor` remains null.

---

## 2. Test Specification & Traceability Matrix (`TST-CON-027`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-CON-027.1` | `TST-CON-027.1` | `SensorTypeTest.kt`, `TranslationParityTest.kt` | `SensorType.RR_INTERVAL` registration, category, units (`ms`), 9-locale parity |
| `REQ-CON-027.2` | `TST-CON-027.2` | `BTLEHeartRateDeviceTest.kt` | Multi-RR unpacking (0, 1, 2, 3 beats), UINT8/UINT16 HR, Energy Expended offset |
| `REQ-CON-027.3` | `TST-CON-027.3` | `BTLEHeartRateDeviceTest.kt` | Monotonic cumulative timestamp reconstruction, drift guard (>2000 ms jump) |
| `REQ-CON-027.4` | `TST-CON-027.4` | `ANTHeartRateDeviceTest.kt` | $\Delta c == 1$ normal beat, 8-bit rollover ($255 \to 0$), $\Delta c == 0$ reject, $\Delta c \ge 2$ GAP |
| `REQ-CON-027.5` | `TST-CON-027.5` | `BTLEHeartRateDeviceTest.kt` | Truncated payload resilience, optical sensor Bit 4 absence backward compatibility |
| `REQ-CON-027` | `TST-CON-027.6` | Targeted Test Suite | Targeted tests: `banalservice.devices.*`, `banalservice.sensor.*` pass 100% |
