# Stage 2: Requirement & Test Specification - ATT-6506: Enforce Android Emulator (emulator-5554) for autonomous agent verification with auto-boot, golden DB seeding, and physical-phone guardrail

**Ticket**: [ATT-6506](https://rainerblind.atlassian.net/browse/ATT-6506)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6506`  
**Requirement Mapping**: `REQ-TST-004` (*Android Emulator Lifecycle & Two-Tier Policy*)  
**Test Spec ID**: `TST-TST-004` (*Emulator Lifecycle, Guardrail & Governance Suite*)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TST-004)

### 1.1 Problem Statement & Rationale
Autonomous agents executing in-sprint Stage 1–5 verification currently target the PO's connected physical phone (`Pixel 10`), hijacking the device. Autonomous agents SHALL exclusively target `emulator-5554` with auto-boot, permission configuration, and golden DB seeding during Stages 1–5, while reserving physical phone deployment for Ceremony 2 (Sprint Review).

### 1.2 Functional & Architectural Requirements
* `REQ-TST-004.1`: `tools/agent_emulator.py ensure` SHALL detect/launch `Pixel_8_Android_15` on `emulator-5554`, poll `sys.boot_completed == 1`, unlock keyguard, and zero animations (`0.0`).
* `REQ-TST-004.2`: `tools/agent_emulator.py configure-app` SHALL grant runtime permissions (`FINE_LOCATION`, `POST_NOTIFICATIONS`, `BLE_*`) and allow `mock_location` appops on `com.atrainingtracker.debug`.
* `REQ-TST-004.3`: `tools/agent_emulator.py seed-db` SHALL extract/push debug SQLite databases via `run-as com.atrainingtracker.debug` without touching physical device user data.
* `REQ-TST-004.4`: `tools/fake_gps.py`, `simulate_kill.py`, `replay_workout.py` SHALL prioritize `emulator-*` and abort on physical devices when `--agent` / `ATT_AGENT_MODE=1` is set.
* `REQ-TST-004.5`: ASPICE governance and agent skills SHALL enforce `emulator-5554` for Stages 1–5 and physical phone for Ceremony 2.
* `REQ-TST-004.6`: Preservation of system invariants (Python 3 stdlib only, zero production code changes in `app/src/main/`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Auto-Boot & Emulator Setup)**: *Given* `Pixel_8_Android_15` AVD, *When* `agent_emulator.py ensure` runs, *Then* it boots, zeroes animations, and outputs `emulator-5554`.
* **Criterion 2 (Guardrail & Multi-Device Resolution)**: *Given* both phone (`66020DLCR002FL`) and `emulator-5554` attached, *When* agent tools execute, *Then* operations run on `emulator-5554` and physical phone is untouched.
* **Criterion 3 (Two-Tier Device Policy)**: *Given* Phase 2 per-ticket verification vs Ceremony 2 Sprint Review, *When* deploying, *Then* Stages 1–5 use `emulator-5554` and Ceremony 2 uses real physical phone.

### 1.4 System Invariants
* Zero modifications to production source code in `app/src/main/`.
* Connected physical phone protected from unauthorized agent modifications.
* Zero external pip dependencies (Python 3 standard library compliance per `TST-PRO-005.6`).

---

## 2. Test Specification (TST-TST-004)

### Test Case 1: ADB Device Parsing & Multi-Device Prioritization (`TST-TST-004.1`)
* **Scope**: Unit Test (`tools/test_agent_emulator.py::TestAdbDeviceParsing`)
* **Assertions**: Parses `adb devices -l`; prioritizes `emulator-5554` when multiple devices are attached.

### Test Case 2: Physical-Phone Guardrail & Agent Mode Rejection (`TST-TST-004.2`)
* **Scope**: Unit Test (`tools/test_agent_emulator.py::TestPhysicalGuardrail`)
* **Assertions**: Simulators abort on physical USB serial when `--agent` or `ATT_AGENT_MODE=1` is active.

### Test Case 3: Emulator Lifecycle & Configuration Command Generation (`TST-TST-004.3`)
* **Scope**: Unit Test (`tools/test_agent_emulator.py::TestLifecycleAndConfigCommands`)
* **Assertions**: Validates command generation for emulator launch, boot polling, keyguard unlock, animator scale, permission grants, and mock location appops.

### Test Case 4: Database Seeding & Extraction Command Pipeline (`TST-TST-004.4`)
* **Scope**: Unit Test (`tools/test_agent_emulator.py::TestDatabaseSeeding`)
* **Assertions**: Validates `run-as com.atrainingtracker.debug` backup and restore command generation.

### Test Case 5: Governance & Skills Two-Tier Policy Parity (`TST-TST-004.5`)
* **Scope**: Unit Test / Contract Test (`tools/test_agent_emulator.py::TestGovernanceParity`)
* **Assertions**: Validates `.agents/rules/aspice_governance.md`, `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md` mandate `emulator-5554` for Stages 1–5 and physical device for Ceremony 2.

### Test Case 6: Python Standard Library Compliance (`TST-TST-004.6`)
* **Scope**: Static AST Audit (`tools/test_agent_emulator.py::TestStdlibCompliance`)
* **Assertions**: Asserts zero third-party packages imported in `tools/agent_emulator.py`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TST-004.1` | Unit | `tools/agent_emulator.py` | `REQ-TST-004.1`, `REQ-TST-004.4` | Specified |
| `TST-TST-004.2` | Unit / Guardrail | `tools/fake_gps.py`, `simulate_kill.py` | `REQ-TST-004.4` | Specified |
| `TST-TST-004.3` | Unit | `tools/agent_emulator.py` | `REQ-TST-004.1`, `REQ-TST-004.2` | Specified |
| `TST-TST-004.4` | Unit | `tools/agent_emulator.py` | `REQ-TST-004.3` | Specified |
| `TST-TST-004.5` | Contract | Governance rules & agent skills | `REQ-TST-004.5` | Specified |
| `TST-TST-004.6` | Compliance | `tools/agent_emulator.py` | `REQ-TST-004.6`, `TST-PRO-005.6` | Specified |
