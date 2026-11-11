# Stage 2: Requirement & Test Specification - ATT-6707: Fix RadarLightController pause/stop lifecycle, detect StVZO hardware, and gate optimistic state updates

**Ticket**: [ATT-6707](https://rainerblind.atlassian.net/browse/ATT-6707)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-CON-031` (*BLE Radar Tail Light Mode Control & Workout Auto-Switch*)  
**Test Spec ID**: `TST-CON-031`  
**Branch**: `subagent-ATT-1286---Radar-Flash-Agent-self-6fd93067`  
**Author**: AI Agent 1 (Flash Agent)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-CON-031`)

### 1.1 Problem Statement & Rationale
Sprint `2026-46.1` audit of `ATT-5102` revealed three systemic defects in Garmin Varia radar tail light mode control:
1. `BTLERadarDevice.java` hardcodes `isStvzo = false` (never querying Device Information `0x2A24` Model Number), leaving StVZO flashing-mode pruning disabled, failing to read initial tail light state on connect, and failing to restore active light mode after mid-ride BLE reconnects.
2. `TrackerService.onDestroy()` unconditionally stops the tail light during workout pause (extinguishing rear safety light at red lights), while `RESUME_AUTOMATICALLY` does not restore it. On stop, `onWorkoutStopped` fires twice and spawns an asynchronous coroutine reading DataStore, racing with `BANALService` unbinding and dropping the `OFF` write. `context.filesDir` checks cause `MockKException` crashes in strict unit tests (`Coding Standards §5.5`).
3. `RadarLightController.setLightMode()` mutates `_currentMode` and `RadarStateRepository` optimistically before confirming dispatch. `RadarSettingsSection` uses an anti-pattern 5-chip `FlowRow` (> 4 options) and dispatches live GATT writes when editing idle preferences. `RadarLightQuickSwitchSheet` uses raw `Color.Gray` and lacks `@Preview`s.

### 1.2 Functional & Architectural Requirements
1. *BLE Light Control Discovery, StVZO Detection & Serialized Queue Routing (`REQ-CON-031.1`)*:
   • `BTLERadarDevice` SHALL discover Device Information service `0x180A` and read `0x2A24` (Model Number string). When matching StVZO patterns (`010-03180-10`, `RTL516`, etc.), `isStvzo` SHALL be set to `true`.
   • `BTLERadarDevice` SHALL read/subscribe `6a4e3202` on connect to sync physical tail light state into `RadarState` and re-apply the active workout light mode upon mid-ride reconnect.
2. *Tail Light Mode Command Mapping, StVZO Capability Pruning & Gated Mutation (`REQ-CON-031.2`)*:
   • Flashing modes (`DAY_FLASH`, `NIGHT_FLASH`, `PELOTON`) SHALL be pruned under StVZO hardware, allowing only `SOLID` and `OFF`.
   • `RadarLightController.setLightMode()` SHALL mutate `_currentMode` and `RadarStateRepository` strictly when `dispatched == true` (or confirmed by GATT).
3. *Cockpit Quick-Switch Bottom Sheet Polish (`REQ-CON-031.3`)*:
   • Inactive mode indicators SHALL use `MaterialTheme.colorScheme.outline` (avoiding raw `Color.Gray`).
   • The component SHALL provide Light, Dark, and StVZO `@Preview` composables.
4. *Workout Lifecycle Auto-Switch Automation & Safety Pause Preservation (`REQ-CON-031.4`)*:
   • `RadarLightController` SHALL use synchronously cached preferences without unstructured background coroutines or `context.filesDir` checks (`Coding Standards §5.5`).
   • During workout **Pause**, the system SHALL strictly preserve active tail light illumination and NEVER extinguish the rear safety light. `TrackerService` SHALL support `StartType.RESUME_AUTOMATICALLY` alongside `START_NORMAL` and `RESUME_BY_USER`.
   • On explicit workout stop (`handleActionStop`), `TrackerService` SHALL dispatch `RadarLightMode.OFF` synchronously *before* unbinding `BANALService`. `onDestroy()` SHALL NOT duplicate or misattribute light shutdown.
5. *Settings Configuration & DataStore Persistence (`REQ-CON-031.5`)*:
   • `RadarSettingsSection.kt` default light mode selector SHALL use a compliant single-selection dialog/menu (replacing the anti-pattern 5-chip `FlowRow`), and editing preferences while idle SHALL update DataStore without dispatching live peripheral writes.
6. *Traffic Simulator Light State Emulation (`REQ-CON-031.6`)*:
   • `RadarTrafficSimulator` SHALL emulate tail light command reception and state transitions for UI testing without physical peripheral hardware.
7. *9-Language Localization Parity (`REQ-CON-031.7`)*:
   • All light mode names, dialog titles, and settings strings in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (StVZO Model Detection & Mode Pruning)**:
  * *Given* a connected Garmin Varia BLE peripheral exposing Model Number string `010-03180-10` on characteristic `0x2A24`,
  * *When* service discovery completes,
  * *Then* `RadarLightController.isStvzo` becomes `true` and only `SOLID` and `OFF` are presented in quick-switch and settings selectors.
* **Criterion 2 (Safety Pause Preservation)**:
  * *Given* an active workout with `radarAutoLightControl` enabled and tail light illuminated,
  * *When* the workout is paused (e.g. at a traffic light),
  * *Then* the rear safety light remains illuminated in its active mode.
* **Criterion 3 (Synchronous Stop Teardown)**:
  * *Given* an active workout with `radarAutoLightControl` enabled,
  * *When* the workout is explicitly stopped via `handleActionStop`,
  * *Then* `RadarLightMode.OFF` is dispatched synchronously before `BANALService` unbinds.
* **Criterion 4 (Gated State Mutation)**:
  * *Given* no connected radar device and simulator inactive,
  * *When* `RadarLightController.setLightMode()` is invoked,
  * *Then* it returns `false` and does not mutate `_currentMode` or `RadarStateRepository`.
* **Criterion 5 (Settings Selector & Idle Behavior)**:
  * *Given* the app is idle outside an active workout,
  * *When* the user changes default light mode in `RadarSettingsSection`,
  * *Then* DataStore is updated without dispatching live peripheral writes.

### 1.4 System Invariants
- StVZO regulatory compliance; non-blocking serialized GATT writes (zero collision with threat CCCD); rear safety light illuminated during pause; zero MockK crashes in unit tests; 100% 9-language translation parity.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-CON-031` (BLE Radar Tail Light Mode Control & Workout Auto-Switch), targeting `BTLERadarDevice.java`, `RadarLightController.kt`, `TrackerService.java`, `RadarSettingsSection.kt`, and `RadarLightQuickSwitchSheet.kt`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-46.1 via `ATT-5102` (commit `3532c253`).
3. *Root Reason for Existing Formulation*: Rapid initial prototype hardcoded `isStvzo = false` pending physical hardware arrival, placed auto-switch teardown in `onDestroy()` without differentiating Pause from Stop, and reused live quick-switch logic in Settings.
4. *Preservation of Core Invariants*: StVZO regulatory compliance; non-blocking GATT FIFO queueing; safety illumination during stops; zero MockK crashes in unit tests; 100% 9-language localization parity.

---

## 2. Test Specification (`TST-CON-031`)

### Test Case 1: StVZO Model Number Detection & Reconnect Sync (`TST-CON-031.1`)
* **Scope**: JVM Unit Test (`BTLERadarDeviceTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLERadarDeviceTest.kt`
* **Preconditions**: Mocked BluetoothGatt with Device Information service `0x180A` and characteristic `0x2A24`.
* **Action**: Emulate characteristic read returning `010-03180-10`.
* **Expected Result**: `RadarLightController.isStvzo.value` evaluates to `true`; reconnect re-enqueues active workout light mode.

### Test Case 2: Mode Pruning & Gated Mutation (`TST-CON-031.2`)
* **Scope**: JVM Unit Test (`RadarLightControllerTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/radar/RadarLightControllerTest.kt`
* **Preconditions**: `RadarLightController` in reset state, no connected device.
* **Action**: Call `setLightMode(RadarLightMode.DAY_FLASH)`.
* **Expected Result**: Returns `false`; `_currentMode.value` remains `OFF`; `RadarStateRepository.getState().lightMode` remains `OFF`.

### Test Case 3: Workout Lifecycle Auto-Switch & Pause Safety (`TST-CON-031.4`)
* **Scope**: Robolectric Unit Test (`TrackerServiceTest.kt` / `RadarLightControllerTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceTest.kt`
* **Preconditions**: Active workout with `radarAutoLightControl = true`, tail light in `DAY_FLASH`.
* **Action**: Trigger workout pause (`START_TYPE == RESUME_AUTOMATICALLY` / auto-pause), followed by `handleActionStop`.
* **Expected Result**: Pause does not extinguish tail light; `handleActionStop` synchronously triggers `OFF` before unbinding. Zero `MockKException` on `context.filesDir`.

### Test Case 4: 9-Language Localization & Format Audit (`TST-CON-031.7`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Verify `strings_radar.xml` across all 9 locales.
* **Expected Result**: 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 5: Tier 1 Targeted Regression Scope (`TST-CON-031.8`)
* **Scope**: Targeted Package Regression
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-031.1` | Unit | `BTLERadarDevice.onCharacteristicRead` | `REQ-CON-031.1` | Specified |
| `TST-CON-031.2` | Unit | `RadarLightController.setLightMode` | `REQ-CON-031.2` | Specified |
| `TST-CON-031.3` | UI / Preview | `RadarLightQuickSwitchSheet` | `REQ-CON-031.3` | Specified |
| `TST-CON-031.4` | Unit / Lifecycle | `RadarLightController.onWorkout*` / `TrackerService` | `REQ-CON-031.4` | Specified |
| `TST-CON-031.5` | UI / Settings | `RadarSettingsSection` | `REQ-CON-031.5` | Specified |
| `TST-CON-031.6` | Unit / Sim | `RadarTrafficSimulator.setSimulatedLightMode` | `REQ-CON-031.6` | Specified |
| `TST-CON-031.7` | Localization | `TranslationParityTest` | `REQ-CON-031.7` | Specified |
| `TST-CON-031.8` | Regression | Tier 1 Targeted Unit Suite | `REQ-PRO-001` | Specified |
