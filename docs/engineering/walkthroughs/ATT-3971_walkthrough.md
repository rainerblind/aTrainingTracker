# Stage 5: Walkthrough & Verification - ATT-3971: Release ADB mock location test providers in replay_workout.py immediately on Pause, Stop, or completion

**Ticket**: [ATT-3971](https://rainerblind.atlassian.net/browse/ATT-3971)  
**Sub-task**: [ATT-4041](https://rainerblind.atlassian.net/browse/ATT-4041) (`[Test]`)  
**Parent Epic**: [ATT-3842](https://rainerblind.atlassian.net/browse/ATT-3842) (*Cycling Power Metrics*)  
**Target Release**: `Pending Final Review (Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-TRK-025`  
**Test Mapping**: `TST-TRK-017`  
**Branch**: `feature/ATT-3971`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the verification of **ATT-3971** (*Release ADB mock location test providers in replay_workout.py immediately on Pause, Stop, or completion*):

1. **Proactive Startup & `--cleanup` Test Provider Removal (`REQ-TRK-025.1`)**:
   - [`tools/replay_workout.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/replay_workout.py) (`AdbGpsInjector.setup()` and `cleanup_device_providers()`) proactively disables and removes any lingering `gps`, `network`, and `fused` mock location test providers (`remove-test-provider <p>`) at startup while remaining in `is_registered = False` (`ReplayState.STOPPED`).
   - Added `--cleanup` CLI option (`python3 tools/replay_workout.py --cleanup`) to immediately remove lingering test providers on a connected Android device without requiring a `.tcx` file.
2. **Lazy Registration on `PLAYING` & Immediate Removal on `PAUSED` / `STOPPED` / `kill` / `EOF` (`REQ-TRK-025.2`)**:
   - `AdbGpsInjector` registers `add-test-provider gps/network/fused` lazily only when playback transitions to `ReplayState.PLAYING` (`on_play()`).
   - `AdbGpsInjector.release_providers()` immediately removes `gps`, `network`, and `fused` test providers (`remove-test-provider <p>`) whenever playback transitions to `PAUSED` (`on_pause()`), `STOPPED` (`on_stop()`), `kill()`, timeline completion (`EOF`), or `teardown()`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-025.1` | `TST-TRK-017.1`, `TST-TRK-017.2` | `TestAdbGpsInjector.test_adb_gps_injector_commands` & `TestCliArguments.test_cleanup_cli_argument` (`tools/test_replay_workout.py`) | **PASSED** | `Verified` |
| `REQ-TRK-025.2` | `TST-TRK-017.1` | `TestAdbGpsInjector.test_adb_gps_injector_removes_test_providers_on_pause_stop_and_eof` (`tools/test_replay_workout.py`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-017.3` | Full Clean-Room `./gradlew testDebugUnitTest` & `python3 -m unittest tools/test_replay_workout.py` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest` & `tools/test_replay_workout.py`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 3m 3s
32 actionable tasks: 1 executed, 31 up-to-date

----------------------------------------------------------------------
Ran 39 tests in 0.328s

OK
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* Pure developer tooling enhancement (`tools/replay_workout.py`) with zero APK runtime bytecode changes in `ATT-3971`; also verified directly on the connected Pixel 10 via `python3 tools/replay_workout.py --cleanup` and `adb shell dumpsys location` (`gps provider` cleanly restored from `LocationProviderManager[mock]` to `gps_hardware`).

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite and Python replay test suite (`39/39`) executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-TRK-025` in `docs/requirements.md` and `TST-TRK-017` in `docs/tests.md` is `Verified` and validated via `python3 tools/verify_requirement_governance.py`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-4041`) transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket (`ATT-3971`) transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
