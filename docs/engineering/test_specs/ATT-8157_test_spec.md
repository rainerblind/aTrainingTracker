# Stage 2: Requirement & Test Specification - ATT-8157: Simulator Self-Test Scoping & Release Gate Honesty

**Ticket**: [ATT-8157](https://rainerblind.atlassian.net/browse/ATT-8157)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_01_Früh` | **SP**: 2 SP  
**Requirement Mapping**: `REQ-PRO-112` (`REQ-PRO-112.1..3`), `REQ-PRO-068` (`REQ-PRO-068.1..4`), `REQ-PRO-072` (`REQ-PRO-072.1..4`), `REQ-PRO-074` (`REQ-PRO-074.4`)  
**Test Spec Mapping**: `TST-PRO-112` (`TST-PRO-112.1..3`), `TST-PRO-068`, `TST-PRO-072`, `TST-PRO-074`  
**Status**: Planned | **Author**: AI Agent 1 | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-112`, `REQ-PRO-068`, `REQ-PRO-072`, `REQ-PRO-074.4`)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-112.1 / REQ-PRO-068 (`Simulator Self-Test Rename & Rescoping`)**: `tools/run_system_integration_tests.py` and `tools/test_system_integration_tests.py` SHALL be renamed to `tools/run_simulator_self_tests.py` and `tools/test_simulator_self_tests.py` (planned Stage 4 targets), reclassified strictly as pure-Python developer simulator self-tests (covering `ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`), removing constant-arithmetic fake claims (`backoffs`, `(10 - 65530) & 0xFFFF`, `oauth_response`) and `--output-json` flag drift, enforced by `SystemIntegrationTestContractTest.kt`.
* **REQ-PRO-112.2 / REQ-PRO-072 / REQ-PRO-074.4 (`Non-Blocking Release Gate Removal`)**: `system_integration_results.json` SHALL be removed as a blocking SYS.4 release gate in `tools/generate_release_verification_summary.py` (`collect_sys4_summary()`), `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, and `ReleaseManagerSystemEvidenceContractTest.kt`. Release gating remains anchored on unit/`INT-SWE-01..08`, CI `AppSmokeAndroidTest.kt`, `SYS.5` (`sys_verification_record.md`), and `verification_summary.md`.
* **REQ-PRO-112.3 (`Honest Gap Disclosure & V5.0.0 Reclassification`)**: `docs/architecture.md` (§6.2, §6.4), `docs/system/sys_integration_tests.md`, and `docs/releases/V5.0.0/` (`verification_summary.md`, `system_integration_results.json`) MUST disclose that Python simulators are developer tools only, physical-sensor verification is manual hardware-in-the-loop (`SYS.5`), and `V5.0.0` `system_integration_results.json` records legacy simulator checks only (never Android app integration or fabricated hardware logs).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Modifies `REQ-PRO-068` (`TST-PRO-068`), `REQ-PRO-072` (`TST-PRO-072`), and `REQ-PRO-074.4` (`TST-PRO-074.4`), and adds net-new `REQ-PRO-112` (`TST-PRO-112`) targeting `tools/run_system_integration_tests.py`, `tools/test_system_integration_tests.py`, `tools/generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/`, `SystemIntegrationTestContractTest.kt`, and `ReleaseManagerSystemEvidenceContractTest.kt`.
* **Historical Origin & Commit Trace**: `REQ-PRO-068` (`ATT-6166`), `REQ-PRO-072` (`ATT-6169`), and `REQ-PRO-074` (`ATT-6138`) originated in Sprint `2026-46.4` (`ATT-6142` / `ATT-6119`); updated in Sprint `2026-11-17_01_Früh` via `ATT-8157` after `ATT-8155` (`b81888c8`) and `ATT-8156` (`84767320`).
* **Root Reason for Existing Formulation**: Prior rules treated Python simulator self-tests (`SystemIntegrationRunner`) as blocking SYS.4 app-integration gates without executing Android/ADB/emulator targets, while `SKILL.md` referenced a nonexistent `--output-json` CLI flag.
* **Preservation of Core Invariants**: Retains Python simulator self-tests, external interface traceability (`SYS-IF-01..05`, `SYSREQ-*`), `SYS.5` hardware sign-off (`sys_verification_record.md`) and `verification_summary.md` blocking gates, zero `app/src/main/` changes, and `ANT-Android-SDKs` untouched (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (`TST-PRO-112.1` / `TST-PRO-068` — Simulator Self-Tests)**: *Given* the renamed simulator self-test runner and unit suite (planned `tools/run_simulator_self_tests.py` and `tools/test_simulator_self_tests.py`), *When* executed via `python3 -m unittest` and `--output`, *Then* simulator contract tests pass (exit `0`), legacy `run_system_integration_tests.py` is absent, no Android-integration or constant-arithmetic claims remain, and `SystemIntegrationTestContractTest.kt` passes.
* **AC-2 (`TST-PRO-112.2` / `TST-PRO-072` / `TST-PRO-074.4` — Release Gate Honesty)**: *Given* a release tag directory lacking `system_integration_results.json` with valid unit/`INT-SWE-01..08` and signed `sys_verification_record.md`, *When* `tools/generate_release_verification_summary.py` runs, *Then* `collect_sys4_summary()` raises zero blocking deviations and `verification_summary.md` evaluates to `PASS (READY FOR RELEASE)`.
* **AC-3 (`TST-PRO-112.2` — Contract Guardrails)**: *Given* `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, and `docs/architecture.md` §6.4, *When* `ReleaseManagerSystemEvidenceContractTest.kt` runs, *Then* it enforces `SYS.5` / `verification_summary.md` gating and forbids blocking `system_integration_results.json` / `--output-json` mandates.
* **AC-4 (`TST-PRO-112.3` — Honest Documentation & V5.0.0)**: *Given* `docs/architecture.md`, `docs/system/sys_integration_tests.md`, and `docs/releases/V5.0.0/`, *When* inspected and checked via `tools/verify_requirement_governance.py`, *Then* simulator-only vs `INT-SWE-01..08` / `AppSmokeAndroidTest.kt` / manual `SYS.5` boundaries are explicit, `V5.0.0` is annotated without fabricated logs, and governance exits `0`.

---

## 4. Test Specifications & Traceability Matrix (`9-Locale Parity`: N/A — zero `strings*.xml` edits)

| Test Case | Scope | Target File / Class | Requirement / Interface | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-112.1`, `TST-PRO-068` | Python + Contract | `tools/test_system_integration_tests.py` (planned `test_simulator_self_tests.py`), `SystemIntegrationTestContractTest.kt` | `REQ-PRO-112.1`, `REQ-PRO-068` | Planned |
| `TST-PRO-112.2`, `TST-PRO-072`, `TST-PRO-074.4` | Python + Contract | `tools/test_generate_release_verification_summary.py`, `ReleaseManagerSystemEvidenceContractTest.kt` | `REQ-PRO-112.2`, `REQ-PRO-072`, `REQ-PRO-074.4` | Planned |
| `TST-PRO-112.3` | Governance / Review | `tools/verify_requirement_governance.py`, `docs/architecture.md`, `docs/system/sys_integration_tests.md`, `docs/releases/V5.0.0/` | `REQ-PRO-112.3` | Planned |
