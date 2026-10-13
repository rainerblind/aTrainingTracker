# ATT-3178 Walkthrough: Add Battery Level Controls for Heart Rate and Power Meter to TCX Replay Tool

## Overview
- **Issue**: [ATT-3178](https://atrainingtracker.atlassian.net/browse/ATT-3178)
- **Requirement**: `REQ-TOOL-003`
- **Test Specification**: `TST-TOOL-003`
- **Sprint**: `2026-41.9`
- **Status**: Verified

## Problem Addressed
During developer testing and sensor battery telemetry verification, there was no way to simulate battery drain or trigger low-battery warnings in the Android application without modifying app code or using physical sensors with drained batteries.
`tools/replay_workout.py` previously only emulated Cycling Power (0x1818) and Heart Rate (0x180D) GATT services, lacking the standard Bluetooth SIG Battery Service (0x180F) and interactive GUI controls.

## Architectural Changes & Solution

1. **GATT Battery Service & Characteristic Implementation (`tools/replay_workout.py`)**:
   - Defined standard UUIDs: `BATTERY_SERVICE_UUID` (`0000180f-0000-1000-8000-00805f9b34fb`) and `BATTERY_LEVEL_CHARACTERISTIC_UUID` (`00002a19-0000-1000-8000-00805f9b34fb`).
   - Implemented `build_battery_payload(level: int) -> List[int]` with strict `[0, 100]` percentage clamping into a 1-byte integer representation.
   - Implemented `BatteryLevelCharacteristic(DBusCharacteristic)` supporting both `'read'` and `'notify'` operations, emitting D-Bus `PropertiesChanged` on `GATT_CHRC_IFACE` upon level modification.
   - Implemented `BatteryService(DBusService)` hosting the characteristic.

2. **Dual-Sensor Simulation in `BleReplayServer`**:
   - Added independent battery state tracking: `hr_battery` and `power_battery` (defaulting to 100%).
   - Instantiated distinct battery services for each simulated peripheral: `service3` for Cycling Power and `service4` for Heart Rate.
   - Updated BlueZ LE advertisement packets:
     - `ATT-Pwr` advertises services `['1818', '180f']`.
     - `ATT-HRM` advertises services `['180d', '180f']`.
   - Exposed public thread-safe update methods `set_hr_battery(val)` and `set_power_battery(val)` to dynamically notify connected BLE centrals.

3. **PyQt6 Obsidian Dark Desktop GUI Enhancement**:
   - Expanded window geometry to 880x680 to accommodate battery controls comfortably.
   - Integrated a dedicated "SENSOR BATTERY SIMULATION" panel containing two columns:
     - Heart Rate (ATT-HRM) and Cycling Power (ATT-Pwr).
   - Added interactive `QSlider` controls (0–100%) paired with live numeric percentage labels.
   - Implemented dynamic severity color coding:
     - Normal (> 20%): Accent Purple (`#BB86FC`)
     - Low Warning (≤ 20%): Amber (`#FFA726`)
     - Critical (≤ 5%): Red (`#CF6679`)
   - Added quick preset trigger buttons for instant test condition setup: `100%`, `50%`, `15% [Low]`, and `5% [Crit]`.

4. **CLI & Headless Mode Parity**:
   - Extracted `build_arg_parser()` exposing `--hr-battery` and `--power-battery` CLI arguments (integer range 0–100).
   - Displayed initial battery levels in the headless terminal simulation header.

5. **Scope Bounding (ATT-1250 / Rule 26)**:
   - Changes are strictly confined to `tools/replay_workout.py` and `tools/test_replay_workout.py`.
   - Zero modifications to Android production code in `app/src/main`.

## Verification & Test Results
- **Targeted Unit Tests (`tools/test_replay_workout.py`)**:
  - `test_build_battery_payload`: Validates 0%, 15%, 50%, and 100% byte encoding.
  - `test_build_battery_payload_clamping`: Confirms boundary clamping underflow (< 0) and overflow (> 100).
  - `test_battery_level_management`: Verifies `BleReplayServer` getters, setters, and boundary constraints.
  - `test_gui_battery_controls`: Verifies widget presence, slider updates, label formatting, and quick preset click events.
  - `test_battery_service_uuids`: Confirms BlueZ GATT standard UUIDs.
  - `test_battery_cli_arguments`: Verifies CLI parser defaults and flag overrides.
  - Result: **28 / 28 unit tests passed cleanly in 0.15s**.
- **Clean-Room Regression**:
  - Full project test suite (`./gradlew testDebugUnitTest`) passed cleanly with zero regressions.

## ASPICE Traceability
- **Requirement**: `REQ-TOOL-003` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-TOOL-003` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3178_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3178_analysis.md`
