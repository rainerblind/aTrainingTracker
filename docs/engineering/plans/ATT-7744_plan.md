# Stage 3: Implementation Plan - ATT-7744: Hide Cockpit radar overlay bar while searching for unconnected BLE radar sensor

**Ticket**: [ATT-7744](https://issue-tracker/ATT-7744)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-TRK-062.4`  
**Test Mapping**: `TST-TRK-062.8`  
**Branch**: `feat/ATT-7744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Architecture Overview
In `TrackingTabsScreen.kt`, the `RadarEdgeOverlay` visibility predicate currently treats `radarState.connectionState != RadarConnectionState.DISCONNECTED` as active. Consequently, when searching for sensors (`CONNECTING`), the radar overlay rail appears in the Cockpit even if the radar is unconnected or unpowered. Furthermore, in `BTLERadarDevice.java:startSearching()`, reconnect attempts unconditionally update `RadarStateRepository` to `CONNECTING`, clobbering `DISCONNECTED_ERROR` and temporarily removing the fail-safe amber warning badge.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-062.4` (*Fail-Safe Disconnect, Error State & 1-Tap Reconnect*)
  - Cockpit overlay visible strictly on `CONNECTED`, `DISCONNECTED_ERROR`, or `isSimulating`.
  - Reconnect search preserves `DISCONNECTED_ERROR`.
* **Test Mapping**: `TST-TRK-062.8` in `BTLERadarDeviceTest.kt` and `TrackingTabsScreenTest.kt`.

---

## 3. System Invariants & Preserved Behavior
1. **Fail-Safe Integrity**: Mid-ride disconnect warnings (`DISCONNECTED_ERROR`) remain visible through auto-reconnection loops until reconnection succeeds (`CONNECTED`) or tracker stops (`DISCONNECTED`).
2. **Simulation Fidelity**: `RadarTrafficSimulator.isSimulating` continues to render `RadarEdgeOverlay` regardless of hardware state.
3. **Database & Schema Invariance**: Zero SQLite modifications.
4. **Code Lock**: Production code modifications strictly locked until Gate 3 passes.

---

## Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-062.4` (`TrackingTabsScreen.kt`, `BTLERadarDevice.java`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-5085` (commit `671c4c1a`); refined in `ATT-6703` and `ATT-7537`.
* **Root Reason for Existing Formulation**: `!= DISCONNECTED` assumed all non-idle states belonged in Cockpit view, missing the initial sensor search edge case.
* **Preservation of Core Invariants**: Retains full fail-safe warning capabilities while eliminating false-positive overlay display when no radar is attached.

---

## 4. UI Consistency & Style Guide Compliance (`docs/design_guidelines.md`)
* **Cockpit Cleanliness**: The overlay rail only renders when verified radar telemetry is active or in explicit failure.
* **Touch Pass-Through**: No changes to hit-testing or gesture routing on `RadarEdgeOverlay`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Cockpit Visibility Predicate)**: In `TrackingTabsScreen.kt`, update `isRadarActive`:
   ```kotlin
   val isRadarActive = screenMode == ScreenMode.TRACKING && (
       radarState.connectionState == RadarConnectionState.CONNECTED ||
       radarState.connectionState == RadarConnectionState.DISCONNECTED_ERROR ||
       RadarTrafficSimulator.isSimulating
   )
   ```
2. **Step 2 (BTLERadarDevice Reconnect & Timeout State Guard)**: In `BTLERadarDevice.java:startSearching()`, gate the transition to `CONNECTING` so it executes only when `currentState.getConnectionState() != RadarConnectionState.DISCONNECTED_ERROR`.
   In `notifyStopSearching(boolean success)`, if `!success` and not in `DISCONNECTED_ERROR`, transition to `DISCONNECTED`.
3. **Step 3 (Targeted Unit Tests)**:
   - In `BTLERadarDeviceTest.kt`, test `startSearching_whenDisconnectedError_preservesDisconnectedError()`.
   - In `BTLERadarDeviceTest.kt`, test `notifyStopSearching_failure_transitionsToDisconnected()`.
   - In `TrackingTabsScreenTest.kt`, test overlay visibility predicate for `CONNECTING`, `CONNECTED`, `DISCONNECTED_ERROR`, `DISCONNECTED`.
4. **Step 4 (Test Execution)**:
   Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`.
