# Stage 3: Implementation Plan - ATT-3971: Release ADB mock location test providers in replay_workout.py immediately on Pause, Stop, or completion

**Ticket**: [ATT-3971](https://rainerblind.atlassian.net/browse/ATT-3971)  
**Sub-task**: [ATT-4039](https://rainerblind.atlassian.net/browse/ATT-4039) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3842](https://rainerblind.atlassian.net/browse/ATT-3842) (*Cycling Power Metrics*)  
**Target Release**: `Pending Final Review (Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-TRK-025`  
**Test Mapping**: `TST-TRK-017`  
**Branch**: `feature/ATT-3971`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

`tools/replay_workout.py` previously registered ADB mock location test providers (`gps`, `network`, `fused`) in `AdbGpsInjector.setup()` at application launch (while `STOPPED`) and only removed them in `teardown()` at process exit. If a user paused, stopped, or completed a workout replay and unplugged the phone before closing `replay_workout.py`, Android's `LocationManager.GPS_PROVIDER` remained trapped in `[mock]` mode, blocking real hardware GNSS updates (`gps_hardware`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-025` (*Deterministic `AdbGpsInjector` Mock Provider Lifecycle Bound to `PLAYING` State in `tools/replay_workout.py`*)
* **Test Mapping**: `TST-TRK-017` (*Deterministic `AdbGpsInjector` Mock Provider Lifecycle (`PLAYING` Registration & Immediate `PAUSED`/`STOPPED`/`EOF` Removal) Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing Python unit tests (`tools/test_replay_workout.py`) and Android unit tests (`./gradlew testDebugUnitTest`) continue to pass cleanly.
2. **Thread Safety & Single-Thread Worker Queue**: `AdbGpsInjector` preserves its background queue worker thread for asynchronous location injection.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)` (Rule 3).

---

## 4. Proposed Architectural Changes

### Component 1: `AdbGpsInjector` Lifecycle (`tools/replay_workout.py`)
* `self.providers_registered: bool = False`: Tracks whether `gps`, `network`, and `fused` mock providers are currently registered on the connected Android device.
* `cleanup_device_providers()`: Proactively disables and removes `gps`, `network`, and `fused` test providers (`set-test-provider-enabled <p> false` + `remove-test-provider <p>`) and sets `self.providers_registered = False`.
* `setup()`: Checks `adb devices`, sets `appops set com.android.shell android:mock_location allow` and `settings put secure mock_location 1`, and invokes `cleanup_device_providers()` without registering test providers while `STOPPED`.
* `register_providers()` / `on_play()` / `enable_providers()`: Lazily registers `add-test-provider` and `set-test-provider-enabled true` for `gps`, `network`, and `fused` when `not self.is_killed`, setting `self.providers_registered = True`.
* `release_providers()` / `on_pause()` / `on_stop()` / `disable_providers()`: Immediately executes `set-test-provider-enabled <p> false` and `remove-test-provider <p>` for `gps`, `network`, and `fused` when `self.providers_registered` is `True`, setting `self.providers_registered = False`.

### Component 2: `ReplayGuiWindow`, `run_headless_cli`, and `--cleanup` CLI Option (`tools/replay_workout.py`)
* `ReplayGuiWindow`: Calls `self.gps.on_play()` on Play, `self.gps.on_pause()` on Pause and on `EOF` (`_on_tick` when `self.engine.state != ReplayState.PLAYING`), and `self.gps.on_stop()` on Stop and `load_file()`.
* `run_headless_cli`: Calls `gps_injector.on_play()` when starting playback and `gps_injector.on_pause()` when `engine.state == ReplayState.PAUSED` (`EOF`).
* `main()`: Adds `--cleanup` CLI flag and makes `tcx_file` `nargs="?"` so `python3 tools/replay_workout.py --cleanup` cleans up lingering mock providers and exits `0`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Verify Gate 3 & `AdbGpsInjector` Lifecycle Implementation (`tools/replay_workout.py`)
* Files: `tools/replay_workout.py`
* Changes: Ensure `AdbGpsInjector` (`setup`, `cleanup_device_providers`, `register_providers`, `release_providers`, `on_play`, `on_pause`, `on_stop`), `ReplayGuiWindow`, `run_headless_cli`, and `main(--cleanup)` implement `REQ-TRK-025.1` and `REQ-TRK-025.2`.

### Step 2: Comprehensive Unit Tests (`tools/test_replay_workout.py`)
* Files: `tools/test_replay_workout.py`
* Changes: Verify `setup()` startup cleanup, lazy `PLAYING` registration, immediate `PAUSED`/`STOPPED`/`kill_gps(True)`/`EOF` headless CLI provider removal, and `--cleanup` CLI execution (`TST-TRK-017.1`, `TST-TRK-017.2`).

### Step 3: Targeted Unit Tests
* Command: `python3 -m unittest tools/test_replay_workout.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted Python unit tests in Stage 4 (`python3 -m unittest tools/test_replay_workout.py`), followed by the full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-3971`) allows full revert without affecting `develop`.
