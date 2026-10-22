# Stage 1 Analysis: ATT-3971 - Release ADB mock location test providers in replay_workout.py immediately on Pause, Stop, or completion

**Ticket**: [ATT-3971](https://rainerblind.atlassian.net/browse/ATT-3971)  
**Sub-task**: [ATT-4037](https://rainerblind.atlassian.net/browse/ATT-4037) (`[Analysis]`)  
**Parent Epic**: [ATT-3842](https://rainerblind.atlassian.net/browse/ATT-3842) (*Cycling Power Metrics*)  
**Target Release**: `Pending Final Review (Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Branch**: `feature/ATT-3971`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

As discovered during the forensic investigation of **ATT-3961**, `tools/replay_workout.py` previously registered Android mock location test providers (`gps`, `network`, and `fused`) inside `AdbGpsInjector.setup()` immediately when `main()` launched, while the replay engine was still in `ReplayState.STOPPED`:
```text
cmd location providers add-test-provider gps --supportsAltitude --supportsSpeed --supportsBearing
cmd location providers set-test-provider-enabled gps true
```
Because `gps_injector.teardown()` (`remove-test-provider gps/network/fused`) was only invoked when the Python process exited:
1. If a developer paused (`ReplayState.PAUSED`), stopped (`ReplayState.STOPPED`), or completed a workout replay (`EOF`) and unplugged the test phone from USB before closing the `replay_workout.py` window, Android's `LocationManager.GPS_PROVIDER` on the phone remained indefinitely trapped in `[mock]` mode (`gps: LocationProviderManager[mock]`).
2. While `LocationManager.GPS_PROVIDER` is overridden by a test provider (`[mock]`) with no active ADB stream injecting coordinates, Android's hardware GNSS chip (`gps_hardware`) is completely blocked from delivering real satellite fixes to `SpeedAndLocationDevice_GPS`.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Premature Registration in `AdbGpsInjector.setup()`**:
   - Originally (`REQ-TOOL-002` Clause 4), `AdbGpsInjector.setup()` simultaneously added (`add-test-provider`) and enabled (`set-test-provider-enabled true`) the `gps`, `network`, and `fused` test providers at startup before the user clicked Play.
2. **Incomplete Lifecycle Coupling on `PAUSED`, `STOPPED`, `kill_gps`, and `EOF`**:
   - When the user clicked Pause or Stop, or when the replay reached the final trackpoint (`current_index == len(points) - 1`), `ReplayEngine` transitioned to `ReplayState.PAUSED` or `ReplayState.STOPPED`, halting periodic calls to `inject_location(...)`, but left the test providers registered on the Android device.
   - Similarly, `kill_gps(True)` only toggled `set-test-provider-enabled <p> false` rather than removing the test provider (`remove-test-provider <p>`), which on Android 14–16 still leaves `gps` bound to a disabled test provider rather than restoring `gps_hardware`.
3. **Lack of Startup / Standalone Cleanup**:
   - If a previous replay session was interrupted (e.g., USB cable unplugged while playing), reconnecting the phone and launching `replay_workout.py` did not clean up lingering test providers while `STOPPED`, nor was there a standalone `--cleanup` CLI flag to restore hardware GPS immediately.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Dynamic Test Provider Lifecycle Bound to `PLAYING` State (`REQ-TRK-025`)**:
     - Do not register `add-test-provider` at startup (`setup()`) while `ReplayEngine` is `STOPPED`.
     - Lazily register `add-test-provider gps/network/fused` (`register_providers()`) only when playback enters `ReplayState.PLAYING` (`on_play()` / `enable_providers()` / `inject_location()` when `not is_killed`).
     - Immediately execute `release_providers()` (`set-test-provider-enabled <p> false` + `remove-test-provider <p>` for `gps`, `network`, `fused`, setting `providers_registered = False`) whenever playback transitions to `PAUSED` (`on_pause()`), `STOPPED` (`on_stop()`), `kill_gps(True)`, workout completion (`EOF` in both `ReplayGuiWindow._on_tick` and `run_headless_cli`), or process exit (`teardown()`).
  2. **Proactive Startup Cleanup & `--cleanup` CLI Flag**:
     - In `AdbGpsInjector.setup()`, proactively remove any lingering `gps`, `network`, and `fused` test providers (`cleanup_device_providers()`) while remaining `providers_registered = False`.
     - Provide `--cleanup` CLI option in `tools/replay_workout.py` to immediately remove lingering test providers on a connected Android device and exit cleanly.
  3. **Comprehensive Automated Unit Verification (`TST-TRK-017`)**:
     - Verify `setup()`, `on_play()`, `on_pause()`, `on_stop()`, `kill_gps(True)`, `EOF` auto-pause, `teardown()`, and `--cleanup` CLI behavior in `tools/test_replay_workout.py`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying BLE peripheral GATT server broadcasting (`BlePeripheralServer`).
  - Modifying TCX parsing or timestamp interpolation (`REQ-TOOL-006`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-TRK-025` (introduced in `ATT-3961` / `ATT-3971`, amending `REQ-TOOL-002` Clause 4 in `docs/requirements.md`), targeting `tools/replay_workout.py` and `tools/test_replay_workout.py`.
* **Historical Origin & Commit Trace**:
  - `ATT-2969` (Sprint 2026-41.6, commit `1523f683`): Introduced `tools/replay_workout.py` (`REQ-TOOL-002`).
  - `ATT-3961` (Sprint 2026-42.8, commit `fc28443c`): Formulated `REQ-TRK-025` and `TST-TRK-017` alongside `REQ-TRK-024`.
* **Root Reason for Existing Formulation**:
  - `AdbGpsInjector.setup()` originally registered `add-test-provider` once at startup to avoid repeating `add-test-provider` on every $1\text{ Hz}$ `inject_location()` call, and deferred `remove-test-provider` to `teardown()` at process exit.
* **Preservation of Core Invariants**:
  - Tracking `self.providers_registered: bool = False` in `AdbGpsInjector` preserves zero-overhead $1\text{ Hz}$ location injection during active `PLAYING` state (since `register_providers()` is a no-op once `self.providers_registered == True`), while guaranteeing that `release_providers()` immediately removes all mock providers whenever playback is `PAUSED`, `STOPPED`, `kill_gps(True)`, or reaches `EOF`.

---

## 5. Architectural Strategy & High-Level Solution

1. **`AdbGpsInjector` State Machine (`tools/replay_workout.py`)**:
   - `self.providers_registered: bool = False` tracks whether `gps`, `network`, and `fused` test providers are currently registered on the Android device.
   - `setup()`: Verifies ADB connectivity, enables `allow_mock_location`, and calls `cleanup_device_providers()` (`remove-test-provider`) without registering new test providers (`self.providers_registered = False`).
   - `register_providers()` / `on_play()` / `enable_providers()`: Lazily executes `add-test-provider` and `set-test-provider-enabled true` for `gps`, `network`, and `fused` when `not self.is_killed`, setting `self.providers_registered = True`.
   - `release_providers()` / `on_pause()` / `on_stop()` / `disable_providers()`: Executes `set-test-provider-enabled false` and `remove-test-provider` for `gps`, `network`, and `fused` whenever `self.providers_registered` is `True`, setting `self.providers_registered = False`.
2. **GUI, Headless CLI & `--cleanup` Integration**:
   - `ReplayGuiWindow`: Calls `gps.on_play()` on Play, `gps.on_pause()` on Pause and on timeline completion (`EOF` in `_on_tick`), and `gps.on_stop()` on Stop and `load_file()`.
   - `run_headless_cli`: Calls `gps.on_play()` on start, `gps.on_pause()` when `engine.state == ReplayState.PAUSED` (`EOF`), and `gps.teardown()` in `finally`.
   - `main()`: Supports `--cleanup` without requiring a `.tcx` positional argument, invoking `gps_injector.cleanup_device_providers()` and returning `0`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`./gradlew testDebugUnitTest` and `python3 -m unittest tools/test_replay_workout.py`).
  2. Immediate restoration of hardware GNSS (`gps_hardware`) whenever replay is paused, stopped, or completed.
  3. Parent ticket Human Decision Gate (`Final Review (Human)`) remains strictly enforced (Rule 3).
* **Risk Rating**: **LOW**
  - Changes are strictly isolated to the developer tooling script `tools/replay_workout.py` and its unit test suite `tools/test_replay_workout.py`.
