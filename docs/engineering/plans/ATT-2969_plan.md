# Stage 3 Implementation Plan: ATT-2969

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3064](https://atrainingtracker.atlassian.net/browse/ATT-3064) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.7`  
**Branch**: `feature/ATT-2969`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Overview & Component Decomposition (SWE.2)

The implementation enhances the standalone workout replay tool (`tools/replay_workout.py`) and its unit test suite (`tools/test_replay_workout.py`):

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        PyQt6 Desktop GUI Window                        │
│  (Transport Controls, Interactive Timeline Slider, Digital Gauges)     │
│                     Connected strictly via pyqtSignal                  │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ pyqtSignal
┌───────────────────────────────────┴────────────────────────────────────┐
│                  ReplayEngine (Multi-Threaded Clock)                   │
│   Advances trackpoints, interpolates metrics, seeks timeline cleanly   │
└──────────────┬──────────────────────────────────────────┬──────────────┘
               │                                          │
               ▼                                          ▼
┌───────────────────────────────┐          ┌─────────────────────────────┐
│  BleReplayServer (BlueZ D-Bus)│          │      AdbGpsInjector         │
│  • Dedicated GLib.MainContext │          │  • Strict Android 14+       │
│  • BlueZ GattManager1 (0x1818,│          │    cmd location providers   │
│    0x180D, 0x180A)            │          │    set-test-provider-loc    │
│  • LEAdvertisingManager1      │          │  • Clean teardown on exit   │
│    dual ads (1818 & 180d)     │          └─────────────────────────────┘
└──────────────┬────────────────┘                         ▲
               │                                          │
               └───────────────┬──────────────────────────┘
                               │
                ┌──────────────┴───────────────┐
                │ Host Pre-Flight Diagnostics  │
                │ • Bluetooth Daemon & Adapter │
                │ • ADB device & appops mock   │
                └──────────────────────────────┘
```

### Architectural Boundaries & Concurrency Model
1. **Host Pre-Flight Self-Diagnostics Module (`HostPreFlightDiagnostics`)**:
   - Executes synchronously prior to engine launch.
   - Evaluates:
     - BlueZ daemon active (`org.bluez` on SystemBus).
     - Bluetooth adapter present and powered on (`org.bluez.Adapter1` with `Powered = True`).
     - LE Advertising Manager present on adapter (`org.bluez.LEAdvertisingManager1`).
     - ADB binary presence and device connection (`adb devices`).
     - Android shell mock location permission (`cmd appops get 2000 android:mock_location`).
   - Returns a structured diagnostic report with actionable shell remediation commands.
2. **BlueZ D-Bus GATT Server & Dual-Advertisement Engine (`BleReplayServer`)**:
   - Implements full BlueZ GATT hierarchy: `Application`, `Service`, `Characteristic`, `Descriptor`, `Advertisement`.
   - Reuses robust, verified GATT implementations from `ble_power_simulator.py` and `ble_hr_simulator.py`:
     - Cycling Power Service (`0x1818`) with Measurement (`0x2A63`, notify), Feature (`0x2A65`, read), Sensor Location (`0x2A5D`, read).
     - Heart Rate Service (`0x180D`) with Measurement (`0x2A37`, notify), Body Sensor Location (`0x2A38`, read).
     - Device Information Service (`0x180A`).
     - Advertisements: Dual registration for `ATT-Pwr` (`1818`) and `ATT-HRM` (`180d`) or consolidated multi-service advertisement.
   - Thread isolation: The GLib main loop runs in a dedicated thread with `GLib.MainContext.push_thread_default()` and `loop.run()`.
3. **Android 14+ Mock GPS Injector (`AdbGpsInjector`)**:
   - Formats `cmd location providers set-test-provider-location <p> --location <lat>,<lon> --accuracy 2.5 --time <ms>` strictly adhering to Android 14 syntax.
   - Preserves `appops set 2000 android:mock_location allow` setup and provider registration (`add-test-provider` + `set-test-provider-enabled true`).
   - Ensures teardown via `remove-test-provider` in `atexit` and `signal` handlers.
4. **Qt Thread Decoupling**:
   - Cross-thread communication from `ReplayEngine` worker thread to GUI uses `pyqtSignal` exclusively.

---

## 2. UI Consistency Audit (Rule 23)

* **Closest Reference**: Existing `replay_workout.py` desktop GUI layout and `SensorGridScreen.kt` telemetry card design language.
* **Palette & Surfaces**:
  - Obsidian Dark Theme: Background `#121214`, Surface Card `#1E1E22`, Outline `#2C2C32`.
  - Brand & Accent Colors: Royal Blue `#2B5BE8` (active playback / track indicator), Emerald Green `#22C55E` (GPS active badge), Vivid Amber `#F59E0B` (BLE advertising badge), Crimson `#EF4444` (Diagnostics warning).
* **Typography**: Clean monospace digital displays for telemetry numbers (Watts, BPM, RPM, km/h) with clear metric unit labels.
* **Justification**: Enhances existing desktop GUI with a dedicated "Diagnostics & Hardware Status" card showing real-time BlueZ and ADB health.

---

## 3. Atomic Step Sequencing

### Step 1: Pre-Flight Self-Diagnostics Implementation (`tools/replay_workout.py`)
- Define `HostPreFlightDiagnostics` class:
  - `check_bluetooth() -> Tuple[bool, str, List[str]]`
  - `check_adb() -> Tuple[bool, str, List[str]]`
  - `run_all() -> PreFlightReport`
- Integrate into CLI startup and GUI status card.
- **Verification**: Unit tests in `tools/test_replay_workout.py`.

### Step 2: ADB Android 14+ Mock Location Injector Fix (`tools/replay_workout.py`)
- Modify `AdbGpsInjector.inject_location()`:
  - Remove `--altitude`, `--speed`, `--bearing` from `set-test-provider-location`.
  - Ensure `--location <lat:.6f>,<lon:.6f> --accuracy 2.5 --time <now_ms>` command line generation.
  - Expose helper `_build_inject_cmd()` for unit test verification.
- **Verification**: Run on attached Pixel 10 and verify exit code 0.

### Step 3: Complete BlueZ D-Bus GATT Server & Dual-Advertisement Engine (`tools/replay_workout.py`)
- Port `Advertisement`, `Application`, `Service`, `Characteristic`, `Descriptor` base classes from `ble_power_simulator.py` and `ble_hr_simulator.py`.
- Implement `CyclingPowerService` (`0x1818`), `HeartRateService` (`0x180D`), and `DeviceInfoService` (`0x180A`).
- Implement dual or multi-service advertising objects (`CyclingPowerAdvertisement` and `HeartRateAdvertisement`).
- In `BleReplayServer.start()`:
  - Register Application with `GattManager1`.
  - Register Advertisements with `LEAdvertisingManager1`.
  - Launch GLib main loop in background thread with `push_thread_default()`.
- In `BleReplayServer.update_telemetry()`:
  - Update characteristic values and emit D-Bus `PropertiesChanged` signal notifications.
- In `BleReplayServer.stop()`:
  - Unregister advertisements and quit GLib loop.

### Step 4: Unit Test Suite Expansion (`tools/test_replay_workout.py`)
- Add `TestAdbGpsInjector`:
  - Verify generated command strings conform to Android 14+ syntax without illegal options.
- Add `TestHostPreFlightDiagnostics`:
  - Mock various system states and verify diagnostic accuracy.
- Add `TestBluezGattStructures`:
  - Verify GATT service and advertisement class properties and UUID signatures.

### Step 5: Clean-Room Full Suite Regression Verification
- Run `python3 -m unittest tools/test_replay_workout.py`.
- Run `./gradlew testDebugUnitTest`.

---

## 4. Invariant Protection & Verification Commands

| Step | Invariant Checked | Verification Command |
| :--- | :--- | :--- |
| Step 1 | Rule 30 Pre-Flight Diagnostics | `python3 -m unittest tools/test_replay_workout.py -k TestHostPreFlightDiagnostics` |
| Step 2 | Android 14+ ADB location syntax | `python3 -m unittest tools/test_replay_workout.py -k TestAdbGpsInjector` |
| Step 3 | BlueZ GATT Server & Advertising | `python3 -m unittest tools/test_replay_workout.py -k TestBluezGatt` |
| Step 5 | Full clean-room regression | `python3 -m unittest tools/test_replay_workout.py && ./gradlew testDebugUnitTest` |
