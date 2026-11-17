# Stage 2: Requirement & Test Specification - ATT-8584: Autonomous Schicht Retro Write-Allowlist

**Ticket**: [ATT-8584](https://rainerblind.atlassian.net/browse/ATT-8584) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät`
**Requirement Mapping**: `REQ-PRO-117` (refining `REQ-PRO-105`, `REQ-STP-010.2`) | **Test Spec ID**: `TST-PRO-117`
**Branch**: `feature/ATT-8584` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (REQ-PRO-117)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Status: `[Planned]`
* **REQ-PRO-117.1 (Autonomous Retro Write-Allowlist)**: `.agents/rules/aspice_governance.md` and `.agents/skills/sprint-planner/SKILL.md` SHALL restrict autonomous `chore(ceremony2)` writes strictly to evidence/log paths: `docs/engineering/retros/Sprint_Review_and_Retro_*.md`, `docs/engineering/gates/*`, `docs/engineering/qa_audits/*`, `docs/engineering/reviews/*`, `docs/engineering/test_evidence/*`, `docs/engineering/metrics/process_metrics.jsonl`, and `docs/management/risk_register.md` (`REQ-STP-010.2`).
* **REQ-PRO-117.2 (Protected Paths & Escalation Routing)**: Without interactive PO authorization or a dedicated PO-approved ticket, autonomous closes MUST NOT modify `.agents/rules/**`, `.agents/skills/**`, `docs/coding_standards.md`, `docs/design_guidelines.md`, `docs/project_governance.md`, `docs/project_protocol.md`, `docs/engineering/rule_history_archive.md`, or `tools/**`; proposed changes SHALL be recorded in `Sprint_Review_and_Retro_<KEY>.md` and rolling `Wochen Review & Retro`. Existing `HoechstmassCoreFrameworkContractTest.kt` markers (`sprint-metrics --record --post-to-retro`, `PA 4.2`, `PA 5.2`) and `REQ-STP-010.2` MUST remain preserved.
* **REQ-PRO-117.3 (Automated Verifier)**: `tools/verify_requirement_governance.py` SHALL expose `verify_retro_write_allowlist()` (`--check-retro-allowlist`) rejecting protected/non-allowlisted edits in autonomous `chore(ceremony2)` commits while exempting pre-cutoff commits (`<= 8aa881df`), dedicated ticket commits (`ATT-*`), and interactive PO-authorized commits (`Interactive-PO:`).
* **REQ-PRO-117.4 (Dual-Layer Verification)**: `tools/test_verify_requirement_governance.py` and `AspiceSweAuditContractTest.kt` SHALL enforce the Python guard and rule/skill documentation parity.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `[Verified]` `REQ-PRO-105` (`ATT-8166`) and `REQ-STP-010.2` (`ATT-8167`) without altering either existing requirement.
* **Historical Origin & Commit Trace**: `REQ-PRO-105` (`ATT-8166`) enforced PO changelog provenance; `Rule 2.2` (`ATT-8030`) introduced 3-Schicht autonomous rollovers and rolling `Wochen Review & Retro`.
* **Root Reason for Existing Formulation**: Interactive Ceremony 2 sessions previously permitted direct rule/skill edits in `SKILL.md` L257 while the human PO co-facilitated.
* **Preservation of Core Invariants**: `[Verified]` `REQ-PRO-105`, `REQ-STP-010.2`, `ensure-weekly-retro`, `HoechstmassCoreFrameworkContractTest` markers, and dedicated PO-approved ticket authority (`447`) stay intact.

---

## 3. Acceptance Criteria (Given-When-Then) & Localization Parity
* **Criterion 1 (Rules & Skill Boundary)**: *Given* `.agents/rules/aspice_governance.md` & `.agents/skills/sprint-planner/SKILL.md`, *When* audited by `AspiceSweAuditContractTest` and `HoechstmassCoreFrameworkContractTest`, *Then* both enforce the 7-path allowlist, protect rules/skills/standards/tools, route proposals to Retro + `Wochen Review & Retro`, and preserve `sprint-metrics` / `PA 4.2` / `PA 5.2` / `REQ-STP-010.2`.
* **Criterion 2 (Automated Guard)**: *Given* `verify_requirement_governance.py --check-retro-allowlist`, *When* evaluating allowlisted `chore(ceremony2)` vs protected-path violations vs ticket-backed or pre-cutoff (`8aa881df`) commits, *Then* violations fail (`1`) and valid/exempt cases pass (`0`).
* **Localization Parity**: `N/A - no UI strings`.

---

## 4. Test Specification (TST-PRO-117)
* **TST-PRO-117.1 (Python Guard Unit Suite)**: `python3 -m unittest tools/test_verify_requirement_governance.py` testing allowlist pass, protected-path rejection, and ticket/PO/pre-cutoff exemptions.
* **TST-PRO-117.2 (Kotlin Contract & Parity Suite)**: `./gradlew :app:testDebugUnitTest --tests "*.AspiceSweAuditContractTest" --tests "*.HoechstmassCoreFrameworkContractTest"` verifying rule/skill allowlist & `sprint-metrics`/`PA 4.2`/`PA 5.2` parity.
* **TST-PRO-117.3 (CLI Governance Parity)**: `python3 tools/verify_requirement_governance.py` and `python3 tools/verify_requirement_governance.py --check-retro-allowlist` exit `0`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-117.1` | Unit (Python) | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | `REQ-PRO-117.3`, `REQ-PRO-117.4` | Planned |
| `TST-PRO-117.2` | Unit (Kotlin) | `AspiceSweAuditContractTest.kt`, `HoechstmassCoreFrameworkContractTest.kt` | `REQ-PRO-117.1`, `REQ-PRO-117.2`, `REQ-PRO-117.4` | Planned |
| `TST-PRO-117.3` | CLI Governance | `tools/verify_requirement_governance.py` | `REQ-PRO-117` | Planned |
