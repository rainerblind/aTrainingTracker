# Stage 1 Analysis: ATT-3178 - [Verbesserung] Add battery level controls for heart rate and power meter to TCX replay tool

**Ticket**: [ATT-3178](https://atrainingtracker.atlassian.net/browse/ATT-3178)  
**Sub-task**: [ATT-3195](https://atrainingtracker.atlassian.net/browse/ATT-3195) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.9`  
**Branch**: `feature/ATT-3178`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

The TCX workout replay tool ([`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py)) was introduced in Sprint 2026-41.6 (`ATT-2969`, `REQ-TOOL-002`) to enable clock-synchronized desk testing of GPS navigation, BLE cycling power, and BLE heart rate streams via an interactive Obsidian Dark PyQt6 desktop GUI and headless CLI.

However, neither the simulated Cycling Power meter (`ATT-Pwr`) nor the simulated Heart Rate monitor (`ATT-HRM`) currently implements the standard Bluetooth SIG Battery Service (UUID `0x180F`) or the Battery Level Characteristic (UUID `0x2A19`). Furthermore, the PyQt6 desktop GUI lacks runtime controls to adjust sensor battery percentages dynamically.

As a consequence, testing battery-dependent application features—such as real-time low battery badges on sensor tiles, connection warning banners, and post-workout low sensor battery alerts (e.g., `ATT-2192`, `ATT-3167`)—cannot be verified at the desk using the replay tool. Developers and testers are currently forced to either manually manipulate SQLite database entries (`ActiveDevicesDbHelper`, `DevicesDatabaseManager`) or hunt for physical hardware sensors with depleted batteries.

---

## 2. Root Cause Analysis (Forensic Investigation & Gap Analysis)

### 2.1 Current Architectural State in `tools/replay_workout.py`
Forensic analysis of [`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py) reveals:
1. **Registered GATT Services**:
   In `BleReplayServer.start()` (lines 787–793):
   ```python
   self.app = DBusApplication(self.bus)
   self.cp_service = CyclingPowerService(self.bus, 0)
   self.hr_service = HeartRateService(self.bus, 1)
   self.app.add_service(self.cp_service)
   self.app.add_service(self.hr_service)
   self.app.add_service(DeviceInfoService(self.bus, 2))
   ```
   BlueZ GATT application exports only `CyclingPowerService` (`0x1818`), `HeartRateService` (`0x180D`), and `DeviceInfoService` (`0x180A`). The standard Bluetooth SIG Battery Service (`0x180F`) is entirely absent.
2. **Dual LE Advertisements**:
   In `BleReplayServer.start()` (lines 795–796):
   ```python
   self.ad_pwr = DBusAdvertisement(self.bus, 0, 'peripheral', ['1818'], 'ATT-Pwr')
   self.ad_hrm = DBusAdvertisement(self.bus, 1, 'peripheral', ['180d'], 'ATT-HRM')
   ```
   Neither advertisement advertises service UUID `180f`, preventing Bluetooth LE scanning engines from identifying that these peripherals support battery reporting.
3. **Reference Implementation in `tools/ble_hr_simulator.py`**:
   The legacy standalone script [`tools/ble_hr_simulator.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/ble_hr_simulator.py) already contains a reference BlueZ D-Bus implementation of `BatteryService` and `BatteryLevelCharacteristic` (lines 302–349):
   - Service UUID: `0000180f-0000-1000-8000-00805f9b34fb`
   - Characteristic UUID: `00002a19-0000-1000-8000-00805f9b34fb`
   - Properties: `['read', 'notify']`
   - Value payload: Single unsigned 8-bit byte (`dbus.Byte(level)`).
   - Notification dispatch: Emits D-Bus signal `PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])`.
4. **PyQt6 GUI Architecture (`ReplayGuiWindow`)**:
   `ReplayGuiWindow` currently organizes the interface into Header Bar, Telemetry Gauges Grid, Timeline Scrubber, Transport Controls Bar, and Status Footer. It has zero widgets for observing or altering battery levels.
5. **CLI Argument Parser**:
   `tools/replay_workout.py` supports `--no-ble`, `--no-gps`, `--device`, and `--speed`, but does not provide `--hr-battery` or `--power-battery` CLI arguments for controlling initial battery levels.

### 2.2 Client-Side Sensor Pairing & Battery Discovery Mechanics
Investigation of [`BTSearchForNewDevicesEngine.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/search_new/BTSearchForNewDevicesEngine.java) and [`MyBTLEDevice.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/MyBTLEDevice.java) demonstrates:
- During pairing discovery, `BTSearchForNewDevicesEngine` queries `gatt.getService(BluetoothConstants.UUID_SERVICE_BATTERY)` and reads `BluetoothConstants.UUID_CHARACTERISTIC_BATTERY_LEVEL` (line 124–125).
- Upon successful read, it stores the integer percentage via `characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)` (line 155).
- During active tracking, `MyBTLEDevice.onServicesDiscovered` discovers the Battery Service, reads the initial value into `setBatteryPercentage(batteryPercentage)`, and receives real-time updates via `onCharacteristicChanged` (lines 150–153, 273–280).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add standard Bluetooth SIG Battery Service (UUID `0x180F`) and Battery Level Characteristic (UUID `0x2A19`) with `['read', 'notify']` support to `tools/replay_workout.py`.
  2. Support dual simulated battery states for Heart Rate (`ATT-HRM`) and Cycling Power (`ATT-Pwr`) with dedicated D-Bus characteristics and service advertisement (`['180d', '180f']` and `['1818', '180f']`).
  3. Implement PyQt6 GUI battery control widgets in `ReplayGuiWindow`:
     - Sliders (0–100%) and percentage display labels for both HR and Power.
     - Quick-preset buttons (`100%`, `50%`, `15%` [Low], `5%` [Critical]) for instant state switching.
     - Real-time `PropertiesChanged` GATT notification dispatch upon slider movement or preset click.
  4. Implement CLI arguments `--hr-battery <0-100>` (default 100) and `--power-battery <0-100>` (default 100) for headless and script execution.
  5. Extend unit test suite in `tools/test_replay_workout.py` to achieve 100% test coverage over battery payload generation, clamping, D-Bus service contracts, and GUI widget wiring.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying Android production source code in `app/src/main`: `aTrainingTracker` already contains complete GATT client support for `0x180F` and `0x2A19`. Production code changes are strictly out-of-scope for this developer tooling ticket.
  2. Modifying TCX file schema: Battery percentage is an interactive simulation control; recorded TCX files do not store battery telemetry.
  3. Altering ADB GPS injection or playback timeline engine mechanics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-TOOL-002` (*Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation*), targeting [`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py).
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.6 under ticket `ATT-2969` (commit `9e11894d`).
* **Root Reason for Existing Formulation**: `REQ-TOOL-002` unified disparate CLI scripts into a synchronized desktop testing tool. Initial scope was strictly focused on GPS coordinate stream pacing and primary sensor metrics (Watts, Cadence, BPM). Battery service emulation was deferred to subsequent iterations.
* **Preservation of Core Invariants**:
  1. `REQ-TOOL-002` core invariants (zero production app code modifications, clean hardware GPS restoration, thread isolation via dedicated `GLib.MainContext.new()`, and backward compatibility with `--no-ble` and headless mode) MUST NOT be broken.
  2. BlueZ GATT peripheral advertising for existing services (`0x1818`, `0x180D`, `0x180A`) remains completely unchanged while augmenting service advertisements with `0x180F`.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 BLE GATT Battery Architecture
1. **Constants**:
   ```python
   BATTERY_SERVICE_UUID = '0000180f-0000-1000-8000-00805f9b34fb'
   BATTERY_LEVEL_CHARACTERISTIC_UUID = '00002a19-0000-1000-8000-00805f9b34fb'
   ```
2. **Payload Helper**:
   `build_battery_payload(level: int) -> List[int]`: Returns `[max(0, min(100, int(level)))]`.
3. **`BatteryLevelCharacteristic`**:
   Subclasses `DBusCharacteristic` with flags `['read', 'notify']`.
   Implements:
   - `ReadValue(options)`: returns `dbus.Array([dbus.Byte(self.level)], signature='y')`.
   - `StartNotify()` / `StopNotify()`: manages notification subscription state.
   - `update_level(level)`: updates internal level and dispatches `PropertiesChanged(GATT_CHRC_IFACE, {'Value': val}, [])`.
4. **`BatteryService`**:
   Subclasses `DBusService` with primary=True and UUID `0x180F`, hosting a `BatteryLevelCharacteristic`.
5. **`BleReplayServer` Integration**:
   - Accepts `hr_battery: int = 100` and `power_battery: int = 100`.
   - Instantiates `hr_battery_service` (path `service3`) and `cp_battery_service` (path `service4`).
   - Augments advertisements:
     - `ad_pwr`: `service_uuids = ['1818', '180f']`
     - `ad_hrm`: `service_uuids = ['180d', '180f']`
   - Exposes public thread-safe setters `set_hr_battery(level: int)` and `set_power_battery(level: int)`.

### 5.2 PyQt6 GUI Controls (`ReplayGuiWindow`)
- Add a dedicated styled card: `SENSOR BATTERY SIMULATION`.
- Grouped into two columns:
  - **HR Battery (`ATT-HRM`)**: Status label (`HR Battery: 100%`), `QSlider` (0–100%), and 4 quick preset buttons (`100%`, `50%`, `15% [Low]`, `5% [Crit]`).
  - **Power Battery (`ATT-Pwr`)**: Status label (`Power Battery: 100%`), `QSlider` (0–100%), and 4 quick preset buttons (`100%`, `50%`, `15% [Low]`, `5% [Crit]`).
- Visual feedback: Percentage label dynamically tints orange (`#FFA726`) at $\le 20\%$ (critical threshold) and red (`#CF6679`) at $\le 5\%$.
- Slider/preset triggers `self.ble.set_hr_battery(...)` / `self.ble.set_power_battery(...)` immediately.

### 5.3 CLI & Headless Support
- Add `--hr-battery <0-100>` (default 100) and `--power-battery <0-100>` (default 100) to `argparse`.
- Display active battery settings in headless dashboard.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero modifications to Android production application code (`app/`).
  2. Clean degradation: Tool continues functioning seamlessly in mock mode when BlueZ / D-Bus is unavailable or when `--no-ble` is passed.
  3. Strict bounds: Battery level values are clamped to $[0, 100]$ across all entry points (CLI, GUI slider, direct API).
  4. Thread safety: BlueZ D-Bus operations remain isolated in their dedicated `GLib.MainContext` worker thread without interfering with PyQt6 Qt event loop.
  5. 100% unit test pass rate across `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.

* **Risk Rating**: **LOW**
  - All modifications are strictly confined to the desktop developer tool (`tools/replay_workout.py`) and its unit tests (`tools/test_replay_workout.py`).
  - D-Bus Battery Service pattern has already been proven in `tools/ble_hr_simulator.py`.
  - Zero risk to production Android application binaries or runtime release builds.
