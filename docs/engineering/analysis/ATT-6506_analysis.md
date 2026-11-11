# Stage 1 Analysis: ATT-6506 - Enforce Android Emulator (emulator-5554) for autonomous agent verification with auto-boot, golden DB seeding, and physical-phone guardrail

**Ticket**: [ATT-6506](https://rainerblind.atlassian.net/browse/ATT-6506)  
**Sub-task**: [ATT-6747](https://rainerblind.atlassian.net/browse/ATT-6747) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6506`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-TST-004` (`docs/requirements/req_process_stability.md`)  
**Test Mapping**: `TST-TST-004` (`docs/tests/tst_process_stability.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Autonomous agents executing Phase 2 per-ticket verification (Stages 1–5) and desk simulation tools (`fake_gps.py`, `simulate_kill.py`, `replay_workout.py`) currently resolve ADB devices by picking `devices[0]`, which targets the PO's connected physical phone (`Pixel 10`, serial `66020DLCR002FL`). Legacy Rule 79 (`ATT-5890`) prohibited booting software emulators.
* **Defect / Disruption**: Autonomous agent deployments, process-kill triggers, mock GPS injections, and UI tap automations hijack the PO's physical daily driver device during in-sprint execution.
* **PO Decision / Two-Tier Device Policy**:
  1. *In-Sprint Per-Ticket Validation (Stages 1–5)*: Autonomous agents MUST exclusively target an emulated phone (`emulator-5554`, `Pixel_8_Android_15`) with auto-boot, permission grant, and golden DB seeding.
  2. *Full-Sprint Evaluation (Ceremony 2 — Sprint Review)*: The integrated sprint build MUST be deployed to and evaluated on the **real physical phone** (`Pixel 10`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `tools/fake_gps.py:244`, `tools/simulate_kill.py:153`, and `tools/replay_workout.py:543` naively select `devices[0]` without differentiating emulators from physical hardware, lacking an agent-mode protection guardrail.
  * `.agents/rules/aspice_governance.md` Rule 7 and `.agents/skills/stage5-verification/SKILL.md` invoke `./gradlew installDebug` and `adb` without `ANDROID_SERIAL=emulator-5554`, while legacy Rule 79 banned software emulator launches.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified `~/Android/Sdk/emulator/emulator -list-avds` contains target AVDs `Pixel_8_Android_15` and `Pixel_7_Android_36`.
  * Verified `adb devices -l` shows `66020DLCR002FL` (`Pixel 10`) connected; without serial gating, tools immediately hijack the physical phone.
  * Verified `run-as com.atrainingtracker.debug` allows non-destructive database extraction from the physical phone to create seeded snapshots.
  * Verified `Sprint_Review_and_Retro_ATT-5890.md` (Finding 3): Rule 79 was a reaction to an unmanaged, CPU-hogging headless QEMU launch (`ATT-5811`). A structured Python lifecycle tool with boot checks and timeouts eliminates that risk safely.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Scale (3 SP)
* **In-Scope Goals**:
  1. Author `tools/agent_emulator.py` (CLI: `ensure`, `configure-app`, `seed-db`) using Python 3 stdlib to manage `emulator-5554`.
  2. Implement physical-phone guardrail in `tools/fake_gps.py`, `tools/simulate_kill.py`, `tools/replay_workout.py` (default to emulator when present; abort on physical device if `--agent` / `ATT_AGENT_MODE=1` is set).
  3. Align `.agents/rules/aspice_governance.md` (superseding Rule 79), `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md`, and `docs/project_protocol.md` with the Two-Tier Device Policy.
  4. Author unit test suite `tools/test_agent_emulator.py` verifying device parsing, guardrails, and CLI command generation.
* **Out-of-Scope Non-Goals**: Modifying Android application runtime code (`app/src/main/`); cloud device farms; automated AVD creation/download.
* **Complexity Scale**: 3 SP (Complex multi-tool and governance integration; strictly within the $\le 3$ SP boundary).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement / Rule**: Legacy Rule 79 (`ATT-5890`, commit `51f4c3c5d8e4ad76ccd7afa8fc70397a5fb410c1`).
* **Root Reason for Existing Formulation**: In `ATT-5811`, an agent launched `qemu-system-x86_64-headless` with software rendering (`swiftshader_indirect`), freezing CPU at 92% until killed by the watchdog.
* **Preservation of Core Invariants**: Replaces the blanket ban with managed, hardware-accelerated lifecycle control (`tools/agent_emulator.py`), boot timeouts (`sys.boot_completed`), and animation suppression (`0.0`), preventing CPU hangs while establishing an absolute physical-phone guardrail.
* **Side-Effect Audit (Rule 68)**: Zero production impact on app code; preserves physical device deployment at Ceremony 2.

---

## 5. Architectural Strategy & High-Level Solution
* **`tools/agent_emulator.py`**:
  * `ensure`: Launches `~/Android/Sdk/emulator/emulator -avd Pixel_8_Android_15` if inactive, polls `sys.boot_completed`, unlocks keyguard (`input keyevent 82`), sets animations to `0.0`.
  * `configure-app`: Grants location/notification/BLE permissions and sets `mock_location allow`.
  * `seed-db`: Pushes golden/extracted SQLite databases (`WorkoutSummaries.db`, `RouteClusters.db`, etc.) into `com.atrainingtracker.debug` via `run-as`.
* **Tool Guardrail**: Add `resolve_target_device(device_serial, agent_mode)` in `tools/` simulators, enforcing `emulator-*` target.
* **Two-Tier Governance**: Mandate `ANDROID_SERIAL=emulator-5554` for Stages 1–5; mandate physical phone for Ceremony 2.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero production code changes (`app/src/main/` 100% untouched).
  2. Physical phone protected against autonomous agent access in Phase 2; Ceremony 2 physical evaluation strictly preserved.
  3. Python standard library compliance (`TST-PRO-005.6`).
* **Risk Rating**: **LOW** — Developer tooling, testing infrastructure, and governance refinement with zero app runtime regressions.
