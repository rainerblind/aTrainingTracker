# Stage 3: Implementation Plan - ATT-6506: Enforce Android Emulator (emulator-5554) with Auto-Boot, Golden DB Seeding & Physical Guardrail

**Ticket**: [ATT-6506](https://rainerblind.atlassian.net/browse/ATT-6506)  
**Sub-task**: [ATT-6749](https://rainerblind.atlassian.net/browse/ATT-6749) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6506`  
**Requirement Mapping**: `REQ-TST-004` (`REQ-TST-004.1`--`REQ-TST-004.6`) | **Test Mapping**: `TST-TST-004` (`TST-TST-004.1`--`TST-TST-004.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Autonomous agents executing in-sprint verification (Stages 1–5) and simulation scripts (`fake_gps.py`, `simulate_kill.py`, `replay_workout.py`) currently resolve ADB devices via `devices[0]`, targeting the PO's physical daily driver (`Pixel 10`, `66020DLCR002FL`). Legacy Rule 79 (`ATT-5890`) prohibited booting software emulators. This plan establishes the Two-Tier Device Policy: autonomous agents exclusively use a managed, data-seeded Android Emulator (`emulator-5554`, `Pixel_8_Android_15`) during Stages 1–5, while reserving physical hardware deployment strictly for full-sprint evaluation at Ceremony 2 (Sprint Review).

---

## 2. Traceability & Requirements Mapping
* **REQ-TST-004.1** (`ensure`): `tools/agent_emulator.py` launches `Pixel_8_Android_15` on `emulator-5554`, waits for `sys.boot_completed == 1`, unlocks keyguard, and disables animations (`0.0`).
* **REQ-TST-004.2** (`configure-app`): Grants location/notification/BLE permissions and sets `mock_location allow` on `com.atrainingtracker.debug`.
* **REQ-TST-004.3** (`seed-db` / `sync-from-physical`): Pulls/pushes SQLite databases via `run-as com.atrainingtracker.debug` without touching physical device user data.
* **REQ-TST-004.4** (*Guardrail*): `tools/fake_gps.py`, `simulate_kill.py`, and `replay_workout.py` prioritize `emulator-*` and reject physical USB serials when `--agent` or `ATT_AGENT_MODE=1` is set.
* **REQ-TST-004.5** (*Governance*): `.agents/rules/aspice_governance.md` (superseding Rule 79), `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md`, and `docs/project_protocol.md` mandate `emulator-5554` for Stages 1–5 and physical hardware for Ceremony 2.
* **REQ-TST-004.6** (*Invariants*): Zero changes to `app/src/main/`; Python 3 standard library only (`TST-PRO-005.6`).
* **Test Mapping**: `TST-TST-004.1` (Device parsing), `TST-TST-004.2` (Guardrail), `TST-TST-004.3` (Lifecycle/config), `TST-TST-004.4` (DB seeding), `TST-TST-004.5` (Governance parity), `TST-TST-004.6` (Stdlib AST audit).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Risk**: `app/src/main/` remains 100% untouched.
2. **Physical Device Shielding**: Zero agent writes/deployments to physical USB serials during Stages 1–5.
3. **Ceremony 2 Hardware Invariant**: Full-sprint integration evaluation preserved on physical Pixel 10.
4. **Python Stdlib Invariant (`TST-PRO-005.6`)**: Zero external pip dependencies in `tools/agent_emulator.py`.
5. **UI Consistency**: No UI changes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`tools/agent_emulator.py`**: Modular Python 3 CLI implementing `AdbDeviceManager`, `EmulatorController`, and `DatabaseSeeder`.
* **`tools/fake_gps.py`, `simulate_kill.py`, `replay_workout.py`**: Add shared device resolution logic `resolve_target_device()` enforcing emulator precedence and physical guardrail in agent mode.
* **Governance & Skills**: Update `.agents/rules/aspice_governance.md` Rule 7, `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md`, `docs/project_protocol.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Check
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` ensuring exit code `0` (`GATE_PASSED`).

### Step 2: Implement `tools/agent_emulator.py`
* Implement device listing, auto-boot with `~/Android/Sdk/emulator/emulator`, `sys.boot_completed` polling, keyguard unlock, animator scale zeroing (`0.0`), permission grant, and database seeding via `run-as`.

### Step 3: Implement Physical-Phone Guardrail in Developer Tools
* Update `tools/fake_gps.py`, `tools/simulate_kill.py`, and `tools/replay_workout.py` with `--agent` / `ATT_AGENT_MODE` physical device protection.

### Step 4: Codify Two-Tier Policy in Governance Rules & Agent Skills
* Update `.agents/rules/aspice_governance.md` (superseding Rule 79), `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md`, and `docs/project_protocol.md`.

### Step 5: Author Unit Test Suite `tools/test_agent_emulator.py`
* Implement unit tests validating `TST-TST-004.1` through `TST-TST-004.6` using `unittest` and `unittest.mock`.

### Step 6: Verification & Governance
* Execute `python3 -m unittest tools/test_agent_emulator.py` and `python3 tools/verify_skills.py`.
* Execute `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Clean run of `test_agent_emulator.py`, `verify_skills.py`, and `verify_requirement_governance.py`.
* **Rollback**: Atomic squash-merge hygiene ensures clean branch deletion or git revert without residue.
