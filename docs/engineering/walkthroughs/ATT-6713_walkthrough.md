# Stage 5: Walkthrough & Verification - ATT-6713: Convert ANT+ estTimestamp to UTC epoch ms, re-register mRrIntervalSensor on reconnect, and refine BLE bundled RR drift guard

**Ticket**: [ATT-6713](https://rainerblind.atlassian.net/browse/ATT-6713)  
**Sub-task**: [ATT-6794](https://rainerblind.atlassian.net/browse/ATT-6794) (`[Test]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-069`  
**Test Mapping**: `TST-TRK-069`  
**Branch**: `feature/ATT-6713`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

Resolved three critical hardware acquisition bugs across ANT+ and Bluetooth LE beat-to-beat drivers:
* **ANT+ UTC Epoch Translation (`ANTHeartRateDevice.java`)**: Converted boot-relative `estTimestamp` (`SystemClock.elapsedRealtime()`) to UTC Unix epoch milliseconds (`System.currentTimeMillis() - SystemClock.elapsedRealtime() + estTimestamp`). Beats now survive `HRVMetricsDevice.pruneOldBeats()` over the 120s sliding window, producing real-time $rMSSD$ and DFA $\alpha_1$, and avoiding 1970 timestamp pollution in `WorkoutRrIntervals`. Added tracker resets on `DataState.ZERO_DETECTED` and $>64\text{ s}$ dropouts.
* **Reconnection Sensor Re-Registration (`ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java`)**: Explicitly cleared `mRrIntervalSensor = null` in `unregisterSensors()` and `shutDown()` for both drivers, ensuring reconnecting sensors successfully re-instantiate and register `mRrIntervalSensor` in `MyDevice`.
* **BLE Bundled Backward Anchoring & Sub-ms Drift Guard (`BTLEHeartRateDevice.java`)**: Reverse-anchored bundled packets ($K > 1$) by assigning the newest beat to `packetArrivalMs` and back-calculating prior beats ($t_{k-1} = t_k - RR_k$). Upgraded cumulative timestamp tracking to `double` precision, eliminating $\approx 0.5\text{ ms}$ integer truncation drift and preventing false `ARTIFACT_GAP` triggers after 30–40 min.
* **End-to-End Integration Verification (`ANTHeartRateDeviceHrvIntegrationTest.kt`)**: Implemented 150-beat simulation wiring `ANTHeartRateDevice` to `HRVMetricsDevice`, proving end-to-end metric computation with 0% artifact rate and `VALID` signal quality status (consolidating `ATT-6742`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-069.1` | `TST-TRK-069.1` | `ANTHeartRateDeviceTest.kt` | **PASSED** | `Verified` |
| `REQ-TRK-069.2` | `TST-TRK-069.2` | `ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt` | **PASSED** | `Verified` |
| `REQ-TRK-069.3` | `TST-TRK-069.3` | `BTLEHeartRateDeviceTest.kt` | **PASSED** | `Verified` |
| `REQ-TRK-069.4` | `TST-TRK-069.4` | `ANTHeartRateDeviceHrvIntegrationTest.kt` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-069.5` | Tier 1 Targeted Driver & HRV Regression Suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

* **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTHeartRateDevice*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEHeartRateDevice*" --tests "com.atrainingtracker.banalservice.devices.hrv.*"`
  - Result: **BUILD SUCCESSFUL** in 22s (33 actionable tasks: 1 executed, 32 up-to-date; 100% PASS across 22 test methods).
* **Living Documentation Governance**:
  - Command: `python3 tools/verify_requirement_governance.py --sync-indexes`
  - Result: **PASS** (Master index tables synchronized with 100% parity; `REQ-TRK-069` and `TST-TRK-069` verified).

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Testing Scope**: On-device emulator execution was explicitly retired per user directive ("using the emulator wasn't a good Idea. We must not use it anymore"). Verification was established via full unit test coverage and end-to-end integration tests between `ANTHeartRateDevice` and `HRVMetricsDevice`.
* **Visual Consistency (Rule 23)**: No UI changes; pure protocol driver and timestamp synchronization fix.

---

## 5. Invariant & Governance Verification

1. **Continuity & Lossless Persistence**: Strict ANT+ `deltaC == 1` continuity check and `WorkoutRrIntervals` storage intact.
2. **Optical Sensor Compatibility**: 1-Hz HR calculation and null RR interval sensor handling when flag `0x10` is absent preserved.
3. **Thread Safety**: Sensor updates remain isolated to driver threads and synchronized `onRrBeat` listeners.
4. **Database Schema Integrity**: Zero SQLite schema modifications or DDL migrations.
5. **Living Documentation Synchronized**: Status in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md` updated to `[Verified]`; master tables synchronized with 100% parity.
