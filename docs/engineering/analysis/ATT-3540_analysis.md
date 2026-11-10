# Stage 1 Analysis: Parse Beat-to-Beat RR Intervals from BLE and ANT+ Sensors (ATT-3540)

**Ticket**: [ATT-3540](https://rainerblind.atlassian.net/browse/ATT-3540) | **Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3540`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Real-time HRV metrics (DFA $\alpha_1$, $rMSSD$, aerobic threshold pacing) require lossless beat-to-beat RR acquisition. Currently, `BTLEHeartRateDevice` only parses heart rate from BLE GATT `0x2A37` at byte offset 1, ignoring Bit 3 (`0x08`, Energy Expended) and Bit 4 (`0x10`, RR-Intervals). `ANTHeartRateDevice` only listens to `IHeartRateDataReceiver`, dropping beat event timestamps and beat counts. Furthermore, 1-Hz sensor polling in `TrackerService` drops high-frequency beats ($120\text{--}180\text{ bpm} \rightarrow 2\text{--}3\text{ beats/s}$), packet arrival timestamps create duplicate beat times for multi-RR BLE packets, and ANT+ RF dropouts risk misrepresenting multi-beat deltas as false bradycardia spikes.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Call-Site Audit**: Living code inspection confirms `BTLEHeartRateDevice.java` ignores Bit 4 (`0x10`) and bundled `UINT16` RR values ($1/1024\text{ s}$). `ANTHeartRateDevice.java` ignores `heartBeatCount` and does not subscribe to `CalculatedRrInterval` or beat deltas.
* **BLE Multi-RR Bundling & Timestamp Drift**: At higher heart rates, BLE packets bundle 2–3 RR intervals. Stamping each beat with packet wall-clock arrival time $t_{\text{pkt}}$ produces duplicate timestamps. Reconstruction must use cumulative sum $t_{\text{beat},k} = t_{\text{beat},k-1} + \text{round}(\text{rrMs}_k)$ with a $2000\text{ ms}$ wall-clock drift/dropout guard (`gapBeats = max(0, round((t_pkt - t_prev) / rrMs) - 1)`).
* **ANT+ Continuity Rule**: ANT+ 4-Hz transmission broadcasts 8-bit rollover `heartBeatCount`. True single-beat continuity requires $\Delta c = (\text{count} - \text{prevCount}) \pmod{256} == 1$. If $\Delta c \ge 2$, RF packet loss occurred; emitting the combined time delta as a single beat creates an artifactual spike. It must emit `artifactFlag = 5 (GAP)` with `gapBeats = deltaC - 1`. $\Delta c == 0$ indicates duplicate/cached packets and must be discarded.
* **Independent Verification**: A dedicated listener-fed push architecture (`RrBeatListener` dispatching `RrBeatEvent`) completely bypasses the 1-Hz `TrackerService` polling bottleneck, feeding both live stream filters (`ATT-3541`) and dedicated SQLite storage (`ATT-3543`).

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  * Activate `SensorType.RR_INTERVAL` (`ms`, `INTEGER`, category `HEART_RATE_AND_ENERGY`) with formatters and localized strings.
  * Define `RrBeatEvent(beatTimestampMs, rrMs, rawCounter, protocol, gapBeats, artifactFlag)` and `RrBeatListener`.
  * Implement BLE `0x2A37` parsing in `BTLEHeartRateDevice` handling flag bits 0, 3, 4, multi-RR unpacking, and cumulative timestamping with drift guard.
  * Implement ANT+ continuity parsing in `ANTHeartRateDevice` enforcing $\Delta c == 1$, RF gap emission ($\Delta c \ge 2$), and duplicate rejection ($\Delta c == 0$).
  * Propose net-new living doc specifications `REQ-CON-027` and `TST-CON-027`.
* **Out-of-Scope Non-Goals**:
  * Autonomic metric computation ($rMSSD$, DFA $\alpha_1$) and Lipponen artifact filter (isolated in `ATT-3541`).
  * SQLite `WorkoutRrIntervals` persistence schema and DAO (isolated in `ATT-3543`).
  * Cockpit UI metric widgets and threshold indicators (isolated in `ATT-3542`).
* **Complexity Rating**: **3 SP (Complex)**. Real-time hardware telemetry protocol parsing across both BLE GATT and ANT+ stacks with temporal continuity guarantees.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-CON-027` and `TST-CON-027` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`, extending `REQ-CON-002` (BLE), `REQ-CON-003` (ANT+), and `REQ-CON-014` (GATT parsing resilience).
* **Historical Origin & Commit Trace**: `ATT-1485` (`REQ-CON-014`) added null-safety to BLE parsing; `SensorType.RR_INTERVAL` existed as a commented placeholder since initial architecture.
* **Root Reason for Existing Formulation**: Early app versions focused strictly on 1-Hz integer HR display, discarding raw beat timings to economize battery and bandwidth.
* **Preservation of Core Invariants**:
  1. `SensorType.HR` instant bpm emission and existing UI displays remain 100% backward compatible.
  2. Malformed/truncated BLE packets must not throw exceptions (`REQ-CON-014`).
  3. Optical sensors lacking Bit 4 (`0x10`) continue regular HR operation without activating RR.

---

## 5. Architectural Strategy & High-Level Solution
* **Data Contracts (`com.atrainingtracker.banalservice.devices`)**: `RrBeatEvent` and `RrBeatListener` interface with thread-safe registration (`CopyOnWriteArrayList`).
* **BLE Unpacking (`BTLEHeartRateDevice.java`)**: Dynamically calculate offset past HR (UINT8 vs UINT16) and Energy Expended (if Bit 3 set), iteratively decode all remaining Little-Endian UINT16 RR words, scale by $1000/1024$, update cumulative timestamps, and dispatch.
* **ANT+ Guard (`ANTHeartRateDevice.java`)**: Track `prevHeartBeatCount` and `prevEventTime`, evaluate rollover $\Delta c$, emit valid beats for $\Delta c == 1$, emit GAP events for $\Delta c \ge 2$, ignore $\Delta c == 0$.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% backward compatibility for HR-only sensors; zero GATT parsing crashes; mathematical precision of RR intervals ($\Delta t \times 1000/1024$ ms); clean-room test pass rate.
* **Risk Rating**: **MEDIUM** (Hardware protocol timing across dual stacks; mitigated by pure mathematical unit tests with synthetic BLE byte buffers and ANT+ event sequences).
