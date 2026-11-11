# Stage 2: Requirement & Test Specification - ATT-6713: Convert ANT+ estTimestamp to UTC epoch ms, re-register mRrIntervalSensor on reconnect, and refine BLE bundled RR drift guard

**Ticket**: [ATT-6713](https://rainerblind.atlassian.net/browse/ATT-6713) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6713`  
**Requirement Mapping**: `REQ-TRK-069` | **Test Spec ID**: `TST-TRK-069`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-069)

### 1.1 Problem Statement & Rationale
Hardware RR acquisition under `ATT-3540` / `ATT-6742` contains three critical lifecycle and clock domain defects:
1. `AntPlusHeartRatePcc` passes `estTimestamp` as boot-relative monotonic time (`SystemClock.elapsedRealtime()`), causing `HRVMetricsDevice.pruneOldBeats()` to immediately evict all ANT+ beats (cutoff $\approx 1.7\times 10^{12}\text{ ms}$, whereas `estTimestamp` $\approx 10^5\text{ ms}$) and permanently reporting `--` while corrupting `WorkoutRrIntervals` with 1970 timestamps.
2. `unregisterSensors()` in `ANTHeartRateDevice` and `BTLEHeartRateDevice` fails to reset `mRrIntervalSensor = null`, causing subsequent reconnects to bypass `addAndRegisterSensor` and permanently dropping RR telemetry.
3. BLE bundled packets anchor Beat 1 to `packetArrivalMs`, shifting subsequent beats into the future; furthermore, integer millisecond truncation accumulates $\approx 0.5\text{ ms}$ error per beat, falsely exceeding `DRIFT_THRESHOLD_MS = 2000L` after 30–40 min of riding and firing spurious `ARTIFACT_GAP` events.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-069.1** (*ANT+ estTimestamp UTC Epoch Conversion*): `ANTHeartRateDevice.java` SHALL translate boot-relative `estTimestamp` to UTC epoch milliseconds via `epochOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime()`, setting `beatTimestampMs = (estTimestamp > 0) ? (epochOffset + estTimestamp) : System.currentTimeMillis()`. Fallback safely to `System.currentTimeMillis()` if `estTimestamp <= 0` or after gap recovery.
* **REQ-TRK-069.2** (*Sensor Re-Registration on Reconnect*): `ANTHeartRateDevice.unregisterSensors()` and `BTLEHeartRateDevice.unregisterSensors()` SHALL set `mRrIntervalSensor = null`. On subsequent `registerSensors()`, `mRrIntervalSensor` SHALL be cleanly re-instantiated and registered with `addAndRegisterSensor`.
* **REQ-TRK-069.3** (*BLE Bundled Backward Anchor & Sub-ms Drift Guard*): In `BTLEHeartRateDevice.java`, bundled RR packets ($K > 1$) SHALL anchor the newest beat ($K$) to `packetArrivalMs` and back-calculate prior beats ($t_{k-1} = t_k - RR_k$). Continuous cumulative timestamp tracking SHALL use `double` precision, bounding drift to $< 2000\text{ ms}$ over $\ge 60\text{ min}$ continuous streaming.
* **REQ-TRK-069.4** (*End-to-End ANT+ & BLE Integration Integrity*): End-to-end regression tests wiring `ANTHeartRateDevice` into `HRVMetricsDevice` SHALL verify that ANT+ beats survive `pruneOldBeats()` and compute valid real-time $rMSSD$ and DFA $\alpha_1$, while preserving `deltaC == 1` continuity and optical fallback (`flag = 0x00`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* an ANT+ RR event with `estTimestamp > 0`, *When* computing `beatTimestampMs`, *Then* timestamp is within $\pm 50\text{ ms}$ of `System.currentTimeMillis()`.
* *Given* `ANTHeartRateDevice` wired to `HRVMetricsDevice`, *When* streaming continuous ANT+ beats over 120s, *Then* `pruneOldBeats()` preserves window beats and computes valid $rMSSD$ and $\alpha_1$.
* *Given* `ANTHeartRateDevice` or `BTLEHeartRateDevice`, *When* disconnected and reconnected, *Then* `mRrIntervalSensor` is non-null and registered in `MyDevice`.
* *Given* BLE bundled packet of 3 intervals at `t = 10000 ms` ([800, 810, 820 ms]), *When* timestamps computed, *Then* Beat 3 has `10000 ms`, Beat 2 has `9180 ms`, Beat 1 has `8370 ms`.
* *Given* continuous 60-minute session (3600 beats), *When* accumulating with double precision, *Then* $|t_{\text{cumulative}} - t_{\text{arrival}}| < 2000\text{ ms}$ and zero false `ARTIFACT_GAP` events occur.
* *Invariants*: `deltaC == 1` ANT+ continuity check preserved; `ARTIFACT_GAP` emitted on genuine packet drop; 1-Hz HR display unaffected; optical sensor fallback intact; zero SQLite schema modifications; 100% test pass rate.

---

## 2. Test Specification (TST-TRK-069)

### Test Case 1: ANT+ estTimestamp UTC Epoch Conversion (`TST-TRK-069.1`)
* **Scope**: Unit Test (`ANTHeartRateDeviceTest.kt`)
* **Assertion**: Verify `estTimestamp > 0` translates to UTC epoch ms matching wall-clock time; verify fallback to `System.currentTimeMillis()` when `estTimestamp <= 0` or after connection reset.

### Test Case 2: Driver Reconnection Sensor Re-Registration (`TST-TRK-069.2`)
* **Scope**: Unit Tests (`ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt`)
* **Assertion**: Verify `unregisterSensors()` clears `mRrIntervalSensor` to `null`; verify `registerSensors()` successfully re-instantiates and registers `mRrIntervalSensor` with `addAndRegisterSensor`.

### Test Case 3: BLE Bundled RR Backward Anchoring & Sub-ms Drift (`TST-TRK-069.3`)
* **Scope**: Unit Test (`BTLEHeartRateDeviceTest.kt`)
* **Assertion**: Verify bundled beats anchor newest beat to `packetArrivalMs` and back-date prior beats; verify 3600-beat simulation retains drift $< 2000\text{ ms}$ with zero false `ARTIFACT_GAP`.

### Test Case 4: End-to-End ANT+ & HRVMetricsDevice Integration Regression (`TST-TRK-069.4`)
* **Scope**: Integration Test (`ANTHeartRateDeviceHrvMetricsTest.kt` / `HRVMetricsDeviceTest.kt`)
* **Assertion**: Wire `ANTHeartRateDevice` to `HRVMetricsDevice`, verify beats survive `pruneOldBeats()`, and non-zero $rMSSD$ / valid $\alpha_1$ are computed.

### Test Case 5: Clean-Room Targeted Test Suite Execution (`TST-TRK-069.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ANTHeartRateDeviceTest" --tests "com.atrainingtracker.banalservice.devices.BTLEHeartRateDeviceTest" --tests "com.atrainingtracker.banalservice.devices.hrv.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-069.1` | Unit (ANT+) | `ANTHeartRateDevice.java` | `REQ-TRK-069.1` | Specified |
| `TST-TRK-069.2` | Unit (Drivers) | `ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java` | `REQ-TRK-069.2` | Specified |
| `TST-TRK-069.3` | Unit (BLE) | `BTLEHeartRateDevice.java` | `REQ-TRK-069.3` | Specified |
| `TST-TRK-069.4` | Integration | `ANTHeartRateDevice.java`, `HRVMetricsDevice.kt` | `REQ-TRK-069.4` | Specified |
| `TST-TRK-069.5` | Regression | `devices.*`, `devices.hrv.*` | `REQ-PRO-001` | Specified |
