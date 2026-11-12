# Stage 2: Requirement & Test Specification - ATT-6742

**Ticket**: [ATT-6742](https://rainerblind.atlassian.net/browse/ATT-6742)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TRK-069` (`REQ-TRK-069.1`, `REQ-TRK-069.4`)  
**Test Mapping**: `TST-TRK-069` (`TST-TRK-069.1`, `TST-TRK-069.4`, `TST-TRK-069.5`)  
**Branch**: `ticket/ATT-6742` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-TRK-069`)
* **REQ-TRK-069.1 (ANT+ estTimestamp UTC Epoch Conversion)**:
  * `ANTHeartRateDevice.java` SHALL convert `estTimestamp` from boot-relative monotonic time (`SystemClock.elapsedRealtime()`) to UTC Unix epoch milliseconds (`System.currentTimeMillis() - SystemClock.elapsedRealtime() + estTimestamp`), setting `beatTimestampMs = (estTimestamp > 0) ? (epochOffset + estTimestamp) : System.currentTimeMillis()`.
  * If `estTimestamp <= 0L`, upon gap recovery or pure JVM unmocked environment, `beatTimestampMs` SHALL fall back to `System.currentTimeMillis()`.
  * ANT+ beat timestamps delivered via `RrBeatEvent` SHALL survive `HRVMetricsDevice.pruneOldBeats(now)` 120s sliding window evaluation without premature eviction.
* **REQ-TRK-069.4 (End-to-End ANT+ & BLE Integration Integrity)**:
  * Integration tests wiring `ANTHeartRateDevice` into `HRVMetricsDevice` SHALL verify that ANT+ beat streams compute valid non-null `rMSSD`, `DFA a1`, and artifact rate.
  * System invariants (`deltaC == 1` continuity guard, $2000\text{ ms}$ drift re-anchoring, BLE backward anchoring) SHALL be strictly preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-TRK-069` (`REQ-TRK-069.1`, `REQ-TRK-069.4`), targeting `ANTHeartRateDevice.java`, `HRVMetricsDevice.kt`, and `WorkoutRrIntervals`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-6713` (commit `c6c7e8c1`) to resolve defect `ATT-6742`.
3. *Root Reason for Existing Formulation*: ANT+ PCC `estTimestamp` is monotonic `elapsedRealtime` (~50,000,000 ms), not wall-clock Unix epoch ms (~1,794,000,000,000 ms). Passing raw monotonic time caused `HRVMetricsDevice.pruneOldBeats(now)` (`cutoff = now - 120_000L`) to immediately evict all beats.
4. *Preservation of Core Invariants*: Strict `deltaC == 1` packet-loss guard, $2000\text{ ms}$ drift threshold, pure JVM test fallback, and zero regression for BLE drivers.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (UTC Epoch Conversion)**:
  * *Given* `ANTHeartRateDevice` receiving an ANT+ packet with monotonic `estTimestamp > 0`,
  * *When* `convertEstTimestampToUtcEpochMs(estTimestamp)` evaluates,
  * *Then* the resulting timestamp is in the `System.currentTimeMillis()` domain within $\pm 50\text{ ms}$ of wall-clock time.
* **Criterion 2 (End-to-End Metric Computation in HRVMetricsDevice)**:
  * *Given* `ANTHeartRateDevice` wired to `HRVMetricsDevice`,
  * *When* streaming continuous synthetic ANT+ packets over a 120-second window,
  * *Then* `HRVMetricsDevice.pruneOldBeats()` retains all active beats and produces non-null `rMSSD`, `DFA a1`, and artifact rate.

---

## 4. Test Verification Specification (`TST-TRK-069`)

| Test ID | Test Scope & Procedure | Verification Oracle | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-TRK-069.1` | `ANTHeartRateDeviceTest.kt`: Unit tests for `convertEstTimestampToUtcEpochMs` | Offset arithmetic & fallback | Matches `System.currentTimeMillis()` within tolerance; handles `estTimestamp <= 0` |
| `TST-TRK-069.4` | `ANTHeartRateDeviceHrvIntegrationTest.kt`: E2E wiring into `HRVMetricsDevice` | `mBeatQueue` size & computed metrics | `rMSSD > 0.0`, `dfaA1 != null`, zero premature pruning |
| `TST-TRK-069.5` | Clean-room test suite execution | Gradle test runner under lock | 100% test pass rate across all device and HRV test suites |
