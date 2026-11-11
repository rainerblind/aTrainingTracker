# Stage 5: Walkthrough & Verification - ATT-6684: tools/replay_workout.py: Compute and pass --speed to set-test-provider-location during workout simulation

**Ticket**: [ATT-6684](https://rainerblind.atlassian.net/browse/ATT-6684)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TOOL-007` (*Simulated Ground Speed Injection & Defensive AOSP CLI Fallback in tools/replay_workout.py*)  
**Test Mapping**: `TST-TOOL-007`  
**Branch**: `feature/ATT-6684`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

During Sprint 2026-46.1 review of `ATT-6256`, workout replays via `tools/replay_workout.py` on connected devices injected coordinates into `gps`, `network`, and `fused` test providers without `--speed`, causing mocked `Location` objects to arrive with zero speed and trigger Doppler stationary pinning on a resting desk device.

In `ATT-6684`, `tools/replay_workout.py` was enhanced to:
1. Support optional `--speed {speed_mps:.2f}` formatting in `AdbGpsInjector.build_inject_cmd()`.
2. Forward 4-tuples `(lat, lon, timestamp_ms, speed_mps)` through `AdbGpsInjector.inject_location()` and `self._queue`.
3. Defensively detect AOSP platform limitations in `_worker_loop()`: if the target device returns `Unknown option: --speed` (returncode 255), log a single notice, set `_supports_speed_flag = False`, and immediately retry without `--speed`.
4. Provide `--speed-injection` and `--no-speed-injection` CLI argument toggles.

All 44 unit tests in `tools/test_replay_workout.py` pass cleanly in 0.187s. Living documentation in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` has been updated to `[Verified]`, and master index tables are 100% synchronized.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TOOL-007.1` | `TST-TOOL-007.1` | `test_build_inject_cmd_with_speed` | **PASSED** | `Verified` |
| `REQ-TOOL-007.1` | `TST-TOOL-007.2` | `test_build_inject_cmd_backward_compatibility` | **PASSED** | `Verified` |
| `REQ-TOOL-007.2` | `TST-TOOL-007.3` | `test_async_queue_injection_with_speed` | **PASSED** | `Verified` |
| `REQ-TOOL-007.3` | `TST-TOOL-007.4` | `test_speed_fallback_on_unsupported_option` | **PASSED** | `Verified` |
| `REQ-TOOL-007.4` | `TST-TOOL-007.5` | `test_speed_injection_cli_arguments` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TOOL-007.6` | Full Tooling Suite (`python3 -m unittest tools/test_replay_workout.py`) | **PASSED** (44/44) | `Verified` |

---

## 3. Automated Test Evidence

### Tooling Unit Test Suite (`python3 -m unittest tools/test_replay_workout.py`)
```text
.........[!] Notice: Target device does not support --speed in set-test-provider-location. Falling back to standard location injection.
............................[*] Scheduled event: ATT-HRM killed at 00:00:10
[*] Scheduled event: GPS killed at 00:00:12
[*] Scheduled event: ATT-Pwr killed at 00:00:15
[*] Scheduled event: ATT-HRM revived at 00:00:20
[*] Scheduled event: GPS revived at 00:00:22
[*] Scheduled event: ATT-Pwr revived at 00:00:25
.......
----------------------------------------------------------------------
Ran 44 tests in 0.187s

OK
```

### Living Documentation Governance Verification (`tools/verify_requirement_governance.py`)
```text
Recalculated master index table in docs/requirements.md: Total=1034, Verified=1010, Planned=24.
Recalculated master index table in docs/tests.md: Total=1024, Verified=1020, Planned=4.
PASS: Master index tables successfully synchronized with 100% parity.
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
```

---

## 4. Hardware / Physical Verification

Pure developer tooling enhancement (`tools/replay_workout.py` and `tools/test_replay_workout.py`); zero APK runtime bytecode changes; verified via automated Python test suite and live Android 15 emulator CLI execution.

### Visual Consistency (Rule 23)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Production Android code in `app/src/main/` is 100% untouched.
2. **Backward Compatibility**: Calling `build_inject_cmd()` with `speed_mps=None` produces exact Android 14+ command syntax without `--speed`.
3. **Living Documentation Synchronized**: `REQ-TOOL-007` and `TST-TOOL-007` updated to `Verified` in domain shards and master index tables.
4. **Parent Ticket Final Review**: Subtask transitioned to `Erledigt` via `gate-verdict PASS`; parent ticket advances to `Final Review (Human)`.
