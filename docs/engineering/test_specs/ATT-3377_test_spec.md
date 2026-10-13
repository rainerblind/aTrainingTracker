# Stage 2: Requirement & Test Specification - ATT-3377: Support simulated exit reason injection and PyQt6 desktop GUI for process kill simulator

**Ticket**: [ATT-3377](https://rainerblind.atlassian.net/browse/ATT-3377)  
**Sub-task**: [ATT-3423](https://rainerblind.atlassian.net/browse/ATT-3423) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-STB-015` (net-new requirement)  
**Test Mapping**: `TST-STB-015` (net-new test specification)  
**Branch**: `feature/ATT-3377`  
**Author**: AI Agent 1 (Requirement & Test Specification Engineer)  
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

## 1. Functional Requirement Specification

### REQ-STB-015: Simulated Process Exit Reason Injection & Native PyQt6 Desktop Kill Simulator Architecture

*The system SHALL provide a debug-only exit reason simulation channel and a native PyQt6 desktop GUI in developer testing tools to enable deterministic verification of process termination dialogs at the desk without physical RAM starvation (ATT-3377, extending `REQ-STB-012` and `ATT-2469`):*

1. **Debug Exit Reason Injection Channel (`ProcessExitReasonHelper.kt`)**:
   - In `BuildConfig.DEBUG` only, `ProcessExitReasonHelper.resolveKillReason` SHALL inspect `SharedPreferences` for the simulation preference key:
     - Key: `pref_debug_simulated_kill_reason` (`String`)
     - Supported Values: `"LOW_MEMORY"`, `"PERMISSION_REVOKED"`, `"BATTERY_KILL"`, `"GENERIC"`
   - When the preference is present in debug mode:
     - The helper SHALL map `"LOW_MEMORY"` to `KillReason.LOW_MEMORY`.
     - The helper SHALL map `"PERMISSION_REVOKED"` to `KillReason.PERMISSION_REVOKED`.
     - The helper SHALL map `"BATTERY_KILL"` to `KillReason.BATTERY_KILL`.
     - The helper SHALL map `"GENERIC"` to `KillReason.GENERIC_UNFINISHED`.
     - The helper SHALL immediately remove the key (`remove(PREF_DEBUG_SIMULATED_KILL_REASON).apply()`) upon reading to guarantee strict single-use idempotency.
   - In release builds (`BuildConfig.DEBUG == false`), the simulation channel MUST be completely bypassed and stripped, relying exclusively on system `ApplicationExitInfo`.

2. **Simulator Tool ADB Injection Controller (`tools/simulate_kill.py`)**:
   - `simulate_kill.py` SHALL implement `inject_simulated_kill_reason(package, reason)` using `adb shell run-as <package>` to write the preference to the app's default SharedPreferences XML file.
   - In `kill_lmk_simulation()`:
     - The tool SHALL inject `pref_debug_simulated_kill_reason = "LOW_MEMORY"` into app preferences prior to terminating the process via `kill -9`.
   - In `kill_battery_simulation()`:
     - The tool SHALL optionally inject `pref_debug_simulated_kill_reason = "BATTERY_KILL"`.
   - In `kill_permission_simulation()`:
     - The tool SHALL revoke the permission and inject `pref_debug_simulated_kill_reason = "PERMISSION_REVOKED"`.
   - The CLI SHALL support direct injection via `--inject-reason <REASON>` and execute LMK injection via `--kill lmk`.

3. **Native PyQt6 Desktop GUI Architecture (`tools/simulate_kill.py`)**:
   - The `--gui` mode SHALL be converted from a local HTTP browser server to a native PyQt6 desktop application matching the architectural design and styling of `tools/replay_workout.py`:
     - Theme: Obsidian dark theme palette (`#121212` background, `#1E1E1E` surface, `#2A2A2A` cards, `#BB86FC` primary purple accent, `#03DAC6` secondary teal, `#CF6679` error/kill red).
     - Live Status Cards: High-glanceability cards for Connected Device, App State, Process PID, Battery Whitelist Status, Permission Status, Battery Kill Escalation Count, and Last Historical OS Exit Record.
     - Quick Action Triggers: One-click buttons executing background worker threads for:
       * *Simulate Low Memory Kill (LMK)* (injects `"LOW_MEMORY"` + terminates process)
       * *Simulate Battery Kill* (Stage 1..3)
       * *Simulate Permission Kill* (revokes permission + injects `"PERMISSION_REVOKED"`)
       * *Raw SIGKILL* (`kill -9`)
       * *Toggle Battery Whitelist* (`dumpsys deviceidle whitelist +/-`)
       * *Toggle Permissions* (`pm grant/revoke`)
       * *Relaunch App* (`am start -n ...`)
       * *Clean Reset* (clears escalation count and preferences)
     - Log Window: Real-time scrolling output log for ADB actions and diagnostics.
   - Fallback Protection: If `PyQt6` is not installed on the host system, the tool SHALL output an advisory notice and fall back cleanly to the interactive terminal menu (`TerminalKeyReader`) without raising unhandled import exceptions.

---

## 2. Acceptance Criteria (Given-When-Then)

- **AC-1 (Deterministic Low Memory Simulation)**:
  - *Given* a debug build of aTrainingTracker running on a connected test device,
  - *When* executing `python3 tools/simulate_kill.py --kill lmk` (or clicking *Low Memory Kill* in the PyQt6 GUI),
  - *Then* `pref_debug_simulated_kill_reason = "LOW_MEMORY"` is injected, the process is terminated, and upon relaunch `StartOrResumeDialog` renders the memory chip icon (`Icons.Default.Memory`) and title `"Training unterbrochen: Speicherengpass"`.

- **AC-2 (Single-Use Idempotent Consumption)**:
  - *Given* an injected simulated kill reason in SharedPreferences,
  - *When* `ProcessExitReasonHelper.resolveKillReason()` executes during app startup,
  - *Then* the simulated reason is returned, and `pref_debug_simulated_kill_reason` is immediately removed from SharedPreferences so that subsequent activity recreations or normal app restarts do not re-trigger the simulated reason.

- **AC-3 (Release Build Isolation)**:
  - *Given* a release build (`BuildConfig.DEBUG == false`),
  - *When* `pref_debug_simulated_kill_reason` is manually present in SharedPreferences,
  - *Then* `ProcessExitReasonHelper` ignores the preference and evaluates only standard system `ApplicationExitInfo`.

- **AC-4 (Native PyQt6 Desktop GUI)**:
  - *Given* a workstation with `PyQt6` installed,
  - *When* executing `python3 tools/simulate_kill.py --gui`,
  - *Then* a native desktop window opens with the obsidian dark theme, live status cards, and action buttons without starting an HTTP server or opening a web browser.

- **AC-5 (PyQt6 Fallback to Terminal)**:
  - *Given* an environment where `PyQt6` is not installed,
  - *When* executing `python3 tools/simulate_kill.py --gui`,
  - *Then* the tool logs a warning and falls back to the interactive terminal menu without crashing.

---

## 3. Test Specification (`TST-STB-015`)

### Test Matrix

| Test ID | Target Component | Test Description | Verification Method |
|---|---|---|---|
| `TST-STB-015-1` | `ProcessExitReasonHelperTest.kt` | Debug simulated exit reason injection for LOW_MEMORY, PERMISSION_REVOKED, BATTERY_KILL, GENERIC | Unit Test (Robolectric / MockContext) |
| `TST-STB-015-2` | `ProcessExitReasonHelperTest.kt` | Idempotent preference removal after single consumption | Unit Test |
| `TST-STB-015-3` | `ProcessExitReasonHelperTest.kt` | Release build bypass (`isDebug = false`) | Unit Test |
| `TST-STB-015-4` | `tools/test_simulate_kill.py` | ADB XML injection command generation & execution contract | Python Contract Test |
| `TST-STB-015-5` | `tools/test_simulate_kill.py` | PyQt6 GUI window structure and button wiring contract | Python Contract Test |
| `TST-STB-015-6` | Clean-Room Full Suite | `./gradlew testDebugUnitTest` regression suite | Gradle clean-room run |

---

## 4. Bidirectional Traceability Matrix

| Requirement ID | Test ID | Target Component | Status |
|---|---|---|---|
| `REQ-STB-015.1` | `TST-STB-015-1`, `TST-STB-015-2`, `TST-STB-015-3` | `ProcessExitReasonHelper.kt` | Specified |
| `REQ-STB-015.2` | `TST-STB-015-4` | `tools/simulate_kill.py` | Specified |
| `REQ-STB-015.3` | `TST-STB-015-5` | `tools/simulate_kill.py` | Specified |
| System Safety | `TST-STB-015-6` | Project test suite | Specified |
