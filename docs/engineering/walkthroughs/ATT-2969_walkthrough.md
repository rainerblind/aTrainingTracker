# Stage 5 Walkthrough: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3043](https://atrainingtracker.atlassian.net/browse/ATT-3043) (`[Test]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements of `REQ-TOOL-002` and test specifications under `TST-TOOL-002` have been implemented and verified.
A unified workout replay tool (`tools/replay_workout.py`) has been created that reads recorded `.tcx` activities (such as `tools/sample_workout.tcx`) and synchronizes:
- **PyQt6 Desktop GUI**: Modern obsidian dark theme, transport controls (Play/Pause, Stop, Step $\pm 10s$, Speed $1\times, 2\times, 5\times, 10\times$), interactive timeline scrubber slider, digital telemetry cards (Power, Cadence, Heart Rate, Speed, Distance, Altitude), and status badges.
- **Headless CLI Mode**: Terminal dashboard with non-blocking keybindings for CI/automated environments (`--headless` / `--cli`).
- **BLE GATT Peripheral**: Simultaneous BlueZ D-Bus advertising of Cycling Power (`0x1818`, measurement `0x2A63`) and Heart Rate (`0x180D`, measurement `0x2A37`) with `--no-ble` fallback.
- **ADB Mock GPS Injector**: Android mock location injection across `gps`, `network`, and `fused` providers via ADB with `--no-gps` fallback and clean `remove-test-provider` teardown upon exit.

---

## 2. Test Execution & Regression Results

### 2.1 Tool Unit Test Suite
- Command: `python3 -m unittest tools/test_replay_workout.py`
- Result: **15 tests passed in 0.15s (100% pass rate)**.
- Verified:
  - Haversine geodesic math, forward bearing calculations, and duration formatting.
  - TCX XML parsing (`TcxParser`), extracting latitude, longitude, altitude, distance, HR, cadence, watts, speed.
  - Missing file and empty XML error handling.
  - BLE Cycling Power 8-byte payload generation (flags, power, cumulative revs, 1/1024s event time).
  - BLE Heart Rate 2-byte payload generation and value clamping ($0 \dots 255$).
  - `ReplayEngine` lifecycle states (`PLAYING`, `PAUSED`, `STOPPED`), seeking by ratio and time, and speed multiplier scaling.
  - `ReplayGuiWindow` contract verification (widget instantiation, slider binding, play/pause state transitions).

### 2.2 Clean-Room Full Test Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 2m 19s**
- Total Tests: **2,284 tests passed**, **0 failed**, **0 regressions**.

---

## 3. Architecture & Code Changes

1. **`tools/sample_workout.tcx`**:
   - Realistic Garmin TCX activity with 21 trackpoints along Munich Olympic Park.
2. **`tools/replay_workout.py`**:
   - `TcxParser`, `TcxPoint`, `ReplayEngine`, `BleReplayServer`, `AdbGpsInjector`, `ReplayGuiWindow`, `run_headless_cli`.
3. **`tools/test_replay_workout.py`**:
   - Comprehensive test suite for replay engine, parser, BLE payloads, and GUI contracts.
4. **Living Documentation & Governance**:
   - `docs/requirements.md`: `REQ-TOOL-002` updated to `Verified`.
   - `docs/tests.md`: `TST-TOOL-002` updated to `Verified`.

---

## 4. Invariants & Preservations Check

- [x] Zero Android application code touched (0% APK regression risk).
- [x] Standard system packages only (`PyQt6`, `dbus`, `gi.repository`).
- [x] Safe device teardown on exit (removes test providers via `atexit` and signal handlers).
- [x] Full-suite clean-room regression passing 100%.

---

## 5. Recommendation

**RECOMMEND PASS**: Advance `ATT-3043` to `Erledigt` via audit, merge `feature/ATT-2969` into `sprint/2026-41.6`, assign fixVersion `V4.9.39`, and transition `ATT-2969` to `Final Review (Human)`.
