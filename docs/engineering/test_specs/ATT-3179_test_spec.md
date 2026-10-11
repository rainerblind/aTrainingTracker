# ATT-3179 Stage 2: Requirement & Test Specification

## 1. Overview & ASPICE Traceability
- **Parent Ticket**: [ATT-3179](https://atrainingtracker.atlassian.net/browse/ATT-3179) (`[Verbesserung] Add simulated device kill and reconnect feature to TCX replay tool`)
- **Requirement ID**: `REQ-TOOL-004` (in `docs/requirements.md`)
- **Test Specification ID**: `TST-TOOL-004` (in `docs/tests.md`)
- **FixVersion**: `V4.9.39` (to be set upon completion per Rule 19)
- **Target Components**: `tools/replay_workout.py`, `tools/test_replay_workout.py`

## 2. Requirement Specification (`REQ-TOOL-004`)
The TCX workout replay tool (`tools/replay_workout.py`) SHALL provide independent runtime kill/disconnect and revive/reconnect controls for each simulated device (Heart Rate Monitor `ATT-HRM`, Cycling Power Meter `ATT-Pwr`, and Mock GPS location stream) to test sensor dropouts, signal loss, and automatic reconnection recovery (ATT-3179):

1. **Per-Device BLE Disconnect & Revive Lifecycle (`BleReplayServer`)**:
   - The server SHALL maintain independent boolean flags: `is_hr_killed` and `is_power_killed`.
   - `kill_hr()`: Unregisters `ad_hrm` advertisement, silences HR measurement and battery GATT notifications, and invokes `Disconnect()` on connected client device objects via BlueZ D-Bus ObjectManager.
   - `revive_hr()`: Re-registers `ad_hrm` advertisement and resumes HR measurement and battery GATT notifications at the active TCX timestamp.
   - `kill_power()`: Unregisters `ad_pwr` advertisement, silences Cycling Power measurement and battery GATT notifications, and invokes `Disconnect()` on connected client device objects.
   - `revive_power()`: Re-registers `ad_pwr` advertisement and resumes CP measurement and battery GATT notifications at the active TCX timestamp.

2. **GPS Signal Loss Simulation (`AdbGpsInjector`)**:
   - The injector SHALL maintain an independent flag: `is_killed`.
   - `kill()`: Suppresses coordinate enqueuing in `inject_location()` while keeping test providers active in the Android system, simulating satellite loss (tunnels/urban canyons).
   - `revive()`: Resumes coordinate injection immediately at the current TCX point.

3. **Master TCX Timeline Invariant (Time-Skip Model)**:
   - The master TCX playback timeline SHALL advance globally regardless of individual device kill states.
   - Reviving a device SHALL immediately synchronize telemetry to the active playback timestamp without rewinding or freezing time.

4. **PyQt6 GUI Connectivity Controls (`ReplayGuiWindow`)**:
   - The GUI dashboard SHALL provide interactive toggle buttons: `btn_kill_hr`, `btn_kill_pwr`, and `btn_kill_gps`.
   - Buttons SHALL dynamically update their text (`Kill` vs. `Revive`) and color styling (Dark `#2A2A2A` when active vs. Red `#CF6679` when killed).
   - Dynamic status badges SHALL clearly indicate broadcasting vs. killed state.

5. **Headless CLI & Scripting Support**:
   - The headless CLI runner (`run_headless_cli`) SHALL support non-blocking hotkeys: `'h'` (HR toggle), `'p'` (Power toggle), `'g'` (GPS toggle).
   - Live status indicators SHALL be integrated into the terminal ticker bar.
   - The CLI parser SHALL support automated timestamp flags: `--kill-hr-at`, `--revive-hr-at`, `--kill-power-at`, `--revive-power-at`, `--kill-gps-at`, `--revive-gps-at`.

6. **Preservation of System Invariants**:
   - Production application code isolation: Zero mock or test code in `app/src/main`.
   - Clean fallback: Seamless mock execution when BlueZ or PyQt6 is unavailable.
   - 100% unit test pass rate across `tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`.

## 3. Test Specification & Verification Matrix (`TST-TOOL-004`)

| Test ID | Test Target | Test Description & Invariant Checked | Expected Result |
| :--- | :--- | :--- | :--- |
| `TST-TOOL-004-1` | `BleReplayServer.kill_hr` | Call `kill_hr()`. Verify `is_hr_killed == True`. Verify GATT notifications suppressed in `update_telemetry()`. | Notifications cease; advertisement unregistered; state updated. |
| `TST-TOOL-004-2` | `BleReplayServer.revive_hr` | Call `revive_hr()`. Verify `is_hr_killed == False`. Verify subsequent `update_telemetry()` emits HR notifications. | Notifications resume at current TCX timestamp; advertisement re-registered. |
| `TST-TOOL-004-3` | `BleReplayServer.kill_power` | Call `kill_power()`. Verify `is_power_killed == True`. Verify CP notifications suppressed in `update_telemetry()`. | Notifications cease; advertisement unregistered; state updated. |
| `TST-TOOL-004-4` | `BleReplayServer.revive_power` | Call `revive_power()`. Verify `is_power_killed == False`. Verify subsequent `update_telemetry()` emits CP notifications. | Notifications resume at current TCX timestamp; advertisement re-registered. |
| `TST-TOOL-004-5` | `AdbGpsInjector.kill` | Call `kill()`. Call `inject_location()`. Verify queue size remains 0 (fixes dropped). | Fixes dropped at the gate; no ADB command invoked. |
| `TST-TOOL-004-6` | `AdbGpsInjector.revive` | Call `revive()`. Call `inject_location()`. Verify coordinate enqueued into worker queue. | Fix enqueued successfully; injection resumes. |
| `TST-TOOL-004-7` | `ReplayGuiWindow` Controls | Verify GUI toggle buttons exist and trigger kill/revive methods, updating text and styling. | Buttons toggle states; colors shift from normal to red alert; status labels update. |
| `TST-TOOL-004-8` | `build_arg_parser` CLI Flags | Parse automated kill and revive timestamp flags. | All 6 `--kill-*-at` and `--revive-*-at` arguments parsed to integer values cleanly. |
| `TST-TOOL-004-9` | Full Clean-Room Regression | Run `python3 -m unittest tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`. | 100% pass rate with zero regressions. |

## 4. Acceptance Criteria (Given-When-Then)
- **Scenario 1: HR Sensor Kill & Revive**:
  - *Given* `BleReplayServer` running with HR active,
  - *When* `kill_hr()` is invoked,
  - *Then* `is_hr_killed` becomes `True`, the HR advertisement is unregistered, and subsequent `update_telemetry()` calls do not dispatch HR notifications.
  - *When* `revive_hr()` is invoked,
  - *Then* `is_hr_killed` becomes `False`, the HR advertisement is re-registered, and subsequent `update_telemetry()` calls dispatch HR notifications.
- **Scenario 2: GPS Stream Kill & Revive**:
  - *Given* `AdbGpsInjector` initialized with test providers active,
  - *When* `kill()` is invoked,
  - *Then* `is_killed` becomes `True` and subsequent `inject_location()` calls discard coordinates without enqueuing them.
  - *When* `revive()` is invoked,
  - *Then* `is_killed` becomes `False` and subsequent coordinates are enqueued normally.
- **Scenario 3: PyQt6 GUI Interaction**:
  - *Given* `ReplayGuiWindow` initialized offscreen,
  - *When* `btn_kill_hr.click()` is invoked,
  - *Then* the button text switches to "Revive HR" and `ble.is_hr_killed` is `True`.
  - *When* `btn_kill_hr.click()` is invoked again,
  - *Then* the button text switches to "Kill HR" and `ble.is_hr_killed` is `False`.
- **Scenario 4: Scripted CLI Arguments**:
  - *Given* `build_arg_parser()`,
  - *When* parsed with `["--kill-hr-at", "15", "--revive-hr-at", "30"]`,
  - *Then* `args.kill_hr_at == 15` and `args.revive_hr_at == 30`.
