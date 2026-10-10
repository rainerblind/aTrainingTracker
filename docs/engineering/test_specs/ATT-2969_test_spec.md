# Stage 2 Requirement & Test Specification: ATT-2969

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3063](https://atrainingtracker.atlassian.net/browse/ATT-3063) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.7`  
**Branch**: `feature/ATT-2969`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Requirement Specification (REQ-TOOL-003)

### REQ-TOOL-003: Robust BlueZ GATT Peripheral Dual-Advertising, Android 14+ Mock GPS Injection & Host Pre-Flight Diagnostics

The project SHALL enhance the standalone external workout replay tool (`tools/replay_workout.py`) with full BlueZ D-Bus GATT server and LE advertising registration, Android 14+ compatible ADB mock GPS injection, and host pre-flight self-diagnostics conforming to Rule 30 (ATT-2969, amending `REQ-TOOL-002`):

1. **Full BlueZ D-Bus GATT Server & Dual LE Advertising**:
   - The tool SHALL register a complete BlueZ GATT application (`org.bluez.GattManager1.RegisterApplication`) exposing:
     - **Cycling Power Service** (`0x1818`) with Cycling Power Measurement (`0x2A63`, notify), Cycling Power Feature (`0x2A65`, read: 0x00000008 for Crank Revs supported), and Sensor Location (`0x2A5D`, read: 13 for Rear Hub / Pedal).
     - **Heart Rate Service** (`0x180D`) with Heart Rate Measurement (`0x2A37`, notify) and Body Sensor Location (`0x2A38`, read: 1 for Chest).
     - **Device Information Service** (`0x180A`) with Manufacturer Name (`0x2A29`, read: "ATT Replay Tool") and Model Number (`0x2A24`, read: "Sim-1.0").
   - The tool SHALL register BlueZ `LEAdvertisement1` advertising objects via `org.bluez.LEAdvertisingManager1.RegisterAdvertisement` with standard 16-bit service UUIDs (`['1818']` for `ATT-Pwr` and `['180d']` for `ATT-HRM`), enabling standard Android BLE scan filters to discover both simulated sensors reliably.
   - The tool SHALL run the GLib main loop in a dedicated background worker thread using `GLib.MainContext.push_thread_default()` to ensure isolated, thread-safe D-Bus method and property handling without deadlocking or colliding with PyQt6 GUI execution.
   - The tool SHALL dispatch real-time GATT notifications on `0x2A63` and `0x2A37` as playback advances, updating characteristic values thread-safely.

2. **Android 14+ Mock Location Provider Injection**:
   - The tool SHALL inject simulated GPS location fixes using strictly the syntax supported by Android 14+ `LocationShellCommand`:
     `cmd location providers set-test-provider-location <PROVIDER> --location <LAT,LON> --accuracy 2.5 --time <TIME>` across all three providers (`gps`, `network`, `fused`).
   - The tool SHALL OMIT unsupported options (`--altitude`, `--speed`, `--bearing`) that trigger `IllegalArgumentException` on Android 14+ devices.
   - The tool SHALL preserve clean test provider teardown (`remove-test-provider`) upon application exit via `atexit` and `signal` handlers.

3. **Rule 30 Host Pre-Flight Self-Diagnostics**:
   - The tool SHALL execute a comprehensive pre-flight self-diagnostic check at startup:
     - **Bluetooth Host Check**: Verify BlueZ system bus connectivity, check if Bluetooth adapter (`hci0`) exists and is powered on, and verify `org.bluez.LEAdvertisingManager1` is available on the adapter interface.
     - **ADB Device Check**: Verify `adb` binary is available, query connected devices via `adb devices`, check if an authorized device is present, and verify Android shell mock location permission (`appops get 2000 android:mock_location`).
   - The tool SHALL output clear, actionable human guidance if any prerequisite is missing (e.g. `sudo systemctl start bluetooth`, `adb devices`, or `appops set 2000 android:mock_location allow`) rather than failing silently or crashing.
   - In PyQt6 GUI mode, diagnostic states SHALL be reflected in dedicated status badges/cards.

4. **Multi-Threaded GUI Signaling Contract**:
   - All cross-thread telemetry and state updates directed from background engine/ADB/D-Bus worker threads to PyQt6 GUI components SHALL be transmitted exclusively via Qt Signals (`pyqtSignal`) connected to GUI slots.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Amends `REQ-TOOL-002` (*Synchronized TCX Workout Replay Tool with PyQt6 GUI Dashboard, BLE Cycling Power, Cadence, Heart Rate, and ADB Mock GPS Simulation*, Sprint 2026-41.6, ATT-2969).
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.6 (`ATT-2969`).
3. **Root Reason for Existing Formulation**: `REQ-TOOL-002` specified the high-level intent of synchronized replay but lacked detailed BlueZ GATT/advertising registration mechanics, was broken by Android 14+ argument rejections in `cmd location`, and lacked host pre-flight diagnostics mandated by Rule 30.
4. **Preservation of Core Invariants**: Standalone Python 3 execution outside `app/`, zero mock code compiled into production APKs, clean hardware GPS restoration upon exit, and 100% full-suite unit test pass rate strictly preserved.

---

## 2. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (BlueZ GATT Server & Advertising Registration)**:
  * *Given* a Linux host with Bluetooth LE support and `bluetoothd` active
  * *When* `tools/replay_workout.py` is started
  * *Then* the tool registers BlueZ GATT services for Cycling Power (`0x1818`) and Heart Rate (`0x180D`), and registers BlueZ LE advertisements (`1818` / `180d`) visible to nearby BLE scanners.
* **Criterion 2 (Android 14+ Location Injection)**:
  * *Given* a connected Google Pixel 10 running Android 14+ with authorized ADB
  * *When* trackpoints advance in `replay_workout.py`
  * *Then* the tool issues `cmd location providers set-test-provider-location <p> --location <lat,lon> --accuracy 2.5 --time <ms>` without `--altitude`, `--speed`, or `--bearing`, and the device receives valid simulated coordinates with returncode 0.
* **Criterion 3 (Rule 30 Pre-Flight Diagnostics)**:
  * *Given* an environment where Bluetooth is disabled or no ADB device is connected
  * *When* `tools/replay_workout.py` starts
  * *Then* the tool reports detailed pre-flight diagnostic messages identifying the exact missing prerequisite and actionable commands to resolve it.
* **Criterion 4 (Clean Teardown Invariant)**:
  * *Given* an active simulation session
  * *When* the user presses Stop or exits (`Ctrl+C`, GUI close)
  * *Then* all registered test location providers (`gps`, `network`, `fused`) and BLE advertisements are unregistered cleanly, restoring real hardware GPS.

---

## 3. Test Specification (TST-TOOL-003)

| Test ID | Test Category | Scope / Target | Execution Procedure | Expected Outcome |
| :--- | :--- | :--- | :--- | :--- |
| **TST-TOOL-003-1** | Unit | ADB Mock Location Syntax | Verify `AdbGpsInjector._build_inject_cmd()` strictly formats `cmd location providers set-test-provider-location <p> --location <lat>,<lon> --accuracy <acc> --time <ms>` without `--altitude`, `--speed`, or `--bearing`. | Matches Android 14+ `LocationShellCommand` regex; zero illegal options. |
| **TST-TOOL-003-2** | Unit | Rule 30 Pre-Flight Check | Execute pre-flight diagnostic routines under mocked BlueZ and ADB states (adapter offline, device disconnected, device unauthorized, mock location disabled). | Detects failure states and returns structured actionable diagnosis. |
| **TST-TOOL-003-3** | Contract | BlueZ GATT Architecture | Verify `BleReplayServer` constructs valid GATT Application, Cycling Power Service (`0x1818`), Heart Rate Service (`0x180D`), Device Info (`0x180A`), and LE Advertisements (`1818`, `180d`). | Object hierarchy and D-Bus properties conform to BlueZ 5.x GATT API. |
| **TST-TOOL-003-4** | Contract | Qt Signal Thread Decoupling | Verify `ReplayEngine` emits state and telemetry updates via `pyqtSignal` without direct GUI widget manipulations. | Signals fire with typed telemetry payloads without cross-thread exceptions. |
| **TST-TOOL-003-5** | Regression | Full Test Suite | Run `python3 -m unittest tools/test_replay_workout.py` and `./gradlew testDebugUnitTest`. | 100% pass rate with zero regressions. |

---

## 4. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method |
| :--- | :--- | :--- |
| `REQ-TOOL-003` (Item 1: BlueZ GATT & Adv) | `TST-TOOL-003-3` | Unit & Contract Test (`tools/test_replay_workout.py`) |
| `REQ-TOOL-003` (Item 2: Android 14+ Injection) | `TST-TOOL-003-1` | Unit Test (`tools/test_replay_workout.py`) |
| `REQ-TOOL-003` (Item 3: Pre-Flight Diagnostics) | `TST-TOOL-003-2` | Unit Test (`tools/test_replay_workout.py`) |
| `REQ-TOOL-003` (Item 4: Multi-Threaded Signals) | `TST-TOOL-003-4` | Unit & Contract Test (`tools/test_replay_workout.py`) |
| `REQ-TOOL-003` (Invariants) | `TST-TOOL-003-5` | Full Clean-Room Regression Suite |
