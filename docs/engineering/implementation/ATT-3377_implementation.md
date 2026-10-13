# Stage 4: Implementation Summary - ATT-3377: Support simulated exit reason injection and PyQt6 desktop GUI for process kill simulator

**Ticket**: [ATT-3377](https://rainerblind.atlassian.net/browse/ATT-3377)  
**Sub-task**: [ATT-3425](https://rainerblind.atlassian.net/browse/ATT-3425) (`[Implementation]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-STB-015`  
**Test Mapping**: `TST-STB-015`  
**Branch**: `feature/ATT-3377`  
**Author**: AI Agent 1 (Software Construction Engineer)  
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

## 1. Summary of Changes

### 1.1 `ProcessExitReasonHelper.kt` (Debug Exit Reason Injection Channel)
- Added constant `const val PREF_DEBUG_SIMULATED_KILL_REASON = "pref_debug_simulated_kill_reason"`.
- Updated `resolveKillReason(context, sdkInt, isDebug)` signature with default `isDebug: Boolean = com.atrainingtracker.BuildConfig.DEBUG`.
- In `resolveKillReason`, when `isDebug == true`:
  - Inspects `SharedPreferences` for `PREF_DEBUG_SIMULATED_KILL_REASON`.
  - When present, immediately removes the key to guarantee strict single-use idempotency.
  - Maps `"LOW_MEMORY"` -> `KillReason.LOW_MEMORY`, `"PERMISSION_REVOKED"` -> `KillReason.PERMISSION_REVOKED`, `"BATTERY_KILL"` -> `KillReason.BATTERY_KILL`, and `"GENERIC"` -> `KillReason.GENERIC_UNFINISHED`.
  - Marks `isSimulated = true`, preventing simulated generic/low memory/permission kills from being erroneously reclassified to battery kills.
- Release builds (`isDebug == false`) completely bypass the simulation channel.

### 1.2 `tools/simulate_kill.py` (ADB Injection & Native PyQt6 Desktop GUI)
- Imported PyQt6 (`QApplication`, `QMainWindow`, `QWidget`, `QVBoxLayout`, `QHBoxLayout`, `QGridLayout`, `QPushButton`, `QLabel`, `QFrame`, `QTextEdit`, `QTimer`, `QFont`) with safe fallback `HAS_PYQT6`.
- Implemented `inject_simulated_kill_reason(self, reason: str)` in `KillSimulatorController` using `adb shell run-as <package>` to add or update `pref_debug_simulated_kill_reason` in `/data/data/{pkg}/shared_prefs/{pkg}_preferences.xml`.
- Integrated injection into simulation actions:
  - `kill_lmk_simulation()`: calls `inject_simulated_kill_reason("LOW_MEMORY")` before trim memory and kill.
  - `kill_battery_simulation()`: calls `inject_simulated_kill_reason("BATTERY_KILL")`.
  - `kill_permission_simulation()`: calls `inject_simulated_kill_reason("PERMISSION_REVOKED")`.
  - `reset_kill_counter()`: removes `pref_debug_simulated_kill_reason` along with kill counts.
- Implemented `KillSimulatorWindow(QMainWindow)` matching the obsidian dark theme of `tools/replay_workout.py`:
  - Real-time periodic status refresh timer (1000ms).
  - High-glanceability status cards: Process State, Running PIDs, Battery Whitelist, Permissions, Escalation Ladder, Last Exit Reason.
  - One-click triggers for LMK, Battery Kill, Permission Kill, SIGKILL, Whitelist Toggle, Permission Toggle, Stage selectors (0..3), App Launch, and Clean Reset.
  - Dedicated real-time action log console.
- In `main()`:
  - Added CLI flag `--inject-reason {LOW_MEMORY, PERMISSION_REVOKED, BATTERY_KILL, GENERIC}`.
  - Wired `--gui` to launch `KillSimulatorWindow` via PyQt6, with automatic fallback notice to `run_interactive_menu(ctrl)` if PyQt6 is absent.

### 1.3 `ProcessExitReasonHelperTest.kt` (Unit Test Suite)
- Updated mock environment in `setUp()` for `getString`, `putString`, and `remove`.
- Added unit tests:
  - `testClassify_simulatedLowMemory_returnsLowMemoryAndClearsPref`: verifies `LOW_MEMORY` returns `KillReason.LOW_MEMORY`, `escalationLevel = 0`, and removes preference.
  - `testClassify_simulatedPermissionRevoked_returnsPermissionRevokedAndClearsPref`: verifies `PERMISSION_REVOKED` classification and removal.
  - `testClassify_simulatedBatteryKill_returnsBatteryKillAndClearsPref`: verifies `BATTERY_KILL` classification and escalation.
  - `testClassify_simulatedGeneric_returnsGenericUnfinishedAndClearsPref`: verifies `GENERIC` classification.
  - `testClassify_simulatedLowMemory_releaseBuildIgnoresSimulatedPref`: verifies release build (`isDebug = false`) ignores injected preference.

### 1.4 `tools/test_simulate_kill.py` (Contract Test Suite)
- Added tests:
  - `test_inject_simulated_kill_reason`: verifies XML construction and ADB `run-as` execution.
  - `test_kill_lmk_simulation_injects_low_memory`: verifies `LOW_MEMORY` injection call.
  - `test_kill_battery_simulation_injects_battery_kill`: verifies `BATTERY_KILL` injection call.
  - `test_kill_permission_simulation_injects_permission_revoked`: verifies `PERMISSION_REVOKED` injection call.
  - `test_pyqt6_gui_instantiation`: verifies `KillSimulatorWindow` instantiates with all action buttons and status cards.

---

## 2. Verification Results

1. **`ProcessExitReasonHelperTest`**:
   - `testClassify_excessiveResourceUsage_returnsBatteryKill` -> PASSED
   - `testClassify_notIgnoringBatteryOptimizations_returnsBatteryKill` -> PASSED
   - `testClassify_lowMemory_returnsLowMemory` -> PASSED
   - `testClassify_permissionChange_returnsPermissionRevoked` -> PASSED
   - `testClassify_normalGeneric_returnsGenericUnfinished` -> PASSED
   - `testBatteryKillCount_incrementsAndResets` -> PASSED
   - `testResolveKillReason_repeatedCalls_doesNotIncrementCounter` -> PASSED
   - `testResolveKillReason_newExitTimestamp_incrementsCounter` -> PASSED
   - `testOpenBatteryOptimizationSettings_launchesActionRequestIgnoreBatteryOptimizationsFirst` -> PASSED
   - `testClassify_simulatedLowMemory_returnsLowMemoryAndClearsPref` -> PASSED
   - `testClassify_simulatedPermissionRevoked_returnsPermissionRevokedAndClearsPref` -> PASSED
   - `testClassify_simulatedBatteryKill_returnsBatteryKillAndClearsPref` -> PASSED
   - `testClassify_simulatedGeneric_returnsGenericUnfinishedAndClearsPref` -> PASSED
   - `testClassify_simulatedLowMemory_releaseBuildIgnoresSimulatedPref` -> PASSED
   - Total: 14/14 tests PASSED (100% success).

2. **`tools/test_simulate_kill.py`**:
   - Ran 14 tests in 1.420s -> OK (100% pass rate).
