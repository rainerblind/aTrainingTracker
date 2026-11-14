# Stage 5: Walkthrough & Verification - ATT-7578: Fix ANT+ Bike Radar slot-index track IDs and per-packet window reset

**Ticket**: [ATT-7578](https://rainerblind.atlassian.net/browse/ATT-7578)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-040` (`REQ-CON-040.1`..`REQ-CON-040.4`)  
**Test Mapping**: `TST-CON-040` (`TST-CON-040.1`..`TST-CON-040.4`)  
**Branch**: `feat/ATT-7578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This deliverable resolves radar technical review findings M7/m7/m5 across `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, and `RadarWindowAggregator.kt`:
1. **Range-Continuity Track Association (`REQ-CON-040.1`)**: Replaced raw slot indexing (`baseId + i`) in `ANTBikeRadarDevice.kt` with range-continuity track matching ($r_{new} \in [r_{old} - 16.0\text{ m}, r_{old} + 4.0\text{ m}]$, minimizing $|r_{new} - r_{old}|$). When closer vehicles overtake and slot indices shift, trailing vehicles retain their track identity and pass count increments strictly once.
2. **0-Bin Target Ingestion (`REQ-CON-040.2`)**: Removed defensive `rawRange > 0` filter in `ANTBikeRadarDevice.kt` to ingest targets at $0.0\text{ m}$ ($< 3.125\text{ m}$ distance bin) right as they pass, allowing lateral passing clearance ($\le 3.0\text{ m}$) to latch properly in `RadarWindowAggregator`.
3. **1,000 ms Window Latch Preservation (`REQ-CON-040.3`)**: Added `latchedPassingDistanceTimestampMs` to `RadarWindowAggregator.kt`. Passing clearances persist across intermediate sub-second frames within `WINDOW_DURATION_MS = 1000L` and decay to `null` strictly after 1,000 ms.
4. **1 Hz Driver Gating (`REQ-CON-040.4`)**: Gated `sampleAndResetWindow()` and scalar sensor updates on a 1-second cadence ($\Delta t \ge 1000\text{ ms}$) in both `ANTBikeRadarDevice.kt` and `BTLERadarDevice.java`, while maintaining high-rate (4–8 Hz) reactive streams for live UI overlays and `RadarWindowAggregator.onTargetsUpdated()`.

---

## 2. Requirement & Test Verification Matrix

| Requirement ID | Test Specification ID | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-040.1` | `TST-CON-040.1` | Automated Unit Test (`ANTBikeRadarDeviceTest`) | **PASSED** | `Verified` |
| `REQ-CON-040.2` | `TST-CON-040.2` | Automated Unit Test (`ANTBikeRadarDeviceTest`) | **PASSED** | `Verified` |
| `REQ-CON-040.3` | `TST-CON-040.3` | Automated Unit Test (`RadarWindowAggregatorTest`) | **PASSED** | `Verified` |
| `REQ-CON-040.4` | `TST-CON-040.4` | Automated Unit Test (`ANTBikeRadarDeviceTest`, `BTLERadarDeviceTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Executed via `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --max-workers=2`:
```text
BUILD SUCCESSFUL in 2m 25s
33 actionable tasks: 6 executed, 27 up-to-date
279 tests completed, 0 failed (100% PASS rate)
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
Net-new requirement(s) detected: REQ-CON-040. Bypassing archaeology check cleanly.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Verification Scope**: Pure non-UI backend sensor driver, BLE GATT / ANT+ packet parsing, and aggregator enhancement.
* **UI Consistency (Rule 23)**: No UI layout, screen, or composable modifications made. Visual consistency audit marked N/A.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: 279 targeted unit tests passed with 100% success rate across ANT+, BLE, and Radar packages.
2. **Predefined Network Slot 1 Isolation**: Maintained Predefined Network Slot 1 isolation for ANT+ bike radar channel.
3. **Rule 1.8 Parity**: Constructor state initialization parity maintained in `RadarWindowAggregator`.
4. **Living Documentation Synchronized**: Status of `REQ-CON-040` and `TST-CON-040` updated to `Verified`.
