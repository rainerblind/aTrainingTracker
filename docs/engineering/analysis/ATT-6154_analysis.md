# Stage 1 Analysis: ATT-6154 - Introduce sprint-level QA process-compliance audit

**Ticket**: [ATT-6154](https://rainerblind.atlassian.net/browse/ATT-6154)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6154`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product & Process Evidence), quality gates (Gates 1–5 and Stage 6) independently audit work products (analysis, specs, plans, code, and test evidence). However, as identified in ASPICE 4.0 Pre-Assessment Finding B-09, there is currently no separate sprint-level QA audit of process adherence (verifying whether agents strictly followed the ASPICE lifecycle rules, preserved 4-Augen-Prinzip role separation, synchronized living documentation, and maintained single commit hygiene).
The objective of ATT-6154 is to introduce an automated, sampled QA process-compliance audit tool (`qa-audit`) executed during Ceremony 2, generating structured compliance records in `docs/engineering/qa_audits/<sprint>.md`, and enforcing its presence via `verify_requirement_governance.py --check-qa-audits`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Quality gates audit deliverables at stage transitions, but retrospective checks do not sample completed tickets to verify end-to-end process conformity across all lifecycle stages.
  * `docs/engineering/` lacks a dedicated, canonical directory for independent process compliance audit reports (`qa_audits/`).
  * `tools/jira_util.py` lacks a dedicated `qa-audit` CLI command to sample $\ge 2$ tickets and evaluate 5 core process criteria (artifacts, gate order, 4-Augen role separation, living doc parity, commit hygiene).
  * `tools/verify_requirement_governance.py` validates gates (`--check-gates`) and metrics (`--check-metrics`), but has no check for QA process-compliance audits.
* **Independent Hypothesis Verification**:
  * Audit of `docs/engineering/`: Contains `gates/` (ATT-6149) and `metrics/` (ATT-6150), but no `qa_audits/`.
  * Audit of `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` & `hoechstmass_config.json`: `"qa_audits"` is not yet registered.
  * Audit of `sprint-planner/SKILL.md` Ceremony 2: Step 4 mandates `sprint-metrics` and `export-gates`, but lacks a separate QA process-compliance audit step.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `qa-audit [--sprint <ID|NAME>] [--sample N] [--out <FILE>]` command to `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
  2. Implement sampling of $\ge 2$ tickets per sprint and evaluation of 5 core compliance checklist items.
  3. Register `"qa_audits"` in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.
  4. Implement `verify_qa_audits(sprint_name=None, repo_root=None)` and `--check-qa-audits` in `tools/verify_requirement_governance.py` and Höchstmaß mirror.
  5. Update `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 to mandate `qa-audit` and `--check-qa-audits`.
  6. Define formal `REQ-PRO-061` and `TST-PRO-061` in living documentation with 100% parity.
  7. Add unit tests in `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Out-of-Scope Non-Goals**:
  * Zero modifications to Android production code in `app/src/main/`.
  * No modification to Jira Cloud schema or automated webhook configurations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-061` (*Sprint-Level QA Process-Compliance Audit*). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/qa_audits/`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6154 under Epic ATT-6141 to resolve ASPICE pre-assessment finding B-09.
* `Root Reason for Existing Formulation`: Gate verdicts validated artifacts, but no independent QA mechanism sampled tickets to verify lifecycle process compliance.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; offline mock testing resilience preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **QA Process Audit Tooling (`qa-audit`)**:
   Query sprint parent issues, sample $\ge 2$ engineering tickets, evaluate 5-point checklist (artifacts, gates, role separation, living doc parity, commit hygiene), format Markdown, and serialize to `docs/engineering/qa_audits/<sprint>.md`.
2. **Directory & Governance Validation**:
   Register `"qa_audits"` in allowlists; implement `verify_qa_audits()` to verify record existence, $\ge 2$ sampled tickets, and compliance verdicts.
3. **Ceremony 2 Integration & Höchstmaß Parity**:
   Integrate `qa-audit` into `sprint-planner/SKILL.md`; replicate all CLI commands and validation logic in `tools/hoechstmass/`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Pure Python standard library; zero production code impact; full backward compatibility with existing CLI commands.
* **Risk Rating**: **LOW** — Tooling, process documentation, and governance verifier enhancement.
