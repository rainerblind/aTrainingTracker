# ATT-3179 Stage 1 Analysis: Add Simulated Device Kill and Reconnect Feature to TCX Replay Tool

## 1. Problem Domain & Background
The TCX workout replay tool (`tools/replay_workout.py`) enables synchronized developer desk testing of cycling telemetry by streaming BLE Cycling Power (`0x1818`), BLE Heart Rate (`0x180D`), BLE Battery Service (`0x180F`), and ADB mock GPS locations.

During sprint reviews and automated integration testing, developers need to test resilience against real-world sensor failure modes:
1. **Sensor Disconnection & Signal Dropout**: Athlete rides out of sensor range or sensor battery dies abruptly during an activity.
2. **Reconnection Recovery**: App background scanner re-acquiring sensor when it comes back into range, ensuring seamless data resumption without losing workout state.
3. **Telemetry Fallback**: Displaying fallback metrics (e.g. GPS speed when crank/wheel cadence drops).
4. **GPS Tunnel / Signal Loss**: App reacting to GPS coordinate starvation (e.g. entering a tunnel, urban canyons) without crashing or fabricating unrealistic distance jumps.

Currently, once `tools/replay_workout.py` starts, all simulated peripherals broadcast and inject coordinates continuously until the tool terminates. There is no runtime control to selectively kill or revive an individual device or stream without stopping the entire replay session.

## 2. Scope & Requirement Archaeology (Chesterton's Fence)
- **Target Component**: Developer testing suite (`tools/replay_workout.py`, `tools/test_replay_workout.py`).
- **Target Epic**: `ATT-2466` (*Developer Testing Tools*).
- **Related Requirements**:
  * Extends `REQ-TOOL-002` (Synchronized TCX Workout Replay Tool).
  * Extends `REQ-TOOL-003` (BLE Battery Service Simulation & PyQt6 Runtime Battery Level Controls).
  * Formalized as `REQ-TOOL-004` (Per-Device Kill, Disconnect & Revive Simulation).
- **Chesterton's Fence Evaluation**:
  * *Why was continuous streaming implemented initially?* Initial focus was end-to-end telemetry playback synchronization and Android 14 ADB syntax compliance. Disconnection lifecycle was out of scope.
  * *BlueZ D-Bus Advertising Invariant*: BlueZ `LEAdvertisingManager1` allows registering and unregistering individual advertisement objects independently (`RegisterAdvertisement` / `UnregisterAdvertisement`) without unregistering the root `GATTManager1` application.
  * *Production Code Isolation*: Strictly preserved. Zero changes to `app/src/main`.

## 3. Technical Architecture & Design

### 3.1 BleReplayServer Kill & Revive Lifecycle & BlueZ Connection Disconnect
- Add independent kill flags:
  * `is_hr_killed: bool = False`
  * `is_power_killed: bool = False`
- **Active Connection Termination Mechanism**:
  * When a peripheral is killed, stopping advertisements and GATT notifications prevents new data. To simulate immediate hard connection dropout (out-of-range or dead battery), the server queries `org.bluez.Device1` objects registered on `org.freedesktop.DBus.ObjectManager`.
  * For any device connected to the adapter (`Connected == True`), the server invokes `org.bluez.Device1.Disconnect()`.
  * If `Disconnect()` encounters permission or device state restrictions, unregistering the advertisement and ceasing GATT notifications ensures the central encounters a supervision timeout, cleanly triggering the Android Bluetooth stack's `onConnectionStateChange(..., STATE_DISCONNECTED)` callback.
- **Methods**:
  * `kill_hr()`:
    1. Set `is_hr_killed = True`.
    2. If `ad_manager` and `ad_hrm` are active: call `ad_manager.UnregisterAdvertisement(self.ad_hrm.get_path())`.
    3. Suppress all HR measurement notifications and HR battery notifications in `update_telemetry()` and `set_hr_battery()`.
    4. Trigger peripheral disconnect on connected clients.
  * `revive_hr()`:
    1. Set `is_hr_killed = False`.
    2. If `ad_manager` and `ad_hrm` are present: call `ad_manager.RegisterAdvertisement(self.ad_hrm.get_path(), {})`.
    3. Resume GATT notifications on subsequent `update_telemetry()` cycles using current workout values.
  * `kill_power()`:
    1. Set `is_power_killed = True`.
    2. If `ad_manager` and `ad_pwr` are active: call `ad_manager.UnregisterAdvertisement(self.ad_pwr.get_path())`.
    3. Suppress all CP measurement notifications and CP battery notifications.
    4. Trigger peripheral disconnect on connected clients.
  * `revive_power()`:
    1. Set `is_power_killed = False`.
    2. If `ad_manager` and `ad_pwr` are present: call `ad_manager.RegisterAdvertisement(self.ad_pwr.get_path(), {})`.
    3. Resume GATT notifications on subsequent `update_telemetry()` cycles using current workout values.
  * `toggle_hr()` and `toggle_power()` convenience toggles.

