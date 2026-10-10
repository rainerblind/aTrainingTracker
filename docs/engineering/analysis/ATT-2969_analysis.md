# Stage 1 Analysis: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3062](https://atrainingtracker.atlassian.net/browse/ATT-3062) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: None (Unassigned per Rule 19)  
**Active Sprint**: `2026-41.7`  
**Branch**: `feature/ATT-2969`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.6 human review and on-device desk testing with the Google Pixel 10 test device, the human user reported:
> *"Unfortunately, this new tool does not work. The app does not find the HR device, does not find the Power device, and does not get the 'fake' GPS Signal."*

The replay tool (`tools/replay_workout.py`) was introduced in sprint 2026-41.6 to provide a synchronized desktop GUI environment for replaying TCX workouts with concurrent BLE Power, BLE Heart Rate, and ADB mock GPS broadcasting. While synthetic unit tests (`tools/test_replay_workout.py`) passed in isolation, real-world execution on the host machine failed completely across all three functional channels (BLE HR, BLE Power, ADB Mock GPS).

Per Rule 30 (*Developer Tool Host Pre-Flight Diagnostics*), developer tools and simulators interacting with host hardware (BlueZ D-Bus, Bluetooth LE Broadcaster/Peripheral emulation, ADB mock location providers) must implement host pre-flight self-diagnostics and robust hardware-level integration.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic deep-dive into `tools/replay_workout.py`, `tools/ble_power_simulator.py`, `tools/ble_hr_simulator.py`, `tools/fake_gps.py`, and Android 14+ location shell command specifications revealed three distinct root causes:

### 2.1 Complete Absence of BLE GATT Services and Advertising Registration
In `tools/replay_workout.py`, `BleReplayServer.start()` (lines 348–359) was written as an empty stub:
```python
def start(self):
    if not self.enabled:
        return
    try:
        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
        self.bus = dbus.SystemBus()
        self.is_advertising = True
    except Exception as e:
        self.is_advertising = False
```
- It instantiated `dbus.SystemBus()`, but **NEVER registered an `org.bluez.LEAdvertisement1`** object via `LEAdvertisingManager1.RegisterAdvertisement`!
- It **NEVER created or registered the GATT application** (`org.bluez.GattManager1.RegisterApplication`) containing the Cycling Power Service (`0x1818`) or Heart Rate Service (`0x180D`).
- Consequently, BlueZ never broadcast any advertising packets over the air, and never exposed GATT services for connection or characteristic discovery. The mobile app naturally could not discover either sensor during BLE scanning.

### 2.2 Invalid Command Options in ADB Mock Location Injection on Android 14+
In `tools/replay_workout.py` (lines 434–446):
```python
def inject_location(self, lat: float, lon: float, ele: float, speed_mps: float, bearing_deg: float):
    ...
    cmds.append(
        f"cmd location providers set-test-provider-location {p} --location {loc_str} "
        f"--altitude {ele:.1f} --speed {speed_mps:.2f} --bearing {bearing_deg:.1f} "
        f"--accuracy 2.5 --time {now_ms}"
    )
```
- Direct execution on the attached Google Pixel 10 running Android 14+ failed with fatal exit code 255:
  `java.lang.IllegalArgumentException: Unknown option: --altitude`
- According to `cmd location help` on Android 14+, `set-test-provider-location` **ONLY accepts** `--location <LAT,LON> [--accuracy <ACCURACY>] [--time <TIME>]`! Options `--altitude`, `--speed`, and `--bearing` are rejected as unknown arguments.
- Passing illegal flags caused `cmd location` to crash on every injection attempt, preventing the Android OS from receiving mock GPS fixes.

### 2.3 Single Shared Peripheral vs. Independent Sensor Dual Advertising
In standard BLE testing:
- Power sensors and Heart Rate monitors are distinct physical hardware peripherals with distinct MAC addresses / advertising packets (`ATT-Pwr` with UUID `1818`, and `ATT-HRM` with UUID `180D`).
- A single combined advertisement with multiple primary service UUIDs often gets filtered or handled unexpectedly by Android scan filters expecting standard single-sensor peripherals. Providing independent or robust dual-advertising with proper service registration is essential.

### 2.4 Lack of Rule 30 Host Pre-Flight Diagnostics
`replay_workout.py` silently caught exceptions or started without validating:
- Whether Bluetooth is powered on and `LEAdvertisingManager1` is available on the host D-Bus.
- Whether `adb` is in PATH and an Android device is authorized.
- Whether Android shell has `android:mock_location` allowed.

