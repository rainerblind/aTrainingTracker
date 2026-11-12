# Stage 1 Analysis: ATT-6742 - [Bug] ANTHeartRateDevice passes ANT+ elapsedRealtime estTimestamp instead of System.currentTimeMillis(), causing HRVMetricsDevice to purge all ANT+ RR beats (a1, rMSSD, and Artifact Rate remain empty)

**Ticket**: [ATT-6742](https://rainerblind.atlassian.net/browse/ATT-6742)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Branch**: `ticket/ATT-6742`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
* **Symptom**: When tracking with an ANT+ heart rate monitor (e.g., Garmin ANT+ chest strap), heart rate and instantaneous RR interval display normally, but HRV metrics (`HRV_DFA_A1`, `HRV_RMSSD`, and `HRV_ARTIFACT_RATE`) remain permanently empty (`--`) in the cockpit, and post-workout RR intervals in `WorkoutRrIntervals` table carry near-1970/epoch-zero timestamps.
* **Impact**: Athletes using ANT+ chest straps are completely deprived of real-time autonomic threshold guidance (`HRVT1`/`HRVT2`), readiness metrics, and post-workout HRV analysis.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**:
  1. *Clock-Domain Mismatch*: In `ANTHeartRateDevice.java`, the Garmin/Dynastream ANT+ Plugin Service (`AntPlusHeartRatePcc`) delivers `estTimestamp` in `SystemClock.elapsedRealtime()` ms (monotonic device uptime, e.g. ~50,000,000 ms), not Unix epoch wall-clock milliseconds (`System.currentTimeMillis()`, e.g. ~1,794,000,000,000 ms).
  2. *Immediate Queue Purge*: `HRVMetricsDevice.kt` uses `System.currentTimeMillis()` as its time source and every 5 seconds invokes `pruneOldBeats(now)` with cutoff `now - 120_000L`. Because `elapsedRealtime` is orders of magnitude smaller than `now - 120_000L`, `pruneOldBeats()` immediately evicts every ANT+ beat from `mBeatQueue`.
  3. *Zero Metric Computation*: With an empty beat queue, `HRVMetricsDevice` cannot compute `rMSSD`, `DFA a1`, or artifact rate.
* **Independent Peer Review & Verification**:
  * Inspected live codebase on `sprint/2026-46.3`: The defect mechanism was identified and addressed during sprint preparation under companion ticket `ATT-6713` (commit `c6c7e8c1`).
  * `ANTHeartRateDevice.java` includes `convertEstTimestampToUtcEpochMs(long estTimestamp)` which offsets `estTimestamp` by `System.currentTimeMillis() - SystemClock.elapsedRealtime()`, with a fallback to `System.currentTimeMillis()` if `estTimestamp <= 0L`.
  * Verified end-to-end integration via `ANTHeartRateDeviceHrvIntegrationTest.kt` confirming that ANT+ beats persist in `HRVMetricsDevice` and produce non-null `rMSSD` and `DFA a1`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Map and verify requirement `REQ-TRK-069` (`REQ-TRK-069.1` ANT+ UTC epoch timestamp conversion and `REQ-TRK-069.4` end-to-end HRV metrics integration) for `ATT-6742`.
  2. Verify that `ANTHeartRateDevice` converts `estTimestamp` to UTC Unix epoch ms and properly feeds `HRVMetricsDevice`.
  3. Confirm regression tests (`ANTHeartRateDeviceTest` and `ANTHeartRateDeviceHrvIntegrationTest`) pass 100%.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Bleed into BLE bundled RR adjustments (`REQ-TRK-069.3`) or sensor reconnection resets (`REQ-TRK-069.2`) beyond verifying non-regression.
  2. Altering `HRVMetricsDevice` windowing or mathematical filters (Kubios/Lipponen).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-069.1` (*ANT+ estTimestamp UTC Epoch Conversion*) and `REQ-TRK-069.4` (*End-to-End ANT+ & BLE Integration Integrity*) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Commit `c6c7e8c1` under `ATT-6713` (`ATT-6742` consolidated duplicate).
* **Root Reason for Existing Formulation**: Required to resolve clock domain divergence between monotonic ANT+ service timestamps and wall-clock epoch used by `HRVMetricsDevice` and SQLite `WorkoutRrIntervals`.
* **Preservation of Core Invariants**: Retains cumulative beat advancement, $2000\text{ ms}$ drift re-anchoring, and pure JVM test fallbacks.

---

## 5. Architectural Strategy & High-Level Solution
* Trace and verify the implementation in `ANTHeartRateDevice.java`:
  - `convertEstTimestampToUtcEpochMs`:
    $$\text{epochMs} = \text{System.currentTimeMillis()} - \text{SystemClock.elapsedRealtime()} + \text{estTimestamp}$$
  - Feed converted `packetArrivalUtcMs` into `RrBeatEvent(beatTimestampMs, ...)` and forward to `RrBeatListener` (`HRVMetricsDevice` and `TrackerService`).
* Validate test coverage in `ANTHeartRateDeviceHrvIntegrationTest.kt`:
  - `ANTHeartRateDevice emits UTC epoch beats that are NOT pruned by HRVMetricsDevice`: asserts non-null `rMSSD` and `DFA a1`.
  - `ANTHeartRateDevice converts estTimestamp to UTC epoch`: validates arithmetic offset and fallback paths.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `deltaC == 1` strict packet-loss guard preserved; multi-beat gaps emit `GAP` artifact.
  2. `System.currentTimeMillis()` monotonic alignment with $2000\text{ ms}$ drift threshold.
  3. Zero regressions in BLE heart rate processing (`BTLEHeartRateDevice`).
* **Risk Rating**: **LOW** (Defect fix verified, isolated to ANT+ timestamp calculation with fallback, fully covered by integration tests).
