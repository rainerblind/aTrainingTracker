# Stage 3: Implementation Plan - ATT-6742: Convert ANT+ estTimestamp to UTC Unix Epoch ms for HRV Metrics

**Ticket**: [ATT-6742](https://rainerblind.atlassian.net/browse/ATT-6742)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TRK-069` (`REQ-TRK-069.1`, `REQ-TRK-069.4`)  
**Test Mapping**: `TST-TRK-069` (`TST-TRK-069.1`, `TST-TRK-069.4`, `TST-TRK-069.5`)  
**Branch**: `ticket/ATT-6742` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
When tracking with an ANT+ heart rate strap, `AntPlusHeartRatePcc` delivers `estTimestamp` in `SystemClock.elapsedRealtime()` monotonic ms since device boot (~50,000,000 ms), not Unix epoch wall-clock milliseconds (`System.currentTimeMillis()`). `HRVMetricsDevice.pruneOldBeats(now)` immediately evicts beats with timestamps older than `now - 120_000L`, causing `mBeatQueue` to remain empty and preventing real-time `rMSSD`, `DFA a1`, and artifact rate from being computed.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-069` (*ANT+ UTC Epoch Timestamping & HRV Integration Integrity*, clauses `069.1` and `069.4`).
* **Test Mapping**: `TST-TRK-069` (`TST-TRK-069.1` UTC conversion unit tests, `TST-TRK-069.4` HRVMetricsDevice E2E integration tests, `TST-TRK-069.5` clean-room test suite).

---

## 3. System Invariants & Preserved Behavior
1. **Packet Loss Guard**: Strict `deltaC == 1` beat count continuity guard is preserved; multi-beat packet drops emit `GAP` artifact.
2. **Cumulative Monotonic Alignment**: Beat timestamps advance monotonically by `roundedRrMs` with a $2000\text{ ms}$ drift threshold re-anchoring to `packetArrivalUtcMs`.
3. **Pure JVM Test Fallback**: `convertEstTimestampToUtcEpochMs` gracefully falls back to `System.currentTimeMillis()` when `SystemClock.elapsedRealtime()` throws unmocked exceptions in unit tests.
4. **Zero BLE Regressions**: `BTLEHeartRateDevice` timestamp handling remains completely unaffected.

---

## 4. Proposed Architectural Changes
* **Component 1**: `ANTHeartRateDevice.java` (`com.atrainingtracker.banalservice.devices.ant_plus`)
  - Enforces `convertEstTimestampToUtcEpochMs(estTimestamp)` calculating $\text{System.currentTimeMillis()} - \text{SystemClock.elapsedRealtime()} + \text{estTimestamp}$.
  - Dispatches `RrBeatEvent` with valid UTC epoch `beatTimestampMs` to `RrBeatListener`.
* **Component 2**: Living Documentation Mapping
  - Ensure `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md` map `ATT-6742` alongside companion `ATT-6713`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Verification & Living Doc Sync
* Audit `ANTHeartRateDevice.java` for `convertEstTimestampToUtcEpochMs` and cumulative beat alignment.
* Update `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-069`) and `docs/tests/tst_tracking_cockpit.md` (`TST-TRK-069`) to record `ATT-6742`.

### Step 2: Test Execution & Verification
* Run unit tests: `ANTHeartRateDeviceTest` and `ANTHeartRateDeviceHrvIntegrationTest`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDevice*"`

### Step 3: Governance Audit
* Run `python3 tools/verify_requirement_governance.py` confirming 100% parity across living doc tables.

---

## 6. Verification & Rollback Plan
* **Verification**: Verify that synthetic ANT+ packets stream into `HRVMetricsDevice` with valid epoch timestamps without being pruned by `pruneOldBeats()`.
* **Rollback**: Git branch isolation (`ticket/ATT-6742`) allows instant reset to `sprint/2026-46.3`.
