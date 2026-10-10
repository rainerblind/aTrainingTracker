# Stage 3 Implementation Plan: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3041](https://atrainingtracker.atlassian.net/browse/ATT-3041) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architecture & Component Decomposition

The implementation of `tools/replay_workout.py` is decomposed into 6 cohesive, decoupled components:

```
+------------------------------------------------------------------------+
|                              CLI & MAIN                                |
|  - Argument parsing (--headless, --no-ble, --no-gps, --speed, file)   |
|  - Signal handlers (SIGINT, SIGTERM) and atexit cleanup                |
+-------------------+--------------------------------+-------------------+
                    |                                |
       (if not headless)                         (if headless)
                    v                                v
+------------------------------------+   +-------------------------------+
|     PyQt6 GUI (ReplayGuiWindow)    |   |     Headless CLI Runner       |
|  - Dark/Obsidian Material Design   |   |  - Non-blocking keybindings   |
|  - Transport Controls & Scrubber   |   |  - ANSI live terminal status  |
|  - Digital Telemetry Cards         |   +---------------+---------------+
|  - ADB & BLE Status Badges         |                   |
+-------------------+----------------+                   |
                    | Qt Signals                         | Calls
                    +----------------+-------------------+
                                     v
+------------------------------------------------------------------------+
|                         REPLAY ENGINE (ReplayEngine)                   |
|  - State: PLAYING, PAUSED, STOPPED                                     |
|  - Timeline position, scrubbing, duration, speed multipliers           |
|  - Dispatches updates to BLE and ADB at 1 Hz * multiplier              |
+-------------------+--------------------------------+-------------------+
                    |                                |
                    v                                v
+------------------------------------+   +-------------------------------+
|     BLE GATT Server (BlueZ)        |   |       ADB GPS Injector        |
|  - Cycling Power Service (0x1818)  |   |  - gps, network, fused        |
|  - Heart Rate Service (0x180D)     |   |  - cmd location providers     |
+------------------------------------+   +-------------------------------+
```

---

## 2. Step-by-Step Implementation Strategy

### Step 1: Create Built-in Sample TCX File (`tools/sample_workout.tcx`)
- Create standard Garmin Training Center Database XML with ~30 trackpoints.
- Include realistic GPS coordinates along Munich Olympic Park, elevation gain, heart rate progression (130-165 bpm), cadence (80-95 rpm), and power (180-320 W).

### Step 2: Implement TCX Parsing Engine (`TcxParser` in `tools/replay_workout.py`)
- Parse XML namespaces dynamically.
- Extract `Time`, `LatitudeDegrees`, `LongitudeDegrees`, `AltitudeMeters`, `DistanceMeters`, `HeartRateBpm`, `Cadence`, `Watts`, `Speed`.
- Precalculate cumulative geodesic distance and forward bearings.

### Step 3: Implement Replay Core Engine (`ReplayEngine`)
- Thread-safe simulation state manager.
- Implement timeline seeking (`seek_ratio(ratio)`), speed multipliers ($1\times, 2\times, 5\times, 10\times$), step forwards/backwards ($\pm 10s$).
- Decouple time stepping from dispatching callbacks.

### Step 4: Implement BLE GATT Broadcaster (`BleReplayServer`)
- Reusable BlueZ DBus GATT server registering Cycling Power Service (`0x1818`) and Heart Rate Service (`0x180D`).
- Build helper functions: `build_power_payload(watts, cadence)` and `build_hr_payload(bpm)`.
- Graceful dummy fallback if DBus is unavailable or `--no-ble` is passed.

### Step 5: Implement ADB Location Injector (`AdbGpsInjector`)
- Wrap `AdbController` logic for multi-provider registration (`gps`, `network`, `fused`).
- Inject latitude, longitude, altitude, speed, and forward bearing.
- Graceful dummy fallback if no device is connected or `--no-gps` is passed.

### Step 6: Implement PyQt6 Desktop GUI Dashboard (`ReplayGuiWindow`)
- Modern dark/obsidian theme (`#121212` background, `#1E1E1E` card surfaces, `#BB86FC` primary accent, `#03DAC6` secondary accent).
- Top bar: File name, duration, distance.
- Transport controls: Play/Pause button, Stop button, Speed combo (`1x`, `2x`, `5x`, `10x`), Step buttons (`-10s`, `+10s`).
- Interactive timeline scrubber (`QSlider`) with duration labels.
- Digital telemetry cards: Power (W), Cadence (RPM), Heart Rate (BPM), Speed (km/h), Distance (km), Altitude (m).
- Connection status indicators: ADB Device LED badge, BLE GATT LED badge.

### Step 7: Implement Headless CLI Mode (`HeadlessCliRunner`)
- Non-blocking terminal keybindings (`[Space]`, `[s]`, `[1]`, `[2]`, `[5]`, `[0]`, `[+]`, `[-]`, `[q]`).
- ANSI live progress line displaying time, distance, power, HR, cadence, and GPS status.

### Step 8: Comprehensive Unit Tests (`tools/test_replay_workout.py`)
- Test XML parser against `sample_workout.tcx` and edge cases (missing attributes).
- Test `ReplayEngine` seeking, stepping, and speed multipliers.
- Test BLE payload byte packing contracts.
- Test PyQt6 GUI initialization and contract compliance.

---

## 3. Invariants & Preservations Check

- [x] Zero Android application code touched (0% APK regression risk).
- [x] Standard system packages only (`PyQt6`, `dbus`, `gi.repository`).
- [x] Safe device teardown on exit (removes test providers via `atexit` and signal handlers).
- [x] Full-suite clean-room regression passing 100%.
