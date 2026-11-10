# Stage 3: Implementation Plan - ATT-3540: Parse Beat-to-Beat RR Intervals from BLE & ANT+ Sensors

**Ticket**: [ATT-3540](https://rainerblind.atlassian.net/browse/ATT-3540) | **Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3540`  
**Requirement Mapping**: `REQ-CON-027` (`REQ-CON-027.1`..`REQ-CON-027.5`)  
**Test Mapping**: `TST-CON-027` (`TST-CON-027.1`..`TST-CON-027.6`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Real-time HRV metrics (DFA $\alpha_1$, $rMSSD$, aerobic threshold pacing) require lossless beat-to-beat RR interval acquisition. Existing `BTLEHeartRateDevice` parses only 1-Hz HR from GATT `0x2A37`, dropping Bit 4 bundled RR intervals. `ANTHeartRateDevice` ignores beat count continuity, risking multi-beat delta spikes during RF dropouts. This plan details the architectural construction of `SensorType.RR_INTERVAL`, push-based `RrBeatEvent` / `RrBeatListener`, multi-RR decoding with cumulative timestamp reconstruction and $>2000\text{ ms}$ drift guard in `BTLEHeartRateDevice`, and $\Delta c == 1$ continuity enforcement in `ANTHeartRateDevice`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-CON-027.1` (`SensorType.RR_INTERVAL` & Push Dispatch), `REQ-CON-027.2` (BLE Multi-RR Decoding), `REQ-CON-027.3` (Cumulative Timestamps & Drift Guard), `REQ-CON-027.4` (ANT+ Continuity & Gap Emission), `REQ-CON-027.5` (Defensive Parsing & Backward Compatibility).
* **Test Mapping**: `TST-CON-027.1` (`SensorTypeTest.kt`, `TranslationParityTest.kt`), `TST-CON-027.2`–`TST-CON-027.3` & `TST-CON-027.5` (`BTLEHeartRateDeviceTest.kt`), `TST-CON-027.4` (`ANTHeartRateDeviceTest.kt`), `TST-CON-027.6` (Targeted suite).

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: `SensorType.HR` instant bpm updates and UI displays remain 100% backward compatible; optical sensors omitting Bit 4 operate as HR-only devices.
2. **Crash Resilience (REQ-CON-014)**: Truncated or malformed BLE GATT packets return safely without throwing `NullPointerException`.
3. **Temporal Monotonicity**: Cumulative timestamps reconstruct monotonic beat epochs, avoiding duplicate packet-time stamps.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/main/` until Gate 3 is approved.
5. **UI Consistency**: No UI changes (display widgets deferred to `ATT-3542`).

---

## 4. Proposed Architectural Changes (SWE.2)
### Component 1: `SensorType.RR_INTERVAL` & Localization
* Activate `SensorType.RR_INTERVAL` (`ms`, `SensorValueType.INTEGER`, category `HEART_RATE_AND_ENERGY`, `IntegerFormatter`).
* Define strings (`rr_interval`, `rr_interval_short`, `units_time_ms`) with 9-language translation parity in `strings.xml`.

### Component 2: `RrBeatEvent` & `RrBeatListener` Contract (`com.atrainingtracker.banalservice.devices`)
* Data class `RrBeatEvent(beatTimestampMs: Long, rrMs: Double, rawCounter: Int, protocol: ProtocolType, gapBeats: Int = 0, artifactFlag: Int = 0)`.
* Enum `ProtocolType { BLE, ANT_PLUS, SIMULATED }` and interface `RrBeatListener { fun onRrBeat(event: RrBeatEvent) }`.
* Thread-safe subscriber management (`CopyOnWriteArrayList<RrBeatListener>`) on heart rate devices.

### Component 3: `BTLEHeartRateDevice.java` Multi-RR Parsing & Drift Guard
* Parse flag Bit 0 (UINT8 vs UINT16 HR), Bit 3 (skip 2-byte Energy Expended), Bit 4 (RR present).
* Unpack Little-Endian UINT16 pairs: $\text{rrMs} = \text{raw} \times 1000.0 / 1024.0$.
* Cumulative timestamp: $t_k = t_{k-1} + \text{round}(\text{rrMs}_k)$. If $|t_{\text{pkt}} - t_k| > 2000\text{ ms}$, emit `gapBeats` and re-anchor $t_k = t_{\text{pkt}}$.

### Component 4: `ANTHeartRateDevice.java` Continuity Guard & Rollover
* Calculate $\Delta c = (\text{heartBeatCount} - \text{prevCount}) \pmod{256}$.
* Emit normal beat when $\Delta c == 1$; discard duplicate when $\Delta c == 0$; emit GAP event (`artifactFlag = 5`, `gapBeats = deltaC - 1`) when $\Delta c \ge 2$.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`SensorType.RR_INTERVAL` & Resources)**: Uncomment `RR_INTERVAL` in `SensorType.java`; add strings across 9 locales; verify `SensorTypeTest` and `TranslationParityTest`.
2. **Step 2 (Data Contract Models)**: Create `RrBeatEvent.kt`, `RrBeatListener.kt`, `ProtocolType.kt` in `banalservice.devices`.
3. **Step 3 (BLE Driver Unpacking & Timestamping)**: Implement multi-RR parsing, drift guard, and listener registry in `BTLEHeartRateDevice.java`.
4. **Step 4 (ANT+ Driver Continuity Guard)**: Implement $\Delta c$ continuity logic and GAP event dispatch in `ANTHeartRateDevice.java`.
5. **Step 5 (Unit Tests & Verification)**: Implement `BTLEHeartRateDeviceTest.kt` and `ANTHeartRateDeviceTest.kt`; execute targeted unit test suite.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.build.*"
  ```
* **Rollback Plan**: Revert cleanly via `git checkout feature/ATT-3540 -- .`.
