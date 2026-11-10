# Stage 2: Requirement & Test Specification - ATT-5102: BLE Radar Tail Light Mode Control

**Ticket**: [ATT-5102](https://rainerblind.atlassian.net/browse/ATT-5102)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-CON-031` (*BLE Radar Tail Light Mode Control & Workout Auto-Switch*)  
**Test Spec ID**: `TST-CON-031`  
**Branch**: `feature/ATT-5102`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-031)

### 1.1 Problem Statement & Rationale
Garmin Varia radar tail lights (e.g. RearVue 820) expose a dedicated BLE light control characteristic `6a4e3202-667b-11e3-949a-0800200c9a66` on service `6a4e3200-667b-11e3-949a-0800200c9a66`. Cyclists require handlebar switching of tail light modes without reaching behind the saddle, coupled with automated workout start/finish lighting control to preserve peripheral battery.

### 1.2 Functional & Architectural Requirements
1. *BLE Light Control Discovery & Serialized Queue Routing (`REQ-CON-031.1`)*: On discovery of characteristic `6a4e3202`, `BTLERadarDevice` SHALL register light control capabilities and route mode write commands via `enqueueWriteCharacteristic` into the serialized GATT FIFO queue without blocking radar telemetry or watchdog checks.
2. *Tail Light Mode Command Mapping & StVZO Capability Pruning (`REQ-CON-031.2`)*: The system SHALL support `RadarLightMode` states (`OFF`, `DAY_FLASH`, `NIGHT_FLASH`, `SOLID`, `PELOTON`). When connected to StVZO hardware, flashing modes SHALL be pruned automatically from selection, exposing only `SOLID` and `OFF`.
3. *Cockpit Quick-Switch Bottom Sheet (`REQ-CON-031.3`)*: Tapping the radar status badge in `RadarEdgeOverlay` SHALL display a modal quick-switch bottom sheet presenting available light modes with live state indication; on radar devices lacking light characteristic `6a4e3202`, the badge SHALL remain non-interactive.
4. *Workout Lifecycle Auto-Switch Automation (`REQ-CON-031.4`)*: When `radarAutoLightControl` is enabled, `TrackerService` SHALL command `RadarLightMode.DAY_FLASH` (or `SOLID` under StVZO) upon workout start/resume and revert to `RadarLightMode.OFF` upon workout stop/finish.
5. *Settings Configuration & DataStore Persistence (`REQ-CON-031.5`)*: `RadarSettingsSection.kt` SHALL expose tail light controls, default mode selection, and the workout auto-switch toggle, backed by `TuningPreferencesDataStore.kt`.
6. *Traffic Simulator Light State Emulation (`REQ-CON-031.6`)*: `RadarTrafficSimulator` SHALL emulate tail light command reception and state transitions for UI testing without physical peripheral hardware.
7. *9-Language Localization Parity (`REQ-CON-031.7`)*: All light mode names, dialog titles, and settings strings in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Quick-Switch Write)**: *Given* a connected Garmin Varia peripheral exposing `6a4e3202`, *When* cyclist selects "Day Flash", *Then* `BTLERadarDevice` enqueues the light mode write to `6a4e3202` without interrupting radar telemetry streaming.
* **Criterion 2 (StVZO Pruning & Legacy Degradation)**: *Given* StVZO hardware or legacy radar without `6a4e3202`, *When* evaluated, *Then* flashing modes are omitted (StVZO) or the quick-switch sheet is disabled (legacy) without errors.
* **Criterion 3 (Workout Auto-Switch)**: *Given* `radarAutoLightControl` is enabled, *When* workout starts, *Then* `RadarLightController` automatically transmits the active light mode command.

### 1.4 System Invariants
* Non-blocking serialized GATT writes; StVZO legal compliance; fail-safe degradation on radar-only hardware; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 2. Test Specification (TST-CON-031)

### Test Case 1: Characteristic Discovery & Queue Routing (`TST-CON-031.1`)
* **Scope**: Unit Test (`BTLERadarDeviceTest.kt`)
* **Preconditions**: Mock GATT peripheral discovering characteristic `6a4e3202`.
* **Action**: Invoke light command write and verify insertion into `mWriteCharacteristicQueue`.
* **Expected Result**: Characteristic write executes asynchronously via GATT queue without starvation.

### Test Case 2: Mode Command Encoding & StVZO Pruning (`TST-CON-031.2`)
* **Scope**: Unit Test (`RadarLightControllerTest.kt`)
* **Preconditions**: `RadarLightMode` enum instances and StVZO variant flag.
* **Action**: Encode command payloads and query available modes.
* **Expected Result**: Produces correct byte commands; prunes flashing modes under StVZO.

### Test Case 3: Cockpit Quick-Switch Sheet UI & State (`TST-CON-031.3`)
* **Scope**: Compose Unit Test (`RadarLightQuickSwitchSheetTest.kt`)
* **Action**: Render sheet with current state and select mode.
* **Expected Result**: Modal sheet renders mode options; invokes selection callback; disables badge tap on radar-only devices.

### Test Case 4: Workout Lifecycle Auto-Switch Automation (`TST-CON-031.4`)
* **Scope**: Unit Test (`RadarLightControllerTest.kt`)
* **Action**: Trigger workout start and finish events with `radarAutoLightControl = true`.
* **Expected Result**: Automatically transmits active light mode on start and `OFF` on finish.

### Test Case 5: 9-Language Localization & Translation Parity (`TST-CON-031.7`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% string and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT in `strings_radar.xml`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-031.1` | Unit | `BTLERadarDevice.java` | `REQ-CON-031.1` | Specified |
| `TST-CON-031.2` | Unit | `RadarLightController.kt` | `REQ-CON-031.2` | Specified |
| `TST-CON-031.3` | UI | `RadarLightQuickSwitchSheet.kt` | `REQ-CON-031.3` | Specified |
| `TST-CON-031.4` | Unit | `RadarLightController.kt`, `TrackerService.java` | `REQ-CON-031.4` | Specified |
| `TST-CON-031.5` | Unit | `TuningPreferencesDataStore.kt` | `REQ-CON-031.5` | Specified |
| `TST-CON-031.6` | Unit | `RadarTrafficSimulator.kt` | `REQ-CON-031.6` | Specified |
| `TST-CON-031.7` | Localization | `TranslationParityTest.kt` | `REQ-CON-031.7` | Specified |
| `TST-CON-031.8` | Regression | Tier 1 Targeted Test Suite | `REQ-PRO-001` | Specified |
