# Stage 3: Implementation Plan - ATT-3377: Support simulated exit reason injection and PyQt6 desktop GUI for process kill simulator

**Ticket**: [ATT-3377](https://rainerblind.atlassian.net/browse/ATT-3377)  
**Sub-task**: [ATT-3424](https://rainerblind.atlassian.net/browse/ATT-3424) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-STB-015`  
**Test Mapping**: `TST-STB-015`  
**Branch**: `feature/ATT-3377`  
**Author**: AI Agent 1 (Software Construction Planner)  
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

## 1. Architectural Decomposition (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        tools/simulate_kill.py                          │
│                                                                        │
│   ┌─────────────────────────────┐    ┌─────────────────────────────┐   │
│   │   KillSimulatorWindow       │    │     TerminalKeyReader       │   │
│   │   (PyQt6 Desktop GUI)       │    │     (Interactive TUI)       │   │
│   │   - Obsidian Dark Theme     │    │     - Fallback if no PyQt6  │   │
│   │   - Live Status Cards       │    │     - Single keypress menu  │   │
│   │   - Action Trigger Buttons  │    └──────────────┬──────────────┘   │
│   └──────────────┬──────────────┘                   │                  │
│                  └────────────────┬─────────────────┘                  │
│                                   ▼                                    │
│                    KillSimulatorController                             │
│                    - inject_simulated_kill_reason(reason)              │
│                    - kill_lmk_simulation() (injects LOW_MEMORY)        │
│                    - kill_battery_simulation() (injects BATTERY_KILL)  │
│                    - kill_permission_simulation() (injects PERMISSION) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (ADB run-as SharedPreferences XML)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│             Android App (ProcessExitReasonHelper.kt)                   │
│                                                                        │
│   resolveKillReason(context, sdkInt, isDebug)                          │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ if (isDebug) {                                                 │   │
│   │     val sim = prefs.getString(PREF_DEBUG_SIMULATED_KILL_REASON)│   │
│   │     if (!sim.isNullOrBlank()) {                                │   │
│   │         prefs.edit().remove(...).apply()                       │   │
│   │         return mapSimulatedReason(sim)                         │   │
│   │     }                                                          │   │
│   │ }                                                              │   │
│   │ // Fallback to normal ApplicationExitInfo evaluation           │   │
│   └────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Boundaries & Responsibilities:

1. **App Diagnostic Layer (`ProcessExitReasonHelper.kt`)**:
   - Constant: `const val PREF_DEBUG_SIMULATED_KILL_REASON = "pref_debug_simulated_kill_reason"`.
   - Method signature:
     ```kotlin
     fun resolveKillReason(
         context: Context,
         sdkInt: Int = Build.VERSION.SDK_INT,
         isDebug: Boolean = com.atrainingtracker.trainingtracker.BuildConfig.DEBUG
     ): KillDiagnosis
     ```
   - Prioritized check: When `isDebug == true`, inspect SharedPreferences for `PREF_DEBUG_SIMULATED_KILL_REASON`.
   - If present:
     - Clear key immediately (`prefs.edit().remove(PREF_DEBUG_SIMULATED_KILL_REASON).apply()`).
     - Map `"LOW_MEMORY"` -> `KillReason.LOW_MEMORY` (`escalationLevel = 0`, `shouldShowBatteryButton = false`).
     - Map `"PERMISSION_REVOKED"` -> `KillReason.PERMISSION_REVOKED` (`escalationLevel = 0`, `shouldShowBatteryButton = false`).
     - Map `"BATTERY_KILL"` -> `KillReason.BATTERY_KILL` (triggers standard battery escalation ladder).
     - Map `"GENERIC"` -> `KillReason.GENERIC_UNFINISHED`.
   - If absent or `isDebug == false`: evaluate standard `ApplicationExitInfo` query and `isIgnoringBatteryOptimizations`.

2. **Tool ADB Controller Layer (`tools/simulate_kill.py`)**:
   - `inject_simulated_kill_reason(reason: str)`:
     - Pulls `/data/data/{self.package_name}/shared_prefs/{self.package_name}_preferences.xml` via `run-as`.
     - Adds/updates `<string name="pref_debug_simulated_kill_reason">VALUE</string>`.
     - Pushes updated XML back via `run-as sh -c 'echo ...'`.
   - `kill_lmk_simulation()`:
     - Calls `inject_simulated_kill_reason("LOW_MEMORY")`.
     - Executes `am send-trim-memory` and `kill -9` fallback.
   - `kill_battery_simulation()`:
     - Un-whitelists app.
     - Calls `inject_simulated_kill_reason("BATTERY_KILL")`.
     - Executes SIGKILL.
   - `kill_permission_simulation()`:
     - Calls `inject_simulated_kill_reason("PERMISSION_REVOKED")`.
     - Revokes fine location permission via `pm revoke`.
   - CLI flags:
     - `--inject-reason {LOW_MEMORY,PERMISSION_REVOKED,BATTERY_KILL,GENERIC}`
     - `--kill lmk` (injects LMK)
     - `--kill battery` (injects Battery)
     - `--kill permission` (injects Permission)

3. **Tool Native Desktop GUI Layer (`tools/simulate_kill.py`)**:
   - Class `KillSimulatorWindow(QMainWindow)`:
     - Stylized matching `tools/replay_workout.py`: obsidian palette (`#121212`, `#1E1E1E`, `#2A2A2A`, `#BB86FC`, `#03DAC6`, `#CF6679`).
     - Real-time QTimer (1.0s) polling `ctrl.get_full_status()`.
     - Status cards:
       * Device & App Status (Serial, Package, Running PIDs)
       * Battery Whitelist & Permissions (Optimized/Exempt, Location, Notifications)
       * Escalation Level (Count, Stage 1/2/3)
       * Last ApplicationExitInfo (Reason, Subreason, Description, Timestamp)
     - Trigger Actions:
       * Battery Kill (1-click)
       * Low Memory Kill (1-click)
       * Permission Kill (1-click)
       * SIGKILL (1-click)
       * Toggle Whitelist
       * Toggle Permission
       * Direct Stage selector buttons (0, 1, 2, 3)
       * Launch App (`am start`)
       * Clean Reset
     - Action Log / Console panel with timestamps.
   - Safe Fallback:
     - If `HAS_PYQT6 == False`, print:
       `"[WARN] PyQt6 is not installed on this system. Falling back to interactive terminal menu."`
       and invoke `run_interactive_menu(ctrl)`.

---

## 2. UI Consistency (Rule 23)

- **Closest Reference Screen**: `tools/replay_workout.py` (Desktop GUI window `ReplayGuiWindow`).
- **Reused Components & Layouts**:
  - `QMainWindow` with dark palette stylesheet (`#121212` background, `#1E1E1E` surface cards, `#2A2A2A` buttons).
  - Status card grid layout (`create_card(title, value, unit)`).
  - Primary action buttons with Material-derived color accents (`#BB86FC` primary purple, `#03DAC6` teal, `#CF6679` danger red).
- **Theme Tokens**:
  - Window Background: `#121212`
  - Card Surface: `#1E1E1E`
  - Card Border: `#333333`
  - Text Primary: `#E0E0E0`
  - Text Dim: `#888888`
  - Accent / Selection: `#BB86FC`
  - Success / Active: `#03DAC6`
  - Danger / Kill: `#CF6679`
- **Zero Ad-Hoc Styling**: Matches the design system of project desktop tools established in ATT-2466 (`replay_workout.py`).

---

## 3. Atomic Step Sequencing

### Step 1: Implement Debug Injection Channel in `ProcessExitReasonHelper.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/helpers/ProcessExitReasonHelper.kt`
- Add `const val PREF_DEBUG_SIMULATED_KILL_REASON = "pref_debug_simulated_kill_reason"`.
- Update `resolveKillReason` with optional `isDebug: Boolean = BuildConfig.DEBUG`.
- Check and consume `PREF_DEBUG_SIMULATED_KILL_REASON` at start of `resolveKillReason`.
- Map simulated string values to `KillReason`.

### Step 2: Unit Tests in `ProcessExitReasonHelperTest.kt`
- File: `app/src/test/java/com/atrainingtracker/trainingtracker/helpers/ProcessExitReasonHelperTest.kt`
- Test `LOW_MEMORY` injection returns `KillReason.LOW_MEMORY` and clears pref.
- Test `PERMISSION_REVOKED` injection returns `KillReason.PERMISSION_REVOKED` and clears pref.
- Test `BATTERY_KILL` injection returns `KillReason.BATTERY_KILL`.
- Test `GENERIC` injection returns `KillReason.GENERIC_UNFINISHED`.
- Test release build isolation (`isDebug = false`) ignores injected preference.

### Step 3: Implement ADB Injection in `tools/simulate_kill.py`
- File: `tools/simulate_kill.py`
- Add `inject_simulated_kill_reason(reason: str)` to `KillSimulatorController`.
- Wire `kill_lmk_simulation()` to call `inject_simulated_kill_reason("LOW_MEMORY")`.
- Wire `kill_battery_simulation()` to call `inject_simulated_kill_reason("BATTERY_KILL")`.
- Wire `kill_permission_simulation()` to call `inject_simulated_kill_reason("PERMISSION_REVOKED")`.
- Add CLI argument `--inject-reason`.

### Step 4: Implement Native PyQt6 Desktop GUI in `tools/simulate_kill.py`
- File: `tools/simulate_kill.py`
- Import PyQt6 with safe `try/except ImportError: HAS_PYQT6 = False`.
- Implement `KillSimulatorWindow(QMainWindow)` with obsidian dark theme, live status cards, one-click trigger buttons, and log console.
- In `main()`, route `--gui` to PyQt6 application if `HAS_PYQT6 == True`, otherwise fall back to `run_interactive_menu(ctrl)`.

### Step 5: Unit & Contract Tests in `tools/test_simulate_kill.py`
- File: `tools/test_simulate_kill.py`
- Test `inject_simulated_kill_reason` generates valid XML run-as command.
- Test `kill_lmk_simulation` calls injection before termination.
- Test `KillSimulatorWindow` instantiates with expected buttons and status cards when PyQt6 is present.
- Test CLI `--inject-reason` argument parsing.

### Step 6: Full Regression Suite Verification
- Command: `./gradlew testDebugUnitTest` and `python3 tools/test_simulate_kill.py`.

---

## 4. Invariant Protection & Rollback Safety

1. **Release Build Isolation Invariant**: `BuildConfig.DEBUG` strictly gates simulation injection. Production APK release builds are 100% shielded from preference injection.
2. **Idempotence & Single-Use Invariant**: The injected preference is removed immediately upon first reading, preventing stale simulation values on subsequent launches or activity rotations.
3. **Resumption Dialog Integrity Invariant**: `StartOrResumeDialog` contracts and athletic partnership copy (`REQ-UI-329`, `REQ-UI-345`, `REQ-STB-012`) remain untouched.
4. **Tool Fallback Invariant**: When PyQt6 is unavailable (e.g. headless CI or stripped environments), the tool falls back cleanly to the terminal interface without crashing.
5. **Clean-Room Test Invariant**: 100% unit test pass rate across all project modules.
