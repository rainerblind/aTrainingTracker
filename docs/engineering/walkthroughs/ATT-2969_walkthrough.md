# Stage 5 Walkthrough: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3066](https://atrainingtracker.atlassian.net/browse/ATT-3066) (`[Test]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements of `REQ-TOOL-002` and test specifications under `TST-TOOL-002` have been fully constructed, verified, and audited.
A comprehensive workout replay developer tool (`tools/replay_workout.py`) executes outside the Android application that parses recorded Garmin/Strava `.tcx` XML activities (such as `tools/sample_workout.tcx`) and synchronizes:
- **PyQt6 Desktop GUI**: Modern obsidian dark theme, transport controls (Play/Pause, Stop, Step $\pm 10s$, Speed $1\times, 2\times, 5\times, 10\times$), interactive timeline scrubber slider, digital telemetry cards (Power, Cadence, Heart Rate, Speed, Distance, Altitude), pre-flight diagnostic badges, and file picker dialog.
- **Headless CLI Mode**: Terminal dashboard with non-blocking keybindings for CI/automated environments (`--headless` / `--cli`).
- **Full BlueZ 5.x D-Bus GATT Peripheral & Dual Advertising**: Complete D-Bus GATT server (`DBusApplication`, `DBusService`, `DBusCharacteristic`, `DBusAdvertisement`) publishing Cycling Power (`0x1818`, measurement `0x2A63`), Heart Rate (`0x180D`, measurement `0x2A37`), and Device Information (`0x180A`). Dual BlueZ advertising objects registered with standard 16-bit UUIDs (`1818` for `ATT-Pwr` and `180d` for `ATT-HRM`).
- **Isolated GLib Event Loop**: D-Bus GLib main context runs on a dedicated background worker thread using `GLib.MainContext.push_thread_default()`, completely decoupling D-Bus method dispatch from the PyQt6 Qt GUI main thread.
- **Android 14+ Mock GPS Injector**: Robust ADB test provider management conforming strictly to Android 14+ shell command requirements (`cmd location providers set-test-provider-location <p> --location <lat,lon> --accuracy 2.5 --time <ms>`), eliminating unsupported `--altitude`, `--speed`, and `--bearing` arguments that trigger `IllegalArgumentException` crashes on Android 14/15.
- **Rule 30 Host Pre-Flight Self-Diagnostics**: Self-diagnostic pre-flight module (`HostPreFlightDiagnostics`) automatically checks host Bluetooth adapter state (`hci0`), BlueZ daemon, D-Bus libraries, ADB connection, and mock location appops on attached Android devices before launching playback.

---

## 2. Test Execution & Regression Results

### 2.1 Tool Unit Test Suite
- Command: `python3 -m unittest tools/test_replay_workout.py`
- Result: **19 tests passed in 0.12s (100% pass rate)**.
- Verified:
  - Haversine geodesic math, forward bearing calculations, and duration formatting.
  - TCX XML parsing (`TcxParser`), extracting latitude, longitude, altitude, distance, HR, cadence, watts, speed.
  - Missing file and empty XML error handling.
  - BLE Cycling Power 8-byte payload generation (flags, power, cumulative revs, 1/1024s event time).
  - BLE Heart Rate 2-byte payload generation and value clamping ($0 \dots 255$).
  - `ReplayEngine` lifecycle states (`PLAYING`, `PAUSED`, `STOPPED`), seeking by ratio and time, and speed multiplier scaling.
  - `ReplayGuiWindow` contract verification (widget instantiation, slider binding, play/pause state transitions).
  - BlueZ GATT Application, Service, Characteristic, and Advertisement D-Bus hierarchy contracts.
  - Android 14+ ADB mock GPS location command formatting.
  - Rule 30 Host Pre-Flight Self-Diagnostics execution.

### 2.2 Host & Physical Hardware Verification (Pixel 10)
- Command: `python3 tools/replay_workout.py --headless --no-ble`
- Attached Device: Google Pixel 10 (`66020DLCR002FL`).
- Diagnostic Results:
  - BlueZ adapter `hci0` verified and active.
  - ADB device `66020DLCR002FL` connected and responsive.
  - Mock location permissions enabled.
  - Clean test provider installation, coordinate streaming, and automated provider removal upon exit verified.

### 2.3 Clean-Room Full Test Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 3m 21s**
- Total Tests: **100% pass rate**, **0 failures**, **0 errors**, **0 regressions**.

---

## 3. Living Documentation & Governance Synchronization

- `docs/requirements.md`: `REQ-TOOL-002` updated to `Verified`.
- `docs/tests.md`: `TST-TOOL-002` updated to `Verified`.
- Governance Audit: Verified via `tools/verify_requirement_governance.py --base-ref sprint/2026-41.7 --text-file docs/engineering/test_specs/ATT-2969_test_spec.md` with 100% Chesterton's Fence compliance.

---

## 4. Invariants & Preservations Check

- [x] Zero Android application code touched (0% APK regression risk).
- [x] Standard system packages only (`PyQt6`, `dbus`, `gi.repository`).
- [x] Safe device teardown on exit (removes test providers via `atexit` and signal handlers).
- [x] Full-suite clean-room regression passing 100%.

---

## 5. Recommendation

**RECOMMEND PASS**: Advance `ATT-3066` to `Erledigt` via audit, merge `feature/ATT-2969` into `sprint/2026-41.7`, and transition parent `ATT-2969` to `Final Review (Human)`.
