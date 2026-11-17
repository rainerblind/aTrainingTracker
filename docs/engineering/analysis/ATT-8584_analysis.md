# Stage 1 Analysis: ATT-8584 - Autonomous Schicht Retro Write-Allowlist

**Ticket**: [ATT-8584](https://rainerblind.atlassian.net/browse/ATT-8584) | **Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät` (`ea6fa3809fcbd3b2c227b3db0ec4303c09628ea6`)
**Branch**: `feature/ATT-8584` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
Under continuous 3-Schicht autonomous operation (`05:00`/`15:00`/`22:00`, `Rule 2.2`), per-Schicht `Review & Retro` closes execute without the PO present. However, `.agents/skills/sprint-planner/SKILL.md` (Ceremony 2 Step 5, L257) still instructs agents to directly edit `.agents/rules/aspice_governance.md`, skill files, `docs/coding_standards.md`, `docs/design_guidelines.md`, and `docs/engineering/rule_history_archive.md`. Without a strict write boundary, an autonomous Retro could weaken rules, gates, or skills without PO approval (`ATT-8166` / `REQ-PRO-105`).

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA**: `sprint-planner/SKILL.md` L257 predates autonomous 3-Schicht rollovers (`ATT-8030`) and `REQ-PRO-105` (`ATT-8166`). Although `ensure-weekly-retro` (`jira_util.py` L3486, `SKILL.md` L45/L249) was added for weekly PO review, Step 5 L257 was never restricted to evidence/log paths.
* **Independent Peer Review & Codebase Audit**:
  * **Protected Paths Confirmed Present**: `.agents/rules/**`, `.agents/skills/**`, `docs/coding_standards.md`, `docs/design_guidelines.md`, `docs/project_governance.md`, `docs/project_protocol.md`, `tools/**` (and `docs/engineering/rule_history_archive.md`).
  * **Recent `chore(ceremony2)` Commits (`c8845cf7` `ATT-8562`, `8ebb1212` `ATT-8500`)**: Touched ONLY `docs/engineering/retros/Sprint_Review_and_Retro_*.md`, `docs/engineering/gates/*`, `docs/engineering/qa_audits/*`, `docs/engineering/reviews/*`, `docs/engineering/test_evidence/*`, `docs/engineering/metrics/process_metrics.jsonl`, and `docs/management/risk_register.md`.
  * **Historical & Ticket-Backed Exceptions**: Pre-cutoff commit `8aa881df` (`2026-11-16`) touched `TrackingRobustnessTest.kt`, and interactive PO retros (`97133154`, `eef7cd1f`) or dedicated PO-approved tickets (including `ATT-8584` itself) legitimately touch rules/skills. Thus any automated check must target autonomous `chore(ceremony2)` commits post-cutoff (or explicit commit/file-list inputs) and never false-positive on ticket-backed commits or pre-`ATT-8584` history.
  * **Existing Contract Tests**: `HoechstmassCoreFrameworkContractTest.kt` L257 tests `sprint-metrics` + `PA 4.2`/`PA 5.2` in `SKILL.md`; `AspiceSweAuditContractTest.kt` L214 tests `aspice_governance.md`; `tools/verify_requirement_governance.py` (`--check-gates`, `--check-metrics`, `--check-qa-audits`, `--check-reviews`, `--check-test-evidence`) currently lacks a Ceremony 2 write-allowlist guard.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (2 SP, Standard)**:
  1. Update `.agents/rules/aspice_governance.md` (`Rule 1.3`/`Rule 2.2`) and `.agents/skills/sprint-planner/SKILL.md` (Ceremony 2 Step 5 L257) to enforce the autonomous Retro write-allowlist (`retros/Sprint_Review_and_Retro_*.md`, `gates/*`, `qa_audits/*`, `reviews/*`, `test_evidence/*`, `metrics/process_metrics.jsonl`, `docs/management/risk_register.md` Sprint Review Log) and block unapproved edits to protected rule/skill/standard/tool paths, routing proposals to the Retro doc + rolling `Wochen Review & Retro`.
  2. Add `verify_retro_write_allowlist()` (`--check-retro-allowlist`) in `tools/verify_requirement_governance.py` + unit tests in `tools/test_verify_requirement_governance.py` (and a lightweight contract assertion in `AspiceSweAuditContractTest.kt` checking the allowlist text in `aspice_governance.md` & `sprint-planner/SKILL.md`).
* **Out-of-Scope**: `app/src/main/`, `tools/hoechstmass/`, rewriting git history, or touching `ANT-Android-SDKs`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-105` (`ATT-8166`, `docs/requirements/req_process_stability.md` L5591) via a net-new `REQ-PRO-*` requirement without altering `[Verified]` `REQ-PRO-105`.
* **Historical Origin & Commit Trace**: `ATT-8166` established PO changelog provenance for agent tickets; `ATT-8030` established 3-Schicht autonomous cadence and rolling `Wochen Review & Retro`.
* **Root Reason for Existing Formulation**: Interactive retros historically permitted rule/skill updates when the human PO was co-facilitating Ceremony 2.
* **Preservation of Core Invariants**: Dedicated PO-approved tickets (`Vom PO Freigegeben` `447`) and interactive PO sessions retain authority to update rules/skills/standards; `Sprint Review Log` appends to `docs/management/risk_register.md` (`REQ-STP-010.2`) and `ensure-weekly-retro` remain preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-*` / `TST-PRO-*` (`docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`) refining `REQ-PRO-105`; `REQ-PRO-105` & `REQ-STP-010` preserved.
* **Affected Architecture Elements**: `.agents/rules/aspice_governance.md`, `.agents/skills/sprint-planner/SKILL.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `AspiceSweAuditContractTest.kt`.
* **Verification Impact & Test Surface**: `python3 -m unittest tools/test_verify_requirement_governance.py`, `python3 tools/verify_requirement_governance.py`, and targeted `AspiceSweAuditContractTest`.
* **Risk Assessment & Invariants**: **LOW** (2 SP process/tooling guard; zero `app/src/main/` changes; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`).

---

## 6. Architectural Strategy & High-Level Solution
* Split Ceremony 2 writes in `aspice_governance.md` & `sprint-planner/SKILL.md` into the strict 7-path **Autonomous Schicht-End Close Allowlist** vs **Protected Paths** (requiring interactive PO approval or a dedicated PO-approved ticket), routing autonomous rule/skill proposals into `Sprint_Review_and_Retro_<KEY>.md` and `Wochen Review & Retro`.
* Implement `verify_retro_write_allowlist()` (`--check-retro-allowlist`) in `tools/verify_requirement_governance.py` validating `chore(ceremony2)` commits against the allowlist/protected-path rules (with ticket/interactive-PO exemption & pre-cutoff history safety) and verify via unit + Kotlin contract tests.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `app/src/main/` untouched; `REQ-PRO-105` & `REQ-STP-010.2` preserved; `HoechstmassCoreFrameworkContractTest` compatibility intact; `ANT-Android-SDKs` at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (2 SP governance & tooling safeguard).
