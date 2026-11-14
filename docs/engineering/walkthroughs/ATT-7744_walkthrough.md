# Stage 5: Walkthrough & Verification - ATT-7744: Hide Cockpit radar overlay bar while searching for unconnected BLE radar sensor

**Ticket**: [ATT-7744](https://issue-tracker/ATT-7744)  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-TRK-062.4`  
**Test Mapping**: `TST-TRK-062.8`  
**Branch**: `feat/ATT-7744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the construction and verification of **ATT-7744**:
1. **Cockpit Radar Overlay Visibility Gating (`REQ-TRK-062.4`)**:
   - In `TrackingTabsScreen.kt`, extracted and applied `@VisibleForTesting fun isRadarOverlayVisible(screenMode, connectionState, isSimulating)`:
     ```kotlin
     screenMode == ScreenMode.TRACKING && (
         connectionState == RadarConnectionState.CONNECTED ||
         connectionState == RadarConnectionState.DISCONNECTED_ERROR ||
         isSimulating
     )
     ```
   - Strictly suppresses `RadarEdgeOverlay` rendering during initial sensor search (`RadarConnectionState.CONNECTING`) and idle `DISCONNECTED` states, eliminating spurious radar UI clutter when riding without a radar or before connection is established.
2. **Reconnection & Search Timeout Fail-Safe Preservation (`REQ-TRK-062.4`)**:
   - In `BTLERadarDevice.java:startSearching()`, gated the `RadarConnectionState.CONNECTING` update with `currentState.getConnectionState() != RadarConnectionState.DISCONNECTED_ERROR`, preventing auto-reconnect loops from clobbering the amber warning badge during mid-ride dropouts.
   - Overrode `notifyStopSearching(boolean success)` in `BTLERadarDevice.java`: when initial search fails (`!success`) and the device is not in `DISCONNECTED_ERROR`, state cleanly transitions to `DISCONNECTED`.
3. **Targeted Unit Test Coverage (`TST-TRK-062.8`)**:
   - Added visibility gating assertions in `RadarEdgeOverlayTest.kt` verifying suppression during `CONNECTING` and idle `DISCONNECTED`, and visibility on `CONNECTED`, `DISCONNECTED_ERROR`, and simulator execution across screen modes.
   - Added state preservation assertions in `BTLERadarDeviceTest.kt` verifying `startSearching()` and `notifyStopSearching(false)` under `DISCONNECTED_ERROR` and `DISCONNECTED`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-062.4` | `TST-TRK-062.8` | Overlay suppression during search & display on connected/error/simulation (`RadarEdgeOverlayTest.kt`) | **PASSED** | `Verified` |
| `REQ-TRK-062.4` | `TST-TRK-062.8` | Reconnect error state preservation in `BTLERadarDevice` (`BTLERadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*" --max-workers=2`
* **Result**: `BUILD SUCCESSFUL` (229 tests completed, 0 failures, 0 errors, 0 skipped).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py --sync-indexes`
* **Result**: `PASS` (Master index tables successfully synchronized with 100% parity across living doc shards).

---

## 4. Invariant & Governance Verification

1. **Fail-Safe Warning Integrity**: Mid-ride disconnect warnings (`DISCONNECTED_ERROR`) remain permanently visible through auto-reconnection loops, ensuring the cyclist is never presented with a false all-clear state.
2. **Simulation Fidelity**: `RadarTrafficSimulator.isSimulating` continues to render `RadarEdgeOverlay` regardless of hardware sensor state.
