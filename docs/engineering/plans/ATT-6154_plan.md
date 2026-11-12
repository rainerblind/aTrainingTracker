# Stage 3: Implementation Plan - ATT-6154: Introduce sprint-level QA process-compliance audit

**Ticket**: [ATT-6154](https://rainerblind.atlassian.net/browse/ATT-6154)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-061` (`REQ-PRO-061.1`–`REQ-PRO-061.5`)  
**Test Mapping**: `TST-PRO-061` (`TST-PRO-061.1`–`TST-PRO-061.4`)  
**Branch**: `feature/ATT-6154`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE SUP.1 and GP 2.2 require independent quality assurance of both work products and process execution. Finding B-09 highlighted that quality gates review individual stage artifacts, but no retrospective mechanism audits end-to-end lifecycle process conformity. This plan details the 4 atomic steps to build the `qa-audit` tool, register `docs/engineering/qa_audits/`, implement governance verification, and mandate execution in Ceremony 2.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-061.1`**: `qa-audit` CLI tool and 5-point checklist evaluation (`TST-PRO-061.1`).
* **`REQ-PRO-061.2`**: Markdown audit report schema in `docs/engineering/qa_audits/<sprint>.md` (`TST-PRO-061.1`).
* **`REQ-PRO-061.3`**: Registration of `qa_audits` in canonical engineering directory allowlists (`TST-PRO-061.4`).
* **`REQ-PRO-061.4`**: Governance verifier `--check-qa-audits [SPRINT]` enforcement (`TST-PRO-061.2`, `TST-PRO-061.3`).
* **`REQ-PRO-061.5`**: Ceremony 2 retrospective mandate in sprint-planner skill (`TST-PRO-061.1`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Impact**: Zero modifications to production Android code or application resources in `app/src/main/`.
2. **Standard Library Footprint**: Pure Python standard library (`json`, `os`, `re`, `sys`).
3. **Inviolable Human Gate**: AI agent roles strictly barred from moving parent tickets to `Erledigt`.
4. **Tooling Mirror Parity**: 100% functional parity between root tools and `tools/hoechstmass/tools/`.

---

## 4. Proposed Architectural Changes
* **`tools/jira_util.py` & `tools/hoechstmass/...`**: Implement `qa_audit(sprint_arg=None, sample_size=2, out_path=None, role="coordinator")` sampling $\ge 2$ tickets and evaluating 5 process criteria (deliverables present, sequential gate closure, 4-Augen role separation, living doc parity, single commit squash).
* **`tools/verify_requirement_governance.py` & `tools/hoechstmass/...`**: Register `"qa_audits"` in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`; implement `verify_qa_audits()` and `--check-qa-audits` CLI parameter.
* **`.agents/skills/sprint-planner/SKILL.md`**: Mandate `python3 tools/jira_util.py qa-audit` and `verify_requirement_governance.py --check-qa-audits` during Ceremony 2.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Directory Registration & Verifier Extension
* Files: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/hoechstmass/hoechstmass_config.json`
* Action: Add `"qa_audits"` to allowlists; add `verify_qa_audits(sprint_name, repo_root)` and wire `--check-qa-audits`.

### Step 2: Implement `qa-audit` Command Engine
* Files: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`
* Action: Implement `qa_audit(...)` function and add `qa-audit` CLI command handling.

### Step 3: Integrate Ceremony 2 Skill Mandate
* Files: `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`
* Action: Add `qa-audit` and `--check-qa-audits` to Ceremony 2 Step 5 checklist.

### Step 4: Unit Testing & Verification
* Files: `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
* Action: Add unit tests validating `qa_audit` generation, `verify_qa_audits` pass/fail cases, and directory allowlist.
* Commands:
  - `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py`
  - `PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute unit tests, test live `qa-audit` and `--check-qa-audits`, and run full living doc parity checks.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or sprint branch.
