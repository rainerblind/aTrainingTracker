# Stage 3 Implementation Plan: ATT-2969

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3095](https://atrainingtracker.atlassian.net/browse/ATT-3095) (`[Impl-Plan]`)  
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
│  • Dedicated GLib.MainContext │          │  • Async queue.Queue worker │
│    (GLib.MainContext.new())   │          │  • Strict Android 14+       │
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
1. **Desktop Process Lifecycle & Root Initialization**:
   - In GUI mode, `QApplication(sys.argv)` is instantiated as the very first operation in `main()` before initializing `ReplayEngine`, `BleReplayServer`, or `AdbGpsInjector`.
   - Ensures Qt GUI integration with the windowing system and GDK/GTK theme integration completes cleanly before any background threads or D-Bus connections are spawned.
2. **Dedicated GLib Context for D-Bus Worker (`BleReplayServer`)**:
   - Spawns a dedicated `GLib.MainContext.new()` (isolated from `GLib.MainContext.default()`) for the D-Bus worker thread.
   - Invokes `ctx.push_thread_default()` and runs `GLib.MainLoop(ctx)` on the worker thread, preventing thread contention on the default context and avoiding GTK/GDK assertion errors (`GDK_IS_SCREEN`).
3. **Asynchronous ADB GPS Worker Queue (`AdbGpsInjector`)**:
   - Decouples ADB subprocess execution to a dedicated background daemon worker thread consuming from a thread-safe `queue.Queue`.
   - Prevents synchronous ADB command blocking (30–80 ms per call) from stalling the PyQt6 main thread event loop, maintaining a smooth 60fps UI experience.
4. **Tilde Path Resolution**:
   - Wraps all file path inputs with `os.path.abspath(os.path.expanduser(tcx_path))` to properly handle user shell tilde paths (`~/...`).
5. **Host Pre-Flight Self-Diagnostics Module (`HostPreFlightDiagnostics`)**:
   - Executes synchronously prior to engine launch.
   - Evaluates:
     - BlueZ daemon active (`org.bluez` on SystemBus).
     - Bluetooth adapter present and powered on (`org.bluez.Adapter1` with `Powered = True`).
     - LE Advertising Manager present on adapter (`org.bluez.LEAdvertisingManager1`).
     - ADB binary presence and device connection (`adb devices`).
     - Android shell mock location permission (`cmd appops get 2000 android:mock_location`).
   - Returns a structured diagnostic report with actionable shell remediation commands.
6. **BlueZ D-Bus GATT Server & Dual-Advertisement Engine (`BleReplayServer`)**:
   - Implements full BlueZ GATT hierarchy: `Application`, `Service`, `Characteristic`, `Descriptor`, `Advertisement`.
   - Cycling Power Service (`0x1818`), Heart Rate Service (`0x180D`), and Device Information Service (`0x180A`).
   - Dual LE advertisement registration for `ATT-Pwr` (`1818`) and `ATT-HRM` (`180d`).
7. **Qt Thread Decoupling**:
   - Cross-thread communication from `ReplayEngine` worker thread to GUI uses `pyqtSignal` exclusively.

---

## 2. UI Consistency Audit (Rule 23)

* **Closest Reference**: Existing `replay_workout.py` desktop GUI layout and `SensorGridScreen.kt` telemetry card design language.
* **Palette & Surfaces**:
  - Obsidian Dark Theme: Background `#121214`, Surface Card `#1E1E22`, Outline `#2C2C32`.
  - Brand & Accent Colors: Royal Blue `#2B5BE8` (active playback / track indicator), Emerald Green `#22C55E` (GPS active badge), Vivid Amber `#F59E0B` (BLE advertising badge), Crimson `#EF4444` (Diagnostics warning).
* **Typography**: Clean monospace digital displays for telemetry numbers (Watts, BPM, RPM, km/h) with clear metric unit labels.
* **Justification**: Enhances existing desktop GUI with a dedicated "Diagnostics & Hardware Status" card showing real-time BlueZ and ADB health, with zero frame drops during playback.

---

## 3. Atomic Step Sequencing

### Step 1: Desktop Lifecycle Root Initialization Ordering (`tools/replay_workout.py`)
- In `main()`:
  - Check `--headless` / `--cli` flag.
  - If GUI mode: initialize `app = QApplication(sys.argv)` as the very first statement before `ReplayEngine`, `BleReplayServer`, or GUI windows.
  - Apply `os.path.abspath(os.path.expanduser(tcx_path))` for robust path handling.
- **Verification**: Launching GUI opens cleanly without GTK assertion failures.

### Step 2: Dedicated GLib MainContext Isolation (`tools/replay_workout.py`)
- In `BleReplayServer.start()`:
  - Allocate a new isolated GLib context: `ctx = GLib.MainContext.new()`.
  - In the GLib worker thread, execute `ctx.push_thread_default()` and `self.loop = GLib.MainLoop(ctx)`.
  - Ensure clean teardown with `self.loop.quit()` and `ctx.pop_thread_default()`.
- **Verification**: Zero thread spin or contention; 0% CPU consumption when idle.

### Step 3: Asynchronous ADB GPS Worker Thread (`tools/replay_workout.py`)
- In `AdbGpsInjector`:
  - Create a worker queue `self.queue = queue.Queue(maxsize=5)`.
  - Spawn a daemon worker thread that pulls location tuples and executes `subprocess.run(...)`.
  - Drop obsolete stale coordinate frames if queue is saturated to preserve real-time tracking latency.
- **Verification**: GUI timeline scrubbing and transport buttons remain fully responsive at 60fps.

### Step 4: Rule 30 Host Pre-Flight Diagnostics (`tools/replay_workout.py`)
- Implement `HostPreFlightDiagnostics`:
  - Query BlueZ adapter and LE advertising capabilities via D-Bus.
  - Query ADB devices and `android:mock_location` appops.
  - Display diagnostic badges in GUI status panel and actionable instructions on CLI.

### Step 5: Android 14+ Mock Location Provider Injection (`tools/replay_workout.py`)
- Format `cmd location providers set-test-provider-location <p> --location <lat>,<lon> --accuracy 2.5 --time <ms>` without unsupported options (`--altitude`, `--speed`, `--bearing`).
- Clean teardown on exit.

### Step 6: Unit Test Suite Expansion (`tools/test_replay_workout.py`)
- Add unit tests for path expansion, ADB command building, GLib context isolation structure, and Rule 30 pre-flight checks.

### Step 7: Clean-Room Full Suite Regression Verification
- Run `python3 -m unittest tools/test_replay_workout.py`.
- Run `./gradlew testDebugUnitTest`.

---

## 4. Invariant Protection & Verification Commands

| Step | Invariant Checked | Verification Command |
| :--- | :--- | :--- |
| Step 1-3 | Desktop Process Lifecycle & Thread Isolation | `python3 -m unittest tools/test_replay_workout.py -k TestDesktopLifecycle` |
| Step 4 | Rule 30 Pre-Flight Diagnostics | `python3 -m unittest tools/test_replay_workout.py -k TestHostPreFlightDiagnostics` |
| Step 5 | Android 14+ ADB location syntax | `python3 -m unittest tools/test_replay_workout.py -k TestAdbGpsInjector` |
| Step 6 | Unit Test Suite Verification | `python3 -m unittest tools/test_replay_workout.py` |
| Step 7 | Full clean-room regression | `./gradlew testDebugUnitTest` |
