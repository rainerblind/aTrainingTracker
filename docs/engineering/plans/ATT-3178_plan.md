# Stage 3: Implementation Plan - ATT-3178: Add battery level controls for heart rate and power meter to TCX replay tool

**Ticket**: [ATT-3178](https://atrainingtracker.atlassian.net/browse/ATT-3178)  
**Sub-task**: [ATT-3197](https://atrainingtracker.atlassian.net/browse/ATT-3197) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-TOOL-003` (*BLE Battery Service Simulation & PyQt6 Runtime Battery Level Controls for TCX Replay Tool*)  
**Test Mapping**: `TST-TOOL-003` (*TCX Replay BLE Battery Service Simulation, GATT Notification & GUI Control Verification*)  
**Branch**: `feature/ATT-3178`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

The TCX workout replay tool ([`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py)) was introduced under `REQ-TOOL-002` to provide synchronized desk verification of GPS movement, BLE Cycling Power, and BLE Heart Rate. However, neither simulated peripheral currently exposes the standard Bluetooth SIG Battery Service (UUID `0x180F`) or Battery Level Characteristic (UUID `0x2A19`), and the PyQt6 GUI dashboard lacks runtime controls to inspect or modify battery percentages.

As identified in the Sprint 2026-40.16 retrospective (`ATT-2389`), verifying low battery alerts and indicators (e.g. `ATT-2192`, `ATT-3167`) at the desk requires either manual SQLite database manipulation or depleted physical sensor hardware. This implementation plan specifies the exact architectural steps to integrate standard BLE GATT Battery Service simulation, dual independent battery states for `ATT-HRM` and `ATT-Pwr`, PyQt6 runtime sliders with quick-preset buttons, real-time `PropertiesChanged` notification dispatching, CLI flags, and comprehensive unit tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TOOL-003` (*BLE Battery Service Simulation & PyQt6 Runtime Battery Level Controls for TCX Replay Tool*)
  - Item 1: Bluetooth SIG Battery Service (`0x180F`) and Battery Level Characteristic (`0x2A19`) with `['read', 'notify']` support.
  - Item 2: Dual independent battery states for Heart Rate (`ATT-HRM`) and Cycling Power (`ATT-Pwr`) with advertisement updates (`['180d', '180f']` and `['1818', '180f']`).
  - Item 3: PyQt6 desktop GUI battery controls in `ReplayGuiWindow` (sliders, percentage displays, severity color coding, and quick-preset buttons).
  - Item 4: CLI argument support (`--hr-battery`, `--power-battery`) and headless dashboard reporting.
  - Item 5: Preservation of system invariants and production code isolation.
* **Test Mapping**: `TST-TOOL-003` (*TCX Replay BLE Battery Service Simulation, GATT Notification & GUI Control Verification*)
  - `TST-TOOL-003.1`: Unit test for payload generation and $[0, 100]$ clamping (`test_build_battery_payload`).
  - `TST-TOOL-003.2`: Contract test for `BATTERY_SERVICE_UUID` and `BATTERY_LEVEL_CHARACTERISTIC_UUID`.
  - `TST-TOOL-003.3`: Unit test for `BleReplayServer` dual battery states and setters.
  - `TST-TOOL-003.4`: GUI unit test for `ReplayGuiWindow` battery sliders and preset interactions.
  - `TST-TOOL-003.5`: CLI unit test for `--hr-battery` and `--power-battery` arguments.
  - `TST-TOOL-003.6`: 9-language translation parity audit (`TranslationParityTest.kt`).
  - `TST-TOOL-003.7`: Full clean-room test suite regression execution (`testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Code Modifications**: All changes are strictly confined to `tools/replay_workout.py` and `tools/test_replay_workout.py`. Zero modifications to `app/src/main`.
2. **Backward Compatibility & Degradation**: If Bluetooth hardware, permissions, or PyQt6 are unavailable, or if `--no-ble` is supplied, the tool continues to operate seamlessly without errors.
3. **Thread Safety & Context Isolation**: BlueZ D-Bus operations remain isolated in the dedicated background thread with `GLib.MainContext.new()`, preventing deadlocks or collisions with the PyQt6 Qt event loop.
4. **Value Range Enforcement**: Battery levels are clamped strictly to $[0, 100]$ across all entry points (CLI arguments, direct API setters, and GUI sliders).
5. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Parent ticket completion remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Bluetooth LE GATT Battery Emulation (`tools/replay_workout.py`)
- Define constants:
  ```python
  BATTERY_SERVICE_UUID = '0000180f-0000-1000-8000-00805f9b34fb'
  BATTERY_LEVEL_CHARACTERISTIC_UUID = '00002a19-0000-1000-8000-00805f9b34fb'
  ```
- Implement `build_battery_payload(level: int) -> List[int]` helper function.
- Implement `BatteryLevelCharacteristic(DBusCharacteristic)`:
  - Supported flags: `['read', 'notify']`.
  - Holds `self.level = max(0, min(100, int(initial_level)))` and `self.notifying = False`.
  - Implements `ReadValue(options)` returning `dbus.Array([dbus.Byte(self.level)], signature='y')`.
  - Implements `StartNotify()` and `StopNotify()`.
  - Implements `update_level(level: int)`: updates internal state and dispatches `PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])`.
- Implement `BatteryService(DBusService)`:
  - Primary service with UUID `BATTERY_SERVICE_UUID`.
  - Hosts `BatteryLevelCharacteristic` at index 0.

### Component 2: `BleReplayServer` Dual Battery State Management (`tools/replay_workout.py`)
- Constructor accepts `hr_battery: int = 100` and `power_battery: int = 100`.
- In `start()`:
  - Instantiates `cp_battery_service = BatteryService(self.bus, 3, initial_battery=self.power_battery)` and `hr_battery_service = BatteryService(self.bus, 4, initial_battery=self.hr_battery)`.
  - Adds both services to `self.app`.
  - Updates advertisements to include `180f`:
    - `ad_pwr`: `service_uuids = ['1818', '180f']`
    - `ad_hrm`: `service_uuids = ['180d', '180f']`
- Exposes public setters:
  - `set_hr_battery(level: int)`
  - `set_power_battery(level: int)`

### Component 3: PyQt6 Desktop GUI Battery Simulation Panel (`ReplayGuiWindow`)
- Add a dedicated framed panel (`QFrame`) between Transport Controls and Status Footer with title `SENSOR BATTERY SIMULATION`.
- Dual-column layout:
  - **Heart Rate (`ATT-HRM`)**:
    * Current percentage display label (e.g. `HR Battery: 100%`).
    * Horizontal `QSlider` (0–100%).
    * Four quick-preset buttons: `[100%]`, `[50%]`, `[15% Low]`, `[5% Crit]`.
  - **Cycling Power (`ATT-Pwr`)**:
    * Current percentage display label (e.g. `Power Battery: 100%`).
    * Horizontal `QSlider` (0–100%).
    * Four quick-preset buttons: `[100%]`, `[50%]`, `[15% Low]`, `[5% Crit]`.
- Dynamic visual feedback:
  - Normal battery ($>20\%$): `#BB86FC` (accent purple) or `#FFFFFF`.
  - Low battery ($\le 20\%$): `#FFA726` (amber/warning).
  - Critical battery ($\le 5\%$): `#CF6679` (red/critical).
- Signal wiring: Moving a slider or clicking a preset button updates the label, tints severity color, and calls `ble.set_hr_battery(...)` / `ble.set_power_battery(...)` immediately.

### Component 4: CLI Argument Parser & Headless Display (`tools/replay_workout.py`)
- Add `--hr-battery` (type=int, default=100) and `--power-battery` (type=int, default=100) to `argparse`.
- Pass arguments to `BleReplayServer`.
- In headless CLI mode, log configured battery levels at startup.

### UI Consistency (Rule 23 — Desktop Tooling Guidelines)
* **Reference component**: Existing Obsidian Dark telemetry cards and transport controls in [`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py).
* **Reused styling patterns**: Frame background `#1A1A1A`, border `#2D2D2D`, border-radius `10px`, button styling `#2A2A2A` with `#444444` border and `#FFFFFF` text.
* **Theme tokens**: Monospace font for numerical percentages, Inter font for labels, accent colors `#BB86FC` (purple), `#03DAC6` (teal), `#FFA726` (amber warning), `#CF6679` (critical red).
* **New one-off styles & justification**: None. Perfectly harmonized with existing desktop GUI theme.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Battery Service & Characteristic in `tools/replay_workout.py`
* **File**: `tools/replay_workout.py`
* **Changes**:
  1. Add `BATTERY_SERVICE_UUID` and `BATTERY_LEVEL_CHARACTERISTIC_UUID` constants.
  2. Implement `build_battery_payload(level: int) -> List[int]` with $[0, 100]$ clamping.
  3. Implement `BatteryLevelCharacteristic` subclassing `DBusCharacteristic` with `['read', 'notify']` flags, `update_level(level)`, and D-Bus `PropertiesChanged` dispatching.
  4. Implement `BatteryService` subclassing `DBusService`.

### Step 2: Implement Dual Battery Management in `BleReplayServer`
* **File**: `tools/replay_workout.py`
* **Changes**:
  1. Update `BleReplayServer.__init__` to accept `hr_battery: int = 100` and `power_battery: int = 100`.
  2. In `start()`, instantiate `cp_battery_service` (service3) and `hr_battery_service` (service4) and register them with `self.app`.
  3. Update `self.ad_pwr` and `self.ad_hrm` service UUIDs to include `'180f'`.
  4. Implement `set_hr_battery(self, level: int)` and `set_power_battery(self, level: int)`.

### Step 3: Implement PyQt6 Desktop Battery Controls in `ReplayGuiWindow`
* **File**: `tools/replay_workout.py`
* **Changes**:
  1. Build `_create_battery_control_panel()` in `ReplayGuiWindow.setup_ui()`.
  2. Create sliders, labels, and preset buttons for both HR and Power.
  3. Implement event handlers: `on_hr_battery_changed(val)`, `on_power_battery_changed(val)`, `set_hr_preset(val)`, `set_power_preset(val)`.
  4. Wire severity color tinting based on $\le 20\%$ and $\le 5\%$ thresholds.

### Step 4: Add CLI Arguments and Headless Reporting
* **File**: `tools/replay_workout.py`
* **Changes**:
  1. Add `--hr-battery` and `--power-battery` options to `argparse`.
  2. Pass parsed values to `BleReplayServer`.
  3. Include battery percentages in headless CLI startup summary.

### Step 5: Extend Unit Tests in `tools/test_replay_workout.py`
* **File**: `tools/test_replay_workout.py`
* **Changes**:
  1. Add `test_build_battery_payload` to `TestBlePayloads`.
  2. Add `test_battery_service_uuids` to `TestBlePayloads`.
  3. Add `TestBleReplayServerBattery` test case covering initial levels, getters/setters, and clamping.
  4. Add `test_gui_battery_controls` to `TestPyQt6GuiContracts` covering widget presence, slider updates, and preset button clicks.
  5. Add `test_battery_cli_arguments` to test argument parsing.

### Step 6: Execute Targeted Unit Tests & Full Clean-Room Suite
* **Commands**:
  1. `python3 -m unittest tools/test_replay_workout.py`
  2. `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Direct execution of `python3 -m unittest tools/test_replay_workout.py` validating 100% test pass rate across all new and existing test cases.
  - Offscreen PyQt6 GUI contract tests validating slider and preset mechanics without requiring an active X11/Wayland display.
  - Clean-room Gradle test suite (`./gradlew testDebugUnitTest`) verifying zero Android regressions.
* **Rollback**:
  - The feature is isolated on branch `feature/ATT-3178`. In the event of an unforeseen issue, the branch can be cleanly reset or deleted without impacting `sprint/2026-41.9` or `develop`.
