# ATT-3179 Stage 3: Implementation Plan

## 1. Architectural Design & SWE.2 Component Decomposition
- **Parent Ticket**: [ATT-3179](https://atrainingtracker.atlassian.net/browse/ATT-3179)
- **Requirement**: `REQ-TOOL-004`
- **Test Specification**: `TST-TOOL-004`
- **Scope**: Confined strictly to `tools/replay_workout.py` and `tools/test_replay_workout.py`. Zero modifications to Android production code in `app/src/main`.

### 1.1 Architecture & State Machine

```
                      +-----------------------------+
                      |         ReplayEngine        |
                      | (Advances Global Timeline)  |
                      +--------------+--------------+
                                     |
                                     | on_update(pt)
                                     v
                 +-------------------+-------------------+
                 |                                       |
                 v                                       v
    +-------------------------+             +-------------------------+
    |     BleReplayServer     |             |      AdbGpsInjector     |
    +-------------------------+             +-------------------------+
    | is_hr_killed: bool      |             | is_killed: bool         |
    | is_power_killed: bool   |             |                         |
    |                         |             |                         |
    | kill_hr() / revive_hr() |             | kill() / revive()       |
    | kill_pwr() / revive_pwr()             | toggle()                |
    +------------+------------+             +------------+------------+
                 |                                       |
       [If killed, suppress                    [If killed, drop
      GATT notifications and                  coordinates at gate
      unregister advertisement]                without enqueuing]
```

### 1.2 Timeline Synchronization Invariant (Time-Skip Model)
- Playback timeline continues advancing globally during any device kill downtime.
- When a killed device is revived, its subsequent telemetry emissions immediately reflect the current active TCX trackpoint (`current_point.time_sec`, `watts`, `cadence`, `hr`, `lat`, `lon`), accurately emulating real-world sensor reconnection recovery.

---

## 2. Atomic Construction Steps

### Step 1: `BleReplayServer` Kill & Revive Lifecycle Implementation
- Add instance attributes to `BleReplayServer.__init__`:
  * `self.is_hr_killed: bool = False`
  * `self.is_power_killed: bool = False`
- Implement helper `_disconnect_clients()`:
  * Inspect `self.bus` for managed objects implementing `org.bluez.Device1` with `Connected == True`.
  * Attempt `dev.Disconnect()` to cleanly sever existing client socket sessions.
- Implement lifecycle methods:
  * `kill_hr()`: Set `is_hr_killed = True`. If `ad_manager` and `ad_hrm` are active, call `ad_manager.UnregisterAdvertisement(self.ad_hrm.get_path())`. Attempt `_disconnect_clients()`.
  * `revive_hr()`: Set `is_hr_killed = False`. If `ad_manager` and `ad_hrm` exist, call `ad_manager.RegisterAdvertisement(self.ad_hrm.get_path(), {})`.
  * `toggle_hr()`: Toggle state.
  * `kill_power()`: Set `is_power_killed = True`. If `ad_manager` and `ad_pwr` are active, call `ad_manager.UnregisterAdvertisement(self.ad_pwr.get_path())`. Attempt `_disconnect_clients()`.
  * `revive_power()`: Set `is_power_killed = False`. If `ad_manager` and `ad_pwr` exist, call `ad_manager.RegisterAdvertisement(self.ad_pwr.get_path(), {})`.
  * `toggle_power()`: Toggle state.
- Guard `update_telemetry()`:
  * Only dispatch CP measurement notifications if `not self.is_power_killed`.
  * Only dispatch HR measurement notifications if `not self.is_hr_killed`.
- Guard `set_hr_battery()` and `set_power_battery()`:
  * Only emit battery notifications if the respective peripheral is not killed.

### Step 2: `AdbGpsInjector` Kill & Revive Lifecycle Implementation
- Add instance attribute to `AdbGpsInjector.__init__`:
  * `self.is_killed: bool = False`
- Implement lifecycle methods:
  * `kill()`: Set `is_killed = True`.
  * `revive()`: Set `is_killed = False`.
  * `toggle()`: Toggle state.
- Guard `inject_location()`:
  * If `self.is_killed`: return immediately without enqueuing `(lat, lon)` into `self._queue`.

### Step 3: CLI Parser & Automated Scheduled Triggers
- Update `build_arg_parser()` with scheduled automation arguments:
  * `--kill-hr-at <sec>` (float, default None)
  * `--revive-hr-at <sec>` (float, default None)
  * `--kill-power-at <sec>` (float, default None)
  * `--revive-power-at <sec>` (float, default None)
  * `--kill-gps-at <sec>` (float, default None)
  * `--revive-gps-at <sec>` (float, default None)
- Implement `ScheduledTriggerManager`:
  * Evaluates current workout timestamp against scheduled targets during `on_update(pt)`.
  * Executes one-shot state transitions when `pt.time_sec >= target`.

### Step 4: PyQt6 Desktop GUI Connectivity Control Panel
- In `ReplayGuiWindow.setup_ui()`:
  * Add a dedicated "DEVICE CONNECTIVITY & SIMULATION" frame between the Battery Simulation panel and Status Footer.
  * Create 3 columns:
    1. **Heart Rate (ATT-HRM)**: `[Kill HR]` button + status label (`● HRM Broadcasting` vs `■ HRM Killed`).
    2. **Power Meter (ATT-Pwr)**: `[Kill Power]` button + status label (`● Power Broadcasting` vs `■ Power Killed`).
    3. **GPS Location**: `[Kill GPS]` button + status label (`● GPS Active` vs `■ GPS Suppressed`).
  * Wire button click handlers to `toggle_hr()`, `toggle_power()`, and `toggle_gps()`.
  * Apply Obsidian Dark button styling: Normal (`#2A2A2A`, white text) vs Killed Alert (`#CF6679`, black text).

### Step 5: Headless CLI Hotkeys & Live Ticker Bar
- In `run_headless_cli()`:
  * Add keybindings in key loop:
    - `'h'`: `ble.toggle_hr()`
    - `'p'`: `ble.toggle_power()`
    - `'g'`: `gps.toggle()`
  * Update terminal ticker format:
    `[HR: ON|OFF] [Pwr: ON|OFF] [GPS: ON|OFF]`.

### Step 6: Comprehensive Unit Test Suite
- Extend `tools/test_replay_workout.py`:
  * `test_ble_kill_and_revive_hr`: Tests state flags, advertisement unregistration/registration, and notification suppression.
  * `test_ble_kill_and_revive_power`: Tests CP state flags, advertisement unregistration/registration, and notification suppression.
  * `test_ble_timeline_catchup_on_revive`: Tests telemetry catchup to active TCX point upon revival.
  * `test_gps_kill_and_revive`: Tests queue drop when killed and resumption upon revive.
  * `test_gui_connectivity_controls`: Tests widget presence, click toggling, label text, and color styling.
  * `test_cli_scheduled_triggers`: Tests argument parsing and trigger evaluation.
  * Run `python3 -m unittest tools/test_replay_workout.py` (assert 100% pass rate).
  * Run `./gradlew testDebugUnitTest` (assert 100% clean-room regression pass rate).

---

## 3. Review Gate 3 Acceptance Criteria
1. Architecture follows SWE.2 decomposition with clean component separation.
2. Invariants explicitly preserved (zero production changes in `app/src/main`, time-skip model, BlueZ unregistration error handling).
3. All atomic implementation steps clearly sequenced with concrete method signatures.
