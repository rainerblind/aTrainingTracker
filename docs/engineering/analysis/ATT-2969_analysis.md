# Stage 1 Analysis: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3039](https://atrainingtracker.atlassian.net/browse/ATT-3039) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Problem Statement

Realistic desk testing and Joint Review verification of multi-sensor live tracking, Live Segments (ATT-2582), Live Climbs (ATT-2565), turn-by-turn navigation (ATT-1450), and sensor power distribution charts currently suffer from severe tool fragmentation:
1. `tools/fake_gps.py` simulates GPS movement over ADB along a GPX track via terminal keybindings, but does not simulate BLE sensors.
2. `tools/ble_power_simulator.py` broadcasts static or oscillating Cycling Power (Watts) and Cadence (RPM) over BlueZ BLE GATT, but has no spatial awareness or synchronization with GPS.
3. `tools/ble_hr_simulator.py` broadcasts static or oscillating Heart Rate (BPM) over BlueZ BLE GATT in an isolated terminal session.

Testing complex tracking scenarios requiring realistic correlations (e.g. an athlete increasing cadence and heart rate during a steep climb segment, then descending with 0W power and accelerating speed) currently requires launching and managing three separate terminal scripts. Furthermore, testers cannot inspect real-time values in a graphical dashboard, scrub to specific timestamps, or jump directly to route waypoints/climbs without restarting simulations.

`ATT-2969` provides a unified desktop workout replay tool (`tools/replay_workout.py`) featuring:
- A modern PyQt6 desktop GUI dashboard with transport controls, timeline scrubber, high-glanceability telemetry cards, and hardware status indicators.
- A headless CLI mode (`--headless`) for continuous integration or headless test runners.
- A robust Garmin/Strava `.tcx` XML parser extracting timestamped coordinates, altitude, distance, heart rate, cadence, watts, and speed.
- A synchronized multi-stream dispatch engine broadcasting BLE Cycling Power (`0x1818`), BLE Heart Rate (`0x180D`), and ADB mock GPS locations (`gps`, `network`, `fused`) in strict lockstep.

---

## 2. Chesterton's Fence Archaeology & Existing Infrastructure

A comprehensive audit of the `tools/` directory reveals established, battle-tested patterns that must be respected and harmonized:

1. **`tools/fake_gps.py`**:
   - `AdbController`: Registers Android test location providers (`gps`, `network`, `fused`) via `cmd location providers add-test-provider` and injects fixes via `cmd location providers set-test-provider-location` with microsecond-level accuracy.
   - Clean teardown: Restores hardware GNSS reception via `remove-test-provider` on exit using `atexit` and `signal` handlers.
   - Geodesic math: `haversine_distance`, `calculate_bearing`, `compute_destination_point`.
2. **`tools/ble_power_simulator.py` & `tools/ble_hr_simulator.py`**:
   - BlueZ D-Bus GATT architecture using `dbus-python` and `gi.repository.GLib`:
     - `Advertisement` registered on `org.bluez.LEAdvertisingManager1`.
     - `Application` registered on `org.bluez.GattManager1`.
     - Cycling Power Service (`0x1818`), Measurement (`0x2A63`), Feature (`0x2A65`), Location (`0x2A5D`).
     - Heart Rate Service (`0x180D`), Measurement (`0x2A37`), Body Location (`0x2A38`).
   - Characteristic notification updates triggered via `PropertiesChanged` signal on `org.bluez.GattCharacteristic1`.
3. **Environment & System Dependencies**:
   - System Python package `python3-pyqt6` (`PyQt6`) is available in `/usr/lib/python3/dist-packages/PyQt6`.
   - `dbus` and `gi.repository` are available in system Python.
   - Android target devices are accessible via ADB.

---

## 3. Technical Architecture & Component Design

The unified replay tool will be structured into modular, decoupled layers:

