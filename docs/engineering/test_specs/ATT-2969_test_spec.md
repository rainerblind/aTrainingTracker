# Stage 2 Test Specification: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3040](https://atrainingtracker.atlassian.net/browse/ATT-3040) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (`REQ-TOOL-002`)

### REQ-TOOL-002: Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation

The project SHALL provide a unified external workout replay tool (`tools/replay_workout.py`) executing outside the Android application that parses recorded Garmin/Strava `.tcx` XML activities and broadcasts simulated BLE Cycling Power (`0x1818`), BLE Heart Rate (`0x180D`), and ADB mock GPS locations (`gps`, `network`, `fused`) concurrently in clock synchronization (ATT-2969):

1. **TCX Parsing & Telemetry Extraction Engine**:
   - The tool SHALL parse standard `.tcx` XML documents (Garmin Training Center Database format) containing `<Trackpoint>` entries.
   - For each trackpoint, the parser SHALL extract:
     - `Time` (converted to UTC timestamp/elapsed seconds)
     - `Position` (`LatitudeDegrees`, `LongitudeDegrees`)
     - `AltitudeMeters`
     - `DistanceMeters`
     - `HeartRateBpm/Value` (integer beats per minute)
     - `Cadence` (integer revolutions per minute)
     - `Extensions` TPX `Watts` (integer instantaneous power) and `Speed` (float meters/second).
   - The engine SHALL compute cumulative distance, track duration, and continuous forward travel bearing ($\theta \in [0^\circ, 360^\circ)$).
   - In the absence of an external file, the tool SHALL support loading a built-in sample workout (`tools/sample_workout.tcx`).

2. **Synchronized Playback Engine & Interactive Scrubbing**:
   - The playback engine SHALL advance through the workout timeline with wall-clock pacing (1 Hz base rate).
   - Supported playback multipliers: $1\times, 2\times, 5\times, 10\times$.
   - Interactive scrubbing: The user SHALL be able to seek to any point in the timeline ($0.0 \dots 1.0$), immediately updating current position, altitude, speed, heart rate, cadence, and power in lockstep.
   - Transport states: `PLAYING`, `PAUSED`, `STOPPED`.

3. **Simultaneous BLE GATT Peripheral Broadcasting**:
   - The tool SHALL provide a BlueZ D-Bus GATT peripheral application advertising:
     - Cycling Power Service (`0x1818`): Cycling Power Measurement (`0x2A63`) with instantaneous watts, cumulative crank revolutions, and crank event time.
     - Heart Rate Service (`0x180D`): Heart Rate Measurement (`0x2A37`) with instantaneous BPM.
   - The tool SHALL provide `--no-ble` flag to bypass BLE GATT server initialization when Bluetooth hardware or permissions are absent.

4. **ADB Mock GPS Location Injection**:
   - The tool SHALL inject simulated GPS fixes via ADB across `gps`, `network`, and `fused` providers.
   - Fixes SHALL include latitude, longitude, altitude, speed, and forward bearing.
   - Clean lifecycle guard: The tool SHALL remove test providers upon exit (`remove-test-provider`) via `atexit` and `signal` handlers.
   - The tool SHALL provide `--no-gps` flag to bypass ADB location injection when no device is attached.

5. **Dual User Interface (PyQt6 Desktop GUI & Headless CLI)**:
   - **PyQt6 Desktop GUI**:
     - Modern dark/obsidian theme (`#121212` background, `#1E1E1E` card surfaces).
     - Transport controls: Play/Pause toggle, Stop, Speed selector combo, Step buttons ($-10s, +10s$).
     - Timeline scrubber: Interactive `QSlider` with elapsed/total duration label (`HH:MM:SS / HH:MM:SS`).
     - Digital telemetry cards: Power (W), Cadence (RPM), Heart Rate (BPM), Speed (km/h), Distance (km), Altitude (m).
     - Status indicators: ADB Device and BLE GATT connection state badges.
     - File picker: Dialog to load any `.tcx` file from disk.
   - **Headless Mode (`--headless` / `--cli`)**:
     - Terminal execution with non-blocking keybindings (`[Space]`, `[s]`, `[1]`, `[2]`, `[5]`, `[0]`, `[q]`) without initializing the PyQt6 GUI.

---

## 2. Test Specification (`TST-TOOL-002`)

### TST-TOOL-002: TCX Parsing, Synchronized Replay Engine, BLE Broadcasting, Mock GPS Injection, and PyQt6 GUI Verification

1. **TCX Parsing Unit Tests (`tools/test_replay_workout.py`)**:
   - Test XML parsing of valid Garmin/Strava `.tcx` content extracting trackpoints with timestamps, coordinates, altitude, distance, HR, cadence, and watts.
   - Test missing/optional field handling (graceful defaults for missing power, cadence, or HR).
   - Test empty or malformed XML handling (raises descriptive `ValueError`).

2. **Playback Engine & Interpolation Tests**:
   - Test transport state transitions (`PLAYING`, `PAUSED`, `STOPPED`).
   - Test timeline seeking: verifying that seeking to 50% correctly updates telemetry metrics.
   - Test speed multipliers ($1\times, 2\times, 5\times, 10\times$).
   - Test step forward/backward ($\pm 10s$).

3. **BLE Payload Construction Tests**:
   - Test `build_power_payload(watts, cadence)` generates valid 8-byte GATT payload conforming to Bluetooth Cycling Power profile (UUID `0x2A63`).
   - Test `build_hr_payload(bpm)` generates valid 2-byte GATT payload conforming to Bluetooth Heart Rate profile (UUID `0x2A37`).

4. **PyQt6 GUI Contract Tests**:
   - Verify GUI class initializes without errors when `PyQt6` is present (or in test harness).
   - Verify presence of transport controls, slider, and telemetry labels.

5. **Regression Verification**:
   - Execute Python unit test suite: `python3 -m unittest tools/test_replay_workout.py`.
   - Execute full Android unit test suite: `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Acceptance Criteria (Given-When-Then)

- **AC-1 (TCX Parsing & Telemetry Extraction)**:
  - *Given* a `.tcx` file with trackpoints containing GPS coordinates, altitude, heart rate, cadence, and power
  - *When* parsed by `TcxParser`
  - *Then* all trackpoints are extracted with valid numerical values, correct bearings, and cumulative distances.

- **AC-2 (Interactive Timeline Scrubbing & Pacing)**:
  - *Given* an active workout replay session
  - *When* seeking to any percentage of the workout or selecting 5x speed
  - *Then* the telemetry metrics update instantly to match the selected trackpoint and playback continues at the selected speed.

- **AC-3 (Synchronized Multi-Stream Broadcast)**:
  - *Given* an active playback session with BLE and ADB enabled
  - *When* playback advances
  - *Then* BLE Cycling Power, BLE Heart Rate, and mock GPS coordinates are emitted simultaneously matching the current trackpoint.

- **AC-4 (Headless CLI Support)**:
  - *Given* a terminal environment without a display
  - *When* launched with `--headless`
  - *Then* the tool runs in terminal mode without attempting to initialize PyQt6 or open a window.
