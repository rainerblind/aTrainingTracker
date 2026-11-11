# Stage 3: Implementation Plan - ATT-6684: tools/replay_workout.py: Compute and pass --speed to set-test-provider-location during workout simulation

**Ticket**: [ATT-6684](https://rainerblind.atlassian.net/browse/ATT-6684)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TOOL-007` (*Simulated Ground Speed Injection & Defensive AOSP CLI Fallback in tools/replay_workout.py*)  
**Test Mapping**: `TST-TOOL-007`  
**Branch**: `feature/ATT-6684`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

During replay of recorded workouts via `tools/replay_workout.py`, parsed/computed trackpoint speed (`speed_mps`) was dropped by `AdbGpsInjector.inject_location()`, preventing mock location providers (`gps`, `network`, `fused`) from receiving realistic velocity. Concurrently, stock Android AOSP `cmd location providers set-test-provider-location` exclusively accepts `--location`, `--accuracy`, and `--time`, throwing `java.lang.IllegalArgumentException: Unknown option: --speed` (returncode 255) when `--speed` is passed. This plan details the implementation of `--speed` command construction, asynchronous queue forwarding, and defensive auto-probing fallback to preserve continuous replay across all Android devices.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TOOL-007` (*Simulated Ground Speed Injection & Defensive AOSP CLI Fallback in tools/replay_workout.py*)
  * `REQ-TOOL-007.1`: Optional `--speed {speed_mps:.2f}` formatting in `AdbGpsInjector.build_inject_cmd()`.
  * `REQ-TOOL-007.2`: Asynchronous queue forwarding of 4-tuple `(lat, lon, timestamp_ms, speed_mps)` in `inject_location()`.
  * `REQ-TOOL-007.3`: Defensive AOSP fallback in `_worker_loop()` with retry and `_supports_speed_flag = False`.
  * `REQ-TOOL-007.4`: CLI flags `--speed-injection` / `--no-speed-injection`.
* **Test Mapping**: `TST-TOOL-007` (`TST-TOOL-007.1` through `TST-TOOL-007.6` in `tools/test_replay_workout.py`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Android App Regressions**: Production Android code in `app/src/main/` is 100% untouched.
2. **Backward Compatibility**: Existing 39 unit tests in `tools/test_replay_workout.py` remain fully green. When `speed_mps=None`, `build_inject_cmd()` strictly preserves Android 14+ syntax without `--speed`.
3. **Continuous Replay on Stock Devices**: If `--speed` is rejected by target device shell, replay seamlessly retries without `--speed` and disables further attempts for the session.
4. **Parent Human Gate Invariance**: Terminal completion remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `AdbGpsInjector.build_inject_cmd` (`tools/replay_workout.py`)
* Signature: `build_inject_cmd(cls, provider: str, lat: float, lon: float, time_ms: int, accuracy: float = 2.5, speed_mps: Optional[float] = None) -> str`
* Logic: When `speed_mps is not None and speed_mps >= 0.0`, append `f" --speed {speed_mps:.2f}"`.

### Component 2: `AdbGpsInjector.inject_location` & `_worker_loop` (`tools/replay_workout.py`)
* Instance variable: `self._supports_speed_flag: Optional[bool] = None`.
* `inject_location()`: Enqueue `(lat, lon, timestamp_ms, speed_mps)`.
* `_worker_loop()`: Unpack 2-, 3-, or 4-tuples. If `_supports_speed_flag is not False` and `speed_mps is not None`, format command with `speed_mps`. If execution fails with `Unknown option: --speed`, log a single warning, set `_supports_speed_flag = False`, and immediately retry without `speed_mps`.

### Component 3: CLI Parser Configuration (`tools/replay_workout.py`)
* Add CLI argument `--speed-injection` / `--no-speed-injection` (default True).

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `build_inject_cmd` (`REQ-TOOL-007.1`)
* Target: `tools/replay_workout.py`
* Add `speed_mps: Optional[float] = None` argument. Conditionally append `--speed {speed_mps:.2f}` when non-negative.

### Step 2: Update `inject_location` and `_worker_loop` (`REQ-TOOL-007.2`, `REQ-TOOL-007.3`)
* Target: `tools/replay_workout.py`
* Initialize `self._supports_speed_flag = True` (or `None`).
* Enqueue 4-tuples with `speed_mps`. Support backward-compatible tuple unpacking in `_worker_loop`.
* Add fallback catch: check output for `Unknown option: --speed`; on failure, set `self._supports_speed_flag = False` and retry without `--speed`.

### Step 3: Add CLI Arguments (`REQ-TOOL-007.4`)
* Target: `tools/replay_workout.py`
* Add `--speed-injection / --no-speed-injection` to `argparse` and pass to `AdbGpsInjector`.

### Step 4: Unit Test Suite Expansion (`TST-TOOL-007.1` – `TST-TOOL-007.4`)
* Target: `tools/test_replay_workout.py`
* Implement `test_build_inject_cmd_with_speed`, `test_build_inject_cmd_backward_compatibility`, `test_async_queue_injection_with_speed`, and `test_speed_fallback_on_unsupported_option`.

### Step 5: Verification & Regression Run (`TST-TOOL-007.6`)
* Command: `python3 -m unittest tools/test_replay_workout.py`
* Verify 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute `python3 -m unittest tools/test_replay_workout.py` and verify `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows full revert (`git restore .`) without affecting `sprint/2026-46.2`.