```
+-----------------------------------------------------------------------+
|                             USER INTERFACES                           |
|   +------------------------------------+   +-----------------------+  |
|   |         PyQt6 Desktop GUI          |   |     Headless CLI      |  |
|   |  - Transport Controls (Play/Pause) |   |  - Non-blocking keys  |  |
|   |  - Interactive Timeline Scrubber   |   |  - ANSI status bar    |  |
|   |  - Digital Telemetry Gauges        |   +-----------------------+  |
|   |  - Device & BLE Connection Status  |                              |
|   +-----------------+------------------+                              |
+---------------------|-------------------------------------------------+
                      | Qt Signals / CLI Calls
+---------------------v-------------------------------------------------+
|                       SYNCHRONIZED REPLAY ENGINE                      |
|  - Manages playback state: PLAYING, PAUSED, STOPPED                   |
|  - Speed multipliers: 1x, 2x, 5x, 10x                                 |
|  - Temporal interpolation & timeline scrubbing                        |
|  - Worker Thread decoupling dispatch from GUI event loop              |
+---------------------+-------------------------------------------------+
                      | Trackpoint Stream
       +--------------+--------------+
       |                             |
+------v-------------------+   +-----v----------------------------------+
|    BLE GATT BROADCASTER   |   |           ADB GPS INJECTOR             |
| - Cycling Power (0x1818) |   | - Multi-provider (gps, network, fused) |
| - Heart Rate (0x180D)    |   | - Bearing & altitude calculation       |
| - Graceful headless/mock |   | - Graceful disconnected device handling|
+--------------------------+   +----------------------------------------+
```

### 3.1 TCX Parser (`TcxParser`)
- Parses XML root `<TrainingCenterDatabase>`.
- Searches across all `<Trackpoint>` nodes.
- Extracts:
  - `Time` -> ISO 8601 string converted to Unix epoch seconds.
  - `Position` -> `LatitudeDegrees` (float), `LongitudeDegrees` (float).
  - `AltitudeMeters` -> float.
  - `DistanceMeters` -> float.
  - `HeartRateBpm/Value` -> int.
  - `Cadence` -> int.
  - `Extensions/TPX/Watts` -> int (or Garmin `ns3:TPX/ns3:Watts`).
  - `Extensions/TPX/Speed` -> float (m/s).
- Computes cumulative distance and calculates forward bearing between successive coordinates.

### 3.2 Unified BLE GATT Peripheral (`BleReplayServer`)
- Supports running unified GATT Application hosting BOTH Cycling Power Service (`0x1818`) and Heart Rate Service (`0x180D`).
- Exposes thread-safe update methods:
  - `update_telemetry(watts: int, cadence: int, hr_bpm: int)`
- Safely handles platforms where Bluetooth adapter is disabled or absent via fallback flags (`--no-ble`).

### 3.3 ADB Mock GPS Injector (`AdbGpsInjector`)
- Adapts `AdbController` to inject latitude, longitude, altitude, bearing, and speed.
- Safely handles disconnected USB devices via fallback flags (`--no-gps`).

### 3.4 PyQt6 Desktop GUI (`ReplayGuiWindow`)
- Styled with modern dark theme (Material/Obsidian palette: `#121212` background, `#1E1E1E` card surfaces, `#BB86FC` / `#03DAC6` accent colors).
- High-glanceability layout:
  - Header: Loaded TCX filename, total points, total distance, total duration.
  - Transport toolbar: Play/Pause button, Stop button, Speed combo (`1x`, `2x`, `5x`, `10x`), Step `-10s`, `+10s`.
  - Timeline Scrubber: Interactive `QSlider` coupled with timestamp label `00:15:23 / 01:24:50`.
  - Telemetry Dashboard: 6 digital readout cards (Power `W`, Cadence `RPM`, Heart Rate `BPM`, Speed `km/h`, Distance `km`, Altitude `m`).
  - Status Indicators: LED badge for ADB Device status and BLE Advertising status.

---

## 4. Scope Bounding & Invariants

- **Zero Android App Changes**: Replay tool is an external testing tool in `tools/`; 0% impact on Android source code or production APK.
- **Dependency Isolation**: Uses standard Linux packages (`PyQt6`, `dbus`, `gi.repository`); no external pip packages required.
- **Fail-Safe Operation**: If BLE or ADB is unavailable, tool degrades gracefully to preview mode or partial simulation without throwing uncaught exceptions.
- **Headless Compatibility**: Can run on CI servers or headless test environments via `--headless` or `--cli`.

---

## 5. Next Steps

Advance to Gate 2 (`[Req & Test Spec]`):
1. Define formal requirement `REQ-TOOL-001` in `docs/requirements.md`.
2. Define test specification `TST-TOOL-001` in `docs/tests.md`.
3. Provide comprehensive acceptance criteria for TCX parsing, timeline scrubbing, BLE broadcast, ADB GPS injection, and PyQt6 GUI launching.