---

## 3. Concurrency, Thread Safety & D-Bus Architecture

To address multi-threaded concurrency between PyQt6 and BlueZ D-Bus:
1. **GLib Thread-Default Main Context Isolation**:
   - In the background BLE worker thread, initialize a dedicated `GLib.MainContext()` and call `context.push_thread_default()` before starting the GLib main loop. This guarantees that D-Bus signal/method handlers attached to the BLE GATT services and advertisement objects run within the isolated GLib loop thread, preventing D-Bus message delivery deadlocks or interference with PyQt6's event loop.
2. **Qt Cross-Thread Signaling Contract**:
   - Background worker threads (replay clock, ADB location injection, D-Bus telemetry notifications) MUST NEVER touch Qt GUI widgets directly.
   - All state transitions and telemetry updates (Watts, Cadence, HR, Speed, Distance, Altitude, Playback Time, Status badges) MUST be dispatched across threads strictly via Qt Signals (`pyqtSignal`) connected to GUI slots. This complies with Qt's strict thread affinity rules and eliminates cross-thread UI violation crashes.
3. **Thread-Safe Property Access & Value Updates**:
   - In-memory characteristic values updated by the ReplayEngine are guarded by Python thread synchronization primitives (e.g. `threading.Lock`) or scheduled via thread-safe GLib idle callbacks (`GLib.idle_add`) when invoking D-Bus `PropertiesChanged` signal emissions.

---

## 4. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Full BlueZ D-Bus GATT Peripheral implementation in `tools/replay_workout.py` mirroring `tools/ble_power_simulator.py` and `tools/ble_hr_simulator.py`, supporting:
     - Cycling Power Service (`0x1818`) with Measurement (`0x2A63`), Feature (`0x2A65`), and Sensor Location (`0x2A5D`).
     - Heart Rate Service (`0x180D`) with Measurement (`0x2A37`), and Body Sensor Location (`0x2A38`).
     - Device Information Service (`0x180A`).
     - Real BlueZ `LEAdvertisement1` advertising packets broadcast over the host Bluetooth controller (`hci0`).
     - Thread-safe GLib main loop integration running in a background thread using `GLib.MainContext.push_thread_default()`.
  2. Fixed ADB mock location injector:
     - Remove unsupported options (`--altitude`, `--speed`, `--bearing`) from `cmd location providers set-test-provider-location`.
     - Inject via `cmd location providers set-test-provider-location <PROVIDER> --location <LAT,LON> --accuracy 2.5 --time <TIME>` across `gps`, `network`, and `fused`.
  3. Pre-Flight Diagnostics (Rule 30):
     - Check BlueZ daemon status, adapter power state, and LE advertising capability.
     - Check ADB connectivity, device authorization, and mock location appops permission.
     - Report clear, actionable diagnostics in both CLI terminal and PyQt6 GUI diagnostics panel.
  4. Unit test suite update in `tools/test_replay_workout.py`:
     - Unit test verifying exact Android 14+ `cmd location providers set-test-provider-location` command formatting.
     - Unit tests verifying pre-flight check logic, GATT structure, and thread signaling contracts.

* **Out-of-Scope Goals**:
  - Rewriting existing standalone simulators `tools/ble_power_simulator.py` and `tools/fake_gps.py`.
  - Android production app code modifications (the issue is strictly within the host testing tool).

---

## 5. Invariant Analysis & Impact Assessment

| System Invariant | Impact & Preservation Strategy |
| :--- | :--- |
| **Android ScanFilter Matching** | Must advertise standard 16-bit UUIDs (`1818`, `180d`) so `aTrainingTracker` sensor scanner discovers them immediately. |
| **GATT Notification Cadence** | Replay engine must dispatch GATT notifications on characteristic value changes as trackpoints advance. |
| **Android 14+ Mock Location Contract** | Must adhere strictly to Android 14 `LocationShellCommand` argument syntax (`--location`, `--accuracy`, `--time`). |
| **Desktop GUI Responsiveness** | D-Bus GLib main loop and ADB subprocess execution must run on background threads; all UI updates piped through `pyqtSignal`. |
| **Rule 30 Compliance** | Must diagnose missing host capabilities gracefully with actionable advice rather than failing silently. |

---

## 6. Conclusion & Next Steps

Stage 1 analysis clearly establishes the exact mechanisms that caused failure during the human review and outlines the architectural thread-safety and D-Bus concurrency boundaries. We can now proceed to Stage 2 (Requirement & Test Specification).
