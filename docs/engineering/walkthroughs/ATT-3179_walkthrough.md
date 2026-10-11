# ATT-3179 Walkthrough: Add Simulated Device Kill and Reconnect Feature to TCX Replay Tool

## Overview
- **Issue**: [ATT-3179](https://atrainingtracker.atlassian.net/browse/ATT-3179)
- **Requirement**: `REQ-TOOL-004`
- **Test Specification**: `TST-TOOL-004`
- **Sprint**: `2026-41.9`
- **Status**: Verified

## Problem Addressed
During developer testing and automated validation of sensor reconnection recovery, developers had no means to simulate sudden device dropouts, BLE link disconnections, or GPS satellite signal loss without physically disconnecting hardware or modifying Android application code.
`tools/replay_workout.py` previously continuously broadcasted telemetry and injected GPS coordinates throughout playback, with no controls to simulate dropouts and subsequent reconnects.

## Architectural Changes & Solution

1. **Per-Device BLE Disconnection & Revive Lifecycle (`BleReplayServer`)**:
   - Added independent dropout state flags: `is_hr_killed: bool = False` and `is_power_killed: bool = False`.
   - Implemented `_disconnect_clients()` utilizing DBus `org.bluez.Device1.Disconnect()` method calls on connected client device objects to simulate true physical link drops.
   - Implemented `kill_hr()`, `revive_hr()`, `toggle_hr()`, `kill_power()`, `revive_power()`, and `toggle_power()` controllers.
   - Added notification guards to `update_telemetry()`, `set_hr_battery()`, and `set_power_battery()` to halt GATT notifications and payload updates when devices are simulated as killed.
   - Upon revival, advertisements (`ad_manager.RegisterAdvertisement`) are re-registered and live GATT notifications immediately catch up to the current active TCX timestamp.

2. **GPS Signal Loss Simulation (`AdbGpsInjector`)**:
   - Added independent dropout state flag: `is_killed: bool = False`.
   - Implemented `kill()`, `revive()`, and `toggle()`.
   - Added an early-return guard in `inject_location()` to immediately drop GPS fixes when killed, simulating satellite loss (tunnels/urban canyons) without deregistering test providers or killing background worker loops.

3. **Master TCX Timeline Invariant (Time-Skip Catchup Model)**:
   - The master TCX playback timeline advances globally regardless of individual device kill states.
   - When revived, devices immediately synchronize their telemetry and coordinates to the active playback timestamp.

4. **PyQt6 Obsidian Dark Desktop GUI Enhancement (`ReplayGuiWindow`)**:
   - Expanded window geometry to 880x750.
   - Integrated a dedicated "DEVICE CONNECTIVITY & DROPOUT SIMULATION" panel containing three columns:
     - Heart Rate Monitor (`ATT-HRM`), Cycling Power Meter (`ATT-Pwr`), and Mock GPS (`GPS`).
   - Added interactive action buttons:
     - `[Kill HR]` / `[Revive HR]`, `[Kill Power]` / `[Revive Power]`, `[Kill GPS]` / `[Revive GPS]`.
   - Implemented dynamic status badges:
     - Active (Normal): Teal (`#03DAC6`) / Purple (`#BB86FC`) badge (`● Active`).
     - Killed / Suppressed: Critical Red (`#CF6679`) badge (`■ Killed` / `■ GPS Suppressed`).

5. **Headless CLI & Automated Scripting Integration**:
   - Added interactive hotkeys to `run_headless_cli()`:
     - `'h'`: Toggle HRM dropout.
     - `'p'`: Toggle Power meter dropout.
     - `'g'`: Toggle GPS mock injection.
   - Added live connectivity status badges to the terminal ticker bar (`[HR:ON|OFF Pwr:ON|OFF GPS:ON|OFF]`).
   - Implemented `ScheduledTriggerManager` supporting automated CLI timestamp flags:
     - `--kill-hr-at <sec>`, `--revive-hr-at <sec>`
     - `--kill-power-at <sec>`, `--revive-power-at <sec>`
     - `--kill-gps-at <sec>`, `--revive-gps-at <sec>`

6. **Scope Bounding (ATT-1250 / Rule 26)**:
   - Changes are strictly confined to `tools/replay_workout.py` and `tools/test_replay_workout.py`.
   - Zero modifications to Android production code in `app/src/main`.

## Verification & Test Results
- **Targeted Unit Tests (`tools/test_replay_workout.py`)**:
  - `test_ble_kill_and_revive_hr`: Validates flag toggling, `kill_hr()`, `revive_hr()`, and telemetry notification suppression.
  - `test_ble_kill_and_revive_power`: Validates flag toggling, `kill_power()`, `revive_power()`, and telemetry notification suppression.
  - `test_gps_kill_and_revive`: Validates GPS dropouts, queue discard when killed, and resumption on revive.
  - `test_gui_connectivity_controls`: Validates PyQt6 widget presence, click toggling, button label updates, and status badge styling.
  - `test_scheduled_kill_cli_arguments`: Validates argument parser support for all 6 timestamp trigger flags.
  - `test_scheduled_triggers_execution`: Validates `ScheduledTriggerManager.evaluate()` event triggers across playback timeline.
  - Result: **34 / 34 unit tests passed cleanly in 0.18s**.
- **Clean-Room Regression**:
  - Full project test suite (`./gradlew testDebugUnitTest`) passed cleanly with zero regressions.

## ASPICE Traceability
- **Requirement**: `REQ-TOOL-004` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-TOOL-004` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3179_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3179_analysis.md`
