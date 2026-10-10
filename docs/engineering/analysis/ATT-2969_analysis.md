# Stage 1 Analysis: ATT-2969 - Synchronized TCX workout replay tool with PyQt6 GUI, BLE Power, Cadence, Heart Rate, and mock GPS

**Ticket**: [ATT-2969](https://atrainingtracker.atlassian.net/browse/ATT-2969)  
**Sub-task**: [ATT-3093](https://atrainingtracker.atlassian.net/browse/ATT-3093) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://atrainingtracker.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `None` (Developer Tooling)  
**Active Sprint**: `Sprint 2026-41.7`  
**Branch**: `feature/ATT-2969`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During on-device Sprint Review of Sprint 2026-41.7, the user attempted to launch the newly implemented developer replay tool (`tools/replay_workout.py`) with a real-world workout activity (`~/Dropbox/apps/Workouts/TCX/2018-04-27_145800.tcx`).
The tool launch failed with a stream of system assertions and error messages:
1. Gtk critical assertion failures: `Gtk-CRITICAL **: gtk_icon_theme_get_for_screen: assertion 'GDK_IS_SCREEN (screen)' failed`
2. GLib main context warning: `Warning: g_main_context_push_thread_default: assertion 'acquired_context' failed at app = QApplication(sys.argv)`
3. Process thread lockup / CPU spin at 90–97% CPU on a background GLib worker thread.
4. The user cannot conduct Sprint Review on-device testing of live navigation, climb, segment, and telemetry tickets without a working replay tool.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic debugging of `tools/replay_workout.py` on Linux under Wayland/X11 revealed three distinct failure mechanisms:

1. **Subsystem Initialization Inversion (`QApplication` vs. GLib MainLoop)**:
   In `main()`, `ble_server = BleReplayServer(...)` is started at line 1478 **before** `app = QApplication(sys.argv)` is created at line 1502.
   In `BleReplayServer.start()`:
   ```python
   def glib_worker():
       ctx = GLib.MainContext.default()
       ctx.push_thread_default()
       self.loop = GLib.MainLoop(ctx)
       self.loop.run()
   self.loop_thread = threading.Thread(target=glib_worker, daemon=True)
   self.loop_thread.start()
   ```
   `GLib.MainContext.default()` is the global default context. In Linux desktop environments, PyQt6/Qt6 uses the GLib event loop integration for its GUI event processing. Spawning a background thread that attempts to seize the default context via `push_thread_default()` without acquiring it fails, and runs an unacquired default context on a background worker thread. When `QApplication` subsequently initializes, GDK/GTK theme integration asserts fail (`GDK_IS_SCREEN failed`) and the GLib loop enters an unthrottled spin loop consuming 100% CPU of a core.

2. **File Path Resolution (Tilde Expansion)**:
   In `main()`, `tcx_path` is processed via `os.path.abspath(tcx_path)`. `os.path.abspath` does not expand shell tildes (`~`). If a user passes `~/Dropbox/...` (or a path starting with `~`), `os.path.expanduser` must be invoked prior to checking `os.path.isfile()`.

3. **Synchronous ADB Socket Subprocess Blocking in Qt Event Loop**:
   In `ReplayGuiWindow.on_engine_update()`, `self.gps.inject_location()` executes `subprocess.run(["adb", "-s", ...])` synchronously on the Qt main GUI thread. When a 10 Hz timer tick triggers coordinate updates, invoking ADB synchronously blocks the Qt event dispatch loop for 30–80 ms per call, causing UI stutter and unresponsive transport buttons.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Initialize `QApplication(sys.argv)` strictly before any subsystem (BLE, DBus, ADB) or GUI widgets are created.
  2. Isolate GLib D-Bus event processing cleanly: run the GLib main loop on an isolated `GLib.MainContext.new()` dedicated exclusively to D-Bus/BlueZ, or integrate cleanly without hijacking the global default context.
  3. Expand user tilde paths via `os.path.expanduser`.
  4. Decouple ADB GPS coordinate injection into an asynchronous non-blocking worker queue or thread so the Qt GUI stays responsive (60 fps).
  5. Provide immediate clear console and UI feedback if ADB device is not attached or if Bluetooth requires permissions.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Android production app code (this is strictly a developer desktop tool).
  * Supporting platforms other than Linux desktop for BLE advertising (BlueZ D-Bus is the Linux standard).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TOOL-002` (*Synchronized TCX Workout Replay & Desktop Telemetry Simulator*)
* **Historical Origin & Commit Trace**: Added in ATT-2969 (earlier iteration in Sprint 2026-41.7).
* **Root Reason for Existing Formulation**: Provides desk-testing capability for GPS track movement, BLE power/cadence, and heart rate without outdoor rides.
* **Preservation of Core Invariants**: The functional specifications of `REQ-TOOL-002` (BLE 0x1818, BLE 0x180D, ADB mock GPS, PyQt6 GUI, headless mode) remain 100% preserved. The architectural fix addresses desktop process lifecycle ordering and GLib context isolation.

---

## 5. Architectural Strategy & High-Level Solution

1. **Strict Initialization Ordering**:
   In `main()`:
   - If running with GUI (default): Instantiating `app = QApplication(sys.argv)` is the very first action before creating `ReplayEngine`, `BleReplayServer`, or `AdbGpsInjector`.
2. **Dedicated GLib Context for D-Bus Worker**:
   In `BleReplayServer`:
   - Replace `GLib.MainContext.default()` with a newly allocated context:
     ```python
     ctx = GLib.MainContext.new()
     ctx.push_thread_default()
     self.loop = GLib.MainLoop(ctx)
     self.loop.run()
     ```
   - Ensure `dbus.mainloop.glib.DBusGMainLoop` binds cleanly or uses the isolated context, eliminating GTK/GDK conflicts.
3. **Asynchronous ADB GPS Worker Thread**:
   In `AdbGpsInjector`:
   - Use a thread-safe `queue.Queue` with a single daemon worker thread to execute `subprocess.run(["adb", ...])` asynchronously without blocking the Qt GUI thread.
4. **User-Friendly Path Handling**:
   - Apply `os.path.abspath(os.path.expanduser(tcx_path))` to handle tildes, relative paths, and drag-and-drop file inputs gracefully.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero production Android app code touched (0% regression risk).
  2. Full test suite `tools/test_replay_workout.py` passing 100%.
  3. Clean termination on exit (`atexit` teardown of test providers and BLE advertisements).
* **Risk Rating**: **LOW** (Desktop developer tool isolated from production APK).
