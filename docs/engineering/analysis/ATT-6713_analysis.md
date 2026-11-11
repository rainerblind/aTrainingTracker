# Stage 1 Analysis: ATT-6713 - Convert ANT+ estTimestamp to UTC epoch ms, re-register mRrIntervalSensor on reconnect, and refine BLE bundled RR drift guard

**Ticket**: [ATT-6713](https://rainerblind.atlassian.net/browse/ATT-6713)  
**Sub-task**: [ATT-6783](https://rainerblind.atlassian.net/browse/ATT-6783) (`[Analysis]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6713`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During sprint testing and forensic review of `ATT-3540` / `ATT-6742`, three critical hardware acquisition bugs were identified:
1. **ANT+ estTimestamp Clock Domain Mismatch**: `AntPlusHeartRatePcc` delivers `estTimestamp` as boot-relative monotonic time (`SystemClock.elapsedRealtime()`), whereas `HRVMetricsDevice` expects UTC Unix epoch ms (`System.currentTimeMillis()`). Every ANT+ beat is pruned immediately (`cutoff = now - 120s`), permanently disabling real-time HRV (`--`) and polluting `WorkoutRrIntervals` with 1970 epoch timestamps.
2. **Sensor Loss on Reconnect**: `unregisterSensors()` in both `ANTHeartRateDevice` and `BTLEHeartRateDevice` cleans registered sensors in `MyDevice` but leaves `mRrIntervalSensor != null`, causing subsequent reconnects to bypass `addAndRegisterSensor`.
3. **BLE Bundled Anchor Future-Shift & Quantization Drift Guard**: Initial BLE bundled packets anchor Beat 1 to `packetArrivalMs`, shifting subsequent beats into the future. Integer `Math.round(rrMs)` accumulation accumulates ~0.5 ms rounding error per beat, falsely exceeding `DRIFT_THRESHOLD_MS = 2000L` after 30–40 min of riding and emitting fake `ARTIFACT_GAP` events.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Live Code Verification**:
  * `ANTHeartRateDevice.java:170`: `beatTimestampMs = (estTimestamp > 0) ? estTimestamp : System.currentTimeMillis();` assigns raw boot-relative time without UTC epoch translation. Verified in `HRVMetricsDevice.kt:148`: `pruneOldBeats(now)` purges every ANT+ beat because `estTimestamp` (e.g., $10^5\text{ ms}$) $\ll$ `now - 120_000L` ($\approx 1.7 \times 10^{12}\text{ ms}$).
  * `ANTHeartRateDevice.java:240-245` & `BTLEHeartRateDevice.java:223-227`: `unregisterSensors()` resets count/time trackers but leaves `mRrIntervalSensor` intact. On reconnect, `if (mRrIntervalSensor == null)` evaluates false, leaving `SensorType.RR_INTERVAL` dead.
  * `BTLEHeartRateDevice.java:188-212`: Initial bundled packets anchor Beat 1 to `packetArrivalMs`, pushing Beats 2..K into the future. Furthermore, `Math.round(rrMs)` truncation causes steady drift between cumulative integer timestamps and phone arrival time, triggering spurious `ARTIFACT_GAP` re-anchors without packet loss.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (2 SP)**:
  * Convert ANT+ `estTimestamp` to UTC epoch ms (`System.currentTimeMillis() - SystemClock.elapsedRealtime() + estTimestamp`, fallback to `System.currentTimeMillis()`) and guard 64s / `DataState.ZERO_DETECTED` disconnects.
  * Reset `mRrIntervalSensor = null` in `unregisterSensors()` for both `ANTHeartRateDevice` and `BTLEHeartRateDevice`.
  * Fix BLE bundled anchor to align the most recent beat with `packetArrivalMs` and back-date prior beats; accumulate double/sub-ms timestamps to eliminate integer drift.
  * End-to-end integration tests wiring `ANTHeartRateDevice` into `HRVMetricsDevice` to prove beats survive `pruneOldBeats()`.
* **Out-of-Scope Non-Goals**:
  * No modifications to `HRVMetricsDevice` DSP formulas, DFA algorithm, or artifact correction matrices.
  * No changes to SQLite schema or other sensor categories (cadence, power, speed).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement ID & Target**: `REQ-CON-027.3` (*BLE Cumulative Timestamps & Drift Guard*) and `REQ-CON-027.4` (*ANT+ Beat Count Continuity*).
* **Historical Origin**: Introduced under `ATT-3540` to enable Kubios-grade beat-to-beat acquisition.
* **Root Reason for Existing Formulation**: Aimed to track cumulative beat intervals while bounding clock drift to 2000 ms. However, it assumed ANT+ `estTimestamp` was already UTC and used integer rounding for BLE accumulation.
* **Preservation of Core Invariants**: Strict `deltaC == 1` continuity guard, packet loss gap flagging (`ARTIFACT_GAP`), and optical sensor fallback (`flag = 0x00`) remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Clock Conversion Utility**: In `ANTHeartRateDevice.java`, compute UTC epoch ms via `epochOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()`, mapping `beatTimestampMs = epochOffset + estTimestamp`.
2. **Lifecycle Reset**: In `unregisterSensors()` of both drivers, set `mRrIntervalSensor = null`.
3. **Sub-ms Accumulator & Backward Bundle Anchor**: In `BTLEHeartRateDevice.java`, back-calculate bundled beats on anchor, and track continuous timestamp with floating-point precision (`double`) to prevent false drift trigger.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Lossless beat-to-beat persistence in `WorkoutRrIntervals`.
  2. Zero regression in 1-Hz HR display or optical wrist sensor compatibility.
  3. Strict ASPICE human decision gate governance.
* **Risk Rating**: **MEDIUM** (Hardware sensor drivers; thoroughly mitigated by deterministic unit and regression test harnesses).
