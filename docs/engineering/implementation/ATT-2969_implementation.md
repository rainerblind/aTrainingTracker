# Stage 4 Implementation: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3042](https://atrainingtracker.atlassian.net/browse/ATT-3042) (`[Implementation]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Code Deliverables

The implementation of `REQ-TOOL-002` has been successfully executed with zero production Android application changes.
All deliverables specified in `ATT-2969_plan.md` have been authored and verified:

1. **`tools/sample_workout.tcx`**:
   - Realistic Garmin Training Center Database format XML with 21 trackpoints along Munich Olympic Park.
   - Comprehensive telemetry: timestamp, coordinates, altitude, distance, heart rate, cadence, watts, speed.

2. **`tools/replay_workout.py`**:
   - `TcxParser`: Robust parsing of standard `.tcx` files with namespace handling, bearing calculation, and fallback defaults.
   - `ReplayEngine`: Transport state controller with timeline seeking, stepping ($\pm 10s$), speed scaling ($1\times, 2\times, 5\times, 10\times$), and callback dispatch.
   - `BleReplayServer`: BlueZ DBus GATT server for Cycling Power (`0x1818`, measurement `0x2A63`) and Heart Rate (`0x180D`, measurement `0x2A37`) with `--no-ble` fallback.
   - `AdbGpsInjector`: Android mock location injector across `gps`, `network`, and `fused` providers via ADB with `--no-gps` fallback and clean `remove-test-provider` exit handlers.
   - `ReplayGuiWindow`: PyQt6 modern dark/obsidian desktop GUI with transport controls, interactive timeline slider, digital telemetry cards, and hardware status indicators.
   - `run_headless_cli`: Non-blocking terminal interface for display-less environments.

3. **`tools/test_replay_workout.py`**:
   - 15 comprehensive unit tests covering geodesic calculations, duration formatting, TCX XML parsing, BLE payload generation, `ReplayEngine` lifecycle, and PyQt6 GUI contracts.
   - 100% pass rate in 0.15s.

---

## 2. Invariants Check

- [x] Zero Android application code touched (0% APK regression risk).
- [x] Standard system packages only (`PyQt6`, `dbus`, `gi.repository`).
- [x] Clean exit teardown registered via `atexit` and signal handlers (`SIGINT`, `SIGTERM`).
- [x] Unit test suite passed 100% (15/15 tests).
