# Stage 5: Walkthrough & Verification - ATT-7575: Arbitrate concurrent BLE and ANT+ radar sources in RadarStateRepository

**Ticket**: [ATT-7575](https://rainerblind.atlassian.net/browse/ATT-7575)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-041` (`REQ-CON-041.1`..`REQ-CON-041.4`)  
**Test Mapping**: `TST-CON-041` (`TST-CON-041.1`..`TST-CON-041.4`)  
**Branch**: `feat/ATT-7575`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This deliverable resolves radar technical review finding M2 (Major) by implementing deterministic multi-source arbitration and transport state decoupling across `RadarStateRepository.kt`, `RadarState.kt`, `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, and `RadarTrafficSimulator.kt`:
1. **RadarSource Hierarchy (`REQ-CON-041.1`)**: Defined `RadarSource` enum (`BLE(3) > ANT_PLUS(2) > SIMULATOR(1) > NONE(0)`) and tagged emitted `RadarState` instances across BLE, ANT+, and simulated drivers.
2. **Priority Stream Arbitration (`REQ-CON-041.2`)**: Implemented priority arbitration in `RadarStateRepository.updateState()`. When BLE and ANT+ streams stream concurrently, `BLE` takes precedence. Telemetry frames from secondary/subordinate sources do not clobber the primary stream.
3. **Secondary Watchdog Disconnect Isolation & Failover (`REQ-CON-041.3`)**: Secondary watchdog timeout events (e.g., ANT+ emitting `DISCONNECTED_ERROR`) update only the isolated secondary source slot in `sourceStates` and never overwrite an active primary `CONNECTED` state. If the active primary disconnects, the repository seamlessly fails over to the highest-priority connected secondary source.
4. **Isolated Transport Inspection (`REQ-CON-041.4`)**: Exposed `RadarStateRepository.getState(source: RadarSource): RadarState` and scoped `ANTBikeRadarDevice.isReceivingData()` strictly to `RadarSource.ANT_PLUS`, preventing cross-transport false status reporting.
5. **Simulator Preemption Decoupling**: Refined `RadarTrafficSimulator.onPhysicalHardwareConnected()` to only stop active simulations (`if (isSimulating)`), and scoped `stop()` to update `RadarSource.SIMULATOR` without clobbering active hardware streams.

---

## 2. Requirement & Test Verification Matrix

| Requirement ID | Test Specification ID | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-041.1` | `TST-CON-041.1` | Automated Unit Test (`RadarStateRepositoryTest`) | **PASSED** | `Verified` |
| `REQ-CON-041.2` | `TST-CON-041.2` | Automated Unit Test (`RadarStateRepositoryTest`) | **PASSED** | `Verified` |
| `REQ-CON-041.3` | `TST-CON-041.3` | Automated Unit Test (`RadarStateRepositoryTest`) | **PASSED** | `Verified` |
| `REQ-CON-041.4` | `TST-CON-041.4` | Automated Unit Test (`ANTBikeRadarDeviceTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Executed via `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.RadarStateRepositoryTest" --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeRadarDeviceTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest" --tests "com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulatorTest" --max-workers=2`:
```text
BUILD SUCCESSFUL in 1m 29s
33 actionable tasks: 4 executed, 29 up-to-date
77 tests completed, 0 failed (100% PASS rate)
- ANTBikeRadarDeviceTest: 19 tests, 0 failures
- RadarTrafficSimulatorTest: 11 tests, 0 failures
- RadarStateRepositoryTest: 4 tests, 0 failures
- BTLERadarDeviceTest: 43 tests, 0 failures
```

### Requirement Governance Audit
Executed via `python3 tools/verify_requirement_governance.py`:
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: All REQ and TST heading IDs are unique across living documentation shards.
PASS: All cited REQ and TST references resolve to authentic headings.
PASS: Zero ungrounded [Verified] requirements detected.
PASS: 100% REQ-TST bidirectional traceability verified.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
Net-new requirement(s) detected: REQ-CON-041. Bypassing archaeology check cleanly.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Verification Scope**: Pure non-UI backend sensor telemetry repository, multi-source arbitration logic, and transport isolation.
* **UI Consistency (Rule 23)**: No UI layout, screen, or composable modifications made. Visual consistency audit marked N/A.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: 77 targeted unit tests passed with 100% success rate across radar, ANT+, and BLE device packages.
2. **Predefined Network Slot 1 Isolation**: Maintained Predefined Network Slot 1 isolation for ANT+ bike radar channel.
3. **Thread Safety**: Guaranteed atomic state arbitration via synchronized lock in `RadarStateRepository`.
4. **Living Documentation Synchronized**: Status of `REQ-CON-041` and `TST-CON-041` updated to `Verified`.
