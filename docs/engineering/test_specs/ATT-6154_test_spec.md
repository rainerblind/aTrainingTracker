# Stage 2: Requirement & Test Specification - ATT-6154: Introduce sprint-level QA process-compliance audit

**Ticket**: [ATT-6154](https://rainerblind.atlassian.net/browse/ATT-6154)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-061` (*Sprint-Level QA Process-Compliance Audit*)  
**Test Spec ID**: `TST-PRO-061`  
**Branch**: `feature/ATT-6154`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-061`)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product Evidence), quality gates independently review work products, but no separate audit validates lifecycle process conformity (Finding B-09). This requirement establishes an automated QA process-compliance audit tool (`qa-audit`) that samples $\ge 2$ tickets per sprint, evaluates a 5-point process checklist, records compliance in `docs/engineering/qa_audits/<sprint>.md`, and validates presence via `--check-qa-audits`.

### 1.2 Functional & Architectural Requirements
1. **QA Process Audit Tooling (`REQ-PRO-061.1`)**: `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL implement `qa-audit [--sprint <ID|NAME>] [--sample N] [--out <FILE>]` to sample $\ge 2$ tickets and evaluate 5 core process criteria (artifacts, gate order, 4-Augen role separation, living doc parity, commit hygiene).
2. **Audit Report Schema (`REQ-PRO-061.2`)**: The audit deliverable (`docs/engineering/qa_audits/<sprint>.md`) SHALL record: Sprint ID/Name, Auditor Role, Date, Sampled Tickets table, Checklist Results per ticket, Overall Compliance Verdict (`COMPLIANT` / `NON-COMPLIANT`), and links to any non-compliance tracking issues.
3. **Directory Governance (`REQ-PRO-061.3`)**: `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `hoechstmass_config.json` SHALL register `"qa_audits"`.
4. **Governance Enforcement CLI (`REQ-PRO-061.4`)**: `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL implement `--check-qa-audits [SPRINT]`, failing with exit code 1 if the audit file is missing, empty, or has $<2$ sampled ticket evaluations.
5. **Ceremony 2 Retrospective Mandate (`REQ-PRO-061.5`)**: `sprint-planner/SKILL.md` Ceremony 2 SHALL mandate executing `qa-audit` and `--check-qa-audits` prior to sprint closure.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-061` (`REQ-PRO-061.1`–`REQ-PRO-061.5`). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/qa_audits/`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6154 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-09.
* `Root Reason for Existing Formulation`: Gaps in independent QA process adherence audits; gate reviews verified artifacts but did not systematically audit end-to-end lifecycle conformity.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; offline mock testing resilience preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (QA Audit Execution)**:
  * *Given* Ceremony 2 of sprint `sprint/2026-46.3`
  * *When* `qa-audit` is executed
  * *Then* it generates `docs/engineering/qa_audits/sprint_2026-46.3.md` auditing $\ge 2$ sampled tickets with compliance verdicts.
* **AC-2 (Missing or Insufficient Audit Failure)**:
  * *Given* a sprint without a QA audit record or with $<2$ sampled tickets
  * *When* `verify_requirement_governance.py --check-qa-audits <sprint>` runs
  * *Then* it aborts with exit code 1.
* **AC-3 (Valid QA Audit Pass)**:
  * *Given* a valid QA audit record with $\ge 2$ sampled tickets
  * *When* `verify_requirement_governance.py --check-qa-audits <sprint>` runs
  * *Then* it passes with exit code 0.

---

## 2. Test Specification (`TST-PRO-061`)

### Test Case 1: qa-audit CLI Command & Report Generation (`TST-PRO-061.1`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Verify `qa_audit` extracts sampled tickets, evaluates 5 checklist items, and writes formatted markdown.

### Test Case 2: Post-Sprint QA Audit Verification (`TST-PRO-061.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Run `verify_qa_audits` against valid, missing, empty, and $<2$ sample files; assert pass/fail status.

### Test Case 3: Counterpart Parity & Tooling Integration (`TST-PRO-061.3`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Verify `qa_audit` and `verify_qa_audits` in Höchstmaß mirror match root tooling behavior.

### Test Case 4: Canonical Directory Registration (`TST-PRO-061.4`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Assert `"qa_audits"` is in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-061.1` | Unit | `qa_audit` | `REQ-PRO-061.1`, `REQ-PRO-061.2` | Specified |
| `TST-PRO-061.2` | Unit | `verify_qa_audits` | `REQ-PRO-061.4` | Specified |
| `TST-PRO-061.3` | Unit | `qa_audit`, `verify_qa_audits` | `REQ-PRO-061.1`, `REQ-PRO-061.4` | Specified |
| `TST-PRO-061.4` | Unit | `verify_engineering_directory_structure` | `REQ-PRO-061.3` | Specified |
