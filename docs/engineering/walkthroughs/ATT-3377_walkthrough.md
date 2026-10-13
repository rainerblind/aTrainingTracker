# Stage 5: Walkthrough - ATT-3377: Support simulated exit reason injection and PyQt6 desktop GUI for process kill simulator

**Ticket**: [ATT-3377](https://rainerblind.atlassian.net/browse/ATT-3377)  
**Sub-task**: [ATT-3426](https://rainerblind.atlassian.net/browse/ATT-3426) (`[Test]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-STB-015`  
**Test Mapping**: `TST-STB-015`  
**Branch**: `feature/ATT-3377`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-STB-015` (*Simulated Process Exit Reason Injection & Native PyQt6 Desktop Kill Simulator Architecture*, net-new developer testing and stability tooling requirement referencing `REQ-STB-012` and `ATT-2469`).
2. **Historical Origin & Commit Trace**:
   - Introduced in Sprint 2026-40.16 (`ATT-2079`, commit `95e0c511`) and Sprint 2026-40.17 (`ATT-2469`, `simulate_kill.py`). Created to allow testing process kill dialogs at the desk without physical device power-cycling or background waiting.
3. **Root Reason for Existing Formulation**:
   - `ProcessExitReasonHelper.kt` relied strictly on Android 11+ `ApplicationExitInfo.getHistoricalProcessExitReasons()`. In Android (AOSP), `ApplicationExitInfo.REASON_LOW_MEMORY` is generated exclusively by the Linux kernel's in-kernel Low Memory Killer daemon (`lmkd`) when the device experiences actual physical RAM exhaustion. User-space ADB commands (`am send-trim-memory` or `kill -9`) produce `REASON_SIGNALED` or `REASON_USER_REQUESTED` in ActivityManagerService, which `ProcessExitReasonHelper` reclassifies as `BATTERY_KILL` when the app is not battery-whitelisted. As a result, testers could not deterministically verify the Low Memory dialog (`"Training unterbrochen: Speicherengpass"`). Furthermore, `simulate_kill.py` initially used an ad-hoc local HTTP browser GUI, whereas the established project standard for developer desktop tools (`tools/replay_workout.py`) is a native PyQt6 desktop application with an obsidian theme.
4. **Preservation of Core Invariants**:
   - Invariant 1: In release builds (`BuildConfig.DEBUG == false`), the simulated exit reason injection channel is 100% disabled/stripped; production code relies exclusively on system `ApplicationExitInfo`.
   - Invariant 2: Recovery dialog contracts (`StartOrResumeInterface`), resumption state machines (`REQ-STB-003`), and battery exemption shortcuts remain unchanged.
   - Invariant 3: Single-use consumption ensures injected simulation keys are cleared immediately upon first evaluation, preventing false positives on subsequent normal launches.
   - Invariant 4: Native PyQt6 GUI falls back gracefully to the interactive terminal menu if PyQt6 is not installed on the developer workstation.

---

## 1. Executive Summary

This deliverable fulfills Stage 5 verification for **ATT-3377**. Prior to ATT-3377, testing Low Memory Kills (LMK) and Permission Revocation dialogs at the desk was hindered because Android's kernel `lmkd` daemon cannot be triggered directly from user-space ADB, causing killed processes to be attributed to `REASON_SIGNALED` and re-classified as `BATTERY_KILL`. Furthermore, `tools/simulate_kill.py` spawned a browser-based UI rather than adhering to the project's native PyQt6 desktop GUI standard (`tools/replay_workout.py`).

ATT-3377 resolves these limitations by:
1. **Debug Injection Channel**: In `BuildConfig.DEBUG` only, `ProcessExitReasonHelper.resolveKillReason()` checks SharedPreferences for `pref_debug_simulated_kill_reason` (`LOW_MEMORY`, `PERMISSION_REVOKED`, `BATTERY_KILL`, `GENERIC`), mapping directly to the appropriate `KillReason`.
2. **Idempotent Single-Use Consumption**: The simulated preference is immediately cleared upon reading, preventing unintended re-triggering upon activity recreation or configuration changes.
3. **Release Isolation**: In release builds (`BuildConfig.DEBUG == false`), the injection channel is completely bypassed, ensuring zero impact on production stability or security.
4. **ADB Tooling Integration**: `tools/simulate_kill.py` injects simulated preferences via `adb shell run-as` before issuing termination commands for LMK, Battery, and Permission simulations.
5. **Native PyQt6 Desktop GUI**: `simulate_kill.py --gui` launches an obsidian-dark-themed PyQt6 desktop application with live device status cards, one-click action buttons, escalation controls, and real-time logs, with automatic fallback to the terminal menu if PyQt6 is unavailable.

---

## 2. Verification Results

### 2.1 Targeted Unit & Contract Testing

#### `ProcessExitReasonHelperTest` (JVM Unit Tests)
- `testResolveKillReason_simulatedLowMemory_returnsLowMemoryAndClearsPreference`: **PASSED** (Injected `"LOW_MEMORY"` evaluates to `KillReason.LOW_MEMORY` and removes preference immediately).
- `testResolveKillReason_simulatedPermissionRevoked_returnsPermissionRevokedAndClearsPreference`: **PASSED** (Injected `"PERMISSION_REVOKED"` evaluates to `KillReason.PERMISSION_REVOKED` and removes preference).
- `testResolveKillReason_simulatedBatteryKill_returnsBatteryKillAndClearsPreference`: **PASSED** (Injected `"BATTERY_KILL"` evaluates to `KillReason.BATTERY_KILL` and removes preference).
- `testResolveKillReason_simulatedGeneric_returnsGenericUnfinishedAndClearsPreference`: **PASSED** (Injected `"GENERIC"` evaluates to `KillReason.GENERIC_UNFINISHED` and removes preference).
- `testResolveKillReason_simulatedReason_isIdempotent`: **PASSED** (First invocation consumes preference; second invocation falls back to standard evaluation).
- `testResolveKillReason_releaseBuild_ignoresSimulatedPreference`: **PASSED** (With `isDebug = false`, simulated preference is completely ignored).
- All 15 existing and new `ProcessExitReasonHelperTest` test cases passed with 100% success rate.

#### `tools/test_simulate_kill.py` (Python Controller & GUI Contract Tests)
- `test_cli_help`: **PASSED**
- `test_cli_inject_reason`: **PASSED**
- `test_cli_kill_battery`: **PASSED**
- `test_cli_kill_lmk`: **PASSED**
- `test_cli_kill_perm`: **PASSED**
- `test_cli_kill_sigkill`: **PASSED**
- `test_cli_reset`: **PASSED**
- `test_controller_inject_simulated_kill_reason`: **PASSED** (Verifies XML preference editing and `adb shell run-as` dispatch).
- `test_controller_kill_battery_simulation_injects_reason`: **PASSED**
- `test_controller_kill_lmk_simulation_injects_reason`: **PASSED**
- `test_controller_kill_permission_simulation_injects_reason`: **PASSED**
- `test_gui_instantiation`: **PASSED** (Verifies `KillSimulatorWindow` components, status cards, and layout).
- `test_gui_without_pyqt6_fallback`: **PASSED** (Verifies graceful fallback message when PyQt6 is missing).
- `test_terminal_menu_display`: **PASSED**
- All 14 test cases in `tools/test_simulate_kill.py` passed in 1.397s.

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (100% pass rate, zero failures, 0 regressions across entire suite).

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-STB-015` updated to `Verified`.
- **`docs/tests.md`**: `TST-STB-015` updated to `Verified`.
- **Governance Check**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.1` -> Clean pass (net-new requirement `REQ-STB-015` verified).

---

## 4. Subtask & Ticket Advancement Plan

1. Subtask **ATT-3426** (`[Test]`) description updated with this walkthrough.
2. Subtask moved to `In Überprüfung` and Gate 5 audit initiated via `python3 tools/review_agent.py audit ATT-3426`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt` via transition `freigabe`.
4. Parent ticket **ATT-3377** stepped through lifecycle transitions to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3377`.
6. Branch `feature/ATT-3377` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3377` deleted.
