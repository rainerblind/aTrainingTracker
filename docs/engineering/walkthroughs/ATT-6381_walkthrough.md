# Stage 5: Walkthrough & Verification - ATT-6381: BTLERunSpeedDevice Thread Leak & State Hygiene (Teilpaket B)

**Ticket**: [ATT-6381](https://rainerblind.atlassian.net/browse/ATT-6381)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6381`  
**Requirement Mapping**: `REQ-CON-026` | **Test Spec ID**: `TST-CON-026`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Scope
Teilpaket B resolves thread lifecycle leaks, runaway stale distance accumulation, and cross-thread memory visibility defects in `BTLERunSpeedDevice`:
1. **ScheduledExecutorService Thread Termination**: In `shutDown()`, `mScheduler.shutdownNow()` is invoked alongside `mDistanceCalculatorHandle.cancel(true)`, releasing the background thread pool worker and preventing memory/thread leaks (`REQ-CON-026.1`).
2. **Stale Speed Timeout & Runaway Distance Prevention**: Added `mLastMeasurementTimestamp` updated upon receiving valid speed measurements. In `distanceCalculator`, accumulation is gated on packet freshness ($\le 5000\text{ ms}$), preventing distance inflation when stationary or disconnected (`REQ-CON-026.2`).
3. **Sensor Disconnect State Reset**: In `unregisterSensors()`, `mSpeed` is reset to `0.0` and `mLastMeasurementTimestamp` is cleared to `0L`, preventing false carry-over on reconnect (`REQ-CON-026.2`).
4. **Cross-Thread Memory Visibility & Shadowing Cleanliness**: Marked `mSpeed`, `mDistance`, `mDistancePresent`, and `mLastMeasurementTimestamp` as `volatile`. Verified zero duplicate `mAddress` or `mTotalDistance` fields (`REQ-CON-026.3-4`).

---

## 2. Automated Test Execution & Scope (Rule 60)
* **Tier 1 Targeted Test Scope**:
  * Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * Scope: All BANALService device drivers, reconnect managers, sensor managers, and build/ASPICE governance contract tests.
  * Result: **100% PASS** (32 tasks, 0 failures under `parallel_sprint_lock.py`).
* **Living Documentation Governance**:
  * Shards: `docs/requirements/req_tracking_cockpit.md` (`REQ-CON-026`) and `docs/tests/tst_tracking_cockpit.md` (`TST-CON-026`) updated to `[Verified]`.

---

## 3. Acceptance Criteria Verification Table

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1 (Thread Pool Termination)**: `mScheduler` terminates on `shutDown()`, handle cancelled | Unit Test | **MET** | `BTLERunSpeedDeviceTest.kt:testShutDown_cancelsHandleAndShutsDownScheduler` |
| **AC-2 (Stale Speed Gating)**: Packets older than 5000ms do not accumulate stale speed into distance | Unit Test | **MET** | `BTLERunSpeedDeviceTest.kt:testDistanceCalculator_packetTimeoutGating_suppressesStaleSpeedAccumulation` |
| **AC-3 (Disconnect State Reset)**: `unregisterSensors()` clears `mSpeed` and packet timestamp | Unit Test | **MET** | `BTLERunSpeedDeviceTest.kt:testUnregisterSensors_resetsSpeedAndTimestamp` |
| **AC-4 (Field Shadowing Audit)**: `BTLERunSpeedDevice` declares no shadow `mAddress` or `mTotalDistance` | Reflection Test | **MET** | `BTLERunSpeedDeviceTest.kt:testFieldShadowing_noShadowAddressOrTotalDistance` |

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Status**: `adb devices` reports no physical device attached. Emulators prohibited per Rule 79 (`ATT-5890`). All lifecycle methods, scheduler termination, and packet parsing verified via unit tests with MockK.
