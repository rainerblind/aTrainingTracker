# Stage 2: Requirement & Test Specification - ATT-7744: Hide Cockpit radar overlay bar while searching for unconnected BLE radar sensor

**Ticket**: [ATT-7744](https://issue-tracker/ATT-7744)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-TRK-062.4`  
**Test Spec ID**: `TST-TRK-062.8`  
**Branch**: `feat/ATT-7744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-TRK-062.4)

### 1.1 Problem Statement & Rationale
When athletes ride without their radar or before a paired radar establishes connection, the Cockpit overlay bar should not be shown. Currently, searching transitions `RadarStateRepository` to `CONNECTING`, triggering overlay rendering. Additionally, during mid-ride reconnection attempts, `DISCONNECTED_ERROR` must not be overwritten by `CONNECTING`, ensuring the amber fail-safe warning badge remains visible until reconnected or cancelled.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration

### 1.3 Functional & Architectural Requirements
1. `TrackingTabsScreen` SHALL render `RadarEdgeOverlay` strictly when `radarState.connectionState == RadarConnectionState.CONNECTED`, `radarState.connectionState == RadarConnectionState.DISCONNECTED_ERROR`, or `RadarTrafficSimulator.isSimulating == true`.
2. When `radarState.connectionState` is `RadarConnectionState.CONNECTING` or initial `DISCONNECTED`, `RadarEdgeOverlay` SHALL be suppressed.
3. In `BTLERadarDevice.startSearching()`, if `currentState.getConnectionState() == RadarConnectionState.DISCONNECTED_ERROR`, the device SHALL preserve `DISCONNECTED_ERROR` without updating to `CONNECTING`.
4. In `BTLERadarDevice.notifyStopSearching(false)` (initial search failure without connection), the state SHALL transition to `DISCONNECTED` if not in `DISCONNECTED_ERROR`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* an athlete on the Cockpit with radar in search (`CONNECTING`), *When* no connection is established, *Then* `RadarEdgeOverlay` is not displayed.
* **Criterion 2**: *Given* an active radar connection (`CONNECTED`), *When* viewing the Cockpit, *Then* `RadarEdgeOverlay` is displayed.
* **Criterion 3**: *Given* a connected radar drops mid-ride (`DISCONNECTED_ERROR`), *When* auto-reconnect initiates `startSearching()`, *Then* `DISCONNECTED_ERROR` is preserved and the amber hazard badge stays visible.

### 1.5 System Invariants
Zero false "All Clear" signals; simulator preview remains unaffected; touch pass-through preserved.

---

## Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-062.4` (`TrackingTabsScreen.kt`, `BTLERadarDevice.java`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-5085` (commit `671c4c1a`); refined in `ATT-6703` and `ATT-7537`.
* **Root Reason for Existing Formulation**: `!= DISCONNECTED` was an initial heuristic assuming any active/searching state should render in the UI, overlooking initial search when no hardware is present.
* **Preservation of Core Invariants**: Retains fail-safe amber warning during mid-ride disconnects while eliminating spurious UI clutter during unconnected sensor search.

---

## 2. Test Specification (TST-TRK-062)

### Test Case 1: Cockpit Overlay Visibility Gating (`TST-TRK-062.8`)
* **Scope**: Unit / Composable Test in `TrackingTabsScreenTest.kt`
* **Preconditions**: `ScreenMode.TRACKING`.
* **Action**: Inject `RadarConnectionState.CONNECTING` and `DISCONNECTED` $\rightarrow$ verify overlay is absent. Inject `CONNECTED`, `DISCONNECTED_ERROR`, and `isSimulating = true` $\rightarrow$ verify overlay renders.

### Test Case 2: Reconnection Error State Preservation (`TST-TRK-062.8`)
* **Scope**: Unit Test in `BTLERadarDeviceTest.kt`
* **Preconditions**: `BTLERadarDevice` in `DISCONNECTED_ERROR`.
* **Action**: Invoke `startSearching()` $\rightarrow$ verify `RadarStateRepository.getState().connectionState` remains `DISCONNECTED_ERROR`.

### Test Case 3: Fast Targeted Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*"`

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-062.4` | Overlay suppression during sensor search (`CONNECTING`) | `TST-TRK-062.8` | Unit / UI Test |
| `REQ-TRK-062.4` | Overlay visibility on `CONNECTED`, `DISCONNECTED_ERROR`, and simulator | `TST-TRK-062.8` | Unit / UI Test |
| `REQ-TRK-062.4` | Reconnect error state preservation in `BTLERadarDevice` | `TST-TRK-062.8` | Unit Test |
