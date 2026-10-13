# Stage 2: Requirement & Test Specification - ATT-3178: Add battery level controls for heart rate and power meter to TCX replay tool

**Ticket**: [ATT-3178](https://atrainingtracker.atlassian.net/browse/ATT-3178)  
**Sub-task**: [ATT-3196](https://atrainingtracker.atlassian.net/browse/ATT-3196) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Requirement Mapping**: `REQ-TOOL-003` (*BLE Battery Service Simulation & PyQt6 Runtime Battery Level Controls for TCX Replay Tool*)  
**Test Spec ID**: `TST-TOOL-003`  
**Branch**: `feature/ATT-3178`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-TOOL-003)

### 1.1 Problem Statement & Rationale
The TCX workout replay tool ([`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py)) simulates GPS movement, BLE Cycling Power, and BLE Heart Rate for desk verification. However, neither the simulated Cycling Power meter (`ATT-Pwr`) nor the Heart Rate monitor (`ATT-HRM`) exposes the standard Bluetooth SIG Battery Service (UUID `0x180F`) or Battery Level Characteristic (UUID `0x2A19`). The PyQt6 desktop GUI also lacks controls to view or manipulate battery percentages during replay.

Testing battery-dependent features—such as real-time low battery badges on sensor tiles, connection warning banners, and post-workout low sensor battery alerts (e.g., `ATT-2192`, `ATT-3167`)—currently requires manual database injection or physical hardware with drained batteries. `REQ-TOOL-003` provides standard BLE GATT Battery Service simulation and dynamic runtime controls in `tools/replay_workout.py` to enable end-to-end desk testing.

### 1.2 Functional & Architectural Requirements
1. **Bluetooth SIG Battery Service (`0x180F`) & Characteristic (`0x2A19`)**:
   - The tool SHALL register standard Bluetooth SIG Battery Service (`0000180f-0000-1000-8000-00805f9b34fb`) instances on the BlueZ D-Bus GATT application.
   - Each Battery Service SHALL expose a Battery Level Characteristic (`00002a19-0000-1000-8000-00805f9b34fb`) supporting `['read', 'notify']`.
   - Value payload SHALL be a single unsigned 8-bit integer (`uint8`, 1 byte) clamped to $[0, 100]$.
   - Reading the characteristic (`ReadValue`) SHALL return the current battery percentage.
   - The characteristic SHALL support notifications (`StartNotify` / `StopNotify`). Whenever the battery level is modified, the characteristic SHALL emit `PropertiesChanged` to dispatch GATT notifications to connected Bluetooth LE clients.
2. **Dual Peripheral Battery Simulation**:
   - The tool SHALL maintain independent battery state variables for Heart Rate (`ATT-HRM`) and Cycling Power (`ATT-Pwr`).
   - Peripheral advertisement objects (`ad_hrm` and `ad_pwr`) SHALL include `'180f'` in their `ServiceUUIDs` lists (`['180d', '180f']` and `['1818', '180f']`).
   - `BleReplayServer` SHALL provide thread-safe methods `set_hr_battery(level: int)` and `set_power_battery(level: int)`.
3. **PyQt6 Runtime Battery Control Widgets (`ReplayGuiWindow`)**:
   - The GUI dashboard SHALL incorporate a dedicated "SENSOR BATTERY SIMULATION" panel.
   - The panel SHALL feature two distinct control columns: Heart Rate Battery (`ATT-HRM`) and Cycling Power Battery (`ATT-Pwr`).
   - Each column SHALL contain:
     * A status label displaying the current battery percentage (e.g. `HR Battery: 100%`).
     * A continuous slider (`QSlider`, range 0 to 100) allowing smooth adjustment.
     * Rapid preset buttons: `100%` (Full), `50%` (Nominal), `15%` (Low), `5%` (Critical).
   - Real-time notification dispatch: Modifying the slider or clicking a preset button SHALL immediately update the label, tint the percentage display based on severity ($\le 20\%$ warning amber `#FFA726`, $\le 5\%$ critical red `#CF6679`), and invoke the corresponding BLE server battery setter to emit GATT notifications in real time.
4. **Command-Line & Headless Support**:
   - The CLI parser SHALL support `--hr-battery <0-100>` (default 100) and `--power-battery <0-100>` (default 100).
   - In headless CLI mode, initial battery levels SHALL be displayed on startup.
5. **Preservation of System Invariants**:
   - Production application code isolation: Zero mock or test code in `app/src/main`.
   - Clean fallback: Seamless mock execution when BlueZ or PyQt6 is unavailable.
   - 100% unit test pass rate across `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (GATT Discovery & Read)**:
  * *Given* `tools/replay_workout.py` running with BLE enabled,
  * *When* `aTrainingTracker` pairs with or connects to `ATT-HRM` or `ATT-Pwr`,
  * *Then* the app discovers the Battery Service (UUID `0x180F`) and successfully reads the configured battery level (`0x2A19`).
* **Criterion 2 (Real-Time Notification Dispatch)**:
  * *Given* an active connected workout replay session,
  * *When* the developer adjusts the HR or Power battery slider in the GUI (e.g. from 80% to 15%),
  * *Then* `PropertiesChanged` is emitted on D-Bus, transmitting a GATT notification with value 15% to connected Android clients.
* **Criterion 3 (CLI Argument Support)**:
  * *Given* the CLI launched with `--hr-battery 15 --power-battery 5`,
  * *When* the tool starts up,
  * *Then* `BleReplayServer` initializes with HR battery at 15% and Power battery at 5%.

### 1.4 System Invariants
1. Production application code isolation: Zero modifications to Android production application code (`app/`).
2. Clean degradation: Tool continues functioning seamlessly in mock mode when BlueZ / D-Bus is unavailable or when `--no-ble` is passed.
3. Strict bounds: Battery level values are clamped to $[0, 100]$ across all entry points (CLI, GUI slider, direct API).
4. Thread safety: BlueZ D-Bus operations remain isolated in their dedicated `GLib.MainContext` worker thread without interfering with PyQt6 Qt event loop.
5. 100% unit test pass rate across `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (TST-TOOL-003)

### Test Case 1: Battery Payload Generation & Clamping (`TST-TOOL-003.1`)
* **Scope**: Unit Test
* **Target File**: [`tools/test_replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/test_replay_workout.py)
* **Method**: `TestBlePayloads.test_build_battery_payload`
* **Preconditions**: Python environment with `tools.replay_workout` imported.
* **Action**:
  1. Call `build_battery_payload(100)` -> assert `[100]`.
  2. Call `build_battery_payload(0)` -> assert `[0]`.
  3. Call `build_battery_payload(15)` -> assert `[15]`.
  4. Call `build_battery_payload(-10)` -> assert `[0]` (lower boundary clamp).
  5. Call `build_battery_payload(150)` -> assert `[100]` (upper boundary clamp).
* **Expected Result**: Single-byte payload generated with correct byte value and strict $[0, 100]$ clamping.

### Test Case 2: Battery Service & Characteristic UUID Contracts (`TST-TOOL-003.2`)
* **Scope**: Unit Test
* **Target File**: [`tools/test_replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/test_replay_workout.py)
* **Method**: `TestBlePayloads.test_battery_service_uuids`
* **Preconditions**: `BATTERY_SERVICE_UUID` and `BATTERY_LEVEL_CHARACTERISTIC_UUID` defined in `tools.replay_workout`.
* **Action**:
  1. Assert `BATTERY_SERVICE_UUID == '0000180f-0000-1000-8000-00805f9b34fb'`.
  2. Assert `BATTERY_LEVEL_CHARACTERISTIC_UUID == '00002a19-0000-1000-8000-00805f9b34fb'`.
* **Expected Result**: Standard 128-bit Bluetooth SIG UUIDs validated.

### Test Case 3: BleReplayServer Dual Battery State Management (`TST-TOOL-003.3`)
* **Scope**: Unit Test
* **Target File**: [`tools/test_replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/test_replay_workout.py)
* **Method**: `TestBleReplayServer.test_battery_level_management`
* **Preconditions**: `BleReplayServer(enabled=False, hr_battery=80, power_battery=45)`.
* **Action**:
  1. Verify initial states: `ble.hr_battery == 80`, `ble.power_battery == 45`.
  2. Invoke `ble.set_hr_battery(15)`. Assert `ble.hr_battery == 15`.
  3. Invoke `ble.set_power_battery(5)`. Assert `ble.power_battery == 5`.
  4. Test boundary clamping: `ble.set_hr_battery(-5)` -> 0, `ble.set_power_battery(200)` -> 100.
* **Expected Result**: Internal battery states update and clamp properly.

### Test Case 4: PyQt6 Battery Controls & Interaction Contract (`TST-TOOL-003.4`)
* **Scope**: GUI Unit Test (PyQt6 Offscreen)
* **Target File**: [`tools/test_replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/test_replay_workout.py)
* **Method**: `TestPyQt6GuiContracts.test_gui_battery_controls`
* **Preconditions**: Offscreen `QApplication` active, `ReplayGuiWindow` instantiated with mock BLE server.
* **Action**:
  1. Verify widget presence: `window.slider_hr_battery`, `window.slider_pwr_battery`, `window.lbl_hr_battery_val`, `window.lbl_pwr_battery_val`.
  2. Verify preset buttons exist (`btn_hr_15`, `btn_pwr_15`, etc.).
  3. Simulate moving HR slider to 15:
     - Check `ble.hr_battery == 15`.
     - Check label text contains `"15%"`.
  4. Simulate clicking Power preset button 5%:
     - Check `ble.power_battery == 5`.
     - Check label text contains `"5%"`.
* **Expected Result**: GUI widgets bind bidirectionally with `BleReplayServer` and update displays.

### Test Case 5: CLI Argument Parsing for Battery Flags (`TST-TOOL-003.5`)
* **Scope**: Unit Test
* **Target File**: [`tools/test_replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/test_replay_workout.py)
* **Method**: `TestCliArguments.test_battery_arguments`
* **Preconditions**: Argument parser configured in `tools/replay_workout.py`.
* **Action**:
  1. Parse args with `["--hr-battery", "25", "--power-battery", "10"]`.
  2. Assert `args.hr_battery == 25`.
  3. Assert `args.power_battery == 10`.
* **Expected Result**: Command-line arguments parsed accurately.

### Test Case 6: 9-Language Localization & Specifier Audit (`TST-TOOL-003.6`)
* **Scope**: Localization Parity Test
* **Target File**: [`app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt)
* **Goal**: Verify 100% translation parity across all 9 supported locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: Zero missing entries, zero format specifier mismatches (0 new Android string tokens added; existing parity preserved).

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-TOOL-003.7`)
* **Scope**: Full Clean-Room Regression Test
* **Commands**:
  1. `python3 -m unittest tools/test_replay_workout.py`
  2. `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across Python and Android Gradle unit test suites with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TOOL-003.1]` | Unit | `build_battery_payload` | `REQ-TOOL-003` (Item 1) | Specified |
| `[TST-TOOL-003.2]` | Contract | `BATTERY_SERVICE_UUID`, `BATTERY_LEVEL_CHARACTERISTIC_UUID` | `REQ-TOOL-003` (Item 1) | Specified |
| `[TST-TOOL-003.3]` | Unit | `BleReplayServer.set_hr_battery`, `set_power_battery` | `REQ-TOOL-003` (Item 2) | Specified |
| `[TST-TOOL-003.4]` | GUI Unit | `ReplayGuiWindow` battery sliders & presets | `REQ-TOOL-003` (Item 3) | Specified |
| `[TST-TOOL-003.5]` | CLI Unit | `argparse` `--hr-battery`, `--power-battery` | `REQ-TOOL-003` (Item 4) | Specified |
| `[TST-TOOL-003.6]` | Localization | `TranslationParityTest` | `REQ-TOOL-003` (Item 5), `REQ-UI-106` | Specified |
| `[TST-TOOL-003.7]` | Regression | `python3 -m unittest` & `./gradlew testDebugUnitTest` | `REQ-TOOL-003` (Item 5), `REQ-PRO-001` | Specified |