### 3.2 TCX Timeline Progression Invariant During Killed State (Time-Skip Model)
- **Time Behavior Definition**: The master TCX timeline **continues advancing globally** regardless of whether individual devices are active or killed.
- A killed device does **NOT** freeze or pause playback time. In the real world, when an athlete's heart rate strap disconnects at minute 10 and reconnects at minute 15, the athlete is at minute 15, not minute 10.
- Therefore, when `revive_hr()`, `revive_power()`, or `revive_gps()` is invoked, the device immediately resumes broadcasting telemetry corresponding to the **current active TCX timestamp** (time-skip / catch-up model).
- This ensures test consistency, realistic sensor recovery metrics, and deterministic integration test behavior.

### 3.3 AdbGpsInjector Kill & Revive Lifecycle
- Add `is_killed: bool = False`.
- Methods:
  * `kill()`: Set `is_killed = True`. In `inject_location()`, return immediately without enqueuing coordinates to the ADB worker thread.
  * `revive()`: Set `is_killed = False`. Subsequent `inject_location()` calls enqueue fixes immediately at the current TCX coordinates.
  * `toggle()` convenience toggle.
- Note: Test providers remain enabled in Android system to simulate genuine GPS satellite/signal loss rather than ADB uninstallation.

### 3.4 Automated Scripting CLI Flags & Ticker Integration
- Support automated timestamp triggers for scripted regression testing:
  * `--kill-hr-at <sec>`: Kill HR when `current_point.time_sec >= <sec>`.
  * `--revive-hr-at <sec>`: Revive HR when `current_point.time_sec >= <sec>`.
  * `--kill-power-at <sec>`: Kill Power when `current_point.time_sec >= <sec>`.
  * `--revive-power-at <sec>`: Revive Power when `current_point.time_sec >= <sec>`.
  * `--kill-gps-at <sec>`: Kill GPS when `current_point.time_sec >= <sec>`.
  * `--revive-gps-at <sec>`: Revive GPS when `current_point.time_sec >= <sec>`.
- **Integration**: Handled inside `ReplayEngine.on_update` callback (shared by both PyQt6 GUI and headless CLI ticker), ensuring automated scheduled state transitions execute deterministically regardless of execution mode.

### 3.5 PyQt6 Obsidian Dark GUI Enhancements (`ReplayGuiWindow`)
- Add a dedicated "DEVICE CONNECTIVITY & SIMULATION" section in the GUI:
  * ATT-HRM column: `[Kill HR]` / `[Revive HR]` button with dynamic styling:
    - Active: `#2A2A2A` background with green indicator badge (`● HRM Broadcasting`).
    - Killed: `#CF6679` background with red indicator badge (`■ HRM Killed`).
  * ATT-Pwr column: `[Kill Power]` / `[Revive Power]` button with dynamic styling:
    - Active: `#2A2A2A` background with green indicator badge (`● Power Broadcasting`).
    - Killed: `#CF6679` background with red indicator badge (`■ Power Killed`).
  * GPS Stream column: `[Kill GPS]` / `[Revive GPS]` button with dynamic styling:
    - Active: `#2A2A2A` background with green indicator badge (`● GPS Active`).
    - Killed: `#CF6679` background with red indicator badge (`■ GPS Suppressed`).

### 3.6 Headless CLI & Keybindings (`run_headless_cli`)
- Add single-key triggers:
  * `'h'`: Toggle HR kill / revive.
  * `'p'`: Toggle Power kill / revive.
  * `'g'`: Toggle GPS kill / revive.
- Display device kill state badges in the live terminal ticker bar:
  `[HR: ACTIVE|KILLED] [Pwr: ACTIVE|KILLED] [GPS: ACTIVE|KILLED]`.

## 4. Risks & Mitigations
| Risk | Severity | Mitigation |
| :--- | :--- | :--- |
| BlueZ throws DBus error on multiple unregister/register calls | Medium | Guard calls with `try/except` and track advertising registration state explicitly. |
| Re-registered advertisement rejected by BlueZ | Low | Reuse same advertisement path `/org/bluez/att/advertisement0` and `advertisement1`. |
| GPS suppression causes ADB worker queue accumulation | Low | `inject_location()` drops coordinates at the gate before reaching the queue when killed. |
| DBus Disconnect fails if adapter lacks permission | Low | Fallback to advertisement unregistration and GATT notification silence, causing Android client supervision timeout. |

## 5. ASPICE Traceability & Governance
- **Requirement to create**: `REQ-TOOL-004` (*TCX Replay Per-Device Kill, Disconnect & Revive Simulation*).
- **Test Specification to create**: `TST-TOOL-004`.
- **Target FixVersion**: `V4.9.39` (to be set upon completion per Rule 19).
