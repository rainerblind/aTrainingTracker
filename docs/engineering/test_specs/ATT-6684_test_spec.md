# Stage 2: Requirement & Test Specification - ATT-6684: tools/replay_workout.py: Compute and pass --speed to set-test-provider-location during workout simulation

**Ticket**: [ATT-6684](https://rainerblind.atlassian.net/browse/ATT-6684)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TOOL-007` (*Simulated Ground Speed Injection & Defensive AOSP CLI Fallback in tools/replay_workout.py*)  
**Test Spec ID**: `TST-TOOL-007`  
**Branch**: `feature/ATT-6684`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TOOL-007)

### 1.1 Problem Statement & Rationale
During workout replays via `tools/replay_workout.py`, parsed/computed `speed_mps` was dropped before ADB mock location injection. Concurrently, stock AOSP `cmd location providers set-test-provider-location` exclusively accepts `--location`, `--accuracy`, and `--time`, rejecting `--speed` with `IllegalArgumentException`. The tool SHALL format `--speed` when available, forward `speed_mps` through the asynchronous queue, and defensively fall back to standard syntax without `--speed` if rejected by the target device.

### 1.2 Functional & Architectural Requirements
1. `AdbGpsInjector.build_inject_cmd(..., speed_mps: Optional[float] = None)` SHALL append `--speed {speed_mps:.2f}` when `speed_mps >= 0.0`. When `speed_mps is None`, it SHALL omit `--speed`.
2. `AdbGpsInjector.inject_location()` SHALL queue 4-tuples `(lat, lon, timestamp_ms, speed_mps)`, and `_worker_loop` SHALL maintain backward compatibility with 2-, 3-, and 4-tuples.
3. If ADB returns `Unknown option: --speed` (exit code 255), `AdbGpsInjector` SHALL set `_supports_speed_flag = False`, emit a single warning notice, and transparently retry without `--speed`.
4. `tools/replay_workout.py` SHALL support `--speed-injection` and `--no-speed-injection` CLI arguments.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Command Formatting)**:
  * *Given* `build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=5.50)`
  * *When* generating the injection command,
  * *Then* the string ends with `--time 1700000000000 --speed 5.50`.
* **Criterion 2 (Backward Compatibility)**:
  * *Given* `build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=None)`
  * *When* generating the injection command,
  * *Then* the string preserves standard Android 14+ syntax and does NOT contain `--speed`.
* **Criterion 3 (AOSP Graceful Fallback)**:
  * *Given* an active replay session where ADB shell returns exit code 255 with `Unknown option: --speed`,
  * *When* `_worker_loop` detects the error,
  * *Then* `_supports_speed_flag` is set to `False`, a single warning is logged, and subsequent injections omit `--speed`.

### 1.4 System Invariants
* Zero modifications to Android production code in `app/src/main/`.
* 100% backward compatibility with all 39 existing unit tests in `tools/test_replay_workout.py`.
* Continuous playback without dropping coordinates on stock AOSP devices.

---

## 2. Test Specification (TST-TOOL-007)

### Test Case 1: `test_build_inject_cmd_with_speed` (`TST-TOOL-007.1`)
* **Scope**: Unit Test (`tools/test_replay_workout.py`)
* **Preconditions**: `AdbGpsInjector` initialized.
* **Action**: Invoke `build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=5.5)`.
* **Expected Result**: Output command contains `--speed 5.50`.

### Test Case 2: `test_build_inject_cmd_backward_compatibility` (`TST-TOOL-007.2`)
* **Scope**: Unit Test (`tools/test_replay_workout.py`)
* **Action**: Invoke `build_inject_cmd("gps", 48.1, 11.5, 1700000000000, accuracy=2.5, speed_mps=None)`.
* **Expected Result**: Output command does NOT contain `--speed`.

### Test Case 3: `test_async_queue_injection_with_speed` (`TST-TOOL-007.3`)
* **Scope**: Unit Test (`tools/test_replay_workout.py`)
* **Action**: Enqueue coordinates via `inject_location(..., speed_mps=8.2, time_sec=1.0)`.
* **Expected Result**: Queue item unpacked as `(48.1, 11.5, 1700000001000, 8.2)`.

### Test Case 4: `test_speed_fallback_on_unsupported_option` (`TST-TOOL-007.4`)
* **Scope**: Unit Test (`tools/test_replay_workout.py`)
* **Action**: Simulate ADB returning exit code 255 with `Unknown option: --speed`.
* **Expected Result**: `_supports_speed_flag` set to `False`, retry executed without `--speed`.

### Test Case 5: 9-Language Localization Audit (`TST-TOOL-007.5`)
* **Scope**: Developer Tooling Audit (no UI string resource modifications).
* **Expected Result**: 100% parity preserved across all 9 locales.

### Test Case 6: Full Tooling Regression Suite (`TST-TOOL-007.6`)
* **Command**: `python3 -m unittest tools/test_replay_workout.py`
* **Expected Result**: 100% pass rate across all tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TOOL-007.1` | Unit | `AdbGpsInjector.build_inject_cmd` | `REQ-TOOL-007.1` | Specified |
| `TST-TOOL-007.2` | Unit | `AdbGpsInjector.build_inject_cmd` | `REQ-TOOL-007.1` | Specified |
| `TST-TOOL-007.3` | Unit | `AdbGpsInjector.inject_location` | `REQ-TOOL-007.2` | Specified |
| `TST-TOOL-007.4` | Unit | `AdbGpsInjector._worker_loop` | `REQ-TOOL-007.3` | Specified |
| `TST-TOOL-007.5` | Localization | `TranslationParityTest` | `REQ-TOOL-007`, `REQ-UI-106` | Specified |
| `TST-TOOL-007.6` | Regression | `tools/test_replay_workout.py` | `REQ-PRO-001` | Specified |
