# Stage 3 Implementation Plan: ATT-5102 - BLE Radar Tail Light Mode Control & Workout Auto-Switch

**Ticket**: [ATT-5102](https://rainerblind.atlassian.net/browse/ATT-5102) | **Sub-task**: [ATT-6617](https://rainerblind.atlassian.net/browse/ATT-6617) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5102`  
**Requirement Mapping**: `REQ-CON-031` (`031.1`–`031.7`) | **Test Spec**: `TST-CON-031` (`031.1`–`031.8`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Shared GATT & Light Controller (`BTLERadarDevice.java`, `RadarLightController.kt`)**:
  - Declare `CHARACTERISTIC_RADAR_LIGHT_CONTROL` (`6a4e3202-667b-11e3-949a-0800200c9a66`) in `BluetoothConstants.java`.
  - Route mode writes via `MyBTLEDevice.enqueueWriteCharacteristic(...)` into the serialized GATT queue without duplicate pairing or DB entries (`REQ-CON-031.1`).
  - Introduce `RadarLightMode` enum (`OFF`, `DAY_FLASH`, `NIGHT_FLASH`, `SOLID`, `PELOTON`) with command byte encoding.
* **Capability Detection & StVZO Pruning (`RadarLightController.kt`)**:
  - Automatically filter available modes under StVZO hardware, exposing only `SOLID` and `OFF` (`REQ-CON-031.2`).
  - Gracefully disable/hide light controls if connected to radar-only devices (e.g. `RVR315`, legacy V1 `RTL515`).
* **Cockpit Quick-Switch Sheet & Edge Overlay (`RadarEdgeOverlay.kt`, `RadarLightQuickSwitchSheet.kt`)**:
  - Tapping top status badge in `RadarEdgeOverlay` opens `AppModalBottomSheet` with active mode indicator and mode buttons (`REQ-CON-031.3`).
* **Workout Auto-Switch & Settings (`TrackerService.java`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`)**:
  - Add `radarAutoLightControl`, `radarLightStartMode`, `radarLightStopMode` preferences.
  - `TrackerService` triggers `setLightMode()` on workout start/finish when auto-light control is enabled (`REQ-CON-031.4,5`).
* **Traffic Simulator Light Emulation (`RadarTrafficSimulator.kt`)**:
  - Emulate light mode reception and expose `currentSimulatedLightMode` for UI testing (`REQ-CON-031.6`).
* **UI Consistency & Localization**:
  - `AppModalBottomSheet` following `design_guidelines.md` §5.3 / §5.8 with `TTColor` tokens.
  - 100% 9-language translation parity in `strings_radar.xml` (`REQ-CON-031.7`).

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Core Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Domain & Constants | `BluetoothConstants.java`<br>`RadarLightMode.kt`<br>`RadarState.kt` | Declare `CHARACTERISTIC_RADAR_LIGHT_CONTROL` (`6a4e3202`); define `RadarLightMode` enum; add light mode to `RadarState`. | `REQ-CON-031.1,2`<br>`TST-CON-031.2` |
| **2** | Light Controller | `RadarLightController.kt` | Implement singleton controller managing mode state, StVZO capability filtering, and dispatching writes via `BTLERadarDevice`. | `REQ-CON-031.1,2,4`<br>`TST-CON-031.1,2,4` |
| **3** | BLE GATT Driver | `BTLERadarDevice.java` | Discover characteristic `6a4e3202`, register controller hook, and route non-blocking light commands via `enqueueWriteCharacteristic`. | `REQ-CON-031.1`<br>`TST-CON-031.1` |
| **4** | Persistence & Settings | `TuningPreferencesDataStore.kt`<br>`RadarSettingsSection.kt` | Add auto-switch preferences (`radarAutoLightControl`, start/stop modes) and UI card in Radar settings. | `REQ-CON-031.4,5`<br>`TST-CON-031.5` |
| **5** | Cockpit Quick Sheet | `RadarLightQuickSwitchSheet.kt`<br>`RadarEdgeOverlay.kt` | Implement modal quick-switch bottom sheet (`AppModalBottomSheet`) and wire top badge tap callback. | `REQ-CON-031.3`<br>`TST-CON-031.3` |
| **6** | Workout Lifecycle | `TrackerService.java` | Hook workout start/resume to apply start light mode and finish/stop to switch light `OFF` when auto-control enabled. | `REQ-CON-031.4`<br>`TST-CON-031.4` |
| **7** | Simulator & Tests | `RadarTrafficSimulator.kt`<br>`BTLERadarDeviceTest.kt`<br>`RadarLightControllerTest.kt` | Emulate light state in simulator; author unit tests covering discovery, queue dispatch, StVZO pruning, and workout hooks. | `REQ-CON-031.1-6`<br>`TST-CON-031.1-6` |
| **8** | Parity & Regression | `TranslationParityTest.kt`<br>Targeted Suite | Verify 9-language parity in `strings_radar.xml` and run targeted unit tests (`banalservice.devices.*`, `trainingtracker.*`). | `REQ-CON-031.7`<br>`TST-CON-031.7,8` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production edits until Gate 3 audit passes (`tools/jira_util.py check-gate`).
* **Zero Duplicate Pairing**: Single GATT connection serves both radar telemetry and light control.
* **Non-Blocking Serialized GATT**: All light commands dispatched through `MyBTLEDevice` queue; never stall radar streaming or watchdog.
* **Fail-Safe Degradation**: Missing light characteristic cleanly disables light controls without impacting radar safety warnings.
* **Risk Rating**: **LOW** (Standard GATT characteristic write on proven serialized queue; zero SQLite migrations).
